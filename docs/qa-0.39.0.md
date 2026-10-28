# QA — alpha 0.39.0（Chatbox 导出包含 Agent 背景）

- 版本：`0.39.0`，versionCode **3909**（`0.38.0` 为 3809）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 10:04–10:14（CST）
- 目标：补 0.38.0 的“导出备份暂不含背景”边界；把 Agent 作用域的
  backgroundImage 写成 Chatbox v2 `background` 资源，并做导出→删除→回环导入。
- 版本规则：新增“导出包含 Agent 背景”这一可见能力，按 minor 从 0.38.0 →
  **0.39.0**，versionCode 3909（390×10+9）。

## 1. 新增能力

- `OxChatTheme.backgroundForAgent(tgId)` 暴露当前 Agent 主题的背景图路径。
- `OxKelivoExporter` 在导出每个 Agent 时：
  - 有聊天的 Agent：把背景作为 `kind=background`、scope=session 资源写入，
    并在 session JSON 加 `background: {type:storage-key, storageKey}`；
  - 无聊天的 Agent：写 global 背景资源，并在 copilot JSON 加 `background`。
- 背景文件走原有 data URL 资源与 sha256/size 校验；不导出 headers/Key。
- 回环导入沿用 0.38.0 的背景导入逻辑，已有自定义背景仍保留。

## 2. 设备验证 — PASS

准备：导入 BG38 背景样例，得到 `Background Demo`
（`kelivo_chat_s1_current`，2 条消息，背景 300×500 PNG）。

- 单 Agent 导出：`Background Demo-chatbox-backup.zip` 15,580 B；
  manifest 1 个 session、1 个 `background` 资源（session scope）、无 copilot；
  session JSON 带 `background.storageKey`；资源 size/sha256 校验通过；
  2 条消息保留。
- 删除原 Agent 与 Agent 主题文件后，导入刚导出的 zip：
  - 新 Agent `kelivo_chat_kelivo_chat_s1_current_current` 含
    `background.png` 14,884 B；
  - `agent_555000080.json` 为 `backgroundMode=image`，路径指向该文件；
  - 原生聊天显示 BG38 壁纸与“背景测试/背景已应用。”两条消息。
- 清理：删除回环 Agent、主题文件、样例与导出 zip；重启后
  dialogs/messages 对 555000079/555000080 均为 0 行，无 Agent/主题残留；
  无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.39.0-import-result.jpg`、
  `qa-0.39.0-roundtrip-result.jpg`、`qa-0.39.0-chat.jpg`。

## 3. 未覆盖 / 已知边界

- 只导出 Agent 作用域的背景图片；全局背景、按会话背景、视频背景不导出。
- 背景走 2 MB/资源总量上限；超限会跳过并记录 note。
- 导出结果只按“文件数”汇总，未单独显示背景数量。
- 外部真实 Chatbox 客户端尚未导入该包验证。

## 4. 产物

- `dist/OrionekoX-0.39.0-alpha-arm64-x64.apk`（versionCode 3909），sha256
  `e06f40bbe691c216bc5d2294c3951fd04730feedcd49e76f34b3d14a90edd54f`
- `dist/OrionekoX-0.39.0-debug-arm64-x64.apk`，sha256
  `56f4494076be8cd487a1bc2927db4901963ce4487c62824c4f10b4668f44f3f7`
