package com.huimao.wechatnotifyevo

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log

/** Single entry point for every optional keep-alive mechanism. */
object KeepAliveCoordinator {
    private const val TAG = "KeepAliveCoordinator"
    internal const val PERIODIC_JOB_ID = 0x4b4101
    private const val RECOVERY_JOB_ID = 0x4b4102
    private const val REBIND_COOLDOWN_MILLIS = 2 * 60 * 1000L
    private const val PERIODIC_INTERVAL_MILLIS = 15 * 60 * 1000L
    private const val RECOVERY_DELAY_MILLIS = 10_000L
    private const val RECOVERY_DEADLINE_MILLIS = 60_000L
    private val rebindCooldown = RebindCooldown(REBIND_COOLDOWN_MILLIS)

    /** Called only after the preference has been persisted as enabled. */
    @Synchronized
    fun enable(context: Context): Boolean {
        val app = context.applicationContext
        if (!Prefs.isKeepAliveEnabled(app)) {
            disable(app)
            return false
        }
        schedulePeriodic(app)
        return OptionalKeepAliveService.startIfEnabled(app)
    }

    /** Used by boot/package-replaced and other system-originated restoration paths. */
    @Synchronized
    fun restoreIfEnabled(context: Context): Boolean {
        val app = context.applicationContext
        if (!Prefs.isKeepAliveEnabled(app)) {
            disable(app)
            return false
        }
        schedulePeriodic(app)
        return OptionalKeepAliveService.startIfEnabled(app)
    }

    /** Cancels all jobs before stopping the FGS; running jobs re-check the preference. */
    @Synchronized
    fun disable(context: Context) {
        val app = context.applicationContext
        cancelJobs(app)
        OptionalKeepAliveService.stop(app)
    }

    /** Low-cost check: no notification content is queried or read. */
    @Synchronized
    fun runHealthCheck(context: Context, source: String) {
        val app = context.applicationContext
        if (!Prefs.isKeepAliveEnabled(app)) return
        if (!WeChatListenerService.isListenerConnected() &&
            rebindCooldown.tryAcquire(SystemClock.elapsedRealtime())
        ) {
            // Check again inside the serialized section immediately before the API call.
            if (!Prefs.isKeepAliveEnabled(app)) return
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(app, WeChatListenerService::class.java)
                )
            } catch (error: RuntimeException) {
                Log.w(TAG, "Listener rebind rejected from $source: ${error.javaClass.simpleName}")
            } catch (error: LinkageError) {
                Log.w(TAG, "Listener rebind unavailable from $source: ${error.javaClass.simpleName}")
            }
        }
        if (Prefs.isKeepAliveEnabled(app)) {
            OptionalKeepAliveService.startIfEnabled(app)
        }
    }

    /** onTaskRemoved must defer to system scheduling instead of starting an FGS directly. */
    @Synchronized
    fun scheduleTaskRemovedRecovery(context: Context) {
        val app = context.applicationContext
        if (!Prefs.isKeepAliveEnabled(app)) return
        try {
            scheduler(app)?.schedule(
                JobInfo.Builder(
                    RECOVERY_JOB_ID,
                    ComponentName(app, KeepAliveHealthJobService::class.java),
                )
                    .setMinimumLatency(RECOVERY_DELAY_MILLIS)
                    .setOverrideDeadline(RECOVERY_DEADLINE_MILLIS)
                    .build()
            )
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to schedule recovery job: ${error.javaClass.simpleName}")
        } catch (error: LinkageError) {
            Log.w(TAG, "Recovery scheduling unavailable: ${error.javaClass.simpleName}")
        }
    }

    private fun schedulePeriodic(context: Context) {
        KeepAliveSchedulingPolicy.reconcile(
            enabled = Prefs.isKeepAliveEnabled(context),
            schedule = {
                try {
                    val result = scheduler(context)?.schedule(
                        JobInfo.Builder(
                            PERIODIC_JOB_ID,
                            ComponentName(context, KeepAliveHealthJobService::class.java),
                        )
                            .setPeriodic(PERIODIC_INTERVAL_MILLIS)
                            .setPersisted(true)
                            .build()
                    )
                    if (result == JobScheduler.RESULT_FAILURE) {
                        Log.w(TAG, "System rejected periodic health job")
                    }
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Unable to schedule periodic job: ${error.javaClass.simpleName}")
                } catch (error: LinkageError) {
                    Log.w(TAG, "Periodic scheduling unavailable: ${error.javaClass.simpleName}")
                }
            },
            cancel = { cancelJobs(context) },
        )
    }

    private fun cancelJobs(context: Context) {
        try {
            scheduler(context)?.apply {
                cancel(PERIODIC_JOB_ID)
                cancel(RECOVERY_JOB_ID)
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to cancel keep-alive jobs: ${error.javaClass.simpleName}")
        } catch (error: LinkageError) {
            Log.w(TAG, "Job cancellation unavailable: ${error.javaClass.simpleName}")
        }
    }

    private fun scheduler(context: Context): JobScheduler? =
        context.getSystemService(JobScheduler::class.java)
}
