# v1.1.0-beta.7

这是完整的 Minis 风格 Provider 重写预发布版，可从 beta.6 直接覆盖安装；beta.6 的 Navigation Tab 崩溃与 Provider/API Key 保存热修复继续保留。

## Provider 重写

- 独立的 Provider 列表、创建向导、详情、模型管理与 Quick Test 页面。
- 多 Provider、多模型使用稳定 ID，切换、保存与默认选择更可靠。
- OpenAI-compatible 同时支持 Chat Completions API 与 Responses API。
- Base URL 可配置，并可选择自动追加 `/v1`；支持主动刷新远端模型列表。
- 旧单 Provider 配置与 API Key 无损、幂等迁移；Key 继续由 Android Keystore 加密且不回显。
- 保留 beta.6 的五 Tab Navigation 修复、Room 异步保存状态与 Key 擦除修复。

## 真实支持边界

- 仅支持 **OpenAI-compatible** HTTP API（Chat Completions / Responses）。
- 不支持 Gemini native API、Anthropic native API 或 OAuth 登录；相关服务仅在提供 OpenAI-compatible 端点时可用。

## 版权与许可

- Provider 配置交互与仓储设计参考并改编自 **OpenMinis**，源码内保留对应声明。
- 项目代码与 GPL 覆盖材料采用 **GPL-3.0-only**；APK 内含 `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES.md`。
- 微信 launcher 图标/商标素材不按 GPL 授权，权利归腾讯和/或相关权利人，仅用于兼容识别；不声称授权、关联或认可。

---

# v1.1.0-beta.6

这是 v1.1.0-beta.5 的 Navigation 与 Provider/Key 热修复版，支持直接覆盖安装，**包含 beta.5 的全部 Provider、GPL 法律文件与微信 launcher 图标恢复功能**。

## 热修复

- 修复 Navigation 3 切换 Tab 时空 back stack 导致的崩溃。
- 修复 Provider/API Key 通过 Room 在主线程保存失败。
- Provider 操作明确展示运行中、成功与错误状态。
- 修复 Key 擦除后被旧状态恢复的问题；API Key 不进入 Compose `SavedState`。

## 完整继承 beta.5

- 保留 OpenMinis 风格多实例 Provider 管理、Keystore 加密密钥、旧配置幂等迁移与 Provider 选择总结。
- 保留 GPL-3.0-only 项目迁移及 APK 内 `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES.md` 法律文件。
- 保留 beta.4 恢复的微信 launcher 图标及 adaptive icon 引用；不改通知 `smallIcon`、会话头像或气泡头像逻辑。
- 继续包含 beta.2 CarConnection 真实连接检测、beta.1 本地记录/隐私优先 AI 总结与五 Tab 界面，以及 v1.0.20.1 气泡学习热修复。

## 版权与许可边界

- 项目代码与 GPL 覆盖材料继续采用 **GPL-3.0-only**。
- 恢复的微信 launcher 图标/商标素材不按 GPL 授权，权利归腾讯和/或相关权利人；仅用于兼容性识别。
- 本项目不声称获得腾讯或其他权利人的授权、关联、赞助或认可。完整边界说明见 APK 内及仓库文件。

---

# v1.1.0-beta.5

这是 Provider 新增/编辑流程修正版，**包含 beta.4 的微信 launcher 图标恢复，以及 beta.3 的全部多 Provider 与 GPL 功能**。

## Provider 编辑器修复

- 修复点击“新增 Provider”按钮无响应的问题。
- 编辑器明确区分 `Create`、`Edit`、`Closed` 状态，新增“取消”“保存”“保存并设为默认”操作。
- 配置存在未保存修改时禁用连接测试，避免测试内容与已保存配置不一致。
- API Key 不进入 Compose `SavedState`，降低敏感信息被状态持久化的风险。

## 完整继承 beta.4 / beta.3

- 保留 beta.4 恢复的原微信 launcher 图标及 adaptive icon 引用；不改通知 `smallIcon`、会话头像或气泡头像逻辑。
- 保留 beta.3 的 OpenMinis 风格多实例 Provider 管理、Keystore 加密密钥、旧配置幂等迁移与 Provider 选择总结。
- 保留 GPL-3.0-only 项目迁移及 APK 内 `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES.md` 法律文件。
- 继续包含 beta.2 CarConnection 真实连接检测、beta.1 本地记录/隐私优先 AI 总结与五 Tab 界面，以及 v1.0.20.1 气泡学习热修复。

## 版权与许可边界

- 项目代码与 GPL 覆盖材料继续采用 **GPL-3.0-only**。
- 恢复的微信 launcher 图标/商标素材不按 GPL 授权，权利归腾讯和/或相关权利人；仅用于兼容性识别。
- 本项目不声称获得腾讯或其他权利人的授权、关联、赞助或认可。使用者与再分发者应自行确认用途并取得所需许可。
- 完整边界说明见 APK 内及仓库中的 `NOTICE`、`THIRD_PARTY_NOTICES.md` 与 `LICENSE`。
