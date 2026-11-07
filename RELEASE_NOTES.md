微信通知进化 **v1.0.20-beta.1** 预发行版（正式签名 APK）。

## 新增与变更

- **Android Auto 兼容动作（模拟）**：新增一个**默认关闭**的实验开关。仅在微信原通知缺少对应入口时，为 Android Auto 补充 WNE 自身的回复/已读动作；微信公开提供的标准 `Notification.Action` / `RemoteInput` / `CarExtender` **真实入口始终优先**且行为不变。
- **模拟回复不发送微信**：只在 WNE 当前进程的通知历史中显示带有“仅车机本地回显，未发送微信”标识的回复；不调用微信 Receiver 或 `PendingIntent`，正文不写日志、不持久化、不上传，进程结束即消失。
- **模拟已读不改变微信状态**：只清除 WNE 本地进化通知、内存会话与 shortcut，不会改变微信未读状态或服务器状态。
- 完整保留 v1.0.19 的 Android Auto 会话展示、关于/致谢页面，以及既有可选后台保活、圆形头像兼容、Android 用户空间隔离和气泡内直达聊天能力。

## 边界与兼容性

- 关闭兼容动作时不补任何模拟动作；既有真实回复、真实已读和通知点击入口不受影响。
- Android Auto 开发者设置中的“未知来源”开关，以及 Google Play 安装/所有权与 Android Auto 对侧载应用的信任过滤，仍是**彼此独立**的限制；此功能不能绕过它们。
- Android Auto 最终是否显示消息、回复或已读动作，仍取决于微信版本、Android Auto、车机实现与连接方式。

## 开源参考致谢

感谢 [Fahrmony](https://github.com/nexen33/Fahrmony) 提供开源参考。WNE 仅依据其 CC BY-NC 4.0 项目进行兼容行为研究，没有复制 Fahrmony 代码；本功能独立基于 Android 公开通知 API 实现。此致谢不表示 Fahrmony 作者或贡献者对 WNE 背书。

## 安装与测试

下载 `app-release.apk`，授予通知使用权（Android 13+ 还需通知权限）。建议先在 Android Auto Desktop Head Unit（DHU）验证默认关闭状态，再分别测试真实入口优先、缺失入口时的模拟回复本地回显和模拟已读本地清理；同时覆盖有线/无线连接及“未知来源”/Play ownership 不同条件。若设备安装的是早期 debug 签名版，签名不同将无法覆盖安装。
