package com.huimao.wechatnotifyevo

import android.Manifest
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date

object DiagnosticsPlatform {
    private const val WECHAT = "com.tencent.mm"
    private const val ANDROID_AUTO = "com.google.android.projection.gearhead"

    fun snapshot(
        context: Context,
        autoConnection: AndroidAutoConnectionState = AndroidAutoConnectionStateStore.current(),
    ): DiagnosticSnapshot {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        val listenerGranted = try {
            nm?.isNotificationListenerAccessGranted(ComponentName(app, WeChatListenerService::class.java)) == true
        } catch (_: RuntimeException) { false }
        val postGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val periodic = try {
            app.getSystemService(JobScheduler::class.java)?.allPendingJobs
                ?.any { it.id == KeepAliveCoordinator.PERIODIC_JOB_ID } == true
        } catch (_: RuntimeException) { false }
        val shortcuts = shortcutManager(app)
        val shortcutCount = if (Build.VERSION.SDK_INT >= 30) try {
            shortcuts?.getShortcuts(
                ShortcutManager.FLAG_MATCH_DYNAMIC or ShortcutManager.FLAG_MATCH_CACHED or ShortcutManager.FLAG_MATCH_PINNED
            )?.count { it.id.startsWith("conv_") } ?: 0
        } catch (_: RuntimeException) { 0 } else 0
        val rateLimited = if (Build.VERSION.SDK_INT >= 25) try { shortcuts?.isRateLimitingActive == true } catch (_: RuntimeException) { false } else false
        val (stableIdentityCount, temporaryIdentityCount) = ConversationIdentityStore.counts(app)
        val conversationChannelCount = if (Build.VERSION.SDK_INT >= 30) try {
            nm?.notificationChannels?.count {
                it.conversationId?.startsWith(ConversationIdentityCore.PREFIX) == true
            } ?: 0
        } catch (_: RuntimeException) { 0 } else 0
        val weChat = packageState(app, WECHAT)
        val auto = packageState(app, ANDROID_AUTO)
        val appBubble = if (Build.VERSION.SDK_INT >= 31) try {
            when (nm?.bubblePreference) {
                NotificationManager.BUBBLE_PREFERENCE_ALL -> "允许所有会话"
                NotificationManager.BUBBLE_PREFERENCE_SELECTED -> "仅允许所选会话"
                NotificationManager.BUBBLE_PREFERENCE_NONE -> "不允许"
                else -> "未检测"
            }
        } catch (_: RuntimeException) { "未检测" } else "系统版本不支持"
        val channelBubble = if (Build.VERSION.SDK_INT >= 29) try {
            listOf(NotificationFactory.CHANNEL_MESSAGE, NotificationFactory.CHANNEL_GROUP, NotificationFactory.CHANNEL_BOT)
                .joinToString { id -> "$id=${nm?.getNotificationChannel(id)?.canBubble() ?: false}" }
        } catch (_: RuntimeException) { "未检测" } else "系统版本不支持"
        val statuses = listOf(
            DiagnosticStatus("应用运行", "正常", true),
            DiagnosticStatus("发送通知权限", if (postGranted) "已授予" else "未授予", postGranted),
            DiagnosticStatus("通知使用权", if (listenerGranted) "已配置" else "未配置", listenerGranted),
            DiagnosticStatus("监听服务连接", if (WeChatListenerService.isListenerConnected()) "已连接" else "未连接", WeChatListenerService.isListenerConnected()),
            DiagnosticStatus("总开关", if (Prefs.isEnabled(app)) "开启" else "关闭", Prefs.isEnabled(app)),
            DiagnosticStatus("前台保活偏好", if (Prefs.isKeepAliveEnabled(app)) "开启" else "关闭"),
            DiagnosticStatus("前台保活进程内状态", if (OptionalKeepAliveService.isRunningOrRequestedForDiagnostics()) "运行或已请求" else "未运行/进程重启后未知"),
            DiagnosticStatus("周期健康任务", if (periodic) "已调度" else "未调度", if (Prefs.isKeepAliveEnabled(app)) periodic else null),
            DiagnosticStatus("系统气泡支持", if (Build.VERSION.SDK_INT >= 30) "支持" else "不支持", Build.VERSION.SDK_INT >= 30),
            DiagnosticStatus("应用级气泡偏好", appBubble),
            DiagnosticStatus("渠道 allowBubbles", channelBubble),
            DiagnosticStatus("稳定会话身份", "$stableIdentityCount 个"),
            DiagnosticStatus("临时会话身份", "$temporaryIdentityCount 个"),
            DiagnosticStatus("当前 WNE 会话 Shortcut", "$shortcutCount 个"),
            DiagnosticStatus("会话专属渠道", "$conversationChannelCount 个"),
            DiagnosticStatus("ShortcutManager 限流", if (rateLimited) "活跃" else "未限流", !rateLimited),
            DiagnosticStatus("微信", if (weChat.installed) "已安装${if (weChat.enabled) "且启用" else "但已停用"}" else "未安装", weChat.installed && weChat.enabled),
            DiagnosticStatus("微信版本", weChat.version ?: "未检测"),
            DiagnosticStatus("Android Auto 包", if (auto.installed) "已安装${if (auto.enabled) "且启用" else "但已停用"}" else "未安装", auto.installed && auto.enabled),
            DiagnosticStatus("Android Auto 包版本", auto.version ?: "未检测"),
            DiagnosticStatus("Android Auto 连接", connectionDescription(autoConnection.type), connectionHealth(autoConnection.type)),
            DiagnosticStatus("Android Auto 连接检测", "${autoConnection.source}；最近更新：${connectionUpdatedAt(autoConnection.updatedAtMillis)}"),
        )
        val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "未知" } catch (_: Exception) { "未知" }
        return DiagnosticSnapshot(version, statuses, Build.MANUFACTURER, Build.BRAND, Build.MODEL, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    internal fun connectionDescription(type: AndroidAutoConnectionType): String = when (type) {
        AndroidAutoConnectionType.NOT_CONNECTED -> "未连接"
        AndroidAutoConnectionType.NATIVE -> "运行于 Android Automotive OS"
        AndroidAutoConnectionType.PROJECTION -> "已连接 Android Auto 投影"
        AndroidAutoConnectionType.UNKNOWN -> "连接状态未知（检测中或车载 host 未提供状态）"
    }

    internal fun connectionHealth(type: AndroidAutoConnectionType): Boolean? = when (type) {
        AndroidAutoConnectionType.PROJECTION, AndroidAutoConnectionType.NATIVE -> true
        AndroidAutoConnectionType.NOT_CONNECTED -> false
        AndroidAutoConnectionType.UNKNOWN -> null
    }

    private fun connectionUpdatedAt(updatedAtMillis: Long?): String = updatedAtMillis?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it))
    } ?: "尚未收到状态"

    data class PackageState(val installed: Boolean, val enabled: Boolean, val version: String?)
    private fun packageState(context: Context, name: String): PackageState = try {
        val info = context.packageManager.getPackageInfo(name, 0)
        PackageState(true, info.applicationInfo?.enabled == true, info.versionName)
    } catch (_: PackageManager.NameNotFoundException) { PackageState(false, false, null) }
      catch (_: RuntimeException) { PackageState(false, false, null) }

    private fun shortcutManager(context: Context): ShortcutManager? =
        if (Build.VERSION.SDK_INT >= 25) context.getSystemService(ShortcutManager::class.java) else null

    fun repairConversationIdentities(context: Context): Int = try {
        val current = ConversationStore.allForUser(CurrentUserScope.id())
            .filter(CurrentUserScope::accepts)
        current.forEach { WeChatListenerService.repostConversation(context, it, silent = true) }
        current.size
    } catch (_: RuntimeException) { -1 }

    fun report(context: Context): String = DiagnosticReportBuilder.build(snapshot(context), InMemoryDiagnostics.snapshot())

    fun shareReport(context: Context): Result<Unit> = runCatching {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        require(dir.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
        val file = File(dir, "wne-diagnostics.md")
        require(file.canonicalFile.parentFile == dir.canonicalFile)
        file.writeText(report(context), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, "脱敏诊断报告", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享脱敏诊断报告"))
    }

    fun standardSettingsIntent(action: String, context: Context): Intent = Intent(action).apply {
        when (action) {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> data = Uri.fromParts("package", context.packageName, null)
            Settings.ACTION_APP_NOTIFICATION_SETTINGS -> putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS -> putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
}
