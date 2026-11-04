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
import android.os.IBinder
import android.util.Log

/**
 * User-opted-in foreground service for notification-listener process reliability.
 * It handles no notification content and does not claim that the process cannot be killed.
 */
class OptionalKeepAliveService : Service() {
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeepAlivePreferencePolicy.KEY && !Prefs.isKeepAliveEnabled(this)) {
                stopAndRemove()
            }
        }

    override fun onCreate() {
        super.onCreate()
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
            START_STICKY
        } catch (error: Exception) {
            // Keep logs free of notification payloads and other user data.
            Log.w(TAG, "Unable to enter foreground: ${error.javaClass.simpleName}")
            stopAndRemove()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Prefs.store(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        removeNotification(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopAndRemove() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        removeNotification(this)
        stopSelf()
    }

    companion object {
        private const val TAG = "OptionalKeepAlive"
        const val CHANNEL_ID = "notification_listener_keepalive"
        const val NOTIFICATION_ID = 0x4b41

        @Synchronized
        fun startIfEnabled(context: Context): Boolean {
            val appContext = context.applicationContext
            if (!Prefs.isKeepAliveEnabled(appContext)) return false
            return try {
                val intent = Intent(appContext, OptionalKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                true
            } catch (error: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Foreground start not allowed by current system state")
                false
            } catch (error: SecurityException) {
                Log.w(TAG, "Foreground start rejected by permission or service-type policy")
                false
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Foreground start rejected while app is backgrounded")
                false
            } catch (error: RuntimeException) {
                Log.w(TAG, "Foreground start failed: ${error.javaClass.simpleName}")
                false
            }
        }

        @Synchronized
        fun stop(context: Context) {
            val appContext = context.applicationContext
            try {
                appContext.stopService(Intent(appContext, OptionalKeepAliveService::class.java))
            } catch (error: RuntimeException) {
                Log.w(TAG, "Foreground stop failed: ${error.javaClass.simpleName}")
            } finally {
                removeNotification(appContext)
            }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
                .setContentText("点按打开设置；此功能只能降低被系统终止的概率")
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build()
        }

        private fun removeNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }
}
