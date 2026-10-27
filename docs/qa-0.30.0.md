# QA — alpha 0.30.0（Kelivo / Chatbox 聊天图片导入）

- 版本：`0.30.0`，versionCode **3009**（`0.29.0` 为 2909）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 06:20–06:38（CST）
- 目标：把 Chatbox v2 备份中的聊天图片资源导入本地 Agent，并在原生聊天中
  显示为照片气泡（含图片说明与纯图片消息）。
- 版本规则：新增“备份聊天图片导入 + 原生照片气泡”这一可见能力，按 minor
  从 0.29.0 → **0.30.0**，versionCode 3009（300×10+9）。

## 1. 新增能力

- 扩展 Chatbox v2 `resources[]`：除头像外，读取 `kind=image`（以及
  `parsed-attachment` / `raw-attachment` 且 MIME 为 `image/*`）的资源，
  按 `originalStorageKeys`/资源 id 建立图片映射。
- 消息 `contentParts[type=image]` 优先按 `storageKey`，其次按 `url` 匹配
  备份资源；每条消息取首张可导入图片，其余图片保留 `[图片]` 占位。
- 导入时写入 `files/oxagents/<agentId>/media/img_<n>.<ext>`，并写入
  `OxChatEntry.imagePath`；纯图片消息的正文为空，有文字时作为照片说明。
- 原生回放 `replayImportedHistory` 识别 `imagePath`，用
  `SendMessagesHelper.generatePhotoSizes` 生成原生照片气泡；失败时退回文字。
- 每个会话最多导入 40 张图片；单张仍受 2 MB / 资源总量 24 MB 限制，不做
  网络下载，只接受 PNG/JPEG/GIF/WebP。
- 预览/结果增加图片数量：预览“将导入 N 张聊天图片；原生聊天中显示为照片
  气泡”，结果“导入聊天图片 N 张”。

## 2. 设备验证 — PASS

样例 zip：1 个 session（`Image Demo`）4 条消息：
1. user 文本“看看这张” + 图片 `image-key-1`（原始 PNG）；
2. assistant 文本“收到图片”；
3. user 纯图片 `image-key-2`（data URL PNG，无文字）；
4. assistant 文本“两张都看到了”。

结果：

- 预览：`Conversations: 1 (4 messages)`，note“将导入 2 张聊天图片；原生聊天
  中显示为照片气泡”。
- 导入：`Imported 1 conversations (4 messages)`、note“导入聊天图片 2 张”。
- 落盘：`media/img_0.png` 2783 B、`media/img_2.png` 3272 B；chat.json 第 1 条
  `content="看看这张"` + `image_path=.../img_0.png`，第 3 条 `content=""` +
  `image_path=.../img_2.png`。
- 原生界面：日期“10月27日”；PIC1 照片气泡带“看看这张”说明、双勾；随后
  “收到图片”；PIC2 为无说明纯图片气泡；最后“两张都看到了”。图片内容与
  样例颜色/文字一致。
- 重复导入：仍 1 段/4 条，note“已存在的 1 段对话历史已替换”“导入聊天图片
  2 张”；两张图片 sha256 未变化（`7185a87e…`、`3748cac9…`），
  messages_v2 仍为 4 条，未翻倍。
- 截图：`docs/screenshots/qa-0.30.0-image-preview.jpg`、
  `qa-0.30.0-image-result.jpg`、`qa-0.30.0-image-chat.jpg`、
  `qa-0.30.0-image-repeat.jpg`。
- 清理：删除 1 个测试 Agent 目录与 Downloads 样例；重启后 dialogs/
  messages_v2 中 555000062 均为 0 行，Agent 与 media 目录不存在；无本包
  FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 每条消息只导入首张图片；多图消息其余部分保留 `[图片]` 占位，不生成相册。
- 只处理备份内图片资源；`http(s)` 图片不下载，远程序列化图片仍为占位。
- 不导入文件、音频、视频附件；不含图片说明的富文本实体/格式。
- 原生照片气泡依赖 Telegram 本地照片缓存；首次打开可能短暂显示占位。
- 真实 Chatbox / Kelivo 导出包尚未复测；样例按 v2 schema 构造。

## 4. 产物

- `dist/OrionekoX-0.30.0-alpha-arm64-x64.apk`（versionCode 3009），sha256
  `199d7c5e2361033774b97b97cb571d4128c77888a3663ca2b3acc119a3c75599`
- `dist/OrionekoX-0.30.0-debug-arm64-x64.apk`，sha256
  `e1af87f999cd7df6e7cc0d36edf7d19a1b7288792563c749062b767459a27307`
