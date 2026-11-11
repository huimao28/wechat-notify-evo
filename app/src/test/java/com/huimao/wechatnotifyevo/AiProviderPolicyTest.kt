package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class AiProviderPolicyTest {
    @Test fun stableLegacyUuid() {
        assertEquals("fc01b78b-4bed-3813-bd76-c93a086c0c94", AiProviderIds.LEGACY)
    }

    @Test fun secretDraftPolicy() {
        assertEquals(SecretUpdate.KEEP, AiSecretPolicy.action(null, false))
        assertEquals(SecretUpdate.KEEP, AiSecretPolicy.action(charArrayOf(), false))
        assertEquals(SecretUpdate.REPLACE, AiSecretPolicy.action("new".toCharArray(), false))
        assertEquals(SecretUpdate.CLEAR, AiSecretPolicy.action(null, true))
    }

    @Test fun officialPresetsAreExactAndHttps() {
        assertEquals("https://api.openai.com/v1/chat/completions", AiProviderEndpointResolver.resolve(AiProviderPreset.OPENAI.baseUrl, false).getOrThrow().chatCompletionsEndpoint)
        assertEquals("https://api.deepseek.com/chat/completions", AiProviderEndpointResolver.resolve(AiProviderPreset.DEEPSEEK.baseUrl, false).getOrThrow().chatCompletionsEndpoint)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", AiProviderEndpointResolver.resolve(AiProviderPreset.OPENROUTER.baseUrl, false).getOrThrow().chatCompletionsEndpoint)
        AiProviderPreset.entries.filter { it != AiProviderPreset.CUSTOM }
            .forEach { assertTrue(AiProviderEndpointResolver.resolve(it.baseUrl,it.appendV1Suffix).isSuccess) }
    }

    @Test fun configProjectionContainsNoSecret() {
        val e = entity("id")
        val text = AiConfig(AiProvider(e, true)).toString()
        assertFalse(text.contains("sk-test"))
        assertFalse(text.contains("secret", true))
    }

    @Test fun selectionUsesEnabledDefaultOrExplicit() {
        val config = AiProviderConfig(listOf(AiProvider(entity("a"), true), AiProvider(entity("b"), true)), "a")
        assertEquals("b", config.selected("b")?.id)
        assertEquals("a", config.selected("missing")?.id)
    }

    private fun entity(id: String) = AiProviderEntity(
        id = id, displayName = id, baseUrl = "https://example.com",
        endpoint = "https://example.com/v1/chat/completions", modelId = "m",
        createdAt = 1L, updatedAt = 1L, sortOrder = 0,
    )
}
