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
| Named appearance presets | ToolPkg themes | custom themes | preset themes | **0.9.0** save/apply/delete; **0.25.2** rename with duplicate-name rejection; **0.27.0** preview a saved preset without applying it |
| Theme import JSON | `.toolpkg` theme templates | CustomTheme JSON | CustomTheme JSON | **0.9.0** JSON; **0.10.0** preview; **0.14.0** Operit preference JSON (`values`) |
| RikkaHub backup settings | — | — | `settings.json` + DB/files | **0.19.0** assistants/providers/MCP; **0.20.0** skills; **0.21.x** skills/headers/openers; **0.24.0** selected-branch text history; **0.25.0** matching `upload/` images as history vision context; tool parts, other files and OAuth not imported |
| Kelivo / Chatbox assistant backup | — | `copilots.json`, `assistants_v1` | — | **0.26.0** assistants/personas with prompt, sampling, headers and model-name override; **0.28–0.44** conversations, avatars, images, attachments, backgrounds, skills import, HTTP MCP and long-term memory; **0.46.0** exports `SKILL.md` files and per-agent skill links; **0.47.1** exports per-agent workspaces (200 files / 2 MiB each / 8 MiB total); **0.48.1** exports/restores per-agent preferences (tool policies, pin, remark, greeting, reasoning/context/sampling settings); **0.50.0** exports/restores per-agent appearance overrides and custom chat/code fonts (colors, bubbles, corner, border, text, scale, line height, background image; 8 MiB per font, 16 MiB total); **0.51.0** exports/restores the global appearance theme, global background and global chat/code fonts via `settings.json`, preserving a local custom global theme; provider keys, base URLs, headers and `extra_body` never exported |
| SillyTavern / RikkaHub character cards | Operit `character_cards` JSON | SillyTavern PNG/JSON | SillyTavern PNG/JSON | **0.45.0–0.45.1** PNG `tEXt`/`iTXt` + JSON via the agent-list entry, six persona sections, first message and PNG avatar, idempotent ids; **0.49.0** exports V2 JSON and PNG cards (avatar re-encoded, Base64 `chara` chunk, generated placeholder when no avatar) with six-section persona roundtrip; no alternate greetings, character book, tags or real-client validation |
| Local agent memory library | — | — | — | **0.52.0** per-agent SQLite with normal keyword and experimental local-hash-vector recall, 45-message compression threshold, readable turn/fact/summary entries, `memory_search` and strict `memory_verify`; **0.53.0** BM25 + vector + retention/importance/access fusion, MMR, power forgetting curve with stability/difficulty updates, fact consolidation/invalidation with temporal history and summary scrubbing; **0.54.0** asynchronous agent-model distillation of abstractive summaries and atomic facts with extractive fallback; **0.55.0** offline TextRank/SummaryBufferMemory/Selective-Context-style compaction with token-pressure triggering and rolling summaries (LLMLingua-2 not bundled); no external embeddings and not yet exported in backups |
| Theme export / share | ToolPkg | copy JSON | export presets | **0.11.0** `.oxtheme` with background + fonts; **0.18.0** `.oxthemes` preset bundle (all named presets + assets, preview, duplicate skip); JSON export |
| Per-chat / per-agent theme | theme configs | chat themes | per-assistant | **0.10.0** per Agent; **0.16.0** per conversation (`files/ox_chat_dialogs/`) with precedence chat > agent > global; agent chat verified; **0.17.1** local-group scope device-verified |
| One-click native reset | — | — | — | **0.9.0** reset clears custom and restores Telegram |
| Custom vs native mode | — | — | — | **0.9.0** two-mode switch |
| Contrast / readability check | — | — | — | **0.10.0** WCAG-style check on device |
| Preset marketplace / one-tap apply | ToolPkg catalog | — | preset list | **0.12.0** six built-in themes + one-tap apply; no online marketplace |
| Live appearance preview | theme studio | preview | preview | **0.21.0** editor dialog with current-scope wallpaper (image/blur/dim), bubbles, border, font and line height |
| Persona-based recommendation | theme studio | — | — | **0.22.0** local keyword scoring over agent name + AGENTS.md picks one of six built-in themes |
| Wallpaper ↔ bubble coordination | — | — | — | **0.13.0** auto palette from current background (device-verified) |
| AMOLED / pure black | theme | yes | yes | **0.12.0** built-in AMOLED theme; **0.23.0** one-tap pure-black background toggle that preserves the rest of the snapshot; Telegram themes also native |
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
  agent scope only. **0.25.1** adds a confirmation dialog before applying and a
  one-level “撤销上次推荐” that restores the pre-recommendation snapshot.

## 0.23.0 implementation — AMOLED toggle

- The appearance snapshot gains an `amoled` boolean that travels through prefs,
  per-agent/per-chat JSON, presets and theme packages; imports accept boolean or
  0/1 aliases.
- A one-tap “AMOLED 纯黑” row makes the current scope's wallpaper a pure-black
  ColorDrawable and paints the status/navigation bars black, while keeping the
  rest of the snapshot (bubbles, text, border, font). Turning it off restores the
  stored wallpaper without losing it.
- Device QA: with an image background, enabling showed pure black in the editor
  preview and in a local group; disabling restored the image and `amoled=false`.
  No FATAL/ANR.
- Limits: background/system bars only (text colors are not auto-adjusted), one
  scope at a time, no schedule/system-dark automation, and no real-screen power
  measurement.

## 0.24.0 implementation — RikkaHub text history

- The backup reader now extracts `rikka_hub.db` (64 MB cap) and previews
  conversation/message-node counts.
- Import reads `conversationentity` and `message_node` read-only, takes
  `messages[select_index]` for each node (the selected branch), extracts only
  user/assistant text parts, and appends them to the mapped `rikka_<uuid>` agent
  chat. Limits: 200 conversations, 500 nodes each, 5000 messages, 100k chars per
  message. Deterministic timestamps and a role/content/ts set make re-imports
  idempotent.
- Device QA: a schema sample with two conversations and four nodes imported
  `Branch B selected` (not the unselected branch A) and kept exactly four
  entries after a repeat import. Test objects and cache DB were removed.
- Limits: only the selected branch; images/files/tool/reasoning parts skipped;
  multiple conversations of one assistant merge into one Ox chat; titles and
  original timestamps are not preserved; a real RikkaHub export still needs a
  compatibility pass.

## 0.25.0 implementation — RikkaHub image history

- The backup reader collects up to 40 `upload/` images (4 MB each, 40 MB total).
  During history import, image parts are matched to those files by basename,
  copied to `files/rikka_upload_<name>` and stored in `OxChatEntry.imagePath`;
  image-only messages use a `[图片]` placeholder.
- The existing history assembly sends a user entry's `imagePath` as an inline
  base64 vision part, so imported images remain useful for later multimodal
  turns. Non-matching/remote/data URLs are skipped.
- Device QA: a one-conversation/three-node sample imported a 70 B PNG as the
  third entry with `image_path`, and a repeat import kept exactly three entries.
  Test objects and the uploaded file were removed.
- Limits: only the first image per message and only `upload/` basenames; other
  file types and the remaining branches are not imported; the image is not
  rendered as a native photo bubble, only used as history context.

## 0.25.1 fixes — recommendation confirmation and undo

- Recommendation now opens a native confirmation dialog naming the candidate
  theme; cancelling changes nothing.
- Before applying, the current scope snapshot is stored under
  `files/ox_recommend/<scope>.json` (first backup wins). An “撤销上次推荐” row
  appears while that backup exists; it restores the snapshot and deletes the
  backup. Backups are isolated per global/agent/dialog scope.
- Device QA: `barB-nokey` showed the 赛博霓虹 candidate, applied it, then undo
  restored the image background, 80% opacity, 24% dim and AMOLED off; the backup
  file was removed. Test-generated agent override was cleaned up.
- Limits: undo restores an explicit snapshot (the agent file remains), only one
  undo level, no redo and no side-by-side candidate preview.

## 0.25.2 implementation — preset rename

- Long-pressing a named preset now offers 重命名/删除. Rename pre-fills the current
  name, sanitizes the new file name with the same rules as save, updates the JSON
  `name`, deletes the old file and refuses a name that already exists.
- Device QA: RenameQA became RenameQA2 with only the new file left; renaming to
  an existing BundleQA was rejected and the original stayed intact; cancel made
  no changes. The temporary preset was removed.
- Limits: single-preset rename only, no copy/bulk actions, sanitized/truncated
  names can collide (then rejected), and exported `.oxthemes` files keep their
  old names.

## 0.26.0 implementation — Kelivo/Chatbox assistants

- Add a Kelivo/Chatbox backup import entry accepting a zip or JSON. From zips it
  reads `copilots.json` and the `assistants_v1` value in `settings.json` (JSON
  string, array or id→object map); direct JSON arrays/objects are also accepted.
- Mapping covers name, systemPrompt/prompt, temperature/topP/maxTokens,
  customHeaders and a chatModelId model-name override. Provider wiring is
  inherited from the first existing agent with baseUrl+apiKey (otherwise offline).
  Agent ids are `kelivo_<id>` and repeat imports update while preserving tg_id
  and custom avatars.
- Device QA with a schema sample: 2 assistants previewed, both created, the
  Kelivo assistant kept its prompt/model/0.5/0.9/256/X-Kelivo header, and a repeat
  import reported 0 created/2 updated with exactly two agents. Test objects and
  the sample were removed.
- Limits: assistants only; conversations, skills, MCP, workspaces,
  avatars/backgrounds, preset messages and regex rules are not imported. A real
  Kelivo export still needs a compatibility pass.
- **0.26.1** adds Provider wiring: `provider_configs_v1` becomes an id→config
  map, assistants use their `chatModelProvider` baseUrl/apiKey and the first model
  when `chatModelId` is absent, otherwise they fall back to the local provider.
  OAuth/multi-key/proxy/provider-specific headers are still skipped.
- **0.27.1** also reads `skills/**/SKILL.md` from the backup zip into the private
  skill library (up to 50 skills, 512 KB each, same-name update). Only the skill
  body is imported; scripts/attachments are not.
- **0.27.2** imports HTTP MCP entries from `mcp_servers_v1` (also `mcpServers`):
  name, URL, headers and enabled flag go into `files/oxmcp/servers.json`, keyed
  by URL so repeat imports do not duplicate. SSE, stdio/command and inmemory
  entries are counted as skipped, never executed. Tool allowlists, OAuth and
  client registration are not imported.
- Device QA: a zip with one HTTP and one SSE entry previewed “1 HTTP MCP, skip 1”,
  imported exactly one `KelivoHTTP` server with its header, and a repeat import
  created nothing. Test objects and the sample were removed.

## 0.26.2 fix — Operit background blur

- `use_background_blur` plus `background_blur_radius` now map to OrionekoX's
  1–3 image background blur (≥20 strong, ≥12 medium, otherwise light) instead of
  only switching bubbles to frosted. Liquid/water glass still maps to frosted.
- Device QA with radius 24: preview summary showed “模糊强”, the agent JSON stored
  `backgroundBlur=3`, and the preview wallpaper was visibly smoothed. The test
  override was removed.
- Limits: the radius threshold is heuristic; solid/gradient backgrounds show no
  visible blur; 9-patch assets, input styles, avatar shapes, padding/crop/repeat
  and video backgrounds remain unmapped.

## 0.27.0 implementation — preset preview

- The appearance preview now takes any snapshot; `presetSnapshot` reads the stored
  preset JSON without touching the active scope. Long-press preset menu offers
  预览 / 重命名 / 删除.
- Device QA: with an image-background global theme, previewing DarkSea showed its
  black solid background and light green bubbles, while the current theme's prefs
  file was unchanged after closing (mtime stayed at 02:20).
- Limits: the preview is the same lightweight sample (no media/tool bubbles,
  replies, avatars or real wrapping); missing preset assets fall back to inherited
  fields; no editing or side-by-side comparison.

## 0.28.0 implementation — Kelivo/Chatbox conversations

- Chatbox v2 zips are read through `manifest.json` and
  `sessions/*/session.json`; older zips with `sessions/**/*.json` and direct JSON
  exports using `chat-sessions-list` + `session:<id>` are also accepted.
- Each thread becomes its own local agent with a stable
  `kelivo_chat_<session>_<thread|current>` id. The first system message becomes
  the agent prompt (falling back to `defaultPrompt`), and session settings wire
  provider/model/temperature/topP/maxTokens (backup provider first, local
  provider otherwise).
- Messages keep their original epoch-ms timestamps; tool turns become
  `[工具] …` assistant bubbles, later system turns `[系统] …`, and
  images/files become `[图片]`/`[文件]` placeholders. Caps: 100 conversations,
  500 messages each, 20k characters per message.
- Imported history is replayed into the native message DB after `syncAgents`,
  so the stock chat screen shows the backup bubbles instead of a greeting.
  Re-import replaces history without duplicating messages.
- Device QA: a 2-session zip produced 3 conversations / 7 messages; a repeat
  import updated all 3 and kept 2/3/2 message rows. The native chat showed the
  system prompt as config, the user/assistant/tool bubbles and the original
  date. A legacy JSON sample imported 1 conversation with its 0.3/0.6/64
  settings. Test agents and samples were removed.
- Limits: attachments are placeholders only; tool calls are flattened; old
  timestamps keep conversations ordered by their original date; threads are
  independent agents with no multi-conversation parent UI; encrypted/real
  Kelivo exports still need a compatibility pass.

## 0.29.0 implementation — Kelivo/Chatbox avatars

- Chatbox v2 `manifest.json.resources[]` entries with `kind=avatar` or
  `kind=copilot-image` are mapped by `originalStorageKeys` and resource id.
  Both raw image bytes and `data-url-base64` text are accepted.
- Assistant avatars come from `avatar.storageKey`/`avatar.url`/`picUrl`; session
  agents use the manifest `meta.assistantAvatarKey`/`meta.picUrl`, then the
  session's own `picUrl`/avatar. Only resources inside the backup are used; no
  network fetch.
- Matching images are written to `files/oxagents/<agentId>/avatar.<ext>` and
  become `avatar_path`; repeat imports keep the existing avatar instead of
  overwriting a user-customized one.
- Caps: 64 candidate files, 2 MB each, 24 MB total; only PNG/JPEG/GIF/WebP
  magic is accepted. Preview and result report avatar counts.
- Device QA: one assistant plus one session with two resources produced two
  avatar files (raw 1310 B, data URL 1623 B), and the native chat headers showed
  the green S1 and blue AI avatars. Repeat import kept both hashes unchanged.
- Limits: chat images/files, backgrounds, fonts and remote avatars are not
  imported; HEIC/AVIF are skipped; real exports still need a compatibility pass.

## 0.30.0 implementation — Kelivo/Chatbox chat images

- Chatbox v2 `resources[]` now also maps `kind=image` (and image attachments)
  through their original storage keys. Message image parts match by
  `storageKey`, then by `url`.
- On import each matched image is copied to the agent's `media/` directory and
  recorded in `OxChatEntry.imagePath`; text becomes the photo caption and a pure
  image message keeps an empty caption.
- `replayImportedHistory` generates native photo bubbles with
  `SendMessagesHelper.generatePhotoSizes`; one image per message is attached and
  further images keep a `[图片]` placeholder.
- Caps: 40 images per conversation, 2 MB per image, 24 MB total, PNG/JPEG/GIF/
  WebP only. Preview/result report image counts.
- Device QA: a 4-message session with two images produced two files (2783 B and
  3272 B); the native chat showed PIC1 with its “看看这张” caption and PIC2 as a
  pure photo bubble. Repeat import kept both hashes and 4 message rows.
- Limits: no albums, files/audio/video or remote images; real exports still
  need a compatibility pass.

## 0.31.0 implementation — Kelivo/Chatbox attachments

- Chatbox v2 `parsed-attachment`/`raw-attachment` resources with non-image MIME
  types are mapped by storage key; message file parts match `storageKey`,
  `rawStorageKey` or `url`.
- `OxChatEntry` gains `filePath`/`fileName`, persisted as `file_path`/`file_name`.
  Imported attachments are copied to the agent's `media/` directory and replay
  through `buildLocalDocument` as native document bubbles.
- Text stays as the caption, pure-attachment messages may have an empty caption,
  and extra attachments keep a `[文件]` placeholder. The model context gets a
  `[文件] name` note; file contents are never executed.
- Caps: 20 attachments per conversation, 2 MB each, 24 MB total. Preview/result
  report attachment counts.
- Device QA: a 4-message session with a raw 35 B text file and a 38 B data-URL
  JSON produced two document bubbles (notes.txt with its caption, data.json pure);
  repeat import kept both hashes and 4 message rows.
- Limits: one attachment per message, audio/video shown as documents, remote
  files skipped; real exports still need a compatibility pass.

## 0.32.0 implementation — Chatbox backup export

- Ox settings offers “Export Kelivo / Chatbox backup”: a confirmation dialog
  explains the scope, then SAF creates `orioneko-chatbox-backup.zip`.
- All non-built-in agents are written once: agents with chat history become a
  Chatbox session (system prompt + messages), chatless agents become copilots.
  This avoids duplicate agents when the app imports its own export.
- Sessions carry text, image and file content parts; avatars, images and
  attachments are emitted as data-URL resources with SHA-256/size descriptors,
  global/session scopes and original storage keys.
- The manifest matches the Chatbox v2 format (`format`, `formatVersion=2`,
  `application`, `data`, `sessions`, `resources`, `warnings`, `stats`).
  API keys, provider URLs, Authorization headers, workspaces, memory, skills,
  MCP and themes are never exported.
- Device QA: the full export was 483,866 B and passed manifest/checksum/stats
  checks; a filtered 8,306 B subset round-tripped back into one assistant and
  one 4-message conversation with prompt, sampling, avatar, image and file
  intact. No `ox1:`/`sk-`/`test-key` appeared in the zip.
- Limits: export is all-or-nothing (no selection), round-tripped ids gain a
  `kelivo_` prefix, and a real Chatbox client has not imported the file yet.

## 0.33.0 implementation — per-agent Chatbox export

- `OxKelivoExporter.exportTo(context, uri, onlyIds)` accepts a selected agent
  set; the original all-agent export remains available in Ox settings.
- The agent long-press menu (non-built-in) now offers “Export Chatbox backup”
  with a confirmation that explains the scope and that API keys/provider URLs
  are excluded. SAF creates `<agent name>-chatbox-backup.zip`.
- Only the selected agent is written: chat history as a session, chatless agents
  as a copilot, with its avatar/images/attachments. No other agents leak in.
- Device QA: a single-agent export was 6,190 B with 1 session, 0 copilots and
  3 resources; stats/checksums passed and no `ox1:`/`sk-`/`api_key`/`test-key`
  appeared. Re-importing it restored prompt, 0.5/0.7/80, avatar, image, notes.txt
  and 4 messages, shown in the native SE/SE1 chat.
- Limits: one non-built-in agent per export, no multi-select; round-tripped ids
  gain a `kelivo_` prefix; a real Chatbox client is still untested.

## 0.34.0 implementation — skills export

- The skill library now offers “Export skills zip”, writing every visible
  SKILL.md as `skills/<id>/SKILL.md` into an SAF-created zip. Duplicate ids get
  `_2`, `_3` suffixes, and only the SKILL.md body is exported (no scripts,
  attachments or keys).
- The import side now strips the `skills/` container prefix, so re-importing
  this zip restores `<id>` instead of creating a duplicate `skills_<id>`.
- Device QA: a 150 B QASkill0340 was exported in a 2,353 B/7-entry zip; a
  272 B filtered subset was re-imported after deleting the private skill and
  restored exact `files/oxskills/QASkill0340/SKILL.md` content with no
  `skills_` duplicate.
- Limits: all-or-nothing export, SKILL.md bodies only, and external
  Kelivo/Chatbox/RikkaHub clients have not imported the zip yet.

## 0.35.0 implementation — RikkaHub image avatars

- RikkaHub `Avatar.Image` URLs (`file:`/`content:` or any type name containing
  `image`) are resolved by basename against the backup's `upload/` folder and
  copied to the agent's `avatar.png/jpg/webp/gif`. Network URLs are never
  fetched, and repeat imports preserve an existing avatar.
- `Avatar.Emoji` is now recognized by type-name substring, so RikkaHub's default
  FQN serialization (`...Avatar.Emoji`) maps its emoji.
- Preview reports “将导入 N 个助手图像头像” and the overview includes image
  avatars; the result note reports imported avatar count.
- Device QA: two assistants (Image from upload/RA and FQN Emoji 🦊); preview
  reported one image avatar; import created two agents with avatar.png 2,519 B,
  the native header showed the RA image, and the emoji agent kept 🦊. A repeat
  import updated both with the avatar hash unchanged.
- Limits: only upload/-backed images, basename matching, no network downloads;
  a real RikkaHub export still needs a compatibility pass.

## 0.36.0 implementation — multi-agent export

- Ox settings offers “Export selected agents”: a native scrollable checkbox list
  of non-built-in agents (none selected by default), then SAF creates
  `orioneko-selected-backup.zip`.
- The exporter receives only the checked ids, so no other agent, resource or
  session leaks into the zip. An empty selection shows “Select at least one
  agent”; cancelling SAF clears the pending ids.
- Device QA: two test agents (Multi B with 2 messages; Single Export with 4
  messages, avatar, image and notes.txt) were selected from the list. The export
  was 6,697 B with 2 sessions, 0 copilots and 3 resources, and only those two
  agents. Re-importing restored both conversations, image and attachment.
- Limits: no search/select-all in the list; still no workspaces, memory, MCP or
  themes, and no external-client validation.

## 0.37.0 implementation — MCP JSON export

- The MCP page now offers “Export MCP JSON”: SAF creates `orioneko-mcp.json`
  with an `mcpServers` map of HTTP servers (`type=streamableHttp`, `url`,
  `isActive`), plus a result count.
- Headers (including Authorization/API keys), OAuth, SSE/stdio entries and tool
  allowlists are never written; duplicate names get suffixes.
- Device QA: a test server containing `Authorization: Bearer QA_SECRET_0370`
  exported as a 390 B JSON with four HTTP servers and no headers field; the
  secret appeared zero times while the internal servers.json still kept it.
  After deleting the test server, re-importing the export restored its URL with
  empty headers and left the other three servers untouched.
- Limits: HTTP-only, no server selection, auth headers require manual re-entry,
  no connectivity test, and external clients have not imported the JSON yet.

## 0.37.1 i18n pass

- Added 27 Chinese strings covering the Kelivo/Chatbox conversation and export
  flow, MCP export, and skill-library export, and bumped the Chinese pack from
  98 to 99 so devices pick them up.
- Audit: every `R.string.Ox*` reference in `ui/Ox*.java` and `ox/*.java` now has
  a zh-cn entry (missing count 0).
- Device QA: Ox settings, skill library and MCP export rows are Chinese; the
  Kelivo preview reads “对话 1 段（2 条消息）/项目：Multi B” with no
  `Conversations:`/`Items:` fallback. Preview was cancelled without writing.

## 0.38.0 implementation — backup chat backgrounds

- Chatbox v2 `kind=background` resources and session/copilot `background`/
  `backgroundImage` references (storageKey, URL or string) are parsed and copied
  to the agent's `background.<ext>`; syncAgents then calls
  `OxChatTheme.applyImportedBackground` to persist an agent-scope image theme.
- An existing different background on the agent is preserved and the result notes
  “其余保留已有自定义背景”; applying the same path again is safe.
- Device QA: a 300×500 BG38 PNG was previewed as one background, imported into
  the conversation agent (14,884 B file plus agent_555000078.json with
  backgroundMode=image), and rendered as the native chat wallpaper. After
  changing the agent background to custom-bg.png, a repeat import applied zero
  backgrounds and left the custom path untouched.
- Limits: images only (no video), agent scope only (no per-dialog background),
  Chatbox export does not include this background yet, and real backups still
  need a compatibility pass.

## 0.39.0 implementation — backgrounds in Chatbox export

- `OxChatTheme.backgroundForAgent` exposes the agent's background file; the
  exporter writes it as a `kind=background` resource. Agents with chat get a
  session-scoped resource plus `session.background`; chatless agents get a global
  resource plus `copilot.background`.
- Background resources use the same data-URL payload, SHA-256/size descriptors
  and 2 MB/total caps as other assets; headers and keys are still excluded.
- Device QA: a BG38 agent exported to a 15,580 B zip with one session, one
  background resource and the correct storage key. After deleting the original
  agent/theme, re-importing restored background.png (14,884 B) and the agent
  theme, and the native chat showed the BG38 wallpaper again.
- Limits: only agent-scope image backgrounds (not global/per-dialog/video), no
  separate background count in the result toast, and no real Chatbox client test.

## 0.40.0 implementation — per-dialog backgrounds in export

- `OxChatTheme.backgroundForDialog` reads a conversation override; the exporter
  now prefers it over the agent background for agents with chat history, so the
  exported session background matches what the user actually sees.
- Chatless copilots still export the agent background. Resource format, caps and
  header/key exclusions are unchanged.
- Device QA: an agent with BG38 plus a dialog override to BG40 rendered BG40 in
  the native chat; the 14,441 B export's background resource decoded to the BG40
  sha256 (not BG38). After deleting the agent/theme/dialog files, re-importing
  restored background.png with the same BG40 hash and the BG40 wallpaper.
- Limits: the dialog-specificity is flattened to agent scope on re-import; only
  image backgrounds are exported (not global or video), and no real Chatbox
  client test yet.

## 0.41.0 implementation — RikkaHub document attachments

- RikkaHub `document` parts are matched against `upload/` entries, copied to
  `files/rikka_upload_<name>`, and stored on `OxChatEntry.filePath`/`fileName`;
  messages may be pure files with an empty caption.
- The importer counts new attachments and reports “导入附件 N 个”. It also tracks
  conversations that received new messages and calls
  `OxAgentsBridge.replayImportedHistory`, fixing the case where `chat.json` had
  history while the native chat still showed only the greeting.
- Device QA: a 1,064 B backup with one 37 B `report.txt` imported two messages
  and one attachment; the native chat showed a `report.txt` document bubble
  (37 B · TXT, caption “看附件”) and the assistant reply. A repeat import added
  no rows and left two messages; cleanup removed the agent/upload/sample and
  dialogs/messages returned to zero with no app FATAL/ANR.
- Limits: only the first document per message; images and documents only
  (video/audio skipped); only `upload/` matches (no remote/data-URL download);
  file entries are carried by the existing Chatbox exporter, but no export
  roundtrip was re-tested.

## 0.42.0 implementation — RikkaHub multi-attachment messages

- Every media part in a message is parsed (up to 20): images become photo
  entries; `document`, `video` and `audio` become file entries with the URL
  basename as fallback name. The first media shares the message text in the
  primary bubble; the rest are emitted as extra bubbles in part order.
- The dedupe key includes the image/file basename, so multiple attachments in
  the same node no longer collapse into one; all extra bubbles share the node
  timestamp, keeping later nodes in order and preserving 0.41.0 single-file
  history without duplicates.
- The result reports “导入图片 N 张” and an attachment count covering
  files/video/audio; the preview explains that multi-attachments are split.
- Device QA: a 2,016 B backup (text + two PNGs + report.txt + mp4 + mp3)
  imported as six messages with two photo bubbles and three file bubbles
  (`report.txt` 24 B · TXT, `clip.mp4` 24 B · MP4, `clip.mp3` 10 B · MP3),
  followed by the assistant reply. A repeat import left six entries and six
  native rows; cleanup returned dialogs/messages to zero with no app
  FATAL/ANR.
- Limits: max 20 media per message; video/audio are generic file bubbles with
  no thumbnail, inline player or duration; only `upload/` matches; no export
  roundtrip was re-tested this round.

## 0.43.0 implementation — Kelivo/Chatbox multi and mixed attachments

- `OxKelivoImporter.convertMessages` now walks `contentParts` in order and keeps
  every available image/file, instead of only the first image and first file
  (and no longer drops files when an image is present). The first media shares
  the message text; the rest become extra entries with empty captions.
- Per-conversation caps remain 40 images / 20 files; images are saved as
  `media/img_<n>`, files as `media/<n>_<name>` with the part `fileName`.
  `[图片]` / `[文件] name` placeholders are stripped after a successful match.
- The preview explains that multi/mixed attachments are split and counts the
  resulting image/file entries.
- Device QA: a 2,392 B backup with text + two PNGs (one data-URL encoded) +
  `notes.txt` + `data.json` produced five messages, two images and two files;
  the native chat showed a green photo with the text caption, an orange photo,
  `notes.txt` (19 B · TXT) and `data.json` (29 B · JSON), then the assistant
  reply. A repeat import replaced the history and left five entries/five rows;
  cleanup returned dialogs/messages to zero with no app FATAL/ANR.
- Limits: 40 images/20 files per conversation and 64 resource files; split
  entries share the source timestamp; `info` parts are ignored; remote URLs are
  not downloaded; no export roundtrip was re-tested.

## 0.43.1 fix — JSON attachments survive Chatbox roundtrip

- A multi-attachment export (3,578 B) contained five messages, two image
  resources and two `raw-attachment` resources; decoded payloads matched the
  source images/`notes.txt`/`data.json` hashes and no API-key/provider strings
  were present.
- Re-importing that export exposed a bug: the zip reader skipped every `.json`
  entry, so an exported `resources/orioneko-*.json` attachment never reached
  the resource map. The condition now only excludes JSON metadata outside
  `resources/`; `manifest.json`, `settings.json` and session JSON keep their
  existing read paths.
- Device QA: the same export previewed as “2 images / 1 attachment” before the
  fix and “2 images / 2 attachments” after it. The roundtrip produced five
  `chat.json` entries and the native chat rendered both photos, `notes.txt`
  (19 B · TXT), `data.json` (29 B · JSON) and the assistant reply.
- Limits: the roundtrip agent id gains a `_current` suffix, so a kept original
  would not be replaced; no real Chatbox client test yet.

## 0.43.2 fix — stable Agent identity across Chatbox roundtrip

- The exporter now writes the original Agent id into the session as
  `orionekoAgentId`. The importer keeps it in `Conversation.agentIdOverride`
  and uses it for both preview counts and actual import, instead of prefixing
  and suffixing the id again.
- Override ids are sanitized to `[a-zA-Z0-9._-]`, stripped of leading dots and
  truncated to 80 characters; external backups without the field still follow
  the old `kelivo_chat_<session>_current` path.
- Device QA: importing the mixed sample created `kelivo_chat_s1_current`
  (tgId 555000091); the exported 3,593 B zip carried the same id and override.
  Re-importing while the original was kept showed “已存在 1 段对话” and replaced
  the history. The agent directory count stayed at one, the tgId was unchanged,
  and both `chat.json` and the native chat still had five messages. Cleanup
  returned dialogs/messages to zero with no app FATAL/ANR.
- Limits: `orionekoAgentId` is an OrionekoX extension ignored by real Chatbox
  clients; chatless copilot exports were not validated; invalid or overlong ids
  are sanitized/truncated.

## 0.44.0 implementation — long-term memory in Chatbox backups

- Agents with chat history write `orionekoMemory` into their session; chatless
  agents write it into their copilot. Empty memory emits no field.
- Export caps memory at 64 KiB per agent and 256 KiB total, keeping the newest
  tail and adding a note when truncated. `Result.memories` counts agents that
  actually carried memory.
- The importer restores `orionekoMemory` for both copilots and conversations
  (200,000-character tail cap), replaces the existing memory file, and reports
  the count. The preview warns “将恢复 N 个智能体的长期记忆（覆盖现有记忆）。”.
  Backups without the field keep their previous behavior.
- The export result main line now includes the memory count, so the system
  Toast does not truncate a second line.
- Localization pack version 99 → 100: the cached `files/ox_zh-cn.xml` was
  reused after app updates, so new Chinese strings (including this dialog and
  Toast) stayed stale even though the APK asset had changed.
- Device QA: a 157 B / 73-character memory exported into a 1,626 B single-agent
  zip, with no `apiKey`/`baseUrl`/`Authorization`/`sk-` matches; deleting then
  importing restored 157 B with SHA-256 `a7744d01…`, and a repeat import kept
  the same hash and 25 agent directories. The final full export was 482,642 B;
  its Toast read “21 个智能体（含记忆 14 个）、12 段对话、245 条消息”, and
  the zip held 10/12 sessions and 4/9 copilots with memory (14 total). The
  settings row and confirmation dialog both showed “长期记忆”; no app
  FATAL/ANR.
- Limits: `orionekoMemory` is an OrionekoX extension ignored by real
  Chatbox/Kelivo clients; chatless copilot memory import was not round-tripped;
  import overwrites instead of merging memory.

## 0.45.0 implementation — SillyTavern / RikkaHub character cards

> Correction (0.45.1): the agent list already imported SillyTavern JSON
> cards since 0.8.114 and uncompressed PNG `tEXt` cards since 0.8.115.
> 0.45.0 duplicated the entry point under RikkaHub settings; 0.45.1 folds the
> enhancements back into the agent-list entry and restores the RikkaHub row
> to backup-only.

- The RikkaHub import entry now also accepts standalone PNG or JSON character
  cards. PNG files are routed through their `tEXt`/`iTXt` chunks, preferring
  `ccv3` and falling back to `chara`; the payload may be base64 or raw JSON.
  JSON files that are not RikkaHub settings are parsed as V2/V3 wrappers or
  flat V1 cards.
- `description`, `personality`, `scenario`, `system_prompt`,
  `post_history_instructions` and `mes_example` become labeled `AGENTS.md`
  sections. `first_mes` becomes the agent greeting, and the PNG itself is
  saved as `avatar.png` and materialized as a native profile photo.
- Stable ids are `rikka_card_<hash(name, description, first_mes)>`, so
  re-importing the same card updates one agent instead of creating copies.
  Existing avatars are preserved.
- Limits: PNG ≤ 8 MiB, card text ≤ 4 MiB, each field ≤ 32 KiB and the prompt
  ≤ 64 KiB; IHDR dimensions are capped at 4096 and 16M pixels to avoid a
  decompression bomb. Compressed `zTXt` is not read.
- Device QA: a 689 B JSON card created `rikka_card_df478b83` (tg id
  555000092) with six persona sections and the expected first message. The
  same card's 8,810 B PNG updated that agent, wrote an identical
  `avatar.png` (SHA-256 `4482184d…`), and produced a native greeting; a repeat
  import kept one directory, the same avatar hash and one greeting. A second
  JSON-only card created `rikka_card_1ac480d6` without an avatar, and a PNG
  without a `chara` chunk produced the card-specific error. Cleanup removed
  both agents and all dialogs/messages for their tg ids; no app FATAL/ANR.
- Limits: only `first_mes` is imported; alternate greetings, character books,
  tags and regex scripts are ignored, cards cannot be exported, imported cards
  start offline, and a real SillyTavern/RikkaHub export was not tested.

## 0.45.1 fix — one character-card importer

- The agent-list “Import character card (SillyTavern PNG/JSON)” entry now
  delegates to `OxRikkaImporter`: it verifies the file is a card, imports the
  six persona sections and `first_mes`, saves a PNG as `avatar.png`, supports
  `iTXt`, keeps a stable `rikka_card_<hash>` id, and greets through
  `syncAgents(true)`. The old random-id + name “· copy” path is gone.
- RikkaHub settings is backup-only again. Selecting a card there shows a
  pointer to the agent-list entry and writes nothing; `settings.json` and zip
  imports are unchanged.
- Device QA: an 8,285 B PNG created `rikka_card_b303c8ed` (tg id 555000094)
  with `avatar.png` SHA-256 `33f7346e…` equal to the source and the expected
  native greeting. Re-importing the same card as 346 B JSON updated that one
  directory, kept the avatar hash and did not duplicate messages. A card
  selected through RikkaHub settings produced the pointer error, while a
  297 B minimal RikkaHub backup still imported one offline assistant. Cleanup
  removed both test agents and their dialogs/messages; no app FATAL/ANR.
- Limits: same as 0.45.0 (`zTXt`, alternate greetings, character books, tags,
  export and real-client validation are still missing).

## 0.46.0 implementation — skills and skill links in Chatbox backups

- Exports now write `skills/<id>/SKILL.md` into the Chatbox zip. Full exports
  carry every visible skill; a selected-agent export carries only the skills
  those agents reference, so sharing one agent does not leak the whole library.
- Session and copilot objects carry `orionekoSkillIds`, restricted to skills
  actually written in the same zip. Caps: 50 skills, 512 KiB each, 8 MiB total.
- Copilots now also carry `orionekoAgentId`. Before this, a chatless copilot
  roundtrip re-derived a `kelivo_…` id and created a duplicate agent even though
  sessions already kept their identity since 0.43.2.
- The importer restores skills first, then applies `skill_ids` only for ids that
  imported successfully; preview and result report the number of linked agents.
  External backups without the fields keep the historical behavior.
- Device QA: a 90 B skill plus one session and one copilot agent exported to a
  1,762 B zip; the skill SHA-256 matched (`6cf9bd70…`), both objects carried the
  original id and skill link, and no API-key/provider strings appeared. After
  deleting the skill and clearing both `skill_ids`, the import reported one
  update, two restored links and one skill; both original agent directories and
  tg ids survived, the skill hash matched, and a repeat import stayed
  idempotent. Cleanup removed the test agents/skill and zeroed their
  dialogs/messages; no app FATAL/ANR.
- Limits: the `skills/` layout and private fields are OrionekoX extensions;
  only SKILL.md bodies travel (no scripts/attachments); full exports include
  unreferenced skills; real Chatbox/Kelivo clients were not tested.

## 0.47.1 implementation — per-agent workspaces in Chatbox backups

- Exports now write `workspace/<agentId>/<rel>` for each selected agent,
  recursively, preserving nested directories, binary content and non-ASCII
  names byte-for-byte. Caps: 200 files, 2 MiB per file, 8 MiB total, depth 6;
  empty or oversized files are skipped and canonical containment rejects `..`.
- The importer counts workspace entries in the preview (“restore N workspace
  files, overwriting same names”), restores only for agents that still exist,
  preserves unrelated files, and reports the restored count. It applies the
  same file/count/total caps and rejects absolute paths, backslashes, `.`/`..`
  and paths deeper than eight segments.
- 0.47.0 device QA exposed a long completion toast that Android truncated to
  two lines, hiding the workspace count. 0.47.1 compresses the toast to
  “agents, workspaces, chats, messages, memories, skills, files” and moves the
  workspace count to second place; the Chinese pack moved 106 → 107.
- Device QA: two self-created agents (one session, one chatless copilot) and
  four workspace files (20 B text, 16 B binary, 33 B nested Chinese path,
  23 B Markdown). Selected export was 2,215 B with four workspace entries;
  every restored byte matched and the secret scan was empty. After adding
  `keep.txt`, 0.47.1 exported 2,414 B / five entries and the toast showed
  “workspace 5”. A per-agent export was 2,055 B / four entries and included
  only that agent's files. Deleting three files while keeping `keep.txt`,
  then importing, restored four files, preserved `keep.txt`, kept both agent
  ids and tg ids, and a repeat import stayed idempotent. A zip containing
  `../../evil0470.txt` was rejected wholesale (`Invalid zip entry path`); a
  2 MiB + 1 B file was skipped (five restored, not six) and never written.
  No app FATAL/ANR; cleanup removed both test agents, their workspaces and
  six test zips, and their dialogs/messages were zero.
- Limits: `workspace/` is an OrionekoX extension ignored by real Chatbox/Kelivo
  clients (not tested with them); empty directories, permissions, timestamps
  and symlinks are not preserved; export-side caps were code-reviewed rather
  than exercised with a 2 MiB source file; deleting an agent leaves its
  workspace on disk; the contacts page needs a restart to drop a deleted local
  agent from its cache. A late 0.47.1 fix made the exporter use a read-only
  workspace lookup and collect only agents that actually appear in the backup,
  so exporting no longer creates empty workspace dirs and cannot emit orphan
  workspace entries.

## 0.48.1 implementation — agent preferences in Chatbox backups

- Session and copilot objects now carry a private `orionekoAgent` object, but
  only when the agent has at least one non-default preference, so ordinary
  agents do not gain noise. Fields: emoji, pinned, remark, first message,
  reasoning effort, tool choice, parallel tool calls, context length,
  presence/frequency penalties, top-k, seed, stop sequences and the per-tool
  `auto`/`ask`/`deny` policy map.
- Credentials are structurally excluded: no API key, base URL, provider id,
  headers or `extra_body` is written. Headers and `extra_body` can carry
  authentication even when they are not keys, so they stay out of the backup
  entirely. On import, provider wiring runs first and preferences are applied
  last, so a provider fallback cannot reset `context_length` (the 0.48.0 bug
  fixed in 0.48.1).
- Bounds mirror the UI: emoji 16 chars, remark/stop 2 KiB, greeting 16 KiB,
  policy map at most 256 entries restricted to `auto`/`ask`/`deny`, numeric
  values clamped to their allowed ranges. The preview reports how many agents
  will regain preferences, and the import never overwrites name, persona,
  avatar, memory, skills or chats.
- The export description and both confirmation dialogs list agent preferences
  and state that API keys, provider URLs and headers are omitted; the Chinese
  pack moved 107 → 108.
- Device QA: two self-created agents (one session with two messages, one
  chatless copilot) with pinned/high/required/32k versus low/none/64k and
  per-tool policies exported to a 1,881 B zip whose session and copilot both
  carried the full object. A full-text scan for `api_key`, `baseUrl`,
  `base_url`, `headers`, `extra_body`, `Authorization`, `Bearer`, `sk-` and
  `ox1:` returned zero. After resetting both agents to defaults and importing,
  the preview and result both reported two preference agents, every field
  matched the source including 32k/64k context, tg ids and the two directories
  were unchanged, and a repeat import stayed idempotent. The native tool page
  showed `get_time` denied and `device_info` ask, the pinned session led the
  agent list, and no app FATAL/ANR occurred. Cleanup removed both agents and
  the test zip, and their dialogs/messages were zero.
- Limits: `orionekoAgent` is an OrionekoX extension ignored by real
  Chatbox/Kelivo clients (not tested against them); headers, `extra_body`,
  provider URLs and keys must be re-entered after moving machines; emoji,
  remark and greeting were verified by `agent.json` readback rather than a
  chat-welcome screenshot; temperature/top-p/max-tokens still travel in the
  standard session/copilot fields.

## 0.49.0 implementation — character card export

- The agent long-press menu now offers “Export character card” for non-built-in
  agents. A native dialog chooses PNG (with avatar) or plain JSON; both go
  through the system document picker and explicitly exclude API keys, provider
  URLs and request headers.
- JSON is a `chara_card_v2` / `2.0` wrapper. The six sections written by the
  importer (`Description`, `Personality`, `Scenario`, `System prompt`,
  `Post-history instructions`, `Example dialogue`) are mapped back into their
  own fields; any other AGENTS.md goes into `description`. First message becomes
  `first_mes`. Flat top-level aliases keep older V1 readers working. Bounds are
  32 KiB per field and 16 KiB for the greeting.
- PNG uses the avatar as its base, scaled to at most 1024 px and 1.6 MP, with
  the card JSON embedded as Base64 in a `tEXt` chunk named `chara` after IHDR
  and a spec-correct CRC32. Agents without a usable avatar get a generated
  512×640 name card with their emoji, so export never depends on a remote
  image.
- Device QA used a self-created 256×256-avatar agent with six persona sections.
  The 2,420 B PNG had valid IHDR/tEXt/sRGB/sBIT/IDAT/IEND chunks, decoded to the
  same six sections and greeting, and its pixels matched the source avatar
  exactly. The 790 B JSON matched field by field. Both scanned clean for keys,
  provider URLs, headers and `extra_body`.
- Re-importing the PNG created one `rikka_card_…` agent with the right name,
  greeting, normalized section spacing and pixel-identical avatar; importing
  the JSON next updated that same directory without a duplicate and kept the
  avatar. A no-avatar agent exported a 20,888 B placeholder PNG (512×640) with
  valid embedded data. No app FATAL/ANR; test agents, cards and old test zips
  were removed and the test dialogs/messages were zero.
- Limits: real SillyTavern/RikkaHub clients were not tested; alternate
  greetings, character books, tags and regex are not exported; native agents
  keep their whole AGENTS.md in `description`; emoji is not a V2 field and
  returns as the default 🤖; avatar re-encoding drops EXIF/ICC/animation.

## 0.50.0 implementation — appearance themes and fonts in Chatbox backups

- Session and copilot entries now carry a private `orionekoTheme` when the agent
  has its own appearance override. It covers mode, background mode/colors/
  alpha/dim/blur/AMOLED/gradient, in/out bubble colors and opacity, corner
  radius, bubble style, border color/width, text color, font scale and line
  height. Agents that follow the global look are not turned into fake
  overrides.
- Absolute local paths are stripped. The background continues to travel in the
  existing `background` resource, while chat and code fonts become
  `fonts/<agentId>/chat.ttf` and `code.ttf` with limits of 8 MiB per file,
  16 MiB total and 40 files. A QA pass caught the generic 2 MiB resource reader
  silently dropping a 2.37 MB chat font; a dedicated font reader fixed it and
  the rebuilt export carried both fonts with matching SHA-256 values.
- Import validates fonts with `Typeface.createFromFile`, writes them into
  app-private storage, and applies the theme only after the agent exists. A
  local per-agent override wins over the backup; fonts written for a skipped
  theme are deleted. Preview and result report theme and font counts, and the
  export info/confirmation strings mention appearance themes.
- Device QA used a chatless copilot (dark background, dim 40, blur 2,
  `#263238`/`#7C4DFF` bubbles, 230 alpha, 20 dp corners, cyan 2 dp border,
  1.12× text, 1.3× line height, 2.37 MB chat font and 108 KB code font) and a
  session agent with one message (green/brown bubbles, 14 dp corners, green
  border, 1.05× text, 1.15× line height, one font). Exports were 1,453,353 B
  and 1,383,082 B: theme JSON paths were empty, background and font hashes
  matched the sources, and secret scans were clean.
- After deleting the theme, fonts and background, both imports restored one
  theme, the expected font count and the background. Every color, radius,
  border, scale and line height matched (float readback differs only at
  1e-7), the restored font/background hashes matched, and the native chat
  showed the dark bubble with cyan border. Re-importing the copilot backup
  restored zero themes, kept the local theme, and left no orphan font files.
  No app FATAL/ANR; test agents, themes, fonts and zips were removed and the
  test dialogs/messages were zero.
- Limits: `orionekoTheme`/`fonts/` are OrionekoX extensions ignored by real
  Chatbox/Kelivo clients (not tested); only per-agent overrides travel, not the
  global theme or per-dialog overrides; an existing local override wins;
  fonts must parse on the device; video backgrounds are not carried.

## 0.51.0 implementation — global appearance in Chatbox backups

- When the global appearance is in custom mode, `settings.json` now carries a
  private `orionekoGlobalTheme` with the same fields as the per-agent snapshot:
  colors, bubble opacity, corner radius, style, border, text, scale, line
  height, background mode/dim/blur/AMOLED/gradient. Native global appearance is
  not written as a fake custom theme.
- Absolute paths are stripped. The global wallpaper travels as a
  `background` resource with `scope=global` and a storage-key reference;
  global chat/code fonts travel as `fonts/global/chat.ttf` and `code.ttf`,
  sharing the 8 MiB per-file and 16 MiB total budget with per-agent fonts.
- Import reads the global theme and background reference, writes the wallpaper
  to `files/ox_chat_bg_imported_<timestamp>.img`, validates fonts with
  `Typeface.createFromFile`, and applies the snapshot only when the local
  global appearance is not already custom. A local custom global theme wins,
  and assets written for the skipped import are deleted. Preview/result report
  the global theme and font counts.
- Device QA used the existing custom global theme (deep blue image, frosted
  bubble, pink border) plus a 2.37 MB chat font and 108 KB code font. The
  1,457,238 B export carried the theme with empty paths, a 16,360 B global
  background whose SHA-256 matched, and both fonts with matching hashes; the
  secret scan and absolute-path check were clean. After deleting the prefs and
  fonts, the import restored the exact colors/radius/border/scale/line height,
  new background and font hashes, and the native chat showed the global look.
- Re-importing the same zip left the prefs hash unchanged, reported that the
  local custom global theme was kept, and left the font/background counts
  unchanged. The user's original prefs were backed up and restored byte-for-byte
  after QA. No app FATAL/ANR; the test agent, imported assets and zip were
  removed and the test dialogs/messages were zero.
- A late copy check showed the longer export info line was truncated to two
  lines by SettingCell; the info line was shortened and the Chinese pack moved
  111 → 112, with the full line verified on device.
- Limits: `orionekoGlobalTheme` and the global assets are OrionekoX extensions
  ignored by real Chatbox/Kelivo clients (not tested); per-dialog overrides are
  still not carried; a local custom global theme wins; fonts must parse; video
  backgrounds are not carried.

## 0.52.0 implementation — local memory library and context compression

- Each agent gets a private SQLite library (`databases/oxmemory.db`) holding
  readable turn notes, extracted facts and dated chunk summaries. Entries carry
  created/last-access times, access count, importance, stability, short/long
  term state, source and a dedupe hash. The cap is 4,000 entries per agent.
- Normal recall ranks token overlap and substring matches. The experimental
  vector mode encodes text into a 128-dimension local signed-hash vector over
  English words and CJK characters/bigrams, then blends cosine with keyword
  score. Neither mode calls an external embedding service.
- Both modes apply a forgetting curve (7-day half-life for short-term, 90-day
  for long-term, plus access and importance terms). Recall updates last-access
  and access count; a short-term entry is promoted after two recalls or when
  importance reaches 0.8. Stale, never-accessed, low-importance short entries
  are pruned after 30 days.
- When the chat exceeds the configured threshold (default 45 entries), the
  oldest not-yet-compressed turns are written to the library, user messages
  with fact markers become high-importance facts, and the batch gets a dated
  summary. Only the newest `max(8, threshold/3)` turns stay verbatim. The
  system prompt receives compression notes, a digest and query-relevant
  memories; `memory_state` prevents re-ingesting the same turns.
- `remember` and `recall` now use the library (while keeping memory.txt
  compatibility). New tools: `memory_search(query, limit)` returns dated,
  scored evidence; `memory_verify(query)` requires at least two meaningful
  shared terms for multi-word claims and otherwise says no record was found.
- Device QA used a 60-message agent with name/birthday/coffee/city/editor facts
  and a local mock OpenAI server. In normal mode the first request carried only
  17 messages while the system prompt contained compression, digest and recall
  blocks; `memory_search` returned the birthday fact. The library held 47
  turns (46 short, one promoted), 3 facts and one summary. Vector mode returned
  the same facts with a 0.61 blended score. `memory_verify` found the birthday
  but correctly reported no evidence for "favorite color" instead of matching
  "favorite editor" on one shared word.
- The memory page shows total/long/short/fact counts, recent readable entries
  and a clear action; the agent editor exposes mode, threshold and keeps the
  editable memory.txt. An initial layout squeezed the library title to
  “本地…”, fixed by shortening the stats line. Chatbox `orionekoAgent` now
  carries memory mode/threshold/recall, but the SQLite library itself is local
  only. No app FATAL/ANR; test agent, rows and test dialogs were removed.
- Limits: local hashed vectors are not semantic embeddings; summaries are
  extractive rather than model-written; the library is not in Chatbox/.oxagent
  backups; memory.txt is not auto-migrated; groups share the agent library;
  the threshold counts messages, not token percentage; the private SQLite is
  not encrypted.

## 0.53.0 implementation — memory retrieval v2

- Schema v2 adds difficulty, valid-from, invalid-at and subject keys, migrating
  existing v1 rows in place. Facts are now split on English/CJK sentence
  punctuation instead of merging a whole paragraph into one fact.
- Retrieval is hybrid: BM25 is computed in Java over the agent's documents and
  normalized per query, then blended with vector cosine (vector mode only),
  power-curve retention, importance and access count. Normal mode is
  BM25-dominant; vector mode adds the local hashed vector. MMR removes
  near-duplicate results before formatting.
- The fixed-half-life decay was replaced by the FSRS-style power curve
  `R=1/(1+ageDays/(9*S))`. Successful recall increases stability and lowers
  difficulty; a short-term entry is promoted at two recalls or stability >=21
  days. Invalidated facts are excluded from normal recall and are only returned
  (penalized) for explicit historical queries.
- Fact consolidation follows Mem0's ADD/UPDATE/NOOP/soft-DELETE idea: a richer
  superset updates the existing fact; a same-subject value change (birthday
  3/14 to 3/15, blue to green) or numeric/negation conflict marks the old fact
  invalid without deleting it. Invalidated facts are removed from active
  summaries, and summaries older than six are merged into a rolling summary.
- `memory_verify` returns facts first and suppresses historical turns when a
  fact matches. The system prompt says to prefer the newest fact on conflicts
  and treat old turns/summaries as history only.
- Device QA used 60 messages with a birthday update, a color change and
  filler. Active facts ended as name/coffee/Hangzhou/birthday 3/15/green;
  birthday 3/14 and blue were invalidated; the summary lost the stale lines.
  `memory_search` ranked the active 3/15 fact first, and `memory_verify` for
  birthday/color returned only the active facts. Vector mode ranked the
  coffee fact at 0.83. Recall raised stability and lowered difficulty.
- Limits: the vector is still a local hash projection, not a semantic
  embedding; summaries remain extractive; there is no model distillation,
  graph memory or token-pressure warning; current providers expose no
  embedding model (opendesign model_not_found, opencode-go 403). The next
  rounds add embedding-provider abstraction, asynchronous LLM extraction/
  summaries and a recall@k benchmark before claiming semantic superiority.

## 0.54.0 implementation — asynchronous model distillation

- Compression still writes deterministic turn/fact/summary entries immediately,
  then a background `OxMemoryDistiller` calls the agent's own chat model for a
  batch of at least eight messages. It asks for strict JSON: one abstractive
  summary (<=1200 chars) and up to 12 atomic facts (<=300 chars, importance
  0.3–0.95), resolving relative dates and preserving corrections without
  invention.
- The call uses the agent's existing base URL, key and headers; no new
  credential is stored and keys are never logged. It runs with
  `stream=false`, a 14k-character transcript cap, 2 MiB response cap and
  connect/read timeouts. Any HTTP/parse failure leaves the extractive memory in
  place and never blocks the chat turn.
- Model facts pass through the same subject consolidation/invalidation logic;
  model summaries are stored with `source=model` and importance 0.9. The digest
  injects at most one summary, preferring the model summary, with facts shown
  first. A per-batch key prevents duplicate distillation.
- QA exposed a subject-key bug for third-person facts: "Lin Zhou's birthday…"
  and "Lin Zhou's favorite color…" both fell back to `lin zhou s`, invalidated
  each other and scrubbed the model summary. A possessive rule plus stopping
  the subject at is/are/like/and now yields `birthday` and `favorite color`.
- Device QA with the 60-message fixture and a mock `[MEMORY_DISTILLER]`
  response: the async request arrived after compression without delaying the
  reply, the model birthday fact and model summary became active, the color
  fact merged into the existing green fact, and the next system prompt held
  five active facts plus only the model summary. Old 3/14/blue values and the
  extractive summary were absent from the prompt. No duplicate request or app
  FATAL/ANR; test data was removed.
- Limits: distillation runs only on compression batches and consumes the
  agent's tokens; output is schema/length-validated but not fact-checked;
  providers must return JSON-compatible content; there is still no semantic
  embedding provider; the failure path is code-reviewed rather than exercised
  with a dead endpoint.

## 0.55.0 implementation — offline open-source-style compaction

- LLMLingua and LongLLMLingua need a 7B-class scorer; LLMLingua-2 needs a
  BERT/ONNX model and mobile benchmarks, so neither is bundled. The offline
  path follows MemGPT recursive summaries, LangChain SummaryBufferMemory's
  summary-plus-recent-window design, Selective Context's budget pruning and
  TextRank's sentence graph.
- `OxContextCompactor` estimates CJK/Latin tokens, builds a sentence similarity
  graph (damping 0.85, 20 iterations), drops near-duplicate sentences (>0.65),
  selects with MMR (>0.55) and writes one sentence per line under a 300-token
  budget. The rolling summary includes the previous summary and the new batch.
- Compaction triggers on the configured message threshold or, when a context
  length is set, on estimated tokens above 45% of it. Under token pressure the
  recent window is chosen from the trailing 55% budget (minimum 256 tokens and
  four messages), with an explicit note in the system prompt. The old fixed
  15-message override was removed.
- The async distiller receives the previous summary and merges it into the new
  abstractive summary; a request cutoff invalidates older summaries so only one
  current summary is injected. Facts remain first and at most one summary is
  shown.
- Device QA: the 60-message fixture produced a 249-character TextRank summary
  containing the coffee/Hangzhou lines, then the model `MODEL_SUMMARY` replaced
  the extractive summary; old 3/14/blue values stayed out of the prompt. A
  20-message fixture with `context_length=500` triggered at ~790 tokens and kept
  seven recent messages after compacting thirteen. No app FATAL/ANR; test data
  was removed.
- Limits: TextRank is a lexical approximation, not LLMLingua-2 semantic token
  compression; token estimation is approximate; the fallback summary budget is
  300 tokens; there is no separate 70% warning stage. External harness agents
  must use their own compact, not this path; harness routing is the next round.

## 0.56.0 implementation — harness ownership routing

- `OxHarnessAdapter` is a capability registry for API direct, OxLinux, Claude
  Code, OpenCode, CommandCode, OpenClaw, Hermes, CodeBuddy, DeepSeek Harness,
  Pi, other-device and generic transports: local compact, harness compact,
  resume/session, tool protocol, multimodal and non-text event support.
- `appendMemoryContext` now branches on ownership. Only `api` runs the local
  TextRank/SummaryBuffer compactor, writes the memory library and injects
  digest/recall. External harnesses keep their own history: no local compact,
  no memory write, no inject. The prompt gets a short “context and compact are
  managed by <harness>” note and `maybeCompact` asks the harness to compact.
- `maybeCompact` is rate-limited to once per half threshold window and runs off
  the chat thread. `requestCompact` substitutes `{session}`/`{cwd}` and can
  execute only the OxLinux command hook through `OxContainerManager.run`;
  other transports return `transport-not-connected`. Only Claude Code and
  CommandCode have default templates, and those are unverified live.
- Agent model/UI/backup: `harness`, `harness_session`,
  `harness_compact_command`, a native harness picker, session and command rows,
  plus validated Chatbox export/import. Chinese pack 114→115.
- Device QA: `qa_harness_0560` (harness=claude, 60-message fixture) sent 22
  messages on the default recent window; compression note, memory digest and
  recall were all absent, the system tail named “Claude Code”, and the agent's
  memory-library row count was zero. No app FATAL/ANR; test data, proxy and
  accessibility were restored.
- Limits: this release is routing and isolation, not working adapters for every
  harness. There is no SSH/ACP/Gateway/persistent-PTY transport yet, so
  Claude/OpenCode/CommandCode/OpenClaw cannot be driven or compacted for real;
  Hermes/CodeBuddy/DSH/Pi are uninstalled and unverified. Per-harness tool
  schemas, multimodal and non-text event mapping, and external-memory mirroring
  remain open. See `docs/harness-adapters.md`.

## Gaps / next phases

1. Operit theme-v1 remaining media: 9-patch bubble assets, avatar/font URIs,
   input styles, per-bubble padding and image cropping (background blur is now
   mapped by 0.26.2).
2. Kelivo full backup import beyond the now-supported assistants/skills/
   conversations/MCP/avatars/images/attachments/long-term memory: video
   backgrounds and external Kelivo workspace layouts (OrionekoX's own
   workspaces now self-roundtrip through Chatbox); SSE/stdio MCP with their
   tool allowlists; RikkaHub non-selected branches, per-conversation grouping,
   skill attachments and standalone providers; native RikkaHub audio/video
   playback bubbles, more than 20 media per message and remote attachments.
   SillyTavern cards now import and export (V2 JSON/PNG), but alternate
   greetings, character books/lorebooks and tag filters remain.
3. Bidirectional export still lacks per-dialog overrides, video backgrounds,
   MCP headers/OAuth and server selection, plus validation against real
   external clients; agent identity (including chatless copilots), skills and
   their per-agent links, long-term memory, per-agent workspaces, per-agent
   preferences (tool policies, pin/remark/greeting,
   reasoning/context/sampling), per-agent appearance and the global appearance
   (colors, bubbles, fonts, background) now survive self-roundtrip, but
   external Chatbox clients remain unverified.
4. Semantic persona recommendation (the 0.22.0 keyword scoring and 0.25.1
   confirmation/undo are deterministic and offline).
5. Per-conversation theme inheritance still copies the parent’s text color; a
   low-contrast combination is reported by the check row but not auto-corrected.
6. Re-run the third A/B/C round on current builds once a real-device/GPU pass can
   confirm background layering and blur on local groups; cover per-agent themes,
   font import/export, preset preview, gradients, group albums, tool bubbles,
   literal `\n` in old messages, skill dedupe/delete and local-agent search.
7. Memory library next: export/import it through Chatbox/.oxagent, optional
   offline/remote embedding providers with dimension and model validation,
   fact-checking of model-distilled memories, migration of existing memory.txt,
   per-group scoping and a recall@k/MRR benchmark on real conversations.
   Abstractive summaries and atomic-fact distillation now exist (0.54.0).
8. Harness adapters: 0.57.0 adds a host bridge with verified compact/resume
   for OpenClaw, CommandCode and OpenCode; 0.58.0 adds per-session context
   telemetry (`/context`, usage/budget/should_compact) and a Harness context
   row, with harness-reported compaction advice participating in the compact
   decision. 0.59.0 adds an explicit, default-off harness→local memory mirror
   (stable-text dedupe, mirror stats/labels, no injection back into the
   harness) and verifies local recall after switching the agent to API mode.
   Claude Code still returns 403, and OxLinux is blocked by Android app-data
   exec policy. Still open: ACP/native transports, Hermes/CodeBuddy/DSH/Pi,
   a real `should_compact=true` device trigger, per-harness thresholds, full
   tool-schema repair, native multimodal/non-text messages, and
   OpenClaw tool-result visibility.

9. OxUI(preview) is the first independent front end: Claude-inspired serif
   editorial conversation, chartreuse signal accent, agent rail and a shared
   local send path. 0.61.0 adds a real root switch, Agents panel with search,
   agent detail + memory stats and long-press copy; 0.62.0 adds image
   attachments end-to-end (SAF picker → real photo message → vision turn),
   locally-created-group groundwork with a Groups panel, contrast/breathing/
   auto-scroll/retry fixes from a subagent visual review. 0.63.0 wires New Group
   creation to the offline local-chat path, so groups are now listed in the
   Groups panel and messageable from OxUI with Telegram UI sync. 0.64.0 adds
   the offline avatar branch on group creation (ImageUpdater returns local
   PhotoSizes instead of FileUploader; attachLocalChatAvatar round-trips a
   TL_chatPhoto with volume/local-id file copies, verified to survive
   force-stop restart), per-agent attribution on group replies
   (OxChatEntry.sender rendered as "Name · time" meta) and targeted
   "Reply <agent>" from the long-press menu (forces that member's turn).
   0.65.0 extends avatars to existing groups (offline edit with a
   millis+chatId photo id and a Remove-photo dialog, both verified across
   force-stop restarts), fixes the 0.64 targeted-reply dispatch bug (the
   forced path never fired because the reply target was cleared before
   dispatch; corrected and re-verified with the groupReply log), adds quote
   semantics for targeted replies (snapshot fields + prompt prefix + muted
   quote line on the bubble), and lands the first seven items of the third
   visual review: unified image+caption card, 0.6-viewport image height cap
   with center-crop, post-send scroll re-alignment, removal of the Preview
   badge, rail fade, full-height assistant accent rule, and
   markdown-stripped list subtitles. 0.66.0 closes the rest of the third
   review's list: in-design long-press menu (bone card, SIGNAL reply dot,
   anchored with back-key/render dismissal), user-bubble timestamps and
   copy menu, a snapped type scale (11/12/14/16/17/28 across 30 sites with
   RIPPLE/SENDING_ALPHA constants), an attachment capsule with a
   dimensions/size mono line, and confirmation that the tall-screenshot
   "split card" was a screenshot-reading mistake (bounds prove image and
   caption share one ink card; image now centers in the wider card). It also
   adds v1 session search: the panel query scans local agent chats and
   group logs off-thread (3 per target, 12 total, 220 ms debounce) and
   renders SIGNAL-highlighted message hits under a "Messages" section.
   0.67.0 adds the dark variant (palette resolved from system night mode
   with status-bar icon inversion; light unchanged), reduced-motion gating
   driven by the system animator scale, an accessibility pass over every
   symbol-only control (cancel/attach/send/search/rail/menu/image cards),
   and in-conversation search v1.5 (header magnifier, n/N counter, cyclic
   up/down navigation, 80dp scroll lead and a SIGNAL row flash). 0.68.0 adds
   an in-app settings sheet (theme: follow system / light / dark, applied
   and persisted immediately, plus version/about) reached from the Agents
   footer, and an agent editor (name, remark, model, persona, multi-select
   SKILL.md association) opened from the agent detail, with BACK closing
   overlays instead of leaving the app. 0.69.0 turns the multi-model design
   battle (7 model-designed variants archived in design-battle-shots/) into a
   skin system: nine presets distilled from those directions plus a token
   editor (7 colors, radius and type scale, JSON export), a human-voice clay
   accent alongside the chartreuse action signal, monogram avatars with model
   tags, and a shell layer toggling navigation (rail / bottom / floating
   dock), surface material (solid/acrylic/mica/glass), dock position (top /
   above input / bottom) and rail/footer visibility, all persisted in the
   skin JSON. The terminal is a first-class surface with Termux/Termius-
   inspired session tabs, grouped quick keys, history, Tab completion, a mono
   type-scale cycle and honest local-only commands. 0.70.0 adds
   component-level toggles to the skin (message density, timestamps,
   assistant accent rule, dock item visibility), JSON import alongside
   export, a genuinely blurred floating dock (snapshot downscale + box blur
   + material tint, refreshed on scroll) and eleven built-in terminal
   palettes in the Termius/Solarized/Dracula/Nord family with an
   accent-driven prompt, session underline and send key. 0.71.0 rebuilt the
   eleven palettes around measured WCAG contrast (text >= 6.6, dim >= 4.0,
   accent >= 4.75; output lines no longer dimmed by indentation) and replaced
   the emoji secret key with uniform Termius-style PWD/PST text keys backed by
   a local-only terminal secret kept out of the exported skin JSON.
   0.72.0 starts the OxUI parity backlog: documents (any non-image/video file)
   now send from OxUI with a Photo/File attach menu, a 📄 capsule preview and
   a paper file bubble (name, size, caption, timestamp) that survives
   restarts, backed by real TL document messages and a file placeholder in
   the agent prompt. Still open:
   density for the composer and lists, file/QR skin sharing, ANSI-16
   terminal colours, avatar creation via emoji markup in agent-only mode,
   user-avatar RPC family, offline title editing, cross-chat search
   aggregation, video/GIF/file attachments, avatar/provider/sampling/tool-
   policy fields in the agent editor, tablet layout, a full TalkBack pass,
   token-level streaming. It is not yet a complete replacement for Telegram
   UI.
