package com.dinklab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinklab.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Dashboard(user: Item, visible: List<Item>, all: List<Item>, navigate: (Page) -> Unit) {
    val today = LocalDate.now()
    val role = BusinessRules.role(user)
    val sessions = visible.filter { it.kind == Kind.SESSION && BusinessRules.active(it) && it["start"].take(10) == today.toString() }.sortedBy { it["start"] }
    val finance = role in setOf(Role.ADMIN, Role.OWNER)
    val vouchers = if (finance) visible.filter { it.kind == Kind.VOUCHER && it.status == "Đã xác nhận" && it["date"].take(7) == today.toString().take(7) } else emptyList()
    val income = vouchers.filter { it["direction"] == "Thu" }.sumOf { it.amount() }
    val expense = vouchers.filter { it["direction"] != "Thu" }.sumOf { it.amount() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("${today.format(DateTimeFormatter.ofPattern("dd 'tháng' MM, yyyy"))} · ${role.title}", color = Muted, style = MaterialTheme.typography.labelLarge)
            Text("Chào ${user.name.substringBefore(" ")},\nsẵn sàng cho hôm nay?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text(if (finance) "DÒNG TIỀN THÁNG NÀY" else "HOẠT ĐỘNG HÔM NAY", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium)
                    Text(if (finance) money(income - expense) else "${sessions.size} buổi học", color = Lime, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                    Text(if (finance) "Thực thu ${money(income)}  ·  Thực chi ${money(expense)}" else "Lịch lớp và công việc dành riêng cho bạn", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { navigate(Page(if (finance) "reports" else "calendar")) }) { Text(if (finance) "Xem báo cáo →" else "Mở lịch →", color = Lime) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Lịch hôm nay", "${sessions.size} buổi", Modifier.weight(1f), onClick = { navigate(Page("calendar")) })
                Metric("Khách cần chăm sóc", "${visible.count { it.kind == Kind.CUSTOMER && it["followUp"].isNotBlank() && it["followUp"] <= today.toString() && !it.archived }}", Modifier.weight(1f), onClick = { navigate(Page("reminders")) })
            }
        }
        if (finance) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Học phí còn phải thu", money(visible.filter { it.kind == Kind.RECEIVABLE && it.status == "Đã xác nhận" }.sumOf { BusinessRules.balance(it, all) }), Modifier.weight(1f), onClick = { navigate(Page("list", Kind.RECEIVABLE)) })
                Metric("Còn phải trả", money(visible.filter { it.kind == Kind.PAYABLE && it.status == "Đã xác nhận" }.sumOf { BusinessRules.balance(it, all) }), Modifier.weight(1f), onClick = { navigate(Page("list", Kind.PAYABLE)) })
            }
        }
        item {
            SectionTitle("Thao tác nhanh")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (BusinessRules.canCreate(user, Kind.CUSTOMER)) FilledTonalButton(onClick = { navigate(Page("form", Kind.CUSTOMER)) }) { Text("+ Khách hàng") }
                if (BusinessRules.canCreate(user, Kind.CLASS)) FilledTonalButton(onClick = { navigate(Page("form", Kind.CLASS)) }) { Text("+ Lớp học") }
                if (BusinessRules.canCreate(user, Kind.VOUCHER)) FilledTonalButton(onClick = { navigate(Page("form", Kind.VOUCHER)) }) { Text("+ Thu–chi") }
            }
        }
        val missing = visible.filter { it.kind == Kind.SESSION && BusinessRules.active(it) && it.status != "Hoàn thành" && (it["courtId"].isBlank() || it["coachId"].isBlank()) }
        if (missing.isNotEmpty()) item {
            MenuCard("${missing.size} buổi cần hoàn thiện", "Thiếu sân hoặc coach · nhấn để kiểm tra", "!") { navigate(Page("reminders")) }
        }
        item { SectionTitle("Trên sân hôm nay", "Xem lịch") { navigate(Page("calendar")) } }
        if (sessions.isEmpty()) item { EmptyState("Hôm nay chưa có lịch học", "Chọn Lịch để xem các ngày tiếp theo.") }
        items(sessions, key = { it.id }) { RecordCard(it, all, { navigate(Page("detail", Kind.SESSION, it.id)) }) }
        val tasks = visible.filter { it.kind == Kind.TASK && it.status == "Cần làm" && !it.archived }.sortedBy { it["due"] }.take(3)
        if (tasks.isNotEmpty()) item { SectionTitle("Việc cần ưu tiên") }
        items(tasks, key = { it.id }) { RecordCard(it, all, { navigate(Page("detail", Kind.TASK, it.id)) }) }
    }
}

@Composable
fun CalendarScreen(user: Item, visible: List<Item>, all: List<Item>, navigate: (Page) -> Unit) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var weekly by rememberSaveable { mutableStateOf(false) }
    var coach by rememberSaveable { mutableStateOf("") }
    var court by rememberSaveable { mutableStateOf("") }
    var clazz by rememberSaveable { mutableStateOf("") }
    val day = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    val start = if (weekly) day.minusDays((day.dayOfWeek.value - 1).toLong()) else day
    val end = if (weekly) start.plusDays(6) else day
    val list = visible.filter { it.kind == Kind.SESSION && !it.archived && it["start"].take(10) >= start.toString() && it["start"].take(10) <= end.toString() &&
        (coach.isBlank() || it["coachId"] == coach) && (court.isBlank() || it["courtId"] == court) && (clazz.isBlank() || it["classId"] == clazz) }.sortedBy { it["start"] }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!weekly, { weekly = false }, label = { Text("Theo ngày") })
                FilterChip(weekly, { weekly = true }, label = { Text("Theo tuần") })
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = { date = day.minusDays(if (weekly) 7 else 1).toString() }) { Icon(Icons.Outlined.ChevronLeft, "Trước") }
                OutlinedTextField(date, { date = it }, label = { Text("Ngày yyyy-MM-dd") }, modifier = Modifier.weight(1f), singleLine = true,
                    isError = runCatching { LocalDate.parse(date) }.isFailure)
                IconButton(onClick = { date = day.plusDays(if (weekly) 7 else 1).toString() }) { Icon(Icons.Outlined.ChevronRight, "Sau") }
            }
            ChoiceField("Lớp học", clazz, listOf("" to "Tất cả lớp") + visible.filter { it.kind == Kind.CLASS }.map { it.id to it.name }, { clazz = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField("Coach", coach, listOf("" to "Tất cả") + visible.filter { it.kind == Kind.COACH }.map { it.id to it.name }, { coach = it }, Modifier.weight(1f))
                ChoiceField("Sân", court, listOf("" to "Tất cả") + visible.filter { it.kind == Kind.COURT }.map { it.id to it.name }, { court = it }, Modifier.weight(1f))
            }
            SectionTitle("${Dates.pretty(start.toString())}${if (weekly) " – ${Dates.pretty(end.toString())}" else ""} · ${list.size} buổi")
            if (BusinessRules.canCreate(user, Kind.SESSION)) OutlinedButton(onClick = { navigate(Page("form", Kind.SESSION)) }) { Text("+ Thêm buổi học") }
        }
        if (list.isEmpty()) item { EmptyState("Không có buổi học trong khoảng này") }
        items(list, key = { it.id }) { RecordCard(it, all, { navigate(Page("detail", Kind.SESSION, it.id)) }) }
    }
}

@Composable
fun ReportsScreen(user: Item, visible: List<Item>, all: List<Item>, navigate: (Page) -> Unit) {
    var from by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var section by rememberSaveable { mutableStateOf("Dòng tiền") }
    val role = BusinessRules.role(user)
    val available = when (role) {
        Role.ADMIN, Role.OWNER -> listOf("Dòng tiền", "Lớp học", "Công nợ", "Coach", "Sân", "Sale", "Marketing")
        Role.SALE -> listOf("Sale", "Công nợ")
        Role.MARKETING -> listOf("Marketing")
        Role.COACH -> listOf("Coach")
    }
    if (section !in available) section = available.first()
    val valid = runCatching { LocalDate.parse(from) <= LocalDate.parse(to) }.getOrDefault(false)
    fun inPeriod(value: String) = valid && value.take(10) >= from && value.take(10) <= to
    val vouchers = visible.filter { it.kind == Kind.VOUCHER && it.status == "Đã xác nhận" && inPeriod(it["date"]) }
    val income = vouchers.filter { it["direction"] == "Thu" }.sumOf { it.amount() }
    val expense = vouchers.filter { it["direction"] != "Thu" }.sumOf { it.amount() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("Từ yyyy-MM-dd") }, singleLine = true, modifier = Modifier.weight(1f), isError = !valid)
                OutlinedTextField(to, { to = it }, label = { Text("Đến yyyy-MM-dd") }, singleLine = true, modifier = Modifier.weight(1f), isError = !valid)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { FilterChip(section == it, { section = it }, label = { Text(it) }) }
            }
            Text("Dữ liệu demo · Chạm từng mục để xem dữ liệu nguồn", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        when (section) {
            "Dòng tiền" -> {
                item { Metric("Chênh lệch thực thu – thực chi", money(income - expense), Modifier.fillMaxWidth(), true) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Thực thu", money(income), Modifier.weight(1f)); Metric("Thực chi / hoàn tiền", money(expense), Modifier.weight(1f)) } }
                item { Text("Không bao gồm phiếu nháp/đã đảo. Chênh lệch dòng tiền không phải lợi nhuận.", color = Muted) }
                items(vouchers) { RecordCard(it, all, { navigate(Page("detail", it.kind, it.id)) }) }
            }
            "Lớp học" -> items(visible.filter { it.kind == Kind.CLASS && !it.archived }) { clazz ->
                val completed = all.filter { it.kind == Kind.SESSION && it["classId"] == clazz.id && it.status == "Hoàn thành" && inPeriod(it["end"]) }.map { it["originalId"].ifBlank { it.id } }.distinct().size
                val tuition = all.filter { it.kind == Kind.ENROLLMENT && it["classId"] == clazz.id && it.status != "Nháp" }.sumOf { it.amount("fee") - it.amount("discount") }
                val revenue = if (clazz.amount("sessions") == 0L) 0L else tuition * completed / clazz.amount("sessions")
                val costs = all.filter { p -> p.kind == Kind.PAYABLE && p.status == "Đã xác nhận" && p["classId"] == clazz.id && all.any { s -> s.id == p["sessionId"] && s.status == "Hoàn thành" && inPeriod(s["end"]) } }.sumOf { it.amount() }
                MenuCard(clazz.name, "Học phí ghi nhận ${money(revenue)}\nChi phí trực tiếp ${money(costs)} · Kết quả ${money(revenue - costs)}", "LH") { navigate(Page("detail", Kind.CLASS, clazz.id)) }
            }
            "Công nợ" -> {
                item { Text("Số dư hiện tại; lọc theo hạn thanh toán trong kỳ. Không phải số dư lịch sử tại ngày kết thúc.", color = Muted) }
                items(visible.filter { it.kind in setOf(Kind.RECEIVABLE, Kind.PAYABLE) && it.status == "Đã xác nhận" && inPeriod(it["due"]) && BusinessRules.balance(it, all) > 0 }) { RecordCard(it, all, { navigate(Page("detail", it.kind, it.id)) }) }
            }
            "Coach" -> items(visible.filter { it.kind == Kind.COACH }) { coach ->
                val sessions = visible.filter { it.kind == Kind.SESSION && it["coachId"] == coach.id && it.status == "Hoàn thành" && inPeriod(it["end"]) }
                val debts = visible.filter { it.kind == Kind.PAYABLE && it["coachId"] == coach.id && it.status == "Đã xác nhận" && sessions.any { s -> s.id == it["sessionId"] } }
                MenuCard(coach.name, "${sessions.size} buổi · Phát sinh ${money(debts.sumOf { it.amount() })}\nĐã trả ${money(debts.sumOf { BusinessRules.settled(it, all) })} · Còn ${money(debts.sumOf { BusinessRules.balance(it, all) })}", "HL") { navigate(Page("detail", Kind.COACH, coach.id)) }
            }
            "Sân" -> items(visible.filter { it.kind == Kind.COURT }) { court ->
                val bookings = visible.filter { it.kind == Kind.BOOKING && it["courtId"] == court.id && it.status == "Hoàn thành" && inPeriod(it["start"]) }
                val hours = bookings.sumOf { BusinessRules.hours(it) }
                val days = if (valid) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) + 1 else 0
                val capacity = (all.find { it.id == court["venueId"] }?.amount("availableHours") ?: 0) * days
                val usage = if (capacity > 0) hours * 100 / capacity else 0.0
                MenuCard(court.name, "${"%.1f".format(hours)} giờ sử dụng · Hiệu suất ${"%.1f".format(usage)}%\nChi phí ${money(bookings.sumOf { it.amount() })}", "ST") { navigate(Page("detail", Kind.COURT, court.id)) }
            }
            "Sale" -> items(visible.filter { it.kind == Kind.STAFF && it["role"] == "SALE" }) { sale ->
                val customers = all.filter { it.kind == Kind.CUSTOMER && it["saleId"] == sale.id }
                val enrollments = all.filter { it.kind == Kind.ENROLLMENT && it["saleId"] == sale.id && inPeriod(it["joined"]) && it.status != "Nháp" }
                val obligations = all.filter { p -> p.kind == Kind.RECEIVABLE && enrollments.any { it.id == p["enrollmentId"] } }
                val paid = all.filter { it.kind == Kind.VOUCHER && it.status == "Đã xác nhận" && it["direction"] == "Thu" && inPeriod(it["date"]) }.sumOf { v -> BusinessRules.allocations(v).filterKeys { id -> obligations.any { it.id == id } }.values.sum() }
                MenuCard(sale.name, "${customers.size} khách đang phụ trách · ${enrollments.size} đăng ký trong kỳ\nHọc phí thu của các đăng ký trong kỳ: ${money(paid)}", "SL") { navigate(Page("list", Kind.CUSTOMER, defaults = mapOf("saleId" to sale.id))) }
            }
            "Marketing" -> {
                item { Text("Khách theo nguồn là tập hiện tại; đăng ký và chi phí lọc theo kỳ. Tỷ lệ chốt = khách đã chốt / tổng khách của nguồn.", color = Muted) }
                items(visible.filter { it.kind == Kind.SOURCE }) { source ->
                    val customers = all.filter { it.kind == Kind.CUSTOMER && it["sourceId"] == source.id && !it.archived }
                    val won = customers.count { it.status == "Đã chốt" }
                    val campaigns = all.filter { it.kind == Kind.CAMPAIGN && it["sourceId"] == source.id }
                    val costs = all.filter { p -> p.kind == Kind.PAYABLE && p.status == "Đã xác nhận" && inPeriod(p["due"]) && campaigns.any { it.id == p["campaignId"] } }.sumOf { it.amount() }
                    MenuCard(source.name, "${customers.size} khách · Đã chốt $won · ${if (customers.isEmpty()) 0 else won * 100 / customers.size}%\nChi phí ${money(costs)} · CPL ${money(if (customers.isEmpty()) 0 else costs / customers.size)}", "MK") { navigate(Page("detail", Kind.SOURCE, source.id)) }
                }
            }
        }
    }
}