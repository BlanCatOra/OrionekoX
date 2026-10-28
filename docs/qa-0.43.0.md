# QA — alpha 0.43.0（Kelivo / Chatbox 多附件与图文混合导入）

- 版本：`0.43.0`，versionCode **4309**（`0.42.0` 为 4209）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 11:24–11:33（CST）
- 目标：Kelivo / Chatbox 备份中单条消息带多张图片、多个文件或图文混合时，
  应全部导入并在原生聊天按顺序显示；重复导入替换历史而不是累积。
- 版本规则：新增“单消息多附件/图文混合拆分”这一可见能力，按 minor 从
  0.42.0 → **0.43.0**，versionCode 4309（430×10+9）。

## 1. 新增能力

- `OxKelivoImporter.convertMessages` 不再只取每消息第一张图片和第一个文件，
  也移除了“有图片就丢弃文件”的旧逻辑。
- 按 `contentParts` 顺序收集全部可用媒体（每段对话仍受 40 张图片 / 20 个
  文件上限约束）；第一条媒体与正文组成主气泡，其余按顺序拆成独立条目。
- 图片保存到 Agent 的 `media/img_<n>.<ext>`，文件保存为
  `media/<n>_<name>`，文件名优先取 `fileName`。
- 正文中的 `[图片]`、`[文件] name` 占位符在成功匹配媒体后移除；纯媒体
  消息允许空正文。
- 预览新增“单条消息的多个/混合附件会按顺序拆成多条气泡”，图片/附件数量
  按实际拆分后的条目统计。

## 2. 设备验证 — PASS

准备：

- 2,392 B 样例 `/sdcard/Download/kelivo-multi-043.zip`：
  1 个会话、4 个资源（2 张 PNG、`notes.txt`、`data.json`）。
- 用户消息的 `contentParts` 依次为：文字“混合附件测试”、image-key-1、
  image-key-2（第二张为 data URL 编码）、notes.txt、data.json；
  助手回复“收到混合附件”。

导入：

- 预览：“对话 1 段（5 条消息）”“将导入 2 张聊天图片”“将导入 2 个聊天附件”，
  并有拆分说明。
- 结果：“导入对话 1 段（5 条消息）”“导入聊天图片 2 张”“导入聊天附件 2 个”。
- `chat.json` 共 5 条，顺序为：
  1. “混合附件测试” + `media/img_0.png`（300 B）
  2. `media/img_1.png`（300 B，空正文）
  3. `media/2_notes.txt`，`file_name=notes.txt`（19 B）
  4. `media/3_data.json`，`file_name=data.json`（29 B）
  5. 助手“收到混合附件”
- 原生聊天（tgId `555000086`）显示绿色照片气泡（正文“混合附件测试”）、
  橙色照片气泡、`notes.txt`（19 B · TXT）、`data.json`（29 B · JSON）
  两个文件气泡，以及助手回复。第二张图片的 data URL 编码也正确解码。
- 截图：`docs/screenshots/qa-0.43.0-mixed-attachments.jpg`。

重复导入与清理：

- 再次导入同一 zip：预览提示“已存在 1 段对话，将替换历史”；结果
  “已存在的 1 段对话历史已替换”“导入聊天图片 2 张”“导入聊天附件 2 个”；
  `chat.json` 仍 5 条，原生 `messages_v2` 仍 5 行，无重复。
- 删除测试 Agent 目录与样例 zip 后重启，对应 `dialogs` / `messages_v2`
  均为 0 行，测试媒体文件随 Agent 目录删除。
- 本包无 FATAL/ANR；已重新启用 `org.telegram.ox.OxAccessibilityService`。

## 3. 未覆盖 / 已知边界

- 沿用每段对话 40 张图片 / 20 个文件、最多 64 个资源文件的限制；超出部分
  跳过。
- 拆分后的媒体共享原消息时间戳；同一消息内的展示顺序依赖导入顺序。
- 不解析 Chatbox `info` 等非文本/非媒体 part，仍按原逻辑忽略。
- 仅当资源能在备份中匹配到 `image`/`raw-attachment`/`parsed-attachment`
  时导入；远程 URL 不下载。
- 未做 Chatbox 导出回环；真实 Cherry Studio/Kelivo 客户端备份未实测。

## 4. 产物

- `dist/OrionekoX-0.43.0-alpha-arm64-x64.apk`（versionCode 4309），sha256
  `420018ff5ba6531a7ba9a3cf6315f30bcbc597d3eb7fba71311af7d6ee5e71e1`
- `dist/OrionekoX-0.43.0-debug-arm64-x64.apk`，sha256
  `afb28a66240c6e4e3f3a4fd3a5f80dec1948e667c1a405b2586d074a552813ce`
