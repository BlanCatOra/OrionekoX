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
| ToolPkg / Skills / marketplace | full runtime | MCP only | medium gap |
| Browser agent / OCR / voice | yes | none | medium gap |
| Character cards / group chats | yes | AGENTS.md + .oxagent export | partial |
| Workflows / scheduled tasks | yes | none | medium gap |
| Local inference (MNN/llama.cpp) | built-in | external Ollama/LM Studio only | medium gap |
| Cross-device Space | Operit2 | LAN API agents only | partial |
| Backup / migration | snapshot export/restore | .oxagent per agent | partial |

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

1. **Android UI automation — done v1** — `ui_dump`, `ui_tap`, `ui_swipe`,
   `ui_screenshot`, `ui_key` are implemented through the accessibility service and
   ask-by-default. Next refinements: click-by-text, scroll-until-found, node
   caching, and arbitrary key events via ADB/root.
2. **Executable ToolPkg / JS/Wasm packages** — skills are readable now; executing
   package code needs a sandbox/runtime.
3. **Local stdio MCP runtime** — Operit's catalogue is mostly Python/Node/npx.
   Needs an installed OxLinux rootfs (or bundled runtime) plus a stdio JSON-RPC
   bridge. Remote MCP already works.
4. **Workflows / scheduled tasks** — background scheduler, battery budget, and
   tool runs that survive process death.
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
  native profile opened. Next refinements: scroll-until-found, node caching.
