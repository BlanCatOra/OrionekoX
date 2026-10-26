# QA — MCP file import and ZIP skill import (0.8.111, 2026-10-26 20:24–20:35)

Build: `io.github.blancatora.orionekox` versionName 0.8.111 (versionCode 1269).

## MCP JSON from file

- The “从 JSON 导入” dialog now has a neutral “从文件” button; it opens the system
  document picker and reads up to 256 KB of UTF-8 JSON, then reuses the paste parser.
- Pushed `kelivo-mcp.json`:
  `{"mcpServers":{"TestImport":{"url":"https://example.com/mcp","headers":{"X-Test":"1"}},"StdioOne":{"command":"npx",...}}}`
- Picker → file → imported. `files/oxmcp/servers.json` gained
  `TestImport` with URL `https://example.com/mcp` and headers `{"X-Test":"1"}`;
  `StdioOne` was correctly skipped as unsupported stdio.
- The MCP list refreshed and showed `TestImport / https://example.com/mcp`.
- Long-press → 删除 removed the test server; QA MCP and DeepWiki remain.

## SKILL.md inside a zip

- Created `zip-skill.zip` containing `ZipSkill/SKILL.md` (YAML frontmatter + body),
  pushed to Download.
- 技能库 → 导入 SKILL.md / zip → file picker → zip.
- List refreshed with “Zip 导入技能 · 验证 zip 内的 SKILL.md 导入”; the app-private
  directory contains `files/oxskills/ZipSkill/SKILL.md`.
- Chinese frontmatter/body imported correctly; no FATAL/ANR.

## Notes

- The zip path was already implemented in 0.8.106; this pass is the first end-to-end
  verification. Idempotent re-import was not repeated (a new folder is created by
  design).
- Test MCP server was removed; test skills remain as synthetic artifacts
  (`orioneko-test-skill`, `ZipSkill`).
