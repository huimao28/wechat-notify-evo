package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class AiCoreTest {
    @Test fun endpointRequiresStrictHttpsWithoutCredentialsOrQuery() {
        assertTrue(AiEndpointValidator.validate("https://api.example.com/v1/chat/completions").isSuccess)
        listOf(
            "http://api.example.com/v1/chat/completions",
            "https://user:pass@api.example.com/v1/chat/completions",
            "https://api.example.com/v1/chat/completions?q=x",
            "https://api.example.com/v1/chat/completions#x",
            "https:///v1/chat/completions",
            "https://api.example.com:8443/v1/chat/completions",
        ).forEach { assertTrue(it, AiEndpointValidator.validate(it).isFailure) }
    }

    @Test fun selectionDefaultsEmptyAndSortsFiltersAndCaps() {
        assertTrue(AiPromptBuilder.prepare(sampleMessages(3), emptySet()).messages.isEmpty())
        val prepared = AiPromptBuilder.prepare(sampleMessages(1100).reversed(), setOf("a"))
        assertEquals(AI_MAX_MESSAGES, prepared.messages.size)
        assertTrue(prepared.messages.zipWithNext().all { it.first.timestamp <= it.second.timestamp })
        assertTrue(prepared.characterCount <= AI_MAX_CHARS)
    }

    @Test fun promptExcludesOutgoingPlaceholdersAndWrongSource() {
        val excluded = listOf(
            message(1, "未发送微信", incoming = true),
            message(2, "本地回显 hello", incoming = true),
            message(3, "secret outgoing", incoming = false),
            message(4, "wrong source", incoming = true, source = "other"),
        )
        val result = AiPromptBuilder.prepare(excluded + message(5, "保留内容"), setOf("a"))
        assertEquals(1, result.messages.size)
        assertTrue(result.prompt.contains("保留内容"))
        assertFalse(result.prompt.contains("secret outgoing"))
        assertFalse(result.prompt.contains("未发送微信"))
    }

    @Test fun jsonEscapesAndParsesContent() {
        val request = AiJson.request("m\"x", "sys\n", "中文\\\n")
        assertTrue(request.contains("m\\\"x"))
        assertTrue(request.contains("sys\\n"))
        assertEquals("第一行\n第二行 \"ok\"", AiJson.responseContent("""{"choices":[{"message":{"content":"第一行\n第二行 \"ok\""}}]}"""))
    }

    @Test fun responseRejectsMissingChoice() {
        assertThrows(IllegalArgumentException::class.java) { AiJson.responseContent("{}") }
    }

    @Test fun fakeSecretStoreNeverNeedsPersistence() {
        val fake = FakeSecretStore()
        val input = "sk-test".toCharArray()
        fake.put(input)
        assertTrue(input.all { it == '\u0000' })
        assertArrayEquals("sk-test".toCharArray(), fake.get())
        fake.clear(); assertFalse(fake.exists())
    }

    private fun sampleMessages(count: Int) = (0 until count).map { message(it.toLong(), "消息$it") }
    private fun message(id: Long, text: String, incoming: Boolean = true, source: String = "wechat_evolved_notification") = StoredHistoryMessage(
        id, "a", "会话", "发送者", text, id, "CHAT", incoming, source,
    )

    private class FakeSecretStore : AiSecretStore {
        private var secret: CharArray? = null
        override fun put(providerId: String, secret: CharArray) { this.secret = secret.copyOf(); secret.fill('\u0000') }
        override fun get(providerId: String) = secret?.copyOf()
        override fun clear(providerId: String) { secret?.fill('\u0000'); secret = null }
    }
}
