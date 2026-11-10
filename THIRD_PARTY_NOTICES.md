# Third-Party Notices (merge fragment)

## OpenMinis Provider configuration framework

Portions of the Provider domain/configuration and settings interaction design are adapted from OpenMinis.

- Upstream: `/tmp/OpenMinis` (OpenMinis project)
- Copyright: Copyright (C) OpenMinis contributors
- License: GNU General Public License v3.0 only (`GPL-3.0-only`)
- Local modifications: reduced to OpenAI-compatible chat-completions providers; Android Room entities/repository; per-provider Android Keystore AES-GCM secrets; WNE summary selection and connection-test flow. OAuth, model groups, native Gemini, multimodal and voice support were intentionally omitted.
- Adapted files: `AiProviders.kt`, `AiConfig.kt`, and Provider-management portions of `AiScreens.kt`.

The complete GPLv3 text must be shipped as `LICENSE` when this branch is merged with the project-wide licensing branch. Source recipients retain all GPLv3 rights, including access to corresponding source and modification/redistribution rights.
