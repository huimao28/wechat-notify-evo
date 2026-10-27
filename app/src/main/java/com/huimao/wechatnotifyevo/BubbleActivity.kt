package com.huimao.wechatnotifyevo

import android.app.Activity
import android.app.PendingIntent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Trampoline for system bubbles. The system requires the PendingIntent
 * attached to a bubble to be mutable, but WeChat's content intent is
 * immutable, so the bubble points here instead: this activity is launched
 * from a mutable PendingIntent created by us, and it forwards to the
 * original intent carried in the "target" extra, then finishes.
 * Same pattern as Nevolution's AgentBubbleActivity.
 *
 * This must hold a visible window while forwarding: a NoDisplay activity
 * proved unreliable here (the forwarded launch never surfaced and the
 * failure was silent), so this uses a translucent theme.
 *
 * The forward must not run in onCreate: the window is not visible yet
 * there, and a forward from a not-yet-visible activity can be dropped by
 * the system without any error. So the forward runs once the window gains
 * focus, with a 1s fallback in case focus never arrives. Failures are
 * toasted instead of swallowed, so a dead bubble tap is diagnosable.
 */
class BubbleActivity : Activity() {
    private var forwarded = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val hasTarget = intent.getParcelableExtra("target") as? PendingIntent != null
        Log.d(TAG, "BubbleActivity created, hasTarget=$hasTarget")
        handler.postDelayed({ if (!isFinishing && !isDestroyed) forward() }, 1000)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) forward()
    }

    private fun forward() {
        if (forwarded) return
        forwarded = true
        try {
            @Suppress("DEPRECATION")
            val target = intent.getParcelableExtra("target") as? PendingIntent
            if (target == null) {
                Log.w(TAG, "Bubble target missing")
                Toast.makeText(this, "气泡目标丢失", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Bubble forwarding to target")
                target.send()
            }
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Bubble target canceled", e)
            Toast.makeText(this, "原通知已失效，无法打开微信", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "Bubble forward failed", e)
            Toast.makeText(this, "打开微信失败：" + (e.message ?: "未知原因"), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val TAG = "WNE.Bubble"
    }
}
