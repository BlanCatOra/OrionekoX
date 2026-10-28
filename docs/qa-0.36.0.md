# QA — alpha 0.36.0（多选 Agent 导出 Chatbox 备份）

- 版本：`0.36.0`，versionCode **3609**（`0.35.0` 为 3509）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 08:22–08:38（CST）
- 目标：把 0.33.0 的单 Agent 导出扩展为可勾选多个 Agent，一次导出到
  Chatbox v2 zip；补上“多选”这一剩余边界。
- 版本规则：新增“多选 Agent 导出”这一可见能力，按 minor 从 0.35.0 →
  **0.36.0**，versionCode 3609（360×10+9）。

## 1. 新增能力

- Ox 设置新增 “Export selected agents”：原生 AlertDialog + 可滚动复选框
  列表，列出所有非内置 Agent，默认不勾选。
- 勾选后点 Export，经 SAF 创建 `orioneko-selected-backup.zip`；未勾选时
  toast “Select at least one agent”，不写文件。
- 导出仍走 `OxKelivoExporter.exportTo(ctx, uri, onlyIds)`：只有勾选的
  Agent 写入 session/copilot 与资源，其他 Agent 不会出现。
- 取消 SAF 会清空待导出选择；全量导出入口保持不变。

## 2. 设备验证 — PASS

准备两个测试 Agent：
- `Multi B`：2 条消息（“B 消息 / B 回复”），无头像/资源；
- `Single Export`：4 条消息，含 avatar、图片与 `notes.txt` 附件。

操作：多选弹窗中勾选 `Multi B` 与 `Single Export`（列表较长，分两次滚动
勾选），点 Export，保存 `orioneko-selected-backup.zip`。

- 文件 6,697 B；manifest `sessionCount=2`、`resourceCount=3`，
  sessions 仅 `Multi B` 与 `Single Export`，copilots 0，资源仅 avatar/
  image/raw-attachment；没有其他 Agent 的 session/资源。
- 回环导入：生成
  `kelivo_chat_kelivo_chat_s2_current_current`（B 消息/B 回复）与
  `kelivo_chat_kelivo_chat_s1_current_current`（图片、notes.txt、4 条消息）；
  内容与源一致。
- 清理：删除 4 个测试 Agent 与全部样例/导出 zip；重启后
  dialogs/messages 对 555000074–555000077 均为 0 行；无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.36.0-select-multi.jpg`（Multi B 勾选）、
  `qa-0.36.0-select-single.jpg`（Single Export 勾选）。

## 3. 未覆盖 / 已知边界

- 列表无搜索/全选按钮；Agent 多时需要滚动逐个勾选。
- 只导出非内置 Agent；默认不勾选，空选择会提示。
- 仍不导出 API Key、provider URL、工作区、记忆、技能、MCP、主题。
- 回环后 Agent id 仍会多一层 `kelivo_` 前缀；外部 Chatbox 客户端未实测。

## 4. 产物

- `dist/OrionekoX-0.36.0-alpha-arm64-x64.apk`（versionCode 3609），sha256
  `3f4b52ac98666a09232a43df6682698b0c01d9206a3e13ab60b38c7ff42f4f01`
- `dist/OrionekoX-0.36.0-debug-arm64-x64.apk`，sha256
  `2a6607834fa32d0783c450e492c651b4b5f18df2c797f746fa73a80134487fe8`
