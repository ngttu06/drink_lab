package com.dinklab.app.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object BusinessRules {
    private val inactive = setOf("Đã hủy", "Đã rút", "Hoãn", "Nháp")
    fun active(item: Item): Boolean = !item.archived && item.status !in inactive
    fun overlaps(aStart: String, aEnd: String, bStart: String, bEnd: String): Boolean =
        LocalDateTime.parse(aStart) < LocalDateTime.parse(bEnd) && LocalDateTime.parse(bStart) < LocalDateTime.parse(aEnd)

    fun role(user: Item): Role = runCatching { Role.valueOf(user["role"]) }.getOrDefault(Role.SALE)

    fun visible(user: Item, item: Item, all: List<Item>): Boolean {
        val role = role(user)
        if (role == Role.ADMIN || role == Role.OWNER) return true
        val customerIds = all.filter { it.kind == Kind.CUSTOMER && it["saleId"] == user.id }.map { it.id }.toSet()
        val coachIds = all.filter { it.kind == Kind.COACH && it["staffId"] == user.id }.map { it.id }.toSet()
        val classIds = all.filter { it.kind == Kind.CLASS && it["coachId"] in coachIds }.map { it.id }.toSet() +
            all.filter { it.kind == Kind.SESSION && it["coachId"] in coachIds }.map { it["classId"] }
        return when (role) {
            Role.SALE -> when (item.kind) {
                Kind.STAFF -> item.id == user.id
                Kind.CUSTOMER -> item.id in customerIds
                Kind.CARE, Kind.TASK -> item["ownerId"] == user.id
                Kind.ENROLLMENT, Kind.RECEIVABLE -> item["customerId"] in customerIds || item["saleId"] == user.id ||
                    all.any { it.id == item["enrollmentId"] && it["customerId"] in customerIds }
                Kind.SOURCE, Kind.CAMPAIGN, Kind.COURSE, Kind.CLASS, Kind.SESSION -> true
                else -> false
            }
            Role.MARKETING -> item.kind in setOf(Kind.SOURCE, Kind.CAMPAIGN) || (item.kind == Kind.STAFF && item.id == user.id) ||
                (item.kind == Kind.PAYABLE && item["category"] == "Marketing")
            Role.COACH -> when (item.kind) {
                Kind.STAFF -> item.id == user.id
                Kind.COACH -> item.id in coachIds
                Kind.CLASS -> item.id in classIds
                Kind.SESSION -> item["coachId"] in coachIds
                Kind.ENROLLMENT -> item["classId"] in classIds
                Kind.CUSTOMER -> all.any { it.kind == Kind.ENROLLMENT && it["customerId"] == item.id && it["classId"] in classIds }
                Kind.ATTENDANCE -> all.any { it.id == item["sessionId"] && it["coachId"] in coachIds }
                Kind.PAYABLE -> item["coachId"] in coachIds
                Kind.COURT, Kind.VENUE -> true
                else -> false
            }
            else -> false
        }
    }

    fun canCreate(user: Item, kind: Kind): Boolean = when (role(user)) {
        Role.ADMIN -> true
        Role.SALE -> kind in setOf(Kind.CUSTOMER, Kind.CARE, Kind.TASK)
        Role.MARKETING -> kind in setOf(Kind.SOURCE, Kind.CAMPAIGN)
        else -> false
    }

    fun canEdit(user: Item, item: Item, all: List<Item>): Boolean {
        if (!visible(user, item, all)) return false
        return when (role(user)) {
            Role.ADMIN -> true
            Role.SALE -> item.kind in setOf(Kind.CUSTOMER, Kind.CARE, Kind.TASK) || item.kind == Kind.STAFF
            Role.MARKETING -> item.kind in setOf(Kind.SOURCE, Kind.CAMPAIGN) || item.kind == Kind.STAFF
            Role.COACH -> item.kind in setOf(Kind.ATTENDANCE, Kind.STAFF)
            Role.OWNER -> item.kind == Kind.STAFF && item.id == user.id
        }
    }

    fun protectedFields(user: Item, kind: Kind): Set<String> {
        val role = role(user)
        if (role == Role.ADMIN || role == Role.OWNER) return emptySet()
        return when (kind) {
            Kind.CUSTOMER -> if (role == Role.COACH) setOf("phone", "email", "saleId", "sourceId", "campaignId", "note", "followUp") else emptySet()
            Kind.ENROLLMENT -> if (role == Role.COACH) setOf("fee", "discount", "discountReason", "due", "saleId", "note") else emptySet()
            Kind.CLASS -> setOf("note")
            Kind.SESSION -> if (role != Role.COACH) setOf("coachCost", "note") else setOf("note")
            else -> emptySet()
        }
    }

    fun allocations(voucher: Item): Map<String, Long> {
        if (voucher["allocations"].isNotBlank()) {
            val entries = voucher["allocations"].split(';').filter { it.isNotBlank() }.map {
                val parts = it.trim().split(':')
                require(parts.size == 2) { "Phân bổ phải có dạng ID:số tiền;ID:số tiền" }
                val amount = parts[1].trim().toLongOrNull()
                require(amount != null && amount > 0) { "Số tiền phân bổ không hợp lệ" }
                parts[0].trim() to amount
            }
            require(entries.map { it.first }.distinct().size == entries.size) { "Một khoản chỉ được phân bổ một lần trong phiếu" }
            return entries.toMap()
        }
        val id = if (voucher["direction"] == "Thu") voucher["receivableId"] else voucher["payableId"]
        return if (id.isBlank()) emptyMap() else mapOf(id to voucher.amount())
    }

    fun settled(obligation: Item, all: List<Item>): Long {
        val payments = all.filter { it.kind == Kind.VOUCHER && it.status == "Đã xác nhận" }
        val paid = payments.filter { it["direction"] != "Hoàn tiền" }.sumOf { allocations(it)[obligation.id] ?: 0L }
        // Refund releases the receivable balance; cancellation/credit must explicitly adjust the obligation.
        val refunded = payments.filter { it["direction"] == "Hoàn tiền" }.sumOf { refund ->
            val original = all.find { it.id == refund["originalId"] } ?: return@sumOf 0L
            val allocated = allocations(original)[obligation.id] ?: 0L
            if (original.amount() == 0L) 0L else refund.amount() * allocated / original.amount()
        }
        return paid - refunded
    }

    fun balance(item: Item, all: List<Item>): Long = if (item.status != "Đã xác nhận") 0L else item.amount() - settled(item, all)

    fun paymentStatus(item: Item, all: List<Item>, today: LocalDate = LocalDate.now()): String {
        if (item.status != "Đã xác nhận") return item.status
        val left = balance(item, all)
        return when {
            left <= 0 -> "Đã thanh toán"
            item["due"].isNotBlank() && LocalDate.parse(item["due"]) < today -> "Quá hạn"
            settled(item, all) > 0 -> "Thanh toán một phần"
            else -> "Chưa thanh toán"
        }
    }

    fun validate(item: Item, all: List<Item>, old: Item? = null) {
        require(item.name.isNotBlank()) { "Vui lòng nhập tên / tiêu đề" }
        require(item.status in Schema[item.kind].statuses) { "Trạng thái không hợp lệ" }
        for (field in Schema[item.kind].fields) {
            val value = item[field.key]
            require(!field.required || value.isNotBlank()) { "${field.label} là bắt buộc" }
            if (value.isBlank()) continue
            when (field.input) {
                Input.NUMBER, Input.MONEY -> require(value.toLongOrNull()?.let { it >= 0 } == true) { "${field.label} phải là số nguyên không âm" }
                Input.DATE -> require(runCatching { LocalDate.parse(value) }.isSuccess) { "${field.label}: dùng yyyy-MM-dd" }
                Input.DATETIME -> require(runCatching { LocalDateTime.parse(value) }.isSuccess) { "${field.label}: dùng yyyy-MM-ddTHH:mm" }
                Input.REFERENCE -> require(all.any { it.id == value && it.kind == field.reference && (!it.archived || old?.get(field.key) == value) }) { "${field.label} không tồn tại hoặc đã lưu trữ" }
                Input.SELECT -> require(value in field.options) { "${field.label} không hợp lệ" }
                Input.PHONE -> require(value.replace(" ", "").matches(Regex("\\+?[0-9]{9,15}"))) { "Số điện thoại không hợp lệ" }
                Input.EMAIL -> require(value.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) { "Email không hợp lệ" }
                else -> Unit
            }
        }
        require(item["code"].isBlank() || all.none { it.id != item.id && it.kind == item.kind && it["code"].equals(item["code"], true) }) { "Mã đã tồn tại" }
        if (item["startDate"].isNotBlank() && item["endDate"].isNotBlank()) require(item["startDate"] <= item["endDate"]) { "Ngày kết thúc phải sau ngày bắt đầu" }
        if (item.kind == Kind.STAFF && old?.get("role") == Role.ADMIN.name && old.status == "Hoạt động" &&
            (item["role"] != Role.ADMIN.name || item.status != "Hoạt động")) {
            require(all.any { it.kind == Kind.STAFF && it.id != item.id && !it.archived && it["role"] == Role.ADMIN.name && it.status == "Hoạt động" }) { "Phải còn ít nhất một Admin hoạt động" }
        }
        if (item.kind == Kind.STAFF && item.status != "Hoạt động") require(all.none {
            it.kind == Kind.CUSTOMER && !it.archived && it["saleId"] == item.id
        }) { "Hãy chuyển khách sang sale khác trước khi ngừng nhân sự" }
        listOf("saleId", "ownerId").forEach { key ->
            if (item[key].isNotBlank() && item.kind in setOf(Kind.CUSTOMER, Kind.ENROLLMENT)) require(
                all.any { it.id == item[key] && it["role"] == Role.SALE.name }
            ) { "Người phụ trách phải có vai trò Sale" }
        }
        if (item.kind == Kind.COACH) {
            require(all.any { it.id == item["staffId"] && it["role"] == Role.COACH.name }) { "Nhân sự phải có vai trò Coach" }
            require(all.none { it.id != item.id && it.kind == Kind.COACH && it["staffId"] == item["staffId"] }) { "Nhân sự đã có hồ sơ coach" }
        }
        if (item.kind in setOf(Kind.CLASS, Kind.COURSE)) {
            listOf("sessions", "minutes", "capacity").forEach { require(item.amount(it) in 1..1000) { "Số buổi, thời lượng và sĩ số phải từ 1 đến 1000" } }
        }
        if (item.kind in setOf(Kind.CLASS, Kind.RATE)) {
            val days = item["weekdays"].split(',').map { it.trim().toIntOrNull() }
            require(days.isNotEmpty() && days.all { it != null && it in 1..7 } && days.distinct().size == days.size) { "Thứ trong tuần: nhập số 1–7, cách nhau bằng dấu phẩy, không trùng" }
            val keys = if (item.kind == Kind.CLASS) listOf("startTime") else listOf("fromTime", "toTime")
            keys.forEach { require(runCatching { LocalTime.parse(item[it]) }.isSuccess) { "Giờ phải theo HH:mm" } }
            if (item.kind == Kind.RATE) require(item["fromTime"] < item["toTime"]) { "Giờ kết thúc phải sau giờ bắt đầu" }
        }
        if (item.kind == Kind.ENROLLMENT) {
            require(item.amount("discount") <= item.amount("fee")) { "Giảm giá không vượt học phí" }
            require(item.amount("discount") == 0L || item["discountReason"].isNotBlank()) { "Cần lý do giảm giá" }
            if (item.status !in setOf("Đã rút", "Nháp")) {
                val clazz = all.first { it.id == item["classId"] }
                require(clazz.status !in setOf("Đã hủy", "Hoàn thành") && !clazz.archived) { "Lớp không còn nhận đăng ký" }
                val others = all.filter { it.kind == Kind.ENROLLMENT && it.id != item.id && it["classId"] == clazz.id && active(it) }
                require(others.none { it["customerId"] == item["customerId"] }) { "Học viên đã có đăng ký hiệu lực trong lớp" }
                require(others.size < clazz.amount("capacity")) { "Lớp đã đủ sĩ số" }
            }
        }
        if (item.kind in setOf(Kind.SESSION, Kind.BOOKING)) {
            require(LocalDateTime.parse(item["start"]) < LocalDateTime.parse(item["end"])) { "Kết thúc phải sau bắt đầu" }
            if (item.status !in setOf("Đã hủy", "Hoãn", "Nháp")) {
                val keys = if (item.kind == Kind.SESSION) listOf("coachId", "classId", "courtId") else listOf("courtId")
                val conflict = all.firstOrNull { other ->
                    other.id != item.id && other.kind == item.kind && active(other) &&
                        keys.any { item[it].isNotBlank() && item[it] == other[it] } &&
                        overlaps(item["start"], item["end"], other["start"], other["end"])
                }
                require(conflict == null) { "Trùng lịch với ${conflict?.name}" }
                val otherKind = if (item.kind == Kind.SESSION) Kind.BOOKING else Kind.SESSION
                require(all.none { other -> other.kind == otherKind && active(other) && item["courtId"].isNotBlank() &&
                    other["courtId"] == item["courtId"] && other["sessionId"] != item.id && item["sessionId"] != other.id &&
                    overlaps(item["start"], item["end"], other["start"], other["end"]) }) { "Sân đã được dùng bởi buổi học / đặt sân khác" }
            }
        }
        if (item.kind == Kind.BOOKING) {
            require(item.amount("deposit") <= item.amount()) { "Tiền cọc không vượt tổng tiền thuê" }
            if (item["sessionId"].isNotBlank()) {
                val session = all.first { it.id == item["sessionId"] }
                require(item["start"] == session["start"] && item["end"] == session["end"] && item["courtId"] == session["courtId"]) { "Đặt sân phải khớp giờ và sân của buổi học" }
                require(all.none { it.kind == Kind.BOOKING && it.id != item.id && active(it) && it["sessionId"] == session.id }) { "Buổi học đã có đặt sân" }
            }
        }
        if (item.kind == Kind.SESSION && item["originalId"].isNotBlank()) {
            val original = all.first { it.id == item["originalId"] }
            require(original.id != item.id && original["classId"] == item["classId"] && original["originalId"].isBlank()) { "Buổi bù phải liên kết buổi gốc cùng lớp" }
            require(original.status in setOf("Hoãn", "Đã hủy")) { "Chỉ tạo học bù cho buổi hoãn/hủy" }
            require(all.none { it.kind == Kind.SESSION && it.id != item.id && active(it) && it["originalId"] == original.id }) { "Buổi gốc đã có lịch bù" }
        }
        if (item.kind == Kind.ATTENDANCE) {
            val session = all.first { it.id == item["sessionId"] }
            val enrollment = all.first { it.id == item["enrollmentId"] }
            require(session["classId"] == enrollment["classId"]) { "Học viên không thuộc lớp của buổi học" }
            require(session.status !in setOf("Đã hủy", "Hoãn")) { "Không điểm danh buổi đã hủy/hoãn" }
            require(all.none { it.id != item.id && it.kind == Kind.ATTENDANCE && it["sessionId"] == session.id && it["enrollmentId"] == enrollment.id }) { "Đã có điểm danh cho học viên trong buổi này" }
        }
        if (item.kind in setOf(Kind.RECEIVABLE, Kind.PAYABLE)) {
            require(item.amount() > 0) { "Số tiền phải lớn hơn 0" }
            require(item.status != "Đã hủy" || settled(item, all) == 0L) { "Khoản đã thanh toán cần hoàn/đảo phiếu trước khi hủy" }
            require(item.amount() >= settled(item, all)) { "Số tiền không được thấp hơn số đã thanh toán" }
            if (item.kind == Kind.RECEIVABLE && item["enrollmentId"].isNotBlank()) {
                val enrollment = all.first { it.id == item["enrollmentId"] }
                require(enrollment["customerId"] == item["customerId"] && enrollment["classId"] == item["classId"]) { "Khoản học phí phải khớp học viên và lớp của đăng ký" }
            }
        }
        if (item.kind == Kind.VOUCHER) validateVoucher(item, all)
    }

    private fun validateVoucher(item: Item, all: List<Item>) {
        require(item.amount() > 0) { "Số tiền phải lớn hơn 0" }
        if (item.status != "Đã xác nhận") return
        val others = all.filter { it.id != item.id }
        if (item["direction"] == "Hoàn tiền") {
            val original = others.find { it.id == item["originalId"] }
            require(original != null && original.kind == Kind.VOUCHER && original.status == "Đã xác nhận" && original["direction"] == "Thu") { "Cần phiếu thu gốc đã xác nhận" }
            val refunded = others.filter { it.kind == Kind.VOUCHER && it.status == "Đã xác nhận" && it["originalId"] == original.id && it["direction"] == "Hoàn tiền" }.sumOf { it.amount() }
            require(item.amount() <= original.amount() - refunded) { "Hoàn tiền vượt số có thể hoàn" }
            require(allocations(original).values.all { allocated -> (item.amount() * allocated) % original.amount() == 0L }) { "Phiếu gốc phân bổ nhiều khoản: số hoàn phải chia chính xác theo tỷ lệ để không mất đơn vị VND" }
            return
        }
        val allocations = allocations(item)
        require(allocations.isNotEmpty() && allocations.values.sum() == item.amount()) { "Tổng phân bổ phải bằng số tiền phiếu" }
        val kind = if (item["direction"] == "Thu") Kind.RECEIVABLE else Kind.PAYABLE
        allocations.forEach { (id, amount) ->
            val target = others.find { it.id == id && it.kind == kind && it.status == "Đã xác nhận" }
            require(target != null) { "Khoản phân bổ không hợp lệ / chưa xác nhận" }
            require(amount <= balance(target, others)) { "Phân bổ vượt số còn lại: ${target.name}" }
        }
    }

    fun generateSchedule(clazz: Item): List<Pair<LocalDateTime, LocalDateTime>> {
        val count = clazz.amount("sessions").toInt()
        require(count in 1..1000) { "Số buổi không hợp lệ" }
        val days = clazz["weekdays"].split(',').map { it.trim().toInt() }.toSet()
        require(days.isNotEmpty() && days.all { it in 1..7 }) { "Thứ không hợp lệ" }
        var day = LocalDate.parse(clazz["startDate"])
        val time = LocalTime.parse(clazz["startTime"])
        val result = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        while (result.size < count) {
            if (day.dayOfWeek.value in days) {
                val start = day.atTime(time)
                result += start to start.plusMinutes(clazz.amount("minutes"))
            }
            day = day.plusDays(1)
        }
        return result
    }

    fun hours(item: Item): Double = runCatching {
        Duration.between(LocalDateTime.parse(item["start"]), LocalDateTime.parse(item["end"])).toMinutes() / 60.0
    }.getOrDefault(0.0)

    fun progress(clazz: Item, all: List<Item>): Pair<Int, Int> {
        val completed = all.filter { it.kind == Kind.SESSION && it["classId"] == clazz.id && it.status == "Hoàn thành" }
            .map { it["originalId"].ifBlank { it.id } }.distinct().size
        return completed to (clazz.amount("sessions").toInt() - completed).coerceAtLeast(0)
    }

    fun consumed(enrollment: Item, all: List<Item>): Int = all.filter {
        it.kind == Kind.ATTENDANCE && it["enrollmentId"] == enrollment.id &&
            (it.status in setOf("Có mặt", "Vắng không phép") || (it.status == "Vắng có phép" && enrollment["absencePolicy"] == "Vắng tính buổi"))
    }.mapNotNull { attendance -> all.find { it.id == attendance["sessionId"] && it.status == "Hoàn thành" } }
        .map { it["originalId"].ifBlank { it.id } }.distinct().size
}