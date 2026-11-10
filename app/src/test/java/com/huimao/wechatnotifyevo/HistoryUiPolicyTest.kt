package com.huimao.wechatnotifyevo

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HistoryUiPolicyTest {
    @Test fun `recording remains opt in by default`() {
        assertFalse(HistorySettingsSnapshot().enabled)
    }

    @Test fun `today cutoff is local start of day`() {
        val zone = ZoneId.of("America/Chicago")
        val now = ZonedDateTime.of(2026, 11, 9, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, HistoryRangePolicy.cutoff(HistoryRange.TODAY, now, zone))
    }

    @Test fun `range cutoffs use full requested days`() {
        val now = 2_000_000_000_000L
        assertEquals(now - 7L * 86_400_000, HistoryRangePolicy.cutoff(HistoryRange.DAYS_7, now))
        assertEquals(now - 30L * 86_400_000, HistoryRangePolicy.cutoff(HistoryRange.DAYS_30, now))
    }

    @Test fun `retention restores safe default`() {
        assertEquals(Retention.DAYS_7, RetentionSelection.restore("unknown"))
        assertEquals(Retention.FOREVER, RetentionSelection.restore(Retention.FOREVER.name))
    }

    @Test fun `decrypt failure maps to safe key state`() {
        assertEquals(HistoryUiError.KEY_UNAVAILABLE, HistoryUiStatePolicy.mapError(HistoryReadException(IllegalStateException())))
        assertEquals(HistoryUiError.READ_FAILED, HistoryUiStatePolicy.mapError(IllegalStateException()))
    }
}
