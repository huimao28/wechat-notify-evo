package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPolicyTest {
    @Test fun recordingDefaultsOffAndHonorsExclusions() {
        assertFalse(HistoryPolicy.shouldRecord(HistorySettingsSnapshot(), "conversation-a"))
        assertFalse(
            HistoryPolicy.shouldRecord(
                HistorySettingsSnapshot(
                    enabled = true,
                    excludedStableConversationIds = setOf("conversation-a"),
                ),
                "conversation-a",
            )
        )
        assertTrue(
            HistoryPolicy.shouldRecord(
                HistorySettingsSnapshot(enabled = true),
                "conversation-a",
            )
        )
    }

    @Test fun retentionSupports24Hours7Days30DaysAndForever() {
        val now = 4_000_000_000L
        assertEquals(now - 24L * 60 * 60 * 1000, HistoryPolicy.cutoff(now, Retention.HOURS_24))
        assertEquals(now - 7L * 24 * 60 * 60 * 1000, HistoryPolicy.cutoff(now, Retention.DAYS_7))
        assertEquals(now - 30L * 24 * 60 * 60 * 1000, HistoryPolicy.cutoff(now, Retention.DAYS_30))
        assertNull(HistoryPolicy.cutoff(now, Retention.FOREVER))
        assertEquals(Retention.DAYS_7, HistorySettingsSnapshot().retention)
    }

    @Test fun hardCapCountsOnlyRowsRemainingAfterRetention() {
        assertEquals(0, HistoryPolicy.overflowAfterRetention(listOf(1, 2, 3), cutoff = 2, maxRows = 2))
        assertEquals(1, HistoryPolicy.overflowAfterRetention(listOf(1, 2, 3, 4), cutoff = 2, maxRows = 2))
        assertEquals(HistoryPolicy.MAX_ROWS, 5000)
    }
}
