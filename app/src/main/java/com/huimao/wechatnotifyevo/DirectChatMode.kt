package com.huimao.wechatnotifyevo

/** Pure policy shared by preference migration and unit tests. */
data class DirectChatFlags(
    val bubble: Boolean,
    val autoLearn: Boolean,
    val launcherControl: Boolean,
) {
    val enabled: Boolean get() = bubble && autoLearn && launcherControl

    companion object {
        fun linked(enabled: Boolean) = DirectChatFlags(enabled, enabled, enabled)
    }
}

object DirectChatModePolicy {
    /** A legacy install opts in only when all three old switches were enabled. */
    fun migrate(legacy: DirectChatFlags): DirectChatFlags = DirectChatFlags.linked(legacy.enabled)
}

/**
 * Orders a mode transition. Enabling is deliberately only one atomic
 * preference write: refreshing an existing notification can make some OEMs
 * expand newly-added bubble metadata and launch its shortcut. Disabling first
 * invalidates callbacks, then removes routes and active evolved notifications
 * without reposting them.
 */
class DirectChatTransition(
    private val writeLinkedFlags: (Boolean) -> Unit,
    private val cancelPendingLearning: () -> Unit,
    private val cleanupLearnedRoutes: () -> Unit,
    private val removeActiveNotifications: () -> Unit,
) {
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            writeLinkedFlags(true)
            return
        }
        cancelPendingLearning()
        writeLinkedFlags(false)
        cleanupLearnedRoutes()
        removeActiveNotifications()
    }
}
