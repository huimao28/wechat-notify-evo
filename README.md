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

## 致谢与来源

设计参考了 Nevolution 官方微信装饰器
（`Nevolution/decorator-wechat`，Apache 2.0）里的会话归档、计数前缀
剥离、`": "` 发送者切分、car-extender 回复转发等思路，但本应用是
独立重写，不依赖 Nevolution SDK 与主应用。
