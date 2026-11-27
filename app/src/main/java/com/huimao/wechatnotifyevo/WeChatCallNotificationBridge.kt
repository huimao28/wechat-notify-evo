package com.huimao.wechatnotifyevo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log

/** Mirrors eligible WeChat call notifications as the public Android CallStyle template. */
object WeChatCallNotificationBridge {
    private const val CHANNEL_ID = "wechat_calls_v1"
    private const val CHANNEL_NAME = "微信来电"
    private const val TAG = "WNE.CallStyle"
    private const val CALL_NOTIFICATION_ID = 0x5ca11001
    private const val DIAGNOSTIC_ID = 0x5ca11fff

    fun notificationId(@Suppress("UNUSED_PARAMETER") key: String): Int = CALL_NOTIFICATION_ID

    fun cancel(context: Context, @Suppress("UNUSED_PARAMETER") key: String = "") {
        context.getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIFICATION_ID)
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(CALL_NOTIFICATION_ID)
        nm.cancel(DIAGNOSTIC_ID)
    }

    @Suppress("DEPRECATION")
    fun mirrorIfSupported(context: Context, sbn: StatusBarNotification): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "SKIP unsupportedSdk sdk=${Build.VERSION.SDK_INT} key=${sbn.key}")
            return false
        }
        Log.w(TAG, "ENTER key=${sbn.key} id=${sbn.id} user=${sbn.userId}")
        val result = runCatching { mirrorInternal(context, sbn) }
            .onFailure { error ->
                Log.e(TAG, "FAIL key=${sbn.key} error=${error.javaClass.simpleName}:${error.message}", error)
                publishDiagnostic(context, sbn.key, error)
            }
            .getOrDefault(false)
        Log.w(TAG, "EXIT key=${sbn.key} mirrored=$result")
        return result
    }

    @Suppress("DEPRECATION")
    private fun mirrorInternal(context: Context, sbn: StatusBarNotification): Boolean {
        val source = sbn.notification ?: run {
            Log.w(TAG, "SKIP noNotification key=${sbn.key}")
            return false
        }
        val extras = source.extras ?: run {
            Log.w(TAG, "SKIP noExtras key=${sbn.key} category=${source.category} channel=${source.channelId}")
            return false
        }
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
        val categoryCall = source.category == Notification.CATEGORY_CALL
        val callStyleTemplate = template.contains("CallStyle", ignoreCase = true)
        val hasCallPerson = extras.containsKey("android.callPerson")
        Log.w(
            TAG,
            "SOURCE key=${sbn.key} category=${source.category} channel=${source.channelId} " +
                "template=${template.substringAfterLast('.')} categoryCall=$categoryCall " +
                "callStyle=$callStyleTemplate answer=${answerExtra != null} " +
                "decline=${declineExtra != null} hangup=${hangUpExtra != null} " +
                "person=$hasCallPerson fullScreen=${source.fullScreenIntent != null} " +
                "content=${source.contentIntent != null} titleLen=${title.length} textLen=${text.length} " +
                "actions=${candidates.joinToString { \"${it.index}:${it.label}/${it.semanticAction}/${it.hasIntent}\" }}"
        )
        val decision = WeChatCallPolicy.decide(
            categoryCall = categoryCall,
            callStyleTemplate = callStyleTemplate,
            hasAnswerExtra = answerExtra != null,
            hasDeclineExtra = declineExtra != null,
            hasHangUpExtra = hangUpExtra != null,
            hasCallPerson = hasCallPerson,
            channelId = source.channelId.orEmpty(),
            titleAndText = "$title $text",
            actions = candidates,
        ) ?: run {
            Log.w(TAG, "SKIP policyRejected key=${sbn.key}")
            return false
        }
        Log.w(TAG, "DECISION key=${sbn.key} phase=${decision.phase} answerIndex=${decision.answerIndex} declineIndex=${decision.declineIndex} hangUpIndex=${decision.hangUpIndex}")

        fun actionIntent(index: Int?): PendingIntent? = index?.let { actions.getOrNull(it)?.actionIntent }
        ensureChannel(context)
        val realAnswer = answerExtra ?: actionIntent(decision.answerIndex)
        val realDecline = declineExtra ?: actionIntent(decision.declineIndex)
        val hangUp = hangUpExtra ?: actionIntent(decision.hangUpIndex)
        val style = when (decision.phase) {
            WeChatCallPhase.INCOMING -> {
                val answer = realAnswer ?: proxyIntent(
                    context, sbn, WeChatCallProxyReceiver.ACTION_OPEN_WECHAT_CALL, source.contentIntent
                )
                val decline = realDecline ?: proxyIntent(
                    context, sbn, WeChatCallProxyReceiver.ACTION_DISMISS_MIRROR, null
                )
                Log.i(TAG, "Incoming CallStyle controls realAnswer=${realAnswer != null} realDecline=${realDecline != null}")
                Notification.CallStyle.forIncomingCall(person(source, title), decline, answer)
            }
            WeChatCallPhase.ONGOING -> {
                val endOrDismiss = hangUp ?: proxyIntent(
                    context, sbn, WeChatCallProxyReceiver.ACTION_DISMISS_MIRROR, null
                )
                Log.i(TAG, "Ongoing CallStyle control realHangUp=${hangUp != null}")
                Notification.CallStyle.forOngoingCall(person(source, title), endOrDismiss)
            }
        }

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
        val manager = context.getSystemService(NotificationManager::class.java) ?: run {
            Log.w(TAG, "SKIP noNotificationManager key=${sbn.key}")
            return false
        }
        val posted = runCatching {
            val built = builder.build()
            Log.w(TAG, "POST key=${sbn.key} id=$CALL_NOTIFICATION_ID phase=${decision.phase} channel=$CHANNEL_ID")
            manager.notify(CALL_NOTIFICATION_ID, built)
            true
        }.onFailure {
            Log.e(TAG, "POST_FAIL key=${sbn.key} error=${it.javaClass.simpleName}:${it.message}", it)
        }.getOrDefault(false)
        if (!posted) return false
        manager.cancel(DIAGNOSTIC_ID)
        Log.i(TAG, "Published ${decision.phase} CallStyle for key=${sbn.key}")
        return true
    }

    private fun publishDiagnostic(context: Context, key: String, error: Throwable) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val detail = buildString {
            append(error.javaClass.simpleName)
            append(": ")
            append(error.message.orEmpty())
            error.cause?.let { cause ->
                append(" | cause=")
                append(cause.javaClass.simpleName)
                append(": ")
                append(cause.message.orEmpty())
            }
        }.take(1000)
        runCatching {
            manager.notify(
                DIAGNOSTIC_ID,
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_wechat)
                    .setContentTitle("CallStyle 转换失败")
                    .setContentText(detail)
                    .setStyle(Notification.BigTextStyle().bigText("key=$key\n$detail"))
                    .setOnlyAlertOnce(true)
                    .build(),
            )
        }
    }

    private fun proxyIntent(
        context: Context,
        sbn: StatusBarNotification,
        action: String,
        sourceContentIntent: PendingIntent?,
    ): PendingIntent {
        val intent = Intent(context, WeChatCallProxyReceiver::class.java).apply {
            this.action = action
            putExtra(WeChatCallProxyReceiver.EXTRA_NOTIFICATION_KEY, sbn.key)
            putExtra(WeChatCallProxyReceiver.EXTRA_SOURCE_CONTENT_INTENT, sourceContentIntent)
        }
        val requestCode = 31 * notificationId(sbn.key) + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun person(source: Notification, fallbackName: String): Person {
        val sourcePerson = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            source.extras?.getParcelable("android.callPerson", Person::class.java)
        } else {
            @Suppress("DEPRECATION") source.extras?.getParcelable("android.callPerson")
        }
        if (sourcePerson != null) return sourcePerson
        val builder = Person.Builder()
            .setName(fallbackName.ifBlank { "微信联系人" })
            .setImportant(true)
        // A resource Icon owned by com.tencent.mm cannot safely be rebuilt by our
        // package. Only copy a bitmap/data/URI large icon; otherwise omit it.
        source.getLargeIcon()?.let { icon ->
            if (icon.type != android.graphics.drawable.Icon.TYPE_RESOURCE) {
                builder.setIcon(icon)
            }
        }
        return builder.build()
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
