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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class AiProviderKind { OPENAI_COMPAT }
enum class AiProviderPreset(val label:String,val baseUrl:String,val model:String,val appendV1Suffix:Boolean){
 OPENAI("OpenAI","https://api.openai.com/v1","gpt-4o-mini",false),
 DEEPSEEK("DeepSeek","https://api.deepseek.com","deepseek-chat",true),
 OPENROUTER("OpenRouter","https://openrouter.ai/api/v1","openai/gpt-4o-mini",false),
 CUSTOM("自定义","","",true)
}
@Entity(tableName="ai_providers",indices=[Index(value=["sortOrder"])])
data class AiProviderEntity(
 @PrimaryKey val id:String=UUID.randomUUID().toString(), val displayName:String,
 val kind:AiProviderKind=AiProviderKind.OPENAI_COMPAT, val baseUrl:String,
 val endpoint:String, val modelId:String, val enabled:Boolean=true,
 val appendV1Suffix:Boolean=true, val apiPathMode:AiApiPathMode=AiApiPathMode.BASE_URL,
 val createdAt:Long, val updatedAt:Long, val sortOrder:Int
)
@Entity(tableName="ai_settings") data class AiSettingsEntity(@PrimaryKey val singleton:Int=1,val defaultProviderId:String?)
data class AiProvider(val entity:AiProviderEntity,val hasApiKey:Boolean){
 val id get()=entity.id; val displayName get()=entity.displayName; val endpoint get()=entity.endpoint; val baseUrl get()=entity.baseUrl
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
@Database(entities=[AiProviderEntity::class,AiSettingsEntity::class],version=2,exportSchema=true)
@TypeConverters(AiProviderConverters::class) abstract class AiProviderDatabase:RoomDatabase(){ abstract fun dao():AiProviderDao
 companion object{
  val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){
   db.execSQL("ALTER TABLE ai_providers ADD COLUMN appendV1Suffix INTEGER NOT NULL DEFAULT 0")
   db.execSQL("ALTER TABLE ai_providers ADD COLUMN apiPathMode TEXT NOT NULL DEFAULT 'FULL_ENDPOINT'")
  }}
  @Volatile private var db:AiProviderDatabase?=null; fun get(c:Context)=db?:synchronized(this){db?:Room.databaseBuilder(c.applicationContext,AiProviderDatabase::class.java,"ai_providers_v1.db").addMigrations(MIGRATION_1_2).build().also{db=it}}}}
class AiProviderConverters{@TypeConverter fun kind(v:AiProviderKind)=v.name;@TypeConverter fun kind(v:String)=AiProviderKind.valueOf(v);@TypeConverter fun pathMode(v:AiApiPathMode)=v.name;@TypeConverter fun pathMode(v:String)=AiApiPathMode.valueOf(v)}
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
