package com.huimao.wechatnotifyevo

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Bubble trampoline: a shortcut-based bubble launches the shortcut intent
 * into the bubble window, and that intent is this activity. On window
 * focus (truly foreground, so the send is not background-blocked) it
 * forwards WeChat's own content intent for the conversation — the same
 * intent tapping the evolved notification sends, so it opens the exact
 * chat, not WeChat main. No username learning is involved: learning the
 * username via contentIntent.send + OnFinished produces nothing on
 * Android 17 (the background send's callback never fires, WNE.Shortcuts
 * log stays empty), so the shortcut would otherwise stay on the WeChat
 * launcher intent.
 *
 * The live content intent is looked up by nid at tap time. Only the nid
 * travels in the shortcut intent: shortcut intents are persisted by the
 * system, and a PendingIntent extra is not persistable — it can keep the
 * shortcut from being created at all, which drops the bubble. If the
 * conversation left the store (process death, dismissal), the trampoline
 * has nothing to forward and just exits.
 *
 * The trampoline shows an opaque loading screen, not a transparent
 * window: a transparent window may not count as a visible foreground
 * window, so its send can be background-blocked (and a black window
 * tells nobody which step failed). With an opaque screen the outcome is
 * explicit: WeChat comes up, or the fallback Toast explains it didn't.
 *
 * Finish rules (lessons from the intent-based trampoline versions):
 * never finish immediately after send() — finishing before the target
 * attaches can tear the task down and the target never appears. Finish
 * on pause (the target came up) or after a 5s fallback with a Toast.
 */
class BubbleTrampolineActivity : Activity() {
    companion object {
        const val EXTRA_NID = "nid"
        private const val TAG = "WNE.Bubble"
        private const val FALLBACK_DELAY_MS = 5000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var forwarded = false
    private val fallback = Runnable {
        if (!isFinishing) {
            Toast.makeText(this, "未能打开微信界面", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opaque loading screen: visible foreground window, and an
        // explicit state instead of a black window when forwarding stalls.
        val tv = TextView(this).apply {
            text = "正在打开微信…"
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }
        setContentView(tv)
        handler.postDelayed(fallback, FALLBACK_DELAY_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !forwarded) {
            forwarded = true
            forward()
        }
    }

    private fun forward() {
        val nid = intent.getIntExtra(EXTRA_NID, -1)
        val pi = if (nid != -1) ConversationStore.get(nid)?.contentIntent else null
        if (pi == null) {
            Log.w(TAG, "No content intent for nid=$nid")
            finish()
            return
        }
        try {
            pi.send()
            Log.d(TAG, "Forwarded content intent for nid=$nid")
            // Do not finish here: see the class comment.
        } catch (e: Exception) {
            // CanceledException included: any send failure must surface
            // instead of leaving a black window behind.
            Log.w(TAG, "Forward failed for nid=$nid", e)
            Toast.makeText(this, "未能打开微信界面", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(fallback)
        // Target came up (or the user left): exit.
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(fallback)
        super.onDestroy()
    }
}
