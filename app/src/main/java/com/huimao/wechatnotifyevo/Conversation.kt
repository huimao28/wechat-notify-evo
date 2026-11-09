package com.huimao.wechatnotifyevo

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.graphics.drawable.Icon

/** One message inside a conversation. sender == null means "self". */
data class ChatMessage(
    val text: String,
    val sender: String?,
    val timestamp: Long,
)

enum class ConversationType { UNKNOWN, DIRECT, GROUP, BOT }

/**
 * One WeChat conversation, keyed by the original notification id.
 * WeChat uses one notification id per conversation; if a different title shows
 * up under an existing id, the store resets that entry instead of mixing two
 * conversations' histories (WeChat recycles ids after removals).
 *
 * Archives live only in process memory (v1): if the app process is killed,
 * history restarts from the next incoming notification.
 */
class Conversation(val nid: Int, val sourceUserId: Int) {
    var title: String = ""
    var type: ConversationType = ConversationType.UNKNOWN
    var smallIcon: Icon? = null
    var originalLargeIcon: Icon? = null
        private set
    var processedLargeIcon: Icon? = null
        private set

    /** Converts once when captured; every notification surface then reuses the cached result. */
    fun updateLargeIcon(context: Context, icon: Icon, circularEnabled: Boolean) {
        originalLargeIcon = icon
        processedLargeIcon = if (circularEnabled) AvatarIconProcessor.circularize(context, icon) else null
        shortcutPushed = false
    }

    /** Rebuilds from the retained source when the compatibility preference changes. */
    fun rebuildLargeIcon(context: Context, circularEnabled: Boolean) {
        processedLargeIcon = if (circularEnabled) {
            originalLargeIcon?.let { AvatarIconProcessor.circularize(context, it) }
        } else {
            null
        }
        shortcutPushed = false
    }

    fun displayLargeIcon(circularEnabled: Boolean): Icon? =
        if (CircularAvatarPolicy.useProcessed(circularEnabled, processedLargeIcon != null)) {
            processedLargeIcon
        } else {
            originalLargeIcon
        }

    var contentIntent: PendingIntent? = null
    // A fresh opaque generation for every notification snapshot, never a chat identifier.
    var snapshotToken: String = java.util.UUID.randomUUID().toString()
    // Security generation only: rejects stale intents and is never a conversation identity.
    var routeToken: String = java.util.UUID.randomUUID().toString()
    /** Stable system identity, independently persisted as an irreversible HMAC output. */
    var conversationStableId: String = ""
    var conversationIdentityKind: ConversationIdentityStore.Kind =
        ConversationIdentityStore.Kind.TEMPORARY
    var sourceIdentity: String? = null
    var autoLearningConsidered = false
    var notificationKey: String? = null
    var notificationPostTime: Long = 0L
    var learnedMainUser: String? = null
    /** True only after a real WeChat post observed while direct chat was enabled. */
    var directChatEligible: Boolean = false
    var learningStatus: String = "尚未学习当前通知"

    fun isCurrentSnapshot(token: String, key: String?, pi: PendingIntent?): Boolean =
        ConversationStore.get(nid, sourceUserId) === this && snapshotToken == token &&
            notificationKey == key && key != null && contentIntent === pi

    fun continues(key: String, identity: String?): Boolean =
        ConversationStore.get(nid, sourceUserId) === this && notificationKey == key &&
            sourceIdentity == identity

    fun replaceSnapshot(key: String, postTime: Long, pi: PendingIntent?, identity: String? = null) {
        val continuous = continues(key, identity)
        // Even a continuous update invalidates an in-flight extraction callback.
        snapshotToken = java.util.UUID.randomUUID().toString()
        if (!continuous) {
            routeToken = java.util.UUID.randomUUID().toString()
            learnedMainUser = null
            autoLearningConsidered = false
            learningStatus = "新会话：尚未学习"
            shortcutPushed = false
        }
        notificationKey = key
        sourceIdentity = identity
        notificationPostTime = postTime
        contentIntent = pi
    }
    /**
     * Whether a shortcut is currently published for this conversation.
     * ShortcutManager rate-limits apps that push too often, so a push is
     * skipped when the shortcut is already published — a push is only
     * needed for a new conversation or when the shortcut is gone.
     * Set only after a successful push, so a rate-limited or failed push
     * is retried on the next message.
     */
    var shortcutPushed: Boolean = false
    /** Opaque WeChat-owned actions. Their PendingIntents are reused verbatim. */
    var replyPendingIntent: PendingIntent? = null
    var replyRemoteInputs: List<RemoteInput> = emptyList()
    var replySourceAction: Notification.Action? = null
    var readPendingIntent: PendingIntent? = null
    var readSourceAction: Notification.Action? = null
    var lastCounter: Int = -1
    val messages: MutableList<ChatMessage> = mutableListOf()
}

object ConversationStore {
    private data class Key(val sourceUserId: Int, val nid: Int)
    private val map = HashMap<Key, Conversation>()

    private fun normalizedUserId(sourceUserId: Int, currentUserId: Int?): Int {
        if (sourceUserId >= 0) return sourceUserId
        return UserIsolationPolicy.normalizeSourceUserId(
            sourceUserId,
            requireNotNull(currentUserId) { "currentUserId is required for an unknown source user" },
        )
    }

    fun getOrCreate(
        nid: Int,
        title: String,
        sourceUserId: Int,
        currentUserId: Int? = null,
    ): Conversation {
        val normalizedUserId = normalizedUserId(sourceUserId, currentUserId)
        val key = Key(normalizedUserId, nid)
        val existing = map[key]
        if (existing != null && existing.title != title) {
            remove(nid, normalizedUserId)
            val fresh = Conversation(nid, normalizedUserId)
            fresh.title = title
            map[key] = fresh
            return fresh
        }
        return existing ?: Conversation(nid, normalizedUserId).also {
            it.title = title
            map[key] = it
        }
    }

    fun get(nid: Int, sourceUserId: Int): Conversation? = map[Key(sourceUserId, nid)]

    fun remove(nid: Int, sourceUserId: Int) {
        map.remove(Key(sourceUserId, nid))?.invalidate()
    }

    private fun Conversation.invalidate() {
        learnedMainUser = null
        contentIntent = null
        snapshotToken = java.util.UUID.randomUUID().toString()
    }

    fun all(): List<Conversation> = map.values.toList()
    fun allForUser(sourceUserId: Int): List<Conversation> =
        map.values.filter { it.sourceUserId == sourceUserId }

    /** Removes stale/foreign-user state and returns it for shortcut cleanup. */
    fun removeOtherUsers(currentUserId: Int): List<Conversation> {
        val removed = map.values.filter { it.sourceUserId != currentUserId }
        removed.forEach { conv ->
            map.remove(Key(conv.sourceUserId, conv.nid))
            conv.invalidate()
        }
        return removed
    }

    fun clear() {
        map.values.toList().forEach { remove(it.nid, it.sourceUserId) }
    }
}

/** Chats whose titles are service accounts, not people or groups. */
private val BOT_TITLES = setOf("服务通知", "订阅号消息", "微信团队", "微信支付")

fun isBotTitle(title: String): Boolean = title in BOT_TITLES

/**
 * Strip a leading unread counter like "[2条]" / "[12]" / "[3則]".
 * Returns the remaining text and the counter, or -1 when there is no counter.
 * Non-numeric brackets such as "[动画表情]" or "[图片]" are left untouched.
 */
fun stripCounter(text: String): Pair<String, Int> {
    if (!text.startsWith("[")) return text to -1
    val close = text.indexOf(']')
    if (close < 2) return text to -1
    val inner = text.substring(1, close)
    var digits = 0
    while (digits < inner.length && inner[digits].isDigit()) digits++
    if (digits == 0) return text to -1
    val count = inner.substring(0, digits).toIntOrNull() ?: return text to -1
    if (count < 2) return text to -1
    return text.substring(close + 1) to count
}

/**
 * Split "sender: message" into sender and body using the ": " separator
 * (the format WeChat uses in tickers and aggregated texts).
 * Returns null sender when there is no separator.
 */
fun splitSender(text: String): Pair<String?, String> {
    val pos = text.indexOf(": ")
    if (pos <= 0) return null to text
    return text.substring(0, pos) to text.substring(pos + 2)
}
