package com.huimao.wechatnotifyevo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Beta5HotfixRegressionTest {
    @Test fun `rapid tab switching preserves non-empty invariant`() {
        var stack: List<AppRoute> = listOf(HomeRoute)
        repeat(1_000) { index ->
            stack = TopLevelNavigationPolicy.switchStack(stack, TopLevelTab.entries[index % TopLevelTab.entries.size])
            assertTrue(stack.isNotEmpty())
        }
        stack = TopLevelNavigationPolicy.navigateBack(emptyList())
        assertEquals(listOf<AppRoute>(HomeRoute), stack)
    }

    @Test fun `replace key projects hasApiKey and wipes submitted chars`() {
        val secrets = FakeSecretStore()
        val submitted = "sk-regression-secret".toCharArray()
        when (AiSecretPolicy.action(submitted, false)) {
            SecretUpdate.REPLACE -> secrets.put("provider", submitted)
            else -> error("expected replacement")
        }
        val provider = AiProvider(entity(), secrets.exists("provider"))
        assertTrue(provider.hasApiKey)
        assertTrue(submitted.all { it == '\u0000' })
    }

    @Test fun `provider failure exposed as safe state without secret or response`() {
        val unsafe = IllegalStateException("sk-live-secret: upstream body")
        val message = ProviderOperationPolicy.safeFailureMessage(unsafe)
        assertEquals("操作失败，请检查配置后重试", message)
        assertFalse(message.contains("sk-live-secret"))
        assertFalse(message.contains("upstream body"))
    }

    private fun entity() = AiProviderEntity(
        id = "provider", displayName = "test", baseUrl = "https://example.com",
        endpoint = "https://example.com/v1/chat/completions", modelId = "m",
        createdAt = 1, updatedAt = 1, sortOrder = 0,
    )

    private class FakeSecretStore : AiSecretStore {
        private val values = mutableMapOf<String, CharArray>()
        override fun put(providerId: String, secret: CharArray) {
            values[providerId] = secret.copyOf()
            secret.fill('\u0000')
        }
        override fun get(providerId: String) = values[providerId]?.copyOf()
        override fun clear(providerId: String) { values.remove(providerId)?.fill('\u0000') }
    }
}
