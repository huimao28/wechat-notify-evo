package com.huimao.wechatnotifyevo

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Memory-only association. Only ShortcutManager receives a locally persisted route. */
object ManualConversationLearning {
    var status by mutableStateOf("尚未手动学习；学习会打开微信")
        private set
    var busy by mutableStateOf(false)
        private set
    private var request = 0L

    // Called only by the confirmation button in a visible Activity, never by the listener.
    fun learn(activity: Activity, conv: Conversation, token: String) {
        val pi = conv.contentIntent
        val key = conv.notificationKey
        if (busy) return
        if (pi == null || !conv.isCurrentSnapshot(token, key, pi) ||
            pi.creatorPackage != WeChatListenerService.WECHAT_PACKAGE) {
            status = "通知已变化或入口不可用；请等待新通知并重新选择"
            return
        }
        val id = ++request
        val handler = Handler(Looper.getMainLooper())
        busy = true
        status = "已请求打开微信，等待当前会话回调（不代表直达成功）"
        val timeout = Runnable {
            if (request == id && busy) {
                busy = false
                ++request // Reject late callbacks, not just their UI updates.
                status = "5 秒内未收到回调；本次已失效，可重新手动学习"
            }
        }
        handler.postDelayed(timeout, 5_000)
        try {
            val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle() else null
            pi.send(activity, 0, null, { returnedPi, intent, _, _, _ ->
                if (request == id && busy) {
                    busy = false
                    handler.removeCallbacks(timeout)
                    if (!conv.isCurrentSnapshot(token, key, pi) || returnedPi != pi) {
                        status = "回调对应的通知已失效；未绑定，请等待新通知后重新学习"
                    } else {
                        val user = try {
                            @Suppress("DEPRECATION")
                            (intent?.extras?.get("Main_User") as? String)?.takeIf { it.isNotEmpty() }
                        } catch (_: RuntimeException) { null }
                        if (user == null) {
                            status = "回调未提供非空 String Main_User；未绑定"
                        } else {
                            conv.learnedMainUser = user
                            conv.shortcutPushed = false
                            conv.learningStatus = "已学习当前快照；请返回应用刷新 shortcut（尚未验证直达）"
                            status = conv.learningStatus
                            // Do not start another activity or re-send PI from the callback.
                            // Explicit foreground refresh is more reliable under shortcut rate limits.
                        }
                    }
                }
            }, handler, null, options)
        } catch (_: Exception) {
            handler.removeCallbacks(timeout)
            busy = false
            ++request
            status = "原通知入口发送失败或已取消；未绑定，请等待新通知"
        }
    }

    fun refresh(context: Context) {
        ConversationShortcuts.pruneOrphans(context)
        val current = ConversationStore.all()
        if (current.isEmpty()) {
            status = "无当前通知；请等待新通知并重新学习"
            return
        }
        current.forEach { WeChatListenerService.repostConversation(context, it, silent = true) }
        val learned = current.filter { it.learnedMainUser != null }
        status = if (learned.isEmpty()) "当前通知未学习（旧快照可能已失效）；请手动选择会话学习" else
            learned.map { it.learningStatus }.distinct().joinToString("\n")
    }

    fun forget(context: Context) {
        ++request
        busy = false
        ConversationStore.all().forEach {
            ConversationShortcuts.remove(context, it.nid)
            it.snapshotToken = java.util.UUID.randomUUID().toString()
            it.learnedMainUser = null
            it.shortcutPushed = false
            it.learningStatus = "已清除学习结果"
            WeChatListenerService.repostConversation(context, it, silent = true)
        }
        status = "已清除内存学习结果并请求移除本地会话 shortcut；已打开的旧气泡请手动关闭"
    }
}
