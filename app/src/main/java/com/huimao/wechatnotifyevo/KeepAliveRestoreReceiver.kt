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
        if (!Prefs.isKeepAliveEnabled(context)) return

        // These two broadcasts are documented background-FGS start exemptions.
        // OEM policy can still reject the start; the controller catches that safely.
        if (!OptionalKeepAliveService.startIfEnabled(context)) {
            Log.w(TAG, "Deferred foreground restore; system did not allow this start")
        }
    }

    private companion object {
        const val TAG = "KeepAliveRestore"
    }
}
