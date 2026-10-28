# Harness adapters: compact, memory, tools, multimodal, non-text

Date: 2026-10-28. Status: 0.56.0 is the routing/guard foundation; only API
direct is device-tested. The OxLinux command hook is wired through
`OxContainerManager` but has no persistent session/PTY test yet. The other
harnesses have command/capability metadata and configurable compact templates,
but their session transports (ACP/Gateway/SSH/PTY) are not connected. Do not
claim end-to-end support for them.

## Ownership rule

| Mode | Context / compact owner | Local memory library |
|---|---|---|
| API direct (`harness=api`) | OrionekoX: `OxContextCompactor` + `OxMemoryStore` | Yes |
| OxLinux | The harness inside the container; OrionekoX only sends its compact command | No injection |
| Claude Code / OpenCode / CommandCode / OpenClaw / Hermes / CodeBuddy / DSH / Pi | That harness | No injection |
| Other device agent | The remote agent; transport pending | No injection |
| Generic harness | User-provided command | No injection |

The local compact must never rewrite an external harness's session history.
`OxHarnessAdapter.maybeCompact` only triggers the configured command and is
rate-limited to once per half threshold window.

## Capability matrix

| Harness | Compact | Resume/session | Tool protocol | Multimodal | Non-text events | Status |
|---|---|---|---|---|---|---|
| API direct | local TextRank/SummaryBuffer | own chat history | OpenAI tools | inline image base64 | text placeholders | implemented, device-tested |
| OxLinux | configurable command through `OxContainerManager` | container session id | harness | harness | harness | command hook wired; persistent PTY/session test pending |
| Claude Code | `/compact`; `--autocompact auto|tokens` | `--resume`, `--continue`, background sessions | MCP / ACP | files/images | tool events | selector + template; transport pending |
| OpenCode | `opencode run --session … "/compact"` (template, unverified) | `--session`, `session export/import`, `api` OpenAPI | MCP / OpenAPI | `--file` | event stream | selector; endpoint unverified |
| CommandCode | `/compact`; `-p "/compact" --resume …` | `--resume`, `--session`, `-p --output-format json` | MCP / ACP | Vision | NDJSON events | selector + template; transport pending |
| OpenClaw | configurable (Gateway/session command unverified) | Gateway sessions, `agent --message` | Gateway / ACP | companion nodes | channel events | selector; transport pending |
| Hermes | user command | unknown | unknown | unknown | unknown | metadata only |
| CodeBuddy | user command | unknown | unknown | unknown | unknown | metadata only |
| DeepSeek Harness | user command | unknown | unknown | unknown | unknown | metadata only |
| Pi | user command | unknown | unknown | unknown | unknown | metadata only |
| Other device | user command | SSH/custom pending | custom | custom | custom | metadata only |
| Generic | user command | user | user | user | user | metadata only |

The only default templates shipped are Claude Code
(`claude -p "/compact" --resume {session}`) and CommandCode
(`commandcode -p "/compact" --resume {session}`), taken from their public CLI
help; even these need a real transport/session test before being called
supported. OpenCode/OpenClaw/OxLinux/Hermes/CodeBuddy/DSH/Pi have no default
until their compact invocation is verified.

## Tool calls

- API direct: OpenAI `tools`/`tool_calls`, executed by `OxBuiltinTools` and MCP.
- Claude Code: MCP servers and ACP; tool results are harness events, not
  OpenAI tool messages.
- OpenCode: MCP plus an OpenAPI server; adapters must translate harness parts.
- CommandCode: MCP/ACP and NDJSON tool events.
- OpenClaw: Gateway tools/ACP.
- Hermes/CodeBuddy/DSH/Pi/other device: protocol unknown; the adapter must not
  pretend OpenAI compatibility.

## Multimodal and non-text messages

- API direct: text plus inline images; files are represented as document
  context; audio/video are not sent to the model yet.
- External harnesses: every adapter declares `multimodal` and
  `nonTextEvents`; until a transport exists, OrionekoX only stores/normalizes
  the message and does not silently convert unsupported media.
- Planned normalization: text, image, audio, video, file, tool_call,
  tool_result, system/status, command output. The adapter maps each to the
  harness's own format and preserves unknown parts as labeled text.

## Next steps

1. Add a persistent harness session/PTY layer for OxLinux, then test the
   configurable compact command end to end.
2. Implement ACP for Claude Code/CommandCode/OpenClaw and the OpenCode
   session/OpenAPI compact endpoint; verify each on a real session.
3. Add adapter-specific threshold configuration and read the harness's own
   compaction status instead of guessing.
4. Implement per-harness memory import/mirror and tool-result normalization.
5. Add multimodal/non-text fixtures per harness; do not mark a harness
   supported until its compact and resume paths are verified.
