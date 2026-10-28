package com.huimao.wechatnotifyevo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.LocusId
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

    /**
     * A group summary is only posted once the group's total unread count
     * reaches this threshold; below it, conversations just show on their own
     * and the summary stays cancelled.
     */
    private const val SUMMARY_MIN_UNREAD = 100

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

    /** Small icon is shared with the status bar; shade rendering is system-controlled. */
    private fun smallIcon(context: Context, conv: Conversation?): Icon {
        when (Prefs.iconChoice(context)) {
            Prefs.ICON_WECHAT -> return Icon.createWithResource(context, R.drawable.ic_stat_wechat)
            Prefs.ICON_CUSTOM -> CustomNotificationIcon.load(context)?.let { return it }
        }
        return conv?.smallIcon ?: Icon.createWithResource(context, R.drawable.ic_stat_message)
    }

    /**
     * Sender identity for MessagingStyle messages (Android 9+). Direct chats
     * and service accounts reuse the chat avatar as the sender avatar; group
     * senders have no per-sender avatar from WeChat, so they get none.
     */
    private fun messagePerson(conv: Conversation, sender: String): Person {
        val builder = Person.Builder()
            .setName(sender)
            .setKey("sender_${conv.nid}_$sender")
        if (conv.type != ConversationType.GROUP) {
            conv.largeIcon?.let { builder.setIcon(it) }
        }
        return builder.build()
    }

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
            if (msg.sender != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ) {
                style.addMessage(msg.text, msg.timestamp, messagePerson(conv, msg.sender))
            } else {
                // Self messages (null sender) and pre-9 devices: plain text sender.
                style.addMessage(msg.text, msg.timestamp, msg.sender)
            }
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
        // Android 11+: link the notification to its conversation shortcut so
        // the system offers per-conversation priority/silent settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setShortcutId(ConversationShortcuts.shortcutId(conv.nid))
            builder.setLocusId(LocusId(ConversationShortcuts.shortcutId(conv.nid)))
        }
        // Android 11+: system bubbles, Nevolution-style shortcut bubbles:
        // the bubble references the conversation shortcut id, and the system
        // launches the shortcut's intent (LauncherUI + Main_User, or the
        // WeChat launcher as fallback) directly into the bubble window. No
        // trampoline PendingIntent, so no mutability crash and no second-hop
        // launch for the system to silently drop. Service notifications
        // (BOT) do not bubble.
        if (Prefs.isBubbleEnabled(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (conv.type == ConversationType.DIRECT || conv.type == ConversationType.GROUP)
        ) {
            builder.setBubbleMetadata(
                Notification.BubbleMetadata.Builder(ConversationShortcuts.shortcutId(conv.nid))
                    .setDesiredHeight(512)
                    .build()
            )
        }
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

    /**
     * One summary per group; null when the group has no conversations left
     * or its total unread count is below [SUMMARY_MIN_UNREAD].
     */
    fun buildSummaryNotification(
        context: Context,
        type: ConversationType,
    ): Notification? {
        val convs = ConversationStore.all().filter { it.type == type }
        if (convs.isEmpty()) return null
        val totalUnread = convs.sumOf { conv ->
            if (conv.lastCounter > 0) conv.lastCounter else conv.messages.size
        }
        if (totalUnread < SUMMARY_MIN_UNREAD) return null
        val style = Notification.InboxStyle().setBigContentTitle("微信")
        for (conv in convs.take(5)) {
            val last = conv.messages.lastOrNull()?.text ?: ""
            style.addLine("${conv.title}：$last")
        }
        style.setSummaryText("${convs.size} 个会话有新消息")
        return Notification.Builder(context, channelFor(type))
            .setSmallIcon(smallIcon(context, null))
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
