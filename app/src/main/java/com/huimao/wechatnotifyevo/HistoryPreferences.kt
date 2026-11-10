package com.huimao.wechatnotifyevo

import android.content.Context

enum class Retention(val millis: Long?) {
    HOURS_24(24 * 60 * 60 * 1000L),
    DAYS_7(7 * 24 * 60 * 60 * 1000L),
    DAYS_30(30 * 24 * 60 * 60 * 1000L),
    FOREVER(null),
}

data class HistorySettingsSnapshot(
    val enabled: Boolean = false,
    val retention: Retention = Retention.DAYS_7,
    val excludedStableConversationIds: Set<String> = emptySet(),
)

object HistoryPolicy {
    const val MAX_ROWS = 5000

    fun cutoff(now: Long, retention: Retention): Long? =
        retention.millis?.let { now - it }

    fun shouldRecord(settings: HistorySettingsSnapshot, stableConversationId: String): Boolean =
        settings.enabled && stableConversationId !in settings.excludedStableConversationIds

    /** Number of oldest post-cutoff rows to remove to satisfy the hard cap. */
    fun overflowAfterRetention(timestamps: List<Long>, cutoff: Long?, maxRows: Int = MAX_ROWS): Int {
        val retained = if (cutoff == null) timestamps.size else timestamps.count { it >= cutoff }
        return (retained - maxRows.coerceAtLeast(0)).coerceAtLeast(0)
    }
}

fun interface HistorySettingsSource {
    fun snapshot(): HistorySettingsSnapshot
}

class AndroidHistorySettings(private val context: Context) : HistorySettingsSource {
    override fun snapshot(): HistorySettingsSnapshot = HistoryPreferences.snapshot(context)
}

object HistoryPreferences {
    private const val ENABLED = "history_enabled_v1"
    private const val RETENTION = "history_retention_v1"
    private const val EXCLUDED = "history_excluded_v1"

    fun snapshot(context: Context) = HistorySettingsSnapshot(
        enabled = enabled(context),
        retention = retention(context),
        excludedStableConversationIds = excluded(context),
    )

    /** Recording is privacy-preserving opt-in: absent preference always means off. */
    fun enabled(context: Context): Boolean = Prefs.store(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        Prefs.store(context).edit().putBoolean(ENABLED, value).apply()
    }

    fun retention(context: Context): Retention = runCatching {
        Retention.valueOf(Prefs.store(context).getString(RETENTION, Retention.DAYS_7.name)!!)
    }.getOrDefault(Retention.DAYS_7)

    fun setRetention(context: Context, value: Retention) {
        Prefs.store(context).edit().putString(RETENTION, value.name).apply()
    }

    fun excluded(context: Context): Set<String> =
        Prefs.store(context).getStringSet(EXCLUDED, emptySet())?.toSet().orEmpty()

    fun setExcluded(context: Context, values: Set<String>) {
        Prefs.store(context).edit().putStringSet(EXCLUDED, values.toSet()).apply()
    }
}
