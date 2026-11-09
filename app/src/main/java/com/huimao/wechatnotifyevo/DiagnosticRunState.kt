package com.huimao.wechatnotifyevo

sealed interface DiagnosticRunState {
    data object Idle : DiagnosticRunState
    data class Running(val startedAtMillis: Long) : DiagnosticRunState
    data class Complete(val completedAtMillis: Long) : DiagnosticRunState
    data class Error(val completedAtMillis: Long, val reason: String) : DiagnosticRunState
}

object MinimumDetectionDisplay {
    const val DEFAULT_MINIMUM_MILLIS = 300L

    fun remainingMillis(
        startedAtMillis: Long,
        nowMillis: Long,
        animationsEnabled: Boolean,
        minimumMillis: Long = DEFAULT_MINIMUM_MILLIS,
    ): Long {
        if (!animationsEnabled) return 0L
        return (minimumMillis - (nowMillis - startedAtMillis)).coerceAtLeast(0L)
    }
}
