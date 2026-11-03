package com.dinklab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dinklab.app.AppViewModel
import com.dinklab.app.domain.*

data class Page(val type: String, val kind: Kind? = null, val id: String? = null, val defaults: Map<String, String> = emptyMap(), val filter: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DinkApp(vm: AppViewModel) {
    val all by vm.items.collectAsStateWithLifecycle()
    val actor by vm.actor.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val stack = remember { mutableStateListOf(Page("home")) }
    val page = stack.last()
    val navigate: (Page) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    LaunchedEffect(actor?.id) { stack.clear(); stack.add(Page("home")) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Forest, tertiary = Color(0xFF63803D),
        background = Canvas, surface = Canvas, onPrimary = Color.White, primaryContainer = Lime, onPrimaryContainer = Forest),
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp))) {
        if (!ready) {
            Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            return@MaterialTheme
        }
        if (actor == null) {
            LoginScreen(all, vm::login)
            return@MaterialTheme
        }
        val user = actor!!
        val visible = all.filter { BusinessRules.visible(user, it, all) }
        val root = page.type in setOf("home", "calendar", "more") || (page.type == "list" && page.kind in setOf(Kind.CLASS, Kind.CUSTOMER) && stack.size == 1)
        if (page.type != "form") BackHandler(enabled = stack.size > 1, onBack = back)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (page.type != "form") TopAppBar(
                    title = { Column {
                        Text(when (page.type) {
                            "home" -> "DinkApp"
                            "calendar" -> "Lịch hoạt động"
                            "more" -> "Không gian làm việc"
                            "reports" -> "Báo cáo & thống kê"
                            "audit" -> "Nhật ký hoạt động"
                            "settings" -> "Cài đặt demo"
                            "reminders" -> "Nhắc việc"
                            "detail" -> page.kind?.title ?: "Chi tiết"
                            else -> page.kind?.title ?: "DinkApp"
                        }, fontWeight = FontWeight.Bold)
                        if (root) Text("DEMO · Dữ liệu cục bộ", style = MaterialTheme.typography.labelSmall, color = Muted)
                    } },
                    navigationIcon = { if (stack.size > 1) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") } },
                    actions = {
                        IconButton(onClick = { navigate(Page("reminders")) }) { Icon(Icons.Outlined.NotificationsNone, "Nhắc việc") }
                        TextButton(onClick = { navigate(Page("settings")) }) { Text(user.name.substringBefore(" ").take(8)) }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
                )
            },
            bottomBar = {
                if (root) NavigationBar(containerColor = Color.White) {
                    val tabs = listOf(Triple("home", "Tổng quan", Icons.Outlined.SpaceDashboard),
                        Triple("calendar", "Lịch", Icons.Outlined.CalendarMonth), Triple("classes", "Lớp học", Icons.Outlined.School),
                        Triple("customers", "Khách hàng", Icons.Outlined.PeopleOutline), Triple("more", "Thêm", Icons.Outlined.GridView))
                    tabs.forEach { (key, title, icon) ->
                        val selected = if (key == "classes") page.kind == Kind.CLASS else if (key == "customers") page.kind == Kind.CUSTOMER else page.type == key
                        NavigationBarItem(selected, onClick = {
                            stack.clear()
                            stack.add(when (key) { "classes" -> Page("list", Kind.CLASS); "customers" -> Page("list", Kind.CUSTOMER); else -> Page(key) })
                        }, icon = { Icon(icon, title) }, label = { Text(title, maxLines = 1) })
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page.type) {
                    "home" -> Dashboard(user, visible, all, navigate)
                    "list" -> RecordList(page, user, visible, all, navigate)
                    "detail" -> DetailScreen(page, vm, user, visible, all, navigate, back)
                    "form" -> RecordForm(page, vm, user, all, back)
                    "calendar" -> CalendarScreen(user, visible, all, navigate)
                    "more" -> MoreScreen(user, navigate)
                    "reports" -> ReportsScreen(user, visible, all, navigate)
                    "audit" -> AuditScreen(vm, user)
                    "settings" -> SettingsScreen(vm, user, navigate)
                    "reminders" -> RemindersScreen(vm, user, visible, all, navigate)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Forest)
            }
        }
    }
}

@Composable
private fun LoginScreen(all: List<Item>, login: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(40.dp))
            Text("DinkApp", style = MaterialTheme.typography.headlineLarge, color = Forest, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Text("Vận hành gọn gàng.\nTập trung vào sân đấu.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Chọn tài khoản để khám phá ứng dụng", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
            StatusBadge("DEMO · Không thanh toán thật")
            Spacer(Modifier.height(16.dp))
        }
        items(all.filter { it.kind == Kind.STAFF && !it.archived && it.status == "Hoạt động" }.sortedWith(compareBy<Item> { if (it.id == "admin") 0 else 1 }.thenBy { it.name }), key = { it.id }) { user ->
            RecordCard(user, all, { login(user.id) })
        }
        item { Text("Dữ liệu hoàn toàn giả lập và chỉ lưu trên thiết bị này.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable
private fun MoreScreen(user: Item, navigate: (Page) -> Unit) {
    val role = BusinessRules.role(user)
    val groups = listOf(
        "Khách hàng & tăng trưởng" to listOf(Kind.CUSTOMER, Kind.CARE, Kind.TASK, Kind.SOURCE, Kind.CAMPAIGN),
        "Đào tạo & vận hành" to listOf(Kind.COURSE, Kind.CLASS, Kind.ENROLLMENT, Kind.SESSION, Kind.ATTENDANCE, Kind.COACH),
        "Sân tập" to listOf(Kind.VENUE, Kind.COURT, Kind.RATE, Kind.BOOKING),
        "Tài chính" to listOf(Kind.RECEIVABLE, Kind.PAYABLE, Kind.VOUCHER),
        "Quản trị" to listOf(Kind.STAFF)
    )
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(user.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(role.title, color = Lime, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        groups.forEach { (title, kinds) ->
            val allowed = kinds.filter { moduleAvailable(role, it) }
            if (allowed.isNotEmpty()) {
                item { SectionTitle(title) }
                items(allowed) { kind -> MenuCard(kind.title, "Quản lý ${kind.singular}", kind.symbol) { navigate(Page("list", kind)) } }
            }
        }
        item { SectionTitle("Tổng hợp & hệ thống") }
        item { MenuCard("Báo cáo", "Chỉ số từ dữ liệu hiện tại", "BC") { navigate(Page("reports")) } }
        if (role in setOf(Role.ADMIN, Role.OWNER)) item { MenuCard("Nhật ký hoạt động", "Lịch sử thay đổi dữ liệu", "NK") { navigate(Page("audit")) } }
        item { MenuCard("Cài đặt & dữ liệu demo", "Hồ sơ, tài khoản, reset", "CĐ") { navigate(Page("settings")) } }
    }
}

fun moduleAvailable(role: Role, kind: Kind): Boolean = when (role) {
    Role.ADMIN, Role.OWNER -> true
    Role.SALE -> kind in setOf(Kind.CUSTOMER, Kind.CARE, Kind.TASK, Kind.SOURCE, Kind.CAMPAIGN, Kind.CLASS, Kind.COURSE, Kind.SESSION, Kind.ENROLLMENT, Kind.RECEIVABLE, Kind.STAFF)
    Role.MARKETING -> kind in setOf(Kind.SOURCE, Kind.CAMPAIGN, Kind.PAYABLE, Kind.STAFF)
    Role.COACH -> kind in setOf(Kind.CLASS, Kind.SESSION, Kind.ENROLLMENT, Kind.ATTENDANCE, Kind.COACH, Kind.CUSTOMER, Kind.COURT, Kind.VENUE, Kind.PAYABLE, Kind.STAFF)
}

@Composable
fun MenuCard(title: String, subtitle: String, symbol: String, click: () -> Unit) {
    Card(onClick = click, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
        }
    }
}