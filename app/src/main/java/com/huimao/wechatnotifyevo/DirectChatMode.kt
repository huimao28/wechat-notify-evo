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
 * Orders a mode transition so disabling first invalidates callbacks, then
 * atomically writes all linked flags, and finally removes learned routes.
 */
class DirectChatTransition(
    private val writeLinkedFlags: (Boolean) -> Unit,
    private val cancelPendingLearning: () -> Unit,
    private val cleanupLearnedRoutes: () -> Unit,
) {
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            writeLinkedFlags(true)
        } else {
            cancelPendingLearning()
            writeLinkedFlags(false)
            cleanupLearnedRoutes()
        }
    }
}
