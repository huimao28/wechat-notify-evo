package com.huimao.wechatnotifyevo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.LocusId
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle

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

    /** Create once. Never recreate or delete: user-locked sound/importance/bubble state must survive. */
    fun ensureConversationChannel(context: Context, conv: Conversation): String {
        val parentId = channelFor(conv.type)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !conv.shortcutPushed) return parentId
        val nm = context.getSystemService(NotificationManager::class.java) ?: return parentId
        val shortcutId = ConversationShortcuts.shortcutId(conv)
        val id = ConversationChannelPolicy.channelId(parentId, shortcutId)
        if (ConversationChannelPolicy.shouldCreate(nm.getNotificationChannel(id) != null,
                conv.shortcutPushed)) {
            val parent = nm.getNotificationChannel(parentId) ?: return parentId
            val channel = NotificationChannel(id, conv.title, parent.importance)
            channel.setConversationId(parentId, shortcutId)
            nm.createNotificationChannel(channel)
        }
        return if (nm.getNotificationChannel(id) != null) id else parentId
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
    private fun messagePerson(conv: Conversation, sender: String, avatar: Icon?): Person {
        val builder = Person.Builder()
            .setName(sender)
            .setKey(ConversationPersonKeys.sender(ConversationShortcuts.shortcutId(conv), sender))
            .setBot(false)
        if (conv.type != ConversationType.GROUP) {
            avatar?.let { builder.setIcon(it) }
        }
        return builder.build()
    }

    /** Summary-only fallback; conversation notifications always use WeChat's exact contentIntent. */
    private fun wechatLaunchIntent(context: Context): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(WeChatListenerService.WECHAT_PACKAGE) ?: return null
        return PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun compatPendingIntent(
        context: Context,
        conv: Conversation,
        action: String,
        mutable: Boolean,
    ): PendingIntent {
        val intent = Intent(context, AndroidAutoCompatActionReceiver::class.java).apply {
            this.action = action
            // Identity is part of PendingIntent matching; old conversation generations cannot alias.
            data = Uri.Builder().scheme("wne-aa-compat")
                .authority(context.packageName)
                .appendPath(conv.sourceUserId.toString())
                .appendPath(conv.nid.toString())
                .appendPath(conv.routeToken)
                .appendPath(action.substringAfterLast('.'))
                .build()
            putExtra(AndroidAutoCompatActionReceiver.EXTRA_NID, conv.nid)
            putExtra(AndroidAutoCompatActionReceiver.EXTRA_SOURCE_USER_ID, conv.sourceUserId)
            putExtra(AndroidAutoCompatActionReceiver.EXTRA_ROUTE_TOKEN, conv.routeToken)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun deletePendingIntent(context: Context, conv: Conversation): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            conv.nid,
            Intent(context, EvolutionActionReceiver::class.java).apply {
                action = EvolutionActionReceiver.ACTION_DELETE
                putExtra(EvolutionActionReceiver.EXTRA_NID, conv.nid)
                putExtra(EvolutionActionReceiver.EXTRA_SOURCE_USER_ID, conv.sourceUserId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun buildConversationNotification(
        context: Context,
        conv: Conversation,
        silent: Boolean,
    ): Notification {
        require(CurrentUserScope.accepts(conv)) { "conversation is outside current user" }
        val avatar = conv.displayLargeIcon(Prefs.isCircularAvatarEnabled(context))
        val selfName = context.getString(R.string.self_display_name)
        val style = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Notification.MessagingStyle(
                Person.Builder().setName(selfName).setKey(ConversationPersonKeys.SELF).setBot(false).build()
            )
        } else {
            @Suppress("DEPRECATION")
            Notification.MessagingStyle(selfName)
        }
        for (msg in conv.messages.takeLast(20)) {
            if (msg.sender != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ) {
                style.addMessage(msg.text, msg.timestamp, messagePerson(conv, msg.sender, avatar))
            } else {
                // Self messages (null sender) and pre-9 devices: plain text sender.
                style.addMessage(msg.text, msg.timestamp, msg.sender)
            }
        }
        val metadata = ConversationMetadataPolicy.forConversation(conv.title, conv.type)
        style.conversationTitle = metadata.conversationTitle
        style.isGroupConversation = metadata.isGroupConversation
        val builder = Notification.Builder(context, ensureConversationChannel(context, conv))
            .setSmallIcon(smallIcon(context, conv))
            .setColor(WECHAT_GREEN)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentTitle(conv.title)
            .setContentText(conv.messages.lastOrNull()?.text ?: "")
            .setShowWhen(true)
            .setWhen(conv.messages.lastOrNull()?.timestamp ?: System.currentTimeMillis())
            .setContentIntent(requireNotNull(conv.contentIntent) { "original WeChat contentIntent is required" })
            .setDeleteIntent(deletePendingIntent(context, conv))
            .setAutoCancel(true)
            .setGroup(groupKeyFor(conv.type))
            .setStyle(style)
            .setOnlyAlertOnce(silent)
        // Android 11+: link the notification to its conversation shortcut so
        // the system offers per-conversation priority/silent settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ConversationPublicationPolicy.associateAsConversation(conv.shortcutPushed)) {
            builder.setShortcutId(ConversationShortcuts.shortcutId(conv))
            builder.setLocusId(LocusId(ConversationShortcuts.shortcutId(conv)))
        }
        // Android 11+: shortcut target is normally our trampoline. The manual
        // launcher control uses only WeChat's public home entry, never chat extras.
        // Neither mode guarantees embedding; the system controls bubble display.
        // A stale or missing target must never receive bubble metadata.
        if (Prefs.isBubbleEnabled(context) &&
            conv.directChatEligible &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (conv.type == ConversationType.DIRECT || conv.type == ConversationType.GROUP) &&
            ConversationShortcuts.isTargetPublished(context, conv)
        ) {
            builder.setBubbleMetadata(
                Notification.BubbleMetadata.Builder(ConversationShortcuts.shortcutId(conv))
                    .setDesiredHeight(512)
                    .build()
            )
        }
        avatar?.let { builder.setLargeIcon(it) }
        val unread = if (conv.lastCounter > 0) conv.lastCounter else conv.messages.size
        if (unread > 0) builder.setNumber(unread)

        val replyEnabled = Prefs.isReplyEnabled(context)
        val replyIntent = conv.replyPendingIntent
        val replyInputs = conv.replyRemoteInputs
        val plan = AndroidAutoCompatPolicy.plan(
            enabled = Prefs.isAndroidAutoCompatActionsEnabled(context),
            hasRealReply = replyEnabled && replyIntent != null && replyInputs.isNotEmpty(),
            hasRealMarkRead = conv.readPendingIntent != null,
        )
        when (plan.reply) {
            CompatActionKind.REAL -> {
                val actionBuilder = Notification.Action.Builder(
                    conv.replySourceAction?.getIcon()
                        ?: Icon.createWithResource(context, R.drawable.ic_stat_message),
                    context.getString(R.string.action_reply),
                    requireNotNull(replyIntent),
                )
                replyInputs.forEach(actionBuilder::addRemoteInput)
                actionBuilder.setAllowGeneratedReplies(
                    conv.replySourceAction?.allowGeneratedReplies ?: true
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    actionBuilder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                }
                builder.addAction(actionBuilder.build())
            }
            CompatActionKind.SIMULATED -> if (replyEnabled) {
                val remoteInput = android.app.RemoteInput.Builder(
                    AndroidAutoCompatPolicy.REMOTE_INPUT_RESULT_KEY
                ).setLabel(context.getString(R.string.action_reply_simulated)).build()
                val actionBuilder = Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_message),
                    context.getString(R.string.action_reply_simulated),
                    compatPendingIntent(
                        context, conv,
                        AndroidAutoCompatActionReceiver.ACTION_SIMULATED_REPLY,
                        mutable = true,
                    ),
                ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true)
                    .addExtras(Bundle().apply {
                        putBoolean(
                            AndroidAutoCompatPolicy.EXTRA_SHOWS_USER_INTERFACE,
                            AndroidAutoCompatPolicy.REPLY_SHOWS_USER_INTERFACE,
                        )
                    })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    actionBuilder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                }
                builder.addAction(actionBuilder.build())
            }
            CompatActionKind.NONE -> Unit
        }

        when (plan.markRead) {
            CompatActionKind.REAL -> {
                val actionBuilder = Notification.Action.Builder(
                    conv.readSourceAction?.getIcon()
                        ?: Icon.createWithResource(context, R.drawable.ic_stat_message),
                    context.getString(R.string.action_mark_read),
                    requireNotNull(conv.readPendingIntent),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    actionBuilder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                }
                builder.addAction(actionBuilder.build())
            }
            CompatActionKind.SIMULATED -> {
                val actionBuilder = Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_message),
                    context.getString(R.string.action_mark_read_simulated),
                    compatPendingIntent(
                        context, conv,
                        AndroidAutoCompatActionReceiver.ACTION_SIMULATED_MARK_READ,
                        mutable = false,
                    ),
                ).addExtras(Bundle().apply {
                    putBoolean(
                        AndroidAutoCompatPolicy.EXTRA_SHOWS_USER_INTERFACE,
                        AndroidAutoCompatPolicy.MARK_READ_SHOWS_USER_INTERFACE,
                    )
                })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    actionBuilder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                }
                builder.addAction(actionBuilder.build())
            }
            CompatActionKind.NONE -> Unit
        }

        // Platform CarExtender exposes unread-conversation reading but no
        // public builder. Avoid AndroidX churn and undocumented Bundle keys:
        // modern Android Auto consumes MessagingStyle plus these actions.
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
        val currentUserId = CurrentUserScope.id()
        val convs = ConversationStore.allForUser(currentUserId).filter { it.type == type }
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
