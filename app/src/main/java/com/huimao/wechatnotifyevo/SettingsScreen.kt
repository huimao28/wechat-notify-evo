package com.huimao.wechatnotifyevo

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private data class SettingsUiState(
    val listenerGranted: Boolean,
    val postGranted: Boolean,
    val showPostPermission: Boolean,
    val enabled: Boolean,
    val replyEnabled: Boolean,
    val androidAutoCompatActionsEnabled: Boolean,
    val groupEnabled: Boolean,
    val keepAliveEnabled: Boolean,
    val directChatEnabled: Boolean,
    val circularAvatarEnabled: Boolean,
    val iconChoice: String,
    val hasCustomIcon: Boolean,
    val learningBusy: Boolean,
    val learningStatus: String,
)

private data class SettingsActions(
    val openListenerSettings: () -> Unit = {},
    val requestPostPermission: () -> Unit = {},
    val setEnabled: (Boolean) -> Unit = {},
    val setReplyEnabled: (Boolean) -> Unit = {},
    val setAndroidAutoCompatActionsEnabled: (Boolean) -> Unit = {},
    val setGroupEnabled: (Boolean) -> Unit = {},
    val setKeepAliveEnabled: (Boolean) -> Unit = {},
    val openBatteryOptimization: () -> Unit = {},
    val openAppDetails: () -> Unit = {},
    val setDirectChatEnabled: (Boolean) -> Unit = {},
    val setCircularAvatarEnabled: (Boolean) -> Unit = {},
    val setIconChoice: (String) -> Unit = {},
    val chooseCustomIcon: () -> Unit = {},
    val learnConversation: (Conversation, String) -> Unit = { _, _ -> },
    val refreshLearning: () -> Unit = {},
    val forgetLearning: () -> Unit = {},
    val rebindListener: () -> Unit = {},
)

@Composable
fun SettingsRoute(onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    var listenerGranted by remember { mutableStateOf(false) }
    var postGranted by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(Prefs.isEnabled(context)) }
    var replyEnabled by remember { mutableStateOf(Prefs.isReplyEnabled(context)) }
    var androidAutoCompatActionsEnabled by remember {
        mutableStateOf(Prefs.isAndroidAutoCompatActionsEnabled(context))
    }
    var groupEnabled by remember { mutableStateOf(Prefs.isGroupEnabled(context)) }
    var keepAliveEnabled by remember { mutableStateOf(Prefs.isKeepAliveEnabled(context)) }
    var iconChoice by remember { mutableStateOf(Prefs.iconChoice(context)) }
    var circularAvatarEnabled by remember { mutableStateOf(Prefs.isCircularAvatarEnabled(context)) }
    var directChatEnabled by remember { mutableStateOf(Prefs.isDirectChatEnabled(context)) }
    var confirmDirectChat by rememberSaveable { mutableStateOf(false) }
    var showIconDialog by rememberSaveable { mutableStateOf(false) }
    var learningChoices by remember { mutableStateOf<List<Pair<Conversation, String>>?>(null) }

    fun refreshPermissions() {
        listenerGranted = isListenerGranted(context)
        postGranted = isPostGranted(context)
    }
    fun repostConversations() {
        ConversationStore.allForUser(CurrentUserScope.id()).filter(CurrentUserScope::accepts).forEach {
            WeChatListenerService.repostConversation(context, it, silent = true)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshPermissions() }

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
                        ConversationStore.allForUser(CurrentUserScope.id()).filter(CurrentUserScope::accepts).forEach {
                            WeChatListenerService.repostConversation(appContext, it, silent = true)
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
    ) { refreshPermissions() }

    val state = SettingsUiState(
        listenerGranted = listenerGranted,
        postGranted = postGranted,
        showPostPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        enabled = enabled,
        replyEnabled = replyEnabled,
        androidAutoCompatActionsEnabled = androidAutoCompatActionsEnabled,
        groupEnabled = groupEnabled,
        keepAliveEnabled = keepAliveEnabled,
        directChatEnabled = directChatEnabled,
        circularAvatarEnabled = circularAvatarEnabled,
        iconChoice = iconChoice,
        hasCustomIcon = CustomNotificationIcon.load(context) != null,
        learningBusy = ManualConversationLearning.busy,
        learningStatus = ManualConversationLearning.status,
    )
    val actions = SettingsActions(
        openListenerSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        requestPostPermission = { postPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        setEnabled = {
            enabled = it
            Prefs.setEnabled(context, it)
            if (!it) WeChatListenerService.cancelAll(context)
        },
        setReplyEnabled = { replyEnabled = it; Prefs.setReplyEnabled(context, it); repostConversations() },
        setAndroidAutoCompatActionsEnabled = {
            androidAutoCompatActionsEnabled = it
            Prefs.setAndroidAutoCompatActionsEnabled(context, it)
            repostConversations()
        },
        setGroupEnabled = { groupEnabled = it; Prefs.setGroupEnabled(context, it) },
        setKeepAliveEnabled = { requested ->
            var started = true
            KeepAliveTransition(
                writePreference = { Prefs.setKeepAliveEnabled(context, it) },
                // This callback is invoked only by the visible settings Activity.
                enableKeepAlive = { started = KeepAliveCoordinator.enable(context) },
                disableKeepAlive = { KeepAliveCoordinator.disable(context) },
            ).setEnabled(requested)
            keepAliveEnabled = Prefs.isKeepAliveEnabled(context)
            if (requested && !started) {
                Toast.makeText(context, "系统未允许启动前台保活，请稍后在本页重试", Toast.LENGTH_LONG).show()
            }
        },
        openBatteryOptimization = {
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: RuntimeException) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        },
        openAppDetails = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        },
        setDirectChatEnabled = { requested ->
            if (requested) {
                confirmDirectChat = true
            } else {
                DirectChatTransition(
                    writeLinkedFlags = { Prefs.setDirectChatEnabled(context, it) },
                    cancelPendingLearning = ManualConversationLearning::cancelPending,
                    cleanupLearnedRoutes = {
                        ManualConversationLearning.forget(context, repostNotifications = false)
                    },
                    // Reposting to remove BubbleMetadata is normally safe, but
                    // canceling is stricter on OEM bubble implementations: no
                    // notification update can expand or dispatch its shortcut.
                    removeActiveNotifications = { WeChatListenerService.cancelAll(context) },
                ).setEnabled(false)
                directChatEnabled = false
            }
        },
        setCircularAvatarEnabled = { enabled ->
            circularAvatarEnabled = enabled
            Prefs.setCircularAvatarEnabled(context, enabled)
            ConversationStore.allForUser(CurrentUserScope.id())
                .filter(CurrentUserScope::accepts)
                .forEach { it.rebuildLargeIcon(context, enabled) }
            repostConversations()
        },
        setIconChoice = { iconChoice = it; Prefs.setIconChoice(context, it); repostConversations() },
        chooseCustomIcon = { imageLauncher.launch("image/*") },
        learnConversation = { conversation, token ->
            val activity = context.findActivity()
            if (activity != null) ManualConversationLearning.learn(activity, conversation, token)
        },
        refreshLearning = { ManualConversationLearning.refresh(context) },
        forgetLearning = { ManualConversationLearning.forget(context) },
        rebindListener = {
            NotificationListenerService.requestRebind(ComponentName(context, WeChatListenerService::class.java))
            Toast.makeText(context, "已请求重新绑定监听服务", Toast.LENGTH_SHORT).show()
        },
    )

    SettingsScreen(
        state = state,
        actions = actions,
        onShowIconDialog = { showIconDialog = true },
        onShowLearningChoices = {
            learningChoices = ConversationStore.allForUser(CurrentUserScope.id())
                .filter { CurrentUserScope.accepts(it) && it.contentIntent != null }
                .sortedByDescending { it.notificationPostTime }.map { it to it.snapshotToken }
        },
        onOpenAbout = onOpenAbout,
    )

    if (confirmDirectChat) DirectChatRiskDialog(
        onConfirm = {
            DirectChatTransition(
                writeLinkedFlags = { Prefs.setDirectChatEnabled(context, it) },
                cancelPendingLearning = ManualConversationLearning::cancelPending,
                cleanupLearnedRoutes = { ManualConversationLearning.forget(context) },
                removeActiveNotifications = { WeChatListenerService.cancelAll(context) },
            ).setEnabled(true)
            directChatEnabled = true
            confirmDirectChat = false
            Toast.makeText(context, "已开启，从下一条新消息起生效", Toast.LENGTH_LONG).show()
        },
        onDismiss = { confirmDirectChat = false },
    )
    if (showIconDialog) IconChoiceDialog(
        currentChoice = iconChoice,
        hasCustomIcon = state.hasCustomIcon,
        onChooseImage = actions.chooseCustomIcon,
        onConfirm = { actions.setIconChoice(it); showIconDialog = false },
        onDismiss = { showIconDialog = false },
    )
    learningChoices?.let { choices ->
        LearningChoicesDialog(
            choices = choices,
            onChoose = { conversation, token ->
                learningChoices = null
                actions.learnConversation(conversation, token)
            },
            onDismiss = { learningChoices = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onShowIconDialog: () -> Unit,
    onShowLearningChoices: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("微信通知进化") }) },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { IntroCard() }
            item { PermissionsSection(state, actions) }
            item { CoreSection(state, actions) }
            item { KeepAliveSection(state, actions) }
            item { DirectChatSection(state, actions) }
            item { AppearanceSection(state, actions, onShowIconDialog) }
            item { DiagnosticsSection(state, actions, onShowLearningChoices) }
            item { AboutSection(onOpenAbout) }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("让微信通知更现代", style = MaterialTheme.typography.titleLarge)
            Text(
                "直接监听并重发为 Messaging 样式，支持多消息会话、直接回复与标为已读。仅能隔离标准独立 Android 用户或工作资料。ColorOS 同用户分身无法可靠区分，请将主微信设为默认打开；处理后原通知会被取消，只保留进化版。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PermissionsSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard("权限状态", "两项权限均就绪后才能稳定接收和发送通知") {
        PermissionItem("通知使用权", state.listenerGranted, "允许读取并改写微信通知", actions.openListenerSettings)
        if (state.showPostPermission) {
            HorizontalDivider()
            PermissionItem("发送通知", state.postGranted, "允许显示进化版通知", actions.requestPostPermission)
        }
    }
}

@Composable
private fun CoreSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard("核心功能") {
        SwitchItem("总开关", "关闭后不再进化新通知，并清除已有进化版通知", state.enabled, actions.setEnabled)
        HorizontalDivider()
        SwitchItem("直接回复", "依赖微信在原通知中提供回复入口；兼容模拟回复也受此开关控制", state.replyEnabled, actions.setReplyEnabled)
        HorizontalDivider()
        SwitchItem(
            "Android Auto 兼容动作（模拟）",
            "默认关闭，仅补齐车机展示要求。模拟已读只清除 WNE 本地进化通知、会话和 shortcut；模拟回复仅在本地通知显示“未发送微信”，不会发送到微信。",
            state.androidAutoCompatActionsEnabled,
            actions.setAndroidAutoCompatActionsEnabled,
        )
        HorizontalDivider()
        SwitchItem("群聊进化", "关闭后群聊通知保持原样", state.groupEnabled, actions.setGroupEnabled)
        Text(
            "真实回复/已读入口始终优先且行为不变；兼容动作只补缺失项。它不能绕过 Android Auto 对侧载应用的“未知来源”或 Play 所有权信任过滤。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun KeepAliveSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(
        title = "后台可靠性",
        subtitle = "ColorOS、MagicOS 等系统仍建议允许自启动、后台运行，并设为不优化电池",
    ) {
        SwitchItem(
            "前台保活（可选）",
            "默认关闭。开启会显示常驻低优先级通知，并启用系统调度健康检查与断线监听重绑定；只能提高存活率，不能保证不被系统终止。",
            state.keepAliveEnabled,
            actions.setKeepAliveEnabled,
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "请在厂商系统设置中同时允许本应用自启动、后台运行，并将电池策略设为不优化。这里不会自动申请电池优化白名单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = actions.openBatteryOptimization,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开电池优化设置") }
            TextButton(onClick = actions.openAppDetails, modifier = Modifier.fillMaxWidth()) {
                Text("打开应用详情")
            }
        }
    }
}

@Composable
private fun DirectChatSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(
        title = "气泡聊天",
        subtitle = "开启不会立即打开微信，从下一条新消息起生效",
    ) {
        SwitchItem(
            "气泡内直达聊天",
            "仅影响下一条新消息；遇到尚未学习的新会话时，自动学习仍可能短暂打开微信",
            state.directChatEnabled,
            actions.setDirectChatEnabled,
        )
        Text(
            "是否显示气泡及能否嵌入聊天由 Android、系统桌面与微信版本共同决定。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AppearanceSection(
    state: SettingsUiState,
    actions: SettingsActions,
    onShowIconDialog: () -> Unit,
) {
    SectionCard("外观") {
        SwitchItem(
            "圆形头像兼容",
            "默认开启。把微信方形头像中心裁剪成圆形透明位图，改善部分厂商的通知头像显示；关闭后使用微信原头像。",
            state.circularAvatarEnabled,
            actions.setCircularAvatarEnabled,
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("通知小图标") },
            supportingContent = { Text("修改状态栏小图标；头像角标由系统绘制，无法保证同步改变") },
            trailingContent = {
                Button(onClick = onShowIconDialog) {
                    Text(when (state.iconChoice) {
                        Prefs.ICON_WECHAT -> "微信图标"
                        Prefs.ICON_CUSTOM -> "自定义"
                        else -> "默认"
                    })
                }
            },
            colors = transparentListItemColors(),
        )
    }
}

@Composable
private fun DiagnosticsSection(
    state: SettingsUiState,
    actions: SettingsActions,
    onShowLearningChoices: () -> Unit,
) {
    SectionCard(
        "诊断与学习",
        "这些入口用于验证会话直达、刷新 shortcut 与恢复监听服务",
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onShowLearningChoices,
                enabled = !state.learningBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("手动学习会话（会打开微信）") }
            Text(state.learningStatus, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = actions.refreshLearning,
                enabled = !state.learningBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("刷新当前通知 / shortcut（不打开微信）") }
            TextButton(onClick = actions.forgetLearning, modifier = Modifier.fillMaxWidth()) {
                Text("清除学习结果")
            }
            HorizontalDivider()
            OutlinedButton(onClick = actions.rebindListener, modifier = Modifier.fillMaxWidth()) {
                Text("重新绑定监听服务")
            }
            Text(
                "学习映射仅在内存；Main_User 随直达 shortcut 保存在系统本机，不显示、不记录日志、不上传。映射会在移除、身份变化、监听断开、关闭气泡内直达聊天或重启后清理。自动失败或跳过不会重试。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "仅隔离标准独立 Android 用户或工作资料；ColorOS 同用户分身无法可靠区分，请将主微信设为默认打开。已知限制：回复与标为已读取决于微信通知入口；群聊判定采用“发送者：内容”启发式；不做表情翻译。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AboutSection(onOpenAbout: () -> Unit) {
    SectionCard(stringResource(R.string.settings_other_title)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_title)) },
            supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
            modifier = Modifier
                .clickable(onClick = onOpenAbout)
                .semantics { role = Role.Button },
            colors = transparentListItemColors(),
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    warning: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            Column(
                Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun PermissionItem(label: String, granted: Boolean, description: String, onAction: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Column {
                Text(description)
                Text(
                    if (granted) "已授予" else "未授予",
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        trailingContent = {
            if (!granted) Button(onClick = onAction) { Text("去设置") }
        },
        colors = transparentListItemColors(),
    )
}

@Composable
private fun SwitchItem(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = transparentListItemColors(),
    )
}

@Composable
private fun transparentListItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun DirectChatRiskDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启气泡内直达聊天？") },
        text = {
            Text("确认开启时只保存开关，不刷新现有通知或 shortcut，也不会立即打开微信；功能从下一条新消息起生效。遇到尚未学习的新会话时，自动学习仍可能短暂打开微信并影响未读状态。每个新会话最多尝试一次；进程内至少间隔 60 秒、每小时最多 6 次，忙碌或限频时跳过且不补发，5 秒无有效回调即放弃。系统必须允许本应用显示气泡。Main_User 会随 shortcut 保存在系统本机，不记录日志、不上传。关闭会立即失效在途结果、清理相关 shortcut，并取消当前进化通知以避免厂商系统触发旧气泡；请等待下一条新消息。")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("了解风险并开启") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun IconChoiceDialog(
    currentChoice: String,
    hasCustomIcon: Boolean,
    onChooseImage: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable(currentChoice) { mutableStateOf(currentChoice) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知小图标") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                IconRadioItem("默认", "微信原始通知的小图标", selected == Prefs.ICON_DEFAULT) {
                    selected = Prefs.ICON_DEFAULT
                }
                IconRadioItem("微信图标", "透明背景、白色微信样式双气泡", selected == Prefs.ICON_WECHAT) {
                    selected = Prefs.ICON_WECHAT
                }
                if (hasCustomIcon) {
                    IconRadioItem("自定义", "使用已保存的图片", selected == Prefs.ICON_CUSTOM) {
                        selected = Prefs.ICON_CUSTOM
                    }
                }
                TextButton(onClick = onChooseImage) { Text("选择自定义图片") }
                Text(
                    "透明图片使用透明度；不透明图片以亮度作为遮罩（白色显示，黑色透明）。选择成功后立即应用。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun IconRadioItem(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        leadingContent = { RadioButton(selected = selected, onClick = onClick) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = transparentListItemColors(),
    )
}

@Composable
private fun LearningChoicesDialog(
    choices: List<Pair<Conversation, String>>,
    onChoose: (Conversation, String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择会话并打开微信") },
        text = {
            Column(
                Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("点击会发送一次该通知原始入口并打开微信。只读取回调 Main_User；返回后请刷新 shortcut。")
                if (choices.isEmpty()) Text("没有当前通知，请等待新通知后再试。")
                choices.forEach { (conversation, token) ->
                    FilledTonalButton(
                        onClick = { onChoose(conversation, token) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("学习并打开：${conversation.title}") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun isListenerGranted(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?: return false
    val component = ComponentName(context, WeChatListenerService::class.java).flattenToString()
    return flat.split(":").any { it == component }
}

private fun isPostGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private val previewState = SettingsUiState(
    listenerGranted = true,
    postGranted = false,
    showPostPermission = true,
    enabled = true,
    replyEnabled = true,
    androidAutoCompatActionsEnabled = false,
    groupEnabled = true,
    keepAliveEnabled = false,
    directChatEnabled = false,
    circularAvatarEnabled = true,
    iconChoice = Prefs.ICON_DEFAULT,
    hasCustomIcon = false,
    learningBusy = false,
    learningStatus = "等待操作",
)

@Preview(name = "Phone", widthDp = 412, heightDp = 892, showBackground = true)
@Preview(name = "Tablet", widthDp = 840, heightDp = 900, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WeChatNotifyEvoTheme(dynamicColor = false) {
        SettingsScreen(previewState, SettingsActions(), {}, {}, {})
    }
}
