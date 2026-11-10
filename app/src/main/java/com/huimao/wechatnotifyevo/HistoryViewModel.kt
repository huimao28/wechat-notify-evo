package com.huimao.wechatnotifyevo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val repository = AndroidHistoryRepository.get(app)
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { HistoryEvents.changes.collectLatest { refresh() } }
    }

    fun onResume() = refresh()

    fun refresh() = viewModelScope.launch {
        val current = mutableState.value
        mutableState.update { it.copy(loading = true, error = null) }
        runCatching {
            val settings = HistoryPreferences.snapshot(app)
            val stats = repository.stats()
            val page = if (settings.enabled) repository.conversationPage(cutoff(current.range), HISTORY_PAGE_SIZE) else emptyList()
            Triple(settings, stats, page)
        }.onSuccess { (settings, stats, page) ->
            mutableState.update {
                it.copy(settings = settings, stats = stats, conversations = page,
                    hasMoreConversations = page.size == HISTORY_PAGE_SIZE, loading = false, error = null)
            }
        }.onFailure(::showError)
    }

    fun setRange(range: HistoryRange) {
        mutableState.update { it.copy(range = range, conversations = emptyList()) }
        refresh()
    }

    fun loadMoreConversations() = viewModelScope.launch {
        val current = mutableState.value
        if (current.loading || !current.hasMoreConversations) return@launch
        mutableState.update { it.copy(loading = true) }
        runCatching { repository.conversationPage(cutoff(current.range), HISTORY_PAGE_SIZE, current.conversations.size) }
            .onSuccess { page -> mutableState.update { it.copy(conversations = it.conversations + page, hasMoreConversations = page.size == HISTORY_PAGE_SIZE, loading = false) } }
            .onFailure(::showError)
    }

    fun openConversation(stableId: String) {
        mutableState.update { it.copy(detailStableId = stableId, detailMessages = emptyList(), hasMoreMessages = true) }
        loadMoreMessages()
    }

    fun closeConversation() = mutableState.update { it.copy(detailStableId = null, detailMessages = emptyList(), hasMoreMessages = false) }

    fun loadMoreMessages() = viewModelScope.launch {
        val current = mutableState.value
        val id = current.detailStableId ?: return@launch
        if (current.loading || !current.hasMoreMessages) return@launch
        mutableState.update { it.copy(loading = true, error = null) }
        runCatching { repository.conversationMessages(id, cutoff(current.range), HISTORY_PAGE_SIZE, current.detailMessages.size) }
            .onSuccess { page -> mutableState.update { it.copy(detailMessages = it.detailMessages + page, hasMoreMessages = page.size == HISTORY_PAGE_SIZE, loading = false) } }
            .onFailure(::showError)
    }

    fun setRecordingEnabled(enabled: Boolean) = mutate {
        HistoryPreferences.setEnabled(app, enabled)
    }

    fun setRetention(retention: Retention) = mutate {
        HistoryPreferences.setRetention(app, retention)
        HistoryPolicy.cutoff(System.currentTimeMillis(), retention)?.let { repository.trimBefore(it) }
    }

    fun saveExcluded(ids: Set<String>, deleteExisting: Boolean) = mutate {
        HistoryPreferences.setExcluded(app, ids)
        if (deleteExisting) ids.forEach { repository.deleteConversation(it) }
    }

    fun deleteConversation(stableId: String) = mutate {
        repository.deleteConversation(stableId)
        if (mutableState.value.detailStableId == stableId) closeConversation()
    }

    fun clearAll() = mutate {
        repository.clear()
        closeConversation()
    }

    private fun mutate(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { HistoryEvents.notifyChanged(); refresh() }
            .onFailure(::showError)
    }

    private fun cutoff(range: HistoryRange) = HistoryRangePolicy.cutoff(range, System.currentTimeMillis())
    private fun showError(error: Throwable) = mutableState.update { it.copy(loading = false, error = HistoryUiStatePolicy.mapError(error)) }
}
