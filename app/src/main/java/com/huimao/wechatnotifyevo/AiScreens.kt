/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2025-2026 OpenMinis contributors
 * Copyright (C) 2026 WeChat Notify Evo contributors
 * Provider settings interaction adapted from OpenMinis; modified for WNE OpenAI-compatible-only BYOK flow.
 */
package com.huimao.wechatnotifyevo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSummaryScreen(state: AiUiState, actions: AiScreenActions) {
    val context = LocalContext.current
    var showRaw by remember { mutableStateOf(false) }
    var exportRisk by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose(actions.onLeave) }

    if (state.phase is AiPhase.Confirming) AlertDialog(
        onDismissRequest = actions.dismissConfirmation,
        title = { Text("确认发送原文？") },
        text = { Text("将使用 ${state.config.providerName}（${state.config.endpointHost} / ${state.config.model}）发送 ${state.range.label}范围内 ${state.prepared.conversationCount} 个会话、${state.prepared.messages.size} 条消息、${state.prepared.characterCount} 字符。原文将离开设备。") },
        confirmButton = { Button(onClick = actions.generate) { Text("确认并联网生成") } },
        dismissButton = { TextButton(onClick = actions.dismissConfirmation) { Text("取消") } },
    )
    if (showRaw) AlertDialog(
        onDismissRequest = { showRaw = false }, title = { Text("将发送的原文") },
        text = { SelectionContainer { Text(state.prepared.prompt, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { showRaw = false }) { Text("关闭") } },
    )
    if (exportRisk != null) AlertDialog(
        onDismissRequest = { exportRisk = null }, title = { Text("内容可能离开应用") },
        text = { Text(if (exportRisk == "copy") "复制后，剪贴板可能被其他应用读取。" else "分享后，摘要将交给你选择的外部应用。") },
        confirmButton = { Button(onClick = {
            val summary = (state.phase as? AiPhase.Success)?.summary.orEmpty()
            if (exportRisk == "copy") {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("AI 总结", summary))
                Toast.makeText(context, "已复制，请留意剪贴板风险", Toast.LENGTH_LONG).show()
            } else shareSummary(context, summary)
            exportRisk = null
        }) { Text("继续") } },
        dismissButton = { TextButton(onClick = { exportRisk = null }) { Text("取消") } },
    )

    Scaffold(topBar = { TopAppBar(title = { Text("AI 总结") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PrivacyBanner(state) }
            if (state.providers.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("本次使用 Provider：${state.config.providerName} · ${state.config.endpointHost} · ${state.config.model}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.providers.filter { it.enabled }.forEach { p ->
                            if (p.id == state.selectedProviderId) Button(onClick = { actions.selectProvider(p.id) }) { Text(p.displayName) }
                            else OutlinedButton(onClick = { actions.selectProvider(p.id) }) { Text(p.displayName) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiRange.entries.forEach { range ->
                        if (range == state.range) Button(onClick = { actions.setRange(range) }) { Text(range.label) }
                        else OutlinedButton(onClick = { actions.setRange(range) }) { Text(range.label) }
                    }
                }
            }
            if (state.loading) item { CircularProgressIndicator(Modifier.semantics { contentDescription = "正在读取本地记录" }) }
            items(state.conversations, key = { it.stableId }) { conversation ->
                Card(Modifier.fillMaxWidth().toggleable(conversation.stableId in state.selectedIds) { actions.toggle(conversation.stableId) }) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Checkbox(conversation.stableId in state.selectedIds, onCheckedChange = null)
                        Column { Text(conversation.title, style = MaterialTheme.typography.titleMedium); Text("${conversation.messageCount} 条通知捕获消息", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Text("已选 ${state.selectedIds.size} 个会话；默认不选择任何会话。排除列表中的会话不会出现。", style = MaterialTheme.typography.bodySmall) }
            item { Button(onClick = actions.preview, enabled = state.config.configured && state.selectedIds.isNotEmpty() && !state.loading, modifier = Modifier.fillMaxWidth()) { Text("准备并预览") } }
            if (state.prepared.messages.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.prepared.conversationCount} 会话 · ${state.prepared.messages.size} 消息 · ${state.prepared.characterCount} 字符")
                        Text("预览包含解密后的标题、发送者与消息原文；确认前不会联网。", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { showRaw = true }) { Text("查看将发送的原文") }
                        Button(onClick = actions.confirm, enabled = state.phase !is AiPhase.Running) { Text("生成总结") }
                    }
                }
            }
            when (val phase = state.phase) {
                AiPhase.ConfigMissing -> item { Text("AI 未配置。请在“设置”中填写 HTTPS 端点、模型和 API Key。", color = MaterialTheme.colorScheme.error) }
                AiPhase.Running -> item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("正在生成。离开页面会取消请求。"); OutlinedButton(onClick = actions.cancel) { Text("取消") } } } }
                is AiPhase.Error -> item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(phase.message, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = actions.retry) { Text("重试") } } } }
                is AiPhase.Success -> item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("总结（仅保存在内存）", style = MaterialTheme.typography.titleLarge); SelectionContainer { Text(phase.summary) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { exportRisk = "copy" }) { Text("复制") }; OutlinedButton(onClick = { exportRisk = "share" }) { Text("系统分享") } } } } }
                else -> Unit
            }
        }
    }
}

data class AiScreenActions(
    val setRange: (AiRange) -> Unit, val selectProvider: (String) -> Unit, val toggle: (String) -> Unit, val preview: () -> Unit,
    val confirm: () -> Unit, val dismissConfirmation: () -> Unit, val generate: () -> Unit,
    val cancel: () -> Unit, val retry: () -> Unit, val onLeave: () -> Unit,
)

@Composable private fun PrivacyBanner(state: AiUiState) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (state.config.configured) "BYOK 已配置 · ${state.config.endpointHost}" else "BYOK 尚未配置", style = MaterialTheme.typography.titleMedium)
        Text("仅在你预览并再次确认后联网；不会自动总结。总结默认不写入数据库或文件。通知捕获可能不完整，不能作为完整聊天备份。")
    }
}

@Composable
fun AiSettingsCard(state: AiUiState, reload: () -> Unit, requestTest: () -> Unit) {
    val context = LocalContext.current
    val editorSaver = remember { mapSaver(
        save = { value: ProviderEditorState -> mapOf(
            "mode" to when (value.mode) {
                ProviderEditorMode.Closed -> "closed"
                ProviderEditorMode.Create -> "create"
                is ProviderEditorMode.Edit -> "edit"
            },
            "id" to value.editingId.orEmpty(), "name" to value.name,
            "endpoint" to value.endpoint, "model" to value.model,
        ) },
        restore = { saved ->
            val mode = when (saved["mode"] as String) {
                "create" -> ProviderEditorMode.Create
                "edit" -> ProviderEditorMode.Edit(saved["id"] as String)
                else -> ProviderEditorMode.Closed
            }
            ProviderEditorState(mode, saved["name"] as String, saved["endpoint"] as String, saved["model"] as String)
        },
    ) }
    var editor by rememberSaveable(stateSaver = editorSaver) { mutableStateOf(ProviderEditorState()) }
    // API keys deliberately stay outside SavedState and are cleared whenever editor identity changes.
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var testConfirm by remember { mutableStateOf(false) }

    fun openCreate() { editor = editor.beginCreate(); key = ""; reveal = false }
    fun openEdit(provider: AiProvider) { editor = editor.beginEdit(provider); key = ""; reveal = false }
    fun closeEditor() { editor = editor.cancel(); key = ""; reveal = false; testConfirm = false }

    val selected = state.providers.firstOrNull { it.id == editor.editingId }
    fun entityFromDraft(): AiProviderEntity {
        val now = System.currentTimeMillis()
        return (selected?.entity ?: AiProviderEntity(
            displayName = editor.name, baseUrl = "", endpoint = editor.endpoint,
            modelId = editor.model, createdAt = now, updatedAt = now,
            sortOrder = state.providers.size,
        )).copy(displayName = editor.name, endpoint = editor.endpoint, modelId = editor.model)
    }
    fun save(makeDefault: Boolean) {
        val entity = entityFromDraft()
        AiProviderStore.save(context, entity, key.toCharArray(), makeDefault = makeDefault).onSuccess { config ->
            val saved = config.providers.firstOrNull { it.id == entity.id }
            if (saved != null) openEdit(saved) else closeEditor()
            reload()
        }
    }

    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("清除 API Key？") }, text = { Text("清除后该 Provider 无法请求，且不能恢复。") }, confirmButton = { Button(onClick = { selected?.let { AiProviderStore.save(context, it.entity, null, clearKey = true) }; clearConfirm = false; reload() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("确认清除") } }, dismissButton = { TextButton(onClick = { clearConfirm = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") } })
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("删除 Provider？") }, text = { Text("将删除普通配置和对应密钥；默认 Provider 会回退到首个已启用项。") }, confirmButton = { Button(onClick = { editor.editingId?.let { AiProviderStore.delete(context, it) }; deleteConfirm = false; closeEditor(); reload() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") } })
    if (testConfirm) AlertDialog(onDismissRequest = { testConfirm = false }, title = { Text("测试连接？") }, text = { Text("仅测试当前已保存 Provider，并发送固定 ping；不会发送聊天内容。Key 留空会保留已保存值。") }, confirmButton = { Button(onClick = { testConfirm = false; requestTest() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("确认测试") } }, dismissButton = { TextButton(onClick = { testConfirm = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") } })

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI Provider（OpenAI-compatible）", style = MaterialTheme.typography.titleLarge)
            Text("支持多个 Provider；不支持 Gemini native。端点必须是完整 HTTPS chat/completions URL。", style = MaterialTheme.typography.bodySmall)
            state.providers.forEach { provider ->
                Card(onClick = { openEdit(provider) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "编辑 Provider：${provider.displayName}" }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(provider.displayName)
                        Text("${if (provider.id == state.selectedProviderId) "当前" else ""} ${provider.endpointHost} · ${provider.modelId} · ${if (provider.hasApiKey) "Key 已配置" else "Key 未配置"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = ::openCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "新增 AI Provider" }) { Text("新增 Provider") }
            if (editor.isVisible) {
                OutlinedTextField(editor.name, { editor = editor.copy(name = it) }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                Text("类型：OPENAI_COMPAT", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AiProviderPreset.entries.forEach { preset -> TextButton(onClick = { editor = editor.copy(name = preset.label, endpoint = preset.endpoint, model = preset.model) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(preset.label) } }
                }
                OutlinedTextField(editor.endpoint, { editor = editor.copy(endpoint = it) }, label = { Text("HTTPS chat completions endpoint") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(editor.model, { editor = editor.copy(model = it) }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text(if (selected?.hasApiKey == true) "API Key（留空保留）" else "API Key") }, visualTransformation = if (reveal) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { reveal = !reveal }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = if (reveal) "隐藏 API Key" else "显示 API Key 输入" }) { Text(if (reveal) "隐藏 Key" else "显示输入") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = { save(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("保存") }
                    Button(onClick = { save(true) }, enabled = editor.canSaveAsDefault, modifier = Modifier.heightIn(min = 48.dp)) { Text("保存并默认") }
                    OutlinedButton(onClick = ::closeEditor, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "取消编辑 Provider 并清空草稿" }) { Text("取消") }
                }
                if (selected != null) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { AiProviderStore.setEnabled(context, selected.id, !selected.enabled); reload() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (selected.enabled) "停用" else "启用") }
                        TextButton(onClick = { AiProviderStore.setDefault(context, selected.id); reload() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("设为默认") }
                        TextButton(onClick = { clearConfirm = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("清除 Key") }
                        TextButton(onClick = { deleteConfirm = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("删除") }
                    }
                }
                val isCreate = editor.mode is ProviderEditorMode.Create
                OutlinedButton(onClick = { testConfirm = true }, enabled = !isCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = if (isCreate) "保存 Provider 后测试连接" else "测试已保存 Provider 连接" }) {
                    Text(if (isCreate) "保存后测试" else "测试连接（固定 ping）")
                }
            }
            Text("API Key 由 Android Keystore AES-GCM 按 Provider 隔离；不进入 Room、SavedState、日志或备份。", style = MaterialTheme.typography.bodySmall)
            state.testStatus?.let { Text(it) }
        }
    }
}
private fun shareSummary(context: Context, summary: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, summary)
    context.startActivity(Intent.createChooser(intent, "分享 AI 总结"))
}
