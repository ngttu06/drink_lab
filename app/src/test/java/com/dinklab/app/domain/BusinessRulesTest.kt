package com.dinklab.app.domain

import com.dinklab.app.data.DemoSeed
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BusinessRulesTest {
    private val day = LocalDate.of(2026, 11, 3)
    private fun item(id: String, kind: Kind, status: String = "Đã xác nhận", vararg fields: Pair<String, String>) =
        Item(id, kind, id, status, fields.toMap())
    private fun expectInvalid(block: () -> Unit) {
        try { block(); fail("Expected validation to reject this operation") } catch (_: IllegalArgumentException) { }
    }

    @Test fun adjacentIntervalsDoNotOverlap() {
        assertFalse(BusinessRules.overlaps("2026-11-03T09:00", "2026-11-03T10:00", "2026-11-03T10:00", "2026-11-03T11:00"))
        assertTrue(BusinessRules.overlaps("2026-11-03T09:00", "2026-11-03T10:01", "2026-11-03T10:00", "2026-11-03T11:00"))
    }

    @Test fun scheduleHasExactlyTenMondayWednesdaySessions() {
        val clazz = item("class", Kind.CLASS, "Nháp", "sessions" to "10", "minutes" to "90", "weekdays" to "1,3", "startTime" to "18:00", "startDate" to day.toString())
        val schedule = BusinessRules.generateSchedule(clazz)
        assertEquals(10, schedule.size)
        assertEquals("2026-11-04T18:00", schedule.first().first.toString())
        assertTrue(schedule.all { it.first.dayOfWeek.value in setOf(1, 3) })
        assertTrue(schedule.all { java.time.Duration.between(it.first, it.second).toMinutes() == 90L })
    }

    @Test fun partialPaymentAndReversalRecomputeDebt() {
        val debt = item("debt", Kind.RECEIVABLE, "Đã xác nhận", "amount" to "3000000", "due" to "2026-11-10")
        val receipt = item("receipt", Kind.VOUCHER, "Đã xác nhận", "direction" to "Thu", "amount" to "1500000", "receivableId" to debt.id)
        assertEquals(1500000, BusinessRules.balance(debt, listOf(debt, receipt)))
        assertEquals("Thanh toán một phần", BusinessRules.paymentStatus(debt, listOf(debt, receipt), day))
        assertEquals(3000000, BusinessRules.balance(debt, listOf(debt, receipt.copy(status = "Đã đảo"))))
        assertEquals("Quá hạn", BusinessRules.paymentStatus(debt, listOf(debt), day.plusDays(10)))
    }

    @Test fun depositAndFinalPaymentDoNotDuplicateExpense() {
        val debt = item("court-debt", Kind.PAYABLE, "Đã xác nhận", "amount" to "300000")
        val deposit = item("deposit", Kind.VOUCHER, "Đã xác nhận", "direction" to "Chi", "amount" to "100000", "payableId" to debt.id)
        val final = item("final", Kind.VOUCHER, "Đã xác nhận", "direction" to "Chi", "amount" to "200000", "payableId" to debt.id)
        assertEquals(0, BusinessRules.balance(debt, listOf(debt, deposit, final)))
        assertEquals(300000, BusinessRules.settled(debt, listOf(debt, deposit, final)))
    }

    @Test fun allocationsRejectDuplicateTargets() {
        val voucher = item("v", Kind.VOUCHER, "Nháp", "allocations" to "a:10;a:20")
        expectInvalid { BusinessRules.allocations(voucher) }
        val valid = voucher.with("allocations" to "a:10;b:20")
        assertEquals(mapOf("a" to 10L, "b" to 20L), BusinessRules.allocations(valid))
    }

    @Test fun refundReopensReceivableAndDoesNotChangeOriginalAmount() {
        val debt = item("r", Kind.RECEIVABLE, "Đã xác nhận", "amount" to "1000")
        val receipt = item("v", Kind.VOUCHER, "Đã xác nhận", "direction" to "Thu", "amount" to "1000", "receivableId" to "r")
        val refund = item("refund", Kind.VOUCHER, "Đã xác nhận", "direction" to "Hoàn tiền", "amount" to "300", "originalId" to "v")
        assertEquals(300, BusinessRules.balance(debt, listOf(debt, receipt, refund)))
        assertEquals(1000, debt.amount())
    }

    @Test fun makeupDoesNotDoubleCountCompletedProgress() {
        val clazz = item("c", Kind.CLASS, "Đang học", "sessions" to "10")
        val original = item("s1", Kind.SESSION, "Đã hủy", "classId" to "c")
        val makeup = item("s2", Kind.SESSION, "Hoàn thành", "classId" to "c", "originalId" to "s1")
        assertEquals(1 to 9, BusinessRules.progress(clazz, listOf(clazz, original, makeup)))
    }

    @Test fun absencePolicyControlsConsumedLessons() {
        val enrollment = item("e", Kind.ENROLLMENT, "Đang học", "absencePolicy" to "Có phép được bù")
        val session = item("s", Kind.SESSION, "Hoàn thành")
        val attendance = item("a", Kind.ATTENDANCE, "Vắng có phép", "enrollmentId" to "e", "sessionId" to "s")
        assertEquals(0, BusinessRules.consumed(enrollment, listOf(session, attendance)))
        assertEquals(1, BusinessRules.consumed(enrollment.with("absencePolicy" to "Vắng tính buổi"), listOf(session, attendance)))
    }

    @Test fun demoSeedHasUniqueIdsAndValidReferences() {
        val seed = DemoSeed.create(day)
        assertEquals(seed.size, seed.map { it.id }.distinct().size)
        seed.forEach { record -> Schema[record.kind].fields.filter { it.reference != null && record[it.key].isNotBlank() }.forEach { field ->
            assertTrue("${record.id}.${field.key}", seed.any { it.id == record[field.key] && it.kind == field.reference })
        } }
        assertEquals(60, seed.count { it.kind == Kind.CUSTOMER })
        assertEquals(12, seed.count { it.kind == Kind.CLASS })
    }

    @Test fun demoSeedValidatesWithoutConflicts() {
        val seed = DemoSeed.create(day)
        seed.filter { it.kind != Kind.VOUCHER }.forEach { record ->
            try { BusinessRules.validate(record, seed, record) }
            catch (e: Exception) { throw AssertionError("${record.id}: ${e.message}", e) }
        }
        seed.filter { it.kind == Kind.VOUCHER }.forEach { record ->
            try { BusinessRules.validate(record, seed.filter { it.id != record.id }, record) }
            catch (e: Exception) { throw AssertionError("${record.id}: ${e.message}", e) }
        }
    }

    @Test fun roleVisibilityAndWritesAreScoped() {
        val seed = DemoSeed.create(day)
        val sale = seed.first { it.id == "sale1" }
        assertTrue(BusinessRules.visible(sale, seed.first { it.id == "customer1" }, seed))
        assertFalse(BusinessRules.visible(sale, seed.first { it.id == "customer2" }, seed))
        assertFalse(BusinessRules.canCreate(sale, Kind.VOUCHER))
        val coach = seed.first { it.id == "staff-coach1" }
        assertFalse(BusinessRules.visible(coach, seed.first { it.id == "receipt1" }, seed))
        assertTrue("fee" in BusinessRules.protectedFields(coach, Kind.ENROLLMENT))
    }

    @Test fun emptyDatasetKeepsLoginAccountsOnly() {
        val seed = DemoSeed.create(day, empty = true)
        assertEquals(12, seed.size)
        assertTrue(seed.all { it.kind == Kind.STAFF })
    }

    @Test fun tuitionDiscountCannotExceedFee() {
        val seed = DemoSeed.create(day)
        val enrollment = seed.first { it.id == "enrollment1" }.with("discount" to "4000000", "discountReason" to "Test")
        expectInvalid { BusinessRules.validate(enrollment, seed, enrollment) }
    }

    @Test fun overpaymentIsRejected() {
        val seed = DemoSeed.create(day)
        val voucher = seed.first { it.id == "draft-voucher" }.copy(status = "Đã xác nhận").with("amount" to "4000000")
        expectInvalid { BusinessRules.validate(voucher, seed) }
    }
}