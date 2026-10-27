package com.huimao.wechatnotifyevo

import android.app.Activity
import android.app.PendingIntent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Trampoline for system bubbles. The system requires the PendingIntent
 * attached to a bubble to be mutable, but WeChat's content intent is
 * immutable, so the bubble points here instead: this activity is launched
 * from a mutable PendingIntent created by us, and it forwards to the
 * original intent carried in the "target" extra.
 * Same pattern as Nevolution's AgentBubbleActivity.
 *
 * This is a REAL activity with UI, not an empty trampoline:
 * - An empty (translucent, content-less) activity showed as a black bubble
 *   window when the forwarded UI never surfaced, and finishing it while
 *   the bubble was still in the expanded stack left an empty bubble task
 *   that crashed when the user switched back to it (v1.0.7 symptom).
 * - So this activity never finishes itself: the system destroys it with
 *   the bubble. It shows "正在打开微信…" while forwarding, and if nothing
 *   surfaces after 5s it shows a retry button instead of a black window.
 *   Tapping the button is a fresh user interaction with our own window,
 *   which is the strongest signal for the system to allow the launch.
 *
 * The forward must not run in onCreate: the window is not visible yet
 * there, and a forward from a not-yet-visible activity can be dropped by
 * the system without any error. So the forward runs once the window gains
 * focus, with a 1s fallback in case focus never arrives.
 */
class BubbleActivity : Activity() {
    private var forwarded = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(64, 64, 64, 64)
        }
        statusText = TextView(this).apply {
            text = "正在打开微信…"
            textSize = 17f
            gravity = Gravity.CENTER
        }
        retryButton = Button(this).apply {
            text = "打开微信"
            visibility = View.GONE
            setOnClickListener { retrySend() }
        }
        layout.addView(statusText)
        layout.addView(
            retryButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        )
        setContentView(layout)

        @Suppress("DEPRECATION")
        val hasTarget = intent.getParcelableExtra("target") as? PendingIntent != null
        Log.d(TAG, "BubbleActivity created, hasTarget=$hasTarget")
        handler.postDelayed({ if (!isFinishing && !isDestroyed) forward() }, 1000)
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                Log.w(TAG, "Bubble target never surfaced after 5s, showing retry")
                statusText.text = "未能自动打开微信"
                retryButton.visibility = View.VISIBLE
            }
        }, 5000)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) forward()
    }

    override fun onDestroy() {
        Log.d(TAG, "Bubble destroyed")
        super.onDestroy()
    }

    private fun forward() {
        if (forwarded) return
        forwarded = true
        try {
            @Suppress("DEPRECATION")
            val target = intent.getParcelableExtra("target") as? PendingIntent
            if (target == null) {
                Log.w(TAG, "Bubble target missing")
                statusText.text = "气泡目标丢失"
                Toast.makeText(this, "气泡目标丢失", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Bubble forwarding to target creator=" + target.creatorPackage)
                target.send()
                Log.d(TAG, "Bubble send() returned normally")
            }
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Bubble target canceled", e)
            statusText.text = "原通知已失效"
            Toast.makeText(this, "原通知已失效，无法打开微信", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "Bubble forward failed", e)
            statusText.text = "打开微信失败"
            Toast.makeText(this, "打开微信失败：" + (e.message ?: "未知原因"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun retrySend() {
        try {
            @Suppress("DEPRECATION")
            val target = intent.getParcelableExtra("target") as? PendingIntent
            if (target == null) {
                Toast.makeText(this, "气泡目标丢失", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Bubble retry sending to target creator=" + target.creatorPackage)
                target.send()
                Log.d(TAG, "Bubble retry send() returned normally")
            }
        } catch (e: PendingIntent.CanceledException) {
            Toast.makeText(this, "原通知已失效，无法打开微信", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "打开微信失败：" + (e.message ?: "未知原因"), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "WNE.Bubble"
    }
}
