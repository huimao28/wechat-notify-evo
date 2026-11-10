package com.huimao.wechatnotifyevo

import android.app.Person
import android.content.ComponentName
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
    private const val MATCH_OWN_SHORTCUTS = ShortcutManager.FLAG_MATCH_DYNAMIC or
        ShortcutManager.FLAG_MATCH_CACHED or ShortcutManager.FLAG_MATCH_PINNED

    fun shortcutId(conv: Conversation): String = conv.conversationStableId.ifEmpty {
        // Defensive only; runtime assigns an HMAC id before publication. It deliberately
        // excludes routeToken so a security-generation rotation cannot reset settings.
        "conv_pending_${conv.sourceUserId}_${conv.nid}"
    }

    private fun launcherComponent() = ComponentName(
        ShortcutTargetCore.WECHAT_PACKAGE, ShortcutTargetCore.WECHAT_LAUNCHER_CLASS
    )

    private fun launcherIsUsable(context: Context): Boolean {
        val info = try { context.packageManager.getActivityInfo(launcherComponent(), 0) }
        catch (_: Exception) { null } ?: return false
        return ShortcutTargetCore.LauncherAccess(
            info.exported, info.enabled, info.applicationInfo.enabled, info.permission
        ).usable
    }

    /** Restores only from entries owned and returned by this app's ShortcutManager. */
    private fun restoreLearnedMainUser(sm: ShortcutManager, conv: Conversation) {
        if (conv.learnedMainUser != null || conv.conversationStableId.isBlank()) return
        val own = try { sm.getShortcuts(MATCH_OWN_SHORTCUTS) } catch (_: RuntimeException) { return }
        val records = own.map { shortcut ->
            val intent = shortcut.intent
            ShortcutTargetCore.OwnShortcut(
                shortcut.id,
                intent?.component?.packageName,
                intent?.component?.className,
                try { intent?.getStringExtra(ShortcutTargetCore.MAIN_USER_EXTRA) }
                catch (_: RuntimeException) { null },
            )
        }
        ShortcutTargetCore.restorableMainUser(shortcutId(conv), records)?.let {
            conv.learnedMainUser = it
            conv.autoLearningConsidered = true
            conv.learningStatus = "已从本机系统 Shortcut 恢复直达会话"
        }
    }

    private fun targetIntent(context: Context, conv: Conversation, sm: ShortcutManager? = null): Intent? {
        if (!CurrentUserScope.accepts(conv) || conv.contentIntent == null) return null
        val directEnabled = Prefs.isLauncherControlEnabled(context) && Prefs.isDirectChatEnabled(context)
        if (directEnabled) sm?.let { restoreLearnedMainUser(it, conv) }
        return if (ShortcutTargetCore.kind(
                conv.learnedMainUser, directEnabled, launcherIsUsable(context)
            ) == ShortcutTargetCore.Kind.DIRECT
        ) directIntent(requireNotNull(conv.learnedMainUser)) else trampolineIntent(context, conv)
    }

    private fun directIntent(mainUser: String): Intent = Intent(Intent.ACTION_MAIN)
        .setComponent(launcherComponent())
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .putExtra(ShortcutTargetCore.MAIN_USER_EXTRA, mainUser)
        .putExtra(ShortcutTargetCore.MULTI_TALKER_EXTRA, false)

    private fun targetOf(intent: Intent?): ShortcutTargetCore.Target? = intent?.let {
        ShortcutTargetCore.Target(
            componentPackage = it.component?.packageName ?: return null,
            componentClass = it.component?.className ?: return null,
            action = it.action.orEmpty(),
            categories = it.categories ?: emptySet(),
            mainUser = try { it.getStringExtra(ShortcutTargetCore.MAIN_USER_EXTRA) }
                catch (_: RuntimeException) { null },
            introIsMultiTalker = if (it.hasExtra(ShortcutTargetCore.MULTI_TALKER_EXTRA)) {
                try { it.getBooleanExtra(ShortcutTargetCore.MULTI_TALKER_EXTRA, true) }
                catch (_: RuntimeException) { null }
            } else null,
            snapshot = try { it.getStringExtra("snapshot") } catch (_: RuntimeException) { null },
            nid = if (it.hasExtra(BubbleTrampolineActivity.EXTRA_NID))
                it.getIntExtra(BubbleTrampolineActivity.EXTRA_NID, -1) else null,
            sourceUserId = if (it.hasExtra(BubbleTrampolineActivity.EXTRA_SOURCE_USER_ID))
                it.getIntExtra(BubbleTrampolineActivity.EXTRA_SOURCE_USER_ID, -1) else null,
        )
    }

    private fun sameTarget(actual: Intent?, expected: Intent): Boolean =
        ShortcutTargetCore.sameTarget(targetOf(actual), requireNotNull(targetOf(expected)))

    fun isTargetPublished(context: Context, conv: Conversation): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
            val target = targetIntent(context, conv, sm) ?: return false
            sm.getShortcuts(MATCH_OWN_SHORTCUTS)
                .any { it.id == shortcutId(conv) && sameTarget(it.intent, target) }
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
            val launch = targetIntent(context, conv, sm) ?: return false
            val already = sm.getShortcuts(MATCH_OWN_SHORTCUTS)
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
