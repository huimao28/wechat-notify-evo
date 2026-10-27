package com.huimao.wechatnotifyevo

import android.content.Context

/** Simple SharedPreferences-backed toggles for the app. */
object Prefs {
    private const val FILE = "wechat_notify_evo"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_REPLY = "reply_enabled"
    private const val KEY_GROUP = "group_enabled"
    private const val KEY_ICON = "icon_choice"

    /** Small-icon badge choice: WeChat's original notification icon. */
    const val ICON_DEFAULT = "default"

    /** Small-icon badge choice: WeChat's launcher icon (green logo). */
    const val ICON_WECHAT = "wechat"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun isReplyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REPLY, true)

    fun setReplyEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REPLY, value).apply()
    }

    fun isGroupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GROUP, true)

    fun setGroupEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_GROUP, value).apply()
    }

    fun iconChoice(context: Context): String =
        prefs(context).getString(KEY_ICON, ICON_WECHAT) ?: ICON_WECHAT

    fun setIconChoice(context: Context, value: String) {
        prefs(context).edit().putString(KEY_ICON, value).apply()
    }
}
