package com.huimao.wechatnotifyevo

import android.content.Context
import android.content.SharedPreferences

/** Simple SharedPreferences-backed settings for the app. */
object Prefs {
    private const val FILE = "wechat_notify_evo"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_REPLY = "reply_enabled"
    private const val KEY_GROUP = "group_enabled"
    private const val KEY_ICON = "icon_choice"

    // Keep these legacy keys for upgrades and for the already-verified runtime path.
    private const val KEY_BUBBLE = "bubble_enabled"
    private const val KEY_AUTO_LEARN = "auto_learning_experiment"
    private const val KEY_LAUNCHER_CONTROL = "launcher_control"
    private const val KEY_DIRECT_CHAT = "bubble_direct_chat_enabled"
    private const val KEY_DIRECT_CHAT_MIGRATED = "bubble_direct_chat_migrated_v1"

    const val ICON_DEFAULT = "default"
    const val ICON_WECHAT = "wechat"
    const val ICON_CUSTOM = "custom"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun isReplyEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_REPLY, true)
    fun setReplyEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REPLY, value).apply()
    }

    fun isGroupEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_GROUP, true)
    fun setGroupEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_GROUP, value).apply()
    }

    /** Normalize old independent switches once without touching unrelated settings. */
    @Synchronized
    private fun migrateDirectChatIfNeeded(prefs: SharedPreferences): Boolean {
        if (prefs.getBoolean(KEY_DIRECT_CHAT_MIGRATED, false)) {
            return prefs.getBoolean(KEY_DIRECT_CHAT, false)
        }
        val migrated = DirectChatModePolicy.migrate(
            DirectChatFlags(
                bubble = prefs.getBoolean(KEY_BUBBLE, true),
                autoLearn = prefs.getBoolean(KEY_AUTO_LEARN, false),
                launcherControl = prefs.getBoolean(KEY_LAUNCHER_CONTROL, false),
            )
        ).enabled
        writeLinkedFlags(prefs, migrated, markMigrated = true)
        return migrated
    }

    private fun writeLinkedFlags(
        prefs: SharedPreferences,
        enabled: Boolean,
        markMigrated: Boolean = true,
    ) {
        // One SharedPreferences commit publishes the master and all compatibility
        // keys together, so readers cannot be left with a persisted mixed state.
        prefs.edit()
            .putBoolean(KEY_DIRECT_CHAT, enabled)
            .putBoolean(KEY_BUBBLE, enabled)
            .putBoolean(KEY_AUTO_LEARN, enabled)
            .putBoolean(KEY_LAUNCHER_CONTROL, enabled)
            .putBoolean(KEY_DIRECT_CHAT_MIGRATED, markMigrated)
            .commit()
    }

    fun isDirectChatEnabled(context: Context): Boolean = migrateDirectChatIfNeeded(prefs(context))

    @Synchronized
    fun setDirectChatEnabled(context: Context, enabled: Boolean) {
        writeLinkedFlags(prefs(context), enabled)
    }

    // Compatibility accessors: all three now represent the one public setting.
    fun isBubbleEnabled(context: Context): Boolean = isDirectChatEnabled(context)
    fun isLauncherControlEnabled(context: Context): Boolean = isDirectChatEnabled(context)
    fun isAutoLearningEnabled(context: Context): Boolean = isDirectChatEnabled(context)
    fun setBubbleEnabled(context: Context, value: Boolean) = setDirectChatEnabled(context, value)
    fun setLauncherControlEnabled(context: Context, value: Boolean) = setDirectChatEnabled(context, value)
    fun setAutoLearningEnabled(context: Context, value: Boolean) = setDirectChatEnabled(context, value)

    fun iconChoice(context: Context): String =
        prefs(context).getString(KEY_ICON, ICON_WECHAT) ?: ICON_WECHAT

    fun setIconChoice(context: Context, value: String) {
        prefs(context).edit().putString(KEY_ICON, value).apply()
    }
}
