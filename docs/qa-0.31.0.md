# QA — alpha 0.31.0（Kelivo / Chatbox 聊天附件导入）

- 版本：`0.31.0`，versionCode **3109**（`0.30.0` 为 3009）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 06:42–06:52（CST）
- 目标：把 Chatbox v2 备份中的非图片附件导入本地 Agent，并在原生聊天中
  显示为文件气泡（含附件说明与纯附件消息）。
- 版本规则：新增“备份聊天附件导入 + 原生文件气泡”这一可见能力，按 minor
  从 0.30.0 → **0.31.0**，versionCode 3109（310×10+9）。

## 1. 新增能力

- `OxChatEntry` 增加 `filePath` / `fileName`，写入 `chat.json` 的
  `file_path` / `file_name`，旧记录不受影响。
- Chatbox v2 `resources[]` 的 `parsed-attachment` / `raw-attachment` 在非图片
  MIME 下进入附件映射；图片 MIME 仍走 0.30.0 的图片链路。
- 消息 `contentParts[type=file]` 按 `storageKey`、`rawStorageKey`、`url`
  匹配资源；导入时复制到 `media/<index>_<safeName>`，原名保留用于显示。
- 有文字时作为文件说明，纯附件消息正文为空，其余附件保留 `[文件]` 占位；
  每条消息最多一个附件，每个会话最多 20 个。
- `replayImportedHistory` 使用 `buildLocalDocument` 生成原生文件气泡
  （文件名、大小、MIME、`localPath`），失败退回文字。
- Agent 上下文 `baseMessages` 会为附件补 `[文件] 名称`，让后续模型知道
  历史里带过文件；不执行文件内容。
- 预览/结果增加附件计数：预览“将导入 N 个聊天附件；原生聊天中显示为文件
  气泡”，结果“导入聊天附件 N 个”。

## 2. 设备验证 — PASS

样例 zip：1 个 session（`Attachment Demo`）4 条消息：
1. user 文本“看一下附件” + `notes.txt`（raw text，35 B）；
2. assistant 文本“收到文本”；
3. user 纯附件 `data.json`（data URL base64，38 B，使用 `rawStorageKey`）；
4. assistant 文本“JSON 也收到了”。

结果：

- 预览：`Conversations: 1 (4 messages)`，note“将导入 2 个聊天附件；原生
  聊天中显示为文件气泡”。
- 导入：`Imported 1 conversations (4 messages)`、note“导入聊天附件 2 个”。
- 落盘：`media/0_notes.txt` 35 B、`media/2_data.json` 38 B；chat.json 第 1 条
  带 `file_path`/`file_name=notes.txt` 与正文“看一下附件”，第 3 条
  `content=""` + `file_path`/`file_name=data.json`。
- 原生界面：日期“10月28日”；`notes.txt` 文件气泡显示“35 B TXT”与说明
  “看一下附件”；随后“收到文本”；`data.json` 纯文件气泡显示“38 B JSON”；
  最后“JSON 也收到了”。
- 重复导入：仍 1 段/4 条，note“已存在的 1 段对话历史已替换”“导入聊天附件
  2 个”；两个附件 sha256 未变化（`8cdc4702…`、`1015af92…`），messages_v2
  仍为 4 条。
- 截图：`docs/screenshots/qa-0.31.0-attach-preview.jpg`、
  `qa-0.31.0-attach-result.jpg`、`qa-0.31.0-attach-chat.jpg`、
  `qa-0.31.0-attach-repeat.jpg`。
- 清理：删除测试 Agent 目录与 Downloads 样例；重启后 dialogs/messages_v2
  中 555000063 均为 0 行，Agent 与 media 目录不存在；无本包 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 每条消息只导入首个附件；多附件消息其余部分保留 `[文件]` 占位。
- 不导入音频/视频为可播放媒体，统一按文档气泡展示。
- 只处理备份内资源；`http(s)` 附件不下载，远程序列化附件仍为占位。
- 不解析附件正文、不执行文件；仅保存和展示。
- 真实 Chatbox / Kelivo 导出包尚未复测；样例按 v2 schema 构造。

## 4. 产物

- `dist/OrionekoX-0.31.0-alpha-arm64-x64.apk`（versionCode 3109），sha256
  `b64728dd51abf7633fdae0250d3736398db418727a9c70eca3fab823d153f45d`
- `dist/OrionekoX-0.31.0-debug-arm64-x64.apk`，sha256
  `8699b26da48a5246894e7d330cb303c3cb04f4e162ebe8450f3492fcf1fa003d`
