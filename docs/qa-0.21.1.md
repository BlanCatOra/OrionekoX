# QA — alpha 0.21.1（RikkaHub 技能分配与助手请求头映射）

- 版本：`0.21.1`，versionCode **2119**（`0.21.0` 为 2109）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 01:25–01:35（CST）
- 目标：补完 0.20.0 的 RikkaHub 备份导入：把备份里的 `enabledSkills` 关联到
  导入后的 Agent，把助手的 `customHeaders` 映射到 Agent 请求头；重复导入保持
  幂等。
- 版本规则：只补两个既有导入字段的映射，按 patch 从 0.21.0 → **0.21.1**，
  versionCode 2119（211×10+9）。

## 1. 新增映射

- 技能先写入技能库，再导入 Agent；按小写名称或技能 id 建立
  “名称/id → 技能目录 id”映射。
- 助手 `enabledSkills` 非空时逐个匹配，匹配到的 skill id 写入
  `agent.skillIds`；备份里没有该技能则忽略。`enabledSkills` 为空时不覆盖
  Agent 已有的技能选择。
- 助手 `customHeaders`（`{name,value}` 数组）映射为 Agent 的
  `headers`（`[[name,value], ...]`）；非空才写入，空数组保留现有值。
- 两条映射都在 Agent 保存前完成，因此新导入和重复导入走同一路径。

## 2. 设备验证 — PASS

样例：`Rikka QA` 的 `enabledSkills=["Rikka Test Skill"]`（技能 frontmatter 名称，
目录 id 为 `TestSkill`），`customHeaders=[{"name":"X-Rikka-Assistant","value":"1"}]`；
备份同时含 `skills/TestSkill/SKILL.md`。

- 首次导入：`新建助手 2，更新 0，新增 MCP 1，跳过 MCP 0，导入技能 1`。
- `rikka_4444…/agent.json`：
  - `skill_ids: ["TestSkill"]`（按 frontmatter 名称命中目录 id）；
  - `headers: [["X-Rikka-Assistant","1"]]`。
- 重复导入：`新建助手 0，更新 2，新增 MCP 0，跳过 MCP 1，导入技能 1`；
  Agent 目录仍为 2 个，`skill_ids`/`headers` 保持一致。
- 截图：`docs/screenshots/qa-0.21.1-rikka-mapping-result.jpg`。
- 清理：测试后删除两个测试助手、RikkaTestMCP、TestSkill 目录与 Downloads
  样例；无 Key 泄漏、无 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 只匹配备份中实际存在的技能；未导入的技能名称会被忽略，不会自动创建。
- 空 `enabledSkills`/空 `customHeaders` 不覆盖已有值，避免重复导入清空用户选择。
- 只映射助手的 `customHeaders`；Provider 级请求头、OAuth、图片头像、开场白
  仍未映射。
- 测试样例按 RikkaHub 源码 schema 构造，真实 RikkaHub 导出备份仍待复测。

## 4. 产物

- `dist/OrionekoX-0.21.1-alpha-arm64-x64.apk`（versionCode 2119），sha256
  `af4c78660ea1b71629ef93e0d3455d0359ea75cb4371c7aea2565c79c25f3c0a`
- `dist/OrionekoX-0.21.1-debug-arm64-x64.apk`，sha256
  `9944a2c2c0054c8b9347ccf7ca2738e6f867292f81e170cbe64334e956153185`
