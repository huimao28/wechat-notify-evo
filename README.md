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

## 测试分支：Main_User 最小检查

`feature/custom-notification-icon` 保留现有消息气泡及 launcher 对照模式；
本次只扩展手动诊断，不改变气泡路由，不自动学习或收集会话标识。

- 在应用前台点击“诊断：检查 Main_User（三项布尔值）”，发送最新活跃会话的原始
  PendingIntent；仍使用 Activity context 和 Android 14+ BAL opt-in，可能打开微信。
- 仅在该次 `OnFinished` 回调中检查 `intent` 的 `Main_User`：是否存在、值是否为
  String、是否为非空字符串。只读取这一键，不枚举/读取其他字段，也不读取
  resultData/resultExtras。实际值仅用于回调内瞬时判断，不显示、不日志、不保存、不用于跳转。
- 三项结果只在当前界面内存中显示；诊断日志仍仅包含状态和耗时。
  缺键或 null intent 为 false/false/false；存在但为 null 或非 String 为 true/false/false；
  空 String 为 true/true/false；非空 String 为 true/true/true（空白字符串也算非空）。
  读取异常显示 `callback_arrived_main_user_check_failed`，不伪装成三个 false。
- 5 秒未收到回调仅提示 `callback_not_observed_within_5s`，不等于键不存在；晚到回调仍更新。
  `send_returned` 不覆盖已到达的回调结果。回调及三个 true 均不证明会话定位或气泡嵌入成功。

安装测试：从本分支 Android CI 的 `WeChatNotifyEvo-debug` artifact 取得 APK，
尝试覆盖已有测试版；如签名不兼容，请停止，不要为此直接卸载丢失数据。
确认通知使用权/通知权限，收到一条新的微信通知后，返回本应用并手动点击上述按钮。
若打开微信，手动返回本应用查看状态、耗时和三项布尔值；只反馈这几项，勿提供实际标识。
没有活跃会话时不会发送。测试后可检查原有气泡及 launcher 开关行为未改变。
本次构建为 debug 测试产物，不发布 Release、不改 main；新增检查的真机结果需另行验证。

## 致谢与来源

设计参考了 Nevolution 官方微信装饰器
（`Nevolution/decorator-wechat`，Apache 2.0）里的会话归档、计数前缀
剥离、`": "` 发送者切分、car-extender 回复转发等思路，但本应用是
独立重写，不依赖 Nevolution SDK 与主应用。
