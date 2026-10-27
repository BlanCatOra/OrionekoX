# QA — alpha 0.27.2（Kelivo / Chatbox HTTP MCP 导入）

- 版本：`0.27.2`，versionCode **2729**（`0.27.1` 为 2719）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 04:42–04:58（CST）
- 目标：补完 Kelivo / Chatbox 备份导入：读取 `mcp_servers_v1`（兼容
  `mcpServers`）中的 HTTP MCP，写入本机 MCP 列表；SSE/stdio/inmemory 跳过。
- 版本规则：仅补既有 Kelivo 导入的 MCP 包，按 patch 从 0.27.1 → **0.27.2**，
  versionCode 2729（272×10+9）。

## 1. 新增能力

- 从 zip `settings.json` 读取 `mcp_servers_v1`，兼容 `mcpServers`；支持
  `streamableHttp` / `http` 条目，读取 name/id、url、headers 与
  `isActive`/`enabled`。
- 写入 `files/oxmcp/servers.json`：`OxMcpManager.McpServer`，headers 以 JSON
  字符串保存，默认启用；按 URL 与本机已有条目去重，重复导入不新增。
- 预览统计 HTTP MCP 数量与跳过数量，并提示“跳过 N 个 SSE/stdio/inmemory
  条目”；总览文案修正为“助手、人设、技能与 HTTP MCP；不含对话与 Provider
  密钥”。
- 结果新增“新增 MCP N 个，跳过 N 个”；仅当有 MCP 结果时追加该 note。
- SSE、stdio/command、inmemory 条目一律跳过，不执行外部命令。

## 2. 设备验证 — PASS

样例 zip：`settings.json` 含 1 个助手 `Kelivo MCP Assistant`；
`mcp_servers_v1` 含 `m1`（KelivoHTTP，`streamableHttp`，
`https://example.invalid/mcp`，header `X-Kelivo: 1`）与 `m2`
（KelivoSSE，`sse`，`https://example.invalid/sse`）。

- 预览：`助手 1 个`；note“仅导入 Kelivo / Chatbox 备份中的助手、人设、技能与
  HTTP MCP；不含对话与 Provider 密钥”“将导入 1 个 HTTP MCP；跳过 1 个
  SSE/stdio/inmemory 条目”。
- 导入：`新建助手 1，更新 0`，note“新增 MCP 1 个，跳过 1 个”；
  `servers.json` 新增 `KelivoHTTP`，URL `https://example.invalid/mcp`、
  enabled `true`、headers `{"X-Kelivo":"1"}`；`KelivoSSE` 不存在。
- 重复导入：`新建助手 0，更新 1`，note“新增 MCP 0 个，跳过 2 个”；
  `KelivoHTTP` 仍只有 1 条，未因 URL 相同重复写入。
- 截图：`docs/screenshots/qa-0.27.2-kelivo-mcp-preview.jpg`。
- 清理：删除测试助手目录 `kelivo_ka-1`、`KelivoHTTP` 条目与 Downloads 样例；
  重启后聊天列表无该助手；无 FATAL/ANR（crash buffer 仅剩此前 toybox grep
  的宿主侧 SIGSEGV，与应用无关）。

## 3. 未覆盖 / 已知边界

- 仅支持 HTTP/streamableHttp MCP；SSE、stdio、inmemory 明确跳过。
- 不导入 MCP 工具白名单、OAuth、动态客户端注册与环境变量；headers 原样保存。
- 不做连通性测试；URL 可访问性由用户后续在 MCP 页验证。
- 仍未导入 Kelivo 对话、Provider 密钥、工作区、头像/背景。
- 样例按 Chatbox/Kelivo 结构构造，真实 Kelivo 备份待复测。

## 4. 产物

- `dist/OrionekoX-0.27.2-alpha-arm64-x64.apk`（versionCode 2729），sha256
  `82b76f4e3a6924559f56ad216b5b2154ef64a0bafa5e008b4eeef664a95fb07c`
- `dist/OrionekoX-0.27.2-debug-arm64-x64.apk`，sha256
  `0e070cc8bed205bcfb7399382827f99f6cd3e307315028d23e0c7743d60a3113`
