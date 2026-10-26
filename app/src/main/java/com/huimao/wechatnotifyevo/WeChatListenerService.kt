package com.huimao.wechatnotifyevo

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Standalone replacement for the Nevolution chain: this service itself is the
 * notification listener, so no Nevolution main app (and no Binder decorator
 * protocol) is involved at all.
 *
 * Flow: WeChat posts -> onNotificationPosted -> parse + archive -> cancel the
 * original -> post the evolved MessagingStyle notification under our own
 * package and channels -> maintain one group summary per conversation type.
 */
class WeChatListenerService : NotificationListenerService() {

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"

        private fun notificationManager(context: Context): NotificationManager? =
            context.getSystemService(NotificationManager::class.java)

        /** (Re)post one conversation's evolved notification and refresh summaries. */
        fun repostConversation(context: Context, conv: Conversation, silent: Boolean) {
            val nm = notificationManager(context) ?: return
            ConversationShortcuts.publish(context, conv)
            nm.notify(
                conv.nid,
                NotificationFactory.buildConversationNotification(context, conv, silent)
            )
            refreshSummaries(context)
        }

        /** Cancel one evolved conversation notification and refresh summaries. */
        fun cancelEvolved(context: Context, nid: Int) {
            val nm = notificationManager(context) ?: return
            nm.cancel(nid)
            ConversationShortcuts.remove(context, nid)
            refreshSummaries(context)
        }

        /** Post or remove the group summary for every conversation type. */
        fun refreshSummaries(context: Context) {
            val nm = notificationManager(context) ?: return
            for (type in ConversationType.values()) {
                val summary = NotificationFactory.buildSummaryNotification(context, type)
                if (summary != null) {
                    nm.notify(NotificationFactory.summaryIdFor(type), summary)
                } else {
                    nm.cancel(NotificationFactory.summaryIdFor(type))
                }
            }
        }

        /** Cancel every evolved notification and drop all archives. */
        fun cancelAll(context: Context) {
            val nm = notificationManager(context) ?: return
            val convs = ConversationStore.all()
            for (conv in convs) {
                nm.cancel(conv.nid)
            }
            ConversationShortcuts.removeAll(context, convs)
            for (type in ConversationType.values()) {
                nm.cancel(NotificationFactory.summaryIdFor(type))
            }
            ConversationStore.clear()
        }
    }

    override fun onListenerConnected() {
        NotificationFactory.ensureChannels(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WECHAT_PACKAGE) return
        if (!Prefs.isEnabled(this)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        // WeChat's own group summary: drop it, our own summary replaces it.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            cancelNotification(sbn.key)
            return
        }
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotEmpty() } ?: return
        // No ticker means a non-conversation notification (e.g. web login
        // confirmation): leave it untouched.
        val ticker = n.tickerText?.toString() ?: return
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // Decide the conversation type before touching the store, so that a
        // group message we are configured to ignore never pollutes the archive.
        val (textNoCounter, _) = stripCounter(rawText)
        val (senderPrefix, _) = splitSender(textNoCounter)
        val isGroupCandidate = senderPrefix != null && senderPrefix != title &&
            !isBotTitle(title)
        if (isGroupCandidate && !Prefs.isGroupEnabled(this)) {
            return
        }

        val conv = ConversationStore.getOrCreate(sbn.id, title)
        conv.title = title
        conv.smallIcon = n.smallIcon
        conv.largeIcon = n.getLargeIcon()
        conv.contentIntent = n.contentIntent

        // Prefer the car-extender unread conversation when WeChat provides
        // one: it carries the authoritative message list plus the reply/read
        // intents that make direct reply and mark-as-read possible.
        val carExt = Notification.CarExtender(n).unreadConversation
        var usedCarExtMessages = false
        if (carExt != null) {
            conv.replyPendingIntent = carExt.replyPendingIntent
            conv.readPendingIntent = carExt.readPendingIntent
            conv.remoteInputResultKey = carExt.remoteInput?.resultKey
            val extMessages = carExt.messages
            if (!extMessages.isNullOrEmpty()) {
                usedCarExtMessages = true
                conv.messages.clear()
                for (entry in extMessages) {
                    val (sender, body) = splitSender(entry)
                    conv.messages.add(
                        ChatMessage(body, sender ?: title, sbn.postTime)
                    )
                }
            }
        }

        if (conv.type == ConversationType.UNKNOWN) {
            conv.type = when {
                isBotTitle(title) -> ConversationType.BOT
                else -> ConversationType.DIRECT
            }
        }
        if (isGroupCandidate) {
            conv.type = ConversationType.GROUP
        } else if (usedCarExtMessages) {
            // A car-extender message from someone other than the title means
            // this is a group conversation even without a "sender: " prefix.
            if (conv.messages.any { it.sender != null && it.sender != title }) {
                conv.type = ConversationType.GROUP
            }
        }

        if (!usedCarExtMessages) {
            // Fall back to parsing this notification's own text.
            val (text, counter) = stripCounter(rawText)
            val (sender, body) = splitSender(text)
            val messageSender = when (conv.type) {
                ConversationType.GROUP -> sender
                else -> title
            }
            val last = conv.messages.lastOrNull()
            val duplicate = last != null && last.text == body &&
                last.sender == messageSender &&
                (counter < 0 || counter == conv.lastCounter)
            if (!duplicate) {
                conv.messages.add(ChatMessage(body, messageSender, sbn.postTime))
                if (counter > 0) conv.lastCounter = counter
            }
            while (conv.messages.size > 20) conv.messages.removeAt(0)
        }

        // Replace the original with the evolved notification. Publish the
        // conversation shortcut first so the system links this notification
        // to it (Android 11+ conversation status).
        cancelNotification(sbn.key)
        val nm = notificationManager(this) ?: return
        ConversationShortcuts.publish(this, conv)
        nm.notify(
            conv.nid,
            NotificationFactory.buildConversationNotification(this, conv, silent = false)
        )
        refreshSummaries(this)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        if (sbn == null || sbn.packageName != WECHAT_PACKAGE) return
        // Our own cancelNotification() calls also arrive here; they must not
        // wipe the archive we just built the evolved notification from.
        if (reason == REASON_LISTENER_CANCEL || reason == REASON_LISTENER_CANCEL_ALL) return
        // WeChat itself removed the notification (read in-app, clicked, or
        // otherwise dismissed): drop our evolved copy too.
        val conv = ConversationStore.get(sbn.id) ?: return
        ConversationStore.remove(sbn.id)
        ConversationShortcuts.remove(this, sbn.id)
        val nm = notificationManager(this) ?: return
        nm.cancel(conv.nid)
        refreshSummaries(this)
    }
}
