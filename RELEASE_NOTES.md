# v1.1.0-beta.1

这是本地历史与隐私优先 AI 总结功能的预发布版本，建议先在非关键设备与测试会话中验证。

## 新增

- 五 Tab Material 3 Expressive 自适应界面：首页、记录、AI 总结、设置、关于。
- 本地加密消息记录默认关闭；仅在用户开启后保存由通知捕获到的消息，支持保留期、会话排除与一键清空。
- 标题、发送者、正文使用 Android Keystore AES-GCM 加密。本地记录来自通知捕获，可能缺失、折叠或无法覆盖历史消息，**不是完整聊天备份**。
- BYOK OpenAI-compatible Chat Completions 总结：由用户自行配置 HTTPS endpoint、model 与 API Key。
- API Key 使用独立 Android Keystore AES-GCM 密钥加密，不进入 Room、明文 SharedPreferences、日志或诊断报告。
- 默认不选择任何会话，也不会自动总结：必须由用户选择会话、预览将发送的原文，并在显示 endpoint host 与消息数量的对话框中二次确认；**仅用户确认后才联网**。
- 支持今天 / 3 天 / 7 天范围；最多 1000 条、50000 字符。总结结果与错误正文不持久化，**总结仅驻留内存**。
- 连接测试只发送固定 ping，不包含聊天内容，也不保存服务响应正文。

## 网络权限与限制

- 新增 `android.permission.INTERNET` 仅用于用户明确确认后的 BYOK AI 请求，以及用户主动发起的 endpoint 连接测试；本地记录功能本身不需要联网。
- 仅允许具有明确 host 的 HTTPS endpoint，并仅支持标准 HTTPS 443 端口；自定义端口不支持。
- 不支持流式响应（stream），禁用自动重定向（redirect）；拒绝 userinfo、query 与 fragment。
- 请求/响应不写日志；错误仅暴露安全状态，不显示服务端可能回显的原消息；响应上限 2 MB。
- 复制与系统分享会让内容进入剪贴板或外部应用，操作前会提示风险。
- AI 结果可能不准确；Prompt 要求不得编造，但仍需人工核对。

## 修复

- 包含 v1.0.20.1 气泡学习修复：恢复 LauncherUI + Main_User 路由学习，并保留自动重试、限流与超时策略。
