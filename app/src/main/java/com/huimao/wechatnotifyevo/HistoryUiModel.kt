package com.huimao.wechatnotifyevo

import java.time.Instant
import java.time.ZoneId

const val HISTORY_PAGE_SIZE = 30

enum class HistoryRange(val label: String) { TODAY("今天"), DAYS_7("7天"), DAYS_30("30天") }

object HistoryRangePolicy {
    fun cutoff(range: HistoryRange, now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long = when (range) {
        HistoryRange.TODAY -> Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
        HistoryRange.DAYS_7 -> now - 7L * 24 * 60 * 60 * 1000
        HistoryRange.DAYS_30 -> now - 30L * 24 * 60 * 60 * 1000
    }
}

enum class HistoryUiError { KEY_UNAVAILABLE, READ_FAILED }

object HistoryUiStatePolicy {
    fun mapError(error: Throwable): HistoryUiError = when (error) {
        is HistoryReadException -> HistoryUiError.KEY_UNAVAILABLE
        else -> HistoryUiError.READ_FAILED
    }
}

object RetentionSelection {
    val values = listOf(Retention.HOURS_24, Retention.DAYS_7, Retention.DAYS_30, Retention.FOREVER)
    fun restore(name: String?): Retention = values.firstOrNull { it.name == name } ?: Retention.DAYS_7
    fun label(value: Retention): String = when (value) {
        Retention.HOURS_24 -> "24 小时"
        Retention.DAYS_7 -> "7 天"
        Retention.DAYS_30 -> "30 天"
        Retention.FOREVER -> "永久（仍受 5000 条上限）"
    }
}

data class HistoryUiState(
    val settings: HistorySettingsSnapshot = HistorySettingsSnapshot(),
    val stats: HistoryStats = HistoryStats(0, 0),
    val range: HistoryRange = HistoryRange.DAYS_7,
    val conversations: List<ConversationHistorySummary> = emptyList(),
    val hasMoreConversations: Boolean = false,
    val detailStableId: String? = null,
    val detailMessages: List<StoredHistoryMessage> = emptyList(),
    val hasMoreMessages: Boolean = false,
    val loading: Boolean = true,
    val error: HistoryUiError? = null,
)
