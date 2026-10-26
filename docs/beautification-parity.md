# Beautification parity: Operit / Kelivo / RikkaHub → OrionekoX

This document tracks the appearance features found in the reference apps, what
OrionekoX ships, and what remains. It is intentionally honest about verification:
“implemented” means code + device QA, “ported” means code only, “planned” means not
started. Community pain points marked “to validate” are hypotheses from the product
comparison and must be confirmed with issue/community research before being treated as
requirements.

## Feature matrix

| Feature | Operit/Operit2 | Kelivo | RikkaHub | OrionekoX |
|---|---|---|---|---|
| Accent / seed color | yes | yes (primary/secondary/tertiary) | yes (CustomTheme) | **0.8.107** import + presets, Ox-owned surfaces |
| Light/dark | yes | yes | yes (Material 3) | Telegram native |
| Chat background color | theme-v1 background | yes | theme surfaces | **0.9.0** custom background color |
| Chat background image | theme-v1 media (PNG/JPEG/9-patch) | yes + gradient | wallpaper support | **0.9.0** image picker/import, alpha |
| Background gradient / dimming | theme-v1 | yes | yes | planned |
| Incoming/outgoing bubble colors | theme-v1 | yes (overrides) | theme | **0.9.0** colors + opacity |
| Bubble border | theme-v1 | width/opacity/color | theme | planned |
| Bubble corner radius | theme-v1 | yes | Material shapes | **0.9.0** 6–22 dp override |
| Bubble blur / frosted glass | glass surfaces | frosted style (sigma/opacity) | Material blur | planned |
| Bubble solid/frosted style switch | theme templates | yes | theme | planned |
| Chat font family | theme fonts | system/imported/Google Fonts | Google Sans/code font | planned |
| Chat font size | yes | yes | yes | **0.9.0** scale 0.9–1.2 |
| UI font / code font separately | theme assets | yes | yes | planned |
| Dynamic color | theme studio | Material You | Material 3 Expressive | planned |
| Named appearance presets | ToolPkg themes | custom themes | preset themes | **0.9.0** save/apply/delete |
| Theme import JSON | `.toolpkg` theme templates | CustomTheme JSON | CustomTheme JSON | **0.9.0** JSON import (color keys) |
| Theme export / share | ToolPkg | copy JSON | export presets | planned |
| Per-chat / per-agent theme | theme configs | chat themes | per-assistant | custom mode is per-agent-chat runtime; **not persisted per agent yet** |
| One-click native reset | — | — | — | **0.9.0** reset clears custom and restores Telegram |
| Custom vs native mode | — | — | — | **0.9.0** two-mode switch |
| Contrast / readability check | — | — | — | planned (innovation) |
| Preset marketplace / one-tap apply | ToolPkg catalog | — | preset list | planned (innovation) |
| Wallpaper ↔ bubble coordination | — | — | — | planned (innovation) |
| AMOLED / pure black | theme | yes | yes | via Telegram themes |
| Import preview / rollback | ToolPkg validation | preview? | — | **0.8.112** preview for MCP; appearance import needs preview |

## Community pain points (to validate next research pass)

- Themes do not travel between clients; users lose custom bubbles/fonts when switching.
- Per-chat/per-agent themes are requested when one global theme cannot fit all chats.
- Custom fonts often break layout or fall back silently; users want previews and
  per-surface font selection.
- Frosted/blurred bubbles look good but can hurt scrolling and readability; users want
  performance and contrast warnings.
- Users want a safe “reset to default” after experimenting, and named presets to A/B
  test looks.
- Export/backup of appearance settings is often separate from chat backup.

These need verification against GitHub issues/discussions and store reviews; the next
research round should cite concrete threads.

## 0.9.0 implementation

- New `OxChatTheme` model: mode (`native`/`custom`), background color/image, incoming
  and outgoing bubble colors, bubble alpha, corner radius, message font scale, named
  presets in app-private storage.
- New `OxChatAppearanceActivity` (UniversalRecyclerView/SettingCell):
  mode switch, color cycling, background image picker, opacity/radius/font rows,
  JSON import, save/apply/delete presets, reset to native.
- `OxAppearanceActivity` links to it; `ChatActivity` applies overrides only for local
  agent dialogs on resume and restores Telegram colors/background on pause.
- `MessageDrawable.setOverrideRoundRadius` enables the radius override.
- Import mapping accepts Kelivo/RikkaHub-style `backgroundArgb`, `inBubbleArgb`,
  `outBubbleArgb`, `bubbleAlpha`, `cornerRadius`, `fontScale`, plus
  `primaryColorArgb`/`secondaryColorArgb` fallbacks. Operit theme-v1 needs a dedicated
  mapper.

## Gaps / next phases

1. Full Operit theme-v1 mapping (background images, 9-patch, fonts, input styles,
   bubble borders) and preview/rollback.
2. Bubble border, frosted/blur, gradient backgrounds, separate UI/code fonts.
3. Persist per-agent themes (not just “custom while this chat is open”) and export
   presets.
4. Contrast/readability check and live preview before applying.
5. Community research with citations, then a second parity pass and multi-round QA.
