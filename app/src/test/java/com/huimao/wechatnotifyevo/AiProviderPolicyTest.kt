package com.huimao.wechatnotifyevo

import org.junit.Assert.*
import org.junit.Test

class AiProviderPolicyTest {
 @Test fun stableLegacyUuid(){assertEquals("dbef7c28-c8cc-3ca8-a093-83c86c9c8283",AiProviderIds.LEGACY);assertEquals(AiProviderIds.LEGACY,AiProviderIds.LEGACY)}
 @Test fun secretDraftPolicy(){assertEquals(SecretUpdate.KEEP,AiSecretPolicy.action(null,false));assertEquals(SecretUpdate.KEEP,AiSecretPolicy.action(charArrayOf(),false));assertEquals(SecretUpdate.REPLACE,AiSecretPolicy.action("new".toCharArray(),false));assertEquals(SecretUpdate.CLEAR,AiSecretPolicy.action(null,true))}
 @Test fun officialPresetsAreExactAndHttps(){assertEquals("https://api.openai.com/v1/chat/completions",AiProviderPreset.OPENAI.endpoint);assertEquals("https://api.deepseek.com/chat/completions",AiProviderPreset.DEEPSEEK.endpoint);assertEquals("https://openrouter.ai/api/v1/chat/completions",AiProviderPreset.OPENROUTER.endpoint);AiProviderPreset.entries.filter{it!=AiProviderPreset.CUSTOM}.forEach{assertTrue(AiEndpointValidator.validate(it.endpoint).isSuccess)}}
 @Test fun configProjectionContainsNoSecret(){val e=AiProviderEntity("id","p",baseUrl="https://example.com",endpoint="https://example.com/v1/chat/completions",modelId="m",createdAt=1,updatedAt=1,sortOrder=0);val text=AiConfig(AiProvider(e,true)).toString();assertFalse(text.contains("apiKey",true));assertFalse(text.contains("secret",true))}
 @Test fun selectionUsesEnabledDefaultOrExplicit(){val now=1L;fun p(id:String,en:Boolean)=AiProvider(AiProviderEntity(id,id,baseUrl="https://example.com",endpoint="https://example.com/v1/chat/completions",modelId="m",enabled=en,createdAt=now,updatedAt=now,sortOrder=0),true);val c=AiProviderConfig(listOf(p("a",true),p("b",true)),"a");assertEquals("b",c.selected("b")?.id);assertEquals("a",c.selected("missing")?.id)}
}
