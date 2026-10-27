# QA — alpha 0.33.0（按 Agent 导出 Chatbox 备份）

- 版本：`0.33.0`，versionCode **3309**（`0.32.0` 为 3209）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 07:24–07:35（CST）
- 目标：补 0.32.0 “只能全量导出”的边界，在 Agent 长按菜单中支持只导出
  单个 Agent 的 Chatbox v2 备份，并做导出→回环导入验证。
- 版本规则：新增“按单个 Agent 导出”这一可见能力，按 minor 从 0.32.0 →
  **0.33.0**，versionCode 3309（330×10+9）。

## 1. 新增能力

- `OxKelivoExporter.exportTo(context, uri, onlyIds)`：支持只导出指定 Agent
  集合；原 `exportTo(context, uri)` 仍导出全部非内置 Agent。
- Agent 长按菜单（非内置）新增 “Export Chatbox backup”，位于
  “导出 .oxagent” 与 “删除”之间。
- 点击后原生确认框说明“导出该 Agent 的聊天、头像、图片和附件；不含 API Key
  与 provider URL”，确认后 SAF 创建 `<Agent 名>-chatbox-backup.zip`。
- 只导出该 Agent：有聊天写 session，无聊天写 copilot；不会夹带其他 Agent。
- 结果 toast：“Exported <名称>（N 条消息，M 个文件）”；失败显示
  “Export failed: …”。

## 2. 设备验证 — PASS

准备：导入一个只含 session 的 seed，得到 `Single Export`
（`kelivo_chat_s1_current`，头像 SE，0.5/0.7/80，含 1 张图片、
1 个 `notes.txt` 附件、4 条消息）。

- 长按 `Single Export`：菜单出现 “Export Chatbox backup”；确认框内容正确。
- SAF 默认文件名 `Single Export-chatbox-backup.zip`，保存成功，文件 6,190 B。
- 导出包 schema：`format=chatbox-backup`、`formatVersion=2`；
  1 个 session、0 个 copilot、3 个资源（avatar/image/raw-attachment）、
  stats 1/3/0/0；3 个资源 size/sha256 全部匹配；只含 `Single Export`，
  不含其他 Agent；无 `ox1:`、`sk-`、`api_key`、`test-key`。
- 回环导入：预览 1 段对话/4 条；导入生成
  `kelivo_chat_kelivo_chat_s1_current_current`，保留 prompt、0.5/0.7/80、
  头像、`img_0.png`、`2_notes.txt` 与 4 条消息。
- 原生界面：标题左侧 SE 头像；SE1 图片带“单 Agent 导出”说明；
  `notes.txt` 25 B TXT 文件气泡；“收到图片。”“收到附件。”正确。
- 清理：删除 seed 与回环 Agent、seed/导出 zip；重启后
  dialogs/messages 对 555000068/555000069 均为 0 行；无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.33.0-agent-menu.jpg`、
  `qa-0.33.0-export-confirm.jpg`、`qa-0.33.0-roundtrip-result.jpg`、
  `qa-0.33.0-roundtrip-chat.jpg`。

## 3. 未覆盖 / 已知边界

- 只支持单个非内置 Agent；无多选/批量导出。
- 有聊天的 Agent 仍只写 session（不写 copilot），回环 id 会多一层
  `kelivo_` 前缀。
- 不导出 API Key、provider URL、工作区、记忆、技能、MCP、主题。
- 外部真实 Chatbox 客户端尚未导入验证；真实 Kelivo 导出包也未复测。

## 4. 产物

- `dist/OrionekoX-0.33.0-alpha-arm64-x64.apk`（versionCode 3309），sha256
  `b291c20d78639e6cd17882ad09e8b61cf4449164b841cae55f15c1ddb22cb0d2`
- `dist/OrionekoX-0.33.0-debug-arm64-x64.apk`，sha256
  `bb8d787220eefd399d4f9b5645e789874d16013952b2390d3301c7af44fa898b`
