package com.huimao.wechatnotifyevo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var previewReport by rememberSaveable { mutableStateOf(false) }
    fun refresh() { refreshKey++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val snapshot = remember(refreshKey) { DiagnosticsPlatform.snapshot(context) }
    val events = remember(refreshKey) { InMemoryDiagnostics.snapshot() }
    val vendor = remember(snapshot.manufacturer, snapshot.brand) {
        VendorGuidePolicy.identify(snapshot.manufacturer, snapshot.brand)
    }
    DiagnosticsScreen(
        snapshot = snapshot,
        events = events,
        vendor = vendor,
        onBack = onBack,
        onRefresh = ::refresh,
        onRebind = {
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, WeChatListenerService::class.java)) }
                .onSuccess { toast(context, "已请求重新绑定") }.onFailure { toast(context, "重新绑定请求失败") }
            refresh()
        },
        onOpenListener = { openSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onOpenNotifications = { openSettings(context, DiagnosticsPlatform.standardSettingsIntent(Settings.ACTION_APP_NOTIFICATION_SETTINGS, context)) },
        onOpenDetails = { openSettings(context, DiagnosticsPlatform.standardSettingsIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context)) },
        onOpenBattery = { openSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        onOpenBubbles = {
            val intent = if (Build.VERSION.SDK_INT >= 29) DiagnosticsPlatform.standardSettingsIntent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS, context)
            else DiagnosticsPlatform.standardSettingsIntent(Settings.ACTION_APP_NOTIFICATION_SETTINGS, context)
            openSettings(context, intent, DiagnosticsPlatform.standardSettingsIntent(Settings.ACTION_APP_NOTIFICATION_SETTINGS, context))
        },
        onClean = {
            val count = DiagnosticsPlatform.cleanOrphanShortcuts(context)
            toast(context, if (count >= 0) "已清理 $count 个 WNE 失效 Shortcut" else "清理失败，请稍后重试")
            refresh()
        },
        onPreview = { previewReport = true },
        onShare = {
            DiagnosticsPlatform.shareReport(context).onFailure { toast(context, "报告导出失败") }
        },
        onCopy = {
            val report = DiagnosticsPlatform.report(context)
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("WNE 脱敏诊断摘要", report))
            toast(context, "脱敏报告已复制到剪贴板；其他应用可能读取剪贴板")
        },
    )
    if (previewReport) ReportPreviewDialog(DiagnosticsPlatform.report(context), onDismiss = { previewReport = false })
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
private fun openSettings(context: Context, primary: Intent, fallback: Intent = Intent(Settings.ACTION_SETTINGS)) {
    try { context.startActivity(primary) } catch (_: RuntimeException) {
        try { context.startActivity(fallback) } catch (_: RuntimeException) { toast(context, "此设备无法打开该设置页") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(
    snapshot: DiagnosticSnapshot,
    events: List<DiagnosticEvent>,
    vendor: VendorFamily,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRebind: () -> Unit,
    onOpenListener: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenBubbles: () -> Unit,
    onClean: () -> Unit,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("诊断中心") },
            navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { stateDescription = "返回" }) } },
        )
    }) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(360.dp),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(inner),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { PrivacyCard() }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("自检状态") }
            snapshot.statuses.forEach { status -> item { StatusCard(status) } }
            item { DeviceCard(snapshot) }
            item { ActionCard(onRefresh, onRebind, onOpenListener, onOpenNotifications, onOpenDetails, onOpenBattery, onOpenBubbles, onClean) }
            item { VendorCard(vendor) }
            item { ReportCard(onPreview, onShare, onCopy) }
            item(span = { GridItemSpan(maxLineSpan) }) { RecentEventsCard(events) }
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable private fun PrivacyCard() = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    modifier = Modifier.fillMaxWidth(),
) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("仅本进程、仅脱敏诊断", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text("最近事件重启即清空。不会记录标题、正文、联系人、Main_User、通知 key、Intent extras、会话 ID 或凭据。")
} }

@Composable private fun StatusCard(status: DiagnosticStatus) = Card(
    modifier = Modifier.fillMaxWidth().semantics { stateDescription = "${status.label}：${status.value}" },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(status.label, style = MaterialTheme.typography.titleMedium)
    Text(status.value, color = when (status.healthy) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant })
} }

@Composable private fun DeviceCard(snapshot: DiagnosticSnapshot) = DiagnosticSection("设备与版本") {
    Text("应用 ${snapshot.appVersion}")
    Text("${snapshot.manufacturer} / ${snapshot.brand} / ${snapshot.model}")
    Text(snapshot.osVersion)
}

@Composable private fun ActionCard(
    refresh: () -> Unit, rebind: () -> Unit, listener: () -> Unit, notifications: () -> Unit,
    details: () -> Unit, battery: () -> Unit, bubbles: () -> Unit, clean: () -> Unit,
) = DiagnosticSection("自检与修复") {
    FilledTonalButton(refresh, Modifier.fillMaxWidth()) { Text("刷新诊断") }
    Button(rebind, Modifier.fillMaxWidth()) { Text("重新绑定通知监听") }
    OutlinedButton(listener, Modifier.fillMaxWidth()) { Text("打开通知使用权") }
    OutlinedButton(notifications, Modifier.fillMaxWidth()) { Text("打开通知设置") }
    OutlinedButton(bubbles, Modifier.fillMaxWidth()) { Text("打开气泡设置") }
    OutlinedButton(battery, Modifier.fillMaxWidth()) { Text("打开电池优化设置") }
    TextButton(details, Modifier.fillMaxWidth()) { Text("打开应用详情") }
    HorizontalDivider()
    TextButton(clean, Modifier.fillMaxWidth()) { Text("仅清理 WNE 失效 Shortcut") }
    Text("不会自动修改系统设置，也不使用 Shizuku。", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun VendorCard(vendor: VendorFamily) = DiagnosticSection("厂商后台与气泡引导") {
    Text(vendor.displayName, style = MaterialTheme.typography.titleMedium)
    VendorGuidePolicy.steps(vendor).forEachIndexed { i, step -> Text("${i + 1}. $step") }
}

@Composable private fun ReportCard(preview: () -> Unit, share: () -> Unit, copy: () -> Unit) = DiagnosticSection("脱敏报告") {
    Text(DiagnosticReportBuilder.PRIVACY_NOTICE, style = MaterialTheme.typography.bodySmall)
    FilledTonalButton(preview, Modifier.fillMaxWidth()) { Text("预览报告") }
    Button(share, Modifier.fillMaxWidth()) { Text("分享 Markdown") }
    TextButton(copy, Modifier.fillMaxWidth()) { Text("复制摘要（写入剪贴板）") }
}

@Composable private fun RecentEventsCard(events: List<DiagnosticEvent>) = DiagnosticSection("最近微信通知处理") {
    Text("为什么本条未进化：查看每条枚举对应说明；最多保留 20 条。", style = MaterialTheme.typography.bodySmall)
    if (events.isEmpty()) Text("暂无内存事件") else events.forEach { event ->
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(event.timestampMillis))
        Text("$time · user ${event.sourceUserId} · ${event.stage.wireName}", style = MaterialTheme.typography.labelLarge)
        Text(event.stage.explanation(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
}

@Composable private fun DiagnosticSection(title: String, content: @Composable () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    content()
} }

@Composable private fun ReportPreviewDialog(report: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("脱敏报告预览") },
    text = { Text(report, style = MaterialTheme.typography.bodySmall) },
    confirmButton = { TextButton(onDismiss) { Text("关闭") } },
)

private val previewSnapshot = DiagnosticSnapshot(
    "1.0.20-beta.2", listOf(
        DiagnosticStatus("应用运行", "正常", true), DiagnosticStatus("通知使用权", "未配置", false),
        DiagnosticStatus("应用级气泡偏好", "仅允许所选会话"), DiagnosticStatus("Android Auto", "仅包已安装；投影连接未检测"),
    ), "Google", "google", "Pixel", "Android 15 (API 35)",
)

@Preview(name = "Diagnostics Phone", device = Devices.PHONE, showBackground = true)
@Composable private fun DiagnosticsPhonePreview() = WeChatNotifyEvoTheme(dynamicColor = false) {
    DiagnosticsScreen(previewSnapshot, listOf(DiagnosticEvent(0, 0, DiagnosticStage.MISSING_TICKER)), VendorFamily.GENERIC, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Diagnostics Tablet", device = Devices.TABLET, showBackground = true, fontScale = 1.3f)
@Composable private fun DiagnosticsTabletPreview() = WeChatNotifyEvoTheme(dynamicColor = false) {
    DiagnosticsScreen(previewSnapshot, emptyList(), VendorFamily.ONE_UI, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}
