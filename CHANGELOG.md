# Changelog

> AI disclosure: entries after 2026-10-24 were produced by the AI coding agent
> **blancatora** (DeepSeek Harness, model `deepseek-v4.1-flash`; since 2026-10-29
> also the WorkBuddy agent). See [NOTICE](NOTICE).
> This project is an independent GPL-2.0 fork of DrKLO/Telegram.

## 0.88.0 — 2026-10-30

Verification-only release: closes the three end-to-end gaps that had been
carried as `⚠️ pending` since their implementation landed. No behaviour change
in 8.3 / 8.4 / 9.4 themselves — what was missing was a way to *prove* them, not
the code.

- **8.3 group reverse-sync — verified end to end.** The implementation shipped
  in 0.83.0, but no local group had ever actually been created on the test
  device (`ox_groups.xml` was an empty map the whole time), so every attempt
  only ever hit an empty list. Built one through Telegram UI → New Group →
  two agents. Deleting a message from OxUI now reproduces the full chain:
  `onNativeMessagesDeleted` → `deleteMessages local-group ... cacheOnly=true`
  → `deleteOxEntry`. `log_1800000001` dropped 2 → 1 entries with the user turn
  pruned and the agent reply kept; `cacheOnly=true` confirms nothing was sent
  to Telegram's servers.
- **8.4 search navigation — verified end to end.** In-chat `⌕` opens the search
  bar; searching `needle` over two messages reports `1/2` and `↓` advances to
  `2/2`, scrolling to and signalling the matching bubble.
- **9.4 terminal ANSI-16 colouring — verified end to end.** The earlier note
  claiming ESC could not be injected was misleading. The OxUI panel terminal
  is not a shell at all — it has a fixed command set and renders plain
  `TextView` text, so ANSI never had a chance there. `OxTerminalActivity` (the
  Telegram UI Terminal tab) is the real one: `ProcessBuilder("/system/bin/sh")`
  plus `ansiToSpannable`. Verified by writing a fixture with SGR sequences to
  `/data/local/tmp/a.txt` and `cat`-ing it: all 16 foreground colours, 4
  backgrounds, bold and underline render correctly, and non-SGR CSI
  (`ESC[2J`, `ESC[1;1H`) is stripped rather than printed.
- Known, unfixed: a local group opened from **Telegram UI** loads zero messages
  (`processLoadedMessages size 0`) even though `dialogs.last_mid`, `messages_v2`
  and `chats` all hold the right rows — the chat page keeps showing the
  "You have created a group" card. OxUI's own group view reads the store
  directly and works fine, which is how 8.3 was verified.
- Test-harness note: the emulator must not run with `-gpu off`; opening a group
  chat page then segfaults qemu's RenderThread deterministically
  (`SEGV_MAPERR`, same offset both times). `-gpu swiftshader_indirect` is fine.

## 0.87.0 — 2026-10-30

- OxUI: the Agents panel footer is reachable again, so Settings / Terminal /
  Open Telegram UI can actually be tapped.
  - `showAgentPanel()` sized `listScroll` as `height=0, weight=1` inside a panel
    that is the single child of an `AT_MOST`-measured `ScrollView`; with
    `AT_MOST` the weighted child resolves to 0, the list overflowed onto the
    footer and those three links had no clickable nodes at all.
  - The list is now `WRAP_CONTENT` with a 150dp minimum, and the footer is two
    rows (hint / links). Verified on emulator-5554: the links are real
    clickable nodes, tapping Settings opens the appearance panel, and
    Cozy ↔ Compact still resizes the composer (116px ↔ 95px).

## 0.86.0 — 2026-10-30

- OxUI stage 9.2: density now scales the composer, not just bubble gaps.
  - `OxUiSkin.density` only tightened bubble row gaps before (`rowGap` ×0.62);
    the composer — the tallest fixed element on screen — ignored it entirely,
    so "compact = more conversation per screen" never held where it mattered.
  - New `sd()` box-metric helper (×0.82 under compact) applied to the composer
    wrapper, composer padding, ＋/↑ buttons, input padding + minHeight, footer
    row and the attachment row including its thumbnail.
  - Type sizes are deliberately left alone: trading legibility for space is the
    wrong trade in a chat app.
  - Measured on emulator-5554: input 116px → 95px, attach 105×116 → 86×95,
    send 116 → 95 (−18.1%, matches the 0.82 design value).

## 0.85.0 — 2026-10-30

- OxUI stage 9.1: video / GIF attachments.
  - The composer's ＋ menu now offers Photo / **Video** / File; picking a video
    imports it into `oxui_media` with its original name.
  - A video or GIF file renders as a video bubble instead of a 📄 file chip: a
    poster frame decoded with `MediaMetadataRetriever`, a ▶ affordance, and a
    `VIDEO`/`GIF · WxH · M:SS · size` meta line. Tapping opens the system player
    through the app's FileProvider.
  - Pending (in-flight) sends show the same video bubble at reduced alpha.
  - The agent bridge already understood video/GIF uploads, so existing chats
    with video attachments now render correctly too.

## 0.84.0 — 2026-10-30

- OxTerminal 支持 ANSI-16 颜色渲染（9.4）：终端输出此前为纯 `TextView`，shell
  程序的着色转义序列（如 `ls --color`、`grep --color`、`git` 着色）会以乱码显示。
  新增 SGR 解析与 `Spannable` 渲染，支持 16 色前景/背景、粗体、下划线；其余 CSI
  （光标移动、清屏）与 OSC（窗口标题）序列被剥离，不再污染显示。跨流式分块沿用
  着色状态，refresh / clear / restart 时正确重置。扩展色（256/真彩）做安全剥离，
  不渲染。详见 `docs/qa-oxui-0.84.0.md`。诚实缺口：真机着色截图缺实证，列为
  ⚠️ 待端到端验证（ESC 序列无法经软键盘/adb 输入）。

## 0.83.0 — 2026-10-29

- OxUI stage 8.3: 群组反向同步（group reverse sync）。
  - 此前「反向删除同步」(0.77) 仅接入 agent 对话；本地群（dialogId<0 且
    `OxGroupStore.isLocalGroup`）消息在 Telegram UI 被删除时，OxUI 群记忆存储
    未同步裁剪。本轮在 `MessagesController.deleteMessages` 的 shunt 中为本地群
    新增分支：置 `cacheOnly=true` 并调用 `OxAgentsBridge.onNativeMessagesDeleted`，
    对齐 agent 路径。真实 Telegram 群（非本地创建）不命中分支，行为不变。
  - `onNativeMessagesDeleted` 裁剪后调用 `refreshDialogRow`，使会话列表即时更新。
  - 诚实缺口：打开本地群聊天页在模拟器中曾致 qemu 退出，无法做端到端取证；
    本项仅构建验证 + 代码审查通过，标注 ⚠️ 待端到端验证。

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
