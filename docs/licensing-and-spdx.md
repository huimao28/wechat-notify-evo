# Licensing and SPDX policy

WNE project-authored source and artwork are licensed `GPL-3.0-only` unless a file or `THIRD_PARTY_NOTICES.md` says otherwise.

New or substantially rewritten project-authored source files must begin with the comment appropriate to their format:

```text
SPDX-FileCopyrightText: YEAR NAME
SPDX-License-Identifier: GPL-3.0-only
```

Use the actual author/rightsholder and year; do not copy `huimao28`, `OpenMinis`, or another name when that party did not create or acquire the file. Preserve all upstream SPDX and copyright notices on imported/ported code. Imported code requires a compatible license and an entry in `THIRD_PARTY_NOTICES.md` with upstream URL, revision, files, copyright, license, and modifications.

Existing files are not mass-edited solely to add headers because that obscures history. Their project-level license is declared by `LICENSE`, `NOTICE`, and this policy. Files materially changed during licensing work should receive headers where the format permits.

The OpenMinis reservation in `NOTICE` is prospective only. A future OpenMinis port must add exact provenance when code is actually imported; no current file may claim OpenMinis authorship merely in anticipation.

Binary assets require documented source and license before commit. Do not commit icons or resources extracted from proprietary APKs. Generated release artifacts must include `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` under `assets/legal/`.
