package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class AiProviderBaseUrlTest {
    private fun resolved(value:String, append:Boolean=true)=AiProviderEndpointResolver.resolve(value,append).getOrThrow()

    @Test fun `host only appends v1 and chat path`() = assertEquals(
        "https://cdn.pigcode.org/v1/chat/completions", resolved("https://cdn.pigcode.org").chatCompletionsEndpoint)
    @Test fun `trailing slash is normalized`() = assertEquals(
        "https://example.com/v1/chat/completions", resolved("https://example.com/").chatCompletionsEndpoint)
    @Test fun `v1 is never duplicated`() = assertEquals(
        "https://example.com/v1/chat/completions", resolved("https://example.com/v1").chatCompletionsEndpoint)
    @Test fun `full endpoint remains unchanged`() {
        val result=resolved("https://example.com/custom/chat/completions")
        assertEquals("https://example.com/custom/chat/completions",result.chatCompletionsEndpoint)
        assertEquals(AiApiPathMode.FULL_ENDPOINT,result.apiPathMode)
    }
    @Test fun `presets resolve exactly once`() {
        assertEquals("https://api.openai.com/v1/chat/completions",resolved(AiProviderPreset.OPENAI.baseUrl,AiProviderPreset.OPENAI.appendV1Suffix).chatCompletionsEndpoint)
        assertEquals("https://api.deepseek.com/v1/chat/completions",resolved(AiProviderPreset.DEEPSEEK.baseUrl,AiProviderPreset.DEEPSEEK.appendV1Suffix).chatCompletionsEndpoint)
        assertEquals("https://openrouter.ai/api/v1/chat/completions",resolved(AiProviderPreset.OPENROUTER.baseUrl,AiProviderPreset.OPENROUTER.appendV1Suffix).chatCompletionsEndpoint)
    }
    @Test fun `unsafe URL components are rejected`() {
        listOf("https://example.com:8443","https://example.com/v1?q=x","https://u:p@example.com","http://example.com").forEach {
            assertTrue(it,AiProviderEndpointResolver.resolve(it,true).isFailure)
        }
    }
    @Test fun `legacy migration preserves endpoint`() = assertEquals(
        "https://legacy.example/custom/chat/completions",
        AiProviderEndpointResolver.migrateLegacyEndpoint("https://legacy.example/custom/chat/completions").chatCompletionsEndpoint)
    @Test fun `classified errors never leak exception text`() {
        val secret="sk-secret upstream body"
        val cases=listOf(
            ProviderConfigException.EmptyName() to "名称不能为空",
            ProviderConfigException.InvalidBaseUrl() to "Base URL 格式无效",
            ProviderConfigException.EmptyModel() to "模型不能为空",
            ProviderConfigException.SecretUnavailable() to "密钥存储不可用",
            ProviderConfigException.DatabaseSaveFailed() to "数据库保存失败",
            IllegalStateException(secret) to "操作失败，请检查配置后重试",
        )
        cases.forEach { (error,expected) ->
            val message=ProviderOperationPolicy.safeFailureMessage(error)
            assertEquals(expected,message); assertFalse(message.contains(secret))
        }
    }
}
