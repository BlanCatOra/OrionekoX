# Changelog

> AI disclosure: entries after 2026-10-24 were produced by the AI coding agent
> **blancatora** (DeepSeek Harness, model `deepseek-v4.1-flash`). See [NOTICE](NOTICE).
> This project is an independent GPL-2.0 fork of DrKLO/Telegram.

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
