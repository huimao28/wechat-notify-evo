package com.huimao.wechatnotifyevo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.UUID

object ProviderUiPolicy {
    fun preset(kind: AiProviderKind) = AiProviderPreset.entries.first { it.name == kind.name }
    fun summary(provider: AiProvider?) = provider?.let { "${it.displayName} · ${it.entity.kind.name} · ${it.endpointHost ?: "端点无效"} · ${if (it.hasApiKey) "Key 已配置" else "Key 未配置"}" } ?: "尚未配置默认 Provider"
}

@Composable fun ProviderSettingsSummary(state: AiUiState, onManage: () -> Unit) {
    val provider = state.providers.firstOrNull { it.id == state.config.providerId }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), Arrangement.spacedBy(10.dp)) {
        Text("AI Provider", style = MaterialTheme.typography.titleLarge)
        Text(ProviderUiPolicy.summary(provider), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onManage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("管理 AI Provider") }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProviderListScreen(state: AiUiState, onBack:()->Unit, onAdd:()->Unit, onEdit:(String)->Unit) {
    Scaffold(topBar={ TopAppBar(title={Text("AI Provider")},navigationIcon={Back(onBack)}) }, floatingActionButton={FloatingActionButton(onClick=onAdd){Icon(Icons.Default.Add,"新增 Provider")}}) { p ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(p)) {
            val wide=maxWidth>=720.dp
            Row(Modifier.fillMaxSize()) {
                ProviderList(state, onEdit, Modifier.weight(if(wide) .44f else 1f))
                if(wide) { VerticalDivider(); Box(Modifier.weight(.56f).fillMaxHeight().padding(24.dp)){ Text("选择 Provider 查看详情，或新增 Provider",style=MaterialTheme.typography.titleLarge) } }
            }
        }
    }
}
@Composable private fun ProviderList(state:AiUiState,onEdit:(String)->Unit,modifier:Modifier=Modifier){
    LazyColumn(modifier,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(state.providers.isEmpty()) item { Card{Text("尚无 Provider。点击新增开始配置。",Modifier.padding(20.dp))} }
        items(state.providers,key={it.id}){ provider -> Card(Modifier.fillMaxWidth().clickable{onEdit(provider.id)}.semantics{role=Role.Button}){
            ListItem(headlineContent={Text(provider.displayName)},supportingContent={Text("${provider.entity.kind.name} · ${provider.endpointHost ?: "端点无效"}\n${if(provider.hasApiKey)"凭据已配置" else "凭据未配置"} · ${if(provider.enabled)"已启用" else "已停用"}")},trailingContent={if(provider.id==state.config.providerId) AssistChip(onClick={},label={Text("默认")})})
        }}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AddProviderChooseScreen(onBack:()->Unit,onChoose:(AiProviderKind)->Unit){ Scaffold(topBar={TopAppBar(title={Text("新增 Provider")},navigationIcon={Back(onBack)})}){p-> LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item{Text("选择类型",style=MaterialTheme.typography.headlineSmall)}
    items(AiProviderKind.entries){kind->val preset=ProviderUiPolicy.preset(kind);Card(Modifier.fillMaxWidth().clickable{onChoose(kind)}){ListItem(headlineContent={Text(preset.label)},supportingContent={Text(if(kind==AiProviderKind.CUSTOM)"自定义 OpenAI-compatible 端点" else preset.baseUrl)})}}
    item{Text("仅支持 API Key 凭据，不支持 OAuth。Key 加密保存在本机。",style=MaterialTheme.typography.bodySmall)}
} } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProviderEditorScreen(state:AiUiState,id:String?,presetName:String?,actions:ProviderEditorActions,onBack:()->Unit,onModels:(String)->Unit,onTest:(String?)->Unit){
    val current=state.providers.firstOrNull{it.id==id};val preset=runCatching{AiProviderPreset.valueOf(presetName?:"CUSTOM")}.getOrDefault(AiProviderPreset.CUSTOM)
    var name by rememberSaveable(id,presetName){mutableStateOf(current?.displayName?:preset.label)};var base by rememberSaveable(id,presetName){mutableStateOf(current?.entity?.baseUrl?:preset.baseUrl)}
    var appendV1 by rememberSaveable(id,presetName){mutableStateOf(current?.entity?.appendV1?:preset.appendV1)};var mode by rememberSaveable(id,presetName){mutableStateOf(current?.entity?.apiMode?:preset.defaultMode)}
    var key by remember(id,presetName){mutableStateOf("")};var enabled by rememberSaveable(id){mutableStateOf(current?.enabled?:true)};var makeDefault by rememberSaveable(id){mutableStateOf(current?.id==state.config.providerId)};var deleteConfirm by rememberSaveable{mutableStateOf(false)}
    if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("删除 Provider？")},text={Text("配置、模型和对应密钥将删除；默认项会安全回退。")},confirmButton={Button(onClick={current?.let{actions.delete(it.id)};deleteConfirm=false;onBack()}){Text("删除")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("取消")}})
    Scaffold(topBar={TopAppBar(title={Text(if(current==null)"配置 Provider" else "编辑 Provider")},navigationIcon={Back(onBack)})}){p-> LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{OutlinedTextField(name,{name=it},label={Text("名称")},singleLine=true,modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(base,{base=it},label={Text("Base URL")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{SwitchRow("自动追加 /v1",appendV1){appendV1=it}};item{Text("API 模式",style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(mode==AiApiMode.CHAT_COMPLETIONS,{mode=AiApiMode.CHAT_COMPLETIONS},{Text("Chat")});FilterChip(mode==AiApiMode.RESPONSES,{mode=AiApiMode.RESPONSES},{Text("Responses")})}}
        item{Text(if(current?.hasApiKey==true)"Key 已配置；留空保留" else "Key 未配置",style=MaterialTheme.typography.bodySmall);OutlinedTextField(key,{key=it},label={Text("API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{SwitchRow("启用",enabled){enabled=it};SwitchRow("设为默认",makeDefault){makeDefault=it}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={val now=System.currentTimeMillis();val entity=(current?.entity?:AiProviderEntity(displayName=name,kind=AiProviderKind.valueOf(preset.name),baseUrl=base,createdAt=now,updatedAt=now,sortOrder=state.providers.size)).copy(displayName=name,baseUrl=base,appendV1=appendV1,apiMode=mode,enabled=enabled);actions.save(entity,key.toCharArray(),makeDefault);key=""},enabled=!state.providerOperationRunning&&name.isNotBlank()&&base.isNotBlank(),modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(if(state.providerOperationRunning)"保存中…" else "保存")};OutlinedButton(onClick={onTest(current?.id)},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Quick Test")}}}
        if(current!=null)item{OutlinedButton(onClick={onModels(current.id)},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("管理模型")};if(current.hasApiKey)TextButton(onClick={actions.clearKey},Modifier.fillMaxWidth()){Text("清除 API Key")};TextButton(onClick={deleteConfirm=true},Modifier.fillMaxWidth()){Text("删除 Provider",color=MaterialTheme.colorScheme.error)}}
        item{state.providerOperationMessage?.let{Text(it,color=if(state.providerOperationError)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)};Text("仅支持 API Key，不支持 OAuth。",style=MaterialTheme.typography.bodySmall)}
    }}
}

data class ProviderEditorActions(val save:(AiProviderEntity,CharArray,Boolean)->Unit,val delete:(String)->Unit,val clearKey:(String)->Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProviderModelsScreen(state:AiUiState,providerId:String,onBack:()->Unit,onAdd:(String,String?)->Unit,onDelete:(String)->Unit,onDefault:(String,String)->Unit,onRefresh:(String)->Unit){
 val provider=state.providers.firstOrNull{it.id==providerId};var model by rememberSaveable{mutableStateOf("")};var alias by rememberSaveable{mutableStateOf("")};var confirmRefresh by rememberSaveable{mutableStateOf(false)}
 if(confirmRefresh)AlertDialog(onDismissRequest={confirmRefresh=false},title={Text("联网刷新模型？")},text={Text("将使用此 Provider 的已保存 Key 主动请求 GET /models；手动模型会保留。")},confirmButton={Button(onClick={confirmRefresh=false;onRefresh(providerId)}){Text("确认联网")}},dismissButton={TextButton(onClick={confirmRefresh=false}){Text("取消")}})
 Scaffold(topBar={TopAppBar(title={Text("${provider?.displayName?:"Provider"} · 模型")},navigationIcon={Back(onBack)})}){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{OutlinedButton(onClick={confirmRefresh=true},enabled=!state.providerOperationRunning,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("刷新 GET /models")};Text("刷新后合并远端列表，并保留所有手动模型。",style=MaterialTheme.typography.bodySmall)}
  provider?.let{pr->items(pr.models,key={it.id}){m->Card{ListItem(headlineContent={Text(m.alias?:m.modelId)},supportingContent={Text("${m.modelId} · ${m.origin}")},trailingContent={Row{TextButton(onClick={onDefault(providerId,m.id)}){Text(if(m.id==pr.defaultModelEntryId)"默认" else "设默认")};TextButton(onClick={onDelete(m.id)}){Text("删除")}}})}}}
  item{OutlinedTextField(model,{model=it},label={Text("模型 ID")},modifier=Modifier.fillMaxWidth());OutlinedTextField(alias,{alias=it},label={Text("别名（可选）")},modifier=Modifier.fillMaxWidth());Button(onClick={onAdd(model,alias.ifBlank{null});model="";alias=""},enabled=model.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("手动添加")};state.providerOperationMessage?.let{Text(it)}}
 }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProviderQuickTestScreen(state:AiUiState,providerId:String?,modelEntryId:String?,onBack:()->Unit,onRun:(String?,String?,AiProviderEntity?,CharArray)->Unit,onCancel:()->Unit){
 val saved=state.providers.firstOrNull{it.id==providerId};var base by rememberSaveable{mutableStateOf("")};var model by rememberSaveable{mutableStateOf("")};var key by remember{mutableStateOf("")};var mode by rememberSaveable{mutableStateOf(AiApiMode.CHAT_COMPLETIONS)};var confirm by rememberSaveable{mutableStateOf(false)}
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("开始 Quick Test？")},text={Text("将发送固定 ping，不含聊天内容；草稿配置和临时 Key 不会落盘。")},confirmButton={Button(onClick={confirm=false;val n=System.currentTimeMillis();val draft=if(saved==null)AiProviderEntity(displayName="临时草稿",baseUrl=base,modelId=model,createdAt=n,updatedAt=n,sortOrder=0,apiMode=mode)else null;onRun(providerId,modelEntryId,draft,key.toCharArray());key=""}){Text("确认联网")}},dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
 Scaffold(topBar={TopAppBar(title={Text("Provider Quick Test")},navigationIcon={Back(onBack)})}){p->Column(Modifier.fillMaxSize().padding(p).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(saved?.let{"${it.displayName} · ${it.endpointHost} · ${it.models.firstOrNull{x->x.id==modelEntryId}?.modelId?:it.modelId}"}?:"测试未保存草稿",style=MaterialTheme.typography.titleMedium)
  if(saved==null){OutlinedTextField(base,{base=it},label={Text("Base URL")},Modifier.fillMaxWidth());OutlinedTextField(model,{model=it},label={Text("模型 ID")},Modifier.fillMaxWidth());OutlinedTextField(key,{key=it},label={Text("临时 API Key")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(mode==AiApiMode.CHAT_COMPLETIONS,{mode=AiApiMode.CHAT_COMPLETIONS},{Text("Chat")});FilterChip(mode==AiApiMode.RESPONSES,{mode=AiApiMode.RESPONSES},{Text("Responses")})}}
  if(state.testStatus=="测试中…"){CircularProgressIndicator();OutlinedButton(onClick=onCancel){Text("取消")}}else Button(onClick={confirm=true},enabled=saved!=null||(base.isNotBlank()&&model.isNotBlank()&&key.isNotBlank()),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("运行固定 ping")}
  state.testStatus?.let{Text(it,color=if(it.contains("失败"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)};Text("响应正文不会保存；错误仅显示安全分类。",style=MaterialTheme.typography.bodySmall)
 }}
}

@Composable private fun Back(onBack:()->Unit)=IconButton(onClick=onBack,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
@Composable private fun SwitchRow(text:String,checked:Boolean,onChange:(Boolean)->Unit)=ListItem(headlineContent={Text(text)},trailingContent={Switch(checked,onChange)},modifier=Modifier.clickable{onChange(!checked)})

@Preview(name="Phone",widthDp=412,heightDp=892,showBackground=true) @Preview(name="Tablet",widthDp=840,heightDp=900,showBackground=true) @Composable private fun ProviderPreview(){WeChatNotifyEvoTheme(dynamicColor=false){ProviderListScreen(AiUiState(),{}, {}, {})}}
@Preview(name="Dark",widthDp=412,heightDp=892,showBackground=true,uiMode=0x20) @Composable private fun ProviderDarkPreview(){WeChatNotifyEvoTheme(dynamicColor=false){AddProviderChooseScreen({}, {})}}
