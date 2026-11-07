微信通知进化 **v1.0.19** 正式版（正式签名 APK）。

## 新增与变更

- **Android Auto 会话展示**：进化通知现在声明 Android Auto 通知能力，并完整提供 `MessagingStyle`、消息 `Person`、`CATEGORY_MESSAGE`、时间、有效的微信原 `contentIntent`、群聊标题/标记及长效会话 shortcut。
- **安全动作桥接**：优先读取微信原通知公开的标准 `Notification.Action` + `RemoteInput`，其次读取平台 `CarExtender` unread conversation；回复的 `resultKey` 和微信原 `PendingIntent` 原样复用。回复/已读取决于微信原通知是否提供入口；没有入口时 Android Auto 只展示会话，不显示动作，也不调用微信未导出 Receiver。
- 仅在存在合法回复或已读入口时补充平台 `CarExtender` 兼容数据；参与者、消息和时间均来自当前进化会话。
- 合并“关于/致谢”页面，展示版本、项目地址、许可现状与准确致谢。当前仓库没有 LICENSE 文件，页面如实说明未提供项目许可文本。
- 保留 v1.0.19 beta 系列的圆形头像兼容、标准 Android 用户空间隔离、可选后台保活与气泡内直达聊天。

## 行为与安全边界

- Android Auto 通知点击只使用微信原通知的 `contentIntent`；缺少该入口时不替换原通知。
- 车载适配不会额外触发自动气泡学习或主动打开微信。现有“气泡内直达聊天”开关与通知到达触发逻辑保持不变；开启时首次新会话仍可能按既有限频策略短暂打开微信，关闭时不会学习。
- 不新增车载开关，不引入 AndroidX Core 或 Car App Library；实现只使用公开平台通知 API。
- 回复/已读入口、Android Auto 最终展示及语音回复能力仍取决于微信版本、Android Auto 与车机实现。

## 安装与测试

下载 `app-release.apk`，授予通知使用权（Android 13+ 还需通知权限）。建议分别测试 Android Auto 有线和无线连接：有合法回复入口时验证语音回复与已读；无入口时预期只展示会话。若设备上安装的是早期 debug 签名版，签名不同将无法覆盖安装。
