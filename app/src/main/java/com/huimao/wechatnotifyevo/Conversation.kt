package com.huimao.wechatnotifyevo

import android.app.PendingIntent
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
class Conversation(val nid: Int) {
    var title: String = ""
    var type: ConversationType = ConversationType.UNKNOWN
    var smallIcon: Icon? = null
    var largeIcon: Icon? = null
    var contentIntent: PendingIntent? = null
    /**
     * WeChat username (wxid) for this conversation, learned once via
     * contentIntent.send + OnFinished reading the Main_User extra
     * (Nevolution's trick). Null until learned; the shortcut intent falls
     * back to the WeChat launcher until then.
     */
    var wxid: String? = null
    var replyPendingIntent: PendingIntent? = null
    var readPendingIntent: PendingIntent? = null
    var remoteInputResultKey: String? = null
    var lastCounter: Int = -1
    val messages: MutableList<ChatMessage> = mutableListOf()
}

object ConversationStore {
    private val map = HashMap<Int, Conversation>()

    fun getOrCreate(nid: Int, title: String): Conversation {
        val existing = map[nid]
        if (existing != null && existing.title != title) {
            val fresh = Conversation(nid)
            fresh.title = title
            map[nid] = fresh
            return fresh
        }
        return existing ?: Conversation(nid).also {
            it.title = title
            map[nid] = it
        }
    }

    fun get(nid: Int): Conversation? = map[nid]

    fun remove(nid: Int) {
        map.remove(nid)
    }

    fun all(): List<Conversation> = map.values.toList()

    fun clear() {
        map.clear()
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
