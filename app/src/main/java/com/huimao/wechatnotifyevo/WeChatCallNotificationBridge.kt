package com.huimao.wechatnotifyevo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log

/** Mirrors eligible WeChat call notifications as the public Android CallStyle template. */
object WeChatCallNotificationBridge {
    private const val CHANNEL_ID = "wechat_calls_v1"
    private const val CHANNEL_NAME = "微信来电"
    private const val TAG = "WNE.CallStyle"
    private const val ID_BASE = 0x5ca11000
    private val activeKeys = linkedSetOf<String>()

    fun notificationId(key: String): Int = ID_BASE xor key.hashCode()

    @Synchronized
    fun cancel(context: Context, key: String) {
        activeKeys.remove(key)
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(key))
    }

    @Synchronized
    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        activeKeys.forEach { nm.cancel(notificationId(it)) }
        activeKeys.clear()
    }

    @Suppress("DEPRECATION")
    fun mirrorIfSupported(context: Context, sbn: StatusBarNotification): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val source = sbn.notification ?: return false
        val extras = source.extras ?: return false
        val actions = source.actions.orEmpty()
        val candidates = actions.mapIndexed { index, action ->
            CallActionCandidate(
                index = index,
                label = action.title?.toString().orEmpty(),
                hasIntent = action.actionIntent != null,
                semanticAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) action.semanticAction else 0,
            )
        }
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val answerExtra = extras.pendingIntent("android.answerIntent")
        val declineExtra = extras.pendingIntent("android.declineIntent")
        val hangUpExtra = extras.pendingIntent("android.hangUpIntent")
        val decision = WeChatCallPolicy.decide(
            categoryCall = source.category == Notification.CATEGORY_CALL,
            callStyleTemplate = template.contains("CallStyle", ignoreCase = true),
            hasAnswerExtra = answerExtra != null,
            hasDeclineExtra = declineExtra != null,
            hasHangUpExtra = hangUpExtra != null,
            hasCallPerson = extras.containsKey("android.callPerson"),
            channelId = source.channelId.orEmpty(),
            titleAndText = "$title $text",
            actions = candidates,
        ) ?: return false

        fun actionIntent(index: Int?): PendingIntent? = index?.let { actions.getOrNull(it)?.actionIntent }
        val answer = answerExtra ?: actionIntent(decision.answerIndex)
        val decline = declineExtra ?: actionIntent(decision.declineIndex)
        val hangUp = hangUpExtra ?: actionIntent(decision.hangUpIndex)
        val style = when (decision.phase) {
            WeChatCallPhase.INCOMING -> {
                // Never publish fake controls: both operations must be public intents supplied by WeChat.
                if (answer == null || decline == null) {
                    Log.i(TAG, "Call detected but controls incomplete: answer=${answer != null} decline=${decline != null} actions=${candidates.map { \"${it.index}:${it.label}:${it.semanticAction}:${it.hasIntent}\" }} fullScreen=${source.fullScreenIntent != null}; original left untouched")
                    return false
                }
                Notification.CallStyle.forIncomingCall(person(source, title), decline, answer)
            }
            WeChatCallPhase.ONGOING -> {
                if (hangUp == null) {
                    Log.i(TAG, "Ongoing call detected but hang-up PendingIntent is absent; original left untouched")
                    return false
                }
                Notification.CallStyle.forOngoingCall(person(source, title), hangUp)
            }
        }

        ensureChannel(context)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.huimao.wechatnotifyevo.R.drawable.ic_stat_wechat)
            .setCategory(Notification.CATEGORY_CALL)
            .setStyle(style)
            .setContentTitle(title.ifBlank { "微信通话" })
            .setContentText(text)
            .setContentIntent(source.fullScreenIntent ?: source.contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(decision.phase == WeChatCallPhase.ONGOING)
            .setWhen(sbn.postTime)
            .setShowWhen(true)
        // Do not request USE_FULL_SCREEN_INTENT on behalf of WeChat. CallStyle still
        // receives system call ranking/heads-up treatment, while WeChat keeps the
        // authoritative lock-screen/full-screen entry point.
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val posted = runCatching {
            manager.notify(notificationId(sbn.key), builder.build())
            true
        }.onFailure { Log.w(TAG, "Failed to publish CallStyle mirror", it) }.getOrDefault(false)
        if (!posted) return false
        synchronized(this) { activeKeys.add(sbn.key) }
        Log.i(TAG, "Published ${decision.phase} CallStyle for key=${sbn.key}")
        return true
    }

    private fun person(source: Notification, fallbackName: String): Person {
        val sourcePerson = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            source.extras?.getParcelable("android.callPerson", Person::class.java)
        } else {
            @Suppress("DEPRECATION") source.extras?.getParcelable("android.callPerson")
        }
        if (sourcePerson != null) return sourcePerson
        val icon = source.getLargeIcon() ?: source.smallIcon
        return Person.Builder()
            .setName(fallbackName.ifBlank { "微信联系人" })
            .setIcon(icon)
            .setImportant(true)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "微信语音和视频来电的系统 CallStyle 通知"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        })
    }

    @Suppress("DEPRECATION")
    private fun android.os.Bundle.pendingIntent(key: String): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, PendingIntent::class.java)
        } else {
            getParcelable(key)
        }
}
