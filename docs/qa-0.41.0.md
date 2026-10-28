# QA — alpha 0.41.0（RikkaHub 文件附件导入）

- 版本：`0.41.0`，versionCode **4109**（`0.40.0` 为 4009）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 10:44–11:00（CST）
- 目标：RikkaHub 聊天历史里的非图片 `document` 附件应匹配 `upload/` 文件，
  导入为本地 Agent 的文件消息，并在原生聊天显示文件气泡；同时保证重复导入
  幂等。
- 版本规则：新增“RikkaHub 非图片附件导入”这一可见能力，按 minor 从
  0.40.0 → **0.41.0**，versionCode 4109（410×10+9）。

## 1. 新增能力

- `OxRikkaImporter` 解析消息 part 中 `type=document`：
  - 用 `url` 在备份 `upload/` 中匹配文件，复制到应用私有目录
    `files/rikka_upload_<name>`；
  - 读取 `fileName`，缺失时回退实际文件名；
  - 写入 `OxChatEntry.filePath` / `fileName`，纯文件消息允许正文为空；
  - 每条消息最多取第一个 document（与现有单图片策略一致）。
- 导入结果新增附件计数，note 显示“导入附件 N 个”。
- 导入器记录本次实际新增消息的会话，导入完成后调用
  `OxAgentsBridge.replayImportedHistory`，把 `chat.json` 回放成原生消息。
  修复了此前 RikkaHub 历史只写入 `chat.json`、原生会话仍停留在开场白的
  问题。
- 文件沿用 RikkaHub 阅读上限：单文件 4 MB、总读取 40 MB。

## 2. 设备验证 — PASS

准备：

- 最小样例 `/sdcard/Download/rikka-files-041.zip`，1,064 B：
  `settings.json`、`rikka_hub.db`、`upload/report.txt`（37 B）。
- 数据库含 1 个会话、2 条消息：用户文字“看附件”+ document
  `report.txt`，助手回复“收到附件”。

首次导入：

- 预览显示“助手 1 个 / 历史：会话 1 个 · 消息节点 2 条”。
- 结果：“新建助手 1，更新 0……已导入历史消息 2 条，导入附件 1 个”。
- `files/oxagents/rikka_11111111-1111-4111-8111-111111111111/chat.json`
  两条记录，用户记录带 `file_path` 指向 `files/rikka_upload_report.txt`、
  `file_name=report.txt`；复制出的附件为 37 B。
- 修复回放后重新导入（先移除本地助手回复再导入）：结果“已导入历史消息
  1 条”，原生聊天经 `tg://openmessage?user_id=555000083` 打开后显示两条
  消息：`report.txt` 文件气泡（37 B · TXT，标题下方正文“看附件”）和助手
  “收到附件”。
- 截图：`docs/screenshots/qa-0.41.0-file-bubble.jpg`。

幂等：

- 再次导入同一 zip：结果只显示“新建助手 0，更新 1……”，没有历史/附件
  note；`chat.json` 仍为 2 条，原生聊天仍是同一文件气泡 + 一条回复，没有
  重复消息。
- 截图：`docs/screenshots/qa-0.41.0-repeat-chat.jpg`。

清理：

- 删除测试 Agent 目录、`files/rikka_upload_report.txt` 与样例 zip，重启后
  `dialogs` / `messages_v2` 对 555000083 均为 0 行，残留文件 0 个。
- 本包无 FATAL/ANR；crash buffer 仅有 Operit 自带 bash 的
  `ndk_translation` 段错误（第三方进程，与本包无关）。
- 重装/重启后已重新启用 `org.telegram.ox.OxAccessibilityService`。

## 3. 未覆盖 / 已知边界

- 支持图片与 document；video/audio 等其它 part 仍跳过。
- 每条消息只取第一个 document，多个附件会丢失其余项。
- 仅匹配备份 `upload/` 中的文件；远程 URL、data URL 附件不下载。
- 文件复制到应用私有目录，不进入 Agent 的 `media/`；文件条目由现有 Chatbox
  导出逻辑携带，但本轮未做导出回环复验。
- 未对真实 RikkaHub 导出的完整数据库/大附件做兼容性与性能验证。

## 4. 产物

- `dist/OrionekoX-0.41.0-alpha-arm64-x64.apk`（versionCode 4109），sha256
  `aff7743a17de46a103cff08b89ab5427b638fbcdbd10a273c8d0da20131476a5`
- `dist/OrionekoX-0.41.0-debug-arm64-x64.apk`，sha256
  `0eca64a72f5401230716bc00fda485bc416dac549c46860f2d680c22c3d04fe0`
