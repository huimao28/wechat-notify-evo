package com.huimao.wechatnotifyevo

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/** System-scheduled health check; it never reads notification content. */
class KeepAliveHealthJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (!Prefs.isKeepAliveEnabled(this)) return false
        return try {
            KeepAliveCoordinator.runHealthCheck(this, "job")
            false
        } catch (error: RuntimeException) {
            Log.w(TAG, "Health job failed safely: ${error.javaClass.simpleName}")
            false
        } catch (error: LinkageError) {
            Log.w(TAG, "Health job API unavailable: ${error.javaClass.simpleName}")
            false
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    private companion object {
        const val TAG = "KeepAliveHealthJob"
    }
}
