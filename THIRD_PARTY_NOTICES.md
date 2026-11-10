# Third-party notices and provenance

This file distinguishes bundled dependencies/material from research references. It is not a substitute for the license texts shipped by dependency authors.

## Runtime and build dependencies

WNE uses Android SDK/Gradle/Kotlin and Maven artifacts including AndroidX (Activity, Car App, Compose, Core, Lifecycle, Navigation 3, Navigation Event, Room), Material icons, Square OkHttp/MockWebServer, JUnit, KSP, and the Android Gradle Plugin. These are separately licensed by their copyright holders, predominantly under Apache License 2.0; JUnit 4 is licensed under Eclipse Public License 1.0. Transitive dependencies may carry additional compatible notices. Gradle/Maven metadata and packaged `META-INF` notices are authoritative for the resolved version. WNE does not relicense those components under GPL-3.0-only; GPLv3 section 7 and each component's own terms continue to apply.

Before a release, generate/review the resolved dependency graph (`./gradlew :app:dependencies`) and retain upstream `META-INF` license/notice resources. Do not strip dependency notices during packaging.

## Research and acknowledgements — no copied code

- **Nevolution / decorator-wechat**, Oasis Feng and contributors. Studied as prior art and for notification-evolution concepts/behavior. WNE is a standalone implementation using public Android APIs; the audit found no copied Nevolution source or reverse-engineered implementation incorporated into WNE.
- **QQ-Notify-Evolution**, ichenhe and contributors. Consulted as a reference for project behavior/configuration. The audit found no copied QQ project source or assets incorporated into WNE.
- **Fahrmony**, nexen33 and contributors, tag `v1.3.0`, commit `b23fd66307fe0bbb098a53808beecd674740b143`, licensed CC BY-NC 4.0. It was used only for behavioral comparison. No Fahrmony implementation code or assets are distributed by WNE. Details: `docs/fahrmony-im-bridge-analysis.md`. Because CC BY-NC material was not copied or adapted, its non-commercial restriction is not applied to WNE.

These acknowledgements do not imply endorsement.

## Removed/excluded restricted material

Earlier revisions contained launcher raster resources named `wechat_launcher_background.webp` and `wechat_launcher_foreground.webp`, reported as originating from the WeChat APK. Tencent's original WeChat launcher artwork is not project-owned and cannot be relicensed as GPL. Those six raster files are deleted from the distributable tree and replaced by an original, generic WNE vector icon. Do not recover or redistribute those historical files in releases. If repository history itself is publicly mirrored, the historical blobs remain a residual provenance/takedown risk; consider a coordinated history rewrite only after maintainer/legal review (not performed by this migration).

Runtime notification avatars/icons obtained from an installed WeChat instance are user/device data used transiently through Android APIs; they are not checked into or distributed with WNE.

## Trademarks

Android, WeChat, Tencent, Nevolution, Fahrmony, QQ, and other names/marks belong to their respective owners. Their mention identifies compatibility or provenance only; it does not imply affiliation or endorsement.

## OpenMinis Provider configuration framework

Portions of the Provider domain/configuration and settings interaction design are adapted from OpenMinis.

- Upstream: `/tmp/OpenMinis` (OpenMinis project)
- Copyright: Copyright (C) OpenMinis contributors
- License: GNU General Public License v3.0 only (`GPL-3.0-only`)
- Local modifications: reduced to OpenAI-compatible chat-completions providers; Android Room entities/repository; per-provider Android Keystore AES-GCM secrets; WNE summary selection and connection-test flow. OAuth, model groups, native Gemini, multimodal and voice support were intentionally omitted.
- Adapted files: `AiProviders.kt`, `AiConfig.kt`, and Provider-management portions of `AiScreens.kt`.

The complete GPLv3 text must be shipped as `LICENSE` when this branch is merged with the project-wide licensing branch. Source recipients retain all GPLv3 rights, including access to corresponding source and modification/redistribution rights.
