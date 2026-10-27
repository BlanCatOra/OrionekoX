# Changelog

> AI disclosure: entries after 2026-10-24 were produced by the AI coding agent
> **blancatora** (DeepSeek Harness, model `deepseek-v4.1-flash`). See [NOTICE](NOTICE).
> This project is an independent GPL-2.0 fork of DrKLO/Telegram.

## 0.24.0 — 2026-10-28

- RikkaHub backup import now reads `rikka_hub.db` and imports the selected text
  branch of each conversation into the matching local agent chat.
- For every message node the importer takes `messages[select_index]`, keeps only
  user/assistant text parts, and appends with deterministic timestamps;
  re-imports are deduplicated. Limits: 200 conversations, 500 nodes each, 5000
  messages, 100k characters per message.
- The preview reports conversation/message-node counts and the result reports how
  many messages were imported.
- Verified with a schema sample: two conversations/four nodes imported the
  selected `Branch B selected` (not the unselected branch) and stayed at four
  entries after a repeat import. Images, files, tool parts and attachments are
  not imported.

## 0.23.0 — 2026-10-28

- Added a one-tap AMOLED pure-black toggle in the appearance editor. It paints
  the current scope's wallpaper and system bars pure black while keeping bubbles,
  text, border, font and line height; turning it off restores the stored
  wallpaper.
- The `amoled` flag persists through global/agent/chat scopes, presets and theme
  packages, and the import parser accepts boolean or 0/1 aliases.
- Device QA: with an image background, enabling showed pure black in the editor
  preview and a local group; disabling restored the image and cleared the flag.

## 0.22.0 — 2026-10-28

- Added persona-based appearance recommendation on agent appearance pages:
  local keyword scoring over the agent name and AGENTS.md selects one of the six
  built-in themes, with 深海 as the deterministic fallback. No model or network
  request is made.
- Applying a recommendation replaces the agent's current appearance snapshot and
  shows the chosen theme name; the agent remains scoped to that override until
  cleared.
- Device QA: `barB-nokey` (深夜/酒吧 persona) recommended 赛博霓虹 and rendered
  the expected gradient, purple outgoing bubble, cyan text and 2 dp pink border.

## 0.21.2 — 2026-10-28

- RikkaHub provider `customHeaders` and assistant `customHeaders` are merged by
  header name (assistant overrides provider) into the agent headers; provider
  headers apply when one of its models is referenced by an assistant.
- The first assistant text part in `presetMessages` is imported as the agent's
  opening message (`firstMes`, capped at 4000 characters).
- Fixed a merge bug where pairs were round-tripped through JSON arrays and
  provider headers disappeared; merging now collects from the source arrays.
  Verified both headers and the opener survive a repeat import.

## 0.21.1 — 2026-10-28

- RikkaHub backup import now resolves assistant `enabledSkills` against the
  imported/local skill library by skill name or id and writes the matches to the
  agent's `skillIds`; an empty list preserves the existing selection.
- Assistant `customHeaders` are mapped into the agent's request-header array;
  empty arrays also leave existing values untouched.
- Verified with a schema sample: `Rikka QA` with skill `Rikka Test Skill` and
  header `X-Rikka-Assistant: 1` imported as `skill_ids=["TestSkill"]` and
  `headers=[["X-Rikka-Assistant","1"]]`; repeat import updated the same agents
  and kept both mappings.

## 0.21.0 — 2026-10-28

- Added a live appearance preview in the custom appearance editor: a native
  dialog renders the active scope's wallpaper (including image blur and dim),
  incoming/outgoing sample bubbles, border, text color, chat font, font scale
  and line height.
- The preview reads the current snapshot on every open, so global, per-agent and
  per-chat scopes each show their own values; changing blur and reopening the
  preview reflects the new wallpaper treatment.
- It is a lightweight sample rather than real message cells: media/tool bubbles,
  replies, avatars and real wrapping are not shown, and frosted bubbles remain an
  alpha simulation.

## 0.20.0 — 2026-10-28

- RikkaHub backup import now also reads `skills/<name>/SKILL.md` from the same
  zip (up to 50 skills of 512 KB each) and writes them into the app-private
  skill library through the new `OxSkillStore.importSkill` entry point.
- Skill ids come from the folder relative to `skills/`; repeat imports update
  the same skill instead of creating copies and never delete other local skills.
- The preview and result dialogs report skill counts and existing skills; a
  `settings.json`-only import still works with zero skills.
- Verified with a schema sample: preview “2 assistants · 1 provider · 1 MCP ·
  1 skill”; import created the skill with frontmatter intact; repeat import kept
  exactly one skill directory. Test objects and the sample file were removed.

## 0.19.0 — 2026-10-28

- Added RikkaHub backup import: pick a backup zip or `settings.json` and preview
  the assistants, referenced providers and streamable-HTTP MCP servers before
  writing.
- Assistants map their system prompt, model wiring, emoji avatar, temperature,
  topP, max tokens and reasoning level. Repeat imports update agents with ids
  `rikka_<assistant UUID>` while preserving `tg_id` and custom avatars; MCP
  servers are deduplicated by URL.
- The chat database, attachments, skills, OAuth tokens and SSE MCP servers are
  intentionally skipped. Preview and result dialogs never show API keys.
- A schema-derived sample verified preview, create, idempotent update, headers
  and rejection of a non-RikkaHub file; the test agents and MCP entry were
  removed afterwards. A real RikkaHub export is still needed for a full pass.

## 0.18.0 — 2026-10-27

- Added portable preset bundles (`.oxthemes`): a zip with `bundle.json` and the
  background/font assets referenced by every named preset. Export all presets,
  preview the count/names/duplicate names before importing, and restore assets
  into app-private storage with rewritten paths.
- Imports skip existing preset names instead of overwriting, validate the whole
  bundle before writing (100 presets, 300 entries, 8 MB per asset, 40 MB total),
  and remove files created by a failed import.
- Device QA: colour-only presets re-imported idempotently (0/2); a preset with a
  16 KB wallpaper was exported, deleted, restored (1/2) and applied; an invalid
  file was rejected without touching presets or assets.

## 0.17.1 — 2026-10-27

- Fixed per-chat appearance being skipped on the first open after a cold start:
  the chat activity now re-applies the theme once the message list exists.
- Local groups now honour per-conversation appearance, including background
  blur; the group editor and scoped reset were verified on a local test group.
- Clearing a chat/agent override now reports “已清除覆盖，现跟随上层外观”
  instead of the global “reset to native” message.
- QA note: `-gpu off` repeatedly crashed the emulator host renderer when a chat
  opened; group verification used `-gpu guest`. This is an emulator limitation,
  not an app FATAL/ANR, and a real-device/GPU pass is still pending.

## 0.17.0 — 2026-10-27

- True image-background blur with three levels (轻/中/强), implemented with a
  cached stack blur at 3×/5×/8× downscale. The value travels through global
  preferences, per-agent/per-chat JSON, presets and `.oxtheme`; imports accept
  `backgroundBlur`, `background_blur`, `blur`, `blurSigma` and `blur_sigma`.
- Device measurement on an agent chat: the visible wallpaper strip's grayscale
  standard deviation fell from 29.54 (off) to 0.49–0.54 (strong), and edge
  energy from 2.1 to 0.03.

## 0.16.0 — 2026-10-27

- Per-conversation appearance: a new chat menu icon (local agent chats and local
  groups) opens the appearance editor scoped to that conversation. Overrides are
  stored in `files/ox_chat_dialogs/dialog_<id>.json` and read as
  chat → agent → global; scoped pages inherit the parent look and offer a
  “clear override and follow parent” action.
- Verified on an agent chat: the override rendered immediately, another agent
  kept the global theme, the override survived a force-stop/relaunch, and
  clearing it restored the global look. The local-group entry is wired but not
  device-verified because opening a local group repeatedly crashed qemu through
  Chromium on the `-gpu off` emulator.

## 0.15.0 — 2026-10-27

- Configurable message line height for agent chats: a new row below font size
  cycles 1.0–1.5×, and the value persists in global/per-agent snapshots, named
  presets, JSON exports and `.oxtheme` packages. Imports (including 0.8–1.8
  values) recognise `lineHeight`, `line_height`, `lineSpacing` and
  `line_spacing`.
- `MessageObject.TextLayoutBlocks` applies the multiplier only to the currently
  themed agent dialog, covering text, quote and code blocks; other Telegram
  chats stay at 1.0. Verified at 1.4× on a long Markdown message, and the
  exported theme's `theme.json` contained `lineHeight=1.4`.

## 0.14.1 — 2026-10-27

- Theme every agent-chat bubble: the appearance snapshot now overrides all eight
  bubble drawables (incoming/outgoing, selected, media and media-selected) and
  restores them when the chat closes. Previously only the text bubbles were
  covered, so agent tool-call hints kept the native white media bubble and their
  white custom text became invisible. Verified on existing history without
  sending new messages.

## 0.14.0 — 2026-10-27

- Import Operit / Operit2 appearance backups: unwraps the nested `values`
  object and maps AI/user bubble and text colors, font scale, background
  opacity, theme mode, glass flags and the primary color (border). The same
  preview → confirm → write path is used, so invalid or unrelated JSON leaves
  the current theme untouched. Operit detection requires Operit-specific keys,
  so Kelivo/RikkaHub files are not misread as Operit.
- Verified on device with an Operit preference file: preview and applied rows
  matched, and an agent chat rendered the mapped purple bubbles, white text and
  pink 1dp border over the retained wallpaper.

## 0.13.0 — 2026-10-27

- “Auto palette from background”: samples the wallpaper (or averages the
  gradient/solid colors), then generates a readable incoming/outgoing bubble
  pair, text color, border and dimming. Verified with a dark-blue wallpaper on
  R2C Card while keeping the wallpaper.

## 0.12.0 — 2026-10-27

- Six one-tap built-in themes: AMOLED “Midnight Ink”, Paper, Deep Sea,
  Sakura, Forest and Cyberpunk; each writes a coherent snapshot to the current
  global or per-agent scope.
- Fixed custom text colors: `ThemeDelegate` now overrides
  `chat_messageTextIn/Out` while a custom theme is active, so cyberpunk's cyan
  text and other overrides are no longer reset to the Telegram theme color.

## 0.11.0 — 2026-10-27

- Appearance export now produces a portable `.oxtheme` package containing
  `theme.json`, the chat background and imported chat/code fonts. Import
  detects JSON or the package, previews the included assets, restores them to
  app-private storage and rewrites the snapshot paths.
- Verified on device with an imported TTF and image background: export → reset
  font → re-import → font and background restored.

## 0.10.0 — 2026-10-27

- Per-agent chat appearances with global fallback and “apply to all agents”.
- Solid/gradient/image backgrounds, independent background opacity and dimming,
  system bar colors and a wallpaper-layer override.
- Solid/frosted bubbles, bubble borders, text color and a WCAG contrast hint.
- Chat/code font families with TTF import; JSON import preview before applying;
  theme export to a file; shortcut to Telegram's native chat theme.
- Agent chats retry an empty first history pass (0.9.1) and file-picker images
  become photo bubbles with vision.

## 0.9.1 — 2026-10-27

- Agent chats no longer reopen blank: stale scroll positions are ignored, an empty
  first history pass gets one bounded retry, and the stock “Delete this chat”
  overlay is suppressed while local history loads.
- File-picker images now become photo bubbles with thumbnails and reach vision;
  outbound tool-call bubbles show a short summary instead of raw JSON.
- Theme JSON import accepts snake_case keys (`in_bubble`, `out_bubble`,
  `corner_radius`, `font_size`, …) and Kelivo-style aliases.
- Assistant text double-escaped newlines are decoded before persistence;
  SillyTavern `first_mes` becomes the greeting; skills overwrite on re-import and
  can be deleted; local agents match substring searches.

## 0.9.0 — 2026-10-26

- New Agent chat appearance page: Telegram-native or custom mode, background
  color/image, incoming/outgoing bubble colors, opacity, radius, message font
  scale, named presets, JSON import and one-click native reset.
- Second-round independent test reports A/B/C and the consolidated issue tracker.

## 0.8.103 — 2026-10-26

- Local groups: removing an agent member is fully local. Long-press a member →
  从群组中移除; member count and online count refresh immediately, without a
  `deleteChatUser` request.
- Fixed local-group profile subtitle ordering so “2 位成员, 2 人在线” is shown in place.
- Public repository documentation, AI disclosure, bilingual README, release and
  sanitization checklist, Kelivo/RikkaHub/Operit compatibility analysis.

## 0.8.102 — 2026-10-26

- Local groups: adding an agent after creation is fully local, with no
  `addChatUser` request.
- Fixed `chatInfoDidLoad` fourth-argument NPE in `TopicsFragment` and a duplicate
  member row caused by `ProfileActivity`’s optimistic append.

## 0.8.100 — 2026-10-26

- Local-group creator row renders the real self user (BlanCat · 在线 · 创建者).
- Agent profile actions trimmed to 消息/静音; bot-only share/report/block entries hidden.

## 0.8.95–0.8.99 — 2026-10-26

- Local-group profiles rebuild and persist `ChatFull`, so members and the
  add-member row survive restart.
- Diagnosed the blank creator row and the media bubble X/0KB regressions.

## 0.8.87 — 2026-10-26

- Media bubbles render correctly after local document injection
  (`attachPathExists`/`mediaExists`), files archived to the agent inbox.

## 0.8.55–0.8.56 — 2026-10-25

- Local-only group text is persisted locally with double checks and per-group
  conversation history; group replies use the group log.

## 0.0.1–0.0.6 — 2026-10-24/25

- Fork rebrand, standalone package and signing, dual-ABI sing-box/VPN, local agent
  system, tool calling with per-tool permissions, terminal, MCP, skills, workspace,
  workflows and Chinese localization.

Full per-round evidence lives in
[docs/OrionekoX-alpha-0.0.1-构建报告.md](docs/OrionekoX-alpha-0.0.1-构建报告.md)
and `docs/qa-*.md`.
