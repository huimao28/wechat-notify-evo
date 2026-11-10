package com.huimao.wechatnotifyevo

enum class AutoLearningDecision { ADMITTED, DISABLED, BUSY, RATE_LIMITED, ALREADY_CONSIDERED, ALREADY_LEARNED, STALE }

/** Main-thread only. Busy/rate-limited work is skipped without consuming the conversation. */
class AutoLearningPolicy {
    private val attempts = ArrayDeque<Long>()

    fun decide(conv: Conversation, enabled: Boolean, busy: Boolean, now: Long): AutoLearningDecision {
        if (!enabled) return AutoLearningDecision.DISABLED
        if (conv.autoLearningConsidered) return AutoLearningDecision.ALREADY_CONSIDERED
        if (conv.learnedMainUser != null) return AutoLearningDecision.ALREADY_LEARNED
        if (ConversationStore.get(conv.nid, conv.sourceUserId) !== conv) return AutoLearningDecision.STALE
        if (busy) return AutoLearningDecision.BUSY
        while (attempts.isNotEmpty() && now - attempts.first() >= 3_600_000L) attempts.removeFirst()
        if (attempts.size >= 6 || (attempts.isNotEmpty() && now - attempts.last() < 60_000L)) {
            return AutoLearningDecision.RATE_LIMITED
        }
        attempts.addLast(now)
        conv.autoLearningConsidered = true
        return AutoLearningDecision.ADMITTED
    }

    fun admit(conv: Conversation, enabled: Boolean, busy: Boolean, now: Long): Boolean =
        decide(conv, enabled, busy, now) == AutoLearningDecision.ADMITTED

    /** A failed admitted attempt may be retried by a later notification post. */
    fun failed(conv: Conversation) {
        if (conv.learnedMainUser == null) conv.autoLearningConsidered = false
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
