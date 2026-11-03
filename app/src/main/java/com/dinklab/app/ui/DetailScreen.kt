package com.dinklab.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinklab.app.AppViewModel
import com.dinklab.app.domain.*

@Composable
fun DetailScreen(page: Page, vm: AppViewModel, user: Item, visible: List<Item>, all: List<Item>, navigate: (Page) -> Unit, back: () -> Unit) {
    val item = visible.find { it.id == page.id }
    if (item == null) { EmptyState("Không thể mở bản ghi", "Bản ghi không còn tồn tại hoặc bạn không có quyền xem."); return }
    val events by vm.events.collectAsState()
    var tab by rememberSaveable(item.id) { mutableStateOf("Thông tin") }
    var confirm by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var targetClass by remember { mutableStateOf("") }
    var futureDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(1).toString()) }
    var futureTime by remember { mutableStateOf(item["startTime"]) }
    var futureDays by remember { mutableStateOf(item["weekdays"]) }
    var futureCoach by remember { mutableStateOf(item["coachId"]) }
    var futureCourt by remember { mutableStateOf(item["courtId"]) }
    val admin = BusinessRules.role(user) == Role.ADMIN
    val edit = BusinessRules.canEdit(user, item, all) && !item.archived
    val hidden = BusinessRules.protectedFields(user, item.kind)
    val linked = visible.filter { other -> other.id != item.id && other.values.any { (key, value) -> key.endsWith("Id") && value == item.id } }
    val tabs = when (item.kind) {
        Kind.CLASS -> listOf("Thông tin", "Học viên", "Lịch học", "Tài chính", "Lịch sử")
        Kind.CUSTOMER -> listOf("Thông tin", "Chăm sóc", "Lớp học", "Tài chính", "Lịch sử")
        Kind.SESSION -> listOf("Thông tin", "Điểm danh", "Đặt sân", "Tài chính", "Lịch sử")
        else -> listOf("Thông tin", "Liên quan", "Lịch sử")
    }
    val related = when (tab) {
        "Học viên", "Lớp học" -> linked.filter { it.kind == Kind.ENROLLMENT }
        "Lịch học" -> linked.filter { it.kind == Kind.SESSION }.sortedBy { it["start"] }
        "Chăm sóc" -> linked.filter { it.kind in setOf(Kind.CARE, Kind.TASK) }
        "Điểm danh" -> linked.filter { it.kind == Kind.ATTENDANCE }
        "Đặt sân" -> linked.filter { it.kind == Kind.BOOKING }
        "Tài chính" -> linked.filter { it.kind in setOf(Kind.RECEIVABLE, Kind.PAYABLE, Kind.VOUCHER) }
        else -> linked
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp)); StatusBadge(if (item.archived) "Đã lưu trữ" else item.status)
            Text("ID: ${item.id}", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (edit) OutlinedButton(onClick = { navigate(Page("form", item.kind, item.id)) }) { Text("Chỉnh sửa") }
                if (edit && (admin || BusinessRules.canCreate(user, item.kind))) TextButton(onClick = { confirm = "delete" }) { Text("Xóa / lưu trữ", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (item.kind == Kind.CLASS) item {
            val (completed, remaining) = BusinessRules.progress(item, all)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Đã hoàn thành", "$completed buổi", Modifier.weight(1f))
                Metric("Còn lại", "$remaining buổi", Modifier.weight(1f))
            }
        }
        if (item.kind == Kind.ENROLLMENT) item {
            val clazz = all.find { it.id == item["classId"] }
            val used = BusinessRules.consumed(item, all)
            Text("Đã sử dụng: $used / ${clazz?.get("sessions").orEmpty()} buổi", fontWeight = FontWeight.SemiBold)
        }
        if (item.kind in setOf(Kind.RECEIVABLE, Kind.PAYABLE)) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Đã thanh toán", money(BusinessRules.settled(item, all)), Modifier.weight(1f))
                Metric("Còn lại", money(BusinessRules.balance(item, all)), Modifier.weight(1f), dark = true)
            }
            Spacer(Modifier.height(8.dp)); StatusBadge(BusinessRules.paymentStatus(item, all))
        }
        if (admin && !item.archived) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (item.kind) {
                    Kind.CLASS -> {
                        Button(onClick = { confirm = "generate" }, modifier = Modifier.fillMaxWidth()) { Text("Xem trước & sinh lịch học") }
                        OutlinedButton(onClick = { confirm = "future" }, modifier = Modifier.fillMaxWidth()) { Text("Đổi lịch các buổi tương lai") }
                        OutlinedButton(onClick = { navigate(Page("form", Kind.ENROLLMENT, defaults = mapOf("classId" to item.id))) }, modifier = Modifier.fillMaxWidth()) { Text("Thêm học viên vào lớp") }
                    }
                    Kind.CUSTOMER -> {
                        Button(onClick = { navigate(Page("form", Kind.ENROLLMENT, defaults = mapOf("customerId" to item.id, "saleId" to item["saleId"]))) }, modifier = Modifier.fillMaxWidth()) { Text("Đăng ký học") }
                    }
                    Kind.SESSION -> {
                        OutlinedButton(onClick = { vm.operation("Đã khởi tạo điểm danh") { vm.repository.initializeAttendance(item.id, it) }; tab = "Điểm danh" }, modifier = Modifier.fillMaxWidth()) { Text("Khởi tạo danh sách điểm danh") }
                        OutlinedButton(onClick = { navigate(Page("form", Kind.BOOKING, defaults = mapOf("sessionId" to item.id))) }, modifier = Modifier.fillMaxWidth()) { Text("Đặt sân cho buổi này") }
                        if (item.status in setOf("Đã hủy", "Hoãn")) OutlinedButton(onClick = {
                            navigate(Page("form", Kind.SESSION, defaults = mapOf("classId" to item["classId"], "originalId" to item.id, "coachId" to item["coachId"], "courtId" to item["courtId"], "coachCost" to item["coachCost"])))
                        }, modifier = Modifier.fillMaxWidth()) { Text("Tạo lịch học bù") }
                    }
                    Kind.ENROLLMENT -> OutlinedButton(onClick = { confirm = "transfer" }, modifier = Modifier.fillMaxWidth()) { Text("Chuyển lớp · giữ lịch sử học phí") }
                    Kind.RECEIVABLE, Kind.PAYABLE -> if (item.status == "Đã xác nhận" && BusinessRules.balance(item, all) > 0) Button(onClick = {
                        navigate(Page("form", Kind.VOUCHER, defaults = mapOf((if (item.kind == Kind.RECEIVABLE) "receivableId" else "payableId") to item.id)))
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (item.kind == Kind.RECEIVABLE) "Tạo phiếu thu" else "Tạo phiếu chi") }
                    Kind.VOUCHER -> if (item.status == "Đã xác nhận") {
                        OutlinedButton(onClick = { confirm = "reverse" }, modifier = Modifier.fillMaxWidth()) { Text("Đảo phiếu") }
                        if (item["direction"] == "Thu") OutlinedButton(onClick = { navigate(Page("form", Kind.VOUCHER, defaults = mapOf("direction" to "Hoàn tiền", "originalId" to item.id, "party" to item["party"]))) }, modifier = Modifier.fillMaxWidth()) { Text("Lập phiếu hoàn tiền") }
                    }
                    else -> Unit
                }
            }
        }
        if (item.kind == Kind.CUSTOMER && BusinessRules.canCreate(user, Kind.CARE)) item {
            OutlinedButton(onClick = { navigate(Page("form", Kind.CARE, defaults = mapOf("customerId" to item.id))) }, modifier = Modifier.fillMaxWidth()) { Text("Thêm lịch sử chăm sóc") }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { title -> FilterChip(tab == title, { tab = title }, label = { Text(title) }) }
            }
        }
        if (tab == "Thông tin") {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Schema[item.kind].fields.filter { it.key !in hidden }.forEach { field ->
                            val raw = item[field.key]
                            val target = all.find { it.id == raw && BusinessRules.visible(user, it, all) }
                            val value = when (field.input) {
                                Input.REFERENCE -> target?.name ?: if (raw.isBlank()) "" else "Thông tin được giới hạn"
                                Input.MONEY -> money(item.amount(field.key))
                                Input.DATE, Input.DATETIME -> Dates.pretty(raw)
                                else -> raw
                            }
                            InfoRow(field.label, value, if (target != null) ({ navigate(Page("detail", target.kind, target.id)) }) else null)
                            HorizontalDivider(color = Canvas)
                        }
                    }
                }
            }
        } else if (tab == "Lịch sử") {
            if (BusinessRules.role(user) in setOf(Role.ADMIN, Role.OWNER)) {
                val list = events.filter { it.targetId == item.id }
                if (list.isEmpty()) item { EmptyState("Chưa có thao tác", "Dữ liệu mẫu không tạo nhật ký giả cho từng bản ghi.") }
                items(list, key = { it.id }) { event -> MenuCard(event.action, "${event.actorName}\n${event.detail}", "NK") {} }
            } else item { EmptyState("Lịch sử giới hạn", "Chỉ quản trị và chủ doanh nghiệp được xem nhật ký chi tiết.") }
        } else {
            if (related.isEmpty()) item { EmptyState("Chưa có dữ liệu liên quan") }
            items(related, key = { it.id }) { record -> RecordCard(record, all, { navigate(Page("detail", record.kind, record.id)) }, BusinessRules.role(user) == Role.COACH && record.kind == Kind.ENROLLMENT) }
        }
    }
    if (confirm.isNotBlank()) AlertDialog(onDismissRequest = { confirm = "" }, title = {
        Text(when (confirm) { "generate" -> "Xem trước lịch học"; "future" -> "Đổi các buổi tương lai"; "transfer" -> "Chuyển lớp"; "reverse" -> "Đảo phiếu đã xác nhận?"; else -> "Xóa hoặc lưu trữ?" })
    }, text = {
        Column {
            if (confirm == "generate") {
                val schedule = runCatching { BusinessRules.generateSchedule(item) }
                Text(schedule.exceptionOrNull()?.message ?: "Sẽ tạo ${schedule.getOrNull()?.size} buổi. Toàn bộ lịch được kiểm tra xung đột trước khi lưu.")
                LazyColumn(Modifier.heightIn(max = 260.dp)) { items(schedule.getOrDefault(emptyList())) { (start, end) -> Text("${Dates.pretty(start.toString())} – ${end.toLocalTime()}", modifier = Modifier.padding(vertical = 5.dp)) } }
            } else {
                Text(if (confirm == "transfer") "Tạo đăng ký nháp ở lớp mới. Học phí/công nợ cũ được giữ nguyên và phải xử lý riêng." else "Thao tác được lưu vào nhật ký. Bản ghi có liên kết sẽ được bảo vệ.")
                if (confirm == "future") {
                    OutlinedTextField(futureDate, { futureDate = it }, label = { Text("Bắt đầu từ yyyy-MM-dd") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(futureTime, { futureTime = it }, label = { Text("Giờ HH:mm") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(futureDays, { futureDays = it }, label = { Text("Thứ (1,3,…)") }, modifier = Modifier.weight(1f))
                    }
                    ChoiceField("Coach", futureCoach, listOf("" to "Chưa phân công") + all.filter { it.kind == Kind.COACH && !it.archived }.map { it.id to it.name }, { futureCoach = it })
                    ChoiceField("Sân", futureCourt, listOf("" to "Chưa có sân") + all.filter { it.kind == Kind.COURT && !it.archived }.map { it.id to it.name }, { futureCourt = it })
                }
                if (confirm == "transfer") ChoiceField("Lớp đích", targetClass, all.filter { it.kind == Kind.CLASS && it.id != item["classId"] && !it.archived && it.status !in setOf("Đã hủy", "Hoàn thành") }.map { it.id to it.name }, { targetClass = it })
                OutlinedTextField(reason, { reason = it }, label = { Text("Lý do bắt buộc") }, minLines = 2)
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            when (confirm) {
                "generate" -> vm.operation("Đã sinh lịch", { confirm = ""; tab = "Lịch học" }) { vm.repository.generateSessions(item.id, it) }
                "future" -> vm.operation("Đã đổi các buổi tương lai", { confirm = ""; tab = "Lịch học" }) { vm.repository.rescheduleFuture(item.id, futureDate, futureTime, futureDays, futureCoach, futureCourt, it, reason) }
                "reverse" -> vm.operation("Đã đảo phiếu", { confirm = "" }) { vm.repository.reverseVoucher(item.id, it, reason) }
                "transfer" -> vm.operation("Đã tạo đăng ký nháp ở lớp mới", { confirm = "" }) { vm.repository.transferEnrollment(item.id, targetClass, it, reason) }
                else -> vm.operation("Đã xóa / lưu trữ", { confirm = ""; back() }) { vm.repository.deleteOrArchive(item.id, it, reason) }
            }
        }) { Text("Xác nhận") }
    }, dismissButton = { TextButton(onClick = { confirm = "" }) { Text("Hủy") } })
}