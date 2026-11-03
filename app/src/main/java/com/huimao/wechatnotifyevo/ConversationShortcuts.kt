package com.huimao.wechatnotifyevo

import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.util.Log

/**
 * System conversation support (Android 11+): each evolved conversation gets
 * a long-lived shortcut so the system treats its notification as a real
 * conversation — conversation section in the shade and per-conversation
 * priority/silent settings that survive reboots. Below Android 11 these
 * calls are no-ops and notifications behave exactly as before.
 *
 * Default: our trampoline forwards the opaque notification PendingIntent
 * on user interaction. Optional direct mode uses the exported public launcher:
 * without learning it opens home; after explicit learning it includes Main_User.
 * Association and shortcut identity are scoped to one notification snapshot.
 * Neither route guarantees that the system embeds WeChat inside a bubble.
 */
object ConversationShortcuts {
    private const val TAG = "WNE.Shortcuts"

    fun shortcutId(nid: Int): String = ConversationStore.get(nid)?.let {
        "conv_${it.routeToken}"
    } ?: "conv_$nid"

    /** Public APIs only. No hidden PendingIntent introspection or private activity. */
    private fun targetIntent(context: Context, conv: Conversation): Intent? {
        if (ConversationStore.get(conv.nid) !== conv) return null
        if (!Prefs.isLauncherControlEnabled(context)) return trampolineIntent(context, conv)
        val learned = conv.learnedMainUser
        if (learned != null) {
            val component = android.content.ComponentName(
                WeChatListenerService.WECHAT_PACKAGE, "com.tencent.mm.ui.LauncherUI")
            val info = try { context.packageManager.getActivityInfo(component, 0) }
                catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
            if (info != null && info.exported && info.enabled &&
                info.applicationInfo.enabled && info.permission.isNullOrEmpty()) {
                return Intent(Intent.ACTION_MAIN).setComponent(component)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .putExtra("Main_User", learned)
                    .putExtra("Intro_Is_Muti_Talker", false)
            }
            conv.learningStatus = "已学习，但 LauncherUI 不可用；回退原通知中转"
            return trampolineIntent(context, conv)
        }
        val query = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(WeChatListenerService.WECHAT_PACKAGE)
        val info = context.packageManager.queryIntentActivities(query, 0)
            .mapNotNull { it.activityInfo }
            .firstOrNull {
                it.packageName == WeChatListenerService.WECHAT_PACKAGE &&
                    it.exported && it.enabled && it.applicationInfo.enabled &&
                    it.permission.isNullOrEmpty()
            } ?: return null
        return Intent(query).setClassName(info.packageName, info.name)
    }

    // Intent.filterEquals ignores extras; learned/unlearned launcher targets
    // must not compare equal, nor may an old trampoline generation be reused.
    private fun sameTarget(actual: Intent?, expected: Intent): Boolean = actual != null &&
        actual.filterEquals(expected) &&
        actual.getStringExtra("Main_User") == expected.getStringExtra("Main_User") &&
        actual.hasExtra("Intro_Is_Muti_Talker") == expected.hasExtra("Intro_Is_Muti_Talker") &&
        actual.getBooleanExtra("Intro_Is_Muti_Talker", false) ==
            expected.getBooleanExtra("Intro_Is_Muti_Talker", false) &&
        actual.getStringExtra("snapshot") == expected.getStringExtra("snapshot")

    /** Do not attach a bubble to a stale target after a failed/rate-limited switch. */
    fun isTargetPublished(context: Context, conv: Conversation): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val target = targetIntent(context, conv) ?: return false
            context.getSystemService(ShortcutManager::class.java)?.dynamicShortcuts
                ?.any { it.id == shortcutId(conv.nid) && sameTarget(it.intent, target) } == true
        } catch (e: RuntimeException) {
            false
        }
    }

    fun publish(context: Context, conv: Conversation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !Prefs.isDirectChatEnabled(context)) return
        try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            // Skip redundant pushes: ShortcutManager rate-limits apps that
            // push too often (Nevolution checks isRateLimitingActive for
            // exactly this reason). The trampoline intent is stable per
            // conversation (it looks the live content intent up by nid at
            // tap time), so a push is only needed once, or again if the
            // shortcut is gone (evicted).
            val id = shortcutId(conv.nid)
            val launch = targetIntent(context, conv)
            if (launch == null) {
                Log.w(TAG, "Public launcher unavailable; no control bubble published")
                return
            }
            val alreadyPublished = conv.shortcutPushed &&
                sm.dynamicShortcuts.any { it.id == id && sameTarget(it.intent, launch) }
            if (alreadyPublished) return
            if (sm.isRateLimitingActive) {
                conv.shortcutPushed = false
                conv.learningStatus = "系统 shortcut 限流：尚未更新；请稍后手动刷新（不要重复学习）"
                Log.w(TAG, "Shortcut refresh rate limited")
                return
            }
            val person = Person.Builder()
                .setName(conv.title)
                .setKey("person_${conv.nid}")
                .apply { conv.largeIcon?.let { setIcon(it) } }
                .build()
            val shortcut = ShortcutInfo.Builder(context, id)
                .setShortLabel(conv.title)
                .setLongLabel(conv.title)
                .setLongLived(true)
                .setIntent(launch)
                .setPersons(arrayOf(person))
                .setCategories(setOf("android.shortcut.conversation"))
                .setLocusId(LocusId(shortcutId(conv.nid)))
                .apply { conv.largeIcon?.let { setIcon(it) } }
                .build()
            // pushDynamicShortcut evicts the lowest-ranked shortcut when
            // the system limit is reached, so no manual LRU is needed.
            sm.pushDynamicShortcut(shortcut)
            conv.shortcutPushed = isTargetPublished(context, conv)
            conv.learningStatus = when {
                !conv.shortcutPushed -> "shortcut 尚未确认发布；请稍后刷新"
                conv.learnedMainUser == null -> "等待学习：当前入口仅打开微信首页"
                !Prefs.isLauncherControlEnabled(context) -> "已学习；气泡内直达聊天未开启"
                launch.component?.className != "com.tencent.mm.ui.LauncherUI" -> "已学习；LauncherUI 不可用，已回退中转"
                else -> "已学习并发布会话 shortcut；关闭旧气泡后可从当前通知重开"
            }
        } catch (e: RuntimeException) {
            // Conversation status is an enhancement: never let a shortcut
            // failure break notification posting.
            conv.shortcutPushed = false
            conv.learningStatus = "shortcut 更新失败；请稍后刷新（未确认直达）"
            Log.w(TAG, "Shortcut update failed")
        }
    }

    /**
     * The bubble opens our trampoline, which forwards WeChat's own content
     * intent: tapping the evolved notification opens the exact chat, so the
     * trampoline does too, with no username learning in between. The live
     * content intent is looked up by nid at tap time. Only the nid travels
     * in the shortcut intent: shortcut intents are persisted by the system,
     * and a PendingIntent extra is not persistable — it can keep the
     * shortcut from being created at all, which drops the bubble.
     */
    private fun trampolineIntent(context: Context, conv: Conversation): Intent {
        // ShortcutInfo.Builder.setIntent() requires a non-null action
        // (it throws NullPointerException "intent's action must be set"
        // otherwise, which silently drops the whole shortcut — and the
        // bubble with it). ACTION_VIEW fits "view this conversation".
        return Intent(context, BubbleTrampolineActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(BubbleTrampolineActivity.EXTRA_NID, conv.nid)
            .putExtra("snapshot", conv.routeToken)
    }

    private fun removeIds(context: Context, ids: List<String>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || ids.isEmpty()) return true
        return try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
            sm.disableShortcuts(ids) // Also make any pinned copies unavailable.
            sm.removeDynamicShortcuts(ids)
            sm.removeLongLivedShortcuts(ids)
            true
        } catch (_: RuntimeException) {
            Log.w(TAG, "Shortcut cleanup failed; will retry on next refresh")
            false
        }
    }

    fun remove(context: Context, nid: Int): Boolean =
        removeIds(context, listOf(shortcutId(nid), "conv_$nid").distinct())

    fun removeAll(context: Context, convs: List<Conversation>) {
        removeIds(context, convs.flatMap { listOf(shortcutId(it.nid), "conv_${it.nid}") }.distinct())
    }

    fun pruneOrphans(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            val live = ConversationStore.all().map { shortcutId(it.nid) }.toSet()
            val stale = sm.getShortcuts(ShortcutManager.FLAG_MATCH_DYNAMIC or
                ShortcutManager.FLAG_MATCH_CACHED or ShortcutManager.FLAG_MATCH_PINNED)
                .map { it.id }.filter { it.startsWith("conv_") && it !in live }
            removeIds(context, stale)
        } catch (_: RuntimeException) {
            Log.w(TAG, "Shortcut cleanup query failed")
        }
    }
}
