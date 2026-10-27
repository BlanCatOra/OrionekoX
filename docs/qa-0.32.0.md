# QA — alpha 0.32.0（导出 Kelivo / Chatbox 备份）

- 版本：`0.32.0`，versionCode **3209**（`0.31.0` 为 3109）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 07:00–07:18（CST）
- 目标：把本机非内置 Agent、聊天、头像、图片和附件导出为 Chatbox v2 zip，
  验证导出包可被本应用重新导入，补上“双向兼容”的导出方向。
- 版本规则：新增“Chatbox v2 备份导出”这一可见能力，按 minor 从 0.31.0 →
  **0.32.0**，versionCode 3209（320×10+9）。

## 1. 新增能力

- Ox 设置新增“Export Kelivo / Chatbox backup”：确认框说明范围后走系统
  `ACTION_CREATE_DOCUMENT`，默认文件名 `orioneko-chatbox-backup.zip`。
- 导出所有非内置 Agent：
  - 有聊天记录的 Agent 写成 session（system prompt + 消息），不再重复写
    copilot，保证回环导入只生成一个 Agent；
  - 无聊天记录的 Agent 写成 copilot（提示词、模型名、采样参数、头像）。
- Chatbox v2 结构：`manifest.json`（format/formatVersion/exportedAt/
  application/exportItems/data/sessions/resources/warnings/stats）、
  `settings.json`、`copilots.json`、`sessions/<id>/session.json`、
  `resources/*`；每个文件都有 sha256 与 size。
- 头像 `kind=avatar`、图片 `kind=image`、附件 `kind=raw-attachment`；
  资源正文使用 `data:<mime>;base64,...`（encoding=data-url-base64），
  按 global/session 作用域与 `originalStorageKeys` 映射。
- 明确不导出：API Key、provider baseUrl、Authorization、工作区、长期记忆、
  技能、MCP、主题。资源上限 64 个/单个 2 MB/总计 24 MB。
- 结果 toast 汇总 agents/conversations/messages/files，并最多显示 3 条跳过
  原因（资源过大或超总量）。

## 2. 设备验证 — PASS

准备：导入一个 seed 备份，得到
- `Roundtrip Assistant`（copilot，头像 AV，prompt/temperature 0.6/0.9/128）；
- `Roundtrip Chat`（session，头像 AV，prompt，0.4/0.8/96，含 1 张图片、
  1 个 `notes.txt` 附件、4 条消息）。

导出：

- 确认框显示“Export all non-built-in agents… API keys and provider URLs are
  not included.”，SAF 默认文件名 `orioneko-chatbox-backup.zip`，保存成功。
- 文件 483,866 B；schema 检查：`format=chatbox-backup`、
  `formatVersion=2`、13 sessions / 15 resources、stats 计数一致；
  15 个资源的 size/sha256 全部匹配；10 个 copilots；
  导出包内无 `ox1:`、`sk-`、`test-key`，`api_key` 仅出现在一条历史
  401 错误文本中，不是密钥。
- 因模拟器还保留多轮旧测试 Agent，另生成一个只含 seed 两个 Agent 的
  过滤子集（8,306 B）用于回环验证；过滤只裁剪条目，不改写资源/会话格式。

回环导入：

- 预览：`助手 1 个`、`Conversations: 1 (4 messages)`、2 个头像资源、
  1 张聊天图片、1 个聊天附件。
- 导入：新建助手 1、1 段对话/4 条；回环生成的
  `kelivo_kelivo_a2` 保留 prompt、0.6/0.9/128 与头像；
  `kelivo_chat_kelivo_chat_s1_current_current` 保留 prompt、0.4/0.8/96、
  头像、图片 `img_0.png`、附件 `2_notes.txt` 与 4 条消息。
- 原生界面：头像 AV 显示在标题左侧；RT 图片带“导出这张图”说明；
  `notes.txt` 22 B TXT 文件气泡；“已收到截图。”“附件也收到了。”均正确。
- 清理：删除 4 个测试 Agent（seed 与回环副本）、seed/subset/导出 zip；
  重启后 dialogs/messages 对 555000064–555000067 均为 0 行；无本包
  FATAL/ANR。
- 截图：`docs/screenshots/qa-0.32.0-export-confirm.jpg`、
  `qa-0.32.0-export-saf.jpg`、`qa-0.32.0-roundtrip-preview.jpg`、
  `qa-0.32.0-roundtrip-result.jpg`、`qa-0.32.0-roundtrip-chat.jpg`。

## 3. 未覆盖 / 已知边界

- 导出范围是全部非内置 Agent，没有单选/多选；模拟器上的旧测试 Agent 也会
  进入完整导出包，回环 QA 用过滤子集隔离。
- 有聊天的 Agent 只导出 session（不重复 copilot），无聊天的只导出 copilot；
  回环后 Agent id 会带一层 `kelivo_`/`kelivo_chat_` 前缀，不是原 id。
- 不导出 API Key、provider baseUrl、工作区、记忆、技能、MCP、主题、
  工具权限；外部 Chatbox 客户端尚未实际导入验证。
- 资源上限会跳过超大头像/图片/附件；历史文本里可能自然出现 `api_key`
  字样（错误消息），但不会有真实密钥。
- 只支持 chat.json 中已有的文本/图片/附件；纯系统提示、空会话可能不生成
  session。

## 4. 产物

- `dist/OrionekoX-0.32.0-alpha-arm64-x64.apk`（versionCode 3209），sha256
  `c358fd23483d17ec8a50f1015f0494da1ec8e801fd988818cb50911a37556b51`
- `dist/OrionekoX-0.32.0-debug-arm64-x64.apk`，sha256
  `5e0a54de54e0c0cd8e6a45d621f6e6b3c5f7475f95fb5fdfc89d9b37e5422a96`
