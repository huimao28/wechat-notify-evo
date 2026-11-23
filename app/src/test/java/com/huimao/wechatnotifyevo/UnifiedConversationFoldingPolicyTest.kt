package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedConversationFoldingPolicyTest {
    private fun entry(
        id: Int,
        type: ConversationType = ConversationType.DIRECT,
        timestamp: Long = id.toLong(),
    ) = UnifiedConversationFoldingPolicy.Entry(
        id, type, "会话$id", "消息$id", timestamp
    )

    @Test fun `zero through three active conversations have no summary`() {
        for (count in 0..3) {
            assertFalse(UnifiedConversationFoldingPolicy.evaluate((1..count).map(::entry)).showSummary)
        }
    }

    @Test fun `four or more active conversations have one summary`() {
        assertTrue(UnifiedConversationFoldingPolicy.evaluate((1..4).map(::entry)).showSummary)
        assertTrue(UnifiedConversationFoldingPolicy.evaluate((1..9).map(::entry)).showSummary)
    }

    @Test fun `many messages in one conversation do not fold`() {
        // Message count/counter is intentionally absent from the policy input.
        assertFalse(UnifiedConversationFoldingPolicy.evaluate(listOf(entry(1))).showSummary)
    }

    @Test fun `conversation types share the same threshold`() {
        val mixed = listOf(
            entry(1, ConversationType.DIRECT), entry(2, ConversationType.GROUP),
            entry(3, ConversationType.BOT), entry(4, ConversationType.UNKNOWN),
        )
        assertTrue(UnifiedConversationFoldingPolicy.evaluate(mixed).showSummary)
    }

    @Test fun `summary sorts newest first and limits lines to five`() {
        val result = UnifiedConversationFoldingPolicy.evaluate(
            listOf(entry(1, timestamp = 10), entry(2, timestamp = 60),
                entry(3, timestamp = 20), entry(4, timestamp = 50),
                entry(5, timestamp = 30), entry(6, timestamp = 40))
        )
        assertEquals(listOf(2, 4, 6, 5, 3), result.entries.map { it.id })
    }

    @Test fun `dropping from four to three cancels summary decision`() {
        assertTrue(UnifiedConversationFoldingPolicy.evaluate((1..4).map(::entry)).showSummary)
        assertFalse(UnifiedConversationFoldingPolicy.evaluate((1..3).map(::entry)).showSummary)
    }

    @Test fun `all children use one stable group and summary id`() {
        ConversationType.values().forEach {
            assertEquals(UnifiedConversationFoldingPolicy.GROUP_KEY, NotificationFactory.groupKeyFor(it))
        }
        assertEquals(900010, UnifiedConversationFoldingPolicy.SUMMARY_ID)
        assertEquals(NotificationFactory.SUMMARY_ID_UNIFIED, UnifiedConversationFoldingPolicy.SUMMARY_ID)
    }
}
