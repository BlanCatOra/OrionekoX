# QA — alpha 0.26.0（Kelivo / Chatbox 备份助手导入）

- 版本：`0.26.0`，versionCode **2609**（`0.25.2` 为 2529）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 03:34–03:48（CST）
- 目标：推进 Kelivo/Chatbox 兼容：从 Chatbox 备份 zip 或 JSON 中读取
  `copilots.json` 与 `assistants_v1`，导入助手名称、人设、采样参数与请求头。
- 版本规则：新增一类第三方备份的助手导入能力，按 minor 从 0.25.2 →
  **0.26.0**，versionCode 2609（260×10+9）。

## 1. 新增能力

- Ox 设置新增“从 Kelivo / Chatbox 备份导入”，支持 zip 或 JSON，SAF 选择。
- 解析：
  - zip 内 `copilots.json`（id/name/prompt/picUrl）；
  - `settings.json` 中的 `assistants_v1`（JSON 字符串、数组或 id→对象映射）；
  - 直接选择 JSON 数组/对象时也支持同样字段；递归扫描深度 3。
- 映射到本地 Agent：
  - name；`systemPrompt` 或 `prompt` → `AGENTS.md`；
  - `temperature`/`topP`/`maxTokens`；
  - `customHeaders` → Agent `headers`；
  - `chatModelId` 作为模型名覆盖；
  - Provider 从现有第一个有 `baseUrl + apiKey` 的 Agent 继承（baseUrl/key/context）；
    没有可用 Provider 时按离线模式并提示。
- Agent id 为 `kelivo_<id>`；重复导入更新同一 Agent，保留 `tg_id` 与自定义头像。
- 预览显示助手数量、前 8 个名称、已存在数量；明确提示不含对话、Provider 密钥、
  技能与 MCP。

## 2. 设备验证 — PASS（schema 样例）

样例 zip：
`copilots.json=[Chatbox Copilot(prompt)]`，
`settings.json={"assistants_v1":"[Kelivo Assistant(systemPrompt, chatModelId,
temperature 0.5, topP 0.9, maxTokens 256, X-Kelivo:1)]"}`。

- 预览：`助手 2 个`，名称 Chatbox Copilot / Kelivo Assistant，提示只导入助手。
- 导入：`新建助手 2，更新 0`，生成 `kelivo_cb-1`、`kelivo_ka-1`。
- `kelivo_ka-1/agent.json`：`agents_md` 正确、`model=kelivo-test-model`、
  base_url 继承现有 Provider、`temperature=0.5`、`top_p=0.9`、
  `max_tokens=256`、`headers=[["X-Kelivo","1"]]`。
- `kelivo_cb-1`：人设来自 prompt，模型继承现有 Provider。
- 重复导入：`新建助手 0，更新 2`，仍只有 2 个 `kelivo_*` Agent。
- 截图：`docs/screenshots/qa-0.26.0-kelivo-preview.jpg`、
  `qa-0.26.0-kelivo-result.jpg`。
- 清理：删除两个测试助手与 Downloads 样例；无 Key 泄漏、无 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 只导入助手/人设；Kelivo 的 Provider 配置与密钥、对话历史、skills、MCP、
  工作区、头像背景均未导入。
- `copilots.json` 只有 `prompt`，没有采样参数/模型接线；`assistants_v1` 的
  `chatModelId` 可能是逻辑模型 id，不一定等于服务端真实模型名。
- 样例按当前 Kelivo/Chatbox 源码字段构造，尚未用真实 Kelivo 导出备份复测。
- 未导入 `presetMessages` 开场白、`skillIds`、`mcpServerIds`、regex 规则等。

## 4. 产物

- `dist/OrionekoX-0.26.0-alpha-arm64-x64.apk`（versionCode 2609），sha256
  `bdf2b2a2d03ecf25c50cccb8c38ef44812bb08dea12e80790dc36b3d3ee17f89`
- `dist/OrionekoX-0.26.0-debug-arm64-x64.apk`，sha256
  `f02f1a18c1ea1f1af6ea1d3f8262ec2d2639d7e8aa86f2311dea59e595df9019`
