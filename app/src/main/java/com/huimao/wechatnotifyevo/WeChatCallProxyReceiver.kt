package com.huimao.wechatnotifyevo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Proxy controls used only when WeChat exposes no public call actions.
 * "Answer" opens WeChat's authoritative call UI; "Decline" only dismisses our mirror.
 */
class WeChatCallProxyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_NOTIFICATION_KEY) ?: return
        when (intent.action) {
            ACTION_OPEN_WECHAT_CALL -> {
                val source = intent.pendingIntentExtra(EXTRA_SOURCE_CONTENT_INTENT)
                val opened = source?.let { pending ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val options = android.app.ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(
                                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                            pending.send(context, 0, null, null, null, null, options.toBundle())
                        } else {
                            pending.send()
                        }
                        true
                    }.getOrDefault(false)
                } ?: false
                Log.i(TAG, "Proxy answer opened WeChat=$opened")
                WeChatCallNotificationBridge.cancel(context, key)
            }
            ACTION_DISMISS_MIRROR -> {
                Log.i(TAG, "Proxy decline dismissed mirror only")
                WeChatCallNotificationBridge.cancel(context, key)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.pendingIntentExtra(name: String): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, PendingIntent::class.java)
        } else {
            getParcelableExtra(name)
        }

    companion object {
        private const val TAG = "WNE.CallStyle"
        const val ACTION_OPEN_WECHAT_CALL = "com.huimao.wechatnotifyevo.ACTION_OPEN_WECHAT_CALL"
        const val ACTION_DISMISS_MIRROR = "com.huimao.wechatnotifyevo.ACTION_DISMISS_CALL_MIRROR"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
        const val EXTRA_SOURCE_CONTENT_INTENT = "source_content_intent"
    }
}
