/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2025-2026 OpenMinis contributors
 * Copyright (C) 2026 WeChat Notify Evo contributors
 * Source: OpenMinis ProviderRepository.kt and EncryptedPrefsFactory.kt.
 * Modified 2026-11-10: WNE Room repository and per-provider AES-GCM credentials.
 */
package com.huimao.wechatnotifyevo
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class SecretUpdate{KEEP,REPLACE,CLEAR}
object ProviderOperationPolicy{fun safeFailureMessage(@Suppress("UNUSED_PARAMETER")e:Throwable)="操作失败，请检查配置后重试"}
object AiSecretPolicy{fun action(v:CharArray?,clear:Boolean)=when{clear->SecretUpdate.CLEAR;v.isNullOrEmpty()->SecretUpdate.KEEP;else->SecretUpdate.REPLACE}}
interface AiSecretStore{fun put(providerId:String,secret:CharArray);fun get(providerId:String):CharArray?;fun clear(providerId:String);fun exists(providerId:String):Boolean{val s=get(providerId)?:return false;s.fill('\u0000');return true};fun put(secret:CharArray)=put(LEGACY_KEY,secret);fun get()=get(LEGACY_KEY);fun clear()=clear(LEGACY_KEY);fun exists()=exists(LEGACY_KEY);companion object{const val LEGACY_KEY="legacy"}}
class AndroidAiSecretStore(context:Context):AiSecretStore{
 private val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 override fun put(providerId:String,secret:CharArray){require(secret.isNotEmpty());val c=Cipher.getInstance(X);c.init(Cipher.ENCRYPT_MODE,key());val p=secret.concatToString().toByteArray();try{check(prefs.edit().putString(blob(providerId),Base64.encodeToString(c.iv+c.doFinal(p),Base64.NO_WRAP)).commit())}finally{p.fill(0);secret.fill('\u0000')}}
 override fun get(providerId:String):CharArray?{val e=prefs.getString(blob(providerId),null)?:return null;return try{val a=Base64.decode(e,Base64.NO_WRAP);require(a.size>12);val c=Cipher.getInstance(X);c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,a.copyOfRange(0,12)));val p=c.doFinal(a.copyOfRange(12,a.size));try{p.toString(Charsets.UTF_8).toCharArray()}finally{p.fill(0);a.fill(0)}}catch(_:Throwable){null}}
 override fun clear(providerId:String){prefs.edit().remove(blob(providerId)).commit()};private fun blob(id:String)=if(id==AiSecretStore.LEGACY_KEY)"api_key_aes_gcm" else "provider_$id"
 private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(ks.getKey(ALIAS,null)as?SecretKey)?.let{return it};return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())}.generateKey()}
 companion object{const val PREFS="ai_secret_ciphertext_v1";const val ALIAS="wne_ai_api_key_v1";const val X="AES/GCM/NoPadding"}
}
object AiProviderStore{
 private const val MP="ai_provider_migration";private const val DONE="legacy_v2_done"
 fun load(c:Context,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{migrate(c,s);val d=AiProviderDatabase.get(c).dao();val st=d.settings();val rows=d.all().map{AiProvider(it,s.exists(it.id),d.models(it.id),if(it.id==st?.defaultProviderId)st.defaultModelEntryId else null)};return AiProviderConfig(rows,st?.defaultProviderId,st?.defaultModelEntryId)}
 fun save(c:Context,p:AiProviderEntity,key:CharArray?,clearKey:Boolean=false,makeDefault:Boolean=false,s:AiSecretStore=AndroidAiSecretStore(c)):Result<AiProviderConfig> = runCatching{
  val endpoint=AiEndpointResolver.resolve(p.baseUrl,p.appendV1,p.apiMode);AiEndpointValidator.validate(endpoint).getOrThrow();require(p.displayName.isNotBlank())
  when(AiSecretPolicy.action(key,clearKey)){SecretUpdate.REPLACE->s.put(p.id,key!!);SecretUpdate.CLEAR->s.clear(p.id);else->Unit};val d=AiProviderDatabase.get(c).dao();d.upsert(p.copy(displayName=p.displayName.trim(),baseUrl=p.baseUrl.trim().trimEnd('/'),endpoint=endpoint,updatedAt=System.currentTimeMillis()));if(d.models(p.id).isEmpty()&&p.modelId.isNotBlank())d.upsertModel(AiProviderModelEntry(providerId=p.id,modelId=p.modelId));if(makeDefault||d.settings()==null){val m=d.models(p.id).firstOrNull()?.id;d.settings(AiSettingsEntity(defaultProviderId=p.id,defaultModelEntryId=m))};load(c,s)
 }
 fun addModel(c:Context,providerId:String,modelId:String,alias:String?=null,origin:AiModelOrigin=AiModelOrigin.USER,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{require(modelId.isNotBlank()&&modelId.length<=200);val d=AiProviderDatabase.get(c).dao();d.upsertModel(AiProviderModelEntry(providerId=providerId,modelId=modelId.trim(),alias=alias?.trim()?.takeIf{it.isNotEmpty()},origin=origin,sortOrder=d.models(providerId).size));return load(c,s)}
 fun setDefaultModel(c:Context,providerId:String,entryId:String,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{val d=AiProviderDatabase.get(c).dao();require(d.model(entryId)?.providerId==providerId);d.settings(AiSettingsEntity(defaultProviderId=providerId,defaultModelEntryId=entryId));return load(c,s)}
 fun deleteModel(c:Context,id:String,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{val d=AiProviderDatabase.get(c).dao();val m=d.model(id)?:return load(c,s);d.deleteModel(id);val st=d.settings();if(st?.defaultModelEntryId==id)d.settings(AiSettingsEntity(defaultProviderId=st.defaultProviderId,defaultModelEntryId=d.models(m.providerId).firstOrNull()?.id));return load(c,s)}
 suspend fun refreshModels(c:Context,id:String,s:AiSecretStore=AndroidAiSecretStore(c),useCase:RefreshProviderModelsUseCase=RefreshProviderModelsUseCase()):AiProviderConfig{val d=AiProviderDatabase.get(c).dao();val p=requireNotNull(d.byId(id));val key=requireNotNull(s.get(id)){"API key missing"};val merged=useCase.execute(p,key,d.models(id));d.upsertModels(merged);return load(c,s)}
 fun delete(c:Context,id:String,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{s.clear(id);AiProviderDatabase.get(c).dao().deleteAndFallback(id);return load(c,s)}
 fun setDefault(c:Context,id:String,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{val d=AiProviderDatabase.get(c).dao();require(d.byId(id)?.enabled==true);d.settings(AiSettingsEntity(defaultProviderId=id,defaultModelEntryId=d.models(id).firstOrNull()?.id));return load(c,s)}
 fun setEnabled(c:Context,id:String,e:Boolean,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{val d=AiProviderDatabase.get(c).dao();val p=requireNotNull(d.byId(id));d.upsert(p.copy(enabled=e,updatedAt=System.currentTimeMillis()));if(!e&&d.settings()?.defaultProviderId==id){val n=d.all().firstOrNull{it.enabled&&it.id!=id};d.settings(AiSettingsEntity(defaultProviderId=n?.id,defaultModelEntryId=n?.let{d.models(it.id).firstOrNull()?.id}))};return load(c,s)}
 fun migrate(c:Context,s:AiSecretStore){val marker=c.getSharedPreferences(MP,0);if(marker.getBoolean(DONE,false))return;val old=c.getSharedPreferences("ai_config_v1",0);val ep=old.getString("endpoint","").orEmpty();val model=old.getString("model","").orEmpty();if(ep.isNotBlank()&&model.isNotBlank()){val d=AiProviderDatabase.get(c).dao();val now=System.currentTimeMillis();val base=ep.substringBefore("/chat/completions").removeSuffix("/v1");if(d.byId(AiProviderIds.LEGACY)==null){d.upsert(AiProviderEntity(AiProviderIds.LEGACY,"已迁移的 Provider",baseUrl=base,endpoint=ep,modelId=model,createdAt=now,updatedAt=now,sortOrder=0,appendV1=ep.contains("/v1/")));d.upsertModel(AiProviderModelEntry(providerId=AiProviderIds.LEGACY,modelId=model));d.settings(AiSettingsEntity(defaultProviderId=AiProviderIds.LEGACY,defaultModelEntryId=d.models(AiProviderIds.LEGACY).first().id))};if(s.exists()&&!s.exists(AiProviderIds.LEGACY)){val k=s.get()?:return;s.put(AiProviderIds.LEGACY,k)}};marker.edit().putBoolean(DONE,true).commit()}
}
object AiConfigStore{
 fun load(c:Context,s:AiSecretStore=AndroidAiSecretStore(c))=AiProviderStore.load(c,s).defaultProvider?.let(::AiConfig)?:AiConfig()
 fun save(c:Context,endpoint:String,model:String,key:CharArray?,s:AiSecretStore=AndroidAiSecretStore(c)):Result<AiConfig>{val cfg=AiProviderStore.load(c,s);val now=System.currentTimeMillis();val base=endpoint.substringBefore("/chat/completions").substringBefore("/responses").removeSuffix("/v1");val p=cfg.defaultProvider?.entity?:AiProviderEntity(displayName="OpenAI-compatible",baseUrl=base,endpoint=endpoint,modelId=model,createdAt=now,updatedAt=now,sortOrder=cfg.providers.size,appendV1=endpoint.contains("/v1/"));return AiProviderStore.save(c,p.copy(baseUrl=base,modelId=model),key,s=s).map{AiConfig(requireNotNull(it.defaultProvider))}}
}
