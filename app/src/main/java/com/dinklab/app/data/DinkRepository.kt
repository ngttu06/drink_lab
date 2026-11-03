package com.dinklab.app.data

import androidx.room.withTransaction
import com.dinklab.app.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DinkRepository(private val db: LocalDatabase) {
    private val dao = db.dao()
    private val mutex = Mutex()
    val items = dao.observeItems().map { list -> list.map { it.domain() } }
    val events = dao.observeEvents()

    suspend fun initialize() = mutex.withLock {
        db.withTransaction {
            if (dao.preference("initialized") == null) {
                dao.put(DemoSeed.create().map(StoredItem::from))
                dao.preference(Preference("initialized", "true"))
            }
            if (dao.preference("branding-dinkapp") == null) {
                // Update only known seeded branding; preserve custom names and all financial data.
                val replacements = mapOf(
                    "owner" to ("DinkLab · Chủ doanh nghiệp" to "DinkApp · Chủ doanh nghiệp"),
                    "venue1" to ("DinkLab Thảo Điền" to "DinkApp Thảo Điền")
                )
                val branded = records().mapNotNull { record ->
                    var updated = record
                    replacements[record.id]?.let { (before, after) ->
                        if (updated.name == before) updated = updated.copy(name = after)
                    }
                    if (record.id == "admin" && record["email"] == "admin@dinklab.example") {
                        updated = updated.with("email" to "admin@dinkapp.example")
                    }
                    if (record.kind == Kind.COURT && record["venueId"] == "venue1" && record.name.endsWith(" · DinkLab Thảo Điền")) {
                        updated = updated.copy(name = record.name.removeSuffix("DinkLab Thảo Điền") + "DinkApp Thảo Điền")
                    }
                    updated.takeIf { it != record }
                }
                dao.put(branded.map(StoredItem::from))
                if (dao.preference("businessName") == "DinkLab") dao.preference(Preference("businessName", "DinkApp"))
                dao.preference(Preference("branding-dinkapp", "true"))
            }
        }
    }

    private suspend fun records() = dao.items().map { it.domain() }
    private suspend fun audit(user: Item, action: String, target: Item, detail: String = "") = dao.event(
        AuditEvent(UUID.randomUUID().toString(), user.id, user.name, action, target.id, target.name, detail)
    )

    suspend fun save(input: Item, actorId: String, reason: String = ""): String = mutex.withLock {
        db.withTransaction {
            val all = records()
            val actor = actor(all, actorId)
            val old = all.find { it.id == input.id }
            require(if (old == null) BusinessRules.canCreate(actor, input.kind) else BusinessRules.canEdit(actor, old, all)) { "Bạn không có quyền thực hiện thao tác này" }
            require(old == null || !old.archived) { "Bản ghi đã lưu trữ" }
            var item = input.copy(updatedAt = System.currentTimeMillis())
            if (old == null && item.kind == Kind.VOUCHER) require(item.status != "Đã đảo") { "Không tạo phiếu mới ở trạng thái đã đảo" }
            if (BusinessRules.role(actor) != Role.ADMIN) {
                if (item.kind == Kind.STAFF) {
                    require(old != null && old.id == actor.id && old.status == item.status &&
                        old.values.filterKeys { it !in setOf("phone", "email") } == item.values.filterKeys { it !in setOf("phone", "email") }) { "Chỉ được sửa hồ sơ cá nhân, không được sửa vai trò" }
                }
                if (item.kind == Kind.CUSTOMER) {
                    require(old == null || old["saleId"] == item["saleId"]) { "Chỉ Admin được chuyển sale" }
                    if (old == null) item = item.with("saleId" to actor.id)
                }
                if (item.kind in setOf(Kind.CARE, Kind.TASK)) item = item.with("ownerId" to actor.id)
                if (item.kind == Kind.ATTENDANCE) require(old != null && old.values.filterKeys { it != "note" } == item.values.filterKeys { it != "note" }) { "Coach chỉ được cập nhật trạng thái và ghi chú điểm danh" }
            }
            if (old != null) enforceImmutable(old, item, reason, all)
            if (old != null && item.kind == Kind.CUSTOMER && old["saleId"] != item["saleId"]) require(reason.isNotBlank()) { "Cần lý do chuyển sale" }
            if (item.kind == Kind.CLASS && all.any { it.kind == Kind.SESSION && it["classId"] == item.id } && old != null) {
                require(item["sessions"] == old["sessions"]) { "Lớp đã có lịch: không đổi số buổi khóa tùy tiện" }
                val count = all.count { it.kind == Kind.ENROLLMENT && it["classId"] == item.id && BusinessRules.active(it) }
                require(item.amount("capacity") >= count) { "Sĩ số không thấp hơn số học viên hiện tại" }
            }
            BusinessRules.validate(item, all, old)
            val updated = all.filter { it.id != item.id }.toMutableList().apply { add(item) }
            syncDerived(item, old, updated)
            dao.put(updated.map(StoredItem::from))
            val changes = if (old == null) "Tạo ${item.kind.singular}" else buildString {
                if (old.name != item.name) append("Tên: ${old.name} → ${item.name}; ")
                if (old.status != item.status) append("Trạng thái: ${old.status} → ${item.status}; ")
                (old.values.keys + item.values.keys).filter { old[it] != item[it] }.forEach { append("$it: ${old[it]} → ${item[it]}; ") }
                if (reason.isNotBlank()) append("Lý do: $reason")
            }
            audit(actor, if (old == null) "Tạo mới" else "Cập nhật", item, changes)
            item.id
        }
    }

    private fun actor(all: List<Item>, id: String): Item = all.firstOrNull {
        it.id == id && it.kind == Kind.STAFF && it.status == "Hoạt động" && !it.archived
    } ?: error("Tài khoản không còn hoạt động. Hãy đăng nhập lại.")

    private fun enforceImmutable(old: Item, next: Item, reason: String, all: List<Item>) {
        if (old == next.copy(updatedAt = old.updatedAt)) return
        when (old.kind) {
            Kind.VOUCHER -> require(old.status == "Nháp") { "Phiếu đã xác nhận không được sửa. Hãy dùng Đảo phiếu." }
            Kind.RECEIVABLE, Kind.PAYABLE -> {
                require(old.status != "Đã hủy") { "Khoản đã hủy không được sửa" }
                if (old.status == "Đã xác nhận") {
                    require(reason.isNotBlank()) { "Cần nhập lý do điều chỉnh khoản đã xác nhận" }
                    require(old.values.filterKeys { it !in setOf("amount", "due", "note") } == next.values.filterKeys { it !in setOf("amount", "due", "note") }) { "Không đổi đối tượng của khoản đã xác nhận" }
                    require(next.status in setOf("Đã xác nhận", "Đã hủy")) { "Không đưa khoản đã xác nhận về nháp" }
                }
            }
            Kind.SESSION -> {
                require(old.status != "Hoàn thành" || old == next.copy(updatedAt = old.updatedAt)) { "Buổi hoàn thành đã khóa lịch và đơn giá" }
                if (old["start"] != next["start"] || old["coachId"] != next["coachId"] || old["courtId"] != next["courtId"] || old.status != next.status) {
                    require(reason.isNotBlank()) { "Cần lý do thay đổi lịch / trạng thái" }
                }
            }
            Kind.ENROLLMENT -> if (old.status != "Nháp") {
                require(old["customerId"] == next["customerId"] && old["classId"] == next["classId"]) { "Không đổi lớp/học viên trực tiếp. Dùng Chuyển lớp." }
                if (old["fee"] != next["fee"] || old["discount"] != next["discount"] || old.status != next.status) require(reason.isNotBlank()) { "Cần lý do điều chỉnh đăng ký" }
            }
            Kind.ATTENDANCE -> if (all.any { it.id == old["sessionId"] && it.status == "Hoàn thành" }) require(reason.isNotBlank()) { "Cần lý do điều chỉnh điểm danh đã chốt" }
            else -> Unit
        }
    }

    private fun syncDerived(item: Item, old: Item?, all: MutableList<Item>) {
        fun upsert(value: Item) {
            val index = all.indexOfFirst { it.id == value.id }
            if (index < 0) all.add(value) else all[index] = value
        }
        fun obligation(source: Item, id: String, kind: Kind, name: String, fields: Map<String, String>, canceled: Boolean = false) {
            val existing = all.find { it.id == id } ?: all.find { it.kind == kind && it["generated"] == source.id }
            val candidate = (existing ?: Item(id, kind, name, "Đã xác nhận")).copy(
                status = if (canceled) "Đã hủy" else "Đã xác nhận",
                values = fields + ("generated" to source.id), updatedAt = System.currentTimeMillis()
            )
            BusinessRules.validate(candidate, all, existing)
            upsert(candidate)
        }
        when (item.kind) {
            Kind.ENROLLMENT -> if (item.status != "Nháp") {
                val fee = item.amount("fee") - item.amount("discount")
                if (fee > 0) obligation(item, "receivable-${item.id}", Kind.RECEIVABLE, "Học phí · ${item.name}", mapOf(
                    "enrollmentId" to item.id, "customerId" to item["customerId"], "classId" to item["classId"],
                    "amount" to fee.toString(), "due" to item["due"]
                ))
                else all.find { it.kind == Kind.RECEIVABLE && it["generated"] == item.id }?.let { existing ->
                    require(BusinessRules.settled(existing, all) == 0L) { "Cần hoàn/đảo khoản đã thu trước khi miễn toàn bộ học phí" }
                    upsert(existing.copy(status = "Đã hủy"))
                }
                // Withdrawal does not silently forgive remaining tuition or refund any money.
            }
            Kind.SESSION -> {
                val bookings = all.filter { it.kind == Kind.BOOKING && it["sessionId"] == item.id && it.status != "Đã hủy" }
                if (old != null && bookings.isNotEmpty()) {
                    bookings.forEach { booking ->
                        require(item["courtId"].isNotBlank() || item.status in setOf("Đã hủy", "Hoãn")) { "Buổi đã đặt sân: không được bỏ sân, hãy hủy đặt sân trước" }
                        val changed = old["courtId"] != item["courtId"] || old["start"] != item["start"] || old["end"] != item["end"]
                        val pay = all.find { it.kind == Kind.PAYABLE && it["bookingId"] == booking.id }
                        if (changed) require(pay == null || BusinessRules.settled(pay, all) == 0L) { "Đặt sân đã thanh toán: xử lý đảo/hoàn hoặc thỏa thuận trước khi đổi lịch" }
                        val court = all.find { it.id == item["courtId"] }
                        val price = if (old["courtId"] != item["courtId"]) court?.amount("price") ?: booking.amount("price") else booking.amount("price")
                        val status = when (item.status) { "Đã hủy", "Hoãn" -> "Đã hủy"; "Hoàn thành" -> "Hoàn thành"; else -> booking.status }
                        val updated = booking.copy(status = status).with("courtId" to item["courtId"], "start" to item["start"], "end" to item["end"], "price" to price.toString(), "amount" to (BusinessRules.hours(item) * price).toLong().toString())
                        BusinessRules.validate(updated, all.filter { it.id != booking.id }, booking)
                        upsert(updated)
                        syncDerived(updated, booking, all)
                    }
                }
                if (item.status == "Hoàn thành") {
                    require(item["coachId"].isNotBlank() && item["courtId"].isNotBlank()) { "Cần coach và sân trước khi hoàn thành buổi" }
                    if (item.amount("coachCost") > 0) obligation(item, "coach-pay-${item.id}", Kind.PAYABLE, "Công coach · ${item.name}", mapOf(
                        "category" to "Tiền coach", "classId" to item["classId"], "sessionId" to item.id, "coachId" to item["coachId"],
                        "payee" to (all.find { it.id == item["coachId"] }?.name ?: "Coach"), "amount" to item["coachCost"],
                        "due" to LocalDateTime.parse(item["end"]).toLocalDate().plusDays(7).toString()
                    ))
                }
            }
            Kind.BOOKING -> if (item.status != "Nháp") {
                val session = all.find { it.id == item["sessionId"] }
                val cancellation = item.status == "Đã hủy"
                val amount = if (cancellation) item.amount("cancelFee") else item.amount()
                val previous = all.find { it.kind == Kind.PAYABLE && it["bookingId"] == item.id }
                if (amount > 0 || previous != null) obligation(item, "court-pay-${item.id}", Kind.PAYABLE, "Tiền sân · ${item.name}", mapOf(
                    "category" to "Tiền sân", "classId" to session?.get("classId").orEmpty(), "sessionId" to item["sessionId"],
                    "bookingId" to item.id, "payee" to (all.find { it.id == item["courtId"] }?.name ?: "Đối tác sân"),
                    "amount" to (if (amount == 0L) previous?.amount() ?: item.amount() else amount).toString(),
                    "due" to LocalDateTime.parse(item["start"]).toLocalDate().toString()
                ), canceled = cancellation && amount == 0L)
            }
            else -> Unit
        }
    }

    suspend fun deleteOrArchive(id: String, actorId: String, reason: String): String = mutex.withLock {
        db.withTransaction {
            val all = records()
            val actor = actor(all, actorId)
            val item = all.first { it.id == id }
            require(BusinessRules.canEdit(actor, item, all) && item.kind != Kind.STAFF || BusinessRules.role(actor) == Role.ADMIN) { "Không có quyền xóa" }
            require(reason.isNotBlank()) { "Cần lý do xóa / lưu trữ" }
            require(item.kind !in setOf(Kind.VOUCHER, Kind.RECEIVABLE, Kind.PAYABLE) || item.status == "Nháp") { "Chỉ xóa chứng từ nháp; hãy dùng hủy hoặc đảo" }
            require(item.kind !in setOf(Kind.SESSION, Kind.BOOKING) || item.status == "Nháp" || (item.kind == Kind.SESSION && item.status == "Dự kiến")) { "Lịch đã xác nhận: hãy sửa trạng thái thành hủy" }
            if (item.kind == Kind.STAFF) {
                require(item.id != actorId) { "Không xóa tài khoản đang sử dụng" }
                require(item["role"] != "ADMIN" || all.count { it.kind == Kind.STAFF && it["role"] == "ADMIN" && it.status == "Hoạt động" && !it.archived } > 1) { "Phải còn một Admin hoạt động" }
                require(all.none { it.kind == Kind.CUSTOMER && it["saleId"] == id && !it.archived }) { "Cần chuyển khách sang sale khác trước" }
            }
            val linked = all.any { other -> other.id != id && (other.values.any { (key, value) -> key.endsWith("Id") && value == id } ||
                (other.kind == Kind.VOUCHER && runCatching { BusinessRules.allocations(other).containsKey(id) }.getOrDefault(false))) }
            val archived = linked || (item.kind == Kind.CLASS && item.status != "Nháp") || (item.kind == Kind.ENROLLMENT && item.status != "Nháp")
            if (archived) dao.put(listOf(StoredItem.from(item.copy(archived = true, updatedAt = System.currentTimeMillis())))) else dao.delete(id)
            audit(actor, if (archived) "Lưu trữ" else "Xóa", item, reason)
            if (archived) "Đã lưu trữ để giữ lịch sử liên kết" else "Đã xóa"
        }
    }

    suspend fun reverseVoucher(id: String, actorId: String, reason: String) = mutex.withLock {
        db.withTransaction {
            val all = records()
            val actor = actor(all, actorId)
            require(BusinessRules.role(actor) == Role.ADMIN) { "Chỉ Admin được đảo phiếu" }
            require(reason.isNotBlank()) { "Cần lý do đảo phiếu" }
            val item = all.first { it.id == id && it.kind == Kind.VOUCHER }
            require(item.status == "Đã xác nhận") { "Chỉ đảo phiếu đã xác nhận" }
            require(all.none { it.kind == Kind.VOUCHER && it["originalId"] == id && it.status == "Đã xác nhận" }) { "Cần đảo các phiếu hoàn tiền liên quan trước" }
            dao.put(listOf(StoredItem.from(item.copy(status = "Đã đảo", updatedAt = System.currentTimeMillis()).with("reversalReason" to reason))))
            audit(actor, "Đảo phiếu", item, reason)
        }
    }

    suspend fun generateSessions(classId: String, actorId: String): Int = mutex.withLock {
        db.withTransaction {
            val all = records().toMutableList()
            val actor = actor(all, actorId)
            require(BusinessRules.role(actor) == Role.ADMIN) { "Chỉ Admin được sinh lịch" }
            val clazz = all.first { it.id == classId }
            require(all.none { it.kind == Kind.SESSION && it["classId"] == classId }) { "Lớp đã có lịch; không sinh lại để tránh trùng" }
            val generated = BusinessRules.generateSchedule(clazz).mapIndexed { index, (start, end) ->
                Item(kind = Kind.SESSION, name = "${clazz["code"]} · Buổi ${index + 1}", status = "Dự kiến", values = mapOf(
                    "classId" to classId, "start" to start.toString(), "end" to end.toString(),
                    "coachId" to clazz["coachId"], "courtId" to clazz["courtId"],
                    "coachCost" to (all.find { it.id == clazz["coachId"] }?.get("rate") ?: "0")
                )).also { BusinessRules.validate(it, all); all.add(it) }
            }
            dao.put(generated.map(StoredItem::from))
            audit(actor, "Sinh lịch", clazz, "${generated.size} buổi; kiểm tra trùng lịch và lưu toàn bộ cùng lúc")
            generated.size
        }
    }

    suspend fun initializeAttendance(sessionId: String, actorId: String): Int = mutex.withLock {
        db.withTransaction {
            val all = records()
            val actor = actor(all, actorId)
            val session = all.first { it.id == sessionId }
            require(BusinessRules.role(actor) == Role.ADMIN) { "Chỉ Admin khởi tạo danh sách điểm danh" }
            val enrollments = all.filter { it.kind == Kind.ENROLLMENT && it["classId"] == session["classId"] && BusinessRules.active(it) }
            val generated = enrollments.filter { e -> all.none { it.kind == Kind.ATTENDANCE && it["sessionId"] == sessionId && it["enrollmentId"] == e.id } }.map { e ->
                Item(kind = Kind.ATTENDANCE, name = "${all.find { it.id == e["customerId"] }?.name} · ${session.name}", status = "Chưa xác nhận", values = mapOf("sessionId" to sessionId, "enrollmentId" to e.id))
            }
            dao.put(generated.map(StoredItem::from))
            audit(actor, "Khởi tạo điểm danh", session, "${generated.size} học viên")
            generated.size
        }
    }

    suspend fun rescheduleFuture(classId: String, startDate: String, time: String, weekdays: String,
                                 coachId: String, courtId: String, actorId: String, reason: String): Int = mutex.withLock {
        db.withTransaction {
            val all = records().toMutableList()
            val actor = actor(all, actorId)
            require(BusinessRules.role(actor) == Role.ADMIN && reason.isNotBlank()) { "Cần Admin và lý do đổi lịch hàng loạt" }
            val clazz = all.first { it.id == classId && it.kind == Kind.CLASS }
            val future = all.filter { it.kind == Kind.SESSION && it["classId"] == classId && !it.archived &&
                it.status in setOf("Dự kiến", "Đã xác nhận") && LocalDateTime.parse(it["start"]) > LocalDateTime.now() }.sortedBy { it["start"] }
            require(future.isNotEmpty()) { "Không có buổi tương lai phù hợp" }
            val template = clazz.with("startDate" to startDate, "startTime" to time, "weekdays" to weekdays,
                "sessions" to future.size.toString(), "coachId" to coachId, "courtId" to courtId)
            BusinessRules.validate(template, all, clazz)
            val schedule = BusinessRules.generateSchedule(template)
            require(schedule.first().first > LocalDateTime.now()) { "Lịch mới phải bắt đầu trong tương lai" }
            // Temporarily remove all moved sessions/bookings before checking the replacement batch.
            val futureIds = future.map { it.id }.toSet()
            val bookings = all.filter { it.kind == Kind.BOOKING && it["sessionId"] in futureIds && it.status != "Đã hủy" }
            val bookingIds = bookings.map { it.id }.toSet()
            require(all.filter { it.kind == Kind.PAYABLE && it["bookingId"] in bookingIds }.all { BusinessRules.settled(it, all) == 0L }) {
                "Có đặt sân đã thanh toán trong nhóm; cần xử lý giao dịch trước khi đổi hàng loạt"
            }
            val validating = all.filter { it.id !in futureIds && it.id !in bookingIds }.toMutableList()
            val changed = future.mapIndexed { index, old ->
                val (start, end) = schedule[index]
                old.with("start" to start.toString(), "end" to end.toString(), "coachId" to coachId, "courtId" to courtId,
                    "coachCost" to if (coachId == old["coachId"]) old["coachCost"] else all.find { it.id == coachId }?.get("rate").orEmpty())
                    .copy(updatedAt = System.currentTimeMillis()).also { BusinessRules.validate(it, validating, old); validating.add(it) }
            }
            changed.forEach { next ->
                val old = all.first { it.id == next.id }
                all[all.indexOfFirst { it.id == next.id }] = next
                // Check moved bookings against the full proposed session batch, not stale times.
                changed.forEach { proposed -> all[all.indexOfFirst { it.id == proposed.id }] = proposed }
                val ownBookingIds = bookings.filter { it["sessionId"] != next.id }.map { it.id }.toSet()
                val working = all.filter { it.id !in ownBookingIds }.toMutableList()
                syncDerived(next, old, working)
                working.filter { candidate -> candidate.kind in setOf(Kind.BOOKING, Kind.PAYABLE) }.forEach { updated ->
                    val pos = all.indexOfFirst { it.id == updated.id }
                    if (pos >= 0) all[pos] = updated else all.add(updated)
                }
                audit(actor, "Đổi lịch hàng loạt", next, "${old["start"]} → ${next["start"]}; $reason")
            }
            all.filter { it.id in bookingIds }.forEach { BusinessRules.validate(it, all, it) }
            dao.put(all.map(StoredItem::from))
            audit(actor, "Đổi lịch tương lai", clazz, "${changed.size} buổi; $reason")
            changed.size
        }
    }

    suspend fun transferEnrollment(id: String, targetClassId: String, actorId: String, reason: String): String = mutex.withLock {
        db.withTransaction {
            val all = records()
            val actor = actor(all, actorId)
            require(BusinessRules.role(actor) == Role.ADMIN && reason.isNotBlank()) { "Cần Admin và lý do chuyển lớp" }
            val old = all.first { it.id == id }
            val target = all.first { it.id == targetClassId && it.kind == Kind.CLASS && !it.archived }
            require(old.kind == Kind.ENROLLMENT && old["classId"] != targetClassId && BusinessRules.active(old)) { "Đăng ký hoặc lớp đích không hợp lệ" }
            val new = old.copy(id = UUID.randomUUID().toString(), name = "${all.find { it.id == old["customerId"] }?.name} · ${target.name}", status = "Nháp", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                .with("classId" to targetClassId, "joined" to Dates.day(), "note" to "Chuyển từ ${old.name}. $reason. Học phí cần xác nhận riêng.")
            dao.put(listOf(StoredItem.from(old.copy(status = "Đã rút")), StoredItem.from(new)))
            audit(actor, "Chuyển lớp", old, "Sang ${target.name}; tạo đăng ký nháp, giữ nguyên khoản thu cũ; $reason")
            new.id
        }
    }

    suspend fun reset(actorId: String, empty: Boolean) = mutex.withLock {
        db.withTransaction {
            val actor = actor(records(), actorId)
            require(BusinessRules.role(actor) == Role.ADMIN) { "Chỉ Admin được reset" }
            dao.clearItems(); dao.clearEvents(); dao.clearPreferences()
            dao.put(DemoSeed.create(empty = empty).map(StoredItem::from))
            dao.preference(Preference("initialized", "true"))
            audit(actor, "Khôi phục demo", actor, if (empty) "Bộ dữ liệu rỗng; giữ tài khoản demo" else "Bộ dữ liệu đầy đủ")
        }
    }

    suspend fun setting(key: String): String? = dao.preference(key)
    suspend fun setting(key: String, value: String) = dao.preference(Preference(key, value))
}