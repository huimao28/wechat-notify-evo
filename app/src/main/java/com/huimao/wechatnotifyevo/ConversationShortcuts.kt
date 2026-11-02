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
 * on user interaction. Optional control: the exported public WeChat launcher,
 * without conversation extras. Launcher control does not route to a chat.
 * Neither route guarantees that the system embeds WeChat inside a bubble.
 */
object ConversationShortcuts {
    private const val TAG = "WNE.Shortcuts"

    fun shortcutId(nid: Int): String = "conv_$nid"

    /** Resolve only an exported, unprotected MAIN/LAUNCHER entry. No chat extras. */
    private fun targetIntent(context: Context, conv: Conversation): Intent? {
        if (!Prefs.isLauncherControlEnabled(context)) return trampolineIntent(context, conv)
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

    /** Do not attach a bubble to a stale target after a failed/rate-limited switch. */
    fun isTargetPublished(context: Context, conv: Conversation): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val target = targetIntent(context, conv) ?: return false
            context.getSystemService(ShortcutManager::class.java)?.dynamicShortcuts
                ?.any { it.id == shortcutId(conv.nid) && it.intent?.filterEquals(target) == true } == true
        } catch (e: RuntimeException) {
            false
        }
    }

    fun publish(context: Context, conv: Conversation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
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
                sm.dynamicShortcuts.any { it.id == id && it.intent?.filterEquals(launch) == true }
            if (alreadyPublished) return
            if (sm.isRateLimitingActive) {
                Log.w(TAG, "Rate limited, skip push for nid=" + conv.nid)
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
            conv.shortcutPushed = true
        } catch (e: RuntimeException) {
            // Conversation status is an enhancement: never let a shortcut
            // failure break notification posting.
            Log.w(TAG, "Failed to push shortcut for nid=" + conv.nid, e)
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
    }

    fun remove(context: Context, nid: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        val id = shortcutId(nid)
        sm.removeDynamicShortcuts(listOf(id))
        sm.removeLongLivedShortcuts(listOf(id))
    }

    fun removeAll(context: Context, convs: List<Conversation>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        val ids = convs.map { shortcutId(it.nid) }
        if (ids.isEmpty()) return
        sm.removeDynamicShortcuts(ids)
        sm.removeLongLivedShortcuts(ids)
    }
}
