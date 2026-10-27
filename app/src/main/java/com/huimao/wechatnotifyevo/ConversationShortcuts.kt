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
 * The shortcut intent is our trampoline, not WeChat directly: learning the
 * WeChat username (Nevolution's contentIntent.send + OnFinished trick)
 * produces nothing on Android 17 — the background send's callback never
 * fires (WNE.Shortcuts log stays empty after a fresh message), so the
 * shortcut would stay on the WeChat launcher intent and the bubble would
 * open WeChat main. The trampoline instead forwards WeChat's own content
 * intent at tap time (a user gesture, so the trampoline is foreground and
 * its send is not background-blocked); tapping the evolved notification
 * opens the exact chat, so the trampoline does too. The system bubble
 * references this shortcut id.
 */
object ConversationShortcuts {
    private const val TAG = "WNE.Shortcuts"

    fun shortcutId(nid: Int): String = "conv_$nid"

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
            val alreadyPublished = conv.shortcutPushed &&
                sm.dynamicShortcuts.any { it.id == id }
            if (alreadyPublished) return
            if (sm.isRateLimitingActive) {
                Log.w(TAG, "Rate limited, skip push for nid=" + conv.nid)
                return
            }
            val launch = trampolineIntent(context, conv)
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
     * content intent is looked up by nid at tap time; the extra is only a
     * fallback for a conversation that left the store.
     */
    private fun trampolineIntent(context: Context, conv: Conversation): Intent {
        return Intent(context, BubbleTrampolineActivity::class.java)
            .putExtra(BubbleTrampolineActivity.EXTRA_NID, conv.nid)
            .apply {
                conv.contentIntent?.let {
                    putExtra(BubbleTrampolineActivity.EXTRA_TARGET, it)
                }
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
