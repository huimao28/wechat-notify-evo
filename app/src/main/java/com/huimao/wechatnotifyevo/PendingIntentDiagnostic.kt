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

    data class MainUserCheck(val present: Boolean, val isString: Boolean, val nonEmpty: Boolean)
    data class Result(val status: String, val elapsedMs: Long, val mainUser: MainUserCheck? = null)

    /** Only invoke from an explicit user click in a visible Activity. */
    fun send(activity: Activity, pi: PendingIntent, onResult: (Result) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val callbackArrived = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        fun report(status: String, mainUser: MainUserCheck? = null) {
            val elapsed = SystemClock.elapsedRealtime() - started
            // No notification/session identifiers, payload, result data or exception messages.
            Log.i(TAG, "status=$status elapsedMs=$elapsed")
            onResult(Result(status, elapsed, mainUser))
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
                    callbackArrived.set(true)
                    handler.removeCallbacks(timeout)
                    // Only this explicitly authorized key is inspected; never enumerate extras.
                    // The raw value stays local: no logging, persistence or navigation.
                    val check = try {
                        val present = intent?.hasExtra("Main_User") == true
                        @Suppress("DEPRECATION")
                        val value = if (present) intent?.extras?.get("Main_User") else null
                        MainUserCheck(present, value is String, value is String && value.isNotEmpty())
                    } catch (_: RuntimeException) {
                        // A failed unparcel/read is unknown, not three false results.
                        report("callback_arrived_main_user_check_failed")
                        return
                    }
                    report("callback_arrived", check) // Includes callbacks later than five seconds.
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
