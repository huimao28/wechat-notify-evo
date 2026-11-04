package com.huimao.wechatnotifyevo

/** Pure preference and transition policy, kept Android-free for unit tests. */
internal object KeepAlivePreferencePolicy {
    const val KEY = "foreground_keepalive_enabled"
    const val DEFAULT_ENABLED = false

    fun read(readBoolean: (String, Boolean) -> Boolean): Boolean =
        readBoolean(KEY, DEFAULT_ENABLED)
}

/** Applies one user toggle in a deterministic order. */
internal class KeepAliveTransition(
    private val writePreference: (Boolean) -> Unit,
    private val enableKeepAlive: () -> Unit,
    private val disableKeepAlive: () -> Unit,
) {
    fun setEnabled(enabled: Boolean) {
        // Persist first so services and already-running jobs see the new choice.
        writePreference(enabled)
        if (enabled) enableKeepAlive() else disableKeepAlive()
    }
}

/** Android-free scheduling decision used by the coordinator and unit tests. */
internal object KeepAliveSchedulingPolicy {
    fun reconcile(enabled: Boolean, schedule: () -> Unit, cancel: () -> Unit) {
        if (enabled) schedule() else cancel()
    }
}

/** Thread-safe monotonic-time gate preventing repeated listener rebind requests. */
internal class RebindCooldown(private val cooldownMillis: Long) {
    private var lastRequestAt = Long.MIN_VALUE

    @Synchronized
    fun tryAcquire(nowMillis: Long): Boolean {
        val elapsed = if (lastRequestAt == Long.MIN_VALUE) Long.MAX_VALUE else nowMillis - lastRequestAt
        if (elapsed < 0 || elapsed < cooldownMillis) return false
        lastRequestAt = nowMillis
        return true
    }
}
