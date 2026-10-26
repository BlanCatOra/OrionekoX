# QA — MCP `mcpServers` JSON import (0.8.105, 2026-10-26 18:55–19:10)

Build: `io.github.blancatora.orionekox` versionName 0.8.105 (versionCode 1209).

## What was added

- Ox 设置 → MCP 服务 now has a “从 JSON 导入” row (download icon) with the hint
  “粘贴 mcpServers JSON（Kelivo / RikkaHub / Claude Desktop）”.
- The dialog accepts a top-level `mcpServers` object (or a bare server map) and imports
  HTTP / streamable-HTTP servers: `url`/`baseUrl`, `headers`, `disabled`/`isActive`.
- Idempotent by URL; duplicate URLs are skipped. `stdio` and `sse` entries are counted
  separately: stdio is reported as unsupported, SSE is skipped because the runtime only
  speaks Streamable HTTP. The summary toast reports imported/skipped/stdio counts.
- Chinese pack 66 adds the new strings; English strings were added to
  `values/strings.xml`.

## Verified

- Built release + debug successfully; installed release.
- Navigation: 设置 → Ox 设置 → MCP 服务 shows QA MCP, DeepWiki, “＋ 添加 MCP 服务器”
  and the new “从 JSON 导入” row.
- Tapping the row opens the native `AlertDialog` with the multiline hint and
  取消 / 从 JSON 导入 buttons.

## Not fully exercised

- End-to-end paste was blocked by the emulator shell: `adb shell input text` strips
  double quotes, so a valid JSON object could not be typed into the dialog. The import
  path is code-reviewed and compiles, but the actual add/skip counts were not observed in
  this pass. A future pass should use a clipboard helper or a test-only fixture.
- No existing MCP server was added/removed; QA MCP (HTTP 502) and DeepWiki remain as-is.

## Next

- Theme JSON import (Kelivo/RikkaHub `primaryColorArgb`) and `SKILL.md` package import.
- Add a deterministic JSON fixture test so the parser is covered without the shell.
