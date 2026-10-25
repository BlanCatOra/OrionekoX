# QA report — OrionekoX 0.8.4

Environment: Android emulator API 34, 1080×2400, Chinese UI, Ox local mode.
Package `io.github.blancatora.orionekox`, versionCode 19.

## Coverage matrix

| Area / control | Result | Evidence |
|---|---|---|
| Cold start, home, no skeleton | PASS | 0 Telegram sockets; screenshot 0.8.4 home |
| Top-right menu (Agents/MCP/Ox Settings/Sign in) | PASS | opened every item |
| Ox Settings rows (mode, language, guide, license, agents, MCP, tools, container, version, repo) | PASS | Chinese, GPL-2.0, version 0.8.4 |
| Language list and Simplified Chinese selection | PASS | plain “简体中文”, UI switches |
| **Tools & permissions** page | PASS | Android statuses; per-tool auto/ask/deny; policy change persisted |
| Onboarding 1–6 pages | PASS | new card layout, fixed progress header, hint only on 4–6 |
| Onboarding mode choice | PASS | Ox hides Bot-compatible container; Login shows it |
| Onboarding skip/exit/back | PASS (back/exit) | skip step exists on 4–6; exit returns/closes |
| Agents list, create, save, delete | PASS | QA Tools created/deleted; QA2 created |
| Duplicate greeting bug | FIXED | QA2 got exactly one greeting |
| Agent editor fields (name/remark/base URL/key/model/AGENTS.md) | PASS | values saved |
| Provider sheet search and selection | PASS | 32 presets, half-screen, search filters |
| Model list fetch | PASS | OpenCode Zen returned models |
| Advanced parameters expand/collapse | PASS | collapsed summary, expanded fields |
| Custom headers key/value | PASS (earlier) | add/remove dialog, header persisted in draft |
| Chat with Echo agent | PASS | real Telegram chat, reply |
| Tool loop with mock model | PASS | `remember` result, `write_file` result, tool permission dialog |
| Long-term memory file | PASS | `/files/oxagents/<id>/memory.txt` contains the fact |
| Workspace file | PASS | `oxworkspace/<id>/qa/notes.txt` written, contents correct |
| MCP empty state / add / error | PASS | add dialog, server row, red connection error toast |
| MCP toggle / long-press delete | NOT RE-RUN in 0.8.4 | code path unchanged; tested in earlier build |
| Mode switch Ox → Login | PASS | confirmation, LoginActivity, stack reset |
| Data restore after mode switch | PASS | full data backup/restore, home restored |
| Login authentication | NOT TESTED | no account/code; no message or code was sent |
| Permission request taps | PARTIAL | statuses verified; accessibility/settings flows verified previously |
| Container mirror/version/components | PARTIAL | switch and cards verified; pickers unchanged from prior build |
| Import/export .oxagent | NOT RE-RUN | file picker path unchanged |
| Dark theme / rotation / real device | NOT TESTED | needs device pass |

## Findings fixed during this pass

1. **Duplicate agent greeting** — `syncAgents(false)` re-greeted before the first
   insert landed; now guarded by a storage-queue lock + per-dialog set.
2. **ADB permission label truncated** on the tools page — shorter dedicated string.
3. **Tool descriptions English** in a Chinese UI — localized for all built-in tools.
4. **Provider sheet** was full-height and had no search — half-screen + search.
5. **Advanced parameters** overwhelmed the form — collapsed by default, summary line.
6. **Ox chat list skeleton** — `DialogsAdapter` inserted a Flicker item while
   `isDialogsEndReached` was false; suppressed in agent-only mode.
7. **The “page content blocked by bottom bar”** — onboarding content and action bar
   are now separate vertical blocks; long pages scroll, nothing is overlaid.

## Residual risks / not covered

- Real Telegram login and the logged-in ↔ Ox switch were not exercised end-to-end
  (would require a real account/code; no message was sent).
- Android UI automation, OCR, voice, ToolPkg/Skills, workflows, browser agent,
  character cards and the container host app are roadmap items (see
  `docs/operit-gap-analysis.md`).
- “Every possible user operation” is combinatorially unbounded; this pass covered
  every visible page and control category plus the main write paths above.
