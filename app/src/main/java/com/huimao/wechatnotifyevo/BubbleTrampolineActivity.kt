package com.huimao.wechatnotifyevo

import android.app.Activity
import android.app.PendingIntent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Bubble trampoline: a shortcut-based bubble launches the shortcut intent
 * into the bubble window, and that intent is this transparent activity.
 * On window focus (truly foreground, so the send is not background-blocked)
 * it forwards WeChat's own content intent for the conversation — the same
 * intent tapping the evolved notification sends, so it opens the exact
 * chat, not WeChat main. No username learning is involved: learning the
 * username via contentIntent.send + OnFinished produces nothing on
 * Android 17 (the background send's callback never fires, WNE.Shortcuts
 * log stays empty), so the shortcut would otherwise stay on the WeChat
 * launcher intent.
 *
 * The live content intent is looked up by nid at tap time; the "target"
 * extra is only a fallback for a conversation that left the store.
 *
 * Finish rules (lessons from the intent-based trampoline versions):
 * never finish immediately after send() — finishing before the target
 * attaches can tear the task down and the target never appears. Finish
 * on pause (the target came up) or after a 5s fallback with a Toast.
 */
class BubbleTrampolineActivity : Activity() {
    companion object {
        const val EXTRA_NID = "nid"
        const val EXTRA_TARGET = "target"
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
        // No UI: transparent theme, forward on focus.
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
        @Suppress("DEPRECATION")
        val fallbackPi = intent.getParcelableExtra(EXTRA_TARGET) as? PendingIntent
        val pi = if (nid != -1) ConversationStore.get(nid)?.contentIntent else null
            ?: fallbackPi
        if (pi == null) {
            Log.w(TAG, "No content intent for nid=$nid")
            finish()
            return
        }
        try {
            pi.send()
            Log.d(TAG, "Forwarded content intent for nid=$nid")
            // Do not finish here: see the class comment.
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Content intent canceled for nid=$nid")
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
