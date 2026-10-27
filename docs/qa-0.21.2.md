# QA — alpha 0.21.2（RikkaHub Provider 请求头与开场白映射）

- 版本：`0.21.2`，versionCode **2129**（`0.21.1` 为 2119）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 01:36–01:48（CST）
- 目标：补完 RikkaHub 备份导入：Provider 级 `customHeaders` 与助手请求头合并；
  助手 `presetMessages` 的第一条 assistant 文本映射为 `first_mes`。
- 版本规则：只补既有导入字段映射，按 patch 从 0.21.1 → **0.21.2**，
  versionCode 2129（212×10+9）。

## 1. 新增映射

- Provider 的 `customHeaders` 与助手 `customHeaders` 合并：
  `LinkedHashMap` 按名称去重，助手同名 header 覆盖 Provider；没有 header 时
  不覆盖 Agent 已有值。
- 助手 `presetMessages` 中第一条 `role=assistant` 且含 `parts[].type=text`
  的消息写入 `agent.firstMes`；长度上限 4000 字符；找不到时保留原值。
- 映射在 Agent 保存前完成，重复导入走同一路径。

## 2. QA 中发现并修复的问题

- 第一版把 header 数组先转成 `[name,value]` 对，再在 `mergeHeaders` 里用
  `optJSONArray(i)` 读回；设备上出现 `hdr p=1 a=1 m=1`（Provider header 丢失）
  和 `hdr p=1 a=-1 m=0`（只有 Provider header 时全丢）。
- 改为直接遍历源 JSON 数组（兼容 `{name,value}` 与 `[name,value]`）写入
  `LinkedHashMap`，不再做二次 JSON 数组往返；复测 `hdr p=1 a=1 m=2`。

## 3. 设备验证 — PASS

样例：Provider `customHeaders=[X-Rikka-Provider: p]`；助手
`customHeaders=[X-Rikka-Assistant: 1]`，
`presetMessages=[{role:assistant, parts:[{type:text, text:"Welcome from RikkaHub preset."}]}]`。

- 导入 `Rikka QA` 后 `agent.json`：
  - `first_mes: "Welcome from RikkaHub preset."`
  - `headers: [["X-Rikka-Provider","p"], ["X-Rikka-Assistant","1"]]`
  - `skill_ids: ["TestSkill"]`
- 重复导入：`新建 0，更新 2，新增 MCP 0，跳过 MCP 1，导入技能 1`；
  Agent 仍为 2 个，上述字段保持一致。
- 无 Key 泄漏、无 FATAL/ANR。
- 清理：测试后删除两个测试助手、RikkaTestMCP、TestSkill 与 Downloads 样例。

## 4. 未覆盖 / 已知边界

- Provider header 只在该 Provider 的模型被助手引用时导入；没有助手引用的
  独立 Provider 仍不落库。
- 同名 header 助手覆盖 Provider，未提供“保留两份”的选项。
- `presetMessages` 只取第一条 assistant 文本；图片、工具消息、多条预设消息
  和 `first_mes` 之外的开场结构未导入。
- 聊天数据库、附件、OAuth、SSE MCP 仍未导入；真实 RikkaHub 导出备份未复测。

## 5. 产物

- `dist/OrionekoX-0.21.2-alpha-arm64-x64.apk`（versionCode 2129），sha256
  `16b01215b881239b7d338de46bf3a75336b14bd29bb4d38d7f562089279f0c6e`
- `dist/OrionekoX-0.21.2-debug-arm64-x64.apk`，sha256
  `3475b1bfb461604671bbb5b2372063a221319061afc2a85b7f7257fd83fcf808`
