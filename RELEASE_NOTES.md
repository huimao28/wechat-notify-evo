# v1.1.0-beta.3

这是许可证迁移与多 Provider 配置的预发布版本，包含 v1.1.0-beta.2 和 beta.1 的全部功能。建议先在非关键设备、测试会话与 Android Auto DHU 中验证。

## GPLv3 与原创图标

- 项目整体切换为 **GPL-3.0-only**；源码、修改与再分发须遵守 GPLv3，完整许可证、NOTICE 与第三方声明随 APK 打包在 `assets/legal/`。
- “关于”页和 README 提供许可证与第三方声明入口。
- 删除早期来自微信 APK 的启动图标栅格资源，改用原创 WNE 矢量图标；不改写旧 Git 历史。

## OpenMinis 风格多 Provider

- 新增多实例 Provider 管理与总结时选择，内置 OpenAI、DeepSeek、OpenRouter，并支持自定义 OpenAI-compatible HTTPS endpoint。
- 每个 Provider 可单独配置名称、模型、启用状态和 API Key；API Key 使用 Android Keystore AES-GCM 加密，界面与诊断均不回显明文。
- 旧版单 Provider `AiConfig` 首次启动时幂等迁移到独立 Room 数据库；历史消息数据库保持独立，不破坏现有记录。
- Provider 密钥密文排除于 Android 备份；诊断 FileProvider 行为保持不变。

## 延续功能

- 保留 v1.1.0-beta.2 基于 AndroidX Car App `CarConnection` 的真实连接检测，区分 Android Auto 已安装与 `NOT_CONNECTED` / `NATIVE` / `PROJECTION` / `UNKNOWN` 实际状态。
- 保留 v1.1.0-beta.1 五 Tab 自适应界面、本地 Keystore 加密记录、会话筛选和隐私优先 AI 总结；默认不记录、不选会话、不自动联网。
- 保留 v1.0.20.1 气泡学习热修复（LauncherUI + Main_User 路由学习、重试、限流与超时）。

## 升级验证建议

1. 从 beta.2 覆盖安装，确认既有记录仍可读取。
2. 打开 AI 设置，确认旧 endpoint、模型和 Key 状态仅迁移一次，Key 不回显；分别新增并测试 OpenAI、DeepSeek、OpenRouter 和自定义 Provider。
3. 在总结页切换 Provider，预览原文并确认后执行总结。
4. 使用 DHU 连接/断开，确认诊断页 CarConnection 状态正确变化；再验证通知、气泡学习与诊断报告分享。
