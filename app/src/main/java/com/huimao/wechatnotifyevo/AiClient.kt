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

class AiClientException(val safeMessage: String) : IOException(safeMessage)

interface AiCompletionClient {
    suspend fun complete(config: AiConfig, apiKey: CharArray, userPrompt: String): String
}

class OkHttpAiCompletionClient(
    private val client: OkHttpClient = secureClient(),
) : AiCompletionClient {
    override suspend fun complete(config: AiConfig, apiKey: CharArray, userPrompt: String): String {
        require(userPrompt.length <= AI_MAX_CHARS) { "请求内容超过 50000 字符" }
        val endpoint = AiEndpointValidator.validate(config.endpoint).getOrElse { throw AiClientException("端点配置无效") }
        require(config.model.isNotBlank()) { "模型未配置" }
        val keyString = apiKey.concatToString()
        val json = AiJson.request(config.model, AiPromptBuilder.SYSTEM, userPrompt)
        val request = try {
            Request.Builder().url(endpoint.url)
                .header("Authorization", "Bearer $keyString")
                .header("Accept", "application/json")
                .post(json.toRequestBody(JSON)).build()
        } finally { apiKey.fill('\u0000') }
        val response = try { client.newCall(request).await() }
            catch (e: AiClientException) { throw e }
            catch (_: IOException) { throw AiClientException("网络请求失败或超时") }
        return response.use { readResponse(it) }
    }

    private fun readResponse(response: Response): String {
        if (!response.isSuccessful) throw AiClientException("服务返回 HTTP ${response.code}")
        val body = response.body
        if (body.contentLength() > AI_MAX_RESPONSE_BYTES) throw AiClientException("响应超过 2MB 限制")
        val source = body.source()
        val bytes = source.readByteArray(AI_MAX_RESPONSE_BYTES + 1)
        if (bytes.size > AI_MAX_RESPONSE_BYTES) throw AiClientException("响应超过 2MB 限制")
        val content = bytes.toString(Charsets.UTF_8)
        return runCatching { AiJson.responseContent(content) }
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
