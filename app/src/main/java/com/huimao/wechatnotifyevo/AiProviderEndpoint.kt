/* SPDX-License-Identifier: GPL-3.0-only */
package com.huimao.wechatnotifyevo

import java.net.URI

enum class AiApiPathMode { BASE_URL, FULL_ENDPOINT }

data class ResolvedAiEndpoint(
    val baseUrl: String,
    val chatCompletionsEndpoint: String,
    val host: String,
    val appendV1Suffix: Boolean,
    val apiPathMode: AiApiPathMode,
)

/** Resolves an OpenAI-compatible Base URL without accepting URL-borne credentials or routing data. */
object AiProviderEndpointResolver {
    private const val CHAT_PATH = "/chat/completions"

    fun resolve(raw: String, appendV1Suffix: Boolean): Result<ResolvedAiEndpoint> = runCatching {
        require(raw.isNotBlank()) { "Base URL 不能为空" }
        val uri = URI(raw.trim())
        require(uri.scheme.equals("https", true)) { "Base URL 仅允许 HTTPS" }
        require(uri.rawUserInfo == null) { "Base URL 不能包含用户信息" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "Base URL 不能包含查询或片段" }
        require(!uri.host.isNullOrBlank()) { "Base URL 必须包含明确主机" }
        require(uri.port == -1 || uri.port == 443) { "Base URL 仅允许默认 HTTPS 端口" }
        require(uri.rawPath.orEmpty().split('/').none { it == "." || it == ".." }) { "Base URL 路径无效" }

        val origin = "https://${uri.host.lowercase()}"
        var path = uri.normalize().rawPath.orEmpty().trimEnd('/')
        if (path == "/") path = ""
        val fullEndpoint = path.endsWith(CHAT_PATH, ignoreCase = true)
        val normalizedPath = if (fullEndpoint) path else {
            val effective = if (appendV1Suffix && !path.endsWith("/v1", ignoreCase = true)) "$path/v1" else path
            "$effective$CHAT_PATH"
        }
        val endpoint = origin + normalizedPath
        val basePath = if (fullEndpoint) path.dropLast(CHAT_PATH.length) else path
        ResolvedAiEndpoint(
            baseUrl = origin + basePath,
            chatCompletionsEndpoint = endpoint,
            host = uri.host.lowercase(),
            appendV1Suffix = appendV1Suffix,
            apiPathMode = if (fullEndpoint) AiApiPathMode.FULL_ENDPOINT else AiApiPathMode.BASE_URL,
        )
    }

    fun migrateLegacyEndpoint(endpoint: String): ResolvedAiEndpoint =
        resolve(endpoint, appendV1Suffix = false).getOrThrow()
}

sealed class ProviderConfigException(message: String) : IllegalArgumentException(message) {
    class EmptyName : ProviderConfigException("名称不能为空")
    class InvalidBaseUrl : ProviderConfigException("Base URL 格式无效")
    class EmptyModel : ProviderConfigException("模型不能为空")
    class SecretUnavailable : ProviderConfigException("密钥存储不可用")
    class DatabaseSaveFailed : ProviderConfigException("数据库保存失败")
}
