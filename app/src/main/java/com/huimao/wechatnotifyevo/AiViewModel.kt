package com.huimao.wechatnotifyevo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiUiState(
    val config: AiConfig = AiConfig(),
    val providers: List<AiProvider> = emptyList(),
    val selectedProviderId: String? = null,
    val phase: AiPhase = AiPhase.Idle,
    val range: AiRange = AiRange.TODAY,
    val conversations: List<ConversationHistorySummary> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val prepared: AiPreparedPayload = AiPreparedPayload(emptyList(), "", 0, 0),
    val loading: Boolean = false,
    val testStatus: String? = null,
    val providerOperationRunning: Boolean = false,
    val providerOperationMessage: String? = null,
    val providerOperationError: Boolean = false,
)

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val repository = AndroidHistoryRepository.get(app)
    private val secrets: AiSecretStore = AndroidAiSecretStore(app)
    private val client: AiCompletionClient = OkHttpAiCompletionClient()
    private val mutableState = MutableStateFlow(AiUiState())
    val state = mutableState.asStateFlow()
    private var requestJob: Job? = null

    init { reloadConfig(); loadConversations() }

    fun reloadConfig() = providerRead { AiProviderStore.load(app, secrets) }

    fun saveProvider(entity: AiProviderEntity, apiKey: CharArray, makeDefault: Boolean) {
        val ownedKey = apiKey.copyOf()
        apiKey.fill('\u0000')
        providerWrite("Provider 已保存") {
            try { AiProviderStore.save(app, entity, ownedKey.takeIf { it.isNotEmpty() }, makeDefault = makeDefault, s = secrets).getOrThrow() }
            finally { ownedKey.fill('\u0000') }
        }
    }

    fun deleteProvider(id: String) = providerWrite("Provider 已删除") { AiProviderStore.delete(app, id, secrets) }
    fun setDefaultProvider(id: String) = providerWrite("默认 Provider 已更新") { AiProviderStore.setDefault(app, id, secrets) }
    fun setProviderEnabled(id: String, enabled: Boolean) = providerWrite(if (enabled) "Provider 已启用" else "Provider 已停用") { AiProviderStore.setEnabled(app, id, enabled, secrets) }
    fun clearProviderKey(id: String) = providerWrite("API Key 已清除") {
        val provider = AiProviderStore.load(app, secrets).providers.firstOrNull { it.id == id } ?: error("Provider 不存在")
        AiProviderStore.save(app, provider.entity, null, clearKey = true, s = secrets).getOrThrow()
    }

    fun addProviderModel(providerId: String, modelId: String, alias: String?) = providerWrite("模型已添加") { AiProviderStore.addModel(app, providerId, modelId, alias, s = secrets) }
    fun deleteProviderModel(id: String) = providerWrite("模型已删除") { AiProviderStore.deleteModel(app, id, secrets) }
    fun setDefaultModel(providerId: String, entryId: String) = providerWrite("默认模型已更新") { AiProviderStore.setDefaultModel(app, providerId, entryId, secrets) }
    fun refreshProviderModels(providerId: String) = providerWriteSuspend("模型列表已刷新") { AiProviderStore.refreshModels(app, providerId, secrets) }

    private fun providerWriteSuspend(success: String, block: suspend () -> AiProviderConfig) {
        if (mutableState.value.providerOperationRunning) return
        viewModelScope.launch {
            mutableState.update { it.copy(providerOperationRunning = true, providerOperationMessage = null, providerOperationError = false) }
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { applyProviderConfig(it, success) }
                .onFailure { error -> mutableState.update { it.copy(providerOperationRunning = false, providerOperationMessage = ProviderOperationPolicy.safeFailureMessage(error), providerOperationError = true) } }
        }
    }

    private fun providerRead(block: () -> AiProviderConfig) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess(::applyProviderConfig)
                .onFailure { mutableState.update { it.copy(providerOperationMessage = "无法读取 Provider 配置", providerOperationError = true) } }
        }
    }

    private fun providerWrite(success: String, block: () -> AiProviderConfig) {
        if (mutableState.value.providerOperationRunning) return
        viewModelScope.launch {
            mutableState.update { it.copy(providerOperationRunning = true, providerOperationMessage = null, providerOperationError = false) }
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { config -> applyProviderConfig(config, success) }
                .onFailure { error -> mutableState.update { it.copy(providerOperationRunning = false, providerOperationMessage = ProviderOperationPolicy.safeFailureMessage(error), providerOperationError = true) } }
        }
    }

    private fun applyProviderConfig(providerConfig: AiProviderConfig, message: String? = null) {
        val selected = providerConfig.selected(mutableState.value.selectedProviderId)
        val config = selected?.let(::AiConfig) ?: AiConfig()
        mutableState.update { old -> old.copy(
            config = config, providers = providerConfig.providers, selectedProviderId = selected?.id,
            providerOperationRunning = false, providerOperationMessage = message, providerOperationError = false,
            phase = when { !config.configured -> AiPhase.ConfigMissing; old.selectedIds.isEmpty() -> AiPhase.Selecting; else -> old.phase },
        ) }
    }

    fun saveConfig(endpoint: String, model: String, apiKey: CharArray) {
        val ownedKey = apiKey.copyOf(); apiKey.fill('\u0000')
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { AiConfigStore.save(app, endpoint, model, ownedKey.takeIf { it.isNotEmpty() }, secrets) }
                result.onSuccess { config -> mutableState.update { it.copy(config = config, phase = if (it.selectedIds.isEmpty()) AiPhase.Selecting else it.phase, testStatus = "配置已保存") } }
                    .onFailure { mutableState.update { it.copy(testStatus = "配置无效") } }
            } finally { ownedKey.fill('\u0000') }
        }
    }

    fun selectProvider(id: String) {
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { AiProviderStore.load(app, secrets).selected(id) } ?: return@launch
            mutableState.update { it.copy(config = AiConfig(p), selectedProviderId = p.id, phase = if (it.selectedIds.isEmpty()) AiPhase.Selecting else it.phase) }
        }
    }

    fun setRange(range: AiRange) {
        cancel(); mutableState.update { it.copy(range = range, selectedIds = emptySet(), prepared = AiPreparedPayload(emptyList(), "", 0, 0), phase = if (it.config.configured) AiPhase.Selecting else AiPhase.ConfigMissing) }
        loadConversations()
    }

    fun toggleConversation(id: String) = mutableState.update { state ->
        val selected = state.selectedIds.toMutableSet().apply { if (!add(id)) remove(id) }.toSet()
        state.copy(selectedIds = selected, prepared = AiPreparedPayload(emptyList(), "", 0, 0), phase = if (state.config.configured) AiPhase.Selecting else AiPhase.ConfigMissing)
    }

    fun loadConversations() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val state = mutableState.value
            runCatching {
                val settings = HistoryPreferences.snapshot(app)
                if (!settings.enabled) emptyList() else repository.conversationPage(cutoff(state.range), 100)
                    .filterNot { it.stableId in settings.excludedStableConversationIds }
            }.onSuccess { list -> mutableState.update { it.copy(conversations = list, selectedIds = it.selectedIds.intersect(list.mapTo(mutableSetOf()) { row -> row.stableId }), loading = false) } }
                .onFailure { mutableState.update { it.copy(loading = false, phase = AiPhase.Error("无法读取本地记录")) } }
        }
    }

    fun preview() {
        val state = mutableState.value
        if (state.selectedIds.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            runCatching {
                val all = state.selectedIds.flatMap { id -> repository.conversationMessages(id, cutoff(state.range), AI_MAX_MESSAGES) }
                AiPromptBuilder.prepare(all, state.selectedIds)
            }.onSuccess { payload ->
                mutableState.update { it.copy(prepared = payload, loading = false, phase = if (payload.messages.isEmpty()) AiPhase.Error("所选范围没有可发送消息") else AiPhase.Preview) }
            }.onFailure { mutableState.update { it.copy(loading = false, phase = AiPhase.Error("解密失败，密钥可能已失效")) } }
        }
    }

    fun requestConfirmation() { if (mutableState.value.prepared.messages.isNotEmpty()) mutableState.update { it.copy(phase = AiPhase.Confirming) } }
    fun dismissConfirmation() = mutableState.update { it.copy(phase = AiPhase.Preview) }

    fun generate() {
        val state = mutableState.value
        if (state.phase !is AiPhase.Confirming || !state.config.configured) return
        val key = secrets.get(state.config.providerId) ?: run { mutableState.update { it.copy(phase = AiPhase.ConfigMissing) }; return }
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(phase = AiPhase.Running) }
            try {
                val summary = client.complete(state.config, key, state.prepared.prompt)
                mutableState.update { it.copy(phase = AiPhase.Success(summary)) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                mutableState.update { it.copy(phase = AiPhase.Preview) }; throw cancelled
            } catch (error: Throwable) {
                val safe = (error as? AiClientException)?.safeMessage ?: "请求失败"
                mutableState.update { it.copy(phase = AiPhase.Error(safe)) }
            }
        }
    }

    fun quickTest(providerId: String?, modelEntryId: String?, draft: AiProviderEntity?, temporaryKey: CharArray) {
        if (requestJob?.isActive == true) { temporaryKey.fill('\u0000'); return }
        val ownedKey = temporaryKey.copyOf(); temporaryKey.fill('\u0000')
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(testStatus = "测试中…") }
            var key: CharArray? = null
            try {
                val config = withContext(Dispatchers.IO) {
                    if (draft != null) {
                        key = ownedKey
                        AiConfig(endpoint = AiEndpointResolver.resolve(draft.baseUrl, draft.appendV1, draft.apiMode), model = draft.modelId, hasApiKey = true, providerName = draft.displayName, apiMode = draft.apiMode)
                    } else {
                        val p = AiProviderStore.load(app, secrets).providers.firstOrNull { it.id == providerId } ?: error("Provider 不存在")
                        val model = p.models.firstOrNull { it.id == modelEntryId } ?: p.defaultModel ?: error("模型未配置")
                        key = secrets.get(p.id) ?: error("API Key 未配置")
                        AiConfig(p).copy(model = model.modelId, modelEntryId = model.id)
                    }
                }
                client.complete(config, requireNotNull(key), "ping")
                mutableState.update { it.copy(testStatus = "连接成功（响应正文未保存）") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                mutableState.update { it.copy(testStatus = "测试已取消") }; throw cancelled
            } catch (error: Throwable) {
                val safe = (error as? AiClientException)?.safeMessage ?: when { error.message?.contains("Key") == true -> "凭据未配置"; else -> "配置或网络错误" }
                mutableState.update { it.copy(testStatus = "连接失败：$safe") }
            } finally { key?.fill('\u0000'); ownedKey.fill('\u0000') }
        }
    }

    fun testConnection() {
        val state = mutableState.value
        if (!state.config.configured) return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(testStatus = "测试中…") }
            val key = withContext(Dispatchers.IO) { secrets.get(state.config.providerId) }
                ?: run { mutableState.update { it.copy(testStatus = "连接失败：未配置 Key") }; return@launch }
            try {
                client.complete(state.config, key, "这是连接测试，不含聊天内容。请只回复 OK。")
                mutableState.update { it.copy(testStatus = "连接成功（响应正文未保存）") }
            } catch (_: Throwable) { mutableState.update { it.copy(testStatus = "连接失败") } }
            finally { key.fill('\u0000') }
        }
    }

    fun cancel() { requestJob?.cancel(); requestJob = null }
    fun retry() { mutableState.update { it.copy(phase = if (it.prepared.messages.isEmpty()) AiPhase.Selecting else AiPhase.Preview) } }
    fun onLeave() = cancel()
    private fun cutoff(range: AiRange) = AiRangePolicy.cutoff(range, System.currentTimeMillis())
    override fun onCleared() { cancel(); super.onCleared() }
}
