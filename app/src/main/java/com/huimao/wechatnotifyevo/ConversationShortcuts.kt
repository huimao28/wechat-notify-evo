package com.huimao.wechatnotifyevo

import android.app.Person
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build

/**
 * System conversation support (Android 11+): each evolved conversation gets
 * a long-lived shortcut so the system treats its notification as a real
 * conversation — conversation section in the shade and per-conversation
 * priority/silent settings that survive reboots. Below Android 11 these
 * calls are no-ops and notifications behave exactly as before.
 *
 * The shortcut intent is WeChat's launcher intent: the notification's own
 * contentIntent (which opens the exact chat) stays on the notification
 * itself, so tapping the notification still lands in the right chat.
 */
object ConversationShortcuts {
    fun shortcutId(nid: Int): String = "conv_$nid"

    fun publish(context: Context, conv: Conversation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            val launch = context.packageManager
                .getLaunchIntentForPackage(WeChatListenerService.WECHAT_PACKAGE) ?: return
            val person = Person.Builder()
                .setName(conv.title)
                .setKey("person_${conv.nid}")
                .apply { conv.largeIcon?.let { setIcon(it) } }
                .build()
            val shortcut = ShortcutInfo.Builder(context, shortcutId(conv.nid))
                .setShortLabel(conv.title)
                .setLongLabel(conv.title)
                .setLongLived(true)
                .setIntent(launch)
                .setPersons(arrayOf(person))
                .apply { conv.largeIcon?.let { setIcon(it) } }
                .build()
            // pushDynamicShortcut evicts the lowest-ranked shortcut when
            // the system limit is reached, so no manual LRU is needed.
            sm.pushDynamicShortcut(shortcut)
        } catch (e: RuntimeException) {
            // Conversation status is an enhancement: never let a shortcut
            // failure break notification posting.
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
