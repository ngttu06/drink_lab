package com.dinklab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dinklab.app.AppViewModel
import com.dinklab.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SettingsScreen(vm: AppViewModel, user: Item, navigate: (Page) -> Unit) {
    val delay by vm.simulateDelay.collectAsStateWithLifecycle()
    val failure by vm.simulateFailure.collectAsStateWithLifecycle()
    var reset by remember { mutableStateOf<String?>(null) }
    var businessName by rememberSaveable { mutableStateOf("DinkLab") }
    LaunchedEffect(Unit) { businessName = vm.repository.setting("businessName") ?: "DinkLab" }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle("Tài khoản hiện tại")
            MenuCard(user.name, BusinessRules.role(user).title, "NS") { navigate(Page("detail", Kind.STAFF, user.id)) }
            OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất / đổi tài khoản demo") }
        }
        item { SectionTitle("Cấu hình demo") }
        item { InfoRow("Tiền tệ / ngày giờ", "VND · dd/MM/yyyy · múi giờ thiết bị") }
        if (BusinessRules.role(user) == Role.ADMIN) {
            item {
                OutlinedTextField(businessName, { businessName = it }, label = { Text("Tên đơn vị") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.operation("Đã lưu tên đơn vị") { require(businessName.isNotBlank()) { "Tên không được trống" }; vm.repository.setting("businessName", businessName) } }) { Text("Lưu cấu hình") }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Mô phỏng xử lý chậm"); Text("Thêm 900ms cho thao tác lưu", color = Muted, style = MaterialTheme.typography.bodySmall) }
                    Switch(delay, vm::delayEnabled)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Lỗi ở thao tác tiếp theo"); Text("Không ghi dữ liệu; giữ nguyên form", color = Muted, style = MaterialTheme.typography.bodySmall) }
                    Switch(failure, { vm.simulateFailure.value = it })
                }
            }
            item { SectionTitle("Quản lý dữ liệu") }
            item { OutlinedButton(onClick = { reset = "full" }, modifier = Modifier.fillMaxWidth()) { Text("Khôi phục bộ dữ liệu demo đầy đủ") } }
            item { OutlinedButton(onClick = { reset = "empty" }, modifier = Modifier.fillMaxWidth()) { Text("Bộ dữ liệu rỗng · giữ tài khoản") } }
        }
        item {
            SectionTitle("Thông tin ứng dụng")
            InfoRow("Phiên bản", "0.1.0-demo")
            Text("Dữ liệu cục bộ, không đồng bộ nhiều thiết bị. Tài khoản và phân quyền chỉ là giả lập. Không dùng cho thanh toán hoặc dữ liệu nhạy cảm thật.", color = Muted)
        }
    }
    reset?.let { selected -> AlertDialog(onDismissRequest = { reset = null }, title = { Text("Thay toàn bộ dữ liệu?") },
        text = { Text("Mọi thay đổi và nhật ký hiện tại sẽ bị xóa. Bạn sẽ trở về màn hình chọn tài khoản.") },
        confirmButton = { TextButton(onClick = { vm.operation("Đã khôi phục dữ liệu", { reset = null; vm.logout() }) { vm.repository.reset(it, selected == "empty") } }) { Text("Xóa và khôi phục") } },
        dismissButton = { TextButton(onClick = { reset = null }) { Text("Hủy") } }) }
}

@Composable
fun AuditScreen(vm: AppViewModel, user: Item) {
    if (BusinessRules.role(user) !in setOf(Role.ADMIN, Role.OWNER)) { EmptyState("Không có quyền xem"); return }
    val events by vm.events.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val list = events.filter { "${it.action} ${it.actorName} ${it.targetName} ${it.detail}".contains(query, true) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query, { query = it }, label = { Text("Tìm người, thao tác, đối tượng") }, modifier = Modifier.fillMaxWidth()) }
        if (list.isEmpty()) item { EmptyState("Chưa có lịch sử", "Các thao tác sau khi mở demo sẽ xuất hiện tại đây.") }
        items(list, key = { it.id }) { event ->
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("${event.action} · ${event.targetName}", fontWeight = FontWeight.SemiBold)
                    Text("${event.actorName} · ${Dates.pretty(Instant.ofEpochMilli(event.at).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(event.detail, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun RemindersScreen(vm: AppViewModel, user: Item, visible: List<Item>, all: List<Item>, navigate: (Page) -> Unit) {
    var readIds by remember { mutableStateOf(setOf<String>()) }
    var hiddenIds by remember { mutableStateOf(setOf<String>()) }
    var group by rememberSaveable { mutableStateOf("Tất cả") }
    var enabled by remember { mutableStateOf(true) }
    val today = LocalDate.now().toString()
    LaunchedEffect(user.id) {
        readIds = vm.repository.setting("read-${user.id}").orEmpty().split('|').filter { it.isNotBlank() }.toSet()
        hiddenIds = vm.repository.setting("hidden-${user.id}").orEmpty().split('|').filter { it.isNotBlank() }.toSet()
        enabled = vm.repository.setting("reminders-${user.id}") != "false"
    }
    fun category(item: Item) = when (item.kind) { Kind.SESSION -> "Lịch học"; Kind.CUSTOMER, Kind.TASK -> "Chăm sóc"; else -> "Công nợ" }
    val list = visible.filter { item -> !item.archived && when (item.kind) {
        Kind.SESSION -> BusinessRules.active(item) && item.status != "Hoàn thành" && (item["start"].take(10) == today || item["coachId"].isBlank() || item["courtId"].isBlank())
        Kind.CUSTOMER -> item["followUp"].isNotBlank() && item["followUp"] <= today
        Kind.TASK -> item.status == "Cần làm" && item["due"] <= today
        Kind.RECEIVABLE, Kind.PAYABLE -> item.status == "Đã xác nhận" && item["due"] <= today && BusinessRules.balance(item, all) > 0
        else -> false
    } }.filter { it.id !in hiddenIds && (group == "Tất cả" || category(it) == group) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hiển thị nhắc việc trong app")
                Switch(enabled, { value -> enabled = value; vm.operation("Đã lưu tùy chọn") { vm.repository.setting("reminders-${user.id}", value.toString()) } })
            }
            ChoiceField("Nhóm nhắc việc", group, listOf("Tất cả", "Lịch học", "Chăm sóc", "Công nợ").map { it to it }, { group = it })
            Text("Nhắc việc được tính khi mở màn hình, không gửi SMS/push.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (!enabled || list.isEmpty()) item { EmptyState(if (!enabled) "Đã tắt nhắc việc" else "Không còn nhắc việc") }
        if (enabled) items(list, key = { it.id }) { item ->
            Column {
                Text("${category(item)} · ${if (item.id in readIds) "Đã đọc" else "Chưa đọc"}", color = Muted, style = MaterialTheme.typography.labelSmall)
                RecordCard(item, all, {
                    readIds = readIds + item.id
                    vm.operation("Đã đánh dấu đã đọc") { vm.repository.setting("read-${user.id}", readIds.joinToString("|")) }
                    navigate(Page("detail", item.kind, item.id))
                })
                TextButton(onClick = {
                    hiddenIds = hiddenIds + item.id
                    vm.operation("Đã ẩn nhắc việc") { vm.repository.setting("hidden-${user.id}", hiddenIds.joinToString("|")) }
                }) { Text("Ẩn nhắc việc này") }
            }
        }
    }
}