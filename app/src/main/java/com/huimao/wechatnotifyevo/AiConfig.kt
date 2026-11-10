/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2025-2026 OpenMinis contributors
 * Copyright (C) 2026 WeChat Notify Evo contributors
 * Adapted from OpenMinis provider credential/configuration concepts.
 * Modified: Android Keystore AES-GCM only, per-provider IDs, no plaintext fallback.
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
object AiSecretPolicy{fun action(value:CharArray?,clear:Boolean)=when{clear->SecretUpdate.CLEAR;value == null || value.isEmpty()->SecretUpdate.KEEP;else->SecretUpdate.REPLACE}}
interface AiSecretStore{
 fun put(providerId:String,secret:CharArray);fun get(providerId:String):CharArray?;fun clear(providerId:String)
 fun exists(providerId:String):Boolean{val s=get(providerId)?:return false;s.fill('\u0000');return true}
 fun put(secret:CharArray)=put(LEGACY_KEY,secret);fun get()=get(LEGACY_KEY);fun clear()=clear(LEGACY_KEY);fun exists()=exists(LEGACY_KEY)
 companion object{const val LEGACY_KEY="legacy"}
}
class AndroidAiSecretStore(context:Context):AiSecretStore{
 private val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 override fun put(providerId:String,secret:CharArray){require(secret.isNotEmpty());val c=Cipher.getInstance(X);c.init(Cipher.ENCRYPT_MODE,key());val plain=secret.concatToString().toByteArray();try{check(prefs.edit().putString(blob(providerId),Base64.encodeToString(c.iv+c.doFinal(plain),Base64.NO_WRAP)).commit())}finally{plain.fill(0);secret.fill('\u0000')}}
 override fun get(providerId:String):CharArray?{val encoded=prefs.getString(blob(providerId),null)?:return null;return try{val all=Base64.decode(encoded,Base64.NO_WRAP);require(all.size>12);val c=Cipher.getInstance(X);c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,all.copyOfRange(0,12)));val p=c.doFinal(all.copyOfRange(12,all.size));try{p.toString(Charsets.UTF_8).toCharArray()}finally{p.fill(0);all.fill(0)}}catch(_:Throwable){clear(providerId);null}}
 override fun clear(providerId:String){prefs.edit().remove(blob(providerId)).commit()}
 private fun blob(id:String)=if(id==AiSecretStore.LEGACY_KEY)"api_key_aes_gcm" else "provider_$id"
 private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(ks.getKey(ALIAS,null)as?SecretKey)?.let{return it};return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())}.generateKey()}
 companion object{const val PREFS="ai_secret_ciphertext_v1";const val ALIAS="wne_ai_api_key_v1";const val X="AES/GCM/NoPadding"}
}

object AiProviderStore{
 private const val MIGRATION_PREFS="ai_provider_migration";private const val DONE="legacy_v1_done"
 fun load(c:Context,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{migrate(c,s);val d=AiProviderDatabase.get(c).dao();val rows=d.all().map{AiProvider(it,s.exists(it.id))};val default=d.settings()?.defaultProviderId?.takeIf{id->rows.any{it.id==id&&it.enabled}};return AiProviderConfig(rows,default)}
 fun save(c:Context,p:AiProviderEntity,key:CharArray?,clearKey:Boolean=false,makeDefault:Boolean=false,s:AiSecretStore=AndroidAiSecretStore(c)):Result<AiProviderConfig> = runCatching{
  val endpoint=AiEndpointValidator.validate(p.endpoint).getOrThrow().url;require(p.displayName.isNotBlank()&&p.modelId.isNotBlank()&&p.modelId.length<=200)
  when(AiSecretPolicy.action(key,clearKey)){SecretUpdate.REPLACE->s.put(p.id,key!!);SecretUpdate.CLEAR->s.clear(p.id);else->Unit}
  val d=AiProviderDatabase.get(c).dao();d.upsert(p.copy(baseUrl=java.net.URI(endpoint).let{"${it.scheme}://${it.host}"},endpoint=endpoint,displayName=p.displayName.trim(),modelId=p.modelId.trim(),updatedAt=System.currentTimeMillis()))
  if(makeDefault||d.settings()==null)d.settings(AiSettingsEntity(defaultProviderId=p.id));load(c,s)
 }
 fun delete(c:Context,id:String,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{s.clear(id);AiProviderDatabase.get(c).dao().deleteAndFallback(id);return load(c,s)}
 fun setDefault(c:Context,id:String,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{val d=AiProviderDatabase.get(c).dao();require(d.byId(id)?.enabled==true);d.settings(AiSettingsEntity(defaultProviderId=id));return load(c,s)}
 fun setEnabled(c:Context,id:String,enabled:Boolean,s:AiSecretStore=AndroidAiSecretStore(c)):AiProviderConfig{val d=AiProviderDatabase.get(c).dao();val p=requireNotNull(d.byId(id));d.upsert(p.copy(enabled=enabled,updatedAt=System.currentTimeMillis()));if(!enabled&&d.settings()?.defaultProviderId==id)d.settings(AiSettingsEntity(defaultProviderId=d.all().firstOrNull{it.enabled&&it.id!=id}?.id));return load(c,s)}
 fun migrate(c:Context,s:AiSecretStore){val marker=c.getSharedPreferences(MIGRATION_PREFS,0);if(marker.getBoolean(DONE,false))return;val old=c.getSharedPreferences("ai_config_v1",0);val ep=old.getString("endpoint","").orEmpty();val model=old.getString("model","").orEmpty();if(ep.isBlank()||model.isBlank()){marker.edit().putBoolean(DONE,true).commit();return};val d=AiProviderDatabase.get(c).dao();val now=System.currentTimeMillis();if(d.byId(AiProviderIds.LEGACY)==null)d.upsert(AiProviderEntity(AiProviderIds.LEGACY,"已迁移的 Provider",baseUrl="",endpoint=ep,modelId=model,createdAt=now,updatedAt=now,sortOrder=0));d.settings(AiSettingsEntity(defaultProviderId=AiProviderIds.LEGACY));if(s.exists()){val legacy=s.get()?:return;runCatching{s.put(AiProviderIds.LEGACY,legacy)}.getOrElse{return};if(!s.exists(AiProviderIds.LEGACY))return};marker.edit().putBoolean(DONE,true).commit()}
}
object AiConfigStore{
 fun load(c:Context,s:AiSecretStore=AndroidAiSecretStore(c))=AiProviderStore.load(c,s).defaultProvider?.let(::AiConfig)?:AiConfig()
 fun save(c:Context,endpoint:String,model:String,apiKey:CharArray?,s:AiSecretStore=AndroidAiSecretStore(c)):Result<AiConfig>{val cfg=AiProviderStore.load(c,s);val now=System.currentTimeMillis();val current=cfg.defaultProvider?.entity?:AiProviderEntity(displayName="OpenAI-compatible",baseUrl="",endpoint=endpoint,modelId=model,createdAt=now,updatedAt=now,sortOrder=cfg.providers.size);return AiProviderStore.save(c,current.copy(endpoint=endpoint,modelId=model),apiKey,s=s).map{AiConfig(requireNotNull(it.defaultProvider))}}
}
