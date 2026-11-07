# Fahrmony IM bridge behavior review

Reviewed source: `nexen33/Fahrmony`, tag `v1.3.0`, commit `b23fd66307fe0bbb098a53808beecd674740b143`.
The local `/tmp/fahrmony.apk` declares versionName `1.3.0` / versionCode `7`, matching that tag's `android/app/build.gradle` values. The repository is licensed CC BY-NC 4.0 (`LICENSE`). No Fahrmony implementation code is copied into WNE; WNE's implementation is independently structured from Android's public notification APIs and official Android Auto messaging guidance.

## Source evidence

- `android/app/src/main/java/com/fahrmony/app/nativebridge/FahrmonyNotificationListener.kt:20-34`: listener class, `fahrmony_im_bridge` channel ID/name, and target IM packages including WeChat.
- Same file `:161-206`: only while a car is connected and the app is enabled, parse the source notification and call `forwardToCarMessagingStyle`.
- Same file `:224-256`: map the parsed sender/body into a new local `MessagingStyle` notification; it does not retain or forward a source WeChat action.
- Same file `:258-302`: construct app-owned mark-read and reply actions. Mark-read uses semantic `MARK_AS_READ`, no UI and immutable `PendingIntent`; reply uses result key `extra_voice_reply`, semantic `REPLY`, no UI and mutable `PendingIntent`.
- Same file `:314-340`: publish the translated notification on `fahrmony_im_bridge` with a derived local notification ID.
- `android/app/src/main/java/com/fahrmony/app/nativebridge/FahrmonyMessageReceiver.kt:15-45`: `ACTION_MARK_AS_READ` only cancels the Fahrmony notification and logs it. `ACTION_REPLY` reads RemoteInput, logs the full reply body, then cancels the Fahrmony notification. There is no send to WeChat, no invocation of a WeChat PendingIntent/Receiver, and no local reply message repost.
- `android/app/src/main/AndroidManifest.xml:80-92`: notification listener registration.
- Same manifest `:124-133`: message receiver is `exported=false`.

## Conclusion and WNE boundary

Fahrmony's actions are AA-compliance shims, not real IM read/reply transport. WNE adopts only the general, public-platform idea of app-owned fallback actions, with stricter semantics: real notification-supplied WeChat actions win; fallback is opt-in and only fills missing actions; simulated reply is visibly labeled as not sent, is never logged/persisted/uploaded, and remains process-memory-only; simulated read only clears WNE local notification/conversation/shortcut state.
