# v1.1.0-beta.1（测试草案）

> 仅供 feature/local-history-ai-summary 真机测试；尚未发布、未打标签、未合并 main。

## 新增

- 五标签自适应界面：首页、记录、AI 总结、设置、关于。
- 本地历史记录默认关闭；开启后标题、发送者、正文均使用 Android Keystore AES-GCM 加密，支持保留期、会话排除与清空。
- BYOK OpenAI-compatible Chat Completions 总结：用户配置 HTTPS endpoint、model 与 API Key。
- AI 总结必须手动选择会话、预览原文并在显示 endpoint host 和数量的对话框中再次确认，才会联网。
- 今天 / 3 天 / 7 天范围；最多 1000 条、50000 字符；总结、错误正文均不持久化。
- 连接测试只发送固定 ping，不包含聊天内容，也不保存服务响应正文。
- API Key 使用独立 Android Keystore AES-GCM 密钥加密，不进入 Room、明文 SharedPreferences、日志或诊断报告。

## 安全边界

- 只允许 HTTPS、明确 host、默认 443；拒绝 userinfo/query/fragment，禁用自动重定向。
- 请求/响应不记录；错误只暴露安全状态，不显示服务端可能回显的原消息；响应上限 2MB。
- 复制与系统分享前提示剪贴板/外部应用风险。
- 本地记录来自通知捕获，可能缺失、被折叠或无法覆盖历史消息；不是完整聊天备份。
- AI 结果可能不准确；Prompt 要求不得编造，但仍需人工核对。

## 修复

- 合并 v1.0.20.1 气泡学习热修复：恢复 LauncherUI + Main_User 路由学习，并保留自动重试、限流与超时策略。
