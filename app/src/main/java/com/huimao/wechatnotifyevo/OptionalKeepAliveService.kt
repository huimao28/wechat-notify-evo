package com.huimao.wechatnotifyevo

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/** User-opted-in foreground reliability aid; it never reads notification content. */
class OptionalKeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!Prefs.isKeepAliveEnabled(this@OptionalKeepAliveService)) {
                stopAndRemove()
                return
            }
            KeepAliveCoordinator.runHealthCheck(this@OptionalKeepAliveService, "foreground-heartbeat")
            if (Prefs.isKeepAliveEnabled(this@OptionalKeepAliveService)) {
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MILLIS)
            }
        }
    }
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeepAlivePreferencePolicy.KEY && !Prefs.isKeepAliveEnabled(this)) {
                stopAndRemove()
            }
        }

    override fun onCreate() {
        super.onCreate()
        runningOrRequested = true
        Prefs.store(this).registerOnSharedPreferenceChangeListener(preferenceListener)
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.isKeepAliveEnabled(this)) {
            stopAndRemove()
            return START_NOT_STICKY
        }
        return try {
            val notification = buildNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startHeartbeat()
            START_STICKY
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to enter foreground: ${error.javaClass.simpleName}")
            stopAndRemove()
            START_NOT_STICKY
        } catch (error: LinkageError) {
            Log.w(TAG, "Foreground API unavailable: ${error.javaClass.simpleName}")
            stopAndRemove()
            START_NOT_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Prefs.isKeepAliveEnabled(this)) {
            KeepAliveCoordinator.scheduleTaskRemovedRecovery(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopHeartbeat()
        runningOrRequested = false
        try {
            Prefs.store(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Preference listener cleanup failed: ${error.javaClass.simpleName}")
        }
        removeNotification(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startHeartbeat() {
        handler.removeCallbacks(heartbeat)
        handler.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MILLIS)
    }

    private fun stopHeartbeat() {
        handler.removeCallbacks(heartbeat)
    }

    private fun stopAndRemove() {
        stopHeartbeat()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Foreground removal failed: ${error.javaClass.simpleName}")
        }
        removeNotification(this)
        stopSelf()
    }

    companion object {
        private const val TAG = "OptionalKeepAlive"
        private const val HEARTBEAT_INTERVAL_MILLIS = 5 * 60 * 1000L
        const val CHANNEL_ID = "notification_listener_keepalive"
        const val NOTIFICATION_ID = 0x4b41

        @Volatile
        private var runningOrRequested = false

        @Synchronized
        fun startIfEnabled(context: Context): Boolean {
            val appContext = context.applicationContext
            if (!Prefs.isKeepAliveEnabled(appContext)) return false
            if (runningOrRequested) return true
            return try {
                runningOrRequested = true
                val intent = Intent(appContext, OptionalKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                true
            } catch (error: ForegroundServiceStartNotAllowedException) {
                runningOrRequested = false
                Log.w(TAG, "Foreground start not allowed by current system state")
                false
            } catch (error: SecurityException) {
                runningOrRequested = false
                Log.w(TAG, "Foreground start rejected by permission or service-type policy")
                false
            } catch (error: IllegalStateException) {
                runningOrRequested = false
                Log.w(TAG, "Foreground start rejected while app is backgrounded")
                false
            } catch (error: RuntimeException) {
                runningOrRequested = false
                Log.w(TAG, "Foreground start failed: ${error.javaClass.simpleName}")
                false
            } catch (error: LinkageError) {
                runningOrRequested = false
                Log.w(TAG, "Foreground start API unavailable: ${error.javaClass.simpleName}")
                false
            }
        }

        @Synchronized
        fun stop(context: Context) {
            val appContext = context.applicationContext
            runningOrRequested = false
            try {
                appContext.stopService(Intent(appContext, OptionalKeepAliveService::class.java))
            } catch (error: RuntimeException) {
                Log.w(TAG, "Foreground stop failed: ${error.javaClass.simpleName}")
            } catch (error: LinkageError) {
                Log.w(TAG, "Foreground stop API unavailable: ${error.javaClass.simpleName}")
            } finally {
                removeNotification(appContext)
            }
        }

        fun isRunningOrRequestedForDiagnostics(): Boolean = runningOrRequested

        fun ensureChannel(context: Context) {
            try {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "通知监听保活",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "用户可选的通知监听前台保活状态"
                        setShowBadge(false)
                    }
                )
            } catch (error: RuntimeException) {
                Log.w(TAG, "Notification channel setup failed: ${error.javaClass.simpleName}")
            }
        }

        private fun buildNotification(context: Context): Notification {
            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_wechat)
                .setContentTitle("通知监听保活中")
                .setContentText("系统会低频健康检查并在断开时请求监听重绑定")
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build()
        }

        private fun removeNotification(context: Context) {
            try {
                context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Keep-alive notification cleanup failed: ${error.javaClass.simpleName}")
            }
        }
    }
}
