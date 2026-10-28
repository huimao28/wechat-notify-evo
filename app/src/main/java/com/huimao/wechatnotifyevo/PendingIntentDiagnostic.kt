package com.huimao.wechatnotifyevo

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** User-triggered only: probes the original WeChat content PendingIntent. */
object PendingIntentDiagnostic {
    private const val TAG = "WNE.PendingProbe"
    private const val TIMEOUT_MS = 5_000L

    data class Result(val nid: Int, val status: String, val mainUserPresent: Boolean?, val elapsedMs: Long)

    fun send(
        pi: PendingIntent,
        nid: Int,
        onResult: (Result) -> Unit,
    ) {
        val started = System.currentTimeMillis()
        val done = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        fun finish(status: String, present: Boolean?) {
            if (!done.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            val elapsed = System.currentTimeMillis() - started
            // Never log the Intent, extras, usernames, or notification text.
            Log.i(TAG, "nid=$nid status=$status mainUserPresent=$present elapsedMs=$elapsed")
            onResult(Result(nid, status, present, elapsed))
        }
        handler.postDelayed({ finish("timeout", null) }, TIMEOUT_MS)
        try {
            pi.send(null, 0, null, object : PendingIntent.OnFinished {
                override fun onSendFinished(
                    pendingIntent: PendingIntent?, intent: Intent?, resultCode: Int, resultData: String?, resultExtras: Bundle?
                ) {
                    // Check only presence; never log values or intent contents.
                    val present = intent?.hasExtra("Main_User") == true || resultExtras?.containsKey("Main_User") == true
                    finish("callback", present)
                }
            }, handler)
        } catch (e: PendingIntent.CanceledException) {
            finish("canceled", null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "send failed exceptionType=${e.javaClass.name}")
            finish("failed:${e.javaClass.simpleName}", null)
        }
    }
}
