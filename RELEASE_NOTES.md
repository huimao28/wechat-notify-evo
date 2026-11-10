# v1.1.0-beta.4

这是 v1.1.0-beta.3 的图标修正版，**包含 beta.3 的全部功能**；应用代码、通知 `smallIcon` 与运行时圆形头像逻辑均未改变。

## 唯一功能性变更：恢复原应用桌面图标

- 将 launcher 图标资源及 adaptive icon 引用逐字节恢复为 `v1.1.0-beta.2` 的版本，包括 `wechat_launcher_foreground.webp`、`wechat_launcher_background.webp`、`ic_launcher.xml`、`ic_launcher_round.xml` 与相关前景 XML。
- 移除 beta.3 新增的通用 WNE launcher 背景资源；不改动通知栏小图标、会话头像或气泡头像逻辑。

## 版权与许可边界

- 项目其余代码与 GPL 覆盖材料继续采用 **GPL-3.0-only**。
- 恢复的微信 launcher 图标/商标素材不按 GPL 授权，权利归腾讯和/或相关权利人；仅用于兼容性识别。
- 本项目不声称获得腾讯或其他权利人的授权、关联、赞助或认可。使用者与再分发者应自行确认用途并取得所需许可。
- 完整边界说明见 APK 内及仓库中的 `NOTICE`、`THIRD_PARTY_NOTICES.md` 与 `LICENSE`。

## 完整继承 beta.3

- GPL-3.0-only 项目迁移及 APK 内法律文件。
- OpenMinis 风格多实例 Provider 管理、Keystore 加密密钥、旧配置幂等迁移与 Provider 选择总结。
- beta.2 CarConnection 真实连接检测、beta.1 本地记录/隐私优先 AI 总结与五 Tab 界面。
- v1.0.20.1 气泡学习热修复。
