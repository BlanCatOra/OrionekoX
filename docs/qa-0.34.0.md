# QA — alpha 0.34.0（技能库导出 SKILL.md zip）

- 版本：`0.34.0`，versionCode **3409**（`0.33.0` 为 3309）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 07:40–07:55（CST）
- 目标：把技能库导出为 `skills/**/SKILL.md` zip，供 Kelivo/Chatbox/RikkaHub
  或本应用重新导入，补上技能方向的双向兼容。
- 版本规则：新增“技能库导出”这一可见能力，按 minor 从 0.33.0 → **0.34.0**，
  versionCode 3409（340×10+9）。

## 1. 新增能力

- 技能库新增 “Export skills zip” 入口，SAF 创建 `orioneko-skills.zip`。
- `OxSkillStore.exportZip` 遍历当前可见技能（应用私有目录与
  Download/Operit、Download/Operit2），写成
  `skills/<id>/SKILL.md`；同名 id 自动加 `_2` 等后缀，避免覆盖。
- 单技能上限 512 KB；只导出 SKILL.md 正文，不包含脚本/附件，不包含任何
  密钥。
- 结果 toast：“Exported N skill(s)”；无技能时提示 “No skills to export”，
  失败提示 “Skills export failed: …”。
- 修正导入侧：`skills/<id>/SKILL.md` 的 `skills/` 只是容器目录，不再进入
  技能 id（此前会生成 `skills_<id>`，与已有 `<id>` 重复）。

## 2. 设备验证 — PASS

准备：通过技能库导入 `QASkill0340.md`（150 B，frontmatter+正文），私有目录
出现 `files/oxskills/QASkill0340/SKILL.md`。

- 导出：SAF 默认 `orioneko-skills.zip`，保存成功，文件 2,353 B。
- 导出包：7 个条目，全部为 `skills/*/SKILL.md`；包含
  `skills/QASkill0340/SKILL.md`（150 B，内容一致）；无 `ox1:`/`sk-` 等密钥。
- 回环：从导出包裁剪出只含 QASkill0340 的 272 B 子集，删除私有技能目录后
  重新导入；首次复测发现旧逻辑生成 `files/oxskills/skills_QASkill0340`
  （应在 `<id>` 下）。修正 `importFromUri` 去掉 `skills/` 容器前缀并重建
  0.34.0 后，再次导入得到准确的
  `files/oxskills/QASkill0340/SKILL.md`，内容 150 B 一致。
- 列表刷新后显示 `QASkill0340 / Round-trip skill for OrionekoX export QA`；
  无重复 `skills_QASkill0340` 目录。
- 清理：删除测试技能目录、QASkill0340.md、导出 zip 与子集；重启后技能
  目录无 QASkill 残留；无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.34.0-skills-export-row.jpg`、
  `docs/screenshots/qa-0.34.0-skill-roundtrip.jpg`。

## 3. 未覆盖 / 已知边界

- 导出全部可见技能，无单选/多选；Download 中的只读技能也会被导出。
- 只导出 SKILL.md 正文；脚本、附件、frontmatter 之外的资源不导出。
- 同名 id 会用 `_2` 后缀，不覆盖原技能；回环可能产生 `_2` 副本。
- 外部 Kelivo/Chatbox/RikkaHub 尚未实际导入该 zip 验证；真实技能包未复测。

## 4. 产物

- `dist/OrionekoX-0.34.0-alpha-arm64-x64.apk`（versionCode 3409），sha256
  `79be212983be381e14510b712d9aaacb20728a45535929d4d2a8a9a86b476545`
- `dist/OrionekoX-0.34.0-debug-arm64-x64.apk`，sha256
  `092601ccd82860d54f395e7cb4820056131f90234e60b3baa8e4b7800e2c415b`
