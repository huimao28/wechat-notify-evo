package com.huimao.wechatnotifyevo

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationFactory.ensureChannels(this)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EvoScreen()
                }
            }
        }
    }
}

@Composable
private fun EvoScreen() {
    val context = LocalContext.current
    var listenerGranted by remember { mutableStateOf(false) }
    var postGranted by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(Prefs.isEnabled(context)) }
    var replyEnabled by remember { mutableStateOf(Prefs.isReplyEnabled(context)) }
    var groupEnabled by remember { mutableStateOf(Prefs.isGroupEnabled(context)) }
    var iconChoice by remember { mutableStateOf(Prefs.iconChoice(context)) }
    var bubbleEnabled by remember { mutableStateOf(Prefs.isBubbleEnabled(context)) }
    var showIconDialog by remember { mutableStateOf(false) }

    fun refresh() {
        listenerGranted = isListenerGranted(context)
        postGranted = isPostGranted(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            Thread {
                val success = CustomNotificationIcon.import(appContext, uri)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (success) {
                        iconChoice = Prefs.ICON_CUSTOM
                        Prefs.setIconChoice(appContext, iconChoice)
                        showIconDialog = false
                        for (conv in ConversationStore.all()) {
                            WeChatListenerService.repostConversation(appContext, conv, silent = true)
                        }
                    } else {
                        Toast.makeText(appContext, "图片无法导入，请选择不超过 10 MB 的有效图片", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    val postPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("微信通知进化", style = MaterialTheme.typography.headlineMedium)
        Text(
            "不依赖 Nevolution 主应用：直接监听微信通知，把它改写成现代 Messaging 样式重发，" +
                "支持多消息会话、直接回复与标为已读。原微信通知会被取消，只保留进化版。",
            style = MaterialTheme.typography.bodyMedium
        )

        StatusRow(
            label = "通知使用权",
            granted = listenerGranted,
            onAction = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            actionLabel = "去开启"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusRow(
                label = "发送通知权限",
                granted = postGranted,
                onAction = {
                    postPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                actionLabel = "去授权"
            )
        }
        SwitchRow(
            label = "总开关",
            desc = "关闭后不再进化新通知，已有进化版通知会被清除",
            checked = enabled
        ) {
            enabled = it
            Prefs.setEnabled(context, it)
            if (!it) WeChatListenerService.cancelAll(context)
        }
        SwitchRow(
            label = "直接回复",
            desc = "在进化版通知上直接回消息（依赖微信提供回复入口）",
            checked = replyEnabled
        ) {
            replyEnabled = it
            Prefs.setReplyEnabled(context, it)
        }
        SwitchRow(
            label = "群聊进化",
            desc = "关闭后群聊通知保持原样",
            checked = groupEnabled
        ) {
            groupEnabled = it
            Prefs.setGroupEnabled(context, it)
        }
        SwitchRow(
            label = "消息气泡",
            desc = "会话通知以系统气泡显示，点气泡直接进微信对应聊天（需在系统设置里允许本应用显示气泡）",
            checked = bubbleEnabled
        ) {
            bubbleEnabled = it
            Prefs.setBubbleEnabled(context, it)
            // Rebuild already-posted evolved notifications so the change
            // shows immediately, not on the next message.
            for (conv in ConversationStore.all()) {
                WeChatListenerService.repostConversation(context, conv, silent = true)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("通知小图标", style = MaterialTheme.typography.titleMedium)
                Text(
                    "修改状态栏小图标；头像角标由系统绘制，不保证随之改变，也无法可靠地单独设置。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = { showIconDialog = true }) {
                Text(when (iconChoice) {
                    Prefs.ICON_WECHAT -> "微信图标"
                    Prefs.ICON_CUSTOM -> "自定义"
                    else -> "默认"
                })
            }
        }
        if (showIconDialog) {
            var selected by remember(iconChoice) { mutableStateOf(iconChoice) }
            AlertDialog(
                onDismissRequest = { showIconDialog = false },
                title = { Text("通知小图标") },
                text = {
                    Column {
                        IconRadioRow(
                            label = "默认",
                            desc = "微信原始通知的小图标",
                            selected = selected == Prefs.ICON_DEFAULT
                        ) { selected = Prefs.ICON_DEFAULT }
                        IconRadioRow(
                            label = "微信图标",
                            desc = "透明背景、白色微信样式双气泡",
                            selected = selected == Prefs.ICON_WECHAT
                        ) { selected = Prefs.ICON_WECHAT }
                        if (CustomNotificationIcon.load(context) != null) {
                            IconRadioRow(
                                label = "自定义",
                                desc = "使用已保存的图片",
                                selected = selected == Prefs.ICON_CUSTOM
                            ) { selected = Prefs.ICON_CUSTOM }
                        }
                        TextButton(onClick = { imageLauncher.launch("image/*") }) {
                            Text("选择自定义图片")
                        }
                        Text("透明图片使用透明度；不透明图片以亮度作为遮罩（白色显示，黑色透明）。选择成功后立即应用。", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        iconChoice = selected
                        Prefs.setIconChoice(context, selected)
                        showIconDialog = false
                        // Refresh already-posted evolved notifications so the
                        // change shows immediately, not on the next message.
                        for (conv in ConversationStore.all()) {
                            WeChatListenerService.repostConversation(
                                context, conv, silent = true
                            )
                        }
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showIconDialog = false }) { Text("取消") }
                }
            )
        }
        Button(onClick = {
            NotificationListenerService.requestRebind(
                ComponentName(context, WeChatListenerService::class.java)
            )
            Toast.makeText(context, "已请求重新绑定监听服务", Toast.LENGTH_SHORT).show()
        }) {
            Text("重新绑定监听服务")
        }
        Text(
            "已知限制：直接回复与标为已读依赖微信是否在通知里提供相应入口；" +
                "群聊判定是启发式（按“发送者：内容”格式）；v1 不做表情翻译。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun IconRadioRow(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    granted: Boolean,
    onAction: () -> Unit,
    actionLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label：${if (granted) "已授予" else "未授予"}")
        if (!granted) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun isListenerGranted(context: Context): Boolean {
    // Settings.Secure.ENABLED_NOTIFICATION_LISTENERS is @hide, not in the
    // public SDK, so use the literal setting key.
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val component = ComponentName(context, WeChatListenerService::class.java).flattenToString()
    return flat.split(":").any { it == component }
}

private fun isPostGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}
