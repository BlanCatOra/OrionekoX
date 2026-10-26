# QA — MCP import preview and idempotent re-import (0.8.112, 2026-10-26 20:40–20:47)

Build: `io.github.blancatora.orionekox` versionName 0.8.112 (versionCode 1279).

## Change

- `importMcpJson` now parses into a plan first: unique HTTP/streamable servers are
  listed, duplicates are skipped, stdio is counted separately, invalid/SSE entries are
  skipped.
- A native `AlertDialog` previews “将导入 N 个服务器，跳过 M 个” plus the first eight
  names/URLs and a stdio note. Only 从 JSON 导入 confirms and writes; 取消 leaves the
  store untouched. If nothing new is available, it only shows the summary toast.

## Verified

- Imported `kelivo-mcp.json` (1 HTTP + headers, 1 stdio) from Downloads:
  - preview: “将导入 1 个服务器，跳过 1 个 / 1 个 stdio 服务器暂不支持 / • TestImport
    https://example.com/mcp”;
  - confirm: list shows TestImport; `files/oxmcp/servers.json` contains exactly one
    TestImport with the header JSON.
- Re-imported the same file: no preview dialog, `servers.json` still contains exactly
  one TestImport (0 new, 2 skipped incl. stdio).
- Long-press → 删除 removed the test server; QA MCP and DeepWiki remain.
- No FATAL/ANR.

## Notes

- Preview lists at most eight servers; larger imports show an ellipsis but still apply
  the full plan after confirmation.
- The same duplicate rule is URL-based, matching the existing store.
