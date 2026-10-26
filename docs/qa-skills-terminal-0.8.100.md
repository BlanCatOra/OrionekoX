# QA — skills library and terminal settings (0.8.100, 2026-10-26 13:05–13:10)

Build: `io.github.blancatora.orionekox` versionName 0.8.100 (versionCode 1159),
emulator-5554 with `-gpu off`. No settings were changed and no skill was imported or
removed.

## Skills library

- 技能库 header: “只读浏览 Operit/Operit2 导出的 SKILL.md 包。智能体可调用
  list_skills 和 read_skill。”
- One entry: 示例技能 · 测试 Operit SKILL.md 扫描.
- Opening it shows the raw SKILL.md body with YAML frontmatter
  (`name: 示例技能`, `description: …`, `# 示例技能`) and a 确定 button. Read-only,
  as described.

## Terminal settings

- Page title 终端设置; 配色方案 lists Ox · 炭黑底绿字 / 经典 · 黑底白字 /
  琥珀 · 黑底琥珀字 / 浅色 · 纸白底深字 / Ubuntu · 茄紫底.
- 字号 lists 12 sp / 14 sp / 16 sp / 18 sp.
- Footer: “Shell 从共享存储启动，ls、cp 等命令可以直接看到真实文件。”
- Values were inspected only; the current scheme and font size were not changed.

## State

- No OrionekoX FATAL/ANR; the device stayed online under `-gpu off`.
