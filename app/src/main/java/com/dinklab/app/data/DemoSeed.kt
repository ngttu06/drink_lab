package com.dinklab.app.data

import com.dinklab.app.domain.*
import java.time.LocalDate

object DemoSeed {
    fun create(today: LocalDate = LocalDate.now(), empty: Boolean = false): List<Item> {
        val result = mutableListOf<Item>()
        fun add(id: String, kind: Kind, name: String, status: String = Schema[kind].statuses.first(), vararg fields: Pair<String, String>): Item {
            val item = Item(id, kind, name, status, fields.toMap())
            result += item
            return item
        }
        add("admin", Kind.STAFF, "Minh Anh · Admin", "Hoạt động", "code" to "NS001", "role" to "ADMIN", "email" to "admin@dinklab.example")
        (1..3).forEach { add("sale$it", Kind.STAFF, listOf("", "Linh Nguyễn", "Tuấn Trần", "Hà Phạm")[it], "Hoạt động", "code" to "SALE00$it", "role" to "SALE") }
        (1..5).forEach {
            add("staff-coach$it", Kind.STAFF, listOf("", "Hoàng Nam", "Thu Trang", "Đức Huy", "Quỳnh Anh", "Minh Quân")[it], "Hoạt động", "code" to "HLV00$it", "role" to "COACH")
        }
        add("marketing", Kind.STAFF, "Bảo Ngọc · Marketing", "Hoạt động", "code" to "MKT001", "role" to "MARKETING")
        add("owner", Kind.STAFF, "DinkLab · Chủ doanh nghiệp", "Hoạt động", "code" to "CEO001", "role" to "OWNER")
        add("admin2", Kind.STAFF, "Quang · Vận hành", "Hoạt động", "code" to "NS002", "role" to "ADMIN")
        if (empty) return result
        (1..5).forEach { add("coach$it", Kind.COACH, "Coach ${listOf("", "Hoàng Nam", "Thu Trang", "Đức Huy", "Quỳnh Anh", "Minh Quân")[it]}", "Hoạt động", "staffId" to "staff-coach$it", "specialty" to "Pickleball cơ bản & chiến thuật", "payType" to "Theo buổi", "rate" to "350000") }
        listOf("Facebook", "TikTok", "Giới thiệu", "Sự kiện", "Website", "Khách tự liên hệ").forEachIndexed { index, name -> add("source${index + 1}", Kind.SOURCE, name) }
        (1..5).forEach { add("campaign$it", Kind.CAMPAIGN, "Pickleball mùa mới · đợt $it", "Đang chạy", "sourceId" to "source${(it - 1) % 6 + 1}", "ownerId" to "marketing", "startDate" to today.minusDays(30).toString(), "endDate" to today.plusDays(30).toString(), "budget" to "5000000") }
        val venues = listOf("DinkLab Thảo Điền", "Riverside Bình Thạnh", "Pickle Arena Quận 7", "The Court Tân Bình")
        venues.forEachIndexed { i, name -> add("venue${i + 1}", Kind.VENUE, name, "Hoạt động", "address" to "${20 + i * 12} Đường thể thao, TP. Hồ Chí Minh (demo)", "contact" to "Quản lý sân ${i + 1}", "opening" to "06:00–22:00", "availableHours" to "16") }
        (1..10).forEach { i ->
            add("court$i", Kind.COURT, "Sân ${i.toString().padStart(2, '0')} · ${venues[(i - 1) % 4]}", "Hoạt động", "code" to "SAN${i.toString().padStart(2, '0')}", "venueId" to "venue${(i - 1) % 4 + 1}", "type" to "Có mái che", "price" to "200000")
            add("rate$i", Kind.RATE, "Giá tiêu chuẩn sân $i", "Hoạt động", "courtId" to "court$i", "weekdays" to "1,2,3,4,5,6,7", "fromTime" to "06:00", "toTime" to "22:00", "price" to "200000")
        }
        listOf("Làm quen Pickleball", "Nền tảng kỹ thuật", "Chiến thuật đánh đôi", "Nâng cao thi đấu").forEachIndexed { i, name ->
            add("course${i + 1}", Kind.COURSE, name, "Hoạt động", "code" to "KH0${i + 1}", "level" to listOf("Mới bắt đầu", "Cơ bản", "Trung cấp", "Nâng cao")[i], "sessions" to "10", "minutes" to "90", "fee" to "3000000", "capacity" to "4")
        }
        val firstNames = listOf("Minh", "Anh", "Linh", "Huy", "Trang", "Nam", "Ngọc", "Quân", "Thảo", "Bảo")
        val lastNames = listOf("Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ")
        (1..60).forEach { i ->
            add("customer$i", Kind.CUSTOMER, "${lastNames[(i - 1) / 10]} ${firstNames[(i - 1) % 10]} · $i", if (i <= 48) "Đã chốt" else if (i % 2 == 0) "Mới" else "Đang tư vấn",
                "code" to "KH${i.toString().padStart(3, '0')}", "phone" to "0900${i.toString().padStart(6, '0')}", "email" to "hocvien$i@example.test", "need" to "Học nhóm, cải thiện kỹ thuật", "level" to "Mới bắt đầu", "preferredTime" to "Buổi tối / cuối tuần", "sourceId" to "source${(i - 1) % 6 + 1}", "campaignId" to "campaign${(i - 1) % 5 + 1}", "saleId" to "sale${(i - 1) % 3 + 1}", "followUp" to today.plusDays((i % 7 - 2).toLong()).toString())
        }
        (1..100).forEach { i -> add("care$i", Kind.CARE, "Tư vấn gói học · lần $i", "Đã ghi nhận", "customerId" to "customer${(i - 1) % 60 + 1}", "ownerId" to "sale${(i - 1) % 3 + 1}", "channel" to "Nhắn tin", "at" to today.minusDays((i % 20).toLong()).atTime(10, 0).toString(), "result" to "Đã gửi thông tin khóa học và khung giờ", "followUp" to today.plusDays((i % 5).toLong()).toString()) }
        (1..30).forEach { i -> add("task$i", Kind.TASK, "Gọi lại khách hàng $i", if (i % 5 == 0) "Hoàn thành" else "Cần làm", "customerId" to "customer$i", "ownerId" to "sale${(i - 1) % 3 + 1}", "due" to today.plusDays((i % 7 - 3).toLong()).toString()) }
        (1..12).forEach { c ->
            val startDay = today.minusDays(14).plusDays((c - 1).toLong())
            val classStatus = when (c) { 10 -> "Tạm dừng"; 11 -> "Đang tuyển"; 12 -> "Nháp"; else -> "Đang học" }
            val clazz = add("class$c", Kind.CLASS, "${listOf("Starter", "Foundation", "Doubles", "Advanced")[(c - 1) % 4]} · ${c.toString().padStart(2, '0')}", classStatus,
                "code" to "DL-${c.toString().padStart(3, '0')}", "courseId" to "course${(c - 1) % 4 + 1}", "startDate" to startDay.toString(), "sessions" to "10", "minutes" to "90", "capacity" to "4", "coachId" to "coach${(c - 1) % 5 + 1}", "courtId" to "court${(c - 1) % 10 + 1}", "weekdays" to "1,3", "startTime" to "${(6 + c).toString().padStart(2, '0')}:00")
            (1..4).forEach { n ->
                val customer = (c - 1) * 4 + n
                add("enrollment$customer", Kind.ENROLLMENT, "Học viên $customer · ${clazz.name}", "Đang học", "customerId" to "customer$customer", "classId" to clazz.id, "saleId" to "sale${(customer - 1) % 3 + 1}", "joined" to startDay.toString(), "fee" to "3000000", "discount" to "0", "due" to startDay.plusDays(7).toString(), "absencePolicy" to "Có phép được bù")
                add("receivable$customer", Kind.RECEIVABLE, "Học phí · Học viên $customer", "Đã xác nhận", "enrollmentId" to "enrollment$customer", "customerId" to "customer$customer", "classId" to clazz.id, "amount" to "3000000", "due" to startDay.plusDays(7).toString(), "generated" to "enrollment$customer")
                if (customer % 3 != 0) add("receipt$customer", Kind.VOUCHER, "Thu học phí học viên $customer", "Đã xác nhận", "direction" to "Thu", "date" to today.minusDays((customer % 10).toLong()).toString(), "amount" to if (customer % 2 == 0) "1500000" else "3000000", "method" to "Chuyển khoản demo", "party" to "Học viên $customer", "receivableId" to "receivable$customer")
            }
            if (c <= 10) BusinessRules.generateSchedule(clazz).forEachIndexed { index, (start, end) ->
                val completed = start.toLocalDate() < today
                val status = if (completed) "Hoàn thành" else "Đã xác nhận"
                val coach = if (c == 1 && index == 5) "coach4" else clazz["coachId"]
                val court = if (c == 1 && index == 6) "court9" else clazz["courtId"]
                val sessionId = "session-$c-$index"
                val bookingId = "booking-$c-$index"
                add(sessionId, Kind.SESSION, "${clazz["code"]} · Buổi ${index + 1}", status, "classId" to clazz.id, "start" to start.toString(), "end" to end.toString(), "coachId" to coach, "courtId" to court, "coachCost" to "350000")
                add(bookingId, Kind.BOOKING, "Đặt sân · ${clazz["code"]} · ${index + 1}", if (completed) "Hoàn thành" else "Đã xác nhận", "sessionId" to sessionId, "courtId" to court, "start" to start.toString(), "end" to end.toString(), "price" to "200000", "amount" to "300000", "deposit" to "100000", "cancelFee" to "0")
                val courtPayId = "pay-court-$c-$index"
                add(courtPayId, Kind.PAYABLE, "Tiền sân · ${clazz["code"]} · ${index + 1}", "Đã xác nhận", "category" to "Tiền sân", "classId" to clazz.id, "sessionId" to sessionId, "bookingId" to bookingId, "payee" to "Đối tác sân", "amount" to "300000", "due" to start.toLocalDate().toString(), "generated" to bookingId)
                if (completed) {
                    add("pay-coach-$c-$index", Kind.PAYABLE, "Công coach · ${clazz["code"]} · ${index + 1}", "Đã xác nhận", "category" to "Tiền coach", "classId" to clazz.id, "sessionId" to sessionId, "coachId" to coach, "payee" to "Coach", "amount" to "350000", "due" to start.toLocalDate().plusDays(7).toString(), "generated" to sessionId)
                    (1..4).forEach { n -> add("attendance-$c-$index-$n", Kind.ATTENDANCE, "Điểm danh · HV ${(c - 1) * 4 + n}", if (n == 4 && index % 3 == 0) "Vắng có phép" else "Có mặt", "sessionId" to sessionId, "enrollmentId" to "enrollment${(c - 1) * 4 + n}") }
                }
                if (index == 0) {
                    add("deposit-$c", Kind.VOUCHER, "Cọc sân · ${clazz["code"]}", "Đã xác nhận", "direction" to "Chi", "date" to start.toLocalDate().toString(), "amount" to "100000", "method" to "Chuyển khoản demo", "party" to "Đối tác sân", "payableId" to courtPayId)
                    add("balance-$c", Kind.VOUCHER, "Thanh toán sân còn lại · ${clazz["code"]}", "Đã xác nhận", "direction" to "Chi", "date" to start.toLocalDate().toString(), "amount" to "200000", "method" to "Chuyển khoản demo", "party" to "Đối tác sân", "payableId" to courtPayId)
                }
            }
        }
        // Cancel a future session and replace it; keep the original as the progress identity.
        val originalIndex = result.indexOfFirst { it.id == "session-1-9" }
        val original = result[originalIndex]
        result[originalIndex] = original.copy(status = "Đã hủy")
        val originalBookingIndex = result.indexOfFirst { it.id == "booking-1-9" }
        result[originalBookingIndex] = result[originalBookingIndex].copy(status = "Đã hủy")
        val payableIndex = result.indexOfFirst { it.id == "pay-court-1-9" }
        result[payableIndex] = result[payableIndex].copy(status = "Đã hủy")
        val makeUpStart = java.time.LocalDateTime.parse(original["start"]).plusDays(1)
        add("makeup-1", Kind.SESSION, "DL-001 · Học bù buổi 10", "Dự kiến", "classId" to "class1", "start" to makeUpStart.toString(), "end" to makeUpStart.plusMinutes(90).toString(), "originalId" to original.id, "coachCost" to "350000")
        add("draft-voucher", Kind.VOUCHER, "Phiếu thu đang nhập", "Nháp", "direction" to "Thu", "date" to today.toString(), "amount" to "500000", "method" to "Tiền mặt", "party" to "Học viên demo", "receivableId" to "receivable3")
        add("marketing-pay", Kind.PAYABLE, "Quảng cáo khai giảng", "Đã xác nhận", "category" to "Marketing", "campaignId" to "campaign1", "payee" to "Nền tảng quảng cáo demo", "amount" to "2000000", "due" to today.plusDays(5).toString())
        return result
    }
}