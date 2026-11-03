package com.dinklab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinklab.app.domain.*
import java.text.NumberFormat
import java.util.Locale

val Forest = Color(0xFF183D32)
val Lime = Color(0xFFD7EE83)
val Canvas = Color(0xFFF5F7F3)
val Muted = Color(0xFF6C7971)
val MoneyFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
fun money(amount: Long): String = "${MoneyFormat.format(amount)} ₫"

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when {
        status in setOf("Đã hủy", "Đã đảo", "Quá hạn", "Vắng không phép", "Đã rút") -> Color(0xFFAD443A)
        status in setOf("Nháp", "Hoãn", "Tạm dừng", "Chưa xác nhận", "Thanh toán một phần", "Chưa thanh toán", "Giữ chỗ") -> Color(0xFF936211)
        else -> Forest
    }
    Text(status, color = color, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(color.copy(alpha = .09f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, dark: Boolean = false, onClick: () -> Unit = {}) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (dark) Forest else Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = if (dark) Color.White.copy(alpha = .75f) else Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(9.dp))
            Text(value, color = if (dark) Lime else Forest, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

fun itemSubtitle(item: Item, all: List<Item>): String {
    fun name(key: String) = all.find { it.id == item[key] }?.name.orEmpty()
    return when (item.kind) {
        Kind.CUSTOMER -> "${item["phone"]} · ${name("saleId").ifBlank { "Chưa phân công" }}"
        Kind.CLASS -> "${item["code"]} · ${all.count { it.kind == Kind.ENROLLMENT && it["classId"] == item.id && BusinessRules.active(it) }}/${item["capacity"]} học viên"
        Kind.SESSION -> "${Dates.pretty(item["start"])} · ${name("courtId").ifBlank { "Chưa có sân" }}"
        Kind.ENROLLMENT -> "${name("customerId")} · ${name("classId")}"
        Kind.RECEIVABLE, Kind.PAYABLE -> "${money(item.amount())} · Còn ${money(BusinessRules.balance(item, all))}"
        Kind.VOUCHER -> "${item["direction"]} ${money(item.amount())} · ${Dates.pretty(item["date"])}"
        Kind.COURT -> "${name("venueId")} · ${money(item.amount("price"))}/giờ"
        Kind.COACH -> "${item["specialty"]} · ${money(item.amount("rate"))}/buổi"
        Kind.BOOKING -> "${Dates.pretty(item["start"])} · ${money(item.amount())}"
        Kind.TASK -> "Hạn ${Dates.pretty(item["due"])} · ${name("ownerId")}"
        Kind.CARE -> "${name("customerId")} · ${item["result"]}"
        Kind.STAFF -> runCatching { Role.valueOf(item["role"]).title }.getOrDefault(item["role"])
        Kind.CAMPAIGN -> "${Dates.pretty(item["startDate"])} · Ngân sách ${money(item.amount("budget"))}"
        Kind.COURSE -> "${item["sessions"]} buổi · ${money(item.amount("fee"))}"
        Kind.ATTENDANCE -> name("sessionId")
        else -> item["address"].ifBlank { item["note"].ifBlank { item.kind.title } }
    }
}

@Composable
fun RecordCard(item: Item, all: List<Item>, onClick: () -> Unit, hideSubtitle: Boolean = false) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).background(Forest.copy(alpha = .07f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text(item.kind.symbol, color = Forest, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!hideSubtitle) Text(itemSubtitle(item, all), style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(7.dp))
                StatusBadge(if (item.archived) "Đã lưu trữ" else if (item.kind in setOf(Kind.RECEIVABLE, Kind.PAYABLE)) BusinessRules.paymentStatus(item, all) else item.status)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Xem chi tiết", tint = Muted)
        }
    }
}

@Composable
fun EmptyState(title: String = "Chưa có dữ liệu", subtitle: String = "Thêm bản ghi mới hoặc thay đổi bộ lọc để bắt đầu.") {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Inbox, null, Modifier.size(52.dp), tint = Muted)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(value.ifBlank { "Chưa cập nhật" }, color = if (onClick != null) Forest else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp))
    }
}