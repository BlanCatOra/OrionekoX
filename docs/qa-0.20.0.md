# QA — alpha 0.20.0（RikkaHub 备份 skills/ 导入）

- 版本：`0.20.0`，versionCode **2009**（`0.19.0` 为 1909）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 00:50–01:05（CST）
- 目标：在 0.19.0 的 RikkaHub 备份导入基础上，读取备份 zip 内的
  `skills/<name>/SKILL.md`，导入 OrionekoX 私有技能库；预览、同名更新、
  不删除本地其他技能。
- 版本规则：新增第三方备份技能包导入能力，按 minor 从 0.19.0 → **0.20.0**，
  versionCode 2009（200×10+9）。

## 1. 新增能力

- `OxRikkaImporter` 现在一次扫描备份 zip：`settings.json` 仍按 0.19.0 映射
  助手/Provider/MCP；同时收集 `skills/` 下以 `SKILL.md` 结尾的条目。
- 每个技能上限 512 KB，单次最多 50 个；条目总数仍限制 1000。
- 技能 id 取 `skills/` 后的相对目录（嵌套目录用 `_` 连接），再由
  `OxSkillStore.skillId()` 统一 sanitize；写入
  `files/oxskills/<id>/SKILL.md`。
- 同名技能更新原文件，不生成 `_1` 副本，也不删除技能库里的其他技能。
- 预览新增技能总数、前 8 个技能 id、已存在技能数；导入结果新增“导入技能 N”。
- 直接选择 `settings.json`（非 zip）时技能数为 0，行为与 0.19.0 一致。

## 2. 设备验证 — PASS

样例 zip：`settings.json`（2 个助手、1 个 Provider、1 个 HTTP MCP、1 个 SSE）
加 `skills/TestSkill/SKILL.md`（152 B，含 `name`/`description` frontmatter），
另有占位 `rikka_hub.db`。

### 2.1 预览

- 显示“助手 2 个 · Provider 1 个 · MCP 1 个 · 技能 1 个”，
  助手 `Rikka QA、Rikka No Model`，技能 `TestSkill`；
  说明“仅导入 settings.json 与 skills/ 技能；不含聊天数据库、附件和
  OAuth token”“技能按目录名导入，同名技能更新，不删除本地其他技能”；
  SSE 跳过 1 个。
- 截图：`docs/screenshots/qa-0.20.0-rikka-skills-preview.jpg`。

### 2.2 导入

- 结果：“新建助手 2，更新 0，新增 MCP 1，跳过 MCP 0，导入技能 1”。
- `files/oxskills/TestSkill/SKILL.md` 存在，152 B，内容保留 frontmatter：
  `name: Rikka Test Skill` / `description: Test skill imported from a RikkaHub backup`。
- 截图：`docs/screenshots/qa-0.20.0-rikka-skills-result.jpg`。

### 2.3 幂等

- 再次导入同一 zip：预览显示“已存在助手 2 个、MCP 1 个、技能 1 个”；
  结果为“新建助手 0，更新 2，新增 MCP 0，跳过 MCP 1，导入技能 1”。
- `files/oxskills` 下仍只有 1 个 `TestSkill` 目录，没有 `TestSkill_1`。
- 截图：`docs/screenshots/qa-0.20.0-rikka-skills-idempotent.jpg`。

### 2.4 安全与清理

- 预览/结果未显示 API Key；`logcat` 检索测试 Key 命中 0；无 FATAL/ANR。
- 测试后通过 UI 删除两个测试助手与 `RikkaTestMCP`；测试技能目录
  `files/oxskills/TestSkill` 与 Downloads 样例 zip 已删除。

## 3. 未覆盖 / 已知边界

- 只导入 `SKILL.md` 正文；RikkaHub 技能的附件、脚本、额外资源文件未导入。
- 技能目录名相同会更新同一 id；极端情况下 sanitize/截断后可能碰撞。
- 技能只是进入技能库，不会自动分配给助手；RikkaHub 的 `enabledSkills`
  仍未映射到 Agent。
- 仍不导入 RikkaHub 聊天数据库、附件、工作区、Lorebook、快捷消息、OAuth
  和 SSE MCP；真实 RikkaHub 导出备份仍未复测。

## 4. 产物

- `dist/OrionekoX-0.20.0-alpha-arm64-x64.apk`（versionCode 2009），sha256
  `cae7554226d5593a42f8d281783ddce19a8eec7b045f7b5adcffaf3e3bd643b2`
- `dist/OrionekoX-0.20.0-debug-arm64-x64.apk`，sha256
  `79f538d0d2d39bce3a646a9a7d9af2acd976d688cd84a4a5437919b5f812003b`
