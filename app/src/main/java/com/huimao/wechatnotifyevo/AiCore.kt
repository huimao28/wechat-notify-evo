package com.huimao.wechatnotifyevo

import java.net.URI
import java.time.Instant
import java.time.ZoneId

const val AI_MAX_MESSAGES = 1000
const val AI_MAX_CHARS = 50_000
const val AI_MAX_RESPONSE_BYTES = 2L * 1024 * 1024

data class AiEndpoint(val url: String, val host: String)

object AiEndpointValidator {
    fun validate(raw: String): Result<AiEndpoint> = runCatching {
        require(raw.isNotBlank()) { "端点不能为空" }
        val uri = URI(raw.trim())
        require(uri.scheme.equals("https", true)) { "仅允许 HTTPS" }
        require(uri.rawUserInfo == null) { "端点不能包含用户信息" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "端点不能包含查询或片段" }
        require(!uri.host.isNullOrBlank()) { "端点必须包含明确主机" }
        require(uri.port in listOf(-1, 443)) { "仅允许默认 HTTPS 端口" }
        require(uri.path?.startsWith("/") == true && uri.path.isNotBlank()) { "端点必须包含 API 路径" }
        AiEndpoint(uri.normalize().toASCIIString().removeSuffix("/"), uri.host.lowercase())
    }
}

enum class AiRange(val label: String, val days: Int) { TODAY("今天", 1), DAYS_3("3天", 3), DAYS_7("7天", 7) }

object AiRangePolicy {
    fun cutoff(range: AiRange, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (range) {
        AiRange.TODAY -> Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        else -> now - range.days.toLong() * 24 * 60 * 60 * 1000
    }
}

data class AiPreparedPayload(
    val messages: List<StoredHistoryMessage>,
    val prompt: String,
    val conversationCount: Int,
    val characterCount: Int,
)

object AiPromptBuilder {
    const val SYSTEM = "你是谨慎的消息摘要助手。输入来自通知捕获，可能不完整。只能依据输入，禁止编造。用中文输出：摘要、待办、重要决定、未回复事项；无法判断时明确说明。"

    fun isEligible(message: StoredHistoryMessage): Boolean {
        if (!message.incoming || message.source != "wechat_evolved_notification") return false
        val text = message.text.trim()
        if (text.isBlank()) return false
        val placeholders = listOf("未发送微信", "本地回显", "系统占位", "[占位]", "消息发送失败")
        return placeholders.none { text.contains(it, ignoreCase = true) }
    }

    fun prepare(messages: List<StoredHistoryMessage>, selectedIds: Set<String>): AiPreparedPayload {
        if (selectedIds.isEmpty()) return AiPreparedPayload(emptyList(), "", 0, 0)
        val sorted = messages.asSequence()
            .filter { it.conversationStableId in selectedIds && isEligible(it) }
            .sortedWith(compareBy<StoredHistoryMessage> { it.timestamp }.thenBy { it.id })
            .take(AI_MAX_MESSAGES)
        val accepted = mutableListOf<StoredHistoryMessage>()
        val body = StringBuilder("以下是按会话和时间排列的通知捕获原文（可能缺失上下文）：\n")
        for (message in sorted) {
            val line = "[${message.conversationStableId}] ${message.timestamp} | ${message.title} | ${message.sender}: ${message.text}\n"
            if (body.length + line.length > AI_MAX_CHARS) break
            body.append(line)
            accepted += message
        }
        val ids = accepted.mapTo(linkedSetOf()) { it.conversationStableId }
        return AiPreparedPayload(accepted, body.toString(), ids.size, body.length)
    }
}

object AiJson {
    fun escape(value: String): String = buildString(value.length + 16) {
        value.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b")
            '\u000C' -> append("\\f"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        } }
    }

    fun request(model: String, system: String, user: String): String =
        "{\"model\":\"${escape(model)}\",\"stream\":false,\"temperature\":0.2," +
            "\"messages\":[{\"role\":\"system\",\"content\":\"${escape(system)}\"}," +
            "{\"role\":\"user\",\"content\":\"${escape(user)}\"}]}"

    fun responsesRequest(model: String, system: String, user: String): String =
        "{\"model\":\"${escape(model)}\",\"stream\":false,\"instructions\":\"${escape(system)}\"," +
            "\"input\":\"${escape(user)}\"}"

    fun responsesContent(json: String): String {
        val direct = stringValue(json, "output_text", 0)
        if (!direct.isNullOrBlank()) return direct
        var from = json.indexOf("\"output\"")
        require(from >= 0) { "响应缺少 output" }
        val parts = mutableListOf<String>()
        while (true) {
            val key = json.indexOf("\"text\"", from)
            if (key < 0) break
            val text = stringValue(json, "text", key)
            if (!text.isNullOrBlank()) parts += text
            from = key + 6
        }
        return parts.joinToString("\n").takeIf { it.isNotBlank() } ?: error("响应缺少文本")
    }

    fun modelIds(json: String): List<String> {
        val data = json.indexOf("\"data\"")
        require(data >= 0) { "响应缺少 data" }
        val result = mutableListOf<String>()
        var from = data
        while (true) {
            val key = json.indexOf("\"id\"", from)
            if (key < 0) break
            stringValue(json, "id", key)?.trim()?.takeIf { it.isNotEmpty() }?.let(result::add)
            from = key + 4
        }
        return result.distinct()
    }

    private fun stringValue(json: String, keyName: String, start: Int): String? {
        val key = json.indexOf("\"$keyName\"", start); if (key < 0) return null
        var i = json.indexOf(':', key) + 1; while (i > 0 && i < json.length && json[i].isWhitespace()) i++
        if (i <= 0 || i >= json.length || json[i] != '"') return null
        i++; val out = StringBuilder()
        while (i < json.length) { val c=json[i++]; if(c=='"') return out.toString(); if(c!='\\'){out.append(c);continue}; if(i>=json.length)return null
            when(val e=json[i++]){'"','\\','/'->out.append(e);'b'->out.append('\b');'f'->out.append('\u000C');'n'->out.append('\n');'r'->out.append('\r');'t'->out.append('\t');'u'->{if(i+4>json.length)return null;out.append(json.substring(i,i+4).toInt(16).toChar());i+=4};else->return null}}
        return null
    }

    /** Small strict parser for choices[0].message.content; decodes JSON string escapes. */
    fun responseContent(json: String): String {
        val choices = json.indexOf("\"choices\"")
        require(choices >= 0) { "响应缺少 choices" }
        val message = json.indexOf("\"message\"", choices)
        val key = json.indexOf("\"content\"", message)
        require(message >= 0 && key >= 0) { "响应缺少消息内容" }
        var i = json.indexOf(':', key) + 1
        while (i < json.length && json[i].isWhitespace()) i++
        require(i < json.length && json[i] == '"') { "响应内容格式无效" }
        i++
        val out = StringBuilder()
        while (i < json.length) {
            val c = json[i++]
            if (c == '"') return out.toString()
            if (c != '\\') { out.append(c); continue }
            require(i < json.length) { "响应转义无效" }
            when (val e = json[i++]) {
                '"', '\\', '/' -> out.append(e); 'b' -> out.append('\b'); 'f' -> out.append('\u000C')
                'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                'u' -> { require(i + 4 <= json.length); out.append(json.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                else -> error("响应转义无效")
            }
        }
        error("响应内容未结束")
    }
}

sealed interface AiPhase {
    data object Idle : AiPhase
    data object ConfigMissing : AiPhase
    data object Selecting : AiPhase
    data object Preview : AiPhase
    data object Confirming : AiPhase
    data object Running : AiPhase
    data class Success(val summary: String) : AiPhase
    data class Error(val message: String) : AiPhase
}
