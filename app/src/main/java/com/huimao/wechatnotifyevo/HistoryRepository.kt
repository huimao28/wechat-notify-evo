package com.huimao.wechatnotifyevo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plain input deliberately contains no Main_User, notification key, Intent, or extras. */
data class HistoryMessage(
    val conversationStableId: String,
    val title: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val type: String,
    val incoming: Boolean,
    val source: String,
)

data class ConversationHistorySummary(
    val stableId: String,
    val title: String,
    val lastTimestamp: Long,
    val messageCount: Int,
    val titleDecryptionFailed: Boolean,
)

data class HistoryStats(val count: Int, val encryptedBytes: Long)

class HistoryReadException(cause: Throwable) : Exception(cause)

data class StoredHistoryMessage(
    val id: Long,
    val conversationStableId: String,
    val title: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val type: String,
    val incoming: Boolean,
    val source: String,
)

class HistoryRepository(
    private val dao: HistoryDao,
    private val settingsSource: HistorySettingsSource,
    private val crypto: HistoryCrypto,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Returns false when disabled/excluded/invalid or when the HMAC already exists. */
    suspend fun record(message: HistoryMessage): Boolean = withContext(ioDispatcher) {
        val settings = settingsSource.snapshot()
        if (message.conversationStableId.isBlank() ||
            !HistoryPolicy.shouldRecord(settings, message.conversationStableId)
        ) return@withContext false

        val digest = crypto.dedupeHmac(
            listOf(
                DEDUPE_VERSION,
                message.conversationStableId,
                message.title,
                message.sender,
                message.text,
                message.timestamp.toString(),
                message.type,
                message.incoming.toString(),
                message.source,
            )
        )
        val entity = CapturedMessageEntity(
            conversationStableId = message.conversationStableId,
            title = crypto.encrypt(message.title),
            sender = crypto.encrypt(message.sender),
            text = crypto.encrypt(message.text),
            timestamp = message.timestamp,
            type = message.type,
            incoming = message.incoming,
            source = message.source,
            dedupeDigest = digest,
        )
        dao.insertAndEnforce(
            entity,
            HistoryPolicy.cutoff(now(), settings.retention),
            HistoryPolicy.MAX_ROWS,
        ) != INSERT_IGNORED
    }

    suspend fun conversationPage(
        from: Long,
        limit: Int,
        offset: Int = 0,
    ): List<ConversationHistorySummary> = withContext(ioDispatcher) {
        dao.conversationSummaries(from, limit.coerceIn(1, 100), offset.coerceAtLeast(0)).map { row ->
            val titleResult = runCatching {
                dao.latestConversationMessage(row.conversationStableId)?.let { crypto.decrypt(it.title) }
                    .orEmpty()
            }
            ConversationHistorySummary(
                stableId = row.conversationStableId,
                title = titleResult.getOrNull()?.takeIf(String::isNotBlank) ?: "无法读取的会话",
                lastTimestamp = row.lastTimestamp,
                messageCount = row.messageCount,
                titleDecryptionFailed = titleResult.isFailure,
            )
        }
    }

    suspend fun stats(): HistoryStats = withContext(ioDispatcher) {
        HistoryStats(dao.count(), dao.encryptedBytes())
    }

    suspend fun messages(from: Long, limit: Int, offset: Int = 0): List<StoredHistoryMessage> =
        withContext(ioDispatcher) {
            dao.messages(from, limit.coerceIn(1, HistoryPolicy.MAX_ROWS), offset.coerceAtLeast(0))
                .map { entity ->
                    try { decrypt(entity) } catch (error: Throwable) { throw HistoryReadException(error) }
                }
        }

    suspend fun conversationMessages(
        stableConversationId: String,
        from: Long,
        limit: Int,
        offset: Int = 0,
    ): List<StoredHistoryMessage> = withContext(ioDispatcher) {
        dao.conversationMessages(
            stableConversationId,
            from,
            limit.coerceIn(1, HistoryPolicy.MAX_ROWS),
            offset.coerceAtLeast(0),
        ).map { entity ->
            try { decrypt(entity) } catch (error: Throwable) { throw HistoryReadException(error) }
        }
    }

    suspend fun trimBefore(cutoff: Long): Int = withContext(ioDispatcher) { dao.deleteOlderThan(cutoff) }

    suspend fun deleteConversation(stableConversationId: String): Int = withContext(ioDispatcher) {
        dao.deleteConversation(stableConversationId)
    }

    suspend fun clear() = withContext(ioDispatcher) { dao.clear() }

    private fun decrypt(entity: CapturedMessageEntity) = StoredHistoryMessage(
        id = entity.id,
        conversationStableId = entity.conversationStableId,
        title = crypto.decrypt(entity.title),
        sender = crypto.decrypt(entity.sender),
        text = crypto.decrypt(entity.text),
        timestamp = entity.timestamp,
        type = entity.type,
        incoming = entity.incoming,
        source = entity.source,
    )

    private companion object {
        const val DEDUPE_VERSION = "history-dedupe-v1"
        const val INSERT_IGNORED = -1L
    }
}

object HistoryEvents {
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = mutableChanges.asSharedFlow()
    fun notifyChanged() { mutableChanges.tryEmit(Unit) }
}

object AndroidHistoryRepository {
    @Volatile private var instance: HistoryRepository? = null

    fun get(context: Context): HistoryRepository = instance ?: synchronized(this) {
        instance ?: HistoryRepository(
            dao = HistoryDatabase.get(context).historyDao(),
            settingsSource = AndroidHistorySettings(context.applicationContext),
            crypto = AndroidKeystoreHistoryCrypto(),
        ).also { instance = it }
    }
}

/** Fire-and-forget boundary used only after the evolved notification was posted successfully. */
object HistoryRecorder {
    private const val TAG = "WNE.History"
    private const val SOURCE = "wechat_evolved_notification"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recordEvolved(context: Context, conversation: Conversation) {
        val latest = conversation.messages.lastOrNull() ?: return
        val input = HistoryMessage(
            conversationStableId = conversation.conversationStableId,
            title = conversation.title,
            sender = latest.sender.orEmpty(),
            text = latest.text,
            timestamp = latest.timestamp,
            type = conversation.type.name,
            incoming = true,
            source = SOURCE,
        )
        val appContext = context.applicationContext
        scope.launch {
            runCatching { AndroidHistoryRepository.get(appContext).record(input) }
                .onSuccess { inserted -> if (inserted) HistoryEvents.notifyChanged() }
                .onFailure { Log.w(TAG, "Local history write failed: ${it.javaClass.simpleName}") }
        }
    }
}
