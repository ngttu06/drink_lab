package com.dinklab.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dinklab.app.data.*
import com.dinklab.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: LocalDatabase
    private lateinit var repository: DinkRepository
    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LocalDatabase::class.java).build(); repository = DinkRepository(db) }
    @After fun tearDown() { db.close() }

    @Test fun seedIsIdempotentAndCrudPersists() = runTest {
        repository.initialize()
        val count = repository.items.first().size
        repository.initialize()
        assertEquals(count, repository.items.first().size)
        val source = Item(kind = Kind.SOURCE, name = "Nguồn mới", status = "Hoạt động")
        repository.save(source, "admin")
        repository.save(source.copy(name = "Nguồn đã sửa"), "admin")
        assertEquals("Nguồn đã sửa", repository.items.first().first { it.id == source.id }.name)
        repository.deleteOrArchive(source.id, "admin", "Kiểm thử xóa")
        assertFalse(repository.items.first().any { it.id == source.id })
    }

    @Test fun confirmedReceiptCannotBeEditedButCanBeReversed() = runTest {
        repository.initialize()
        val receipt = repository.items.first().first { it.id == "receipt1" }
        var rejected = false
        try { repository.save(receipt.with("amount" to "1"), "admin", "Test") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        repository.reverseVoucher(receipt.id, "admin", "Nhập sai")
        assertEquals("Đã đảo", repository.items.first().first { it.id == receipt.id }.status)
    }

    @Test fun resetEmptyDoesNotReseedOnInitialize() = runTest {
        repository.initialize()
        repository.reset("admin", true)
        repository.initialize()
        assertTrue(repository.items.first().all { it.kind == Kind.STAFF })
    }

    @Test fun saleCannotCreateFinancialRecords() = runTest {
        repository.initialize()
        var rejected = false
        try { repository.save(Item(kind = Kind.VOUCHER, name = "Không hợp lệ", status = "Nháp"), "sale1") }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun brandingUpgradePreservesCustomNamesAndFinancialData() = runTest {
        repository.initialize()
        val before = repository.items.first()
        val owner = before.first { it.id == "owner" }.copy(name = "DinkLab · Chủ doanh nghiệp")
        val customVenue = before.first { it.id == "venue1" }.copy(name = "Sân riêng của tôi")
        db.dao().put(listOf(StoredItem.from(owner), StoredItem.from(customVenue)))
        // A missing preference simulates an installed version before the rebrand.
        db.openHelper.writableDatabase.execSQL("DELETE FROM preferences WHERE `key` = 'branding-dinkapp'")
        repository.setting("businessName", "DinkLab")
        repository.initialize()
        val after = repository.items.first()
        assertEquals("DinkApp · Chủ doanh nghiệp", after.first { it.id == "owner" }.name)
        assertEquals("Sân riêng của tôi", after.first { it.id == "venue1" }.name)
        assertEquals("DinkApp", repository.setting("businessName"))
        assertEquals(before.filter { it.kind == Kind.VOUCHER }, after.filter { it.kind == Kind.VOUCHER })
    }

    @Test fun batchReschedulePreservesCompletedSessions() = runTest {
        repository.initialize()
        val before = repository.items.first()
        val completed = before.filter { it.kind == Kind.SESSION && it["classId"] == "class1" && it.status == "Hoàn thành" }
        val count = repository.rescheduleFuture("class1", java.time.LocalDate.now().plusDays(30).toString(), "20:00", "2,4", "coach1", "court1", "admin", "Đổi lịch tháng tới")
        assertTrue(count > 0)
        val after = repository.items.first()
        completed.forEach { original -> assertEquals(original, after.first { it.id == original.id }) }
    }

    @Test fun sessionCompletionCreatesCoachPayOnlyOnce() = runTest {
        repository.initialize()
        val all = repository.items.first()
        val session = all.first { it.kind == Kind.SESSION && it.status == "Đã xác nhận" && it["coachId"].isNotBlank() && all.none { p -> p.kind == Kind.PAYABLE && p["sessionId"] == it.id && p["category"] == "Tiền coach" } }
        val done = session.copy(status = "Hoàn thành")
        repository.save(done, "admin", "Buổi đã dạy")
        repository.save(done, "admin", "Lặp thao tác")
        assertEquals(1, repository.items.first().count { it.kind == Kind.PAYABLE && it["sessionId"] == session.id && it["category"] == "Tiền coach" })
    }
}