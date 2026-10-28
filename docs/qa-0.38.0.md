# QA — alpha 0.38.0（Chatbox / Kelivo 聊天背景导入）

- 版本：`0.38.0`，versionCode **3809**（`0.37.1` 为 3719）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 09:40–09:55（CST）
- 目标：读取 Kelivo/Chatbox 备份的 `kind=background` 资源与 session
  `background` 引用，把背景图写入 Agent 目录，并在 sync 后套用为按 Agent
  主题背景；已有自定义背景时保留。
- 版本规则：新增可见的备份聊天背景导入能力，按 minor 从 0.37.1 → 0.38.0，
  versionCode 3809（380×10+9）。

## 1. 新增能力

- Chatbox v2 `resources[]` 新增 `kind=background` 解析（data URL/原始字节，
  只接受 PNG/JPEG/GIF/WebP，单文件 2 MB）；`background` /
  `backgroundImage` 支持 storageKey/url/字符串引用。
- session JSON 的 `background` 字段会随对话一起解析；助手 copilot 的
  `background` 同样支持。
- 导入时把背景写入 `files/oxagents/<id>/background.<ext>`；syncAgents 后
  调用 `OxChatTheme.applyImportedBackground(tgId, path)` 写入按 Agent 主题
  （mode=custom、backgroundMode=image），复用现有背景渲染。
- 已有 Agent 主题背景与备份路径不同时跳过，并在结果 note 说明“其余保留
  已有自定义背景”；同一路径可重复应用。
- 预览/结果增加背景数量提示；导出 Chatbox 备份暂不包含该背景（见边界）。

## 2. 设备验证 — PASS

样例：1 个 session `Background Demo`（2 条消息），`background` 指向
`key-bg`，资源为 300×500 PNG（BG38）+ data URL。

- 预览：`对话 1 段（2 条消息）`、`项目：Background Demo`，note
  “备份含 1 个聊天背景；将套用到对应智能体。”
- 导入：`导入对话 1 段（2 条消息）`、note“已套用 1 个聊天背景”；
  `files/oxagents/kelivo_chat_s1_current/background.png` 14,884 B；
  `files/ox_chat_agents/agent_555000078.json` 的
  `backgroundMode=image`、`backgroundImage` 指向该文件。
- 原生聊天：打开 `Background Demo`，BG38 渐变图作为聊天壁纸铺开，
  “背景测试/背景已应用。”气泡与输入栏正常。
- 自定义保留：把 Agent 主题背景改为
  `.../files/custom-bg.png` 后重复导入同一备份，结果 note
  “已套用 0 个聊天背景；其余保留已有自定义背景”，主题文件仍是
  `custom-bg.png`，未被备份路径覆盖。
- 清理：删除测试 Agent、`agent_555000078.json` 与样例；重启后
  dialogs/messages 对 555000078 均为 0 行，Agent/主题文件不存在；
  无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.38.0-preview.jpg`、
  `qa-0.38.0-result.jpg`、`qa-0.38.0-chat.jpg`、
  `qa-0.38.0-preserve-custom.jpg`。

## 3. 未覆盖 / 已知边界

- 只支持图片背景；视频背景、纯色/渐变字段仍由主题 JSON 导入处理。
- 只按 Agent 套用，不创建按会话背景；导出 Chatbox 备份暂不包含该背景图。
- 外部真实 Kelivo/Chatbox 导出的 background 资源未复测；样例按 v2 schema
  构造。
- 背景文件保留在 Agent 目录，删除 Agent 时随目录一起清理。

## 4. 产物

- `dist/OrionekoX-0.38.0-alpha-arm64-x64.apk`（versionCode 3809），sha256
  `a99da68291cdaf59779f9e0b69a61bdd8a857dc48bab00d0c6827da4f01034e0`
- `dist/OrionekoX-0.38.0-debug-arm64-x64.apk`，sha256
  `c6616f5f1be8338b463c559203a4f0f57390bdbef6726e8c938bd0fc52f8c969`
