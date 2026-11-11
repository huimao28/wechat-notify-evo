package com.huimao.wechatnotifyevo

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource

class AiClientException(val safeMessage: String) : IOException(safeMessage)

interface AiCompletionClient {
    suspend fun complete(config: AiConfig, apiKey: CharArray, userPrompt: String): String
}

class AiModelsClient(private val client: OkHttpClient = OkHttpAiCompletionClient.secureClient(), private val validate: (String) -> Result<AiEndpoint> = AiEndpointValidator::validate) {
    suspend fun fetch(provider: AiProviderEntity, apiKey: CharArray): List<String> {
        val url = validate(AiEndpointResolver.modelsEndpoint(provider.baseUrl.ifBlank { provider.endpoint }, provider.appendV1)).getOrElse { throw AiClientException("端点配置无效") }
        val key = apiKey.concatToString()
        val request = try { Request.Builder().url(url.url).header("Authorization", "Bearer $key").header("Accept", "application/json").get().build() } finally { apiKey.fill('\u0000') }
        val response = try { client.newCall(request).await() } catch (_: IOException) { throw AiClientException("网络请求失败或超时") }
        return response.use {
            if (!it.isSuccessful) throw AiClientException("服务返回 HTTP ${it.code}")
            val body = it.body.source().readUpTo(AI_MAX_RESPONSE_BYTES + 1)
            if (body.size > AI_MAX_RESPONSE_BYTES) throw AiClientException("响应超过 2MB 限制")
            runCatching { AiJson.modelIds(body.toString(Charsets.UTF_8)) }.getOrElse { throw AiClientException("模型列表格式无效") }
        }
    }
}

class RefreshProviderModelsUseCase(private val client: AiModelsClient = AiModelsClient()) {
    suspend fun execute(provider: AiProviderEntity, key: CharArray, existing: List<AiProviderModelEntry>): List<AiProviderModelEntry> {
        val ids = client.fetch(provider, key)
        val byModel = existing.associateBy { it.modelId }
        val remote = ids.mapIndexed { index, id -> byModel[id] ?: AiProviderModelEntry(providerId=provider.id, modelId=id, origin=AiModelOrigin.REMOTE, sortOrder=index) }
        return (existing.filter { it.origin == AiModelOrigin.USER } + remote).distinctBy { it.modelId }
    }
}

class OkHttpAiCompletionClient(
    private val client: OkHttpClient = secureClient(),
    private val validate: (String) -> Result<AiEndpoint> = AiEndpointValidator::validate,
) : AiCompletionClient {
    override suspend fun complete(config: AiConfig, apiKey: CharArray, userPrompt: String): String {
        require(userPrompt.length <= AI_MAX_CHARS) { "请求内容超过 50000 字符" }
        val endpoint = validate(config.endpoint).getOrElse { throw AiClientException("端点配置无效") }
        require(config.model.isNotBlank()) { "模型未配置" }
        val keyString = apiKey.concatToString()
        val json = when (config.apiMode) {
            AiApiMode.CHAT_COMPLETIONS -> AiJson.request(config.model, AiPromptBuilder.SYSTEM, userPrompt)
            AiApiMode.RESPONSES -> AiJson.responsesRequest(config.model, AiPromptBuilder.SYSTEM, userPrompt)
        }
        val request = try {
            Request.Builder().url(endpoint.url)
                .header("Authorization", "Bearer $keyString")
                .header("Accept", "application/json")
                .post(json.toRequestBody(JSON)).build()
        } finally { apiKey.fill('\u0000') }
        val response = try { client.newCall(request).await() }
            catch (e: AiClientException) { throw e }
            catch (_: IOException) { throw AiClientException("网络请求失败或超时") }
        return response.use { readResponse(it, config.apiMode) }
    }

    private fun readResponse(response: Response, mode: AiApiMode): String {
        if (!response.isSuccessful) throw AiClientException("服务返回 HTTP ${response.code}")
        val body = response.body
        if (body.contentLength() > AI_MAX_RESPONSE_BYTES) throw AiClientException("响应超过 2MB 限制")
        val source = body.source()
        val bytes = source.readUpTo(AI_MAX_RESPONSE_BYTES + 1)
        if (bytes.size > AI_MAX_RESPONSE_BYTES) throw AiClientException("响应超过 2MB 限制")
        val content = bytes.toString(Charsets.UTF_8)
        return runCatching { if (mode == AiApiMode.RESPONSES) AiJson.responsesContent(content) else AiJson.responseContent(content) }
            .getOrElse { throw AiClientException("服务响应格式无效") }
            .takeIf { it.isNotBlank() } ?: throw AiClientException("服务返回空总结")
    }

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        fun secureClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

private fun BufferedSource.readUpTo(limit: Long): ByteArray {
    val buffer = Buffer()
    while (buffer.size < limit) {
        if (read(buffer, limit - buffer.size) == -1L) break
    }
    return buffer.readByteArray()
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
