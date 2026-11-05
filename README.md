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
- 群聊进化、通知图标选择、监听服务诊断。
- **圆形头像兼容**：默认开启，把微信方形头像中心裁剪为透明圆形位图，并统一用于通知头像、
  私聊 Person、会话 shortcut 与气泡图标来源，以改善部分厂商的显示；关闭后使用微信原头像。
  输出最长边限制为 256 px，已较小头像不会放大；无法安全处理时自动回退原图。
- **可选后台保活**：默认关闭；启用低优先级前台服务（FGS）与 `JobScheduler`
  watchdog，断线时尝试重新绑定通知监听，只提高存活率、不保证不被系统终止。
- **用户空间隔离**：仅隔离具有明确不同 UserHandle 的标准独立 Android 用户或工作资料。
  ColorOS 同用户分身与主微信共享 UserHandle，无法可靠区分；请在系统中将主微信设为默认打开。
- **气泡内直达聊天**：一个正式设置同时管理系统气泡、自动学习与微信公开
  Launcher 直达入口。首次遇到新会话时可能短暂打开微信；学习有限频，失败不循环重试。
- 旧版三个独立开关会自动迁移：仅当“消息气泡”“自动学习”“直接 launcher”
  原先全部开启时，新总开关才开启；否则统一关闭，不影响通知进化、回复、群聊等核心功能。

## 气泡内直达聊天

开启时需先确认风险。应用对每个新会话最多学习一次，进程内至少间隔 60 秒、
每小时最多 6 次；忙碌或限频时跳过且不补发。学习成功后，系统会话 shortcut
使用微信公开且可导出的 `LauncherUI`，携带当前快照获得的 `Main_User` 尝试直达。

关闭时会先失效在途学习回调，再原子关闭三个兼容偏好，并清除内存学习结果、
禁用/移除相关 shortcut；已经发送给微信的入口无法撤回，已经打开的旧气泡可能仍需手动关闭。
手动学习、刷新 shortcut、清除学习结果等入口保留在“诊断与学习”中。

### 隐私边界

- App 不显示、记录日志或上传 `Main_User`，会话映射只保存在进程内存。
- 为让系统直接启动微信，`Main_User` 必须作为 shortcut Intent extra 交给 Android，
  因而会由 Launcher/系统保存在本机，不能视为完全不落盘。
- shortcut 使用随机快照代号；通知替换、移除、关闭功能、监听断开或进程重启后不复用旧学习。

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

## 已知限制（v1.0.19-beta.3）

- beta.2 已回退 beta.1 的严格过滤：用户空间隔离只拒绝通知来源中明确的非当前 Android 用户；系统/未知负值会按当前用户放行，
  `PendingIntent` 创建用户仅用于诊断、不参与拒绝。它只隔离标准独立 Android 用户或工作资料；
  ColorOS 同用户分身无法可靠区分，请在系统中将主微信设为默认打开。
- 可选 FGS + `JobScheduler` watchdog 只提高可靠性；省电策略、厂商后台限制、强行停止仍可使其失效。
- 首次遇到新会话时自动学习可能短暂打开微信并影响未读状态；系统也可能拦截后台启动。
- 每个新会话只尝试一次，5 秒无有效回调即放弃；限频或失败不会自动补学。
- 微信 `LauncherUI` 不可用时无法建立直达入口；shortcut 更新受系统限流。
- Android/桌面可能把微信聊天全屏打开，而不是嵌入悬浮气泡；显示气泡不等于已嵌入聊天。
- 直接回复与标为已读依赖微信提供 car-extender 数据；缺失时按钮不出现，但消息进化不受影响。
- 群聊判定采用“发送者：内容”启发式；会话归档只存在进程内存；不翻译表情占位文本。
- 正式签名与早期 debug 签名不兼容时无法覆盖安装，需用户自行备份并卸载旧版。

## 构建与发布

- 本地：Android Studio（minSdk 26、target/compileSdk 35、Java 17、Gradle 8.9）。
- CI：`.github/workflows/android.yml` 对分支运行 `testDebugUnitTest assembleDebug` 并上传 debug APK。
- Release：创建并推送 annotated tag `v*` 后，`.github/workflows/release.yml` 使用仓库 Secrets
  解码正式 keystore、运行 `assembleRelease`，并将 `app-release.apk` 发布到对应 GitHub Release。
- 仓库不带 `gradle-wrapper.jar`，CI 使用 `gradle/actions/setup-gradle` 安装 Gradle 8.9。

## 致谢与来源

设计参考 Nevolution 官方微信装饰器（`Nevolution/decorator-wechat`，Apache 2.0）的
会话归档、计数前缀、发送者切分和 car-extender 转发思路；本应用为独立重写。
