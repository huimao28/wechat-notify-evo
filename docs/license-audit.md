# GPLv3 migration technical audit (v1.1.0-beta.2)

Date: 2026-11-10. Scope: Git tree/history reachable from latest `main` at `ff93a93` and the resolved source/dependency declarations. This is a technical compliance review, not legal advice.

## Authorship and authority

`git shortlog -sne --all` reports 65 commits by Kobby Li, 24 by huimao28 (both use `huimao28@users.noreply.github.com`), and 13 by `Minis <minis@localhost>`. Kobby Li and huimao28 appear to be the same GitHub account identity, but Git metadata alone cannot legally prove identity, employment status, originality, or copyright assignment. The repository owner/user expressly approved licensing WNE as GPLv3; that supports licensing project-owned contributions, but does not transfer rights owned by another person.

`Minis` appears to be a local automation/tool author identity. Authorship fields do not establish a separate human rightsholder or an assignment to huimao28. Before a public release, the maintainer should retain records showing those commits were generated at the maintainer's direction from project-owned/original inputs. If any commit contains a third party's expressive contribution, obtain that contributor's GPL-3.0-only consent or remove/rewrite it. No contributor license agreement or signed-off-by chain was found.

Subject to that unresolved identity/assignment evidence, code authored and controlled by huimao28/the project can be offered under GPL-3.0-only. This audit does not claim authority over anyone else's copyright.

## Code/reference findings

- Fahrmony v1.3.0 is CC BY-NC 4.0. Existing analysis and source structure state it was used only to compare behavior; no copied code/assets were identified. Its non-commercial license therefore is not being applied to WNE. Any later copy/adaptation would require a fresh compatibility review and must not be silently placed under GPL.
- Nevolution/decorator-wechat was acknowledged as prior art/behavioral research. No copied or ported implementation was identified in this tree.
- QQ-Notify-Evolution was acknowledged as behavioral/configuration reference. No copied code/assets were identified in this tree.
- Comments describing equivalent behavior or public Android APIs do not by themselves establish copying, but repository history cannot prove clean-room independence. Preserve research notes and review any future import commit-by-commit.
- OpenMinis code is not presently identified as imported. `NOTICE` retains only a prospective attribution rule and expressly does not claim a completed port.

## Asset findings and remediation

Six tracked raster launcher files named `wechat_launcher_{background,foreground}.webp` were reported as extracted from the WeChat APK. They are not project-owned GPL-compatible assets. They were removed and replaced by project-authored generic WNE vector foreground/background resources carrying SPDX headers. Adaptive icon XML now references only those vectors. Runtime icons/avatars obtained transiently from installed WeChat notifications are not repository-distributed assets.

Historical Git blobs still contain the removed raster files. They are excluded from new checkout/build artifacts but remain reachable in old commits/tags. Rewriting published history is disruptive and was intentionally not done; maintainer/legal review should decide whether takedown/history filtering is necessary.

## Dependencies and notices

Declared components are Android/Kotlin/AndroidX/Compose/Room/Car App/Navigation, Material icons, OkHttp/MockWebServer, JUnit, AGP and KSP. They are separately licensed (primarily Apache-2.0; JUnit 4 EPL-1.0) and are not relicensed as WNE code. Exact transitive notices must be reviewed from the resolved artifacts for each release. `THIRD_PARTY_NOTICES.md` records this boundary, while Gradle packages the project's `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` as `assets/legal/*`.

## Remaining release gates

1. Confirm the legal identity/authority behind `Minis <minis@localhost>` commits and retain evidence.
2. Confirm Kobby Li and huimao28 are the same rightsholder or document permission.
3. Review resolved transitive dependency license metadata on every dependency upgrade.
4. Do not restore historical WeChat APK launcher files; assess whether public-history rewrite is required.
5. Require SPDX/provenance records for future OpenMinis or other third-party ports.
