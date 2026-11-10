# v1.1.0-beta.2

这是 Android Auto 官方连接状态检测与 v1.1 全部记录/AI 功能的预发布版本，建议先在非关键设备、测试会话与 DHU 中验证。

## Android Auto 真实连接检测

- 使用 AndroidX Car App 官方 `CarConnection` 实时报告 `NOT_CONNECTED`、`NATIVE`、`PROJECTION`、`UNKNOWN`。
- Android Auto 包的安装/启用状态与实际车载连接状态分开展示：安装 Android Auto 不再被误判为已连接。
- 状态观察绑定界面生命周期并安全释放，忽略重复状态更新，避免泄漏与无效刷新。
- Android Auto Desktop Head Unit（DHU）建立投影连接后，诊断页应显示 `PROJECTION`；断开后应回到 `NOT_CONNECTED`。

## 包含 v1.1.0-beta.1 全部功能

- 五 Tab Material 3 Expressive 自适应界面：首页、记录、AI 总结、设置、关于。
- 本地加密消息记录默认关闭；仅保存开启后由通知捕获到的消息，支持保留期、会话排除与一键清空。通知捕获可能缺失或折叠，**不是完整聊天备份**。
- 标题、发送者、正文使用 Android Keystore AES-GCM 加密。
- 隐私优先 BYOK OpenAI-compatible AI 总结：API Key 独立加密；默认不选会话、不自动总结；仅在用户预览原文并二次确认后联网。
- 支持今天 / 3 天 / 7 天范围，最多 1000 条、50000 字符；总结结果与错误正文不持久化，仅驻留内存。
- endpoint 仅允许具有明确 host 的 HTTPS 443；不支持 stream、redirect、自定义端口、userinfo、query 或 fragment；响应上限 2 MB。

## 修复与兼容性

- 包含 v1.0.20.1 气泡学习修复：恢复 LauncherUI + Main_User 路由学习，并保留自动重试、限流与超时策略。
- Android Auto 标准消息通知及可选模拟兼容动作行为保持不变；真实微信回复/已读入口仍优先。
