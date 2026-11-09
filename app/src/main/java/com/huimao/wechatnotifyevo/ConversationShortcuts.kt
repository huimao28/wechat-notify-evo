package com.huimao.wechatnotifyevo

import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.util.Log

/** Publishes stable long-lived Android conversation shortcuts before notifications. */
object ConversationShortcuts {
    private const val TAG = "WNE.Shortcuts"

    fun shortcutId(conv: Conversation): String = conv.conversationStableId.ifEmpty {
        // Defensive only; runtime assigns an HMAC id before publication. It deliberately
        // excludes routeToken so a security-generation rotation cannot reset settings.
        "conv_pending_${conv.sourceUserId}_${conv.nid}"
    }

    private fun targetIntent(context: Context, conv: Conversation): Intent? =
        if (CurrentUserScope.accepts(conv) && conv.contentIntent != null) trampolineIntent(context, conv)
        else null

    private fun sameTarget(actual: Intent?, expected: Intent): Boolean = actual != null &&
        actual.filterEquals(expected) &&
        actual.getStringExtra("snapshot") == expected.getStringExtra("snapshot") &&
        actual.getIntExtra(BubbleTrampolineActivity.EXTRA_NID, -1) ==
            expected.getIntExtra(BubbleTrampolineActivity.EXTRA_NID, -1) &&
        actual.getIntExtra(BubbleTrampolineActivity.EXTRA_SOURCE_USER_ID, -1) ==
            expected.getIntExtra(BubbleTrampolineActivity.EXTRA_SOURCE_USER_ID, -1)

    fun isTargetPublished(context: Context, conv: Conversation): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val target = targetIntent(context, conv) ?: return false
            context.getSystemService(ShortcutManager::class.java)?.getShortcuts(
                ShortcutManager.FLAG_MATCH_DYNAMIC or ShortcutManager.FLAG_MATCH_CACHED or
                    ShortcutManager.FLAG_MATCH_PINNED
            )?.any { it.id == shortcutId(conv) && sameTarget(it.intent, target) } == true
        } catch (_: RuntimeException) { false }
    }

    /** Returns true only after the platform confirms the long-lived shortcut exists. */
    fun publish(context: Context, conv: Conversation): Boolean {
        if (!CurrentUserScope.accepts(conv) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
            val id = shortcutId(conv)
            if (!id.startsWith(ConversationIdentityCore.PREFIX)) {
                conv.shortcutPushed = false
                return false
            }
            val launch = targetIntent(context, conv) ?: return false
            val already = sm.getShortcuts(ShortcutManager.FLAG_MATCH_DYNAMIC or
                ShortcutManager.FLAG_MATCH_CACHED or ShortcutManager.FLAG_MATCH_PINNED)
                .any { it.id == id && sameTarget(it.intent, launch) }
            if (already) {
                conv.shortcutPushed = true
                return true
            }
            if (sm.isRateLimitingActive) {
                conv.shortcutPushed = false
                conv.learningStatus = "系统 shortcut 限流：本次使用普通消息通知"
                return false
            }
            val avatar = conv.displayLargeIcon(Prefs.isCircularAvatarEnabled(context))
            val person = Person.Builder().setName(conv.title).setKey(ConversationPersonKeys.participant(id))
                .setBot(false).apply { avatar?.let { setIcon(it) } }.build()
            val shortcut = ShortcutInfo.Builder(context, id)
                .setShortLabel(conv.title).setLongLabel(conv.title)
                .setLongLived(true).setIntent(launch).setPersons(arrayOf(person))
                .setCategories(setOf("android.shortcut.conversation"))
                .setLocusId(LocusId(id)).apply { avatar?.let { setIcon(it) } }.build()
            sm.pushDynamicShortcut(shortcut)
            conv.shortcutPushed = isTargetPublished(context, conv)
            conv.shortcutPushed
        } catch (_: RuntimeException) {
            conv.shortcutPushed = false
            Log.w(TAG, "Shortcut publication failed")
            false
        }
    }

    private fun trampolineIntent(context: Context, conv: Conversation): Intent =
        Intent(context, BubbleTrampolineActivity::class.java).setAction(Intent.ACTION_VIEW)
            .putExtra(BubbleTrampolineActivity.EXTRA_NID, conv.nid)
            .putExtra(BubbleTrampolineActivity.EXTRA_SOURCE_USER_ID, conv.sourceUserId)
            .putExtra("snapshot", conv.routeToken)

    /** Remove only a superseded temporary/legacy shortcut; channels are intentionally retained. */
    fun retireSupersededShortcut(context: Context, id: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || id.isBlank()) return
        try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            sm.removeDynamicShortcuts(listOf(id))
            sm.removeLongLivedShortcuts(listOf(id))
        } catch (_: RuntimeException) { Log.w(TAG, "Superseded shortcut cleanup failed") }
    }

    /** A dismissed WeChat notification does not mean the real conversation ceased to exist. */
    fun remove(context: Context, nid: Int, sourceUserId: Int): Boolean = true
    fun removeAll(context: Context, convs: List<Conversation>) = Unit

    /** Preserve cached/long-lived shortcuts: deleting them discards user conversation settings. */
    fun pruneOrphans(context: Context) = Unit
}
