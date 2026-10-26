# QA — SKILL.md / zip import (0.8.106, 2026-10-26 19:00–19:12)

Build: `io.github.blancatora.orionekox` versionName 0.8.106 (versionCode 1219).

## What was added

- 技能库 now has “导入 SKILL.md / zip” above the list, backed by the system document
  picker (`ACTION_OPEN_DOCUMENT`, `*/*`). It does not require
  MANAGE_EXTERNAL_STORAGE because imported files are copied into the app-private
  `files/oxskills/<id>/SKILL.md`.
- Plain Markdown: imports the picked file as one skill; the id comes from the display
  name with illegal characters replaced.
- Zip: walks up to 200 entries, imports every `SKILL.md` (up to 50 skills), using the
  containing folder name as the id.
- Limits: 2 MB archive, 512 KB per SKILL.md, app-private storage only; no path from
  the archive is used as a filesystem path.
- `OxSkillStore.list()` now scans the app-private directory first, then the existing
  Operit/Operit2 Downloads folders. The list refresh uses
  `UniversalAdapter.update(true)`.

## Verified

- Pushed `orioneko-test-skill.md` (Chinese frontmatter/name) to Download, opened
  技能库 → 导入 SKILL.md / zip → Downloads → selected the file.
- File landed at
  `/data/data/io.github.blancatora.orionekox/files/oxskills/orioneko-test-skill/SKILL.md`.
- The list refreshed and showed “测试导入技能 · 验证 Kelivo/RikkaHub SKILL.md 导入”;
  tapping it opened the native preview with the YAML frontmatter and body.
- The built-in “示例技能” remains.

## Not yet exercised

- Zip import path is implemented but not exercised on-device.
- Enabling the imported skill in an agent editor and observing prompt injection was not
  re-run; the existing per-agent skill binding reads the same `OxSkillStore.list()`.
- No delete/disable UI for imported skills yet.

## Next

- Theme JSON import (`primaryColorArgb`) and a native appearance page.
- Zip fixture test and optional remove/disable for imported skills.
