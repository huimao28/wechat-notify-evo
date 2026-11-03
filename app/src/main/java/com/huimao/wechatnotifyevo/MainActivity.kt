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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        ConversationShortcuts.pruneOrphans(this)
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
    var launcherControl by remember { mutableStateOf(Prefs.isLauncherControlEnabled(context)) }
    var autoLearning by remember { mutableStateOf(Prefs.isAutoLearningEnabled(context)) }
    var confirmAutoLearning by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var learningChoices by remember { mutableStateOf<List<Pair<Conversation, String>>?>(null) }

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
            .verticalScroll(rememberScrollState())
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
            desc = "请求系统气泡（需系统允许）；展示由系统控制，打开微信不代表聊天已嵌入气泡。",
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
        SwitchRow(
            label = "自动学习（实验）",
            desc = "默认关闭。仅对启用后新会话尝试一次，会发送原微信通知 PendingIntent，可能拉起微信、影响未读状态；不是无感学习。失败不自动重试。",
            checked = autoLearning
        ) {
            if (it) confirmAutoLearning = true else {
                autoLearning = false
                Prefs.setAutoLearningEnabled(context, false)
            }
        }
        if (confirmAutoLearning) {
            AlertDialog(
                onDismissRequest = { confirmAutoLearning = false },
                title = { Text("允许自动发送微信通知入口？") },
                text = { Text("每个新会话最多尝试一次；进程内至少间隔 60 秒、每小时最多 6 次，忙碌或限频时跳过且不补发。会发送原微信 PendingIntent，可能打开微信；不额外授予后台启动权限，系统可能拦截。5 秒无有效回调即放弃。开启前已有会话不会补学。直达仍需自行开启下方 launcher 对照。Main_User 仅在内存处理，但直达 shortcut 会由系统保存在本机，不日志、不上传。关闭立即阻止在途结果应用；不能撤回已发送入口。") },
                confirmButton = { TextButton(onClick = {
                    Prefs.setAutoLearningEnabled(context, true)
                    autoLearning = true
                    confirmAutoLearning = false
                }) { Text("了解风险并开启") } },
                dismissButton = { TextButton(onClick = { confirmAutoLearning = false }) { Text("取消") } }
            )
        }
        SwitchRow(
            label = "实验：直接 launcher 对照（默认关闭）",
            desc = "未学习时打开微信首页；手动学习当前会话后尝试直达。关闭恢复原通知中转；切换后请关闭旧气泡并刷新。仍需真机验证。",
            checked = launcherControl
        ) {
            launcherControl = it
            Prefs.setLauncherControlEnabled(context, it)
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
        Button(enabled = !ManualConversationLearning.busy, onClick = {
            learningChoices = ConversationStore.all().filter { it.contentIntent != null }
                .sortedByDescending { it.notificationPostTime }.map { it to it.snapshotToken }
        }) { Text("手动学习会话（会打开微信）") }
        learningChoices?.let { choices ->
            AlertDialog(
                onDismissRequest = { learningChoices = null },
                title = { Text("选择会话并打开微信") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("点击下方会话会发送一次该通知原始入口并打开微信。只读取回调 Main_User；返回后请刷新 shortcut。")
                        if (choices.isEmpty()) Text("没有当前通知，请等待新通知后再试。")
                        choices.forEach { (conv, token) ->
                            TextButton(onClick = {
                                learningChoices = null
                                val activity = generateSequence(context) {
                                    (it as? android.content.ContextWrapper)?.baseContext
                                }.filterIsInstance<android.app.Activity>().firstOrNull()
                                if (activity != null) ManualConversationLearning.learn(activity, conv, token)
                            }) { Text("学习并打开：${conv.title}") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { learningChoices = null }) { Text("取消") } }
            )
        }
        Text(ManualConversationLearning.status, style = MaterialTheme.typography.bodySmall)
        Button(enabled = !ManualConversationLearning.busy, onClick = {
            ManualConversationLearning.refresh(context)
        }) { Text("刷新当前通知 / shortcut（不打开微信）") }
        TextButton(onClick = { ManualConversationLearning.forget(context) }) { Text("清除学习结果") }
        Text("学习映射仅在内存；Main_User 随直达 shortcut 保存在系统本机，不显示、不日志、不上传。只在同一连续会话对象、相同 notification key 和源身份下复用；每条新消息仍废止旧的在途回调。移除、身份变化、监听断开、重启后清理映射和旧 shortcut。无法识别微信未报告的同名同 key 隐式换人，不能保证跨微信版本可靠。自动失败或跳过不会重试；旧气泡请手动关闭。关闭自动学习不清除已学结果，可用下方清除按钮。直达及气泡嵌入须真机验证。", style = MaterialTheme.typography.bodySmall)
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
