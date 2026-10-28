# QA — alpha 0.37.0（MCP 服务器 JSON 导出）

- 版本：`0.37.0`，versionCode **3709**（`0.36.0` 为 3609）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 08:48–09:02（CST）
- 目标：把本机 MCP 服务器导出为 `mcpServers` JSON，供 Kelivo/RikkaHub/
  Claude Desktop 或本应用重新导入；导出明确剔除 headers / API Key。
- 版本规则：新增“MCP 服务器 JSON 导出”这一可见能力，按 minor 从 0.36.0 →
  **0.37.0**，versionCode 3709（370×10+9）。

## 1. 新增能力

- MCP 服务页新增 “Export MCP JSON”，SAF 创建 `orioneko-mcp.json`。
- 导出结构：`{"mcpServers":{name:{"type":"streamableHttp","url":...,
  "isActive":...}}}`；只导出 http/https 服务器，同名 key 加序号后缀。
- **不导出 headers**（含 Authorization/token）、OAuth、stdio/SSE、工具白名单；
  不联网、不改服务器状态。
- 结果 toast “Exported N MCP server(s)”；无 HTTP 服务器时提示
  “No HTTP MCP servers to export”，不写文件。
- 服务器列表与全量导出保持原样；不做连通性测试。

## 2. 设备验证 — PASS

样例：通过 MCP 页“从 JSON 导入 → 从文件”加入测试服务器
`MCP Export Test`，URL `https://example.invalid/mcp-export`，headers
`Authorization: Bearer QA_SECRET_0370`、`X-Test: 1`。

- 导出：`orioneko-mcp.json` 390 B，包含 QA MCP、DeepWiki、R2C MCP、
  MCP Export Test 四个条目；全部为 `streamableHttp` + url + isActive，
  **没有 headers 字段**，文件中搜索 `QA_SECRET_0370` 为 0 次。
- 内部 `files/oxmcp/servers.json` 仍保留测试 header，证明导出层做了剔除，
  不是导入时丢失。
- 回环：长按删除 `MCP Export Test` 后，导入刚导出的 JSON；预览 1 个新增/
  其余 3 个按 URL 跳过，确认后 `MCP Export Test` 的 URL 恢复，headers 为空
  （导出不带 headers，符合预期），原有 3 个服务器未变。
- 清理：再次删除测试服务器，`servers.json` 仅剩原有 QA MCP、DeepWiki、
  R2C MCP；删除 Download 中的测试/导出 JSON；无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.37.0-mcp-page.jpg`、
  `qa-0.37.0-import-preview.jpg`、`qa-0.37.0-roundtrip-preview.jpg`。

## 3. 未覆盖 / 已知边界

- 只导出 HTTP/streamableHttp；SSE、stdio、inmemory、OAuth 与工具白名单
  不导出。
- 明确不导出 headers/密钥，因此回环后需要用户重新填写认证 header。
- 无单选/多选，导出当前全部 HTTP 服务器；不做连通性测试。
- 外部 Kelivo/RikkaHub/Claude Desktop 尚未实际导入该 JSON 验证。

## 4. 产物

- `dist/OrionekoX-0.37.0-alpha-arm64-x64.apk`（versionCode 3709），sha256
  `7b1b8ebd7528baea1dfe1c6ee1fc8cce3e78bf72dc919d4e5c159786e4224bce`
- `dist/OrionekoX-0.37.0-debug-arm64-x64.apk`，sha256
  `e6d3b625262a4b2f24955f5aeedc7d5470bc7ec886935b235b9654a182ace0b7`
