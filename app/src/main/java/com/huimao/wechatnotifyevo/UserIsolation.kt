package com.huimao.wechatnotifyevo

import android.app.PendingIntent
import android.os.Process

/** Decisions are deliberately based only on public Android user handles. */
enum class UserIsolationDecision { PROCESS, IGNORE_SOURCE_USER, IGNORE_CREATOR_USER }

object UserIsolationPolicy {
    /** Unknown/system creator is allowed; a known different creator is rejected. */
    fun decide(sourceUserId: Int, currentUserId: Int, creatorUserId: Int?): UserIsolationDecision = when {
        sourceUserId != currentUserId -> UserIsolationDecision.IGNORE_SOURCE_USER
        creatorUserId != null && creatorUserId != currentUserId -> UserIsolationDecision.IGNORE_CREATOR_USER
        else -> UserIsolationDecision.PROCESS
    }
}

object CurrentUserScope {
    // Android allocates each user a fixed UID range. The listener's strict gate
    // also compares UserHandle objects, whose equality is identifier-based.
    private const val PER_USER_RANGE = 100_000
    fun id(): Int = Process.myUid() / PER_USER_RANGE

    fun isCurrent(handle: android.os.UserHandle): Boolean = handle == Process.myUserHandle()

    fun creatorUserHandle(pendingIntent: PendingIntent?): android.os.UserHandle? {
        if (pendingIntent == null) return null
        return try {
            pendingIntent.creatorUserHandle
        } catch (_: RuntimeException) {
            // Some system/vendor PendingIntents do not expose a creator handle.
            null
        }
    }

    /** Used only for content-free logging; UserHandle equality makes the decision. */
    fun creatorUserId(pendingIntent: PendingIntent?): Int? =
        creatorUserHandle(pendingIntent)?.hashCode()

    fun accepts(sourceUserId: Int, pendingIntent: PendingIntent?): Boolean =
        sourceUserId == id() && creatorUserHandle(pendingIntent)?.let(::isCurrent) != false

    fun accepts(conv: Conversation): Boolean =
        ConversationStore.get(conv.nid, conv.sourceUserId) === conv && conv.sourceUserId == id() &&
            UserIsolationPolicy.decide(
                conv.sourceUserId,
                id(),
                creatorUserId(conv.contentIntent),
            ) == UserIsolationDecision.PROCESS
}
