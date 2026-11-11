package com.huimao.wechatnotifyevo

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class AiClientMockWebServerTest {
 private fun tlsServer(): Pair<MockWebServer,OkHttpClient> {
  val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
  val serverCerts=HandshakeCertificates.Builder().heldCertificate(cert).build()
  val clientCerts=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
  val server=MockWebServer().apply { useHttps(serverCerts.sslSocketFactory()); start() }
  val client=OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(),clientCerts.trustManager).build()
  return server to client
 }
 @Test fun chatAndResponsesRequestsAreComplete() = runBlocking {
  val (server,http)=tlsServer()
  try {
   server.enqueue(MockResponse.Builder().body("{\"choices\":[{\"message\":{\"content\":\"chat-ok\"}}]}").build())
   server.enqueue(MockResponse.Builder().body("{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"response-ok\"}]}]}").build())
   val client=OkHttpAiCompletionClient(http) { Result.success(AiEndpoint(it, "localhost")) }
   assertEquals("chat-ok",client.complete(AiConfig(endpoint=server.url("/v1/chat/completions").toString(),model="m",apiMode=AiApiMode.CHAT_COMPLETIONS),"key".toCharArray(),"hello"))
   val chat=server.takeRequest();assertEquals("POST",chat.method);assertTrue(chat.body!!.utf8().contains("\"messages\""));assertEquals("Bearer key",chat.headers["Authorization"])
   assertEquals("response-ok",client.complete(AiConfig(endpoint=server.url("/v1/responses").toString(),model="m",apiMode=AiApiMode.RESPONSES),"key".toCharArray(),"hello"))
   val responses=server.takeRequest();val body=responses.body!!.utf8();assertTrue(body.contains("\"stream\":false"));assertTrue(body.contains("\"input\":\"hello\""))
  } finally { server.close() }
 }
 @Test fun modelsGetParsesIds() = runBlocking {
  val (server,http)=tlsServer()
  try {
   server.enqueue(MockResponse.Builder().body("{\"data\":[{\"id\":\"m1\"},{\"id\":\"m2\"}]}").build())
   val provider=AiProviderEntity(displayName="p",baseUrl=server.url("/").toString(),createdAt=1,updatedAt=1,sortOrder=0,appendV1=true)
   assertEquals(listOf("m1","m2"),AiModelsClient(http) { Result.success(AiEndpoint(it, "localhost")) }.fetch(provider,"key".toCharArray()))
   val request=server.takeRequest();assertEquals("GET",request.method);assertEquals("/v1/models",request.url.encodedPath)
  } finally { server.close() }
 }
}
