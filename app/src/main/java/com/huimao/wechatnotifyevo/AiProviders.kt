/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2025-2026 OpenMinis contributors
 * Copyright (C) 2026 WeChat Notify Evo contributors
 * Adapted from OpenMinis ProviderConfig/ProviderInstanceEntity/ProviderRepository.
 * Modified: reduced to OpenAI-compatible BYOK providers; removed OAuth, model groups and modalities.
 */
package com.huimao.wechatnotifyevo

import android.content.Context
import androidx.room.*
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class AiProviderKind { OPENAI_COMPAT }
enum class AiProviderPreset(val label:String,val endpoint:String,val model:String){
 OPENAI("OpenAI","https://api.openai.com/v1/chat/completions","gpt-4o-mini"),
 DEEPSEEK("DeepSeek","https://api.deepseek.com/chat/completions","deepseek-chat"),
 OPENROUTER("OpenRouter","https://openrouter.ai/api/v1/chat/completions","openai/gpt-4o-mini"),
 CUSTOM("自定义","","")
}
@Entity(tableName="ai_providers",indices=[Index(value=["sortOrder"])])
data class AiProviderEntity(
 @PrimaryKey val id:String=UUID.randomUUID().toString(), val displayName:String,
 val kind:AiProviderKind=AiProviderKind.OPENAI_COMPAT, val baseUrl:String,
 val endpoint:String, val modelId:String, val enabled:Boolean=true,
 val createdAt:Long, val updatedAt:Long, val sortOrder:Int
)
@Entity(tableName="ai_settings") data class AiSettingsEntity(@PrimaryKey val singleton:Int=1,val defaultProviderId:String?)
data class AiProvider(val entity:AiProviderEntity,val hasApiKey:Boolean){
 val id get()=entity.id; val displayName get()=entity.displayName; val endpoint get()=entity.endpoint
 val modelId get()=entity.modelId; val enabled get()=entity.enabled
 val endpointHost get()=AiEndpointValidator.validate(endpoint).getOrNull()?.host
 val configured get()=enabled&&hasApiKey&&endpointHost!=null&&modelId.isNotBlank()
}
@Dao interface AiProviderDao{
 @Query("SELECT * FROM ai_providers ORDER BY sortOrder, createdAt") fun all():List<AiProviderEntity>
 @Query("SELECT * FROM ai_providers WHERE id=:id") fun byId(id:String):AiProviderEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) fun upsert(value:AiProviderEntity)
 @Query("DELETE FROM ai_providers WHERE id=:id") fun delete(id:String)
 @Query("SELECT * FROM ai_settings WHERE singleton=1") fun settings():AiSettingsEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) fun settings(value:AiSettingsEntity)
 @Transaction fun deleteAndFallback(id:String){ delete(id); val s=settings(); if(s?.defaultProviderId==id) settings(AiSettingsEntity(defaultProviderId=all().firstOrNull{it.enabled}?.id)) }
}
@Database(entities=[AiProviderEntity::class,AiSettingsEntity::class],version=1,exportSchema=true)
@TypeConverters(AiProviderConverters::class) abstract class AiProviderDatabase:RoomDatabase(){ abstract fun dao():AiProviderDao
 companion object{@Volatile private var db:AiProviderDatabase?=null; fun get(c:Context)=db?:synchronized(this){db?:Room.databaseBuilder(c.applicationContext,AiProviderDatabase::class.java,"ai_providers_v1.db").build().also{db=it}}}}
class AiProviderConverters{@TypeConverter fun kind(v:AiProviderKind)=v.name;@TypeConverter fun kind(v:String)=AiProviderKind.valueOf(v)}
object AiProviderIds{ val LEGACY:String=UUID.nameUUIDFromBytes("wechat-notify-evo:legacy-ai-provider:v1".toByteArray(StandardCharsets.UTF_8)).toString() }

data class AiProviderConfig(val providers:List<AiProvider>,val defaultProviderId:String?){
 val defaultProvider get()=providers.firstOrNull{it.id==defaultProviderId&&it.enabled}
 fun selected(id:String?)=providers.firstOrNull{it.id==id&&it.enabled}?:defaultProvider
}
data class AiConfig(val endpoint:String="",val model:String="",val hasApiKey:Boolean=false,val providerId:String="",val providerName:String=""){
 constructor(p:AiProvider):this(p.endpoint,p.modelId,p.hasApiKey,p.id,p.displayName)
 val endpointHost get()=AiEndpointValidator.validate(endpoint).getOrNull()?.host
 val configured get()=endpointHost!=null&&model.isNotBlank()&&hasApiKey
}
