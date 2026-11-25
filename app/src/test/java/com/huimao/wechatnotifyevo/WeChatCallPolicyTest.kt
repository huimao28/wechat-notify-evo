package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeChatCallPolicyTest {
    @Test fun ordinaryOngoingNotificationIsNotCall() {
        assertNull(decide(text = "微信正在运行"))
    }

    @Test fun incomingActionsProduceIncomingCall() {
        val result = decide(
            actions = listOf(
                CallActionCandidate(0, "拒绝", true),
                CallActionCandidate(1, "接听", true),
            )
        )
        assertEquals(WeChatCallPhase.INCOMING, result?.phase)
        assertEquals(1, result?.answerIndex)
        assertEquals(0, result?.declineIndex)
    }

    @Test fun hangUpActionProducesOngoingCall() {
        val result = decide(actions = listOf(CallActionCandidate(2, "挂断", true)))
        assertEquals(WeChatCallPhase.ONGOING, result?.phase)
        assertEquals(2, result?.hangUpIndex)
    }

    @Test fun iconOnlySemanticActionsProduceIncomingCall() {
        val result = decide(
            actions = listOf(
                CallActionCandidate(0, "", true, semanticAction = 11),
                CallActionCandidate(1, "", true, semanticAction = 10),
            )
        )
        assertEquals(WeChatCallPhase.INCOMING, result?.phase)
        assertEquals(1, result?.answerIndex)
        assertEquals(0, result?.declineIndex)
    }

    @Test fun ringingTextIsRecognizedAsIncoming() {
        assertEquals(
            WeChatCallPhase.INCOMING,
            decide(text = "小明邀请你语音通话")?.phase,
        )
        assertEquals(WeChatCallPhase.INCOMING, decide(text = "[语音通话]")?.phase)
        assertEquals(WeChatCallPhase.INCOMING, decide(text = "[视频通话]")?.phase)
    }

    @Test fun callTextGateCoversWeChatFallbackMessages() {
        assertEquals(true, WeChatCallPolicy.looksLikeCallText("[语音通话]"))
        assertEquals(true, WeChatCallPolicy.looksLikeCallText("邀请你视频通话"))
        assertEquals(false, WeChatCallPolicy.looksLikeCallText("普通聊天消息"))
    }

    @Test fun callChannelWithoutControlsIsDetectedForSafeFallback() {
        assertEquals(
            WeChatCallPhase.INCOMING,
            decide(channel = "wechat_voip_call")?.phase,
        )
    }

    private fun decide(
        text: String = "",
        channel: String = "message",
        actions: List<CallActionCandidate> = emptyList(),
    ) = WeChatCallPolicy.decide(
        categoryCall = false,
        callStyleTemplate = false,
        hasAnswerExtra = false,
        hasDeclineExtra = false,
        hasHangUpExtra = false,
        hasCallPerson = false,
        channelId = channel,
        titleAndText = text,
        actions = actions,
    )
}
