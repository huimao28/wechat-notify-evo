package com.huimao.wechatnotifyevo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

/** Builds the evolved notifications and owns the app's notification channels. */
object NotificationFactory {
    const val CHANNEL_MESSAGE = "evo_message"
    const val CHANNEL_GROUP = "evo_group"
    const val CHANNEL_BOT = "evo_bot"

    const val GROUP_DIRECT = "evo_group_direct"
    const val GROUP_GROUP = "evo_group_group"
    const val GROUP_BOT = "evo_group_bot"

    private const val SUMMARY_ID_DIRECT = 900001
    private const val SUMMARY_ID_GROUP = 900002
    private const val SUMMARY_ID_BOT = 900003

    private const val WECHAT_GREEN = 0xFF07C160.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGE,
                context.getString(R.string.channel_message),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_message_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GROUP,
                context.getString(R.string.channel_group),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_group_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BOT,
                context.getString(R.string.channel_bot),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_bot_desc) }
        )
    }

    fun channelFor(type: ConversationType): String = when (type) {
        ConversationType.GROUP -> CHANNEL_GROUP
        ConversationType.BOT -> CHANNEL_BOT
        else -> CHANNEL_MESSAGE
    }

    fun groupKeyFor(type: ConversationType): String = when (type) {
        ConversationType.GROUP -> GROUP_GROUP
        ConversationType.BOT -> GROUP_BOT
        else -> GROUP_DIRECT
    }

    fun summaryIdFor(type: ConversationType): Int = when (type) {
        ConversationType.GROUP -> SUMMARY_ID_GROUP
        ConversationType.BOT -> SUMMARY_ID_BOT
        else -> SUMMARY_ID_DIRECT
    }

    private fun smallIcon(context: Context, conv: Conversation): Icon =
        conv.smallIcon ?: Icon.createWithResource(context, R.drawable.ic_stat_message)

    private fun wechatLaunchIntent(context: Context): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(WeChatListenerService.WECHAT_PACKAGE) ?: return null
        return PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun deletePendingIntent(context: Context, nid: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            nid,
            Intent(context, EvolutionActionReceiver::class.java).apply {
                action = EvolutionActionReceiver.ACTION_DELETE
                putExtra(EvolutionActionReceiver.EXTRA_NID, nid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun buildConversationNotification(
        context: Context,
        conv: Conversation,
        silent: Boolean,
    ): Notification {
        val style = Notification.MessagingStyle(context.getString(R.string.self_display_name))
        for (msg in conv.messages.takeLast(20)) {
            style.addMessage(msg.text, msg.timestamp, msg.sender)
        }
        if (conv.type == ConversationType.GROUP) {
            style.conversationTitle = conv.title
            style.isGroupConversation = true
        }
        val builder = Notification.Builder(context, channelFor(conv.type))
            .setSmallIcon(smallIcon(context, conv))
            .setColor(WECHAT_GREEN)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentTitle(conv.title)
            .setContentText(conv.messages.lastOrNull()?.text ?: "")
            .setShowWhen(true)
            .setWhen(conv.messages.lastOrNull()?.timestamp ?: System.currentTimeMillis())
            .setContentIntent(conv.contentIntent ?: wechatLaunchIntent(context))
            .setDeleteIntent(deletePendingIntent(context, conv.nid))
            .setAutoCancel(true)
            .setGroup(groupKeyFor(conv.type))
            .setStyle(style)
            .setOnlyAlertOnce(silent)
        conv.largeIcon?.let { builder.setLargeIcon(it) }
        val unread = if (conv.lastCounter > 0) conv.lastCounter else conv.messages.size
        if (unread > 0) builder.setNumber(unread)

        if (Prefs.isReplyEnabled(context) &&
            conv.replyPendingIntent != null && conv.remoteInputResultKey != null
        ) {
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                conv.nid,
                Intent(context, EvolutionActionReceiver::class.java).apply {
                    action = EvolutionActionReceiver.ACTION_REPLY
                    putExtra(EvolutionActionReceiver.EXTRA_NID, conv.nid)
                    putExtra(EvolutionActionReceiver.EXTRA_REPLY_PI, conv.replyPendingIntent)
                    putExtra(EvolutionActionReceiver.EXTRA_RESULT_KEY, conv.remoteInputResultKey)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val remoteInput = RemoteInput.Builder(conv.remoteInputResultKey!!)
                .setAllowFreeFormInput(true)
                .build()
            val actionBuilder = Notification.Action.Builder(
                null,
                context.getString(R.string.action_reply),
                replyPendingIntent
            ).addRemoteInput(remoteInput)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                actionBuilder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            }
            builder.addAction(actionBuilder.build())
        }

        if (conv.readPendingIntent != null) {
            val markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                conv.nid,
                Intent(context, EvolutionActionReceiver::class.java).apply {
                    action = EvolutionActionReceiver.ACTION_MARK_READ
                    putExtra(EvolutionActionReceiver.EXTRA_NID, conv.nid)
                    putExtra(EvolutionActionReceiver.EXTRA_READ_PI, conv.readPendingIntent)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_mark_read),
                    markReadPendingIntent
                ).build()
            )
        }
        return builder.build()
    }

    /** One summary per group; null when the group has no conversations left. */
    fun buildSummaryNotification(
        context: Context,
        type: ConversationType,
    ): Notification? {
        val convs = ConversationStore.all().filter { it.type == type }
        if (convs.isEmpty()) return null
        val style = Notification.InboxStyle().setBigContentTitle("微信")
        for (conv in convs.take(5)) {
            val last = conv.messages.lastOrNull()?.text ?: ""
            style.addLine("${conv.title}：$last")
        }
        style.setSummaryText("${convs.size} 个会话有新消息")
        return Notification.Builder(context, channelFor(type))
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat_message))
            .setColor(WECHAT_GREEN)
            .setGroup(groupKeyFor(type))
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setContentIntent(wechatLaunchIntent(context))
            .setAutoCancel(true)
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .build()
    }
}
