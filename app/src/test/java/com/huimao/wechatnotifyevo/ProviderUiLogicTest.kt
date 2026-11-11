package com.huimao.wechatnotifyevo

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class ProviderUiLogicTest {
    @Test fun typedProviderRoutesRoundTripAndStayInSettings() {
        val routes = listOf(
            ProviderEditorRoute(preset = "OPENAI"),
            ProviderEditorRoute(id = "provider-1"),
            ProviderModelsRoute("provider-1"),
            ProviderQuickTestRoute("provider-1", "model-1"),
            ProviderQuickTestRoute(),
        )
        routes.forEach { route ->
            assertEquals(route, AppRouteSavePolicy.decode(AppRouteSavePolicy.encode(route)))
            assertEquals(TopLevelTab.SETTINGS, TopLevelNavigationPolicy.tabFor(route))
        }
        assertEquals(listOf(SettingsRouteKey, ProviderEditorRoute(preset = "OPENAI")), TopLevelNavigationPolicy.openDetail(listOf(SettingsRouteKey), ProviderEditorRoute(preset = "OPENAI")))
    }

    @Test fun wizardDraftCancelsWithoutLeakingState() {
        val draft = ProviderEditorState().beginCreate().copy(name = "draft", endpoint = "https://draft.example/v1/chat/completions", model = "draft-model")
        assertTrue(draft.isVisible)
        assertEquals(ProviderEditorMode.Create, draft.mode)
        assertEquals(ProviderEditorState(), draft.cancel())
    }

    @Test fun refreshMergePreservesManualModels() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse.Builder().body("{\"data\":[{\"id\":\"remote\"},{\"id\":\"shared\"}]}").build())
            val provider = entity("provider", server.url("/").toString())
            val manual = AiProviderModelEntry(id = "manual-id", providerId = provider.id, modelId = "manual", origin = AiModelOrigin.USER)
            val shared = AiProviderModelEntry(id = "shared-id", providerId = provider.id, modelId = "shared", alias = "kept", origin = AiModelOrigin.USER)
            val client = AiModelsClient(OkHttpClient()) { Result.success(AiEndpoint(it, "localhost")) }
            val merged = RefreshProviderModelsUseCase(client).execute(provider, "key".toCharArray(), listOf(manual, shared))
            assertEquals(listOf("manual", "shared", "remote"), merged.map { it.modelId })
            assertEquals("shared-id", merged.single { it.modelId == "shared" }.id)
            assertEquals(AiModelOrigin.USER, merged.single { it.modelId == "shared" }.origin)
        } finally { server.close() }
    }

    @Test fun providerAndModelDeletionChooseEnabledFallbacks() {
        val providers = listOf(entity("removed"), entity("disabled").copy(enabled = false), entity("next"))
        assertEquals("next", ProviderFallbackPolicy.providerAfterRemoval(providers, "removed"))
        val models = listOf(
            AiProviderModelEntry(id = "removed", providerId = "next", modelId = "a"),
            AiProviderModelEntry(id = "disabled", providerId = "next", modelId = "b", enabled = false),
            AiProviderModelEntry(id = "next-model", providerId = "next", modelId = "c"),
        )
        assertEquals("next-model", ProviderFallbackPolicy.modelAfterRemoval(models, "removed"))
    }

    @Test fun uiReducerFallsBackSelectionAndClearsOperationState() {
        val fallback = AiProvider(entity("fallback"), hasApiKey = true, models = listOf(AiProviderModelEntry(id = "m", providerId = "fallback", modelId = "model")), defaultModelEntryId = "m")
        val old = AiUiState(selectedProviderId = "deleted", providerOperationRunning = true, providerOperationError = true)
        val reduced = ProviderUiReducer.loaded(old, AiProviderConfig(listOf(fallback), "fallback", "m"), "done")
        assertEquals("fallback", reduced.selectedProviderId)
        assertEquals("model", reduced.config.model)
        assertFalse(reduced.providerOperationRunning)
        assertFalse(reduced.providerOperationError)
        assertEquals("done", reduced.providerOperationMessage)
        assertEquals(AiPhase.Selecting, reduced.phase)
    }

    private fun entity(id: String, base: String = "https://example.com") = AiProviderEntity(
        id = id, displayName = id, baseUrl = base, createdAt = 1, updatedAt = 1, sortOrder = 0,
    )
}
