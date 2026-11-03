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

## 权限

- **通知使用权**：监听和改写微信通知。
- **发送通知权限**（Android 13+）：显示进化版通知。
- 系统还必须允许本应用显示气泡；最终是否显示、是否在气泡窗口内承载微信界面，
  由 Android、系统桌面和微信版本共同决定。

## 已知限制（v1.0.18）

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
