package com.huimao.wechatnotifyevo

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import android.os.SystemClock
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

    data class Result(val status: String, val elapsedMs: Long)

    /** Only invoke from an explicit user click in a visible Activity. */
    fun send(activity: Activity, pi: PendingIntent, onResult: (Result) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val callbackArrived = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        fun report(status: String) {
            val elapsed = SystemClock.elapsedRealtime() - started
            // No notification/session identifiers, payload, result data or exception messages.
            Log.i(TAG, "status=$status elapsedMs=$elapsed")
            onResult(Result(status, elapsed))
        }
        val timeout = Runnable {
            if (!callbackArrived.get()) report("callback_not_observed_within_5s")
        }
        try {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } else null
            report("send_started")
            pi.send(activity, 0, null, object : PendingIntent.OnFinished {
                override fun onSendFinished(
                    pendingIntent: PendingIntent?, intent: Intent?, resultCode: Int,
                    resultData: String?, resultExtras: Bundle?
                ) {
                    // Deliberately never inspect any callback payload, even key presence.
                    callbackArrived.set(true)
                    handler.removeCallbacks(timeout)
                    report("callback_arrived") // Also records callbacks later than five seconds.
                }
            }, handler, null, options)
            report("send_returned") // Not proof that a target UI or conversation appeared.
            if (!callbackArrived.get()) handler.postDelayed(timeout, TIMEOUT_MS)
        } catch (e: PendingIntent.CanceledException) {
            handler.removeCallbacks(timeout)
            report("send_exception:${e.javaClass.name}")
        } catch (e: RuntimeException) {
            handler.removeCallbacks(timeout)
            report("send_exception:${e.javaClass.name}")
        }
    }
}
