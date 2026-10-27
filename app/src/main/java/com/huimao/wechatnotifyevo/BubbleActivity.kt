package com.huimao.wechatnotifyevo

import android.app.Activity
import android.app.PendingIntent
import android.os.Bundle

/**
 * Trampoline for system bubbles. The system requires the PendingIntent
 * attached to a bubble to be mutable, but WeChat's content intent is
 * immutable, so the bubble points here instead: this activity is launched
 * from a mutable PendingIntent created by us, and it forwards to the
 * original intent carried in the "target" extra, then finishes.
 * Same pattern as Nevolution's AgentBubbleActivity.
 */
class BubbleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            @Suppress("DEPRECATION")
            val target = intent.getParcelableExtra("target") as? PendingIntent
            target?.send()
        } catch (_: PendingIntent.CanceledException) {
            // Original notification is gone; nothing to open.
        } catch (_: Exception) {
            // Never crash the bubble path.
        }
        finish()
    }
}
