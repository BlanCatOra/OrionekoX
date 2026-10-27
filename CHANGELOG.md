# Changelog

> AI disclosure: entries after 2026-10-24 were produced by the AI coding agent
> **blancatora** (DeepSeek Harness, model `deepseek-v4.1-flash`). See [NOTICE](NOTICE).
> This project is an independent GPL-2.0 fork of DrKLO/Telegram.

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
