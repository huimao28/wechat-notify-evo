package com.huimao.wechatnotifyevo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun reloadConfig() {
        val providerConfig = AiProviderStore.load(app, secrets)
        val selected = providerConfig.selected(mutableState.value.selectedProviderId)
        val config = selected?.let(::AiConfig) ?: AiConfig()
        mutableState.update { old ->
            old.copy(config = config, providers = providerConfig.providers, selectedProviderId = selected?.id, phase = when {
                !config.configured -> AiPhase.ConfigMissing
                old.selectedIds.isEmpty() -> AiPhase.Selecting
                else -> old.phase
            })
        }
    }

    fun saveConfig(endpoint: String, model: String, apiKey: CharArray) {
        val optional = apiKey.takeIf { it.isNotEmpty() }
        AiConfigStore.save(app, endpoint, model, optional, secrets)
            .onSuccess { config -> mutableState.update { it.copy(config = config, phase = if (it.selectedIds.isEmpty()) AiPhase.Selecting else it.phase, testStatus = "配置已保存") } }
            .onFailure { error -> mutableState.update { it.copy(testStatus = error.message ?: "配置无效") } }
    }


    fun selectProvider(id: String) {
        val p = AiProviderStore.load(app, secrets).selected(id) ?: return
        mutableState.update { it.copy(config = AiConfig(p), selectedProviderId = p.id, phase = if (it.selectedIds.isEmpty()) AiPhase.Selecting else it.phase) }
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

    fun testConnection() {
        val state = mutableState.value
        if (!state.config.configured) return
        val key = secrets.get(state.config.providerId) ?: return reloadConfig()
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(testStatus = "测试中…") }
            try {
                client.complete(state.config, key, "这是连接测试，不含聊天内容。请只回复 OK。")
                mutableState.update { it.copy(testStatus = "连接成功（响应正文未保存）") }
            } catch (_: Throwable) { mutableState.update { it.copy(testStatus = "连接失败") } }
        }
    }

    fun cancel() { requestJob?.cancel(); requestJob = null }
    fun retry() { mutableState.update { it.copy(phase = if (it.prepared.messages.isEmpty()) AiPhase.Selecting else AiPhase.Preview) } }
    fun onLeave() = cancel()
    private fun cutoff(range: AiRange) = AiRangePolicy.cutoff(range, System.currentTimeMillis())
    override fun onCleared() { cancel(); super.onCleared() }
}
