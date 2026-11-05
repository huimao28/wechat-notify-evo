package com.huimao.wechatnotifyevo

import android.app.PendingIntent
import android.os.Process

/** Decisions are deliberately based only on public Android user identifiers. */
enum class UserIsolationDecision { PROCESS, IGNORE_SOURCE_USER }

object UserIsolationPolicy {
    /**
     * Fail open for Android system/unknown negative source ids. Only a concrete,
     * non-negative source id belonging to another Android user is rejected.
     * creatorUserId is diagnostic-only because OEMs may proxy PendingIntents.
     */
    @Suppress("UNUSED_PARAMETER")
    fun decide(sourceUserId: Int, currentUserId: Int, creatorUserId: Int?): UserIsolationDecision = when {
        sourceUserId < 0 -> UserIsolationDecision.PROCESS
        sourceUserId == currentUserId -> UserIsolationDecision.PROCESS
        else -> UserIsolationDecision.IGNORE_SOURCE_USER
    }

    /** Unknown/system sources are stored as the current user for all later routing. */
    fun normalizeSourceUserId(sourceUserId: Int, currentUserId: Int): Int =
        if (sourceUserId < 0) currentUserId else sourceUserId
}

object CurrentUserScope {
    private const val PER_USER_RANGE = 100_000
    fun id(): Int = Process.myUid() / PER_USER_RANGE

    fun creatorUserHandle(pendingIntent: PendingIntent?): android.os.UserHandle? {
        if (pendingIntent == null) return null
        return try {
            pendingIntent.creatorUserHandle
        } catch (_: RuntimeException) {
            // Some system/vendor PendingIntents do not expose a creator handle.
            null
        }
    }

    /** Diagnostic only. creatorUid is public and avoids treating hashCode as an id. */
    fun creatorUserId(pendingIntent: PendingIntent?): Int? {
        if (pendingIntent == null) return null
        return try {
            pendingIntent.creatorUid.takeIf { it >= 0 }?.div(PER_USER_RANGE)
        } catch (_: RuntimeException) {
            null
        }
    }

    fun accepts(sourceUserId: Int, pendingIntent: PendingIntent?): Boolean =
        UserIsolationPolicy.decide(sourceUserId, id(), creatorUserId(pendingIntent)) ==
            UserIsolationDecision.PROCESS

    fun accepts(conv: Conversation): Boolean = accepts(conv, id())

    internal fun accepts(conv: Conversation, currentUserId: Int): Boolean =
        ConversationStore.get(conv.nid, conv.sourceUserId) === conv &&
            UserIsolationPolicy.decide(
                conv.sourceUserId,
                currentUserId,
                creatorUserId(conv.contentIntent),
            ) == UserIsolationDecision.PROCESS
}
