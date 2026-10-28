# Harness adapters: bridge, compact, tools, multimodal

Date: 2026-10-28. Status: **0.57.0 adds the first real external-harness
transport.** A host-side bridge (`tools/ox-harness-bridge.py`) drives the four
installed CLIs with their own native sessions and returns normalized NDJSON.
OpenClaw, CommandCode and OpenCode complete real turns and native compact on
this machine; Claude Code's transport runs but its provider rejects the
account with HTTP 403, so Claude is not claimed as a working model path here.
OxLinux has a persistent `proot` shell implementation, but Android denies
executing binaries in app data (`execve("/usr/bin/dash"): Permission denied`),
so its compact hook remains blocked on this device.

## Why a host bridge

The Android app cannot spawn Claude Code, CommandCode, OpenCode or OpenClaw in
its sandbox. The bridge runs on the machine that owns those CLIs and exposes:

```
GET    /v1/health
POST   /v1/sessions                 {harness, cwd, model, agent, system}
GET    /v1/sessions/<id>
POST   /v1/sessions/<id>/turns      {text, attachments}  -> NDJSON
POST   /v1/sessions/<id>/compact
GET    /v1/sessions/<id>/context
DELETE /v1/sessions/<id>
```

All routes require `Authorization: Bearer <token>`. The bridge binds loopback
by default; `--host 0.0.0.0` is only for a trusted LAN/emulator and must keep a
token. Sessions are persisted in
`~/.local/state/orionekox-harness-bridge/sessions.json` (0600), without prompt
text. The token is generated into
`~/.config/orionekox/harness-bridge-token` (0600) and can be printed with
`--print-token`.

Run:

```bash
python3 tools/ox-harness-bridge.py \
  --host 0.0.0.0 --port 8765 \
  --workdir ~/orionekox-harness
python3 tools/ox-harness-bridge.py --print-token
```

In the app: **Agent settings → Connection / harness** selects the harness,
then fill **Bridge URL** (for the emulator `http://10.0.2.2:8765`), **Bridge
token**, optional working directory/model/OpenClaw agent id, and tap **Test
harness bridge**. A missing bridge or an unreachable host surfaces as an
error; the reply pipeline never falls back to the local model or echo mode.

## Ownership rule

| Mode | Context / compact owner | Local memory library |
|---|---|---|
| API direct (`harness=api`) | OrionekoX: `OxContextCompactor` + `OxMemoryStore` | Yes |
| Bridged Claude/CommandCode/OpenCode/OpenClaw | The harness, driven through the bridge | No injection |
| OxLinux | Persistent container shell; user command | No injection |
| Hermes/CodeBuddy/DSH/Pi/device/generic | Harness/device owner | No injection |

`appendMemoryContext` now calls `maybeCompact` **before** the local memory-off
early return, so disabling local memory no longer disables the harness's own
compact request. `OxHarnessAdapter.requestCompact` routes a configured bridge
to its native compact endpoint before looking at any local template.

## Verified transports

| Harness | Turn transport | Native resume | Native compact | Evidence |
|---|---|---|---|---|
| OpenClaw | `openclaw agent --session-id … --json` (Gateway CLI) | Same session id | `openclaw sessions compact <key> --json` | real turn, second turn, compact, post-compact turn all returned `BRIDGE-OCL-731` |
| CommandCode | `commandcode -p … --output-format json` | `--resume <sessionId>` from NDJSON | interactive PTY, wait for idle prompt, send `/compact` | real turn, resume, PTY confirmed `对话已经过压缩`, post-compact turn returned `BRIDGE-CC-731` |
| OpenCode | `opencode run --format json --session …` | `--session <ses_…>` | private `opencode serve` + `POST /api/session/<id>/compact` (Basic `opencode:<password>`) | real turn, resume, HTTP 200 compaction message, post-compact turn returned `BRIDGE-OC-731` |
| Claude Code | `claude -p … --output-format stream-json --session-id/--resume` | UUID session | `claude -p "/compact" --resume <id>` | transport initialized and streamed the provider's 403; compact returned `Not enough messages to compact.` The model path is **not working** on this account |
| Fake (tests) | in-process | bridge session | in-process | 15 unit/HTTP tests pass |

Bridge smoke artifacts: `/tmp/ox-bridge-smoke/{openclaw,commandcode,opencode,claude}.json`.
These are one-off validation records, not bundled test fixtures.

## Normalized events

The bridge translates each harness into one event schema consumed by
`OxHarnessTransport` and rendered by `OxAgentsBridge`:

| Event | App behavior |
|---|---|
| `session` | records the native session id for logs/compact; never replaces the bridge session id |
| `text` | streams into the agent bubble |
| `tool_call` | `🔧 name(args) …` message + chat entry |
| `tool_result` | `↳`/`⚠` message + chat entry |
| `command_output` | `⌨` message + chat entry (when a harness emits it) |
| `status` | logged; `retrying`/`notice` shown as `ℹ`; OpenClaw context status logged |
| `attachment` | `📎` URL + chat entry; no silent download/conversion |
| `error` | finishes the turn with `⚠` and the harness's own message |
| `done` | final text, native session id, usage |

Per-harness mapping:

- **Claude Code**: `stream_event.content_block_delta` → text; `assistant`
  `tool_use` → tool_call; `user` `tool_result` → tool_result; `system.status`
  (`requesting`, `compacting`) → status; `result` → done/error. Partial text is
  not duplicated by the full assistant message.
- **CommandCode**: `text_delta` → text; `tool_queued`/`tool_completed` →
  tool events; `thinking_*` kept out of the visible answer; `api_retry` →
  retrying status; `run_end`/`result` → done/error.
- **OpenCode**: `text` → text; `tool_use` with `state.status` → call/result;
  `step_start`/`step_finish` → status; `error` → error.
- **OpenClaw**: one JSON result per turn (no token streaming); payload text →
  text, `mediaUrl` → attachment, `agentMeta.usage`/`contextBudgetStatus` →
  usage/status. `sessionFile` is retained as the Gateway compact key.

Attachments from the app are sent as base64, written under
`<cwd>/.ox-attachments/` with a random prefix and 0600 mode, then:
OpenCode receives each path through `--file`; Claude/CommandCode/OpenClaw get
a labeled path list in the prompt so their own file tools can read it. This is
a real path handoff, **not** a claim of harness-native inline vision.

## Harness context telemetry

The bridge records the last turn's usage/context per session and exposes it at
`GET /v1/sessions/<id>/context`. It stores only token/budget/model fields, never
prompt or reply text.

- OpenClaw: `contextBudgetStatus` (`should_compact`, `prompt_tokens`,
  `context_budget`) plus usage.
- CommandCode: `inputTokens`/`outputTokens`/`cacheReadTokens` from run events.
- OpenCode: private `serve` `GET /api/session/<id>` merges session `tokens`
  (`input`, `output`, `reasoning`, `cache.read/write`), model and cost.
- Claude Code: result usage when a turn succeeds; currently the provider 403s.
- `done` events also carry the same `context` object, so the app updates
  without an extra request.

The app shows a **Harness context** row in the agent editor and calls
`OxHarnessAdapter.maybeCompact` with the harness-reported `should_compact`
(with a 60-second cooldown) in addition to the local history threshold. A true
`should_compact` value has not been forced on a real harness yet; do not claim
automatic native compaction was device-verified.

## Explicit memory mirror (0.59.0)

External harness agents never receive local memory. `harness_mirror` is an
explicit, default-off switch on bridged agents: user/assistant turns and any
tool events the app actually received are written to `OxMemoryStore` as
`source=harness-mirror` / `harness-mirror-tool` rows. The mirror uses stable
text hashes (no timestamp), so retries and reloads are idempotent. Mirror rows
are visible in the memory stats and the Memory library dialog, but are **not**
injected into the harness, because external agents still return before local
digest/recall. If the agent is later switched to API mode, those rows become
available to ordinary local recall. Mirroring is not compaction and does not
change the harness's own history.

## Boundaries (do not overclaim)

- Claude Code's transport is implemented and tested, but the provider returned
  `403 Request not allowed` (`authentication_failed`). It must not be shown as a
  usable model path until a working account is verified.
- OpenClaw's CLI path is request/response JSON, not streaming. The app shows
  the final text after the turn completes.
- OpenClaw tool results are not exposed by its `--json` payload; only payload
  text/media and usage are normalized. Do not claim per-tool rendering.
- The bridge has no TLS. Use it on loopback or a trusted LAN only.
- Sessions store the system prompt and working directory locally (0600); the
  registry deliberately does not store conversation text.
- External memory mirroring/ownership handoff, per-harness thresholds, reading
  the harness's own compaction state, and embedding-backed recall remain
  future work.
- The bridge does not auto-start from the app; the user starts it on the host.
- OxLinux: `runPersistent` uses one `proot` `/bin/sh` (`/usr/bin/dash` fallback)
  and a sentinel-delimited script channel. On this Android emulator the real
  error is `proot error: execve("/usr/bin/dash"): Permission denied`, because
  app-data executables are blocked by the platform. The persistent session is
  implemented but not usable for rootfs binaries on this device.

## Tests

```bash
python3 -m unittest tools/test_ox_harness_bridge.py -v
```

Covers fake turn streaming, attachments, error events, compact, auth, session
persistence/delete, context endpoint/done.context, OpenCode session token
normalization, and recorded event-shape parsers for CommandCode, OpenCode and
Claude (15 tests).
