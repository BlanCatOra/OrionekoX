# QA — alpha 0.40.0（导出按会话背景）

- 版本：`0.40.0`，versionCode **4009**（`0.39.0` 为 3909）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 10:20–10:36（CST）
- 目标：当会话有自己的背景覆盖时，Chatbox 导出应保存用户实际看到的
  会话背景，而不是只取 Agent 级背景。
- 版本规则：新增“导出按会话背景”这一可见能力，按 minor 从 0.39.0 →
  **0.40.0**，versionCode 4009（400×10+9）。

## 1. 新增能力

- `OxChatTheme.backgroundForDialog(dialogId)` 读取按会话主题文件的背景路径。
- 导出有聊天的 Agent 时，背景优先取 `backgroundForDialog(tgId)`，为空再回退
  `backgroundForAgent(tgId)`；导出的 session `background` 因此反映实际会话
  外观。
- 无聊天的 copilot 仍只取 Agent 背景；资源格式/上限/安全检查不变。
- 导入时仍按 0.38.0 逻辑映射为 Agent 作用域背景（会话级 specificity 会丢失，
  但图片与外观保留）。

## 2. 设备验证 — PASS

准备：
- 导入 BG38 样例，Agent 主题背景为 BG38；
- 在 `files/ox_chat_dialogs/dialog_555000081.json` 写入会话覆盖，背景指向
  另存的 BG40（300×500，13,747 B）；
- 原生聊天显示 BG40。

导出：

- 单 Agent 导出 14,441 B；manifest 只有 1 个 `background` 资源
  （session scope）和 1 个 session。
- 把导出资源 data URL 解码后 sha256 为
  `6f186b4e…`，与 BG40 完全一致（BG38 为 `db82cdea…`），证明导出优先取
  会话背景。

回环：

- 删除 Agent、Agent 主题和会话覆盖文件后导入该 zip；
  `kelivo_chat_kelivo_chat_s1_current_current/background.png` sha256 为
  `6f186b4e…`（BG40）；
- 原生聊天显示 BG40 壁纸与两条历史消息；会话级 specificity 已按设计映射到
  Agent 作用域。
- 清理：删除回环 Agent、主题/会话文件与样例/导出 zip；重启后
  dialogs/messages 对 555000081/555000082 均为 0 行；无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.40.0-dialog-background.jpg`、
  `qa-0.40.0-roundtrip-result.jpg`、`qa-0.40.0-roundtrip-chat.jpg`。

## 3. 未覆盖 / 已知边界

- 回环后会话背景变成 Agent 背景，不保留“仅此会话”的作用域；再次导入会
  按 Agent 级应用。
- 仅图片背景；全局背景、视频背景、非图片主题资产不导出。
- 外部真实 Chatbox 客户端尚未导入该包验证。

## 4. 产物

- `dist/OrionekoX-0.40.0-alpha-arm64-x64.apk`（versionCode 4009），sha256
  `e4805cf78f5448858f07eb61959f375b01a5a5a4bf517f84d6842e08a33570ff`
- `dist/OrionekoX-0.40.0-debug-arm64-x64.apk`，sha256
  `4334c6285c4f35370d5ad874258b0806d71229aee194a5383c05cffafcfdb331`
