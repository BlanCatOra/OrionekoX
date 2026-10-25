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
| UI automation | accessibility/ADB/root, PhoneAgent | none | **high-value gap** |
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
