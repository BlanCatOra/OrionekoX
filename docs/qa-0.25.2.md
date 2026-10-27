# QA — alpha 0.25.2（命名预设重命名）

- 版本：`0.25.2`，versionCode **2529**（`0.25.1` 为 2519）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 03:18–03:32（CST）
- 目标：补齐命名预设管理中的“重命名”，长按预设可选择重命名或删除；保留
  保存/应用/删除/导出/导入的既有行为。
- 版本规则：仅补预设管理的小功能，按 patch 从 0.25.1 → **0.25.2**，
  versionCode 2529（252×10+9）。

## 1. 新增能力

- 长按预设改为原生菜单：`重命名` / `删除`；删除仍走二次确认。
- 重命名对话框预填当前名称、单选行；保存后：
  - 按既有规则 sanitize 文件名词（保留中英文、数字、`.`/`_`/`-`）；
  - 新名称 sanitize 后已存在时拒绝，提示“同名预设已存在”，原文件不动；
  - 成功时把旧 JSON 内容写入新文件并更新 `name` 字段，删除旧文件；
  - toast “已重命名预设：新名称”，列表即时刷新。
- `OxChatTheme.renamePreset` 与 `safePresetName` 供 UI 复用；预设包导出/导入
  继续使用文件名作为预设名。

## 2. 设备验证 — PASS

- 在全局外观页保存临时预设 `RenameQA`，长按 → 重命名 → 改为 `RenameQA2`：
  - `files/ox_chat_presets` 只剩 `RenameQA2.json`（576 B），旧文件删除；
  - 列表显示 `RenameQA2`，可继续长按/应用。
- 再次重命名为已存在的 `BundleQA`：被拒绝，`RenameQA2.json` 保持原样。
- 取消重命名对话框不修改文件；截图：
  `docs/screenshots/qa-0.25.2-rename-dialog.jpg`、
  `qa-0.25.2-renamed.jpg`。
- 测试后删除 `RenameQA2`，预设目录恢复为 BundleQA / DarkSea / R2C-Image。
- 无 FATAL/ANR；未修改用户消息或联系人。

## 3. 未覆盖 / 已知边界

- 仅重命名单个预设，不支持批量重命名/复制。
- 名称 sanitize 后可能变化（空格/斜杠等变为 `_`）；极长名称可能截断后碰撞，
  已有碰撞会被拒绝而不是自动加后缀。
- 重命名不会同步更新 `.oxthemes` 包内旧名称或已导出的文件。
- 预设包导入仍按 sanitize 后的文件名去重。

## 4. 产物

- `dist/OrionekoX-0.25.2-alpha-arm64-x64.apk`（versionCode 2529），sha256
  `454b52f3f04481ee417601c2223268c87cb0ea9b176477cb12a84a9a616dbcec`
- `dist/OrionekoX-0.25.2-debug-arm64-x64.apk`，sha256
  `c0027b7821f9cf1a3e6499d6a3171fc4458225a31146bfd0504fabebffb6adff`
