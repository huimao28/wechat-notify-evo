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
    private val startService: () -> Unit,
    private val stopService: () -> Unit,
) {
    fun setEnabled(enabled: Boolean) {
        // Persist first: a concurrently created/restarted service always observes
        // the user's latest choice. Disabling then stops and removes it immediately.
        writePreference(enabled)
        if (enabled) startService() else stopService()
    }
}
