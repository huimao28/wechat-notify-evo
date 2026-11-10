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
