package com.huimao.wechatnotifyevo

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Handles actions on evolved notifications. All intents are explicit and
 * targeted at this receiver, so it stays non-exported in the manifest.
 *
 * Direct reply works by forwarding the typed text to WeChat's own reply
 * PendingIntent (taken from the original notification's car extender), the
 * same mechanism Nevolution's WeChat decorator used.
 */
class EvolutionActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.huimao.wechatnotifyevo.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.huimao.wechatnotifyevo.ACTION_MARK_READ"
        const val ACTION_DELETE = "com.huimao.wechatnotifyevo.ACTION_DELETE"
        const val EXTRA_NID = "nid"
        const val EXTRA_REPLY_PI = "reply_pi"
        const val EXTRA_RESULT_KEY = "result_key"
        const val EXTRA_READ_PI = "read_pi"
        private const val TAG = "WeChatEvo"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val nid = intent.getIntExtra(EXTRA_NID, -1)
        if (nid < 0) return
        when (intent.action) {
            ACTION_REPLY -> handleReply(context, intent, nid)
            ACTION_MARK_READ -> handleMarkRead(context, intent, nid)
            ACTION_DELETE -> handleDelete(context, nid)
        }
    }

    private fun handleReply(context: Context, intent: Intent, nid: Int) {
        val replyPi = intent.getParcelableExtra<PendingIntent>(EXTRA_REPLY_PI) ?: return
        val resultKey = intent.getStringExtra(EXTRA_RESULT_KEY) ?: return
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val input = results.getCharSequence(resultKey)?.toString() ?: return
        // Forward the typed text to WeChat's own reply PendingIntent.
        val sendIntent = Intent().addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        replyPi.creatorPackage?.let { sendIntent.setPackage(it) }
        val forwardResults = Bundle().apply { putCharSequence(resultKey, input) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(resultKey).build()),
            sendIntent,
            forwardResults
        )
        try {
            replyPi.send(context, 0, sendIntent, { _, _, _, _, _ ->
                // Append the sent message as "self" and silently repost.
                val conv = ConversationStore.get(nid)
                if (conv != null) {
                    conv.messages.add(
                        ChatMessage(input, null, System.currentTimeMillis())
                    )
                    WeChatListenerService.repostConversation(context, conv, silent = true)
                }
            }, null)
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Reply PendingIntent cancelled for nid=$nid")
        }
    }

    private fun handleMarkRead(context: Context, intent: Intent, nid: Int) {
        val readPi = intent.getParcelableExtra<PendingIntent>(EXTRA_READ_PI)
        if (readPi != null) {
            val sendIntent = Intent().addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            readPi.creatorPackage?.let { sendIntent.setPackage(it) }
            try {
                readPi.send(context, 0, sendIntent)
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "Read PendingIntent cancelled for nid=$nid")
            }
        }
        ConversationStore.remove(nid)
        WeChatListenerService.cancelEvolved(context, nid)
    }

    private fun handleDelete(context: Context, nid: Int) {
        ConversationStore.remove(nid)
        WeChatListenerService.refreshSummaries(context)
    }
}
