package com.dinklab.app.data

import android.content.Context
import androidx.room.*
import com.dinklab.app.domain.Item
import com.dinklab.app.domain.Kind
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/** Schema-driven mock storage. References and state transitions are guarded by Repository. */
@Entity(tableName = "records", indices = [Index("kind"), Index("updatedAt")])
data class StoredItem(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val status: String,
    val payload: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun domain(): Item {
        val json = JSONObject(payload)
        val values = json.keys().asSequence().associateWith { json.getString(it) }
        return Item(id, Kind.valueOf(kind), name, status, values, archived, createdAt, updatedAt)
    }
    companion object {
        fun from(item: Item) = StoredItem(item.id, item.kind.name, item.name, item.status,
            JSONObject(item.values).toString(), item.archived, item.createdAt, item.updatedAt)
    }
}

@Entity(tableName = "events")
data class AuditEvent(
    @PrimaryKey val id: String,
    val actorId: String,
    val actorName: String,
    val action: String,
    val targetId: String,
    val targetName: String,
    val detail: String,
    val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "preferences")
data class Preference(@PrimaryKey val key: String, val value: String)

@Dao
interface LocalDao {
    @Query("SELECT * FROM records ORDER BY updatedAt DESC") fun observeItems(): Flow<List<StoredItem>>
    @Query("SELECT * FROM records") suspend fun items(): List<StoredItem>
    @Upsert suspend fun put(items: List<StoredItem>)
    @Query("DELETE FROM records WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM records") suspend fun clearItems()
    @Query("SELECT * FROM events ORDER BY at DESC") fun observeEvents(): Flow<List<AuditEvent>>
    @Insert suspend fun event(event: AuditEvent)
    @Query("DELETE FROM events") suspend fun clearEvents()
    @Query("SELECT value FROM preferences WHERE `key` = :key") suspend fun preference(key: String): String?
    @Upsert suspend fun preference(value: Preference)
    @Query("DELETE FROM preferences") suspend fun clearPreferences()
}

@Database(entities = [StoredItem::class, AuditEvent::class, Preference::class], version = 1, exportSchema = true)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao
    companion object {
        fun open(context: Context): LocalDatabase = Room.databaseBuilder(
            context.applicationContext, LocalDatabase::class.java, "dinklab-demo.db"
        ).build()
    }
}