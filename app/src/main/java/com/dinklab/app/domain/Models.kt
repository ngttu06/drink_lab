package com.dinklab.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class Role(val title: String) {
    ADMIN("Admin"), SALE("Sale"), MARKETING("Marketing"), COACH("Coach"), OWNER("Chủ doanh nghiệp")
}

enum class Kind(val title: String, val singular: String, val symbol: String) {
    STAFF("Nhân sự", "nhân sự", "NS"), CUSTOMER("Khách hàng", "khách hàng", "KH"),
    CARE("Chăm sóc khách", "lần chăm sóc", "CS"), TASK("Việc cần làm", "công việc", "CV"),
    SOURCE("Nguồn khách", "nguồn khách", "NK"), CAMPAIGN("Chiến dịch", "chiến dịch", "CD"),
    COURSE("Khóa học", "khóa học", "KĐ"), CLASS("Lớp học", "lớp học", "LH"),
    ENROLLMENT("Đăng ký học", "đăng ký", "ĐK"), SESSION("Buổi học", "buổi học", "BH"),
    ATTENDANCE("Điểm danh", "điểm danh", "ĐD"), VENUE("Cụm sân", "cụm sân", "SC"),
    COURT("Sân tập", "sân", "ST"), RATE("Bảng giá sân", "mức giá", "BG"),
    BOOKING("Đặt sân", "đặt sân", "ĐS"), COACH("Coach", "coach", "HL"),
    RECEIVABLE("Khoản phải thu", "khoản phải thu", "PT"), PAYABLE("Khoản phải trả", "khoản phải trả", "PC"),
    VOUCHER("Phiếu thu–chi", "phiếu", "TC")
}

data class Item(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind,
    val name: String,
    val status: String,
    val values: Map<String, String> = emptyMap(),
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    operator fun get(key: String): String = values[key].orEmpty()
    fun amount(key: String = "amount"): Long = this[key].toLongOrNull() ?: 0L
    fun with(vararg fields: Pair<String, String>): Item = copy(values = values + fields)
}

enum class Input { TEXT, PHONE, EMAIL, NUMBER, MONEY, DATE, DATETIME, SELECT, REFERENCE, MULTILINE }

data class Field(
    val key: String,
    val label: String,
    val input: Input = Input.TEXT,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val reference: Kind? = null,
    val hint: String = ""
)

data class Module(val kind: Kind, val fields: List<Field>, val statuses: List<String>)

object Dates {
    val display: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    fun day(): String = LocalDate.now().toString()
    fun now(): String = LocalDateTime.now().withSecond(0).withNano(0).toString()
    fun pretty(value: String): String = runCatching {
        if (value.length <= 10) LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        else LocalDateTime.parse(value).format(display)
    }.getOrDefault(value)
}

object Schema {
    private fun text(key: String, label: String, required: Boolean = false) = Field(key, label, required = required)
    private fun ref(key: String, label: String, kind: Kind, required: Boolean = false) =
        Field(key, label, Input.REFERENCE, required, reference = kind)
    private fun money(key: String, label: String, required: Boolean = false) = Field(key, label, Input.MONEY, required)
    private fun number(key: String, label: String, required: Boolean = false) = Field(key, label, Input.NUMBER, required)
    private fun select(key: String, label: String, vararg options: String) = Field(key, label, Input.SELECT, true, options.toList())
    private fun day(key: String, label: String, required: Boolean = false) = Field(key, label, Input.DATE, required, hint = "yyyy-MM-dd")
    private fun time(key: String, label: String) = Field(key, label, Input.DATETIME, true, hint = "yyyy-MM-ddTHH:mm")
    private val note = Field("note", "Ghi chú", Input.MULTILINE)
    private val contact = listOf(Field("phone", "Số điện thoại", Input.PHONE), Field("email", "Email", Input.EMAIL))
    val modules: List<Module> = listOf(
        Module(Kind.STAFF, listOf(text("code", "Mã nhân sự", true)) + contact + listOf(
            select("role", "Vai trò", *Role.entries.map { it.name }.toTypedArray()), day("joined", "Ngày tham gia"), note
        ), listOf("Hoạt động", "Ngừng hoạt động")),
        Module(Kind.CUSTOMER, listOf(
            text("code", "Mã khách", true), Field("phone", "Số điện thoại", Input.PHONE, true),
            Field("email", "Email", Input.EMAIL), text("need", "Nhu cầu học"),
            select("level", "Trình độ", "Mới bắt đầu", "Cơ bản", "Trung cấp", "Nâng cao"),
            text("preferredTime", "Khung giờ mong muốn"), ref("sourceId", "Nguồn khách", Kind.SOURCE),
            ref("campaignId", "Chiến dịch", Kind.CAMPAIGN), ref("saleId", "Sale phụ trách", Kind.STAFF),
            day("followUp", "Chăm sóc tiếp"), note
        ), listOf("Mới", "Đang tư vấn", "Đã chốt", "Chưa phù hợp")),
        Module(Kind.CARE, listOf(ref("customerId", "Khách hàng", Kind.CUSTOMER, true),
            ref("ownerId", "Người phụ trách", Kind.STAFF, true), select("channel", "Hình thức", "Gọi điện", "Nhắn tin", "Gặp trực tiếp", "Khác"),
            time("at", "Thời gian liên hệ"), text("result", "Kết quả", true), day("followUp", "Hẹn tiếp theo"), note
        ), listOf("Đã ghi nhận")),
        Module(Kind.TASK, listOf(ref("customerId", "Khách hàng", Kind.CUSTOMER),
            ref("ownerId", "Người phụ trách", Kind.STAFF, true), day("due", "Hạn hoàn thành", true), note
        ), listOf("Cần làm", "Hoàn thành", "Đã hủy")),
        Module(Kind.SOURCE, listOf(note), listOf("Hoạt động", "Ngừng sử dụng")),
        Module(Kind.CAMPAIGN, listOf(ref("sourceId", "Kênh", Kind.SOURCE, true),
            ref("ownerId", "Phụ trách", Kind.STAFF), day("startDate", "Bắt đầu", true), day("endDate", "Kết thúc", true),
            money("budget", "Ngân sách dự kiến", true), note
        ), listOf("Nháp", "Đang chạy", "Hoàn thành", "Đã hủy")),
        Module(Kind.COURSE, listOf(text("code", "Mã khóa", true),
            select("level", "Trình độ", "Mới bắt đầu", "Cơ bản", "Trung cấp", "Nâng cao"),
            number("sessions", "Số buổi", true), number("minutes", "Phút / buổi", true), money("fee", "Học phí gợi ý", true),
            number("capacity", "Sĩ số tối đa", true), note
        ), listOf("Hoạt động", "Ngừng tuyển")),
        Module(Kind.CLASS, listOf(text("code", "Mã lớp", true), ref("courseId", "Khóa học", Kind.COURSE, true),
            day("startDate", "Ngày bắt đầu", true), number("sessions", "Số buổi", true),
            number("minutes", "Phút / buổi", true), number("capacity", "Sĩ số tối đa", true),
            ref("coachId", "Coach mặc định", Kind.COACH), ref("courtId", "Sân mặc định", Kind.COURT),
            text("weekdays", "Thứ trong tuần (1=T2 … 7=CN)", true), text("startTime", "Giờ học (HH:mm)", true), note
        ), listOf("Nháp", "Đang tuyển", "Đang học", "Tạm dừng", "Hoàn thành", "Đã hủy")),
        Module(Kind.ENROLLMENT, listOf(ref("customerId", "Học viên", Kind.CUSTOMER, true),
            ref("classId", "Lớp", Kind.CLASS, true), ref("saleId", "Sale ghi nhận", Kind.STAFF),
            day("joined", "Ngày đăng ký", true), money("fee", "Học phí gốc", true), money("discount", "Giảm giá"),
            text("discountReason", "Lý do giảm giá"), day("due", "Hạn học phí", true),
            select("absencePolicy", "Quy tắc nghỉ", "Vắng tính buổi", "Có phép được bù"), note
        ), listOf("Nháp", "Đang học", "Tạm dừng", "Hoàn thành", "Đã rút")),
        Module(Kind.SESSION, listOf(ref("classId", "Lớp", Kind.CLASS, true), time("start", "Bắt đầu"), time("end", "Kết thúc"),
            ref("coachId", "Coach", Kind.COACH), ref("courtId", "Sân", Kind.COURT),
            money("coachCost", "Tiền công buổi"), ref("originalId", "Buổi gốc (nếu học bù)", Kind.SESSION), note
        ), listOf("Dự kiến", "Đã xác nhận", "Hoàn thành", "Hoãn", "Đã hủy")),
        Module(Kind.ATTENDANCE, listOf(ref("sessionId", "Buổi học", Kind.SESSION, true),
            ref("enrollmentId", "Đăng ký học", Kind.ENROLLMENT, true), note
        ), listOf("Chưa xác nhận", "Có mặt", "Vắng có phép", "Vắng không phép")),
        Module(Kind.VENUE, listOf(text("address", "Địa chỉ", true), text("contact", "Người liên hệ"),
            Field("phone", "Điện thoại", Input.PHONE), text("opening", "Giờ hoạt động"),
            number("availableHours", "Giờ khả dụng mỗi ngày", true), note
        ), listOf("Hoạt động", "Ngừng hợp tác")),
        Module(Kind.COURT, listOf(text("code", "Mã sân", true), ref("venueId", "Cụm sân", Kind.VENUE, true),
            select("type", "Loại sân", "Ngoài trời", "Trong nhà", "Có mái che"), money("price", "Giá / giờ", true), note
        ), listOf("Hoạt động", "Bảo trì", "Ngừng hoạt động")),
        Module(Kind.RATE, listOf(ref("courtId", "Sân", Kind.COURT, true), text("weekdays", "Thứ áp dụng (1…7)", true),
            text("fromTime", "Từ giờ (HH:mm)", true), text("toTime", "Đến giờ (HH:mm)", true), money("price", "Giá / giờ", true), note
        ), listOf("Hoạt động", "Ngừng sử dụng")),
        Module(Kind.BOOKING, listOf(ref("courtId", "Sân", Kind.COURT, true), ref("sessionId", "Buổi học", Kind.SESSION),
            time("start", "Bắt đầu"), time("end", "Kết thúc"), money("price", "Đơn giá / giờ", true),
            money("amount", "Tổng tiền thuê", true), money("deposit", "Tiền cọc yêu cầu"), money("cancelFee", "Phí hủy"), note
        ), listOf("Nháp", "Giữ chỗ", "Đã xác nhận", "Hoàn thành", "Đã hủy")),
        Module(Kind.COACH, listOf(ref("staffId", "Nhân sự", Kind.STAFF, true), text("specialty", "Chuyên môn"),
            select("payType", "Tính công", "Theo buổi", "Thỏa thuận"), money("rate", "Công mặc định / buổi", true), note
        ), listOf("Hoạt động", "Ngừng hoạt động")),
        Module(Kind.RECEIVABLE, listOf(ref("enrollmentId", "Đăng ký học", Kind.ENROLLMENT),
            ref("customerId", "Khách hàng", Kind.CUSTOMER, true), ref("classId", "Lớp", Kind.CLASS),
            money("amount", "Số tiền phải thu", true), day("due", "Hạn thanh toán", true), note
        ), listOf("Nháp", "Đã xác nhận", "Đã hủy")),
        Module(Kind.PAYABLE, listOf(select("category", "Loại chi", "Tiền sân", "Tiền coach", "Marketing", "Khác"),
            ref("classId", "Lớp", Kind.CLASS), ref("sessionId", "Buổi học", Kind.SESSION),
            ref("bookingId", "Đặt sân", Kind.BOOKING), ref("coachId", "Coach", Kind.COACH),
            ref("campaignId", "Chiến dịch", Kind.CAMPAIGN), text("payee", "Người nhận", true),
            money("amount", "Số tiền phải trả", true), day("due", "Hạn thanh toán", true), note
        ), listOf("Nháp", "Đã xác nhận", "Đã hủy")),
        Module(Kind.VOUCHER, listOf(select("direction", "Loại phiếu", "Thu", "Chi", "Hoàn tiền"),
            day("date", "Ngày giao dịch", true), money("amount", "Số tiền", true),
            select("method", "Phương thức", "Tiền mặt", "Chuyển khoản demo", "Khác"),
            text("party", "Người nộp / nhận", true),
            ref("receivableId", "Khoản phải thu", Kind.RECEIVABLE), ref("payableId", "Khoản phải trả", Kind.PAYABLE),
            ref("originalId", "Phiếu thu gốc (hoàn tiền)", Kind.VOUCHER),
            Field("allocations", "Phân bổ nhiều khoản (ID:số tiền;…)", Input.MULTILINE,
                hint = "Để trống để phân bổ toàn bộ vào khoản đã chọn"), note
        ), listOf("Nháp", "Đã xác nhận", "Đã đảo"))
    )
    operator fun get(kind: Kind): Module = modules.first { it.kind == kind }
}