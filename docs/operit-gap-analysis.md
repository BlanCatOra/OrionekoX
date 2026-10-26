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
| Character cards / group chats | yes | AGENTS.md + .oxagent export + memory editor + duplicate agent with copied persona/provider/tools/skills/memory (0.8.63); local groups with agents persist outgoing text locally and keep a per-group conversation log (0.8.55–56) | partial |
| Workspace / file browser | full editor + preview | per-agent workspace browser with create/edit/preview/rename/move/duplicate/delete/import/export (SAF, 20 MB import cap) | partial (basic text editor, no syntax highlighting) |
| Workflows / scheduled tasks | yes | **Ox workflows**: named prompt + agent + interval or daily wall-clock time, AlarmManager + foreground runner, result notification, manual run, duplicate (paused copy), pause/delete | **done v1** |
| Local inference (MNN/llama.cpp) | built-in | external Ollama/LM Studio only | medium gap |
| Cross-device Space | Operit2 | LAN API agents only | partial |
| Backup / migration | snapshot export/restore | .oxagent per agent: plain JSON when there is no workspace/avatar, zip with agent.json + avatar + workspace files otherwise; round-trip verified for memory, skills, avatar and nested workspace files | parity for single agents |

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
   `ui_find_text`, `ui_scroll_tap_text` (forward/backward), `ui_input_text` are
   implemented through the accessibility service and ask-by-default; `app_open`
   launches installed apps by package or label and `ui_wait_text` waits for a target
   label to appear. Long-list scroll+click, both long-press variants, cross-app
   launch, and the full open → tap → type chain are verified end-to-end; the
   remaining refinements are node caching and arbitrary key events via ADB/root.
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

### 0.8.59 workspace import

- The workspace browser now has `导入文件`: SAF `ACTION_OPEN_DOCUMENT` copies any
  device file into the current agent's private workspace. Names are sanitized,
  collisions get a ` (n)` suffix, the file is capped at 20 MB, and the copy runs on
  a background thread.
- Verified on the emulator with `Download/ox_file.txt` (45 B): the list showed the
  file with size/time, preview rendered both lines, and deleting it restored the
  empty state and removed the on-disk file. No crashes; no chat or contact data was
  touched.

### 0.8.60 workspace export

- Long-pressing a workspace file now opens a native menu: preview, export to the
  device, or delete. Export uses SAF `ACTION_CREATE_DOCUMENT` (MIME guessed from the
  extension, original name as the default) and copies the file on a background
  thread.
- Verified by exporting Yuki's `hello.md` (18 B) to Downloads, checking the content
  (`# Hello from Yuki`), then deleting the exported test copy. The original
  workspace was untouched and no chat data was involved.

### 0.8.61 workspace rename

- The workspace file long-press menu now offers preview, rename, export and delete.
  Rename is prefilled with the current relative path and also allows moving into a
  workspace subdirectory; empty/absolute/`..` paths are rejected, existing targets
  are refused, and a copy+delete fallback covers filesystems where rename fails.
- Verified by renaming Yuki's `hello.md` to `hello2.md` and back to `hello.md`;
  the content stayed `# Hello from Yuki` and the workspace ended in its original
  state. No crashes and no chat/contact data touched.

### 0.8.63 duplicate agent

- The agent long-press menu now offers duplicate: the copy gets a new id, the name
  `<name> · copy`/`副本`, the original persona, provider/sampling settings, tool
  policy, skills and long-term memory, with `built_in=false` and a fresh synthetic
  Telegram id. Avatars are not shared to avoid cross-agent file deletion.
- Verified with Echo Assistant: the copy appeared as `Echo Assistant · 副本`, its
  `agent.json` carried the copied persona and a new `tg_id`, and deleting it removed
  the copy directory and restored the single original row.

### 0.8.64 workspace duplicate file

- The workspace long-press menu now offers preview, rename, duplicate, export and
  delete. Duplicate writes `<name> (copy)<ext>` in the same folder, adds ` (n)` on
  collision, copies on a background thread and refreshes the list.
- Verified with Yuki's `hello.md`: `hello (copy).md` appeared with the same content,
  and deleting it restored the folder to `hello.md` + `notes/`. No crashes.

### 0.8.65 duplicate workflow

- The workflow long-press menu now offers duplicate: name, prompt, agent and interval
  (or daily minute) are copied, the name becomes `<name> · copy`/`副本`, and the copy
  starts paused so a duplicate cannot silently double model usage. Last result and
  run history are not copied.
- Verified with a manual `Copy test` workflow: `Copy test · 副本` appeared with both
  entries disabled; deleting both restored `ox_workflows` to an empty list and left
  no workflow alarm.

### 0.8.67 avatar in .oxagent

- Export now includes a valid custom avatar as `avatar.<ext>` and blanks the
  absolute `avatar_path` in `agent.json`; import writes the avatar into the new
  agent directory and points `avatar_path` at it. Stale paths in old JSON exports
  are cleared when the file no longer exists.
- Verified with an Echo duplicate carrying `ox_photo.png`: the 1740-byte zip held
  `agent.json` + `avatar.png`, and importing restored both the file and a valid
  device path. The test copies and the exported archive were deleted afterwards.

### 0.8.68 app_open

- Added `app_open(package | name)`: exact package match first, then exact launcher
  label, then a contains match; launches with the MAIN/LAUNCHER intent. The manifest
  `<queries>` block now declares that intent so launcher apps are visible without
  requesting QUERY_ALL_PACKAGES. Permission defaults to Ask.
- Verified with a background workflow on Yuki: `app_open(package=com.android.settings)`
  returned `opened Settings (com.android.settings)` and `topResumedActivity` became
  `com.android.settings/.Settings`. The test workflow and temporary Auto policy were
  removed afterwards; no alarms or crashes.

### 0.8.69 ui_wait_text

- Added `ui_wait_text(text, timeout_ms)`: polls the active window every 400 ms, with
  exact then contains matching plus the framework text index, for up to 15 seconds.
  Permission defaults to Ask.
- Verified the cross-app chain in a background workflow: `app_open(Settings)` returned
  `opened Settings`, then `ui_wait_text(Settings, 8000)` returned
  `found "Settings" bounds=[63,401][408,528]`, with Settings as the resumed activity.
  The test workflow and temporary Auto policies were removed afterwards.

### 0.8.70 ui_input_text

- Added `ui_input_text(text, target)`: locates an editable field by text,
  content-description or hint (exact → contains → focused field → first editable),
  focuses it and applies `ACTION_SET_TEXT`; returns `typed into "<label>"` or a
  specific error. Permission defaults to Ask.
- Verified the three-step cross-app chain on Yuki: `app_open(Settings)` returned
  `opened Settings`, `ui_tap_text("Search settings")` clicked
  `bounds=[200,624][553,695]`, and `ui_input_text("battery")` returned
  `typed into "Search settings"`. A `uiautomator dump` confirmed the search field
  `EditText text='battery'` and filtered Battery Saver / Battery usage results.
- Note: reinstalling or force-stopping the app can unbind the accessibility service
  (the secure setting becomes null); re-enabling it restores automation. Temporary
  Auto policies and the test workflow were reverted afterwards.

### 0.8.71 terminal bottom inset

- Fixed the terminal input row being covered by the floating main tab bar: bottom
  padding now includes the navigation-bar inset in both the tab and standalone
  presentations, while the IME path still uses only the keyboard inset. The
  monospace welcome line no longer uses a keyboard glyph that rendered as tofu.
- Verified on 0.8.71: the terminal `EditText` sits at `[98,2010][821,2127]`, fully
  above the tab bar at `y=2255`, and the input/send/keyboard controls are reachable.

### 0.8.72–0.8.73 Chinese date and status copy

- Fixed the doubled “于 于 9:20 AM 发送” accessibility string by dropping the extra
  prefix from `AccDescrSentDate`/`AccDescrReceivedDate`, and replaced the machine
  translation “%s的约定” with “定时于 %s”. `TodayAtFormatted` no longer injects
  “时间:”, so last-seen/location lines read naturally. Pack 51.
- Localized the birthday date format from the English `MMM dd, yyyy` pattern to
  `yyyy年M月d日` and switched the age templates to “%2$s（%1$d 岁）”. Verified on the
  “我的” page: “出生日期: 2008年7月14日（18 岁）”. Pack 52.

### 0.8.74–0.8.75 agent-chat actions and zh branding

- Hid “Block user” for synthetic agent dialogs in the chat-list multi-select menu:
  `updateCounters` now treats `OxAgentsBridge.isAgentDialog` like self/verify, so the
  action no longer appears where it cannot work. Verified on Yuki's dialog.
- Removed the last 30 “ChatAir” brand leftovers from the Chinese pack (account,
  permissions, cache, calls, Passport, notifications), rewrote the QR-login copy into
  natural Chinese targeting Telegram Desktop/Web, and changed “同步联系人到电话” to
  “同步联系人到手机”. Pack 53; runtime pack has zero “ChatAir”.

### 0.8.75 local-group regression pass

- Sent a new message in the existing local group “BlanCat 和 🤖 Yuki Waifu”. It was
  persisted locally, marked 已查看, and Yuki replied with `onRound tools=0`, recalling
  the group codeword from the per-group history. Confirms group storage, history
  injection and replies still work after the 0.8.72–0.8.75 copy changes.
- Environment note: entering text in the chat-list search field makes qemu disappear
  on this headless SwiftShader AVD (reproduced twice, no app FATAL/ANR). Row-coordinate
  navigation is the reliable path for QA here.

### 0.8.76–0.8.77 service-message and price copy

- Rewrote 37 machine-translated group service messages (Stars gifts, giveaways,
  community joins, invite-link edits, voice-chat actions, forward permissions,
  ownership transfer, reactions, birthday suggestion) with natural Chinese and
  preserved `un1`/`un2`/`%d Stars` placeholders.
- Fixed 18 strings whose parentheses were broken or that contained leaked English
  like “( E)”: live-stream/voice-chat endings, votes, privacy counters, price
  breakdowns, and the Mini Apps catalogue description. The AI style editor now
  uses the “海盗” example instead of “Pilate/海象”. Packs 54–55.

### 0.8.78 privacy and gallery copy

- Replaced the garbled “请检查url=值 (帮助)Telegram户口.” with “查看已登录你
  Telegram 帐号的设备列表。”, fixed `PrivacyInvites` (“请” → “邀请”) and the
  groups/channels explanation, and rewrote the account-deletion note in natural
  second person.
- Normalized 13 “画廊” to “相册”, 11 “私人聊天” to “私聊”, and “组” to “群组”,
  including the save-to-gallery hints that previously mixed clauses and a stray
  backslash. Pack 56; verified on the privacy screen and in the runtime pack.

### 0.8.79 private-use glyph cleanup

- Scanned the whole Chinese pack for private-use characters (U+E000–F8FF) and
  replacement glyphs: 16 strings rendered as boxes or gibberish, including the
  Messages privacy explanation, “What is Telegram Premium?”, all five QR-invite
  help lines, “I understand”, story audience “Everyone”, greeting empty-chat text,
  reaction notifications, the Premium-expired hint, “this is you”, the location
  distance action and `DayAgo`.
- Rewrote each from the English source with natural spacing/punctuation and also
  fixed the Premium-boosting sentences. Pack 57; the private-use count is now zero
  and the Messages privacy screen was verified on device.

### 0.8.80 archive round-trip and build hygiene

- Verified the full archive flow on a local group chat: select → Archive (toast
  “对话已归档。”, archived row with unread count), open the archive folder, select
  again → Unarchive. The chat returned to the main list with history intact.
- Rewrote the archive help copy (“擦掉/未同步的聊天” machine translations) into
  natural Chinese. Pack 58.
- Corrected a false alarm: `ps` reported ~100% CPU for the Gradle daemon because that
  column is a lifetime average; sampling `/proc/<pid>/stat` over 5 seconds shows 0
  ticks while idle. The temporary `org.gradle.vfs.watch=false` change was reverted.

### 0.8.81 high-visibility mistranslations

- Fixed 18 clearly wrong strings: “Clear for me” was “我没事” (now 为我清除), birthday
  notifications said “今天是生日”, the Two-Step and VIEW STICKERS/EMOJI/STYLE labels
  were untranslated, plus SIM/Fragment, anti-spam, WebView, channel-gift, TON export,
  gift-pin-limit, profile-video and affiliate-link copy. Pack 59.
- Also corrected the earlier Gradle-CPU conclusion: `ps` %CPU is a lifetime average,
  `/proc/<pid>/stat` shows zero ticks at idle, and the temporary vfs-watch change was
  reverted.
- Backlog: 49 strings still contain legacy `{\fn...}` font markup and mostly
  machine-translated gift/boost text; plan a dedicated pass with placeholder checks.

### 0.8.82 legacy font-markup strings removed

- Rewrote all 49 remaining strings that carried `{\fn...}` legacy font markup and
  mostly machine-translated gift/boost text (collectibles, gift offers, craft
  chances, boost service messages, birthday multi-title, business hours, 2FA SMS
  warnings, scheduled-post date patterns, Star rating/reactions, profile hints).
- Date patterns now use Chinese `M月d日 HH:mm` / `yyyy年M月d日 HH:mm`; the garbled
  “瓳” in the sticker-choosing state is gone. Pack 60; the pack contains zero
  `{\fn` occurrences and still parses 11,842 strings.

### 0.8.82 QA local group and media

- Environment: the frequent whole-emulator crashes on chat/archive/search were
  SwiftShader rendering failures; `-gpu off` keeps chat, attachment picker and media
  preview stable with no OrionekoX FATAL/ANR.
- Local group reply still works (`onRound tools=0`, one reply, 已查看).
- First successful media send: 2.7 KB PNG delivered to Yuki's `inbox/`; the agent
  called `list_dir` and saw `1792983667815_ox_photo.png` (2745 B) and replied. The
  bubble thumbnail still shows X / “0 KB / 2.7 KB”, so local document injection
  needs an attachment-cache or local photo-size fix; delivery itself works.
- Details: `docs/qa-media-0.8.82.md`.

### 0.8.83–0.8.84 local media bubble diagnosis

- Tried `document.localPath` (0.8.83) and local-only ids
  (`id/dc_id = Integer.MIN_VALUE`, 0.8.84) for injected media. Freshly sent bubbles
  still show X / “0 KB / 2.7 KB”, but after an app restart the same messages render
  as normal “ox_photo.png, 2.7 KB PNG” document cards. So the remaining defect is a
  stale upload/download-progress flag on the intercepted message, not file lookup.
- Delivery remains correct (new file in `inbox/`, agent replies). Next fix: clear the
  progress/send state after `persistAndShow`. Details in `docs/qa-media-0.8.84.md`.

### 0.8.85–0.8.86 durable local media paths

- Found that picker documents were injected via `handleOutgoingSpecial` with the
  original document and a temporary picker path, which is deleted after sending.
- 0.8.85/0.8.86 mark the document as local and archive it to the agent workspace
  `inbox/` first, using that durable absolute path for `attachPath`/`localPath`.
  The X and “0 KB / size” text are gone; a fresh bubble still shows the download
  arrow until an app restart, when it becomes a normal document icon. Agent delivery
  and `list_dir` continue to work.

### 0.8.87 live media state

- After constructing the `MessageObject` in `persistAndShow`, the bridge now sets
  `attachPathExists`/`mediaExists` when `attachPath` exists. A freshly sent
  `ox_photo.png` renders as a document card immediately (no X, 0 KB text or
  download arrow), so local media bubbles no longer need an app restart.

### 0.8.87 terminal QA

- Verified direct TTY 1/2/3 switching, `echo`/`pwd` execution, the long-press agent
  assignment dialog (QA2 checked, cancel options) and the `help` command list. The
  composer stays above the floating tab bar with the IME open. No app crash; the
  `-gpu off` AVD remained stable. Details: `docs/qa-terminal-0.8.87.md`.

### 0.8.87 new-agent and MCP QA

- New-agent editor exposes avatar, persona/remark, skills, model service, base URL,
  key, model, fetch/preset model actions, test connection, advanced parameters,
  headers and AGENTS.md; save is the top-right check and back discards. No accidental
  agent creation (list stayed at 7).
- MCP catalogue shows the two remote entries plus Operit local-STDIO plugins; local
  entries open a native detail dialog with repo/copy-config actions. Nothing was
  installed or removed. Details: `docs/qa-ox-0.8.87.md`.

### 0.8.88 giveaway date formats

- An “identical to English” scan left only brand names plus three giveaway date
  patterns. Localized them to `yyyy年M月d日` / `M月d日` / `yyyy年M月d日` so cards no
  longer render “26 10月 2026”. Pack 61.

### 0.8.89 edit-profile copy

- Removed leftover “( E)” from `EditProfileName`, unified “您的信息” to “你的信息”, and
  rewrote the birthday visibility hints (“生日变动>” → “可以看到你的生日。**更改 >**”
  and “在**设置**中选择谁可以看到你的生日。”). Pack 62; verified on the profile sheet.

### 0.8.90 logout copy

- Corrected the account page’s misuse of “注销” for logging out: logout now reads
  “退出登录会使所有加密聊天失效。”, support reads “联系客服 / 退出登录通常并不能解决
  问题。”, and session termination uses “终止…会话” instead of “注销”. Pack 63.

### 0.8.91 hide Stop on agent profiles

- Agent profiles still exposed the bot “Stop” action, which calls
  `onBlockContactClicked(true)` for a synthetic user. `ProfileActionsView` now has a
  `hideStop` flag set by `ProfileActivity.setActionsMode` when the bot id belongs to
  a local agent, so the action row shows only 消息 / 静音 / 共享. Real bots keep Stop.

### 0.8.92–0.8.93 agent profile menu

- Local agent profiles showed a duplicate “分享”, plus “报告” and “删除并屏蔽”, because
  the bot branch adds BotShare and ShareContact and the Chinese `ShareContact` was
  mistranslated to “分享”. 0.8.92 hides BotShare/ShareContact/BotPrivacy/Report and
  block/restart for agent ids; the menu now only has 自动删除 / 添加到主屏幕.
  0.8.93 corrects `ShareContact` to “分享联系人” for real contacts too. Pack 64.

### 0.8.94 agent profile actions

- With `shareAction` false for agents, the profile action row is now only 消息 / 静音.
  The “添加到群组或频道” row and its “manage a group or channel” hint are also
  suppressed for agent ids, since they describe a management bot rather than a local
  conversational agent. Real bots/contacts keep the row.

### 0.8.95–0.8.98 local-group profile

- Local-group profiles were empty after restart because ChatFull existed only in
  memory. `ensureLocalGroupFull(chatId)` now rebuilds participants from
  `OxGroupStore`, puts the full into `MessagesController` and persists it with
  `MessagesStorage.updateChatInfo`; creation also persists immediately, and
  `ProfileActivity` calls the rebuild before reading ChatFull.
- Verified 添加成员, the 2-member list and the Yuki Waifu row appear. Residual: the
  creator’s own row is still blank (only the 创建者 badge); needs a targeted look at
  ProfileActivity’s self-participant resolution.

### 0.8.99 local-group creator row diagnosis

- Logging inside `ensureLocalGroupFull` confirms
  `selfId=6527689286, selfUser=true, selfResolved=true, participants=2` for the test
  group, so the blank creator row is not missing ChatFull/user data; it is a
  ProfileActivity member-cell/sortedUsers rendering detail. Not fixed here.

### 0.8.100 local-group creator row fixed

- The blank creator row came from `putUser(self, false)` leaving a pre-existing
  `TL_userEmpty` placeholder in the user cache. Forcing `putUser(self, true)` makes
  the member cell resolve the real user; the profile now shows
  “BlanCat · 在线 · 创建者” and “Yuki Waifu · 本地智能体”, with “2 位成员, 2 人在线”.

### 0.8.100 group management QA

- Verified the creator/member rows, “2 位成员, 2 人在线”, actions and 添加成员. The
  add-member picker shows the link-invite row and all agents, with Yuki's switch
  checked+disabled because she is already a participant. Message long-press shows
  回复/复制/转发/置顶/举报/删除. No membership or message changes; details in
  `docs/qa-group-0.8.100.md`.
