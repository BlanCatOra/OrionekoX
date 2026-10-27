# QA — alpha 0.29.0（Kelivo / Chatbox 备份头像导入）

- 版本：`0.29.0`，versionCode **2909**（`0.28.0` 为 2809）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 05:55–06:05（CST）
- 目标：读取 Chatbox v2 备份 `resources` 中的头像资源，给导入的助手和会话
  Agent 设置真实头像，并在原生聊天列表/会话页显示。
- 版本规则：新增“备份头像导入”这一可见能力，按 minor 从 0.28.0 →
  **0.29.0**，versionCode 2909（290×10+9）。

## 1. 新增能力

- 读取 `manifest.json` 的 `resources[]`，只处理 `kind=avatar` 与
  `kind=copilot-image` 的图片资源；按 `originalStorageKeys` 和资源 `id`
  建立映射。
- 支持两种载体：zip 内原始图片字节（`encoding=utf8`）和
  `data-url-base64` 文本（解码 `data:image/...;base64,`）。
- 助手头像：读取 `avatar` 的 `storageKey` / `url`，或 `picUrl` / 字符串
  avatar；只有命中备份资源时才写入，不做网络下载。
- 会话头像：读取 manifest session 描述符的 `meta.assistantAvatarKey` /
  `meta.picUrl`，其次 session 的 `picUrl` / avatar 字段。
- 写入 `files/oxagents/<agentId>/avatar.<png|jpg|webp|gif>`；重复导入时若
  Agent 已有头像则保留，不覆盖用户自定义头像，也不重复写文件。
- 资源上限：64 个候选文件、单文件 2 MB、总计 24 MB；只接受 PNG/JPEG/GIF/
  WebP 魔数，非法图片跳过。
- 预览/结果增加头像资源计数与“导入头像 N 个”提示；仅当首次写入头像时计数。

## 2. 设备验证 — PASS

样例 zip：1 个 copilot（`Avatar Assistant`，头像 key
`avatar-key-assistant`）+ 1 个 session（`Avatar Demo`，meta
`assistantAvatarKey=avatar-key-session`）；2 个 PNG 资源，一个原始字节、
一个 data URL。

- 预览：`助手 1 个`、`Conversations: 1 (2 messages)`、
  note“备份含 2 个头像资源；匹配到的助手/对话会更新头像”。
- 导入：`新建助手 1，更新 0`、`Imported 1 conversations (2 messages)`、
  note“导入头像 2 个”。
- 落盘：`kelivo_c1/avatar.png` 1310 B、`kelivo_chat_s1_current/avatar.png`
  1623 B；agent.json 的 `avatar_path` 指向各自文件。
- 原生界面：打开 `Avatar Demo` 会话，标题左侧显示绿色 `S1` 头像；打开
  `Avatar Assistant` 会话，显示蓝色 `AI` 头像；历史气泡与欢迎语正常。
- 重复导入：`新建助手 0，更新 1`、`Imported 1 conversations`、note
  “已存在的 1 段对话历史已替换”；两枚头像 sha256 均未变化
  （`ff645623…`、`c074120b…`），没有新增 Agent 目录。
- 截图：`docs/screenshots/qa-0.29.0-avatar-preview.jpg`、
  `qa-0.29.0-avatar-result.jpg`、`qa-0.29.0-avatar-conversation.jpg`、
  `qa-0.29.0-avatar-assistant.jpg`、`qa-0.29.0-avatar-repeat.jpg`。
- 清理：删除 2 个测试 Agent 目录与 Downloads 样例；重启后
  dialogs/messages_v2 中 555000060/555000061 均为 0 行，Agent 目录不存在；
  无本包 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 只导入 `avatar` / `copilot-image`；聊天图片、文件、背景、字体等资源仍
  不导入。
- 仅支持 zip 内资源或 data URL；`http(s)` 头像不下载，避免导入时联网。
- 只识别 PNG/JPEG/GIF/WebP；HEIC/AVIF 等会跳过。
- 资源上限会截断超大备份；同名资源 key 后写覆盖先写。
- 真实 Chatbox / Kelivo 导出包尚未复测；样例按 v2 schema 构造。

## 4. 产物

- `dist/OrionekoX-0.29.0-alpha-arm64-x64.apk`（versionCode 2909），sha256
  `660e383315c24b23b6e3753047e65bad885140f3072e6d56981cc37b354bb796`
- `dist/OrionekoX-0.29.0-debug-arm64-x64.apk`，sha256
  `58214581a4f991635a343154d67901026cb5cb67feb628d5a102020f708599dd`
