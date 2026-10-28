# Changelog

> AI disclosure: entries after 2026-10-24 were produced by the AI coding agent
> **blancatora** (DeepSeek Harness, model `deepseek-v4.1-flash`). See [NOTICE](NOTICE).
> This project is an independent GPL-2.0 fork of DrKLO/Telegram.

## 0.63.0 — 2026-10-29

- Wires the New Group flow to offline local chat creation: in agent-only
  mode, MessagesController.createChat maps selected contacts to local agents
  (real contacts are skipped, they cannot be invited offline), creates the
  group through OxAgentsBridge.createLocalGroup, and posts
  chatDidCreated/chatDidFailCreate via runOnUIThread so the create screen
  recovers cleanly. An empty agent set fails instead of hanging on a server
  request that ConnectionsManager drops without callbacks.
- Groups are now end-to-end: created in Telegram UI, listed in the OxUI
  Groups panel (titles resolve after cold start via getChatSync), messageable
  from OxUI (rail group chip), and synced with Telegram UI.
- Fixes three regressions: renderGroupList wiping agent rows, missing
  getChatSync title fallback after cold start, and the rail group chip /
  target reset lost in the 0.62 replay. Offline avatar upload on group
  creation is documented as the next gap.

## 0.62.0 — 2026-10-28

- Adds image attachments end-to-end in OxUI: a ＋ button opens the system
  image picker, the URI is imported into files/oxui_media (16MB cap), and
  sending builds a real photo message plus an agent vision turn through the
  same local path as Telegram UI. OxUI renders OxChatEntry.imagePath as a
  rounded image and refreshes signature-aware so image-only turns appear.
- Adds locally-created-group groundwork: OxGroupStore.markLocallyCreated/
  isLocallyCreated (set by createLocalGroup), an OxUI Groups panel with group
  chat view and local send path, and a group chip in the agent rail. Group
  creation itself is not wired yet (createLocalGroup has no caller), so the
  chain is documented as groundwork, not verified end-to-end.
- Applies visual-review fixes: MUTED/LINE contrast, breathing reply dot gated
  on the system animator scale, bottom-scroll only when near the bottom or just
  sent, and a coral "message not sent · tap to retry" row after a 120s pending
  timeout.
- Device QA verifies the image flow with an echo agent (real photo message +
  reply visible in both UIs). Test data removed; interface mode restored.

## 0.61.0 — 2026-10-28

- OxUI becomes a real root for plain launcher starts when
  `interface_mode=oxui`; deep links/shares keep the Telegram path. The OxUI
  header switches back to Telegram UI by clearing the task, and back in root
  mode backgrounds the task instead of revealing Telegram UI.
- Adds an Agents panel with name/remark search, tap-to-chat, long-press agent
  detail (harness/model, memory stats and recent memory), and long-press
  message copy. Detail layout, keyboard offset, Markdown list subtitles and
  selectable-text long-press conflicts were fixed.
- Device QA covers root launch, switching back/forth, filtered search, detail
  memory and Chat return. Test agent/data removed; `interface_mode` restored to
  Telegram UI. Groups, attachments/media, chat search, settings, agent editing
  and tablet layout remain open.

## 0.60.0 — 2026-10-28

- Adds **OxUI (preview)**, an independent front end selectable from the
  onboarding final page and Ox Settings. It does not use Telegram cells or the
  action bar: serif editorial conversation, agent rail, chartreuse signal
  accent, ink user pills and a floating paper composer.
- OxUI reads real local agents/history and sends through
  `OxAgentsBridge.sendFromOxUi`, the same local path as Telegram UI, so a
  message appears in both. Markdown is converted to native spans and copy is
  localized.
- The app root remains Telegram UI. Groups, attachments/media, search,
  settings, memory pages, tablet layout, dynamic theme and full parity are
  still open; the entry is explicitly a preview.
- Design rationale, references and token system: `docs/oxui-design.md`.

## 0.59.0 — 2026-10-28

- Adds a default-off **Mirror harness turns to local memory** switch for
  bridged agents. User/assistant turns and any tool events the app actually
  received are written to `OxMemoryStore` with `harness-mirror` sources. Stable
  text hashes make retries and restarts idempotent.
- Mirror rows are visible in memory stats and the Memory library dialog
  (`镜像对话`/`镜像工具`) but are never injected back into the harness. Switching
  the agent to API mode makes them available to ordinary local recall; device
  QA recalled three mirrored rows at 0.89/0.81/0.80 after the switch.
- Chatbox `orionekoAgent` export/import carries `harness_mirror`. The idle
  monitor now keeps the automation disabled for 12 seconds after dispatch to
  avoid a second delivery racing the current round.
- Boundaries: OpenClaw's `--json` does not expose tool results, so its mirror
  usually contains only user/assistant rows; mirror storage is the same
  plaintext SQLite library as local memory; a real `should_compact=true` trigger
  and Claude/OxLinux support remain unverified.

## 0.58.0 — 2026-10-28

- Bridge sessions now retain the last turn's usage/context (tokens, context
  budget, model and `should_compact`; never prompt or reply text), attach it to
  `done` events and expose `GET /v1/sessions/<id>/context`. OpenCode context is
  completed from its private server's session API; OpenClaw and CommandCode
  values come from their own run events.
- The agent editor gains a **Harness context** row showing values such as
  `in 530 · out 179 · 无需 compact`. Harness-reported `should_compact` now
  participates in the compact decision with a 60-second cooldown, in addition
  to the local history threshold.
- Real values were captured for OpenClaw (budget 1,048,000), CommandCode
  (inputTokens 16,669) and OpenCode (input 2,916, cache_read 4,224). A true
  `should_compact=true` trigger was not forced, and Claude Code remains blocked
  by the provider's 403.
- 15 bridge protocol/parser tests pass. External memory mirroring, per-harness
  threshold configuration and OpenClaw tool-result visibility remain open.

## 0.57.0 — 2026-10-28

- Adds `tools/ox-harness-bridge.py`, an authenticated HTTP/NDJSON bridge that
  runs real Claude Code, CommandCode, OpenCode and OpenClaw CLIs on the host
  with their own native sessions. The Android app talks to it through
  `OxHarnessTransport`; external-harness replies no longer fall back to the
  local model or echo mode.
- OpenClaw, CommandCode and OpenCode are verified through turn, resume,
  harness-native compact and a post-compact turn. Claude Code's transport runs,
  but the provider returned HTTP 403 for this account, so it is not claimed as
  a working model path.
- Normalizes text, tool calls/results, status, attachments and errors into one
  event schema; tool activity is visible in the chat. Adds bridge URL, token
  (Vault-wrapped), working directory, model and OpenClaw-agent fields plus a
  native connection test.
- Fixes harness agents being misclassified as echo mode, compact being skipped
  when local memory is disabled, bridge compact being blocked by an empty
  command-template check, a worker-thread UI update, and repeated rootfs
  downloads.
- OxLinux now uses one persistent `proot` shell with a sentinel channel, but
  Android denies execution of app-data binaries (`execve ... Permission
  denied`); this is documented as a platform blocker, not working support.
- 13 bridge protocol/parser tests pass. The bridge has no TLS; use loopback or
  a trusted LAN with the bearer token.

## 0.56.0 — 2026-10-28

- Adds `OxHarnessAdapter`, a capability registry and ownership router for API
  direct, OxLinux, Claude Code, OpenCode, CommandCode, OpenClaw, Hermes,
  CodeBuddy, DeepSeek Harness, Pi, other-device and generic agents.
- Only API direct agents use OrionekoX's local TextRank/SummaryBuffer compact,
  write the memory library and receive summary/recall injection. External
  harness agents keep their own history: no local compact, no memory writes and
  no injection. They get a short prompt note naming the harness that owns
  context, plus a rate-limited compact request.
- `requestCompact` substitutes the configured session id and can execute the
  OxLinux hook through `OxContainerManager`. Transports that do not exist yet
  return `transport-not-connected` instead of pretending to work. Only Claude
  Code and CommandCode ship default command templates, and those are not yet
  verified against a live session.
- Adds `harness`, `harness_session` and `harness_compact_command` to the agent
  model, a native 12-mode selector in the agent profile, and validated Chatbox
  export/import of the new fields. Chinese pack 114→115.
- Device QA: a 60-message Claude Code fixture sent 22 messages on the default
  recent window, with no local compression note, digest or recall, a system tail
  naming the external harness, and zero memory-library rows. No app FATAL/ANR;
  test data was removed.
- Limits: this is routing and isolation, not working adapters for every
  harness. ACP/Gateway/SSH/persistent-PTY transports, per-harness tool and
  multimodal normalization, non-text event mapping and external memory
  mirroring are not implemented. See `docs/harness-adapters.md`.

## 0.55.0 — 2026-10-28

- Rebuilt compact around offline open-source ideas: MemGPT recursive summaries,
  LangChain SummaryBufferMemory's summary-plus-recent-window design, Selective
  Context budget pruning and TextRank sentence selection. LLMLingua/LongLLMLingua
  need a 7B-class scorer and LLMLingua-2 needs a BERT/ONNX model, so neither is
  bundled.
- `OxContextCompactor` estimates CJK/Latin tokens, builds a sentence similarity
  graph, removes near-duplicate sentences, selects with MMR and writes one
  sentence per line under a 300-token budget. Rolling summaries include the
  previous summary plus the new batch.
- Compaction triggers on the message threshold or, when a context length is
  set, on estimated tokens above 45% of it. Under token pressure the recent
  window comes from the trailing 55% budget (minimum 256 tokens and four
  messages); the old fixed 15-message override was removed. The system prompt
  notes the estimated token count.
- The async model distiller receives the previous summary and merges it into the
  new abstractive summary. A request cutoff invalidates older summaries so only
  one current summary is injected; facts remain first and at most one summary
  is shown.
- Device QA: the 60-message fixture produced a 249-character TextRank summary
  containing the coffee/Hangzhou lines, then the model `MODEL_SUMMARY` replaced
  the extractive summary; old 3/14/blue values stayed out of the prompt. A
  20-message fixture with `context_length=500` triggered at ~790 tokens and kept
  seven recent messages after compacting thirteen. No app FATAL/ANR; test data
  was removed.
- Limits: TextRank is a lexical approximation, not LLMLingua-2 semantic token
  compression; token estimation is approximate; the fallback summary budget is
  300 tokens; there is no separate 70% warning stage. External harness agents
  must use their own compact; harness routing is the next round.

## 0.54.0 — 2026-10-28

- Adds asynchronous model distillation for memory. After a compression batch of
  at least eight messages, `OxMemoryDistiller` calls the agent's own model in
  the background for a strict-JSON abstractive summary and up to 12 atomic
  facts. It reuses the agent's base URL, key and headers with bounded
  transcript, response and timeouts, deduplicates batches, and never blocks the
  chat turn; HTTP or JSON failures keep the extractive fallback.
- Model facts pass through the same subject consolidation/invalidation logic;
  model summaries are stored with `source=model` and importance 0.9. The digest
  injects at most one summary, preferring the model summary, with facts first.
- Fixes possessive subject extraction so third-person facts such as
  “Lin Zhou's birthday…” and “Lin Zhou's favorite color…” no longer collapse
  into one subject, invalidate each other, and scrub the model summary.
- Verified with the 60-message fixture and a mock `[MEMORY_DISTILLER]` response:
  the background request arrived after compression without delaying the reply,
  the model birthday fact and summary became active, the color fact merged into
  the existing green fact, and the next system prompt contained five active
  facts plus only the model summary. Old 3/14/blue values and the extractive
  summary were absent. No duplicate request or app FATAL/ANR; test data was
  removed.
- Limits: distillation runs only on compression batches and consumes the
  agent's tokens; output is schema/length-validated but not fact-checked;
  providers must return JSON-compatible content; there is still no semantic
  embedding provider; the failure path is code-reviewed rather than exercised
  with a dead endpoint.

## 0.53.0 — 2026-10-28

- Rebuilt memory retrieval after reviewing Mem0, MemGPT, Graphiti, Generative
  Agents, FSRS, MemoryBank and LangMem. Retrieval now combines BM25 (computed in
  Java per agent), local vector cosine (vector mode), power-curve retention,
  importance and access count, then applies MMR to avoid near-duplicate
  results.
- Schema v2 adds difficulty, valid-from, invalid-at and subject keys and
  migrates v1 rows in place. The fixed half-life decay became the FSRS-style
  power curve `R=1/(1+ageDays/(9*S))`; recall raises stability and lowers
  difficulty, and entries promote to long-term at two recalls or stability
  >=21 days.
- Facts are split on English and CJK sentence punctuation. A richer same-subject
  fact updates the existing entry; changed values, numeric differences or
  negation conflicts invalidate the old fact while preserving history. Invalid
  facts are excluded from normal recall, allowed only for explicit historical
  queries, and scrubbed from active summaries. More than six summaries are
  merged into a rolling summary.
- `memory_search` and `memory_verify` use the new ranking; verification returns
  facts before historical turns and suppresses old turns when a fact matches.
  The prompt instructs the model to prefer the newest fact on conflicts and
  treat old turns/summaries as history.
- Verified with a deterministic 60-message fixture (birthday 3/14 to 3/15,
  favorite color blue to green, name/coffee/city facts): active facts ended as
  name/coffee/Hangzhou/3-15/green, the old 3/14 and blue entries were
  invalidated, the summary lost the stale lines, `memory_search` ranked the
  active 3/15 fact first, and verification for birthday/color returned only the
  active values. Vector mode ranked the coffee fact at 0.83; recall raised
  stability and lowered difficulty. No app FATAL/ANR; test rows and dialogs
  were removed.
- Limits: the vector is still a local hash projection, not a semantic
  embedding; summaries remain extractive; there is no graph memory, token
  pressure warning or asynchronous model distillation; current providers expose
  no embedding model (opendesign model_not_found, opencode-go 403). Research
  notes and reproducible fixture/mock scripts are included for the next rounds.

## 0.52.0 — 2026-10-28

- Adds a per-agent local memory library (`databases/oxmemory.db`) that stores
  readable turn notes, extracted facts and dated summaries with timestamps,
  access counts, importance, stability, short/long-term state and a dedupe hash.
  Each agent is capped at 4,000 entries.
- Normal recall ranks keyword/token overlap; experimental vector recall uses a
  128-dimension local signed-hash vector (English words, CJK characters and
  bigrams) blended with keywords. Neither mode calls an external embedding
  service.
- Both modes apply a forgetting curve with a 7-day half-life for short-term and
  90 days for long-term entries plus access/importance terms. Two recalls or an
  importance of at least 0.8 promote a short-term entry to long-term; stale,
  never-accessed, low-importance short entries are pruned after 30 days.
- When a chat exceeds the configured threshold (default 45 messages), older
  not-yet-compressed turns are written to the library, fact-marked user lines
  become high-importance facts and the batch gets a dated summary. Only the
  newest `max(8, threshold/3)` turns stay verbatim, alongside a compression
  note, digest and query-relevant memories in the system prompt. A state table
  prevents re-ingesting the same turns.
- Adds `memory_search(query, limit)` and strict `memory_verify(query)` tools and
  upgrades `remember`/`recall`. `memory_verify` requires at least two meaningful
  shared terms for multi-word claims, so “favorite color” no longer matches
  “favorite editor”; when there is no evidence it says so explicitly.
- The agent editor exposes mode, threshold and recall count; the memory page
  shows total/long/short/fact counts, recent readable entries and a clear
  action. Chatbox `orionekoAgent` carries the three memory settings, while the
  SQLite library itself remains local.
- Verified with a 60-message fixture and a local mock OpenAI server: normal mode
  sent only 17 messages with compression/digest/recall blocks and `memory_search`
  returned the birthday fact; the library held 47 turns (46 short, one long),
  3 facts and one summary. Vector mode still recalled the birthday/coffee facts
  with a 0.61 blended score. `memory_verify` found the birthday and correctly
  reported no evidence for “favorite color”. No app FATAL/ANR; test agent, rows
  and test dialogs/messages were removed.
- Limits: local hashed vectors are not semantic embeddings; summaries are
  extractive rather than model-written; the library is not in Chatbox/.oxagent
  backups; memory.txt is not auto-migrated; groups share the agent library; the
  threshold counts messages, not token percentage; the private SQLite file is
  not encrypted.

## 0.51.0 — 2026-10-28

- Chatbox / Kelivo backups now carry the global appearance when it is in custom
  mode, as a private `orionekoGlobalTheme` object in `settings.json`: mode,
  background mode/colors/alpha/dim/blur/AMOLED/gradient, bubble colors and
  opacity, corner radius, style, border, text color, font scale and line height.
  Native global appearance is not written as a fake custom theme.
- Local absolute paths are stripped. The global wallpaper travels as a
  `background` resource with `scope=global` and a storage-key reference; global
  chat and code fonts travel as `fonts/global/chat.ttf` and `code.ttf`, sharing
  the 8 MiB per-file and 16 MiB total budget with per-agent fonts.
- Import validates fonts with `Typeface.createFromFile`, writes the global
  background into app-private storage and applies the snapshot only when the
  local global appearance is not already custom. A local custom global theme
  wins, and assets written for the skipped import are deleted. Preview and
  result report global theme and font counts.
- Verified with a self-created agent and the existing custom global theme
  (deep blue image, frosted bubble, pink border) plus a 2.37 MB chat font and
  108 KB code font. The 1,457,238 B export carried the global theme with empty
  paths, a 16,360 B background whose SHA-256 matched, and both fonts with
  matching hashes; secret and absolute-path scans were clean. After deleting the
  prefs and fonts, the import restored the exact colors, radius, border, scale
  and line height, new background and font hashes, and the native chat showed
  the global look. Re-importing kept the local custom theme, left the prefs hash
  unchanged and created no orphan assets. The user's original prefs were backed
  up and restored byte-for-byte after QA. No app FATAL/ANR; the test agent,
  imported assets and zip were removed and the test dialogs/messages were zero.
- Also shortened the export info line after the native SettingCell truncated it
  to two lines; the Chinese pack moved 111 → 112 and the full line was verified
  on device.
- Limits: `orionekoGlobalTheme` and the global assets are OrionekoX extensions
  ignored by real Chatbox/Kelivo clients (not tested); per-dialog overrides are
  still not carried; a local custom global theme wins; fonts must parse; video
  backgrounds are not carried.

## 0.50.0 — 2026-10-28

- Chatbox / Kelivo backups now carry each agent's per-agent appearance
  override as a private `orionekoTheme` object: mode, background mode/colors/
  alpha/dim/blur/AMOLED/gradient, incoming/outgoing bubble colors and opacity,
  corner radius, bubble style, border color/width, text color, font scale and
  line height. Agents that follow the global look are not written as fake
  overrides.
- Local absolute paths are stripped. The wallpaper keeps using the existing
  `background` resource, while chat and code fonts travel as
  `fonts/<agentId>/chat.ttf` and `code.ttf`, capped at 8 MiB per file, 16 MiB
  total and 40 files. A dedicated font reader replaces the 2 MiB generic
  resource limit that initially dropped a 2.37 MB chat font.
- Import validates fonts with `Typeface.createFromFile`, writes them into
  app-private storage and applies the theme only after the agent exists. An
  existing local per-agent override wins; fonts written for a skipped theme are
  deleted. Preview and result report theme and font counts, and the export
  info/confirmation strings mention appearance themes.
- Verified with a chatless copilot (dark background, dim 40, blur 2,
  `#263238`/`#7C4DFF` bubbles, 230 alpha, 20 dp corners, cyan 2 dp border,
  1.12× text, 1.3× line height, 2.37 MB chat font, 108 KB code font) and a
  session agent with one message (green/brown bubbles, 14 dp corners, green
  border, 1.05× text, 1.15× line height, one font). Exports were 1,453,353 B
  and 1,383,082 B: theme paths were empty, background and font hashes matched
  the sources, and secret scans were clean. After deleting the theme, fonts and
  background, imports restored the expected theme, font and background counts;
  colors, radii, borders, scales and line heights matched (float readback
  differs only at 1e-7), and the native chat showed the dark bubble with cyan
  border. Re-importing the copilot backup restored zero themes, kept the local
  theme and left no orphan font files. No app FATAL/ANR; test agents, themes,
  fonts and zips were removed and the test dialogs/messages were zero.
- Limits: `orionekoTheme`/`fonts/` are OrionekoX extensions ignored by real
  Chatbox/Kelivo clients (not tested); only per-agent overrides travel, not the
  global theme or per-dialog overrides; an existing local override wins; fonts
  must parse on the device; video backgrounds are not carried.

## 0.49.0 — 2026-10-28

- The agent long-press menu now offers “Export character card” for non-built-in
  agents, with PNG (avatar included) and plain JSON choices. Both use the system
  document picker and exclude API keys, provider URLs and request headers.
- JSON is a SillyTavern `chara_card_v2` / `2.0` wrapper. The six persona
  sections written by the importer (`Description`, `Personality`, `Scenario`,
  `System prompt`, `Post-history instructions`, `Example dialogue`) map back to
  their own fields; any other AGENTS.md is exported as `description`. The first
  message becomes `first_mes`, and flat top-level aliases keep older V1 readers
  working. Fields are capped at 32 KiB and the greeting at 16 KiB.
- PNG scales the avatar to at most 1024 px and 1.6 MP, then embeds the card JSON
  as Base64 in a `tEXt` chunk named `chara` after IHDR with a spec-correct
  CRC32. Agents without a usable avatar get a generated 512×640 name card, so
  export never downloads or depends on a remote image.
- Verified with a self-created 256×256-avatar agent carrying six persona
  sections. The 2,420 B PNG had valid IHDR/tEXt/sRGB/sBIT/IDAT/IEND chunks, its
  decoded JSON matched the source field by field, and its pixels matched the
  source avatar exactly. The 790 B JSON matched as well; both scanned clean for
  keys, provider URLs, headers and `extra_body`. Re-importing the PNG created
  one card agent with the right name, greeting and pixel-identical avatar;
  importing the JSON next updated that same directory without a duplicate. A
  no-avatar agent exported a 20,888 B placeholder PNG (512×640) with valid
  embedded data. No app FATAL/ANR; test agents, cards and leftover test zips
  were removed and the test dialogs/messages were zero.
- Limits: real SillyTavern/RikkaHub clients were not tested; alternate
  greetings, character books, tags and regex are not exported; native agents
  keep their whole AGENTS.md in `description`; emoji is not a V2 field and
  returns as the default 🤖; avatar re-encoding drops EXIF/ICC/animation.

## 0.48.1 — 2026-10-28

- Chatbox / Kelivo backups now carry a private `orionekoAgent` object for each
  session and chatless copilot when that agent has non-default preferences:
  emoji, pin, remark, first message, reasoning effort, tool choice, parallel
  tool calls, context length, presence/frequency penalties, top-k, seed, stop
  sequences and the per-tool `auto`/`ask`/`deny` policy map.
- Credentials are structurally excluded. No API key, base URL, provider id,
  request headers or `extra_body` is exported, because headers and bodies can
  carry authentication even when they are not keys. Bounds mirror the UI:
  emoji 16 characters, remark/stop 2 KiB, greeting 16 KiB, at most 256 policy
  entries restricted to `auto`/`ask`/`deny`, and clamped numeric ranges.
- The importer applies preferences only after provider wiring, model override,
  headers and skill links, so a provider fallback cannot reset
  `context_length`; that ordering bug appeared in 0.48.0 and is fixed in
  0.48.1. Preview and result report how many agents regain preferences, and
  name, persona, avatar, memory, skills and chats are never overwritten.
- Verified with two self-created agents (one session with two messages, one
  chatless copilot): a 1,881 B export carried the full object in both entries,
  and a scan for keys, provider URLs, headers and `extra_body` returned zero.
  After resetting both agents to defaults, the import restored every field
  including 32k/64k context lengths, kept both agent ids and tg ids, created no
  duplicate directories, and stayed idempotent on a second import. The native
  tool page showed the restored `deny`/`ask` policies and the pinned session
  led the agent list. No app FATAL/ANR; test agents and the zip were removed
  and their dialogs/messages were zero.
- Limits: `orionekoAgent` is an OrionekoX extension that real Chatbox/Kelivo
  clients have not been tested with; headers, `extra_body`, provider URLs and
  keys must be re-entered after moving machines; emoji, remark and greeting
  were verified by `agent.json` readback rather than a welcome-message
  screenshot; temperature/top-p/max-tokens continue to travel in the standard
  session/copilot fields.

## 0.47.1 — 2026-10-28

- Chatbox / Kelivo backups now carry each agent's workspace files as
  `workspace/<agentId>/<relative path>`, including nested directories, binary
  content and non-ASCII names. Caps are 200 files, 2 MiB per file, 8 MiB total
  and depth 6; empty or oversized files are skipped and canonical containment
  rejects `..`.
- The importer previews the number of workspace files, restores only for agents
  that still exist, overwrites same-name files, preserves unrelated files and
  reports the restored count. Absolute paths, backslashes, `.`/`..` segments and
  paths deeper than eight segments are rejected.
- Fixed the completion toast: the 0.47.0 text was truncated to two lines by
  Android, hiding the workspace count. The compact 0.47.1 toast puts the
  workspace count second. The exporter also uses a read-only workspace lookup
  and collects only agents that actually appear in the backup, so exporting no
  longer creates empty workspace directories or emits orphan entries.
- Verified with two self-created agents and four workspace files: a 2,215 B
  export carried four entries with identical SHA-256 values; after deleting
  three files and keeping a new one, the import restored four, preserved the
  unrelated file, kept the original agent ids and tg ids, and remained
  idempotent on a second import. A zip containing `../../evil0470.txt` was
  rejected wholesale; a 2 MiB + 1 B file was skipped. A final rebuild exported
  an agent with two files plus an agent with none, produced only the two
  entries, and did not create the second agent's workspace directory. No app
  FATAL/ANR; test agents, workspaces and zips were removed and their
  dialogs/messages were zero.
- Limits: `workspace/` is an extension that real Chatbox/Kelivo clients have not
  been tested with; empty directories, permissions, timestamps and symlinks are
  not preserved; export-side caps were code-reviewed rather than exercised with
  a 2 MiB source file; deleting an agent leaves its workspace on disk.

## 0.46.0 — 2026-10-28

- Chatbox / Kelivo backups now carry the skill library and each agent's skill
  links. Exports write `skills/<id>/SKILL.md`; full exports include every
  visible skill, while a selected-agent export includes only the skills those
  agents reference. Sessions and copilots carry `orionekoSkillIds`, restricted
  to skills actually present in the zip. Caps: 50 skills, 512 KiB each, 8 MiB
  total.
- The importer loads skills first and restores `skill_ids` only for ids that
  imported successfully. The preview and result report how many agents were
  linked. External backups without the field keep the old behavior.
- Fixed chatless copilot identity: 0.43.2 only wrote `orionekoAgentId` into
  sessions, so a copilot roundtrip re-derived a `kelivo_…` id and created a
  duplicate agent. Copilots now export the field too, and both preview and
  import use the same override.
- Verified on the emulator with a 90 B skill plus one session and one copilot
  agent: a 1,762 B selected export carried an identical SKILL.md
  (SHA-256 `6cf9bd70…`), both objects kept their original id and skill link,
  and no API-key/provider strings appeared. After deleting the skill and
  clearing both `skill_ids`, the import reported one update, two restored
  links and one skill; both original agent directories and tg ids survived, the
  skill hash matched, and a repeat import stayed idempotent. Cleanup removed the
  test agents/skill and zeroed their dialogs/messages; no app FATAL/ANR.
- Limits: the `skills/` layout and private fields are extensions; only
  SKILL.md bodies travel (no scripts or attachments); full exports include
  unreferenced skills; real Chatbox/Kelivo clients were not tested.

## 0.45.1 — 2026-10-28

- Consolidated character-card import. The agent list has imported SillyTavern
  JSON cards since 0.8.114 and uncompressed PNG `tEXt` cards since 0.8.115;
  0.45.0 added a second implementation under RikkaHub settings. This patch
  makes the agent-list “Import character card (SillyTavern PNG/JSON)” entry use
  the richer `OxRikkaImporter` path, so it now also supports `iTXt`, saves the
  card PNG as `avatar.png`, keeps a stable id on re-import, and greets through
  `syncAgents(true)`. The old random-id and name “· copy” behavior is gone.
- The RikkaHub settings entry is backup-only again. Selecting a character card
  there shows a pointer to the agent-list entry and writes nothing; backup zip
  imports are unchanged.
- Verified on the emulator: an 8,285 B PNG created `rikka_card_b303c8ed`
  (tg id 555000094) with `avatar.png` SHA-256 `33f7346e…` matching the source
  and the expected native greeting. Re-importing the same card as 346 B JSON
  updated that single directory, kept the avatar hash and added no duplicate
  message. A card chosen through RikkaHub settings produced the pointer error,
  while a 297 B minimal backup still imported one offline assistant. Cleanup
  removed both test agents and their dialogs/messages; no app FATAL/ANR.
- Build note: the first build failed because an English string contained an
  apostrophe (`agent list's card entry`), which AAPT2 rejects as an invalid
  unicode escape; rephrasing it fixed the build.
- Limits: same as 0.45.0 — no compressed `zTXt`, alternate greetings,
  character books, tags, card export or real-client validation.

## 0.45.0 — 2026-10-28

- The RikkaHub import entry now also accepts SillyTavern / RikkaHub character
  cards as PNG or JSON. PNG cards are read from their `tEXt`/`iTXt` chunks
  (`ccv3` first, then `chara`, base64 or raw JSON); JSON cards are accepted as
  V2/V3 wrappers or flat V1 objects.
- Description, personality, scenario, system prompt, post-history instructions
  and example dialogue become labeled `AGENTS.md` sections. The first message
  becomes the agent greeting, and a PNG card itself is saved as `avatar.png`
  and shown as the native profile photo.
- Stable ids keep repeated imports on one agent: the same card as JSON, PNG or
  both updates a single directory instead of creating copies, and an existing
  avatar is preserved. The preview uses card-specific wording in Chinese and
  English.
- Safety caps: PNG 8 MiB, card text 4 MiB, each field 32 KiB, prompt 64 KiB,
  and IHDR dimensions at most 4096 × 4096 / 16M pixels to reject decompression
  bombs.
- Verified on the emulator: a 689 B JSON card created `rikka_card_df478b83`
  (tg id 555000092) with six persona sections and the expected greeting. The
  same card's 8,810 B PNG updated that agent and wrote a byte-identical
  `avatar.png` (SHA-256 `4482184d…`); a repeat import kept one directory, the
  same avatar hash and one greeting. A second JSON-only card created
  `rikka_card_1ac480d6` without an avatar, and a PNG with no `chara` chunk
  produced the card-specific error. Cleanup removed both test agents and all
  dialogs/messages for their tg ids; no app FATAL/ANR.
- Limits: compressed `zTXt` is not read; only `first_mes` is imported, so
  alternate greetings, character books/lorebooks, tags and regex scripts are
  skipped; cards cannot be exported; imported cards start offline until a
  provider is wired; a real SillyTavern/RikkaHub export was not tested.

## 0.44.0 — 2026-10-28

- Chatbox / Kelivo backups now carry an agent's long-term memory. Exports write
  a private `orionekoMemory` field into the session (agents with chat) or the
  copilot (chatless agents); imports restore it for both. Without the field,
  third-party backups keep their previous behavior.
- Memory is capped at 64 KiB per agent and 256 KiB per export, keeping the
  newest tail; the importer accepts up to 200,000 characters. The import
  preview warns that existing memory will be replaced and the result reports
  how many agents were restored.
- The export result line now includes the memory count, so the Toast no longer
  truncates a separate memory line. The bundled Chinese pack version moved
  from 99 to 100; previously an app update kept the cached
  `files/ox_zh-cn.xml`, so new Chinese strings did not appear.
- Verified on the emulator: a 157 B memory exported into a 1,626 B single-agent
  zip and restored byte-for-byte (SHA-256 `a7744d01…`) after deletion; a repeat
  import kept the same hash and 25 agent directories. A full export was
  482,642 B and reported 21 agents (14 with memory), 12 conversations and 245
  messages; 10/12 sessions and 4/9 copilots carried memory, matching the count.
  No API-key/provider fields appeared, and the app logged no FATAL/ANR.
- Limits: `orionekoMemory` is an OrionekoX extension ignored by real
  Chatbox/Kelivo clients; chatless copilot memory import was not round-tripped;
  imports overwrite rather than merge memory.

## 0.43.2 — 2026-10-28

- A Chatbox export now keeps the original Agent identity when imported back
  into the same install. Each exported session carries a private
  `orionekoAgentId` field, and the importer uses it verbatim instead of
  re-deriving an id from the conversation name. Before this, the roundtrip
  produced a `kelivo_chat_kelivo_chat_…_current_current` copy rather than
  replacing the original history.
- The imported id goes through the same sanitizing as `OxAgentStore`
  (`[a-zA-Z0-9._-]`, no leading dots, 80-character cap). Third-party backups
  without the field keep the previous naming behavior.
- Verified on the emulator: a 3,593 B export kept session id `kelivo_chat_s1_current`
  and the same tg id; the preview reported one existing conversation, the import
  replaced history, and the install still had exactly one agent directory with
  five chat entries and five native messages. Cleanup returned dialogs/messages
  to zero with no app FATAL/ANR.
- Limits: `orionekoAgentId` is an OrionekoX extension ignored by a real Chatbox
  client; a conversation without a copilot was not tested; invalid or overlong
  ids are sanitized or truncated.

## 0.43.1 — 2026-10-28

- Fixed Chatbox roundtrip loss of JSON attachments. The zip reader used to skip
  every `.json` entry, so an exported `resources/orioneko-*.json` attachment
  never entered the resource map. It now only excludes JSON metadata outside
  `resources/`; manifests, settings and session JSON keep their existing paths.
- Verified with a 3,578 B single-agent export containing five messages, two
  images and two raw attachments. Decoded payload hashes matched the source
  images, `notes.txt` and `data.json`; no API-key/provider strings were present.
  The same export previewed as two images / one attachment before the fix and
  two images / two attachments after it, and the roundtrip restored both photos,
  both file bubbles and the assistant reply in the native chat.
- Limits: the roundtrip Agent id gains a `_current` suffix, so keeping the
  original agent would add a copy rather than replace it; no real Chatbox client
  import was tested.

## 0.43.0 — 2026-10-28

- Kelivo/Chatbox backups now import every image and file in a message, in
  `contentParts` order, instead of only the first image and first file. The old
  behaviour that discarded files whenever an image was present is gone.
- The first media shares the message text; remaining media become extra
  entries with empty captions. Images are stored under `media/img_<n>`, files
  under `media/<n>_<name>` using the part `fileName`. Existing per-conversation
  caps (40 images / 20 files) still apply.
- The preview explains that multi/mixed attachments are split and reports the
  resulting image/file counts.
- Verified with a 2,392 B backup (text + two PNGs, one data-URL encoded +
  `notes.txt` + `data.json`): five messages, two images and two files. The
  native chat showed a green photo with the text caption, an orange photo,
  `notes.txt` (19 B · TXT), `data.json` (29 B · JSON), and the assistant reply.
  A repeat import replaced the history and left five entries/five rows;
  cleanup returned dialogs/messages to zero with no app FATAL/ANR.
- Limits: 40 images/20 files per conversation and 64 resource files; split
  entries share the source timestamp; `info` parts are ignored; remote URLs are
  not downloaded; no export roundtrip was re-tested.

## 0.42.0 — 2026-10-28

- RikkaHub messages can now import every media part (up to 20): images become
  native photo bubbles, while `document`, `video` and `audio` parts become file
  bubbles. The first media shares the message text; the remaining attachments
  are emitted as extra bubbles in order. Video/audio names fall back to the URL
  basename instead of the internal `rikka_upload_` prefix.
- Deduplication now includes the image/file basename, so multiple attachments
  in one message no longer collapse into a single entry. Extra bubbles share
  the node timestamp, keeping later messages ordered and remaining compatible
  with 0.41.0 single-file history.
- The result reports the image count and an attachment count covering files,
  video and audio.
- Verified with a 2,016 B backup (text + two PNGs + `report.txt` + mp4 + mp3):
  six messages, two photo bubbles and three file bubbles (`report.txt` 24 B ·
  TXT, `clip.mp4` 24 B · MP4, `clip.mp3` 10 B · MP3), followed by the assistant
  reply. A repeat import left six entries and six native rows; cleanup returned
  dialogs/messages to zero with no app FATAL/ANR.
- Limits: max 20 media per message; video/audio are generic file bubbles with
  no thumbnail, inline player or duration; only `upload/` matches are
  supported; no export roundtrip was re-tested this round.

## 0.41.0 — 2026-10-28

- RikkaHub backups now import non-image `document` parts as local file
  messages. The importer matches the part URL against `upload/`, copies the
  file into app-private storage, stores `file_path`/`file_name`, and allows
  pure-file messages with an empty caption; the result reports the attachment
  count.
- Imported RikkaHub conversation history is now replayed into the native chat
  after import. Previously `chat.json` received the messages but the native
  dialog could still show only the greeting.
- Verified with a 1,064 B backup (one conversation, two messages, 37 B
  `report.txt`): the native chat rendered a `report.txt` document bubble
  (37 B · TXT, caption “看附件”) and the assistant reply. A repeat import
  added no duplicate rows; cleanup returned dialogs/messages to zero with no
  app FATAL/ANR.
- Limits: only the first document per message; images/documents only;
  video/audio are skipped; only `upload/` matches are supported; imported
  files are not yet part of Chatbox export.

## 0.40.0 — 2026-10-28

- Chatbox export now prefers the active conversation background over the agent
  background when an agent has chat history, so the exported session matches the
  wallpaper the user actually sees. Chatless copilots still export the agent
  background; resource format and caps are unchanged.
- Verified with an agent whose agent background was BG38 but whose conversation
  override was BG40: the native chat rendered BG40, and the exported background
  resource decoded to the BG40 SHA-256 (not BG38). Deleting the original
  agent/theme/dialog and re-importing restored BG40 and the wallpaper.
- The conversation specificity is flattened to agent scope on re-import; only
  image backgrounds are exported (not global or video).

## 0.39.0 — 2026-10-28

- Chatbox exports now include the agent-scope chat background. Agents with chat
  get a session-scoped `background` resource and `session.background`; chatless
  copilots get a global background resource and `copilot.background`.
- Background assets use the same data-URL payload, SHA-256/size checks and
  2 MB/total caps as other resources; headers and keys are still excluded.
- Verified with a BG38 agent: the 15,580 B export contained one session and one
  background resource with the correct storage key. After deleting the original
  agent/theme and re-importing, background.png (14,884 B) and the agent theme
  were restored, and the native chat showed the BG38 wallpaper again.

## 0.38.0 — 2026-10-28

- Kelivo/Chatbox chat backgrounds are now imported: `kind=background` resources
  and session/copilot `background`/`backgroundImage` references are copied into
  the agent directory and applied as an agent-scope image theme after sync.
- A different background already configured on the agent is preserved; the
  result notes how many backgrounds were applied and that the rest kept their
  custom background.
- Preview and result report background counts.
- Verified with a 300×500 BG38 PNG: import wrote background.png (14,884 B) plus
  the agent theme JSON, and the native chat rendered the image wallpaper. After
  changing the agent background to custom-bg.png, a repeat import applied zero
  backgrounds and left the custom path untouched.

## 0.37.1 — 2026-10-28

- Finished the Chinese copy for the newer export flows: the Kelivo/Chatbox
  conversation and export previews, MCP JSON export, and skill-library export
  now have Chinese strings (27 new entries), and the empty-backup copy mentions
  conversations.
- Bumped the Chinese pack from 98 to 99 so installed devices reload it.
- Audited every `R.string.Ox*` reference in the Ox UI/import code against the
  pack: zero missing entries.
- Verified on device: Ox settings, skill library and MCP export rows are
  Chinese, and the Kelivo preview reads “对话 1 段（2 条消息）/项目：Multi B”
  instead of `Conversations:` / `Items:`. No functional changes.

## 0.37.0 — 2026-10-28

- The MCP page now offers “Export MCP JSON”: SAF creates `orioneko-mcp.json`
  with an `mcpServers` map of HTTP servers (`type=streamableHttp`, `url`,
  `isActive`).
- Headers (including Authorization/API keys), OAuth, SSE/stdio entries and tool
  allowlists are never written, so tokens cannot leak through this export.
  Duplicate names get suffixes; an empty list does not create a file.
- Verified with a test server carrying `Authorization: Bearer QA_SECRET_0370`:
  the 390 B export contained four HTTP servers without any headers field and
  zero occurrences of the secret, while the internal store kept its header.
  Deleting and re-importing the export restored the URL with empty headers and
  left the other three servers untouched.

## 0.36.0 — 2026-10-28

- Added “Export selected agents” in Ox settings: a native scrollable checkbox
  list of non-built-in agents, then SAF creates `orioneko-selected-backup.zip`.
- Only the checked ids are passed to the exporter, so no other agent, session or
  resource is written. An empty selection shows a hint; cancelling SAF clears
  the pending selection.
- Verified with two test agents (Multi B with 2 messages; Single Export with 4
  messages, avatar, image and notes.txt): the 6,697 B export contained exactly
  2 sessions, 0 copilots and 3 resources, and re-importing restored both
  conversations with their image and attachment.

## 0.35.0 — 2026-10-28

- RikkaHub `Avatar.Image` avatars are now imported: the URL basename is matched
  against the backup's `upload/` folder and the image is copied into the agent
  directory. Network URLs are never fetched, and repeat imports keep an existing
  avatar.
- `Avatar.Emoji` is recognized by type-name substring, so RikkaHub's FQN
  serialization keeps its emoji.
- Preview reports image-avatar counts and the overview mentions assistant image
  avatars; the result notes how many avatars were imported.
- Verified with two assistants (upload/RA image and FQN 🦊 emoji): import
  created both, stored a 2,519 B avatar.png, showed the RA header and kept 🦊;
  a repeat import updated both with the avatar hash unchanged.

## 0.34.0 — 2026-10-28

- The skill library can export all visible skills as an SAF-created zip with
  `skills/<id>/SKILL.md` entries, importable by Kelivo, Chatbox, RikkaHub or
  this app. Duplicate ids get `_2`, `_3` suffixes; only SKILL.md bodies are
  written, without scripts, attachments or keys.
- Fixed the import side so the `skills/` container is not part of the skill id;
  re-importing an export now restores `<id>` instead of creating a duplicate
  `skills_<id>`.
- Verified with a 150 B QASkill0340: the export was 2,353 B / 7 SKILL.md files,
  and a 272 B filtered subset re-imported to the exact
  `files/oxskills/QASkill0340/SKILL.md` with no duplicate id.

## 0.33.0 — 2026-10-28

- The non-built-in agent long-press menu now offers “Export Chatbox backup”.
  A confirmation explains that the selected agent's chat, avatar, images and
  attachments are written as a Chatbox v2 zip, without API keys or provider URLs.
- `OxKelivoExporter` accepts an agent-id filter, so a single agent can be
  exported; chat history is written as a session and chatless agents as a
  copilot, with no other agents included.
- SAF creates `<agent name>-chatbox-backup.zip` and the result toast reports the
  message and file counts.
- Verified with a single-agent export of 6,190 B (1 session, 0 copilots,
  3 resources, matching checksums, no `ox1:`/`sk-`/`api_key`/`test-key`).
  Re-importing it restored the prompt, 0.5/0.7/80 sampling, avatar, image,
  notes.txt and four messages, rendered in the native chat.

## 0.32.0 — 2026-10-28

- Added “Export Kelivo / Chatbox backup” in Ox settings: writes all non-built-in
  agents, chats, avatars, images and attachments to a Chatbox v2 zip through the
  system save picker.
- Each agent is written once: agents with chat history become a Chatbox session,
  chatless agents become copilots, so importing the export back does not create
  duplicate agents.
- Resources use data-URL payloads with SHA-256/size descriptors, global/session
  scopes and original storage keys; the manifest carries matching stats.
- API keys, provider URLs, Authorization headers, workspaces, memory, skills,
  MCP and themes are never exported.
- Verified with a 483,866 B full export whose manifest/checksums/stats passed and
  contained no `ox1:`/`sk-`/`test-key`; a filtered 8,306 B subset round-tripped
  back into one assistant and one 4-message conversation with prompt, sampling,
  avatar, image and file intact.

## 0.31.0 — 2026-10-28

- Non-image attachments from Kelivo/Chatbox backups are now imported. Chatbox
  v2 `parsed-attachment`/`raw-attachment` resources are matched to message file
  parts by storage key, raw storage key or URL.
- Matched files are copied into the agent's `media/` directory; chat entries now
  carry `file_path`/`file_name`, and the surrounding text becomes the caption
  while a pure attachment may have an empty caption.
- Attachments replay as native document bubbles (name, size, MIME, local path)
  through `buildLocalDocument`; the model context gets a `[文件] name` note and
  file contents are never executed.
- Caps: 20 attachments per conversation, 2 MB each, 24 MB total. Preview and
  result report attachment counts.
- Verified with a 4-message session containing a raw 35 B text file and a 38 B
  data-URL JSON: the native chat showed notes.txt with its caption and data.json
  as a pure document bubble; a repeat import kept both hashes and 4 message rows.

## 0.30.0 — 2026-10-28

- Chat images from Kelivo/Chatbox backups are now imported. Chatbox v2
  `resources[]` entries of kind `image` (and image attachments) are matched to
  message image parts by storage key or URL.
- Matched images are copied into the agent's `media/` directory and recorded on
  the chat entry; surrounding text becomes the photo caption and a pure-image
  message is allowed to have an empty caption.
- Imported images replay as native photo bubbles through
  `generatePhotoSizes`, with a text fallback when a photo cannot be prepared.
  One image is attached per message and extra images keep a `[图片]` placeholder.
- Caps: 40 images per conversation, 2 MB per image, 24 MB total, PNG/JPEG/GIF/
  WebP only. Preview and result report image counts.
- Verified with a 4-message session containing one raw PNG and one data-URL PNG:
  both landed as files, the native chat showed PIC1 with its caption and PIC2 as
  a pure photo bubble, and a repeat import kept both hashes and 4 message rows.

## 0.29.0 — 2026-10-28

- Kelivo/Chatbox backups now import assistant and conversation avatars. Chatbox
  v2 `manifest.json.resources[]` entries with `kind=avatar` or
  `kind=copilot-image` are mapped through their original storage keys, from raw
  image bytes or `data-url-base64` text.
- A matched image is written to `files/oxagents/<agentId>/avatar.<ext>`, so the
  imported agent shows it in the native chat header and profile. Repeat imports
  keep an existing avatar instead of overwriting a user-set one, and remote
  URLs are never fetched.
- Resources are capped at 64 files, 2 MB each and 24 MB total; only PNG, JPEG,
  GIF and WebP magic is accepted. Preview and result report avatar counts.
- Verified with one assistant and one session using two resources: both avatar
  files landed, the native headers showed the S1/AI images, and a repeat import
  left both hashes unchanged. Test agents and the sample were removed.

## 0.28.0 — 2026-10-28

- Kelivo/Chatbox backups now import conversation history. Chatbox v2 zips are
  read through `manifest.json` and `sessions/*/session.json`; older zips and
  direct JSON exports (`chat-sessions-list` / `session:<id>`) are also accepted.
- Every thread becomes a local agent with a stable id. The first system message
  becomes its prompt, session provider/model/sampling settings are inherited,
  and user/assistant/tool/system turns keep their original timestamps.
- Imported history is replayed into the native message DB, so the stock chat
  screen shows the backup bubbles instead of a greeting. Repeat imports replace
  history without duplicating messages.
- Covers 100 conversations, 500 messages per conversation and 20,000 characters
  per message. Images/files are placeholders, and tool turns are flattened into
  assistant bubbles. Verified with a 3-conversation zip and a legacy JSON sample,
  including repeat-import row counts, then all test objects were removed.

## 0.27.2 — 2026-10-28

- Kelivo/Chatbox backup import now reads HTTP MCP servers from
  `mcp_servers_v1` (also `mcpServers`) into the local MCP list, including name,
  URL, headers and enabled state. Entries are keyed by URL, so repeat imports
  do not duplicate.
- SSE, stdio/command and inmemory entries are reported as skipped and never
  executed. Preview and result show imported/skipped MCP counts, and the
  overview copy now says assistants, personas, skills and HTTP MCP are imported.
- Verified with one HTTP and one SSE entry: only the HTTP server landed with its
  header, a repeat import added nothing, and the test objects were removed.

## 0.27.1 — 2026-10-28

- Kelivo/Chatbox backup import now reads `skills/**/SKILL.md` from the zip into
  the private skill library (up to 50 skills, 512 KB each); same-name skills are
  updated, and preview/result report the skill counts.
- Corrected the preview copy that incorrectly said skills were excluded.
- Verified with a schema sample: one assistant and one 96 B SKILL.md imported,
  then a repeat import updated the assistant and kept exactly one skill folder.

## 0.27.0 — 2026-10-28

- Named presets can now be previewed before applying: long-press opens a native
  menu with 预览 / 重命名 / 删除. Preview reads the stored preset JSON and renders
  its background, bubbles, border, font and line height without writing the
  active scope.
- The preview renderer was refactored to accept any appearance snapshot, shared by
  the current-theme preview and preset preview.
- Verified on device: previewing DarkSea showed its black/green look while the
  current image-background theme and prefs file remained unchanged.

## 0.26.2 — 2026-10-28

- Fixed Operit appearance import blur mapping: `use_background_blur` and
  `background_blur_radius` now drive the 1–3 image blur levels (≥20 strong,
  ≥12 medium, otherwise light) instead of only switching bubbles to frosted.
- Frosted bubbles now follow only the liquid/water glass flags.
- Verified with radius 24: preview reported “模糊强”, the agent JSON stored
  `backgroundBlur=3`, and the preview wallpaper was visibly smoothed.

## 0.26.1 — 2026-10-28

- Kelivo/Chatbox assistant import now reads `provider_configs_v1` from the backup
  and wires each assistant through its `chatModelProvider`: base URL, API key and
  the first model are taken from the referenced provider config, with the
  assistant's `chatModelId` overriding the model name. Missing configs fall back
  to a local provider.
- Preview/result report how many provider configs were used, without showing keys.
- Verified with a schema sample: the referenced provider's URL/model landed in the
  imported agent, an unbound copilot fell back locally, and a repeat import
  updated both existing agents. OAuth, multi-key, proxy and provider-specific
  headers are not imported.

## 0.26.0 — 2026-10-28

- Added Kelivo/Chatbox backup import for assistants: pick a zip or JSON and
  preview the assistant count/names before writing.
- Zips are read through `copilots.json` and the `assistants_v1` value in
  `settings.json` (JSON string, array or id→object map); direct JSON arrays and
  objects are also accepted.
- Assistants map name, system prompt/prompt, temperature, topP, max tokens,
  custom headers and a model-name override. Provider wiring is inherited from
  the first existing agent with a base URL and key (otherwise offline). Repeat
  imports update `kelivo_<id>` agents while preserving tg_id and avatars.
- Conversations, provider keys/configs, skills, MCP, workspaces, avatars and
  preset/regex data are not imported. Verified with a schema sample: two
  assistants created, then a repeat import updated both.

## 0.25.2 — 2026-10-28

- Named presets can now be renamed: long-press opens a native rename/delete menu.
  Rename pre-fills the current name, applies the same file-name sanitization as
  save, updates the JSON name and deletes the old file. An existing target name is
  rejected without overwriting it.
- Verified on device: RenameQA became RenameQA2 with only the new file left;
  renaming to an existing BundleQA was rejected; cancel changed nothing.

## 0.25.1 — 2026-10-28

- Persona recommendations now open a native confirmation dialog naming the
  suggested theme; cancelling leaves the appearance untouched.
- Before applying, the current scope snapshot is backed up under
  `files/ox_recommend/<scope>.json`; an “撤销上次推荐” row restores it and removes
  the backup. Backups are isolated per scope and the first backup is preserved.
- Verified on an agent: recommended 赛博霓虹, then undo restored the original image
  background, opacity, dim and AMOLED state. One test override was removed.
- Fixed a build-only AAPT2 failure caused by an unescaped apostrophe in the English
  confirmation copy.

## 0.25.0 — 2026-10-28

- RikkaHub history import now also collects `upload/` images (up to 40 files,
  4 MB each) and matches them to image parts by file name. Matches are copied to
  app-private storage and stored in the history entry's `imagePath`; image-only
  messages use a `[图片]` placeholder.
- Existing history assembly sends a user entry's `imagePath` as inline base64
  vision content, so imported images remain usable for later multimodal turns.
- Verified with a schema sample: a one-conversation/three-node backup imported a
  70 B PNG as the third entry, and a repeat import kept exactly three entries.
  Remote/data URLs and non-image files are skipped.

## 0.24.1 — 2026-10-28

- Filled 12 missing Chinese strings found by auditing `R.string` references in the
  Ox sources: Operit import (title/explanation/running/done/permission), local
  premium (title/description) and terminal (title/clear/restart/hint).
- Bumped the Chinese pack to 94 so installed devices reload the translations.
  No functional code changed; the settings page and terminal now render in
  Chinese without English fallbacks.

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
