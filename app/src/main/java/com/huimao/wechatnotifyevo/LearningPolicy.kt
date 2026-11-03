package com.huimao.wechatnotifyevo

/** Main-thread only. No queue: a skipped first encounter is consumed, not retried. */
class AutoLearningPolicy {
    private val attempts = ArrayDeque<Long>()

    fun admit(conv: Conversation, enabled: Boolean, busy: Boolean, now: Long): Boolean {
        if (conv.autoLearningConsidered) return false
        conv.autoLearningConsidered = true
        if (!enabled || busy || conv.learnedMainUser != null ||
            ConversationStore.get(conv.nid) !== conv) return false
        while (attempts.isNotEmpty() && now - attempts.first() >= 3_600_000L) {
            attempts.removeFirst()
        }
        if (attempts.size >= 6 || (attempts.isNotEmpty() && now - attempts.last() < 60_000L)) {
            return false
        }
        attempts.addLast(now)
        return true
    }
}

/** Both manual and automatic requests share one slot and one cancellation epoch. */
class LearningRequestGate {
    private var serial = 0L
    private var active: Long? = null
    fun begin(): Long? {
        if (active != null) return null
        return (++serial).also { active = it }
    }
    fun accepts(id: Long): Boolean = active == id
    fun finish(id: Long): Boolean {
        if (!accepts(id)) return false
        active = null
        return true
    }
    fun cancel() { active = null; ++serial }
}
