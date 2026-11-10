package com.huimao.wechatnotifyevo

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(
    tableName = "captured_messages",
    indices = [
        Index(value = ["dedupeDigest"], unique = true),
        Index(value = ["conversationStableId", "timestamp"]),
        Index(value = ["timestamp"]),
    ],
)
data class CapturedMessageEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationStableId: String,
    @Embedded(prefix = "title_") val title: EncryptedField,
    @Embedded(prefix = "sender_") val sender: EncryptedField,
    @Embedded(prefix = "text_") val text: EncryptedField,
    val timestamp: Long,
    val type: String,
    val incoming: Boolean,
    val source: String,
    val dedupeDigest: String,
)

data class ConversationSummaryEntity(
    val conversationStableId: String,
    val lastTimestamp: Long,
    val messageCount: Int,
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CapturedMessageEntity): Long

    @Query("SELECT * FROM captured_messages WHERE timestamp >= :from ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun messages(from: Long, limit: Int, offset: Int): List<CapturedMessageEntity>

    @Query("SELECT * FROM captured_messages WHERE conversationStableId = :id AND timestamp >= :from ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun conversationMessages(
        id: String,
        from: Long,
        limit: Int,
        offset: Int,
    ): List<CapturedMessageEntity>

    @Query("SELECT COUNT(*) FROM captured_messages")
    suspend fun count(): Int

    @Query("SELECT conversationStableId, MAX(timestamp) AS lastTimestamp, COUNT(*) AS messageCount FROM captured_messages WHERE timestamp >= :from GROUP BY conversationStableId ORDER BY lastTimestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun conversationSummaries(from: Long, limit: Int, offset: Int): List<ConversationSummaryEntity>

    @Query("SELECT * FROM captured_messages WHERE conversationStableId = :id ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun latestConversationMessage(id: String): CapturedMessageEntity?

    @Query("SELECT COALESCE(SUM(LENGTH(title_ciphertext) + LENGTH(sender_ciphertext) + LENGTH(text_ciphertext) + LENGTH(title_nonce) + LENGTH(sender_nonce) + LENGTH(text_nonce)), 0) FROM captured_messages")
    suspend fun encryptedBytes(): Long

    @Query("DELETE FROM captured_messages WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM captured_messages WHERE conversationStableId = :id")
    suspend fun deleteConversation(id: String): Int

    @Query("DELETE FROM captured_messages")
    suspend fun clear()

    @Query("DELETE FROM captured_messages WHERE id IN (SELECT id FROM captured_messages ORDER BY timestamp ASC, id ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int): Int

    @Transaction
    suspend fun insertAndEnforce(
        entity: CapturedMessageEntity,
        cutoff: Long?,
        maxRows: Int,
    ): Long {
        val insertedId = insert(entity)
        if (cutoff != null) deleteOlderThan(cutoff)
        val overflow = count() - maxRows.coerceAtLeast(0)
        if (overflow > 0) deleteOldest(overflow)
        return insertedId
    }
}

@Database(entities = [CapturedMessageEntity::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var instance: HistoryDatabase? = null

        fun get(context: Context): HistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HistoryDatabase::class.java,
                "local_history_v1.db",
            ).build().also { instance = it }
        }
    }
}
