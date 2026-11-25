package com.huimao.wechatnotifyevo

enum class WeChatCallPhase { INCOMING, ONGOING }

data class CallActionCandidate(
    val index: Int,
    val label: String,
    val hasIntent: Boolean,
    val semanticAction: Int = 0,
)

data class WeChatCallDecision(
    val phase: WeChatCallPhase,
    val answerIndex: Int? = null,
    val declineIndex: Int? = null,
    val hangUpIndex: Int? = null,
)

/** Android-free policy so call recognition can be regression-tested locally. */
object WeChatCallPolicy {
    private val answerWords = listOf("接听", "接受", "同意", "answer", "accept")
    private val declineWords = listOf("拒绝", "拒接", "decline", "reject")
    private val hangUpWords = listOf("挂断", "结束", "hang up", "hangup", "end call")
    private val ringingWords = listOf("邀请你语音通话", "邀请你视频通话", "语音来电", "视频来电", "来电")
    private val activeWords = listOf("通话中", "正在通话", "语音通话中", "视频通话中")

    fun decide(
        categoryCall: Boolean,
        callStyleTemplate: Boolean,
        hasAnswerExtra: Boolean,
        hasDeclineExtra: Boolean,
        hasHangUpExtra: Boolean,
        hasCallPerson: Boolean,
        channelId: String,
        titleAndText: String,
        actions: List<CallActionCandidate>,
    ): WeChatCallDecision? {
        fun action(words: List<String>, semantic: Int) = actions.firstOrNull { candidate ->
            candidate.hasIntent && (
                candidate.semanticAction == semantic ||
                    words.any { candidate.label.contains(it, ignoreCase = true) }
                )
        }
        // Notification.Action semantic values are stable public API values:
        // ANSWER_CALL=10, DECLINE_CALL=11, HANGUP_CALL=12.
        val answer = action(answerWords, 10)
        val decline = action(declineWords, 11)
        val hangUp = action(hangUpWords, 12)
        val lowerChannel = channelId.lowercase()
        val active = hasHangUpExtra || hangUp != null || activeWords.any { titleAndText.contains(it, true) }
        val incoming = hasAnswerExtra || hasDeclineExtra || answer != null || decline != null ||
            ringingWords.any { titleAndText.contains(it, true) }
        val structural = categoryCall || callStyleTemplate || hasCallPerson ||
            lowerChannel.contains("voip") || lowerChannel.contains("call") || lowerChannel.contains("ring")
        if (!structural && !incoming && !active) return null
        return if (active && !incoming) {
            WeChatCallDecision(WeChatCallPhase.ONGOING, hangUpIndex = hangUp?.index)
        } else {
            WeChatCallDecision(
                WeChatCallPhase.INCOMING,
                answerIndex = answer?.index,
                declineIndex = decline?.index,
                hangUpIndex = hangUp?.index,
            )
        }
    }
}
