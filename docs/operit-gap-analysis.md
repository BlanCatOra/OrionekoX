# OrionekoX vs Operit / Operit2 — what is missing, and does the user need it?

Referenced projects: [Operit](https://github.com/AAswordman/Operit) (Android agent,
8.9k stars) and [Operit2](https://github.com/AAswordman/Operit2) (cross-platform
successor, Rust core + Flutter clients). The comparison is about **user value**,
not feature-count parity.

## Shipped in 0.8.x (the "simple by default, powerful when needed" base)

| Capability | Operit | OrionekoX 0.8.4 | Verdict |
|---|---|---|---|
| First-run guide, Chinese UI | yes | six-step guide, 11.6k-key zh pack, native Telegram UI | parity for our scope |
| Cloud / local / compatible providers | broad | 32 presets, OpenAI-compatible, LAN Ollama/LM Studio/vLLM/opencode, model fetch, test | parity |
| Advanced request controls | broad | temperature, top_p, max_tokens, context, penalties, top_k, seed, stop, reasoning effort, tool choice, parallel calls, extra JSON | parity for chat |
| Per-tool permission | yes | **Tools & permissions page**: per-agent auto/ask/deny, MCP tools included, persisted | parity |
| Long-term memory | graph memory | **simple per-agent memory** (`remember`/`recall`, injected into system prompt) | good v1 |
| Files / workspace | project workspace + editor | **private per-agent workspace** (`list_dir`/`read_file`/`write_file`), path-escape guard | good v1 |
| MCP | yes | MCP Streamable HTTP, tools bridge, add/toggle/refresh/delete | parity for MCP |
| Android permissions | accessibility/Shizuku/root | permission centre + onboarding request flows; **no automation tools yet** | gap |
| UI automation | accessibility/ADB/root, PhoneAgent | accessibility bridge + `ui_dump` / `ui_tap` / `ui_swipe` / `ui_screenshot` / `ui_key` tools | **done v1** |
| Container/terminal | PRoot Ubuntu, SSH/SFTP, editor | PRoot PoC, blocked by W^X; companion host pending | gap |
| ToolPkg / Skills / marketplace | full runtime | MCP market + read-only SKILL.md library + per-agent skill prompt injection | partial (no code execution) |
| Browser agent / OCR / voice | yes | `ui_ocr` implemented; emulator screenshot works after reboot, OCR model is not installed in the Play Services image | partial |
| Character cards / group chats | yes | AGENTS.md + .oxagent export + memory editor; local groups with agents now persist outgoing text locally and keep a per-group conversation log for model context (0.8.55–56) | partial |
| Workspace / file browser | full editor + preview | per-agent workspace browser with create/edit/preview/delete | partial (basic text editor, no syntax highlighting) |
| Workflows / scheduled tasks | yes | **Ox workflows**: named prompt + agent + interval or daily wall-clock time, AlarmManager + foreground runner, result notification, manual run, pause/delete | **done v1** |
| Local inference (MNN/llama.cpp) | built-in | external Ollama/LM Studio only | medium gap |
| Cross-device Space | Operit2 | LAN API agents only | partial |
| Backup / migration | snapshot export/restore | .oxagent per agent: plain JSON when there is no workspace, zip with agent.json + workspace files otherwise; export/import round-trip verified for memory, skills and nested workspace files | parity for single agents |

## Does the user need it?

1. **Tool permissions (done)** — mandatory for safety; now every tool is visible and
   can be set to auto/ask/deny per agent, including MCP tools.
2. **Memory + workspace (done v1)** — the two features users miss most after basic
   chat: "remember this" and "work on this file". Both are scoped per agent.
3. **Android UI automation (next, high value)** — the reason people install Operit:
   let the agent tap through other apps. We already request accessibility/ADB/Shizuku
   permissions; the missing piece is a safe tool set (`ui_dump`, `tap`, `swipe`,
   `screenshot`, `key_event`) behind ask-by-default policies.
4. **ToolPkg / Skills (next, power users)** — MCP already covers the interoperable
   case. ToolPkg adds JS/Wasm packages and UI; only worth it after automation.
5. **Workspace editor / terminal (next)** — valuable but the W^X host component is a
   prerequisite. Keep the current PRoot PoC and finish the targetSdk-28 host.
6. **Workflows / scheduled tasks (later)** — useful for "every morning summarize X";
   needs a background scheduler and battery policy, so not a first-release feature.
7. **Browser agent / OCR / voice (later)** — large surface, better after UI automation.
8. **Character cards / group chats (later)** — our Telegram-native agent contacts are
   already a differentiator; card import can be added without changing the model.

## Product stance

- Default path stays **simple**: three seeded agents, onboarding, provider presets,
  one-tap chat.
- Power features are **discoverable but not in the way**: Tools & permissions,
  advanced parameters collapsed, MCP page, container page.
- Safety is explicit: destructive mode switches require confirmation; every tool
  can be denied; memory/workspace are per-agent and never leave the device.


## 0.8.21 deep-use pass (2026-10-25)

This pass used Operit's actual screens/source as the reference and closed several
gaps that were previously listed as "gap":

| Area | Operit behavior studied | Now in OrionekoX |
| --- | --- | --- |
| Settings visual language | every settings page uses the stock Telegram list, 28dp tinted icons, title + subtitle, blue value, grouped cards, shadow/info rows | Ox settings, Proxy & VPN, Terminal settings and Skills all use `UniversalRecyclerView` + `SettingsActivity.SettingCell`, native switches/radio rows and native section headers |
| Network | proxy and VPN are configured in one settings surface | one `代理与 VPN` page with proxy switch/server/port/type and VPN switch/node list/add inline; chat-list overflow "代理与 VPN" opens the same page |
| Terminal sessions | Operit terminal has browser-like tabs, and a terminal can be assigned to an agent | TTY 1/2/3 are direct-click tabs (no cycling); long-press a tab to assign an agent; the tab shows `TTY 2 · QA2` and the assignment persists |
| Skills | Operit `Download/Operit/skills/<id>/SKILL.md`, list/read/execute | read-only skill library (`技能库`), scans Operit/Operit2 skill folders; agents get `list_skills` / `read_skill` tools |
| MCP ecosystem | Operit MCP catalogue, local stdio plugins, install/refresh | catalogue + one-tap remote HTTP install (DeepWiki/Context7); local stdio entries are browsable with copyable config |
| Agent in groups | Operit characters participate in group chats | agents can be added to a normal Telegram basic group locally; participant list, member count, group header and in-group reply bubbles work |
| Local premium | paid appearance features | local premium already makes `UserConfig.isPremium()` true; resale gift appearance now applies locally with a "使用 · 本地会员已解锁" action instead of the server payment flow |
| Branding / polish | — | chat header and Stories wordmark are OrionekoX; theme menu says 日间模式/夜间模式; bottom labels are equal-width and centred |

### Still missing, in priority order

1. **Android UI automation — done v1** — `ui_dump`, `ui_tap`, `ui_long_press`,
   `ui_swipe`, `ui_screenshot`, `ui_key`, `ui_tap_text`, `ui_long_press_text`,
   `ui_find_text`, `ui_scroll_tap_text` (forward/backward) are implemented through
   the accessibility service and ask-by-default. Long-list scroll+click and both
   long-press variants are verified end-to-end; the remaining refinements are node
   caching and arbitrary key events via ADB/root.
2. **Executable ToolPkg / JS/Wasm packages** — skills are readable now; executing
   package code needs a sandbox/runtime.
3. **Local stdio MCP runtime** — Operit's catalogue is mostly Python/Node/npx.
   Needs an installed OxLinux rootfs (or bundled runtime) plus a stdio JSON-RPC
   bridge. Remote MCP already works.
4. **Workflows / scheduled tasks — done v1** — interval workflows (15 min,
   hourly, 6 h, 24 h or manual) and daily wall-clock workflows (native time
   picker, computed with the local calendar) run through a foreground service,
   inject the prompt into the agent chat, and post a result notification. Limits:
   no multi-step graph or conditions, inexact alarms while `SCHEDULE_EXACT_ALARM`
   is not granted, and a run depends on the app not being force-stopped by the user.
5. **Browser/OCR/voice** — WebView/accessibility automation, ML Kit OCR, and
   on-device STT/TTS.
6. **Full Operit skill execution semantics** — Operit skills can carry scripts and
   per-skill permissions; ours are documentation-only for now.


### 0.8.22 actual test notes

- Real model chat works end-to-end with the imported OpenDesign key: Yuki Waifu called
  `remember` + `recall` and answered from memory in the native ChatActivity.
- Container rootfs installs and reports `state=ready`, but proot cannot exec the guest
  loader on targetSdk 36 (W^X/SELinux). A targetSdk-28 companion host or Shizuku/root is
  required; this is the same platform wall documented for the 0.8.0 PoC.
- TTY browser tabs, long-press agent assignment and the `TTY 2 · QA2` annotation persist
  across session switches.
- MCP market: DeepWiki installed and refreshed 3 tools; the Operit catalogue's 38 local
  stdio entries are browsable/copyable but not executable without a Python/Node runtime.


### 0.8.25 UI automation actual test

- Enabled `OxAccessibilityService` on the emulator, then asked the imported Yuki
  Waifu agent to use the new tools with a real OpenDesign model:
  - `ui_dump` returned the active window tree (header title, typing status, input
    placeholder, content descriptions, bounds).
  - `ui_screenshot` saved a 436 KB PNG to
    `files/oxauto/screen-1792947825540.png`.
  - `ui_key` with `key=home` moved the foreground to the launcher.
  - `ui_tap` at `540,1458` dispatched a gesture successfully.
- All five tools are visible in `工具与权限` with ask-by-default policy and native
  setting rows.


### 0.8.26 click-by-text

- Added `ui_tap_text`: case-insensitive exact pass then contains pass, only matches
  whose ancestor chain has a clickable node; returns the clicked label and bounds.
- Added an `activeRoot()` fallback via `getWindows()` when
  `getRootInActiveWindow()` is null during dialog/IME transitions.
- Actual test: the agent clicked the chat header “🤖 Yuki Waifu” by text and the
  native profile opened.
- Added `ui_find_text` and `ui_scroll_tap_text` (scroll the first scrollable node
  up to 12 times, then report/click). `ui_find_text` was live-tested: it found the
  “Yuki Waifu” header at `[137,0][943,275]` without scrolling. `ui_scroll_tap_text`
  now collects all matches and only clicks one with a clickable ancestor, scrolling
  past non-clickable text; its full long-list E2E is still pending because a test
  `ui_key back` only dismissed the keyboard. Next refinements: node cache,
  ADB/root arbitrary keys. `ui_find_text` / `ui_scroll_tap_text` now also accept
  `direction=backward` for scrolling up; the scroll E2E still needs a target that
  is not present in the just-sent message text.


### 0.8.40 workflows actual test

Operit's scheduled tasks are now covered by a first-party workflow surface instead
of a separate automation graph:

- Ox settings → 工作流: named prompt, agent picker, interval (manual / 15 min /
  hourly / 6 h / daily), enable-pause, run now, delete.
- Each scheduled entry arms one AlarmManager broadcast. Exact alarms are used when
  the system grants them; otherwise the page warns that runs may start late.
  Alarms are re-armed on BOOT_COMPLETED and on every app process start, and overdue
  entries catch up after 30 seconds.
- The foreground runner queues runs (one Service instance per process), injects the
  prompt through the same local-chat path as a user message, waits for the reply to
  be persisted, then posts the result to the `ox_workflows` notification channel.
- Verified on the emulator: manual run, real-model run (Yuki, reply `workflow ok`),
  pause/resume (alarm cancelled and re-armed), manual-only workflow, delete,
  invalid-save validation, dark mode, and a cold-start run with the process killed
  before the alarm (new PID, foreground service allowed, reply and notification
  delivered, next alarm armed for +15 min).
- Limits: no multi-step graphs or conditions, and a user force-stop clears alarms
  until the app is opened again.

### 0.8.41 daily wall-clock time

- The schedule picker now also offers `每天指定时间…` and opens Telegram's native
  time-picker bottom sheet (`AlertsCreator.createTimePickerDialog`). The workflow
  stores `daily_minute` (0–1439) and the scheduler computes the next local
  occurrence with Calendar, so a workflow created at 04:26 for 09:00 arms the alarm
  at 09:00 the same day instead of 24 hours later.
- Actual test: created `Daily test`, picked 09:00 via the native wheel, saved; the
  row showed `每天 09:00 · 下次 09:00`, and `dumpsys alarm` reported
  `origWhen=2026-10-26 09:00:00.000`. Run-now delivered the Echo reply and the
  result notification; deleting the workflow removed the alarm.

### 0.8.42 workflow run history

- Each workflow keeps the newest 10 runs (`ts`, `ok`, `text`) inside its JSON; the
  long-press menu opens `运行记录`, newest first, and tapping an entry shows the
  full output. Empty state and success/failure labels are localized.
- Verified with a real-model run: the entry read
  `10-26 04:34 · 成功 · workflow ok`, and the detail dialog showed the full text.
  History is independent from `last_result`, so editing the agent or prompt does
  not erase past runs.

### 0.8.43 .oxagent with workspace files

- Export switches to a zip only when the agent has workspace files: `agent.json`
  plus `workspace/...` entries; agents without files keep the legacy plain-JSON
  format. Limits are 200 files, 8 MB per file and 32 MB total.
- Import detects both formats, creates the agent first (with an `imp*` id on a
  collision), then restores workspace entries through the path-checked resolver.
- Verified: Yuki's export contained `agent.json`, `hello.md` and `notes/note.txt`;
  the imported copy restored both files (including the nested folder), memory and
  skills, with `api_key` empty. Importing the old 799-byte JSON export still worked,
  and the duplicate agents plus their test workspace were removed afterwards.

### 0.8.44 idempotent Operit import

- Previously every import generated a new `op_*` id from the current time, so the
  same Operit card produced a duplicate agent each run, and the USER.md memory was
  appended again. The importer now looks up an existing `op_<source>_` agent with
  the same name, reuses it (keeping tg id, pin, note, avatar and skills) and skips
  memory text that is already present; new agents get a deterministic id.
- `deleteAgent` now removes nested folders too, so an agent with `media/` no longer
  leaves a half-deleted directory behind.
- Verified by importing the same catalogue twice: agent count stayed at 7, the
  existing configs were updated in place, and Yuki's 75-character memory did not
  grow. Note: during cleanup of the accidental duplicates the two original Yuki and
  Sakura agents were deleted by row position; the imported copies are now the live
  agents, Yuki's memory/skills/workspace were restored from the `.oxagent` export,
  but their recent chat history was lost.

### 0.8.45–0.8.48 long-list scroll+click E2E

- The pending `ui_scroll_tap_text` end-to-end test now passes with a real model:
  starting on the Ox settings tools list, the agent scrolled twice and reported
  `tapped "ui_ocr" ... (coordinate)`.
- Three fixes were required:
  1. the loop no longer scrolls after the final allowed search (targets appearing
     on the last screen were missed);
  2. the node traversal cap was raised from 400 to 2000 and a framework
     `findAccessibilityNodeInfosByText` index pass was added, because long
     Telegram lists were truncated before the target row;
  3. Telegram list rows have no clickable ancestor (RecyclerListView handles the
     tap), so a visible-bounds coordinate tap is used as a fallback after the
     clickable-ancestor `ACTION_CLICK` path.
- `OxA11y` step diagnostics (root package, collected nodes, sample labels,
  scrollable bounds, scroll result) stay in the code for future debugging.
- Test workflows and temporary tool-policy overrides were removed afterwards.

### 0.8.49–0.8.50 long press

- Added `ui_long_press(x, y, duration_ms)` and `ui_long_press_text(text,
  duration_ms)`, both gesture-based and ask-by-default. The text variant tries
  `ACTION_LONG_CLICK` on a clickable ancestor and falls back to a coordinate
  long-press at the label center.
- Verified with a workflow running in the background: `ui_long_press(500,835)`
  put the Echo row into Telegram multi-select, and
  `ui_long_press_text("Echo Assistant")` in the agent list opened the
  pin/edit/memory/export menu.
- Known limitation: chat-list `DialogCell` rows are custom-drawn and expose no
  readable text, so text-based long-press cannot target them; use `ui_dump` plus
  `ui_long_press` coordinates there. Text long-press works on settings and agent
  list rows.

### 0.8.51–0.8.54 chat-list accessibility investigation

- Added `flagIncludeNotImportantViews` to the accessibility service and a
  center-covering `activeRoot()` fallback (prefer the main window over a bottom
  floating bar; dialogs/full-screen pages still win).
- The chat list still exposes only one full-screen application window: 30 nodes
  before the flag, 70 after, with labels for the title, search box, tab bar and a
  few framework nodes, but **no chat rows**. The rows are not just missing text;
  they are absent from the service-visible tree even though `DialogCell` sets a
  content description in `onInitializeAccessibilityNodeInfo`.
- Consequence: text-based tap/long-press cannot target chat rows without a
  Telegram-side accessibility change; use `ui_dump` and coordinate tools there.
  Regression check: agent-list `ui_long_press_text("Echo Assistant")` still works
  (95 nodes, menu opened).

### 0.8.55–0.8.56 local group sends and history

- A local group (synthetic agents only) used to send user text to the server, so the
  bubble showed a red failure mark even though the agent answered. `SendMessagesHelper`
  now routes `peer<0 && OxGroupStore.isLocalGroup(peer)` through
  `sendLocalGroupText`, which persists the message as sent and triggers the group
  turn; real groups with agents keep the normal server send.
- `OxGroupStore` gained `isLocalGroup` and a capped per-group log (40 entries).
  `groupMessages` includes that log, `startGroupTurn` records the user line, and
  `finishTurn` writes group replies only to the group log instead of the agent's
  one-to-one history.
- Verified after the send fix: a new group message showed double checks (no red
  mark) and Yuki replied in the group. The per-group history was then verified with
  a fact that was explicitly not saved to memory:
  - "The group codeword is pineapple. Do not use tools" → Yuki answered without a
    tool call;
  - "What is the group codeword? Do not use tools" → it answered `pineapple` with
    `onRound tools=0`, so the answer came from the new group log, not memory.
- The earlier emulator crashes (normal, headless and `-gpu off` modes losing `adb`
  when opening the chat) were a rendering/stability issue with no app FATAL; after
  a fresh boot with system animations disabled for the test the two-message check
  passed. Animations were restored afterwards.
