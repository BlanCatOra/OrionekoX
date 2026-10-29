# Changelog

> AI disclosure: entries after 2026-10-24 were produced by the AI coding agent
> **blancatora** (DeepSeek Harness, model `deepseek-v4.1-flash`; since 2026-10-29
> also the WorkBuddy agent). See [NOTICE](NOTICE).
> This project is an independent GPL-2.0 fork of DrKLO/Telegram.

## 0.82.0 — 2026-10-29

- OxUI stage 8.2: "edited" marker parity. Messages edited in OxUI now carry an
  `edited` flag on their OxChatEntry (`chat.json` / group log) and render a
  ` · edited` label beside the timestamp, matching the Telegram side's edited
  marker — previously only Telegram showed it (the OxUI entry was rewritten in
  place but stayed unmarked). Covers user and assistant text bubbles in 1:1 and
  local groups. Media captions are not editable yet, so they remain unmarked.

## 0.81.0 — 2026-10-29

- OxUI stage 8.1: agent list management.
  - Archive / unarchive an agent from the long-press menu. Archived agents leave
    the main block and collect under a collapsible `Archived (N)` section, so they
    stay one tap away without cluttering the list.
  - Rename an agent inline from the same menu (empty names rejected); the
    synthetic Telegram dialog name is resynced afterwards.
  - List ordering now sorts archived last, then pinned, then built-in, then name.
  - New `OxAgent.archived` persisted in `agent.json` (`archived`), backwards
    compatible (missing -> false).

## 0.80.0 — 2026-10-29

- Memory stage C (real offline semantic embeddings): unblocked without an external
  embedding endpoint by adding a fully local LSA model (`OxEmbeddingProvider`).
  - TF-IDF document-term matrix -> truncated SVD via a small-Gram Jacobi
    eigendecomposition -> dense 48-dim latent vectors. Pure Java, no native
    library, no bundled model asset, no network.
  - New `MODE_SEMANTIC` ("semantic") recall mode: cosine over learned latent
    semantics, fused with BM25 + forgetting curve; MMR diversity reuses the same
    vectors. Selectable in the agent profile (off -> normal -> vector -> semantic).
  - Independent `mem_embeddings` table (schema v4) keyed by (agent_id, mem_id),
    stamped with model + dim so a model/dimension change or text edit is detected
    and rebuilt (7.2). Rebuild is transaction-batched and throttled (5s).
  - Honest fallback: when the corpus is too small (`MIN_DOCS=8`) the provider
    returns null, recall falls back to lexical scoring and metrics record
    `semantic-unavailable`. It never fabricates vectors; `semantic` stays opt-in
    (default remains `normal`) until an end-to-end baseline comparison shows a
    stable win (7.4).
- Algorithm verified twice: a Python prototype and a standalone Java replica of
  the same math both show same-topic similarity well above cross-topic
  (see `docs/qa-0.80.0.md`). `assembleAfatDebug` BUILD SUCCESSFUL.

## 0.79.0 — 2026-10-29

- Memory stage B (precise recall + Harness bridge):
  - Precise recall channels: `id:`/`mem:` fetch a single memory by public_id; a quoted `"exact phrase"` is pinned to top score.
  - Structured filters `who:`/`people:`, `topic:`/`topics:`, `status:` narrow the candidate set before scoring.
  - Harness Memory Bridge: external (non-local) harnesses now receive the pinned CORE layers + a query-driven recall + recall/memory_verify entry points, without taking over context compaction (honors harness ownership). New `OxMemoryBridge.harnessContext`.
  - Metrics surface: `OxMemoryStore.metricsJson()` reports last recall latency, hit count, injected token count and mode.
  - Tokenizer already supports Chinese unigram+bigram and English word segmentation (local, no network dependency).
- See `docs/qa-0.79.0.md` for acceptance notes and known gaps (6.6 baseline comparison pending end-to-end data).

## 0.78.0 — 2026-10-29

- Memory stage A: `memory.md` (Markdown) is now the single source of truth for
  agent memory; SQLite (`oxmemory.db`) is a fully rebuildable derived index
  (schema v3: `public_id/people/topics/status/supersedes`). All writes go
  Markdown-first with atomic append (fsync) / temp+rename rewrites.
- Pinned read-always layers: CORE / ABOUT_USER / ABOUT_AGENT / CURRENT are
  seeded into `core.md` (ABOUT_USER prefilled from legacy `memory.txt`) and
  injected at every session start, replacing the raw `memory.txt` block.
- Fact consolidation now records a `supersedes` version chain in the Markdown
  source of truth; richer replacements update the entry in place, so review
  and rebuild stay consistent.
- Honest labelling: the "向量模式" recall mode is now labelled 词面混合
  (hashed n-gram projection, NOT a semantic embedding).
- Legacy migration (SQLite rows + `memory.txt`) is lazy, idempotent and
  hash-deduplicated; the index can be rebuilt from Markdown at any time.
  Forgetting-curve counters reset to fixed documented defaults on rebuild.
- See `docs/qa-0.78.0.md` for acceptance evidence and known gaps.

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
