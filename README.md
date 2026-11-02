# 微信通知进化（独立版）

不依赖 Nevolution 主应用的微信通知进化工具。背景：Nevolution 主应用的
`EnhancedNotificationListenerService` 在 Android 17 上 Binder 转发空指针，
导致通知到不了微信装饰器插件，整个进化链路失效。本应用自己就是
`NotificationListenerService`，彻底绕开那条已死的链路。

## 工作原理

1. 监听微信（`com.tencent.mm`）发出的通知。
2. 按通知 id 归档会话：优先用通知自带的 `CarExtender` 未读会话
   （权威消息列表 + 回复/已读 PendingIntent），没有则解析标题与正文。
3. 取消微信原通知，用 framework `Notification.Builder` + `MessagingStyle`
   以本应用自己的渠道重发，私聊 / 群聊 / 服务号分渠道、分组；
   每组一条自己的 summary（只让子通知响铃），且只有当该组未读
   总数达到 100 条时才出现，不满则只显示各会话自己的通知。
4. 点开进化版通知走微信原生的 contentIntent；微信随后取消原通知时，
   本应用同步取消进化版并清归档。

## 权限用途

- **通知使用权**：监听微信通知的唯一途径。没有它应用什么都不做。
- **发送通知权限**（Android 13+）：发出进化版通知。

## 功能

- 多消息会话：同一会话的多条消息聚合为 MessagingStyle 对话。
- 会话优先级（Android 11+）：进化版通知以系统会话发布（长驻快捷
  方式 + Person），长按通知或在系统设置里可按会话调优先 / 默认 /
  静音；气泡暂不做。
- 直接回复：通知上的“回复”按钮把输入转发给微信原生的回复入口
  （car-extender reply PendingIntent）。
- 标为已读：有 read PendingIntent 时提供该动作，点后通知微信已读并
  清除进化版通知。
- 开关：总开关、直接回复开关、群聊进化开关，以及“重新绑定监听服务”
  （系统升级后监听绑定假死时用）。

## 刻意不做的事

- 杂项通知（无 ticker，如网页登录确认）、进行中的通知（VoIP 等）
  原样不动，不进化也不取消。
- `REASON_LISTENER_CANCEL` 的移除回调会被忽略：那是我们自己取消
  原通知触发的，不能因此清归档。

## 已知限制（v1）

- 直接回复与标为已读依赖微信是否在通知里提供 car-extender 数据；
  如果微信某版本不再提供，这两项就不会出现，但消息进化本身不受影响。
- 群聊判定是启发式：正文里“发送者：内容”的发送者前缀不等于标题
  时判定为群聊；服务号按固定标题表（服务通知、订阅号消息、微信团队、
  微信支付）归类。
- 会话归档只存在进程内存里，应用进程被杀后历史从下一条新通知重建。
- v1 不做表情翻译（`[动画表情]` 这类原文照常显示）和气泡。

## 构建

- 本地：Android Studio 打开即可构建（minSdk 26、targetSdk 34、
  compileSdk 34、AGP 8.7.3、Gradle 8.9、Compose BOM 2024.10.01）。
- CI：仓库自带 `.github/workflows/android.yml`，用
  `gradle/actions/setup-gradle` 直接跑 `gradle assembleDebug` 并上传 APK
  artifact。本仓库故意不带 `gradle-wrapper.jar`，不要用 `./gradlew`。

## 测试分支：手动学习当前会话后直接 launcher

`feature/custom-notification-icon`：用户已确认 OnFinished 中 Main_User 为非空 String；
这只证明可取值，不证明路由或气泡内嵌成功。本实现仍需真机验收。

### 操作与路由

1. 收到微信通知，打开本应用。点击“手动学习会话（会打开微信）”，再选择具体会话。
   仅这次明确手势发送一次该通知的原始 PendingIntent；无通知不发送，不随每条消息自动发送。
   使用 Activity context、Android 14+ sender BAL opt-in 和公开 API。
2. OnFinished 只读取 Main_User，并要求非空 String；不读取 resultData/resultExtras。
   回调、会话对象、通知 key、通知代号及当前 PI 均匹配才接受；5 秒超时、失效或迟到回调丢弃。
3. 手动返回本应用，开启“实验：直接 launcher 对照”，点击“刷新当前通知 / shortcut”。
   已学习时使用导出、启用且不受权限保护的 `com.tencent.mm.ui.LauncherUI`，
   参数 `Main_User=<所学值>`、`Intro_Is_Muti_Talker=false`（参照 Nevolution）。
   无学习结果时保留原首页 launcher 对照；关闭开关恢复旧中转；LauncherUI 不可用则回退中转。
4. 状态区区分已学习待刷新、发布成功待验证、系统限流及更新失败。
   限流时不挂接参数不匹配的旧 shortcut，不循环重试、不重发 PI；稍后手动刷新。
   关闭旧气泡，再从当前通知打开新气泡，确认是否是所选会话，以及是否确实在气泡内。

### 隐私与防误绑边界

- App 不显示、记录日志或上传 Main_User，不写数据库/偏好文件。会话映射只在进程内存。
  **为让系统直接启动微信，Main_User 必须作为 shortcut Intent extra 交给 Android，
  因而由系统在本机保存，不能声称整个方案完全不落盘。** Launcher/系统可访问快捷方式。
- 保守选择：**每个新通知快照都会使学习失效，即便 nid、标题、key 相同也不复用**。
  需在当前快照重新手动学习；优先用“刷新”测试，不要主动发新消息作为刷新方法。
  若打开微信后原通知被微信移除，学习会被拒绝/失效，等待新通知重新学习，绝不猜测关联。
- shortcut ID 使用随机快照代号，不以 nid 或标题复用。旧中转也核对快照代号，
  防止旧气泡因 nid 复用转发到新会话。代价是会话级系统偏好/旧气泡不保证跨快照延续，
  高频消息可能触发系统 shortcut 限流；基础消息通知保持工作。
- 通知移除、替换或“清除学习结果”会请求禁用并删除旧本地 shortcut；
  启动、监听重连及刷新会清理无内存对应项的 shortcut。进程重启后不恢复学习。
  系统清理失败时只记录通用状态并在后续刷新重试；已经打开的旧气泡仍应手动关闭。
  直接交给系统的 Intent 无法由本应用在每次点击时重新拦截验证。

### 验收矩阵

- 分别单聊、群聊学习并刷新：确认开的是所选会话，不只是首页；区分全屏与气泡内嵌。
- 不学习时直接 launcher 仍是首页；关闭该模式仍走原通知中转。
- 无通知、取消选择、PI 失效、缺键/非 String/空值、5 秒超时均不生成学习绑定。
- 学习途中更新/移除通知，或先后出现同标题会话：旧回调不得绑定新快照。
- 学习后收到新消息、清空通知、清除学习或重启进程：旧学习不复用。
- 限流/发布失败后不误用旧参数；稍后仅刷新即可重试当前仍有效的学习结果。
- CI 执行 `testDebugUnitTest assembleDebug`，覆盖快照代际、key、对象身份、
  同标题 nid 复用、删除和进程内存清空。回调/系统限流/UI 行为仍需真机测试。

仅构建 debug，不发布 Release、不改 main。覆盖安装若签名冲突则停止，不要直接卸载丢失数据。
反馈模式、状态文字、是否正确会话及是否气泡内嵌即可，不要反馈实际 Main_User。

## 致谢与来源

设计参考了 Nevolution 官方微信装饰器
（`Nevolution/decorator-wechat`，Apache 2.0）里的会话归档、计数前缀
剥离、`": "` 发送者切分、car-extender 回复转发等思路，但本应用是
独立重写，不依赖 Nevolution SDK 与主应用。
