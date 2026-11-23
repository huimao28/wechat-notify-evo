package com.huimao.wechatnotifyevo

/** Pure policy for deciding whether active unread conversations share a summary. */
object UnifiedConversationFoldingPolicy {
    const val SUMMARY_THRESHOLD = 4
    const val MAX_SUMMARY_LINES = 5
    const val GROUP_KEY = "evo_group_unified_conversations"
    const val SUMMARY_ID = 900010
    val LEGACY_SUMMARY_IDS = intArrayOf(900001, 900002, 900003)

    data class Entry(
        val id: Int,
        val type: ConversationType,
        val title: String,
        val lastMessage: String,
        val lastTimestamp: Long,
    )

    data class Result(val showSummary: Boolean, val entries: List<Entry>)

    fun evaluate(entries: List<Entry>): Result = Result(
        showSummary = entries.size >= SUMMARY_THRESHOLD,
        entries = entries.sortedByDescending { it.lastTimestamp }.take(MAX_SUMMARY_LINES),
    )

    fun fromConversations(conversations: List<Conversation>): Result = evaluate(
        conversations.map { conversation ->
            val last = conversation.messages.lastOrNull()
            Entry(
                id = conversation.nid,
                type = conversation.type,
                title = conversation.title,
                lastMessage = last?.text.orEmpty(),
                lastTimestamp = last?.timestamp ?: conversation.notificationPostTime,
            )
        }
    )
}
