@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.huimao.wechatnotifyevo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(state: HistoryUiState, onToggleRecording: (Boolean) -> Unit, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    ScreenList(title = "首页") {
        item(key = "hero", contentType = "card") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("微信通知进化", style = MaterialTheme.typography.headlineSmall)
                    Text(if (state.settings.enabled) "本地记录已开启" else "本地记录保持关闭", style = MaterialTheme.typography.titleMedium)
                    Text("已捕获 ${state.stats.count} 条 · 仅保存在本机并加密", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "record", contentType = "card") {
            ExpressiveCard("本地记录", if (state.settings.enabled) "正在记录新捕获消息" else "默认关闭，不会保存消息正文") {
                Switch(checked = state.settings.enabled, onCheckedChange = onToggleRecording)
            }
        }
        item(key = "actions", contentType = "card") {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("主要入口", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("浏览本地记录") }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("隐私与应用设置") }
            } }
        }
    }
}

@Composable
fun HistoryScreen(state: HistoryUiState, onRange: (HistoryRange) -> Unit, onOpen: (String) -> Unit, onLoadMore: () -> Unit, onClear: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) ConfirmDialog("清空所有本地记录？", "此操作不可撤销。加密数据库中的全部消息会被删除。", "清空", { confirmClear = false; onClear() }, { confirmClear = false })
    ScreenList(title = "记录") {
        item(key = "filters", contentType = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryRange.entries.forEach { range -> FilterChip(selected = state.range == range, onClick = { onRange(range) }, label = { Text(range.label) }) }
            }
        }
        state.error?.let { error -> item(key = "error", contentType = "error") { CryptoErrorCard(error, onClear) } }
        if (!state.settings.enabled) {
            item(key = "off", contentType = "empty") { EmptyCard("本地记录未开启", "前往设置开启后，才会从新的进化通知开始记录。已有通知不会补录。") }
        } else if (state.conversations.isEmpty() && !state.loading && state.error == null) {
            item(key = "empty", contentType = "empty") { EmptyCard("暂无记录", "所选时间范围内还没有捕获到会话。") }
        }
        items(state.conversations, key = { it.stableId }, contentType = { "conversation" }) { conversation ->
            val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
            ListItem(
                headlineContent = { Text(conversation.title) },
                supportingContent = { Text("${formatter.format(Date(conversation.lastTimestamp))} · ${conversation.messageCount} 条${if (conversation.titleDecryptionFailed) " · 标题解密失败" else ""}") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(conversation.stableId) }.semantics { role = Role.Button },
            )
            HorizontalDivider()
        }
        if (state.hasMoreConversations) item(key = "more", contentType = "action") { FilledTonalButton(onClick = onLoadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("加载更多会话") } }
        if (state.stats.count > 0) item(key = "clear", contentType = "action") { TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("清空所有本地记录") } }
    }
}

@Composable
fun HistoryDetailScreen(state: HistoryUiState, onBack: () -> Unit, onLoadMore: () -> Unit, onDelete: (String) -> Unit, onClear: () -> Unit) {
    val id = state.detailStableId ?: return
    var confirmDelete by rememberSaveable(id) { mutableStateOf(false) }
    if (confirmDelete) ConfirmDialog("删除此会话？", "该会话的全部本地记录会被永久删除。", "删除", { confirmDelete = false; onDelete(id) }, { confirmDelete = false })
    Scaffold(topBar = { TopAppBar(title = { Text(state.conversations.firstOrNull { it.stableId == id }?.title ?: "会话记录") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }, actions = { TextButton(onClick = { confirmDelete = true }) { Text("删除") } }) }) { inner ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = inner.calculateTopPadding() + 8.dp, end = 16.dp, bottom = inner.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.error?.let { item(key = "error", contentType = "error") { CryptoErrorCard(it, onClear) } }
            items(state.detailMessages, key = { it.id }, contentType = { "message" }) { message -> MessageCard(message) }
            if (state.hasMoreMessages) item(key = "more", contentType = "action") { FilledTonalButton(onClick = onLoadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("加载更早/更多消息") } }
        }
    }
}

@Composable
private fun MessageCard(message: StoredHistoryMessage) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Card(colors = CardDefaults.cardColors(containerColor = if (message.incoming) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(message.sender.ifBlank { message.title }, style = MaterialTheme.typography.labelLarge)
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
            Text(formatter.format(Date(message.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AiPlaceholderScreen() = ScreenList(title = "AI 总结") {
    item(key = "status", contentType = "card") { EmptyCard("尚未配置", "本阶段不会联网，也没有 API Key 或 AI 客户端。") }
    item(key = "flow", contentType = "card") { Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("未来的隐私流程", style = MaterialTheme.typography.titleLarge)
        Text("1  选择：只选择你明确勾选的本地会话")
        Text("2  预览：发送前查看将使用的时间范围与内容")
        Text("3  确认：再次确认后才允许交给未来配置的服务")
        Text("当前不会上传任何数据。", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    } } }
}

@Composable
fun HistoryPrivacySettingsCard(state: HistoryUiState, onEnabled: (Boolean) -> Unit, onRetention: (Retention) -> Unit, onExcluded: (Set<String>, Boolean) -> Unit, onClear: () -> Unit) {
    var retentionDialog by rememberSaveable { mutableStateOf(false) }
    var exclusionDialog by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    if (retentionDialog) RetentionDialog(state.settings.retention, { retentionDialog = false; onRetention(it) }, { retentionDialog = false })
    if (exclusionDialog) ExclusionDialog(state.conversations, state.settings.excludedStableConversationIds, { ids, delete -> exclusionDialog = false; onExcluded(ids, delete) }, { exclusionDialog = false })
    if (clearDialog) ConfirmDialog("清空所有本地记录？", "设置会保留，但所有已记录消息会被删除。", "清空", { clearDialog = false; onClear() }, { clearDialog = false })
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("隐私与本地记录", style = MaterialTheme.typography.titleLarge); Text("默认关闭 · AES-GCM 加密 · 不联网", style = MaterialTheme.typography.bodySmall) }
            ListItem(headlineContent = { Text("记录新消息") }, supportingContent = { Text("只记录成功生成的进化通知；排除会话不会写入") }, trailingContent = { Switch(state.settings.enabled, onEnabled) }, modifier = Modifier.clickable { onEnabled(!state.settings.enabled) })
            HorizontalDivider()
            ListItem(headlineContent = { Text("保留期") }, supportingContent = { Text(RetentionSelection.label(state.settings.retention)) }, modifier = Modifier.clickable { retentionDialog = true }.heightIn(min = 56.dp))
            ListItem(headlineContent = { Text("用量") }, supportingContent = { Text("${state.stats.count} / ${HistoryPolicy.MAX_ROWS} 条 · ${formatBytes(state.stats.encryptedBytes)} 加密内容") })
            ListItem(headlineContent = { Text("排除会话") }, supportingContent = { Text("已排除 ${state.settings.excludedStableConversationIds.size} 个；从已记录会话选择") }, modifier = Modifier.clickable { exclusionDialog = true }.heightIn(min = 56.dp))
            TextButton(onClick = { clearDialog = true }, enabled = state.stats.count > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("清空本地记录") }
        }
    }
}

@Composable
private fun RetentionDialog(current: Retention, onSelect: (Retention) -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("选择保留期") }, text = { Column { RetentionSelection.values.forEach { value -> ListItem(headlineContent = { Text(RetentionSelection.label(value)) }, leadingContent = { RadioButton(current == value, onClick = { onSelect(value) }) }, modifier = Modifier.clickable { onSelect(value) }) } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

@Composable
private fun ExclusionDialog(conversations: List<ConversationHistorySummary>, current: Set<String>, onSave: (Set<String>, Boolean) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(current) { mutableStateOf(current) }
    var deleteExisting by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("排除会话") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("选中的会话今后不会记录。")
        if (conversations.isEmpty()) Text("暂无可选择的已记录会话。")
        conversations.forEach { item -> ListItem(headlineContent = { Text(item.title) }, leadingContent = { Checkbox(item.stableId in selected, onCheckedChange = null) }, modifier = Modifier.clickable { selected = if (item.stableId in selected) selected - item.stableId else selected + item.stableId }) }
        ListItem(headlineContent = { Text("排除并删除已有记录") }, supportingContent = { Text("保存时删除所有已选会话的已有消息") }, leadingContent = { Checkbox(deleteExisting, { deleteExisting = it }) }, modifier = Modifier.clickable { deleteExisting = !deleteExisting })
    } }, confirmButton = { Button(onClick = { onSave(selected, deleteExisting) }) { Text(if (deleteExisting) "确认并删除" else "保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun CryptoErrorCard(error: HistoryUiError, onClear: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(if (error == HistoryUiError.KEY_UNAVAILABLE) "无法解密本地记录" else "读取本地记录失败", style = MaterialTheme.typography.titleMedium); Text("密钥可能已失效。为保护隐私，不会显示或导出密文。"); Button(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) { Text("清除本地记录") } } } }
@Composable private fun EmptyCard(title: String, body: String) { Card { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun ExpressiveCard(title: String, body: String, trailing: @Composable () -> Unit) { Card { ListItem(headlineContent = { Text(title) }, supportingContent = { Text(body) }, trailingContent = trailing) } }
@Composable private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { Button(onClick = onConfirm) { Text(confirm) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }
private fun formatBytes(bytes: Long): String = if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KiB" else "${bytes / (1024 * 1024)} MiB"

@Composable
private fun ScreenList(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { inner -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = inner.calculateTopPadding() + 8.dp, end = 16.dp, bottom = inner.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) } }

@Preview(name = "History Phone", widthDp = 412, heightDp = 892, showBackground = true)
@Preview(name = "History Tablet", widthDp = 840, heightDp = 900, showBackground = true)
@Composable private fun HistoryPreview() { WeChatNotifyEvoTheme(dynamicColor = false) { HistoryScreen(HistoryUiState(settings = HistorySettingsSnapshot(enabled = true), stats = HistoryStats(12, 2048), conversations = listOf(ConversationHistorySummary("demo", "家人群", 1_700_000_000_000, 12, false)), loading = false), {}, {}, {}, {}) } }
@Preview(name = "History Dark", widthDp = 412, heightDp = 892, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable private fun HistoryDarkPreview() { WeChatNotifyEvoTheme(dynamicColor = false) { AiPlaceholderScreen() } }
@Preview(name = "History Large font", widthDp = 412, heightDp = 892, fontScale = 1.5f, showBackground = true)
@Composable private fun HistoryLargeFontPreview() { WeChatNotifyEvoTheme(dynamicColor = false) { HomeScreen(HistoryUiState(), {}, {}, {}) } }
