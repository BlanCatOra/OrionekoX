# QA — alpha 0.25.0（RikkaHub 聊天图片附件导入）

- 版本：`0.25.0`，versionCode **2509**（`0.24.1` 为 2419）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 02:50–03:05（CST）
- 目标：在 0.24.0 文本历史导入基础上，读取 RikkaHub 备份的 `upload/` 图片，
  匹配消息里的 image part，落盘为本地图片并写入 `image_path`；图片作为
  历史消息的视觉上下文保留。
- 版本规则：新增聊天图片资产导入能力，按 minor 从 0.24.1 → **0.25.0**，
  versionCode 2509（250×10+9）。

## 1. 新增能力

- 备份 zip 读取 `upload/<file>`（最多 40 个、单文件 4 MB、总计 40 MB），
  以文件名为 key 暂存。
- 历史导入遇到 `parts[].type=image` 时，取 URL 的文件名与 upload 条目匹配；
  命中后写入 `files/rikka_upload_<name>`，并在 `OxChatEntry.imagePath` 记录路径。
- 只有图片、没有文字的消息内容记为 `[图片]`；有文字时保留文字。
- 现有 `OxAgentsBridge` 在组装历史时会把 user 条目的 `imagePath` 作为
  inline base64 图片发送给模型，因此导入图片可参与后续视觉对话。
- 未匹配的图片 URL（远程/data URL/缺失文件）跳过；仍只导入 user/assistant
  文本分支。

## 2. 设备验证 — PASS（schema 样例）

样例：1 个会话、3 个消息节点（user 文本、assistant 文本、user 图片），
`upload/history-test.png` 为 70 B 的 1×1 PNG。

- 预览：`历史：会话 1 个 · 消息节点 3 条`，并提示“备份中的聊天图片附件会随
  文本历史一起导入（最多 40 个）”。
- 导入：`新建助手 2，新增 MCP 1，导入技能 1`，note `已导入历史消息 3 条`。
- `chat.json` 第 3 条：
  `{role:user, content:"[图片]", ts:..., image_path:".../files/rikka_upload_history-test.png"}`；
  本地文件存在且为 70 B。
- 重复导入：`新建 0，更新 2，新增 MCP 0，跳过 MCP 1，导入技能 1`，
  `chat.json` 仍为 3 条，图片条目未重复。
- 截图：`docs/screenshots/qa-0.25.0-image-history-result.jpg`。
- 清理：删除两个测试助手、RikkaTestMCP、TestSkill、`rikka_upload_*`、
  Downloads 样例与缓存 DB；无 Key 泄漏、无 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 只匹配 `upload/` 下按文件名对应的图片；远程 URL、data URL、缺失文件跳过。
- 每条消息最多保留第一张图片；其他文件类型（PDF、音频、视频等）不导入。
- 图片不会在聊天中作为原生照片气泡展示；历史内容用 `[图片]` 占位，主要在
  后续视觉上下文里使用。
- 只导入当前分支；其他分支、工具/推理 part 仍跳过；真实 RikkaHub 备份未复测。

## 4. 产物

- `dist/OrionekoX-0.25.0-alpha-arm64-x64.apk`（versionCode 2509），sha256
  `31eba8aed98f9e39226d72d147b2aa0ee6cbc27576f0b13f3819d9db613eff28`
- `dist/OrionekoX-0.25.0-debug-arm64-x64.apk`，sha256
  `95b6640e3a038249fd8c2edbf252060928877f89bf261f4fa66c547491bc9c3b`
