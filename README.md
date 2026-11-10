# 微信通知进化（独立版）

不依赖 Nevolution 主应用的微信通知增强工具。本应用直接通过
`NotificationListenerService` 监听微信通知，并以 `MessagingStyle` 重发。

## 工作原理

1. 监听微信（`com.tencent.mm`）通知。
2. 优先读取通知自带的 `CarExtender` 会话数据，没有时解析标题与正文。
3. 取消微信原通知，按私聊、群聊和服务号分渠道重发进化版通知。
4. 点击进化版通知仍使用微信原生 `contentIntent`；微信移除原通知时同步清理。

## 功能

- 多条消息聚合为 MessagingStyle 会话。
- 在微信提供入口时支持直接回复和标为已读。
- **Android Auto 会话展示**：进化通知声明通知型车载能力，并提供标准 `MessagingStyle`、`Person`、会话 shortcut 与消息分类。微信原通知提供的标准 `Notification.Action`/`RemoteInput` 或 `CarExtender` 入口始终优先。
- **Android Auto 兼容动作（模拟）**：默认关闭；开启后只为缺失项补 WNE 自身动作，以满足车机消息 UI 对 reply/read action 的展示要求。模拟已读只清除 WNE 本地进化通知、内存会话与 shortcut；模拟回复只在当前进程的通知历史中加入 `【仅车机本地回显，未发送微信】我：…`，绝不调用微信内部 Receiver，也不会发送到微信。回复正文不写日志、不持久化、不上传，进程结束即消失。
- 群聊进化、通知图标选择、监听服务诊断。
- **隐私安全诊断中心**：汇总应用/系统版本、通知权限、监听状态、保活与气泡配置，提供按厂商区分的后台/气泡操作引导，以及一键重新绑定通知监听和通知使用权、通知、气泡、电池优化、应用详情等系统设置入口。可预览、复制或分享 Markdown 脱敏报告；最近 20 条通知处理事件仅保存在进程内存，重启即清空。
- **Material 3 Expressive UI**：设置、关于和诊断使用统一动态色、深浅色、Expressive 形状与层级；手机单列、宽屏自适应双列，并改善大字体与 TalkBack 体验。
- **检测状态与预测式返回**：诊断真实异步刷新显示轻量“正在检测…”与完成摘要；设置到关于/诊断支持随手势进度变化的官方 NavigationEvent 预测式返回。
- **稳定、不可逆的会话 ID**：使用应用私有随机盐与 HMAC 生成不暴露 `Main_User` 的稳定 shortcut ID；Android 11+ 显式发布 conversation channel，符合条件的消息默认进入系统“对话”分类。覆盖更新、进程重启和后续通知会保留用户为同一会话设置的优先/默认/静音与气泡状态；应用不能替用户设为“优先对话”。
- **圆形头像兼容**：默认开启，把微信方形头像中心裁剪为透明圆形位图，并统一用于通知头像、
  私聊 Person、会话 shortcut 与气泡图标来源，以改善部分厂商的显示；关闭后使用微信原头像。
  输出最长边限制为 256 px，已较小头像不会放大；无法安全处理时自动回退原图。
- **可选后台保活**：默认关闭；启用低优先级前台服务（FGS）与 `JobScheduler`
  watchdog，断线时尝试重新绑定通知监听，只提高存活率、不保证不被系统终止。
- **用户空间隔离**：仅隔离具有明确不同 UserHandle 的标准独立 Android 用户或工作资料。
  ColorOS 同用户分身与主微信共享 UserHandle，无法可靠区分；请在系统中将主微信设为默认打开。
- **气泡内直达聊天**：一个正式设置同时管理系统气泡、自动学习与微信公开
  Launcher 直达入口。荣耀/MagicOS 上开启时只保存状态，不再重发旧通知；从下一条新消息起生效。
  首次遇到尚未学习的新会话时仍可能短暂打开微信；学习有限频，失败不循环重试。
- 旧版三个独立开关会自动迁移：仅当“消息气泡”“自动学习”“直接 launcher”
  原先全部开启时，新总开关才开启；否则统一关闭，不影响通知进化、回复、群聊等核心功能。

## 气泡内直达聊天

开启时需先确认风险。确认开启只保存开关状态，不刷新或重发已有通知，因此不会因给旧通知
追加气泡元数据而在荣耀/MagicOS 上触发自动展开；功能从下一条微信新消息起生效。应用对每个
新会话最多学习一次，进程内至少间隔 60 秒、每小时最多 6 次；忙碌或限频时跳过且不补发。
学习成功后，系统会话 shortcut 使用微信公开且可导出的 `LauncherUI`，携带当前快照获得的
`Main_User` 尝试直达。尚未学习的新会话仍可能因自动学习而短暂打开微信并影响未读状态。

关闭时会先失效在途学习回调，再原子关闭三个兼容偏好，清除内存学习结果并禁用/移除相关
shortcut；同时取消当前进化通知而不重发，以避免厂商系统触发旧气泡。已经发送给微信的入口
无法撤回；请等待下一条新消息重新生成不带直达气泡的进化通知。
手动学习、刷新 shortcut、清除学习结果等入口保留在“诊断与学习”中。

### 隐私边界

- 诊断报告不含聊天正文、联系人、`Main_User`、通知 key、Intent extras 或凭据；最近通知处理事件只记录时间、来源用户编号和处理阶段枚举，仅保存在进程内存，进程重启即清空。
- App 不显示、记录日志或上传 `Main_User`，会话映射只保存在进程内存。
- 为让系统直接启动微信，`Main_User` 必须作为 shortcut Intent extra 交给 Android，
  因而会由 Launcher/系统保存在本机，不能视为完全不落盘。
- shortcut 会话 ID 由应用私有随机盐、包/用户范围与 `Main_User` 计算稳定的 HMAC 截断值，不能反推出原值；卸载重装会删除私有盐并重置本机会话身份。

## 后台可靠性

“前台保活（可选）”默认关闭。开启后会显示常驻低优先级通知，并使用 FGS +
`JobScheduler` watchdog 做周期健康检查、任务移除恢复及通知监听断线重绑定。Android 与厂商
仍可延迟调度或终止进程，因此它不是永久保活保证。ColorOS、MagicOS 等厂商系统还需用户
手动允许本应用自启动、后台运行，并把电池策略设为“不优化”；应用不会自动申请白名单。

## 权限

- **通知使用权**：监听和改写微信通知。
- **发送通知权限**（Android 13+）：显示进化版通知。
- 系统还必须允许本应用显示气泡；最终是否显示、是否在气泡窗口内承载微信界面，
  由 Android、系统桌面和微信版本共同决定。

## 已知限制（v1.0.20）

- 模拟回复只在 WNE 当前进程本地回显，**不会发送到微信**；模拟已读只清理 WNE 本地通知、内存会话与 shortcut，**不会改变微信未读状态**。
- 从旧随机 ID 首次迁移到稳定会话 ID 时，Android 无公开 API 复制用户锁定设置，既有会话可能需要最后重设一次优先/默认/静音或气泡；应用不能替用户设为优先。完成迁移后覆盖更新会保留设置；卸载重装会重置本机盐与会话身份。
- 用户空间隔离只拒绝通知来源中明确的非当前 Android 用户；系统/未知负值会按当前用户放行，`PendingIntent` 创建用户仅用于诊断、不参与拒绝。它只隔离标准独立 Android 用户或工作资料；ColorOS 主微信与同一 `UserHandle` 的应用分身无法可靠区分，请在系统中将主微信设为默认打开。
- 厂商气泡与保活行为不作保证：可选 FGS + `JobScheduler` watchdog 只提高可靠性；省电策略、厂商后台限制、系统桌面、微信版本或强行停止仍可能使气泡、直达聊天或保活失效。
- 首次遇到新会话时自动学习可能短暂打开微信并影响未读状态；系统也可能拦截后台启动。
- 每个新会话只尝试一次，5 秒无有效回调即放弃；限频或失败不会自动补学。
- 微信 `LauncherUI` 不可用时无法建立直达入口；shortcut 更新受系统限流。
- Android/桌面可能把微信聊天全屏打开，而不是嵌入悬浮气泡；显示气泡不等于已嵌入聊天。
- “Android Auto 兼容动作（模拟）”只补缺失动作，不会把模拟结果同步到微信服务器或改变微信未读状态；关闭时不补动作，既有真实入口行为不变。
- Android Auto 开发者设置中的“未知来源”仍须开启；Google Play 安装/所有权及 Android Auto 自身的侧载信任过滤是独立限制，本功能不能绕过。
- 群聊判定采用“发送者：内容”启发式；会话归档只存在进程内存；不翻译表情占位文本。
- 正式签名与早期 debug 签名不兼容时无法覆盖安装，需用户自行备份并卸载旧版。

## 构建与发布

- 本地：Android Studio（minSdk 26、target/compileSdk 35、Java 17、Gradle 8.9）。
- CI：`.github/workflows/android.yml` 对分支运行 `testDebugUnitTest assembleDebug` 并上传 debug APK。
- Release：先确认标签不存在；仅在 main 的 `testDebugUnitTest assembleDebug` 成功后创建并推送 annotated tag `v*`。`.github/workflows/release.yml` 使用仓库 Secrets 解码正式 keystore、运行 `assembleRelease`，并将 `app-release.apk` 发布到对应非 Draft、非 Pre-release 的 GitHub Release，同时设为 latest。
- 仓库不带 `gradle-wrapper.jar`，CI 使用 `gradle/actions/setup-gradle` 安装 Gradle 8.9。

## 致谢与来源

- 感谢 Oasis Feng 开发 [Nevolution](https://github.com/Nevolution)（中文名“女娲石·通知进化”），
  以及由此推动的通知进化理念与生态。“女娲石”在此指 Nevolution，并非另一位个人。
- 感谢 Nevolution 官方[微信通知进化实现/插件](https://github.com/Nevolution/decorator-wechat)
  （Apache 2.0）在会话归档、计数前缀、发送者切分和 car-extender 转发等方面带来的探索与启发；
  本应用为独立重写。
- 感谢 ichenhe 的 [QQ-Notify-Evolution（企鹅通知进化）](https://github.com/ichenhe/QQ-Notify-Evolution)
  带来的开源参考。
- 感谢 [Fahrmony](https://github.com/nexen33/Fahrmony) 的开源参考；其 CC BY-NC 4.0 项目仅用于兼容行为研究，WNE 未复制其代码并独立基于 Android 公开 API 实现。本致谢不表示 Fahrmony 作者对 WNE 背书。
- 感谢参与测试、反馈、逆向排查与兼容验证的社区用户和贡献者。

以上致谢仅说明灵感、参考与社区帮助，不代表相关作者或贡献者对本项目背书。

## v1.1.0-beta.1 Pre-release：本地历史与 AI 总结

- 五 Tab Material 3 Expressive 界面：首页、记录、AI 总结、设置、关于。
- 本地加密消息记录**默认关闭**，仅保存开启后由通知捕获到的微信消息；支持保留期、会话排除与清空。通知捕获可能缺失或折叠，**不是完整聊天备份**。
- AI 使用用户自备的 OpenAI-compatible HTTPS endpoint、model 与 API Key（BYOK）；API Key 由 Android Keystore AES-GCM 加密保护。
- 默认不选会话且不会自动总结：用户必须选择会话、预览原文并二次确认；**仅用户确认后才联网**，总结仅驻留内存。
- 新增 `INTERNET` 权限仅用于上述用户确认后的 AI 请求和用户主动连接测试。仅支持 HTTPS 443，不支持 stream、redirect 或自定义端口。
- 包含 v1.0.20.1 气泡学习修复。复制或分享会使内容进入剪贴板或外部应用，请自行评估风险。
