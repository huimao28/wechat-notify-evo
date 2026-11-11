package com.huimao.wechatnotifyevo

/** Pure Provider UI policies kept outside Compose/ViewModel for deterministic tests. */
object ProviderQuickTestPolicy {
    const val PROMPT = "ping"
}

object ProviderUiReducer {
    fun loaded(old: AiUiState, providerConfig: AiProviderConfig, message: String? = null): AiUiState {
        val selected = providerConfig.selected(old.selectedProviderId)
        val config = selected?.let(::AiConfig) ?: AiConfig()
        return old.copy(
            config = config,
            providers = providerConfig.providers,
            selectedProviderId = selected?.id,
            providerOperationRunning = false,
            providerOperationMessage = message,
            providerOperationError = false,
            phase = when {
                !config.configured -> AiPhase.ConfigMissing
                old.selectedIds.isEmpty() -> AiPhase.Selecting
                else -> old.phase
            },
        )
    }
}

object ProviderFallbackPolicy {
    fun providerAfterRemoval(providers: List<AiProviderEntity>, removedId: String): String? =
        providers.firstOrNull { it.id != removedId && it.enabled }?.id

    fun modelAfterRemoval(models: List<AiProviderModelEntry>, removedId: String): String? =
        models.firstOrNull { it.id != removedId && it.enabled }?.id
}
