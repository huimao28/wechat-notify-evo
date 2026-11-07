package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoActionPolicyTest {
    @Test
    fun `standard actions win over car extender fallbacks`() {
        val selected = AndroidAutoActionPolicy.select(
            standard = listOf(
                ActionCandidate(0, true, listOf("wechat_reply_key"), true),
                ActionCandidate(1, true, semanticAction = AndroidAutoActionPolicy.SEMANTIC_MARK_AS_READ),
            ),
            carReply = ActionCandidate(-1, true, listOf("car_key"), true),
            carRead = ActionCandidate(-2, true),
        )
        assertEquals(ActionBridgeSource.STANDARD, selected.reply?.source)
        assertEquals(0, selected.reply?.index)
        assertEquals(ActionBridgeSource.STANDARD, selected.markRead?.source)
    }

    @Test
    fun `missing legal entry points exposes no actions`() {
        val selected = AndroidAutoActionPolicy.select(
            standard = listOf(
                ActionCandidate(0, true),
                ActionCandidate(1, false, listOf("key"), true),
            ),
        )
        assertNull(selected.reply)
        assertNull(selected.markRead)
    }

    @Test
    fun `reply result key is preserved exactly`() {
        val selected = AndroidAutoActionPolicy.select(
            standard = listOf(ActionCandidate(4, true, listOf("mm_exact_result_key"), true)),
        )
        assertEquals("mm_exact_result_key", selected.reply?.remoteInputResultKey)
    }

    @Test
    fun `car extender is used only as fallback`() {
        val selected = AndroidAutoActionPolicy.select(
            standard = emptyList(),
            carReply = ActionCandidate(-1, true, listOf("legacy_reply"), true),
            carRead = ActionCandidate(-2, true),
        )
        assertEquals(ActionBridgeSource.CAR_EXTENDER, selected.reply?.source)
        assertEquals("legacy_reply", selected.reply?.remoteInputResultKey)
        assertEquals(ActionBridgeSource.CAR_EXTENDER, selected.markRead?.source)
    }

    @Test
    fun `group metadata has title and group flag`() {
        val group = ConversationMetadataPolicy.forConversation("项目群", ConversationType.GROUP)
        assertEquals("项目群", group.conversationTitle)
        assertTrue(group.isGroupConversation)

        val direct = ConversationMetadataPolicy.forConversation("小明", ConversationType.DIRECT)
        assertNull(direct.conversationTitle)
        assertFalse(direct.isGroupConversation)
    }
}
