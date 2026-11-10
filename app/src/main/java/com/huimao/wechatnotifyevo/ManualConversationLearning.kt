package com.huimao.wechatnotifyevo

import android.content.Context
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
    private val requests = LearningRequestGate()
    private val automatic = AutoLearningPolicy()

    private fun automaticFailed(conv: Conversation, stage: DiagnosticStage) {
        automatic.failed(conv)
        InMemoryDiagnostics.record(conv.sourceUserId, stage)
    }

    fun cancelPending() {
        requests.cancel()
        busy = false
        status = "在途学习已取消；不会应用旧回调"
    }

    fun considerAutomatic(context: Context, conv: Conversation) {
        if (!CurrentUserScope.accepts(conv)) {
            InMemoryDiagnostics.record(conv.sourceUserId, DiagnosticStage.AUTO_LEARN_SKIP)
            return
        }
        when (automatic.decide(conv, Prefs.isAutoLearningEnabled(context), busy,
                android.os.SystemClock.elapsedRealtime())) {
            AutoLearningDecision.ADMITTED -> Unit
            AutoLearningDecision.BUSY -> {
                status = "自动学习忙碌：保留到下一条新通知重试"
                InMemoryDiagnostics.record(conv.sourceUserId, DiagnosticStage.AUTO_LEARN_BUSY)
                return
            }
            AutoLearningDecision.RATE_LIMITED -> {
                status = "自动学习限频：保留到下一条新通知重试"
                InMemoryDiagnostics.record(conv.sourceUserId, DiagnosticStage.AUTO_LEARN_RATE_LIMITED)
                return
            }
            else -> {
                InMemoryDiagnostics.record(conv.sourceUserId, DiagnosticStage.AUTO_LEARN_SKIP)
                return
            }
        }
        InMemoryDiagnostics.record(conv.sourceUserId, DiagnosticStage.AUTO_LEARN_STARTED)
        learn(context, conv, conv.snapshotToken, auto = true)
    }

    // Automatic calls use the Service context and NO sender-side BAL grant.
    fun learn(activity: Context, conv: Conversation, token: String, auto: Boolean = false) {
        if (!CurrentUserScope.accepts(conv)) {
            status = "仅允许学习应用所在用户空间的主微信通知"
            if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_RESULT)
            return
        }
        if (auto && !Prefs.isAutoLearningEnabled(activity)) {
            automatic.failed(conv)
            return
        }
        val pi = conv.contentIntent
        val key = conv.notificationKey
        if (busy) {
            if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_BUSY)
            return
        }
        if (pi == null || !conv.isCurrentSnapshot(token, key, pi) ||
            pi.creatorPackage != WeChatListenerService.WECHAT_PACKAGE ||
            !CurrentUserScope.accepts(conv.sourceUserId, pi)) {
            status = "通知已变化或入口不可用；下一条新通知可重试"
            if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_RESULT)
            return
        }
        val id = requests.begin() ?: run {
            if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_BUSY)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        busy = true
        status = "已请求打开微信，等待当前会话回调（不代表直达成功）"
        val timeout = Runnable {
            if (requests.finish(id)) {
                busy = false
                requests.cancel() // Reject late callbacks, not just their UI updates.
                status = if (auto) "自动学习超时；下一条新通知可重试" else
                    "5 秒内未收到回调；本次已失效，可重新手动学习"
                if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_TIMEOUT)
            }
        }
        handler.postDelayed(timeout, 5_000)
        try {
            pi.send(activity, 0, null, { returnedPi, intent, _, _, _ ->
                if (requests.finish(id)) {
                    busy = false
                    handler.removeCallbacks(timeout)
                    if ((auto && (!Prefs.isAutoLearningEnabled(activity) || !Prefs.isEnabled(activity))) ||
                        !conv.isCurrentSnapshot(token, key, pi) || returnedPi != pi) {
                        status = "回调对应的通知已失效；下一条新通知可重试"
                        if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_RESULT)
                    } else {
                        val user = try {
                            @Suppress("DEPRECATION")
                            (intent?.extras?.get("Main_User") as? String)?.takeIf { it.isNotEmpty() }
                        } catch (_: RuntimeException) { null }
                        if (user == null) {
                            status = if (auto) "自动学习回调缺少会话标识；下一条新通知可重试" else
                                "回调未提供非空 String Main_User；未绑定"
                            if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_MISSING_MAIN_USER)
                        } else {
                            val supersededId = conv.conversationStableId
                            conv.learnedMainUser = user
                            conv.conversationStableId = ConversationIdentityStore.bindMainUser(
                                activity, conv, user
                            )
                            conv.conversationIdentityKind = ConversationIdentityStore.Kind.STABLE
                            conv.shortcutPushed = false
                            if (supersededId != conv.conversationStableId) {
                                ConversationShortcuts.retireSupersededShortcut(activity, supersededId)
                            }
                            conv.learningStatus = "已学习并绑定稳定会话身份；正在刷新 shortcut"
                            status = conv.learningStatus
                            // Publish once, never send another PI or launch an Activity.
                            if (auto) {
                                ConversationShortcuts.publish(activity, conv)
                                if (!ConversationShortcuts.isTargetPublished(activity, conv)) {
                                    conv.shortcutPushed = false
                                    conv.learningStatus = "稳定身份已保存；shortcut 暂未发布，本次使用普通消息通知"
                                    WeChatListenerService.repostConversation(activity, conv, silent = true)
                                } else {
                                    WeChatListenerService.repostConversation(activity, conv, silent = true)
                                }
                                status = conv.learningStatus
                                InMemoryDiagnostics.record(conv.sourceUserId, DiagnosticStage.AUTO_LEARN_SUCCESS)
                            }
                        }
                    }
                }
            }, handler)
        } catch (_: Exception) {
            handler.removeCallbacks(timeout)
            busy = false
            requests.cancel()
            status = if (auto) "自动学习发送失败；下一条新通知可重试" else
                "原通知入口发送失败或已取消；未绑定，请等待新通知"
            if (auto) automaticFailed(conv, DiagnosticStage.AUTO_LEARN_SEND_FAILED)
        }
    }

    fun refresh(context: Context) {
        ConversationShortcuts.pruneOrphans(context)
        val current = ConversationStore.allForUser(CurrentUserScope.id())
            .filter(CurrentUserScope::accepts)
        if (current.isEmpty()) {
            status = "无当前通知；请等待新通知并重新学习"
            return
        }
        current.forEach { WeChatListenerService.repostConversation(context, it, silent = true) }
        val learned = current.filter { it.learnedMainUser != null }
        status = if (learned.isEmpty()) "当前通知未学习（旧快照可能已失效）；请手动选择会话学习" else
            learned.map { it.learningStatus }.distinct().joinToString("\n")
    }

    fun forget(context: Context, repostNotifications: Boolean = true) {
        requests.cancel()
        busy = false
        ConversationStore.allForUser(CurrentUserScope.id())
            .filter(CurrentUserScope::accepts).forEach {
            ConversationShortcuts.remove(context, it.nid, it.sourceUserId)
            it.snapshotToken = java.util.UUID.randomUUID().toString()
            it.routeToken = java.util.UUID.randomUUID().toString()
            it.learnedMainUser = null
            it.shortcutPushed = false
            it.directChatEligible = false
            it.learningStatus = "已清除学习结果"
            if (repostNotifications) {
                WeChatListenerService.repostConversation(context, it, silent = true)
            }
        }
        status = if (repostNotifications) {
            "已清除内存学习结果并请求移除本地会话 shortcut；已打开的旧气泡请手动关闭"
        } else {
            "已关闭直达聊天并清理 shortcut；请等待下一条新消息"
        }
    }
}
