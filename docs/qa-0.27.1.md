# QA — alpha 0.27.1（Kelivo 备份技能导入）

- 版本：`0.27.1`，versionCode **2719**（`0.27.0` 为 2709）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 04:28–04:40（CST）
- 目标：补完 Kelivo/Chatbox 备份导入：读取 zip 内 `skills/**/SKILL.md`，写入
  私有技能库；同名技能更新，预览/结果给出数量。
- 版本规则：仅补既有 Kelivo 导入的技能包，按 patch 从 0.27.0 → **0.27.1**，
  versionCode 2719（271×10+9）。

## 1. 新增能力

- zip 内收集 `skills/…/SKILL.md`（最多 50 个、单文件 512 KB），技能 id 取
  `skills/` 后相对目录（嵌套用 `_` 连接），经 `OxSkillStore.skillId` sanitize。
- 预览新增“将导入 N 个技能；同名技能会更新”，并统计已存在技能数。
- 结果新增“导入技能 N 个”；技能写入 `files/oxskills/<id>/SKILL.md`，同名更新，
  不删除本地其他技能。
- 修正预览文案：由“不含技能与 MCP”改为“不含对话、Provider 密钥与 MCP”，
  避免与新行为矛盾；修正后重建并复验。

## 2. 设备验证 — PASS

样例 zip：
`settings.json` 含 1 个 Kelivo Skill Assistant；
`skills/KelivoSkill/SKILL.md`（96 B，含 name/description frontmatter）。

- 预览：`助手 1 个`，note“仅导入助手、人设与技能；不含对话、Provider 密钥与
  MCP”“将导入 1 个技能；同名技能会更新”。
- 导入：`新建助手 1，更新 0`，note“导入技能 1 个”；
  `files/oxskills/KelivoSkill/SKILL.md` 存在，frontmatter 完整。
- 重复导入：`新建助手 0，更新 1`，note“导入技能 1 个”，技能目录仍只有 1 个。
- 截图：`docs/screenshots/qa-0.27.1-kelivo-skills-preview.jpg`。
- 清理：删除测试助手、KelivoSkill 目录与 Downloads 样例；无 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 只从 zip 的 `skills/` 读取 SKILL.md；直接选择 JSON 时没有技能。
- 技能的脚本/附件/额外资源不导入，只写入 SKILL.md 正文。
- 技能数量/大小上限 50 个/512 KB；超限条目跳过。
- 仍未导入 Kelivo 对话、Provider 密钥、MCP、工作区、头像/背景。
- 样例按 Chatbox 文件结构构造，真实 Kelivo 备份待复测。

## 4. 产物

- `dist/OrionekoX-0.27.1-alpha-arm64-x64.apk`（versionCode 2719），sha256
  `39cb7424a9e847e00fb819096b17821396870ad1b4223060cb35d7d4a160c213`
- `dist/OrionekoX-0.27.1-debug-arm64-x64.apk`，sha256
  `26769a56fb6aadd5b1ad3d276eebb1e44c3f2d4eb198b2cac8ebdc16eacb6537`
