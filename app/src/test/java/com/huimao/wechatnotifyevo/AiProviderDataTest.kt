package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class AiProviderDataTest {
 @Test fun resolverRecognizesFullEndpointsAndAppendV1() {
  assertEquals("https://api.openai.com/v1/responses",AiEndpointResolver.resolve("https://api.openai.com",true,AiApiMode.RESPONSES))
  assertEquals("https://api.deepseek.com/chat/completions",AiEndpointResolver.resolve("https://api.deepseek.com",false,AiApiMode.CHAT_COMPLETIONS))
  assertEquals("https://openrouter.ai/api/v1/chat/completions",AiEndpointResolver.resolve("https://openrouter.ai/api",true,AiApiMode.CHAT_COMPLETIONS))
  assertEquals("https://host/custom/responses",AiEndpointResolver.resolve("https://host/custom/responses",true,AiApiMode.CHAT_COMPLETIONS))
  assertEquals("https://host/v1/models",AiEndpointResolver.modelsEndpoint("https://host/v1/chat/completions",true))
 }
 @Test fun bothPayloadsAndResponsesParsers() {
  val chat=AiJson.request("m","sys","user")
  assertTrue(chat.contains("\"messages\""));assertFalse(chat.contains("stream"))
  val responses=AiJson.responsesRequest("m","sys","user")
  assertTrue(responses.contains("\"stream\":false"));assertTrue(responses.contains("\"instructions\""))
  assertEquals("hello",AiJson.responseContent("{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}"))
  assertEquals("direct",AiJson.responsesContent("{\"output_text\":\"direct\"}"))
  assertEquals("one\ntwo",AiJson.responsesContent("{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"one\"},{\"type\":\"output_text\",\"text\":\"two\"}]}]}"))
 }
 @Test fun modelListIsDistinctAndMigrationIsExplicitAndNonDestructive() {
  assertEquals(listOf("a","b"),AiJson.modelIds("{\"data\":[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"a\"}]}"))
  val sql=MigrationSql.V1_TO_V2.joinToString(";")
  assertTrue(sql.contains("ALTER TABLE ai_providers"));assertTrue(sql.contains("INSERT INTO ai_provider_models"));assertTrue(sql.contains("UPDATE ai_settings"))
  assertFalse(sql.contains("DROP TABLE",true));assertFalse(sql.contains("DELETE FROM",true))
 }
 @Test fun configurationSerializationContainsNoSecretMaterial() {
  val p=AiProviderEntity(id="stable",displayName="p",baseUrl="https://example.com",createdAt=1,updatedAt=1,sortOrder=0)
  val serialized=listOf(p,AiProviderModelEntry(providerId="stable",modelId="m"),AiSettingsEntity(defaultProviderId="stable")).joinToString()
  assertFalse(serialized.contains("apiKey",true));assertFalse(serialized.contains("secret",true));assertEquals("stable",p.id)
 }
}
