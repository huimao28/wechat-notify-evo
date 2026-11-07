package com.huimao.wechatnotifyevo

/** Whether an Android Auto action keeps a real WeChat entry point or uses WNE's local simulation. */
enum class CompatActionKind { REAL, SIMULATED, NONE }

data class CompatActionPlan(
    val reply: CompatActionKind,
    val markRead: CompatActionKind,
)

/** Pure policy: real notification-supplied actions always win; simulation only fills gaps. */
object AndroidAutoCompatPolicy {
    const val REMOTE_INPUT_RESULT_KEY = "wne_android_auto_compat_reply"
    const val MARK_READ_SHOWS_USER_INTERFACE = false
    const val REPLY_SHOWS_USER_INTERFACE = false
    const val EXTRA_SHOWS_USER_INTERFACE = "android.support.action.showsUserInterface"

    fun plan(
        enabled: Boolean,
        hasRealReply: Boolean,
        hasRealMarkRead: Boolean,
    ) = CompatActionPlan(
        reply = action(enabled, hasRealReply),
        markRead = action(enabled, hasRealMarkRead),
    )

    private fun action(enabled: Boolean, hasReal: Boolean): CompatActionKind = when {
        hasReal -> CompatActionKind.REAL
        enabled -> CompatActionKind.SIMULATED
        else -> CompatActionKind.NONE
    }
}

data class CompatRoute(
    val nid: Int,
    val sourceUserId: Int,
    val routeToken: String,
)

/** Rejects forged, cross-user and stale PendingIntents without relying on caller-supplied identity. */
object AndroidAutoCompatRoutePolicy {
    fun isCurrent(
        requested: CompatRoute,
        currentUserId: Int,
        liveNid: Int?,
        liveSourceUserId: Int?,
        liveRouteToken: String?,
    ): Boolean = requested.nid >= 0 && requested.routeToken.isNotBlank() &&
        requested.sourceUserId == currentUserId &&
        requested.nid == liveNid && requested.sourceUserId == liveSourceUserId &&
        requested.routeToken == liveRouteToken
}

data class SimulatedReplyEffect(
    val localEchoText: String,
    val forwardToWechat: Boolean = false,
    val persistReplyText: Boolean = false,
)

/** Produces only an explicit in-memory warning/echo; it never represents a sent WeChat message. */
object SimulatedReplyPolicy {
    const val PREFIX = "【仅车机本地回显，未发送微信】我："

    fun create(reply: String): SimulatedReplyEffect? = reply
        .takeIf { it.isNotBlank() }
        ?.let { SimulatedReplyEffect(localEchoText = PREFIX + it) }
}
