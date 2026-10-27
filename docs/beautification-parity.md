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
| Light/dark | yes | yes | yes (Material 3) | Telegram native; Operit `theme_mode` maps the background base color |
| Chat background color | theme-v1 background | yes | theme surfaces | **0.9.0** custom background color |
| Chat background image | theme-v1 media (PNG/JPEG/9-patch) | yes + gradient | wallpaper support | **0.9.0** picker/import, opacity; **0.11.0** travels in `.oxtheme` |
| Background gradient / dimming | theme-v1 | yes | yes | **0.10.0** two-color gradients (3 directions), opacity, dim; gradient directions only spot-checked |
| Incoming/outgoing bubble colors | theme-v1 | yes (overrides) | theme | **0.9.0** colors + opacity |
| Bubble border | theme-v1 | width/opacity/color | theme | **0.10.0** color + 1–3 dp width (device-verified) |
| Bubble corner radius | theme-v1 | yes | Material shapes | **0.9.0** 6–22 dp override |
| Bubble blur / frosted glass | glass surfaces | frosted style (sigma/opacity) | Material blur | **0.10.0** frosted style via reduced alpha; **0.17.0** true 3-level background-image blur (cached stack blur) |
| Bubble solid/frosted style switch | theme templates | yes | theme | **0.10.0** solid / frosted |
| Chat font family | theme fonts | system/imported/Google Fonts | Google Sans/code font | **0.10.0** system/serif/mono/imported TTF |
| Chat font size | yes | yes | yes | **0.9.0** scale, **0.10.0** 0.9–1.3× |
| Message line height | yes | yes | yes | **0.15.0** 1.0–1.5× in UI, 0.8–1.8 accepted on import; persisted in presets/`.oxtheme` |
| UI font / code font separately | theme assets | yes | yes | **0.10.0** separate chat/code fonts + TTF import; **0.11.0** assets in `.oxtheme` |
| Dynamic color | theme studio | Material You | Material 3 Expressive | **0.13.0** palette derived from background image/gradient (not Material You) |
| Named appearance presets | ToolPkg themes | custom themes | preset themes | **0.9.0** save/apply/delete |
| Theme import JSON | `.toolpkg` theme templates | CustomTheme JSON | CustomTheme JSON | **0.9.0** JSON; **0.10.0** preview; **0.14.0** Operit preference JSON (`values`) |
| RikkaHub backup settings | — | — | `settings.json` + DB/files | **0.19.0** assistants/providers/MCP; **0.20.0** `skills/*/SKILL.md`; **0.21.1** assistant `enabledSkills`/`customHeaders`; **0.21.2** provider+assistant header merge and `presetMessages` opener; DB, attachments, OAuth and SSE MCP not imported |
| Theme export / share | ToolPkg | copy JSON | export presets | **0.11.0** `.oxtheme` with background + fonts; **0.18.0** `.oxthemes` preset bundle (all named presets + assets, preview, duplicate skip); JSON export |
| Per-chat / per-agent theme | theme configs | chat themes | per-assistant | **0.10.0** per Agent; **0.16.0** per conversation (`files/ox_chat_dialogs/`) with precedence chat > agent > global; agent chat verified; **0.17.1** local-group scope device-verified |
| One-click native reset | — | — | — | **0.9.0** reset clears custom and restores Telegram |
| Custom vs native mode | — | — | — | **0.9.0** two-mode switch |
| Contrast / readability check | — | — | — | **0.10.0** WCAG-style check on device |
| Preset marketplace / one-tap apply | ToolPkg catalog | — | preset list | **0.12.0** six built-in themes + one-tap apply; no online marketplace |
| Live appearance preview | theme studio | preview | preview | **0.21.0** editor dialog with current-scope wallpaper (image/blur/dim), bubbles, border, font and line height |
| Persona-based recommendation | theme studio | — | — | **0.22.0** local keyword scoring over agent name + AGENTS.md picks one of six built-in themes |
| Wallpaper ↔ bubble coordination | — | — | — | **0.13.0** auto palette from current background (device-verified) |
| AMOLED / pure black | theme | yes | yes | **0.12.0** built-in AMOLED theme; Telegram themes also native |
| Import preview / rollback | ToolPkg validation | preview? | — | **0.10.0** appearance preview before write; invalid input leaves state unchanged |

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

## 0.12.0 implementation — one-tap built-in themes

- Six curated presets in the appearance page: AMOLED “午夜墨黑”, “纸墨”,
  “深海”, “樱花”, “森林”, “赛博霓虹”; each writes a complete coherent snapshot
  (background, bubbles, text, border, radius) to the current scope.
- Fixed the custom text-color path: `ChatActivity.ThemeDelegate` now overrides
  `key_chat_messageTextIn/Out` while a custom theme is active. Previously
  `ChatMessageCell` reset the text paint to the Telegram theme color during
  drawing, so e.g. cyberpunk’s cyan text rendered black.
- Device QA: cyberpunk and AMOLED applied to R2C Card; per-agent C2 override
  still wins over the global theme. Old test snapshots can carry a stale text
  color (white-on-white); built-ins always write a matching set and the contrast
  row reports such cases.

## 0.13.0 implementation — auto palette from the background

- “从背景自动配色” samples the current image down to 64 px (or averages the
  gradient/solid colors), derives saturated incoming/outgoing bubbles, picks a
  readable text color and a subtle border, and dims dark backgrounds.
- Device QA with the dark-blue `verify-rice.jpg`: generated `#DCDDE5` incoming,
  `#A3A6C2` outgoing, `#BDBFD3` border, dark readable text, dim 60; applied to
  R2C Card without losing the wallpaper.
- This closes the “wallpaper ↔ bubble coordination / dynamic color” innovation
  gap and complements the manual per-agent colors.

## 0.14.0 implementation — Operit preference JSON import

- The appearance parser now unwraps Operit’s nested `values` object and maps
  `bubble_ai_bubble_color`, `bubble_user_bubble_color`, AI/user text colors,
  `font_scale`, `background_image_opacity`, `theme_mode`, glass/blur flags and
  `custom_primary_color` (border).
- The existing preview → confirm → write path is reused, so an Operit backup is
  shown as a summary before it touches the current scope and invalid input does
  not change state.
- Device QA: preview `收 #2D2A45 · 发 #7C5CFF · 80% · 16dp · 1.1x · 磨砂 · 边框
  #FF2D95 1dp`; the applied page matched every row and the agent chat rendered
  purple bubbles, white text and the pink border over the existing wallpaper.
- Import merges onto the current snapshot: fields absent from the Operit file
  (background image, fonts) are inherited, and the preview shows those inherited
  values. This is deliberate for “restore my colors onto my current wallpaper”,
  but users wanting a clean slate should reset first.
- Not covered: Operit 9-patch bubble bitmaps, avatar/font URIs, per-bubble padding
  and image crop rectangles.

## 0.14.1 implementation — all bubble drawables themed

- 0.14.0 only overrode the text and selected-text bubble drawables. Agent tool-hint
  messages rendered through the media drawables, stayed native white, and their
  white custom text became invisible.
- `applyToChat` now overrides and restores all eight bubble drawables (in/out,
  selected, media, media-selected) with the same color, radius and border.
- Device QA on the existing `**Bold** Card` history: both `🔧 device_info({}) …`
  and `get_time({}) …` bubbles switched to `#2D2A45` with the `#FF2D95` 1dp border
  and readable white text, without sending new messages. Media bubble regression
  is covered by the same path but still needs an image-message screenshot.

## 0.15.0 implementation — message line height

- Appearance snapshot gains `lineHeight` (default 1.0, import range 0.8–1.8); the
  page cycles 1.0–1.5× under message font size.
- Persisted in global prefs, per-agent JSON, named presets, theme JSON and
  `.oxtheme`; import aliases `lineHeight`/`line_height`/`lineSpacing` are read.
- Runtime uses `OxChatTheme.lineHeightFor(dialogId)` inside
  `MessageObject.TextLayoutBlocks`, so text, quote and code blocks all honour the
  multiplier, only for the currently themed agent chat, and reset on close.
- Device QA: set 1.4×, reopened `**Bold** Card` and saw wider line spacing without
  losing bubble colors/tool bubbles; exported `.oxtheme` contained
  `lineHeight=1.4`, and the import preview displayed `行高 1.4x · 含背景图`.

## 0.16.0 implementation — per-conversation appearance

- New `files/ox_chat_dialogs/dialog_<id>.json` scope; lookup order is chat
  override → agent override → global. A chat menu icon (local agent dialogs and
  local groups) opens the existing appearance editor with a `会话外观 · name`
  title and a scoped “clear override and follow parent” action.
- Device QA on an agent chat: changed incoming/outgoing bubbles and text color,
  saw the new look in the chat, confirmed `R2C Card` still used the global theme,
  verified the override survived a force-stop/relaunch, then cleared it back to
  the global look.
- The local-group menu entry is wired through `OxGroupStore.isLocalGroup`, but the
  `-gpu off` emulator crashed qemu via Chromium/Vulkan when opening a local group
  twice, so that path remains code-only pending a real-device/GPU pass.
- Inherited text color is not auto-adjusted when a chat-only bubble color is
  changed; the existing contrast row reports low-contrast combinations.

## 0.17.0 implementation — true background blur

- `backgroundBlur` (0–3) is part of the appearance snapshot and travels through
  global prefs, per-agent/per-dialog JSON, presets, JSON export and `.oxtheme`;
  imports accept `backgroundBlur` / `background_blur` / `blur` / `blurSigma` /
  `blur_sigma` and clamp to 0–3.
- Only image backgrounds blur: `Utilities.stackBlurBitmapWithScaleFactor` at
  3×/5×/8× downscale, cached by path + mtime + level so repeated `applyToChat`
  passes do not recompute. Solid and gradient backgrounds are unchanged.
- Device measurement on the agent chat: the visible wallpaper strip’s grayscale
  standard deviation dropped from 29.54 (off) to 0.49–0.54 (strong), edge energy
  from 2.1 to 0.03; the pink wallpaper border became a soft gradient.
- 0.17.0 exposed two application bugs: the first `onResume` could run before the
  message list existed, skipping the theme until the editor was opened; and
  `applyToChat` rejected local groups, so group per-chat themes never applied.

## 0.17.1 fixes — first-open and local groups

- `ChatActivity.onResume` now schedules one 500 ms re-apply, so a cold-start first
  open renders the per-chat theme (font 1.15×, line height 1.4×, bubbles, border,
  blurred wallpaper) without visiting the appearance page.
- `applyToChat` accepts `OxGroupStore.isLocalGroup` as well as local agent
  dialogs; a local group with a 3-level blurred image background and per-chat
  colors/messages was opened on device after a cold start.
- The scoped clear row now reports “已清除覆盖，现跟随上层外观” instead of the
  global “已恢复 Telegram 原生外观”; it still deletes only the current scope
  file and falls back to the agent/global snapshot.
- Emulator note: `-gpu off` repeatedly crashed qemu’s host `RenderThread`
  (SIGSEGV) when opening a chat. Disabling WebView/Chrome and `-feature -Vulkan`
  did not help; group verification was completed under `-gpu guest` software
  rendering. This is an emulator limitation, not an app FATAL/ANR, and still
  needs a real-device/GPU pass.

## 0.18.0 implementation — portable preset bundle

- `.oxthemes` is a zip with `bundle.json` plus per-preset background/font assets.
  Export covers every named preset; import previews the count, up to eight names,
  duplicate names and asset counts before writing anything.
- Import validates the whole bundle first (format, 100 presets, 300 zip entries,
  8 MB per asset, 40 MB total), skips names that already exist instead of
  overwriting, restores assets to new app-private files and rewrites the snapshot
  paths. A failed write removes the preset and asset files created by that import.
- Device QA: two colour-only presets exported to a 506 B bundle and re-imported
  idempotently (0 imported / 2 skipped); a preset with the 16 KB wallpaper exported
  to a 4.7 KB bundle, was deleted, then restored (1 imported / 2 skipped) with a
  fresh asset path and a working apply action. A 13-byte text file with the
  `.oxthemes` extension was rejected without touching presets or assets.
- The Chinese pack version had to be bumped again after the copy changed; the first
  0.18.0 install still showed old/English strings. Final pack 86 shows
  “导入预设包 / 导出预设包”, “导入这个预设包？” and “导入”.
- Limits: font assets were not exercised by the test presets, assets over 8 MB are
  skipped on export, and the mid-write rollback path is code-reviewed only.

## 0.19.0 implementation — RikkaHub settings import

- Ox Settings now accepts a RikkaHub backup zip or its `settings.json` through the
  system picker and previews assistant/provider/MCP counts before writing.
- Providers (`openai`/`google`/`claude`) supply base URL, key and per-model context;
  assistants map name, system prompt, emoji avatar, temperature, topP, maxTokens and
  reasoning level; streamable-HTTP MCP servers map URL, name, enabled state and
  headers. SSE MCP and OAuth tokens are skipped.
- Agent ids are `rikka_<assistant UUID>`, so a repeated import updates the same
  agents and preserves `tg_id`/custom avatars; MCP servers are deduplicated by URL.
  A schema-derived sample verified preview, creation, idempotent update, headers and
  rejection of a non-RikkaHub JSON without any writes; no app FATAL/ANR and no test
  key appeared in previews or logcat.
- Not covered: the RikkaHub database/conversations, attachments, skills, workspaces,
  lorebooks, quick messages, custom assistant headers, image avatars, opening
  messages, enabled skills, OAuth and SSE transports. A real RikkaHub export still
  needs to be used for a compatibility pass.

## 0.20.0 implementation — RikkaHub skills

- The RikkaHub backup import now also scans `skills/<name>/SKILL.md` entries in
  the same zip, capped at 50 skills of 512 KB each and 1000 zip entries.
- Skill ids come from the folder relative to `skills/` (nested folders joined
  with `_`), are sanitized by `OxSkillStore.skillId` and written to
  `files/oxskills/<id>/SKILL.md`. Repeat imports update the same id rather than
  creating `_1` copies and never delete other local skills.
- The preview lists up to eight skill ids plus existing skill counts; the result
  reports how many skills were imported. A `settings.json`-only pick still works
  and reports zero skills.
- Device QA with a schema sample: preview “2 assistants · 1 provider · 1 MCP ·
  1 skill”; import created 2 agents, 1 MCP and 1 skill (152 B, frontmatter
  intact); repeat import updated 2 agents and 1 skill with only one TestSkill
  directory. No key appeared in previews/logcat, no FATAL/ANR; test objects and
  the sample file were removed.
- Still missing: skill attachments/scripts, agent `enabledSkills` assignment,
  the RikkaHub chat database and attachments, OAuth and SSE MCP. A real export
  still needs to be tested.

## 0.21.0 implementation — live appearance preview

- In custom mode the appearance editor now has “预览当前外观”. It opens a native
  AlertDialog rendered from the active scope: wallpaper drawable (solid, gradient
  or image with dim/blur), incoming and outgoing sample bubbles, bubble alpha and
  frosted reduction, corner radius, border, text color, chat typeface, font scale
  and line height.
- The preview reads `OxChatTheme.current()` every time, so global, per-agent and
  per-chat scopes each show their own values. `currentBackgroundDrawable()` and
  `currentTypeface()` reuse the chat rendering helpers instead of duplicating them.
- Device QA: the global image theme showed `VERIFYRICE0.9.1` under the bubbles;
  changing blur from off to strong and reopening the preview softened the
  wallpaper text/border while keeping bubble colors and typography, then blur was
  restored to off. No FATAL/ANR.
- Limits: it is a lightweight sample, not real ChatMessageCell instances, so
  media/tool bubbles, replies, avatars and real wrapping are not shown; frosted
  bubbles remain an alpha simulation; custom TTF preview was not separately
  captured.

## 0.21.1 fixes — skill assignment and assistant headers

- Import skills before assistants, build a name/id → skill directory map, and map
  non-empty `enabledSkills` onto `agent.skillIds`. An empty list leaves the
  agent's existing selection untouched.
- Map assistant `customHeaders` (`{name,value}`) onto the agent's `headers` as
  `[[name,value], ...]`; empty arrays also leave existing values untouched.
- Device QA: `Rikka QA` with `enabledSkills=["Rikka Test Skill"]` and
  `X-Rikka-Assistant: 1` produced `skill_ids=["TestSkill"]` and
  `headers=[["X-Rikka-Assistant","1"]]`; repeat import updated the same two
  agents and kept both mappings. Test objects were removed afterwards.

## 0.21.2 fixes — provider headers and opening message

- Provider `customHeaders` and assistant `customHeaders` are merged by header name
  (assistant wins) into the agent headers; provider headers only apply when one
  of its models is actually referenced by an assistant.
- The first assistant text part in `presetMessages` becomes `agent.firstMes`
  (4000-character cap); other preset parts and extra preset messages are ignored.
- QA caught a JSON-array round-trip bug where provider headers disappeared
  (`m=1`/`m=0`); merging directly from the source arrays fixed it (`m=2`).
- Device result: `first_mes="Welcome from RikkaHub preset."`,
  `headers=[["X-Rikka-Provider","p"],["X-Rikka-Assistant","1"]]` and the existing
  skill mapping survived a repeat import. Test objects were removed.

## 0.22.0 implementation — persona-based recommendation

- Agent appearance pages expose “从人设推荐外观”. `recommendBuiltIn` scores the
  agent name and AGENTS.md against local keyword sets for the six built-in
  themes and applies the winner to that agent's snapshot; no model or network
  call is involved, and the result is deterministic.
- Device QA: `barB-nokey` (深夜/酒吧 persona) recommended 赛博霓虹, showing the
  gradient `#0A0014 → #1B0033`, purple outgoing bubble, cyan text and pink 2 dp
  border; the generated agent JSON matched the built-in. The test override was
  removed to restore the agent's parent theme.
- Limits: keyword heuristics rather than semantic understanding, default 深海
  when nothing matches, whole-snapshot replacement (including wallpaper/fonts),
  agent scope only, and no candidate preview or undo.

## Gaps / next phases

1. Operit theme-v1 remaining media: 9-patch bubble assets, avatar/font URIs,
   input styles, per-bubble padding and image cropping.
2. Kelivo full backup import; RikkaHub chat database/attachments, skill
   attachments, standalone providers and image avatars.
3. Semantic/persona recommendation with candidate preview and undo (the 0.22.0
   keyword version is deterministic and offline).
4. Per-conversation theme inheritance still copies the parent’s text color; a
   low-contrast combination is reported by the check row but not auto-corrected.
5. Re-run the third A/B/C round on current builds once a real-device/GPU pass can
   confirm background layering and blur on local groups; cover per-agent themes,
   font import/export, preset preview, gradients, group albums, tool bubbles,
   literal `\n` in old messages, skill dedupe/delete and local-agent search.
