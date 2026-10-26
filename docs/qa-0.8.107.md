# QA — theme JSON import and Ox accent (0.8.107, 2026-10-26 19:12–19:20)

Build: `io.github.blancatora.orionekox` versionName 0.8.107 (versionCode 1229).

## What was added

- Ox 设置 → 外观 (`OxAppearanceActivity`), built on UniversalRecyclerView/SettingCell:
  - current accent row (name + `#RRGGBB`, or “Telegram 默认强调色”);
  - “导入主题 JSON” through the system document picker;
  - six native presets (Material 紫 / 海洋蓝 / 森林绿 / 琥珀 / 樱花粉 / 青色);
  - “恢复 Telegram 默认强调色”.
- `OxTheme` parses Kelivo/RikkaHub theme JSON:
  `primaryColorArgb`, optional `secondaryColorArgb`/`tertiaryColorArgb`, optional `name`;
  numbers or `#RRGGBB`/`#AARRGGBB` strings; stored in `ox_theme` prefs.
- The accent is deliberately scoped to Ox-owned surfaces: the default terminal color
  scheme uses it for prompt/send/keyboard tint, and the 外观 entry in Ox settings uses
  it for its icon. Telegram's native theme/colors are not modified.
- Chinese pack 68 adds the new strings.

## Verified

- Installed release; pushed `kelivo-theme.json`
  (`{"name":"Kelivo 紫","primaryColorArgb":4284960932,...}`) to Download.
- 设置 → Ox 设置 → 外观: page shows current accent “Telegram 默认强调色”, import row,
  six presets and reset row.
- Import → file picked → page now shows “Kelivo 紫 · #6750A4”.
- Reset → page returns to “Telegram 默认强调色”.
- Existing terminal scheme is a non-default preset (Ubuntu amber), so the custom accent
  was not visually applied there in this pass; the code applies it only to the default
  terminal scheme.

## Not yet

- Accent does not yet recolor Telegram bubbles/buttons (by design for this iteration).
- Custom theme import is not persisted through the public `.oxagent`/backup flow.
- No contrast validation for very light/dark imported colors.
