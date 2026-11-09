package com.huimao.wechatnotifyevo

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Bubble trampoline: a shortcut-based bubble launches the shortcut intent
 * into the bubble window, and that intent is this activity. Once resumed
 * it forwards WeChat's own content PendingIntent, as a notification tap does.
 * Its creator controls the destination; neither send() returning nor
 * OnFinished proves that an exact chat appeared. OnFinished is a completion
 * callback, not a public contract for discovering private conversation routes.
 * Earlier missing callbacks alone cannot identify the cause or establish an
 * Android-version limitation. The optional direct launcher mode is separate.
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
 * on pause (the target took over the bubble window), on focus loss after
 * a successful send (the target opened fullscreen underneath the bubble,
 * which floats on top and so never pauses the trampoline), or after a
 * 5s fallback with a Toast (the send never ran, or ran but the target
 * never appeared).
 */
class BubbleTrampolineActivity : Activity() {
    companion object {
        const val EXTRA_NID = "nid"
        const val EXTRA_SOURCE_USER_ID = "source_user_id"
        private const val TAG = "WNE.Bubble"
        private const val FORWARD_DELAY_MS = 300L
        private const val FALLBACK_DELAY_MS = 5000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var forwarded = false
    private var forwardScheduled = false
    private val forwardRunnable = Runnable {
        forwardScheduled = false
        if (resumed && !forwarded) {
            forwarded = true
            forward()
        }
    }
    private val fallback = Runnable {
        if (!isFinishing) {
            if (!forwarded) {
                Log.w(TAG, "Forward timeout, send never ran")
            } else {
                Log.w(TAG, "Forward timeout after send; target state unknown")
            }
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
        Log.i(TAG, "experiment=activity_context sdk=${Build.VERSION.SDK_INT} task=$taskId display=${windowManager.defaultDisplay.displayId} multiWindow=$isInMultiWindowMode")
        handler.postDelayed(fallback, FALLBACK_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        scheduleForward()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        Log.i(TAG, "onPause forwarded=$forwarded (not proof of target)")
        handler.removeCallbacks(forwardRunnable)
        handler.removeCallbacks(fallback)
        forwardScheduled = false
        // The target may have appeared, or the user may have left; exit.
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.i(TAG, "focus=$hasFocus forwarded=$forwarded (not proof of target)")
        if (hasFocus) {
            scheduleForward()
        } else if (forwarded) {
            // A focus change has several causes, including another window or
            // user action. Keep the existing cleanup, without claiming success.
            handler.removeCallbacks(fallback)
            finish()
        }
    }

    private fun scheduleForward() {
        if (!forwarded && !forwardScheduled) {
            forwardScheduled = true
            // Brief settle delay: sending before the activity is fully
            // foreground can be silently dropped as a background start.
            handler.postDelayed(forwardRunnable, FORWARD_DELAY_MS)
        }
    }

    private fun forward() {
        val nid = intent.getIntExtra(EXTRA_NID, -1)
        val sourceUserId = intent.getIntExtra(EXTRA_SOURCE_USER_ID, -1)
        val currentUserId = CurrentUserScope.id()
        if (sourceUserId != currentUserId) {
            Log.w(TAG, "Rejected bubble route: sourceUserId=$sourceUserId currentUserId=$currentUserId")
            finish()
            return
        }
        val conv = ConversationStore.get(nid, sourceUserId)?.takeIf {
            CurrentUserScope.accepts(it) && intent.getStringExtra("snapshot") == it.routeToken
        }
        val pi = conv?.contentIntent
        if (pi == null) {
            Log.w(TAG, "No live content intent")
            finish()
            return
        }
        // Main_User is used only in this foreground, explicit launch. It is never
        // persisted in ShortcutInfo, preferences, logs or diagnostics.
        if (Prefs.isLauncherControlEnabled(this) && conv.learnedMainUser != null &&
            launchLearnedConversation(conv.learnedMainUser!!)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Grant only this user-triggered send our sender-side BAL privileges.
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                // AOSP uses a non-null context to supply caller display, not
                // necessarily an Activity task token. This is an experiment.
                pi.send(this, 0, null, null, null, null, options.toBundle())
                Log.d(TAG, "Content intent send returned (sender BAL opt-in)")
            } else {
                pi.send()
                Log.d(TAG, "Content intent send returned (legacy)")
            }
            // Returning from send does not prove the target activity appeared.
            // Do not finish here: see the class comment.
        } catch (e: Exception) {
            // CanceledException included: any send failure must surface
            // instead of leaving a black window behind.
            Log.w(TAG, "Content intent send failed: ${e.javaClass.simpleName}")
            Toast.makeText(this, "未能打开微信界面", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun launchLearnedConversation(mainUser: String): Boolean {
        val component = ComponentName(
            WeChatListenerService.WECHAT_PACKAGE, "com.tencent.mm.ui.LauncherUI"
        )
        val info = try { packageManager.getActivityInfo(component, 0) } catch (_: Exception) { null }
        if (info == null || !info.exported || !info.enabled || !info.applicationInfo.enabled ||
            !info.permission.isNullOrEmpty()) return false
        return try {
            startActivity(Intent(Intent.ACTION_MAIN).setComponent(component)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .putExtra("Main_User", mainUser)
                .putExtra("Intro_Is_Muti_Talker", false))
            forwarded = true
            true
        } catch (_: RuntimeException) { false }
    }

    override fun onDestroy() {
        handler.removeCallbacks(forwardRunnable)
        handler.removeCallbacks(fallback)
        super.onDestroy()
    }
}
