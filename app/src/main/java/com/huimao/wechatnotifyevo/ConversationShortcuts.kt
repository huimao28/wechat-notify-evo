package com.huimao.wechatnotifyevo

import android.app.PendingIntent
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
 * The shortcut intent is the WeChat chat itself, Nevolution-style: when the
 * conversation's WeChat username is known, the intent opens LauncherUI with
 * Main_User set (exact chat, not the WeChat main screen); until then it
 * falls back to the WeChat launcher intent. The system bubble references
 * this shortcut id, so tapping the bubble launches the shortcut intent
 * directly into the bubble window — no trampoline activity, no second-hop
 * forward for the system to silently drop.
 */
object ConversationShortcuts {
    private const val TAG = "WNE.Shortcuts"

    fun shortcutId(nid: Int): String = "conv_$nid"

    fun publish(context: Context, conv: Conversation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            // Learn the WeChat username for this conversation (Nevolution's
            // trick): sending the original content intent delivers the
            // resolved intent to OnFinished, whose Main_User extra is the
            // username. Async, no main-thread block: when it arrives, the
            // shortcut is re-published with the exact-chat intent, and the
            // bubble resolves the shortcut intent at expansion time, so
            // even the current bubble benefits. When bubbles are off, no
            // send happens at all.
            if (conv.wxid == null && conv.contentIntent != null && Prefs.isBubbleEnabled(context)) {
                learnWxidAsync(context, conv)
            }
            // Skip redundant pushes: ShortcutManager rate-limits apps that
            // push too often (Nevolution checks isRateLimitingActive for
            // exactly this reason), and pushing an unchanged shortcut buys
            // nothing. Only push when the intent changed (new conversation,
            // or wxid just learned) or the shortcut is gone (evicted).
            val id = shortcutId(conv.nid)
            val alreadyPublished = conv.shortcutPushed && conv.pushedWxid == conv.wxid &&
                sm.dynamicShortcuts.any { it.id == id }
            if (alreadyPublished) return
            if (sm.isRateLimitingActive) {
                Log.w(TAG, "Rate limited, skip push for nid=" + conv.nid)
                return
            }
            val launch = chatIntent(context, conv) ?: return
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
            conv.pushedWxid = conv.wxid
        } catch (e: RuntimeException) {
            // Conversation status is an enhancement: never let a shortcut
            // failure break notification posting.
            Log.w(TAG, "Failed to push shortcut for nid=" + conv.nid, e)
        }
    }

    /** Exact-chat intent when the username is known, else the WeChat launcher. */
    private fun chatIntent(context: Context, conv: Conversation): Intent? {
        val wxid = conv.wxid
        return if (wxid != null) {
            // Nevolution-style: LauncherUI + Main_User. NO_HISTORY keeps the
            // chat out of recents when opened from a bubble.
            Intent()
                .setClassName(WeChatListenerService.WECHAT_PACKAGE, "com.tencent.mm.ui.LauncherUI")
                .putExtra("Main_User", wxid)
                .putExtra("Intro_Is_Muti_Talker", false)
                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        } else {
            context.packageManager.getLaunchIntentForPackage(WeChatListenerService.WECHAT_PACKAGE)
        }
    }

    private fun learnWxidAsync(context: Context, conv: Conversation) {
        val pi = conv.contentIntent ?: return
        try {
            pi.send(context, 0, null, PendingIntent.OnFinished { _, intent, _, _, _ ->
                val wxid = intent?.getStringExtra("Main_User")
                if (!wxid.isNullOrEmpty() && conv.wxid == null) {
                    conv.wxid = wxid
                    Log.d(TAG, "Learned wxid for nid=${conv.nid}")
                    // Refresh the shortcut with the exact-chat intent.
                    // No recursion: wxid is now set, so this publish skips
                    // learning.
                    publish(context, conv)
                    // Re-post the notification so its bubble metadata is
                    // fresh: a bubble posted before learning keeps the
                    // launcher intent and opens WeChat main even after
                    // the shortcut is updated.
                    if (ConversationStore.get(conv.nid) === conv) {
                        WeChatListenerService.repostConversation(context, conv, silent = true)
                        Log.d(TAG, "Reposted after learning wxid for nid=${conv.nid}")
                    }
                } else {
                    Log.w(TAG, "No Main_User for nid=${conv.nid}, launcher fallback stays")
                }
            }, null)
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Cannot learn wxid: contentIntent canceled")
        } catch (e: Exception) {
            Log.w(TAG, "Learn wxid failed", e)
        }
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
