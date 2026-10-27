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
- Kelivo `ChatBubbleStyleOverrides` gives the next import target: `backgroundArgb(Light|
  Dark)`, `borderArgb(Light|Dark)`, `textArgb(Light|Dark)`, `borderWidth`,
  `borderOpacity`, `cornerRadius`, `blurSigma`, `frostedOpacity`, `solidOpacity`.
  The 0.10.0 mapper will read these keys and pick the light/dark variant that matches
  the active Telegram theme; unknown keys are ignored.

## 0.9.0 device QA (second independent round B, 2026-10-27)

- Custom mode applied correctly on device: background `#101114`, incoming `#E8F5E9`,
  outgoing `#EFFDDE`, 86% alpha, 10 dp radius, 1.1× font.
- Named preset save → apply → delete worked; one-click native reset restored the stock
  Telegram wallpaper and bubbles.
- Imported JSON with `#1B1B2F`, `#FF2D55`, `#30D158`, 219 alpha, 18 dp, 1.2× mapped
  correctly and applied immediately.
- Gaps seen in QA: applying a preset says “已导入外观” (wrong verb); import applies
  immediately with no preview/rollback; background-image picker not covered; per-agent
  persistence is still global.

## Community pain points with citations

These confirm the feature directions rather than relying on guesses. Checked
2026-10-27 via the GitHub search API.

- **Kelivo #979 – 整合主题设置模块**: appearance options (background, bubbles, avatars,
  fonts) are scattered; users ask for one theme module with export/import. This matches
  our single “Agent chat appearance” page and preset export plan.
- **Kelivo #940 – 主题色使用问题**: theme color is hard to tune and does not match the
  actual UI; requests independent color controls and background images.
- **Kelivo #581 / #667 – 输入栏毛玻璃开关 / 透明度**: the frosted composer can become a
  murky blob over text; users want a frosted/solid switch and alpha control. We will add
  a solid/frosted style switch and a readability hint instead of forcing blur.
- **Kelivo #548 / #1054 / #892 / #615**: separate code-font size, separate UI/chat/input
  sizes, layout breakage at 125%+, CJK glyph distortion, and a request for separate
  Chinese/Latin fonts. Our next pass adds independent chat/code font families and sizes,
  plus a CJK-safe default.
- **Kelivo #1226 – 聊天行高**: user-selectable 1.2–2.0 line height for chat text.
- **RikkaHub #406 – dark splash flashbang**: theme-aware startup; **#350 – AMOLED pure
  black**; **#1134 – Cyberpunk theme preset** with neon/glass/particles. AMOLED and
  preset themes are confirmed demand, not speculation.
- **RikkaHub #1308 – immersive mode / no forced scroll on send**: appearance work also
  includes interaction polish, not only colors.
- **Operit #1156 – apply current card theme to all characters**: per-agent themes plus
  an “apply to all” action. **#1104 – snapshot import restores theme media**: theme
  export/import must include wallpaper and font assets, not just colors. **#1314** –
  streaming chat backup confirms the value of portable, resource-complete exports.

## 0.10.0 implementation

- Per-agent appearance snapshots (`files/ox_chat_agents/agent_<tgId>.json`) with
  global fallback and “apply to all agents”; entry from the agent long-press menu.
- Background: solid / two-color gradient (3 directions) / image, independent
  background opacity and dim layer; status and navigation bars follow the theme.
- Bubbles: solid/frosted style, border color and 1–3 dp width, independent text
  color, WCAG contrast check.
- Fonts: chat family (system/serif/monospace/imported TTF), code family, size
  0.9–1.3×; imported fonts are validated and stored in `files/oxfonts/`.
- Import preview before applying, JSON export via SAF, and a direct link to
  Telegram's native `ThemeActivity` in native mode.
- `ChatActivity.ThemeDelegate.setOxWallpaperDrawable` lets the custom background
  replace the chat wallpaper drawable instead of fighting the list background.
- Device QA: per-agent isolation PASS (C2 light/white incoming vs R2C Card global
  dark), border/frosted/serif PASS, native link/reset PASS. Background coverage is
  PARTIAL on the `-gpu off` emulator (faint native pattern remains); needs a
  real-device/GPU pass. See `docs/qa-0.10.0.md`.

## 0.11.0 implementation — portable `.oxtheme` packages

- Export now writes a zip (`theme.json`, `background.<ext>`, `chat-font.ttf`,
  `code-font.ttf`) instead of a JSON that only carries local file paths.
- Import detects JSON or zip; the preview lists the summary plus “含背景图 /
  含字体”; applying restores assets into app-private storage and rewrites the
  snapshot paths before persisting.
- Device round-trip PASS: imported `qa-sans.ttf`, set `verify-rice.jpg` as
  background, exported a 464 KB `.oxtheme`, switched the font back to system,
  re-imported the package, and got the imported font + background back.
- This closes the Operit #1104-style gap (theme backup without media) and the
  Kelivo #979 request for a portable appearance module.

## Gaps / next phases

1. Full Operit theme-v1 mapping (background images, 9-patch, fonts, input styles,
   bubble borders) and preview/rollback.
2. Bubble border, frosted/solid switch, gradient backgrounds, separate chat/code fonts
  and sizes, line height, AMOLED/contrast.
3. Persist per-agent themes, add “apply to all agents”, and export presets with their
   wallpaper/font assets.
4. Contrast/readability check and live preview before applying.
5. Re-run multi-round QA after the 0.10.0 batch, including background-image import,
   preset export/import round-trip, and per-agent isolation.
