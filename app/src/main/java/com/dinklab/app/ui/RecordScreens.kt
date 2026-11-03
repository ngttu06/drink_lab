package com.dinklab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dinklab.app.AppViewModel
import com.dinklab.app.domain.*
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun RecordList(page: Page, user: Item, visible: List<Item>, all: List<Item>, navigate: (Page) -> Unit) {
    val kind = page.kind ?: return
    var query by rememberSaveable(kind) { mutableStateOf("") }
    var status by rememberSaveable(kind) { mutableStateOf(page.filter) }
    var archived by rememberSaveable(kind) { mutableStateOf(false) }
    var sort by rememberSaveable(kind) { mutableStateOf("Mới cập nhật") }
    var from by rememberSaveable(kind) { mutableStateOf("") }
    var to by rememberSaveable(kind) { mutableStateOf("") }
    var showDates by rememberSaveable(kind) { mutableStateOf(false) }
    val protected = BusinessRules.protectedFields(user, kind)
    val list = visible.filter { item ->
        item.kind == kind && item.archived == archived &&
            (query.isBlank() || item.name.contains(query, true) || item.values.filterKeys { it !in protected }.values.any { value -> value.contains(query, true) }) &&
            page.defaults.all { (key, value) -> item[key] == value } &&
            (status.isBlank() || item.status == status || (kind in setOf(Kind.PAYABLE, Kind.RECEIVABLE) && BusinessRules.paymentStatus(item, all) == status)) &&
            run {
                val date = listOf("date", "start", "due", "startDate", "joined", "at").firstNotNullOfOrNull { item[it].takeIf(String::isNotBlank) }?.take(10).orEmpty()
                (from.isBlank() || date >= from) && (to.isBlank() || date <= to)
            }
    }.let { result -> when (sort) {
        "Tên A–Z" -> result.sortedBy { it.name }
        "Số tiền giảm dần" -> result.sortedByDescending { it.amount() }
        else -> result.sortedByDescending { it.updatedAt }
    } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(query, { query = it }, label = { Text("Tìm ${kind.singular}") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceField("Trạng thái", status, listOf("" to "Tất cả") + (Schema[kind].statuses + if (kind in setOf(Kind.PAYABLE, Kind.RECEIVABLE)) listOf("Quá hạn", "Chưa thanh toán", "Thanh toán một phần", "Đã thanh toán") else emptyList()).distinct().map { it to it }, { status = it }, Modifier.weight(1f))
                    ChoiceField("Sắp xếp", sort, listOf("Mới cập nhật", "Tên A–Z", "Số tiền giảm dần").map { it to it }, { sort = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(archived, { archived = !archived }, label = { Text("Đã lưu trữ") })
                    FilterChip(showDates, { showDates = !showDates }, label = { Text("Khoảng ngày") })
                }
                if (showDates) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(from, { from = it }, label = { Text("Từ yyyy-MM-dd") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(to, { to = it }, label = { Text("Đến yyyy-MM-dd") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                Text("${list.size} kết quả", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
            }
            if (list.isEmpty()) item { EmptyState(if (query.isNotBlank() || status.isNotBlank()) "Không tìm thấy kết quả" else "Chưa có ${kind.singular}") }
            items(list, key = { it.id }) { item -> RecordCard(item, all, { navigate(Page("detail", kind, item.id)) }, BusinessRules.role(user) == Role.COACH && kind in setOf(Kind.CUSTOMER, Kind.ENROLLMENT)) }
        }
        if (BusinessRules.canCreate(user, kind)) FloatingActionButton(onClick = { navigate(Page("form", kind, defaults = page.defaults)) }, containerColor = Lime,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Outlined.Add, "Thêm ${kind.singular}") }
    }
}

@Composable
fun ChoiceField(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            Text(options.find { it.first == value }?.second ?: value.ifBlank { "Chọn…" }, modifier = Modifier.weight(1f), maxLines = 2)
            Icon(Icons.Outlined.ExpandMore, null)
        }
    }
    if (open) {
        var search by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
            Column {
                OutlinedTextField(search, { search = it }, label = { Text("Tìm kiếm") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options.filter { it.second.contains(search, true) }) { (id, name) ->
                        TextButton(onClick = { onChange(id); open = false }, modifier = Modifier.fillMaxWidth()) { Text(name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("Đóng") } })
    }
}

fun initialValues(kind: Kind, defaults: Map<String, String>, all: List<Item>, user: Item): Map<String, String> {
    val values = Schema[kind].fields.associate { field -> field.key to when (field.input) {
        Input.MONEY -> "0"
        Input.DATE -> Dates.day()
        Input.DATETIME -> if (field.key == "end") LocalDateTime.now().plusMinutes(90).withSecond(0).withNano(0).toString() else Dates.now()
        Input.SELECT -> field.options.firstOrNull().orEmpty()
        else -> ""
    } }.toMutableMap()
    values.putAll(mapOf("code" to "${kind.name.take(3)}-${System.currentTimeMillis().toString().takeLast(6)}", "sessions" to "10", "minutes" to "90", "capacity" to "4", "weekdays" to "1,3", "startTime" to "18:00", "fromTime" to "06:00", "toTime" to "22:00", "availableHours" to "16"))
    if (kind in setOf(Kind.CARE, Kind.TASK)) values["ownerId"] = user.id
    if (kind == Kind.CUSTOMER && BusinessRules.role(user) == Role.SALE) values["saleId"] = user.id
    values.putAll(defaults)
    val session = all.find { it.id == values["sessionId"] }
    if (kind == Kind.BOOKING && session != null) {
        values.putAll(mapOf("courtId" to session["courtId"], "start" to session["start"], "end" to session["end"]))
        val price = all.find { it.id == session["courtId"] }?.amount("price") ?: 0
        values["price"] = price.toString(); values["amount"] = (price * BusinessRules.hours(session)).toLong().toString()
    }
    if (kind == Kind.VOUCHER) {
        val target = all.find { it.id == values["receivableId"] || it.id == values["payableId"] }
        if (target != null) {
            values["direction"] = if (target.kind == Kind.RECEIVABLE) "Thu" else "Chi"
            values["amount"] = BusinessRules.balance(target, all).coerceAtLeast(0).toString()
            values["party"] = target["payee"].ifBlank { all.find { it.id == target["customerId"] }?.name ?: target.name }
        }
    }
    return values.filterKeys { key -> Schema[kind].fields.any { it.key == key } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordForm(page: Page, vm: AppViewModel, user: Item, all: List<Item>, back: () -> Unit) {
    val kind = page.kind ?: return
    val existing = all.find { it.id == page.id }
    val draftId = rememberSaveable { page.id ?: java.util.UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var status by rememberSaveable { mutableStateOf(existing?.status ?: Schema[kind].statuses.first()) }
    val initial = remember { existing?.values ?: initialValues(kind, page.defaults, all, user) }
    var values by rememberSaveable { mutableStateOf<Map<String, String>>(HashMap(initial)) }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var leave by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val dirty = name != (existing?.name ?: "") || values != initial || status != (existing?.status ?: Schema[kind].statuses.first())
    val requestBack = { if (dirty) leave = true else back() }
    BackHandler(onBack = requestBack)
    val canEdit = if (existing == null) BusinessRules.canCreate(user, kind) else BusinessRules.canEdit(user, existing, all)
    val locked = existing != null && ((kind == Kind.VOUCHER && existing.status != "Nháp") || (kind == Kind.SESSION && existing.status == "Hoàn thành"))
    val hidden = BusinessRules.protectedFields(user, kind)
    val editableFields = if (kind == Kind.STAFF && BusinessRules.role(user) != Role.ADMIN) setOf("phone", "email") else null
    Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "Thêm ${kind.singular}" else "Sửa ${kind.singular}") }, navigationIcon = {
        IconButton(onClick = requestBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") }
    }) }, bottomBar = {
        Surface(shadowElevation = 4.dp) {
            Button(onClick = {
                val item = Item(draftId, kind, name.trim(), status, values, existing?.archived ?: false, existing?.createdAt ?: System.currentTimeMillis())
                error = runCatching { BusinessRules.validate(item, all, existing) }.exceptionOrNull()?.message
                if (error == null) vm.save(item, reason, back)
            }, enabled = !busy && canEdit && !locked, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(if (busy) "Đang lưu…" else "Lưu ${kind.singular}") }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("THÔNG TIN ${kind.title.uppercase()}", color = Muted, style = MaterialTheme.typography.labelLarge)
                if (locked) Text("Bản ghi đã khóa. Không thể sửa trực tiếp.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Tên / tiêu đề *") }, modifier = Modifier.fillMaxWidth(), enabled = !locked && canEdit)
                ChoiceField("Trạng thái", status, Schema[kind].statuses.filterNot { kind == Kind.VOUCHER && it == "Đã đảo" }.map { it to it }, { status = it }, enabled = !locked && canEdit && !(kind == Kind.STAFF && BusinessRules.role(user) != Role.ADMIN))
            }
            items(Schema[kind].fields.filter { it.key !in hidden }, key = { it.key }) { field ->
                val enabled = !busy && !locked && canEdit && (editableFields == null || field.key in editableFields)
                val value = values[field.key].orEmpty()
                val change: (String) -> Unit = { new ->
                    values = HashMap(values).apply {
                        put(field.key, new)
                        if (kind == Kind.CLASS && field.key == "courseId" && existing == null) {
                            all.find { it.id == new }?.let { course -> listOf("sessions", "minutes", "capacity").forEach { put(it, course[it]) } }
                        }
                        if (kind == Kind.SESSION && field.key == "coachId") put("coachCost", all.find { it.id == new }?.get("rate") ?: "0")
                        if (kind == Kind.ENROLLMENT && field.key == "classId" && existing == null) {
                            val clazz = all.find { it.id == new }; val course = all.find { it.id == clazz?.get("courseId") }
                            put("fee", course?.get("fee") ?: "0")
                        }
                        if (kind == Kind.ENROLLMENT && field.key == "customerId" && existing == null) put("saleId", all.find { it.id == new }?.get("saleId").orEmpty())
                    }
                    error = null
                }
                val label = field.label + if (field.required) " *" else ""
                when (field.input) {
                    Input.SELECT -> ChoiceField(label, value, field.options.map { option -> option to if (field.key == "role") runCatching { Role.valueOf(option).title }.getOrDefault(option) else option }, change, enabled = enabled)
                    Input.REFERENCE -> {
                        val options = all.filter { candidate -> candidate.kind == field.reference && (!candidate.archived || candidate.id == value) && BusinessRules.visible(user, candidate, all) &&
                            (field.key != "saleId" || candidate["role"] == Role.SALE.name) &&
                            (kind != Kind.COACH || field.key != "staffId" || candidate["role"] == Role.COACH.name)
                        }.map { it.id to "${it.name} · ${it.id.take(8)}" }
                        ChoiceField(label, value, listOf("" to "Chưa chọn") + options, change, enabled = enabled)
                    }
                    else -> OutlinedTextField(value, change, label = { Text(label) }, supportingText = { if (field.hint.isNotBlank()) Text(field.hint) },
                        singleLine = field.input != Input.MULTILINE, minLines = if (field.input == Input.MULTILINE) 2 else 1,
                        keyboardOptions = KeyboardOptions(keyboardType = when (field.input) {
                            Input.MONEY, Input.NUMBER -> KeyboardType.Number
                            Input.PHONE -> KeyboardType.Phone
                            Input.EMAIL -> KeyboardType.Email
                            else -> KeyboardType.Text
                        }), enabled = enabled, modifier = Modifier.fillMaxWidth())
                }
            }
            if (existing != null) item {
                OutlinedTextField(reason, { reason = it }, label = { Text("Lý do điều chỉnh / thay đổi") }, minLines = 2, modifier = Modifier.fillMaxWidth(), enabled = !locked)
            }
            if (kind == Kind.CUSTOMER && values["phone"].orEmpty().isNotBlank() && all.any { it.kind == Kind.CUSTOMER && it.id != draftId && it["phone"] == values["phone"] }) item {
                Text("Lưu ý: số điện thoại đã xuất hiện ở một hồ sơ khác.", color = MaterialTheme.colorScheme.error)
            }
            if (kind == Kind.SESSION && values["classId"].orEmpty().isNotBlank()) item {
                val start = values["start"].orEmpty(); val end = values["end"].orEmpty()
                val enrolled = all.filter { it.kind == Kind.ENROLLMENT && it["classId"] == values["classId"] && BusinessRules.active(it) }.map { it["customerId"] }.toSet()
                val conflict = all.firstOrNull { other -> other.kind == Kind.SESSION && other.id != draftId && other["classId"] != values["classId"] && BusinessRules.active(other) &&
                    all.any { it.kind == Kind.ENROLLMENT && it["classId"] == other["classId"] && it["customerId"] in enrolled && BusinessRules.active(it) } &&
                    runCatching { BusinessRules.overlaps(start, end, other["start"], other["end"]) }.getOrDefault(false) }
                if (conflict != null) Text("Cảnh báo: có học viên trùng lịch với ${conflict.name}", color = MaterialTheme.colorScheme.error)
            }
            error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("Bỏ thay đổi chưa lưu?") },
        text = { Text("Nội dung đang nhập sẽ không được lưu.") },
        confirmButton = { TextButton(onClick = { leave = false; back() }) { Text("Bỏ thay đổi") } },
        dismissButton = { TextButton(onClick = { leave = false }) { Text("Tiếp tục sửa") } })
}