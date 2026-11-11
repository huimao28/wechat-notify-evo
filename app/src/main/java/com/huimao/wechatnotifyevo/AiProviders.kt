/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2025-2026 OpenMinis contributors
 * Copyright (C) 2026 WeChat Notify Evo contributors
 * Adapted from OpenMinis provider configuration and repository concepts.
 */
package com.huimao.wechatnotifyevo

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class AiProviderKind { OPENAI, DEEPSEEK, OPENROUTER, CUSTOM }
enum class AiApiMode { CHAT_COMPLETIONS, RESPONSES }
enum class AiModelOrigin { PRESET, REMOTE, USER }
enum class AiProviderPreset(val label:String,val baseUrl:String,val appendV1:Boolean,val defaultMode:AiApiMode,val models:List<String>){
 OPENAI("OpenAI","https://api.openai.com",true,AiApiMode.RESPONSES,listOf("gpt-5-mini","gpt-4.1-mini")),
 DEEPSEEK("DeepSeek","https://api.deepseek.com",false,AiApiMode.CHAT_COMPLETIONS,listOf("deepseek-chat","deepseek-reasoner")),
 OPENROUTER("OpenRouter","https://openrouter.ai/api",true,AiApiMode.CHAT_COMPLETIONS,listOf("openai/gpt-5-mini","anthropic/claude-sonnet-4.5")),
 CUSTOM("自定义 OpenAI-compatible","",true,AiApiMode.CHAT_COMPLETIONS,emptyList());
 val endpoint get()=AiEndpointResolver.resolve(baseUrl,appendV1,defaultMode)
 val model get()=models.firstOrNull().orEmpty()
}
@Entity(tableName="ai_providers",indices=[Index(value=["sortOrder"])])
data class AiProviderEntity(@PrimaryKey val id:String=UUID.randomUUID().toString(),val displayName:String,val kind:AiProviderKind=AiProviderKind.CUSTOM,val baseUrl:String,val endpoint:String="",val modelId:String="",val enabled:Boolean=true,val createdAt:Long,val updatedAt:Long,val sortOrder:Int,val apiMode:AiApiMode=AiApiMode.CHAT_COMPLETIONS,val appendV1:Boolean=true)
@Entity(tableName="ai_provider_models",foreignKeys=[ForeignKey(entity=AiProviderEntity::class,parentColumns=["id"],childColumns=["providerId"],onDelete=ForeignKey.CASCADE)],indices=[Index("providerId"),Index(value=["providerId","modelId"],unique=true)])
data class AiProviderModelEntry(@PrimaryKey val id:String=UUID.randomUUID().toString(),val providerId:String,val modelId:String,val alias:String?=null,val origin:AiModelOrigin=AiModelOrigin.USER,val enabled:Boolean=true,val sortOrder:Int=0)
@Entity(tableName="ai_settings") data class AiSettingsEntity(@PrimaryKey val singleton:Int=1,val defaultProviderId:String?,val defaultModelEntryId:String?=null)
data class AiProvider(val entity:AiProviderEntity,val hasApiKey:Boolean,val models:List<AiProviderModelEntry> = emptyList(),val defaultModelEntryId:String?=null){
 val id get()=entity.id;val displayName get()=entity.displayName;val enabled get()=entity.enabled
 val defaultModel get()=models.firstOrNull{it.id==defaultModelEntryId&&it.enabled}?:models.firstOrNull{it.enabled}
 val modelId get()=defaultModel?.modelId?:entity.modelId
 val endpoint get()=AiEndpointResolver.resolve(entity.baseUrl.ifBlank{entity.endpoint},entity.appendV1,entity.apiMode)
 val endpointHost get()=AiEndpointValidator.validate(endpoint).getOrNull()?.host
 val configured get()=enabled&&hasApiKey&&endpointHost!=null&&modelId.isNotBlank()
}
@Dao interface AiProviderDao{
 @Query("SELECT * FROM ai_providers ORDER BY sortOrder, createdAt") fun all():List<AiProviderEntity>
 @Query("SELECT * FROM ai_providers WHERE id=:id") fun byId(id:String):AiProviderEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) fun upsert(value:AiProviderEntity)
 @Query("DELETE FROM ai_providers WHERE id=:id") fun delete(id:String)
 @Query("SELECT * FROM ai_provider_models WHERE providerId=:id ORDER BY sortOrder, modelId") fun models(id:String):List<AiProviderModelEntry>
 @Query("SELECT * FROM ai_provider_models WHERE id=:id") fun model(id:String):AiProviderModelEntry?
 @Insert(onConflict=OnConflictStrategy.REPLACE) fun upsertModel(value:AiProviderModelEntry)
 @Insert(onConflict=OnConflictStrategy.REPLACE) fun upsertModels(value:List<AiProviderModelEntry>)
 @Query("DELETE FROM ai_provider_models WHERE id=:id") fun deleteModel(id:String)
 @Query("SELECT * FROM ai_settings WHERE singleton=1") fun settings():AiSettingsEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) fun settings(value:AiSettingsEntity)
 @Transaction fun deleteAndFallback(id:String){delete(id);val s=settings();if(s?.defaultProviderId==id){val nextId=ProviderFallbackPolicy.providerAfterRemoval(all(),id);settings(AiSettingsEntity(defaultProviderId=nextId,defaultModelEntryId=nextId?.let{ProviderFallbackPolicy.modelAfterRemoval(models(it),"")}))}}
}
@Database(entities=[AiProviderEntity::class,AiProviderModelEntry::class,AiSettingsEntity::class],version=2,exportSchema=true)
@TypeConverters(AiProviderConverters::class) abstract class AiProviderDatabase:RoomDatabase(){abstract fun dao():AiProviderDao
 companion object{
  val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){MigrationSql.V1_TO_V2.forEach(db::execSQL)}}
  @Volatile private var db:AiProviderDatabase?=null
  fun get(c:Context)=db?:synchronized(this){db?:Room.databaseBuilder(c.applicationContext,AiProviderDatabase::class.java,"ai_providers_v1.db").addMigrations(MIGRATION_1_2).build().also{db=it}}
 }}
object MigrationSql { val V1_TO_V2=listOf(
 "ALTER TABLE ai_providers ADD COLUMN apiMode TEXT NOT NULL DEFAULT 'CHAT_COMPLETIONS'",
 "ALTER TABLE ai_providers ADD COLUMN appendV1 INTEGER NOT NULL DEFAULT 0",
 "ALTER TABLE ai_settings ADD COLUMN defaultModelEntryId TEXT",
 "CREATE TABLE IF NOT EXISTS ai_provider_models (id TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, alias TEXT, origin TEXT NOT NULL, enabled INTEGER NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(providerId) REFERENCES ai_providers(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
 "CREATE INDEX IF NOT EXISTS index_ai_provider_models_providerId ON ai_provider_models(providerId)",
 "CREATE UNIQUE INDEX IF NOT EXISTS index_ai_provider_models_providerId_modelId ON ai_provider_models(providerId,modelId)",
 "INSERT INTO ai_provider_models(id,providerId,modelId,alias,origin,enabled,sortOrder) SELECT lower(hex(randomblob(4)))||'-'||lower(hex(randomblob(2)))||'-4'||substr(lower(hex(randomblob(2))),2)||'-a'||substr(lower(hex(randomblob(2))),2)||'-'||lower(hex(randomblob(6))),id,modelId,NULL,'USER',1,0 FROM ai_providers WHERE modelId != ''",
 "UPDATE ai_settings SET defaultModelEntryId=(SELECT m.id FROM ai_provider_models m WHERE m.providerId=ai_settings.defaultProviderId ORDER BY m.sortOrder LIMIT 1)"
)}
class AiProviderConverters{
 @TypeConverter fun kind(v:AiProviderKind)=v.name;@TypeConverter fun kind(v:String)=if(v=="OPENAI_COMPAT")AiProviderKind.CUSTOM else AiProviderKind.valueOf(v)
 @TypeConverter fun mode(v:AiApiMode)=v.name;@TypeConverter fun mode(v:String)=AiApiMode.valueOf(v)
 @TypeConverter fun origin(v:AiModelOrigin)=v.name;@TypeConverter fun origin(v:String)=AiModelOrigin.valueOf(v)
}
object AiProviderIds{val LEGACY:String=UUID.nameUUIDFromBytes("wechat-notify-evo:legacy-ai-provider:v1".toByteArray(StandardCharsets.UTF_8)).toString()}
data class AiProviderConfig(val providers:List<AiProvider>,val defaultProviderId:String?,val defaultModelEntryId:String?=null){val defaultProvider get()=providers.firstOrNull{it.id==defaultProviderId&&it.enabled};fun selected(id:String?)=providers.firstOrNull{it.id==id&&it.enabled}?:defaultProvider}
data class AiConfig(val endpoint:String="",val model:String="",val hasApiKey:Boolean=false,val providerId:String="",val providerName:String="",val apiMode:AiApiMode=AiApiMode.CHAT_COMPLETIONS,val modelEntryId:String=""){
 constructor(p:AiProvider):this(p.endpoint,p.modelId,p.hasApiKey,p.id,p.displayName,p.entity.apiMode,p.defaultModel?.id.orEmpty());val endpointHost get()=AiEndpointValidator.validate(endpoint).getOrNull()?.host;val configured get()=endpointHost!=null&&model.isNotBlank()&&hasApiKey
}
object AiEndpointResolver{
 private val suffixes=listOf("/chat/completions","/responses")
 fun resolve(base:String,appendV1:Boolean,mode:AiApiMode):String{val root=base.trim().trimEnd('/');if(root.isBlank())return "";if(suffixes.any{root.endsWith(it)})return root;val versioned=if(appendV1&&!root.endsWith("/v1"))"$root/v1" else root;return "$versioned/${if(mode==AiApiMode.RESPONSES)"responses" else "chat/completions"}"}
 fun modelsEndpoint(baseOrEndpoint:String,appendV1:Boolean):String{var root=baseOrEndpoint.trim().trimEnd('/');suffixes.firstOrNull{root.endsWith(it)}?.let{root=root.removeSuffix(it)};if(appendV1&&!root.endsWith("/v1"))root+="/v1";return "$root/models"}
}
