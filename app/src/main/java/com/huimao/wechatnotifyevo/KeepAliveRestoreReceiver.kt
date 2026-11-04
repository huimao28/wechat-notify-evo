package com.huimao.wechatnotifyevo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Restores only an explicit user opt-in after system-defined exempt broadcasts. */
class KeepAliveRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        // Both persisted scheduling and best-effort FGS restoration are centralized.
        // OEM policy can still reject the start; the coordinator contains failures.
        if (!KeepAliveCoordinator.restoreIfEnabled(context) && Prefs.isKeepAliveEnabled(context)) {
            Log.w(TAG, "Deferred foreground restore; system did not allow this start")
        }
    }

    private companion object {
        const val TAG = "KeepAliveRestore"
    }
}
