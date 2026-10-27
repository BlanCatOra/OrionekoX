# QA — alpha 0.19.0（RikkaHub 备份 settings.json 导入）

- 版本：`0.19.0`，versionCode **1909**（`0.18.0` 为 1809）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 00:10–00:35（CST）
- 目标：推进三位一体兼容，读取 RikkaHub 备份 zip 的 `settings.json`，导入
  助手（含 Provider 接线）、远程 MCP 服务器；导入前预览，重复导入更新/跳过，
  不导入聊天数据库、附件、OAuth token。
- 版本规则：新增一套外部备份格式的导入能力，按 minor 从 0.18.0 → **0.19.0**，
  versionCode 1909（190×10+9）。

## 1. 格式与映射

- RikkaHub 备份是 zip：`settings.json` + `rikka_hub.db` + `files/`（uploads、
  skills、fonts 等）。本轮只读取 `settings.json`，其余条目忽略。
- Provider（`providers[]`）：`type` 为 `openai` / `google` / `claude`，取
  `baseUrl`、`apiKey`、`models[].id`/`modelId`/`contextLength` 建立
  “模型 UUID → Provider”映射。
- Assistant（`assistants[]`）：`id`、`name`、`systemPrompt`、`chatModelId`、
  `avatar.type=emoji`、`temperature`、`topP`、`maxTokens`、`reasoningLevel`。
- MCP（`mcpServers[]`）：只导入 `type=streamable_http` 的 `url` 与
  `commonOptions.name/enable/headers`；`type=sse` 计入跳过，OAuth 状态不导入。
- 写入 OrionekoX：
  - Agent id = `rikka_<assistant UUID>`；已存在同名 id 时更新，不新建。
  - Provider 解析顺序：助手 `chatModelId` → 全局 `chatModelId` → 第一个有
    API Key 且启用的 Provider；都没有则按离线模式导入。
  - `systemPrompt` → `AGENTS.md`；emoji 头像 → `emoji`；`contextLength`、
    `maxTokens`、`temperature`、`topP`、`reasoningLevel` 映射到 Agent 字段。
  - 更新时保留原 `tg_id` 与自定义头像路径，避免断开本地聊天。
  - MCP 以 URL 去重，已有 URL 跳过；headers 转成 JSON 对象。
- 预览显示助手数、实际引用的 Provider 数、可导入 MCP 数、已存在助手/MCP 数、
  前 8 个助手名和跳过说明；不显示任何 API Key。

## 2. 设备验证 — PASS（schema 样例）

样例按 RikkaHub 当前源码字段构造：
`providers=[RikkaTestProvider(openai, https://example.invalid/v1, 模型
rikka-test-model, context 32000)]`；`assistants=[Rikka QA(🦊, temp 0.4,
topP 0.9, maxTokens 512, HIGH), Rikka No Model(无效模型 UUID)]`；
`mcpServers=[RikkaTestMCP(streamable_http, headers X-Rikka-Test=1), 一个 SSE]`。
zip 内另有占位 `rikka_hub.db` 与 `skills/TestSkill/SKILL.md`，用于确认被忽略。

### 2.1 预览

- 提示“助手 2 个 · Provider 1 个 · MCP 1 个”；助手列表
  `Rikka QA、Rikka No Model`；说明“仅导入 settings.json 的助手 / Provider /
  MCP；不含聊天数据库、附件和 OAuth token”；SSE 跳过 1 个。
- 截图：`docs/screenshots/qa-0.19.0-rikka-preview.jpg`。

### 2.2 导入

- 结果：“新建助手 2，更新 0，新增 MCP 1，跳过 MCP 0”。
- `rikka_4444…/agent.json`：name `Rikka QA`、emoji 🦊、base_url
  `https://example.invalid/v1`、model `rikka-test-model`、provider_id `rikka`、
  context 32000、max_tokens 512、temperature 0.4、top_p 0.9、
  reasoning_effort `high`，`tg_id` 已分配。
- 第二个助手引用不存在的模型，按全局 `chatModelId` 使用同一 Provider；
  Provider 映射回退逻辑符合预期。
- `oxmcp/servers.json` 新增 `RikkaTestMCP`，headers 为
  `{"X-Rikka-Test":"1"}`；SSE 条目没有写入。
- 截图：`docs/screenshots/qa-0.19.0-rikka-result.jpg`、
  `qa-0.19.0-rikka-agents.jpg`。

### 2.3 幂等

- 再次导入同一 zip：预览显示“已存在助手 2 个、MCP 1 个”；结果为
  “新建助手 0，更新 2，新增 MCP 0，跳过 MCP 1”。
- `oxagents/rikka_*` 仍为 2 个目录，MCP 仍只有 1 条同名记录。
- 截图：`docs/screenshots/qa-0.19.0-rikka-idempotent.jpg`。

### 2.4 拒绝与密钥

- 导入 `{"foo":1}` 的 JSON：提示“RikkaHub 导入失败：不是有效的 RikkaHub 备份”，
  不产生 Agent/MCP 变更。截图：`qa-0.19.0-rikka-invalid.jpg`。
- `logcat` 全文检索测试 Key（`sk-rikka-test-not-real` 一类）命中 0 次；
  预览与结果弹窗均只显示数量/名称，不显示 Key。
- 最终构建再次导入样例：预览与“新建助手 2，新增 MCP 1”一致；`openInputStream`
  抛异常时统一转成“无法读取备份文件”，不再暴露 ENOENT。
- 全程无 FATAL/ANR。

### 2.5 清理

- 通过 Agents 长按菜单删除两个测试助手（含本地聊天），通过 MCP 长按菜单删除
  `RikkaTestMCP`；`oxagents` 下已无 `rikka_*`，`servers.json` 无该条目。
- Downloads 中 `rikka-backup-test.zip`、`not-rikka.json` 已删除。

## 3. 未覆盖 / 已知边界

- 只导入 `settings.json`；RikkaHub 的聊天数据库、附件、技能目录、工作区、
  Lorebook、快捷消息、模式注入均未导入。
- 独立 Provider 没有助手引用时不会落库；仅作为助手接线使用。
- 助手 `customHeaders`、图片头像、`presetMessages`/开场白、`enabledSkills`
  未映射；需要后续补。
- OAuth token 和 SSE 传输 MCP 不导入；用户需在 OrionekoX 内重新配置。
- 样例是按 RikkaHub 当前源码字段构造的 schema 样例，尚未用真机 RikkaHub
  导出的备份复测；字段兼容性需要真实备份验证。
- 导入的 API Key 走 `OxVault` 存储，不回显；但仍建议用户在可信备份上使用。

## 4. 产物

- `dist/OrionekoX-0.19.0-alpha-arm64-x64.apk`（versionCode 1909），sha256
  `e869a33fdcd6f38c113dd36fa74776add9225f8f6a62ef48c163d763fc85b127`
- `dist/OrionekoX-0.19.0-debug-arm64-x64.apk`，sha256
  `b5391295cc83b2b0009e023dcfdb606ab04be37055b28b2d122a90f77ca49aac`
