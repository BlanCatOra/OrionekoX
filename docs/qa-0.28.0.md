# QA — alpha 0.28.0（Kelivo / Chatbox 对话历史导入）

- 版本：`0.28.0`，versionCode **2809**（`0.27.2` 为 2729）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 05:15–05:45（CST）
- 目标：把 Kelivo / Chatbox 备份中的对话与话题导入为本地 Agent 聊天记录，
  在原生聊天界面按原始时间回放；保持重复导入幂等。
- 版本规则：新增“备份对话历史导入”这一可见能力，按 minor 从 0.27.2 →
  **0.28.0**，versionCode 2809（280×10+9）。

## 1. 新增能力

- Chatbox v2 zip：读取 `manifest.json` 的 `sessions[]` 描述符，按
  `sessions/<id>/session.json` 解析；兼容没有 manifest、只有
  `sessions/**/*.json` 的旧包。
- 兼容直接 JSON：`chat-sessions-list` + `session:<id>` 键；也接受单个
  session 对象或 session 数组。
- 每个 session 的 `threads[]` 各生成一段对话；`session.messages` 为“当前
  话题”，若其消息 id 集合与某个 thread 相同则不重复导入。
- 每段对话生成一个本地 Agent：稳定 id `kelivo_chat_<session>_<thread|current>`，
  名称取 thread.name / session.threadName / session.name；首条 system 消息作为
  `agents_md`，无则使用 settings.defaultPrompt。
- 继承 session.settings 的 provider / modelId / temperature / topP / maxTokens：
  先按 provider id 匹配备份中的 `provider_configs_v1`，未命中回退本机现有
  Provider；`chatbox-ai` 视为无 Provider。
- 消息转换：system 首条进入人设，后续 system 记为 `[系统]`，tool 记为
  `[工具]`，图片/文件记为 `[图片]` / `[文件]`；保留 epoch ms 时间戳；
  上限 100 段对话、每段 500 条、单条 20,000 字符。
- 结果弹窗分开统计助手与对话；仅导入对话时不再显示“新建 0 助手”。
- 导入完成后对每段对话调用 `replayImportedHistory`，把 `chat.json` 写入原生
  messages_v2 并重建 dialog 行，聊天界面不再只显示欢迎语。

## 2. 设备验证 — PASS

样例 zip：

- `s1` “Chatbox Demo”，provider `prov-1`，model `kelivo-test-model`，
  temperature 0.4 / topP 0.8 / maxTokens 128；当前消息 4 条（system、user、
  assistant、tool），thread `old1` “旧话题” 2 条。
- `s2` “Conv Only” 2 条，无 provider、无 thread。

结果：

- 预览：`Conversations: 3 (7 messages)`；Items “旧话题、当前话题、Conv Only”；
  note 提示“将导入 3 段对话（7 条消息）；已存在的对话会替换历史”。
- 首次导入：`Imported 3 conversations (7 messages)`；
  `files/oxagents/kelivo_chat_s1_current|s1_old1|s2_current` 三个目录。
  s1 两个 Agent 的 `base_url=https://kelivo.example/v1`、model
  `kelivo-test-model`、0.4/0.8/128、`agents_md=You are Kelivo demo.`；
  s2 回退本机 Provider，`agents_md=Default Kelivo prompt`。
- 聊天内容：s1_current 为 user“你好”、assistant“你好！这是导入的历史。”、
  assistant“[工具] tool result: 42”，system 已转为 AGENTS.md；s1_old1 为
  “旧问题/旧回答”；s2 为“第二段对话/收到。”。
- 重复导入同一 zip：`Imported 3 conversations (7 messages)`，note
  “已存在的 3 段对话历史已替换”；三个 Agent 目录仍只有 3 个，
  messages_v2 行数仍为 2/3/2，未翻倍。
- 原生界面：deep link 打开 `555000054`，标题“当前话题”，日期分隔
  “2025年10月28日”，用户“你好”、助手“你好！这是导入的历史。”与
  “[工具] tool result: 42”三个气泡均正确显示；无欢迎语残留。
- 直接 JSON：1 段“JSON 对话”（2 条），Agent 配置 0.3/0.6/64 正确；原生界面
  显示“10月25日”“JSON 历史 / JSON 回复”。
- 截图：`docs/screenshots/qa-0.28.0-kelivo-conv-preview.jpg`、
  `qa-0.28.0-kelivo-conv-result.jpg`、`qa-0.28.0-kelivo-conv-chat.jpg`、
  `qa-0.28.0-json-preview.jpg`、`qa-0.28.0-json-chat.jpg`。
- 清理：删除 4 个测试 Agent 目录与 Downloads 样例；重启后
  dialogs/messages_v2 中 555000053–555000056 均为 0 行；无本包 FATAL/ANR
  （crash buffer 只有 `sh.orioneko.messenger` 的旧 VPN 记录，与本包无关）。

## 3. 未覆盖 / 已知边界

- 图片、文件、音频附件只留占位文本，不复制备份资源；工具调用结果压平成
  assistant 气泡，不还原 tool 角色/调用卡片。
- 时间戳按备份原值保留，因此旧备份的对话会排在聊天列表靠后的位置；这是
  忠实排序，而非导入丢失。
- 每个 thread 独立成一个 Agent，不建立“一个助手含多段会话”的父子关系；
  暂无按会话切换的界面。
- 仅支持 Chatbox v2 / 旧版 session 列表结构；真实 Kelivo 备份、加密包、
  工作区、头像/背景、presetMessages/regex 仍未导入。
- 宿主模拟器在本轮打开搜索输入框/IME 时会整机退出（`-gpu guest` 与
  `-gpu off` 均可复现），属于宿主渲染问题；本轮改用 deep link 完成聊天
  渲染验收，未把该现象计入应用缺陷。

## 4. 产物

- `dist/OrionekoX-0.28.0-alpha-arm64-x64.apk`（versionCode 2809），sha256
  `7cff4b8b9b360190374dbfc277e8b328ae599fe65ccf866a89c2b5667d230f7b`
- `dist/OrionekoX-0.28.0-debug-arm64-x64.apk`，sha256
  `149ed3bd0483b18cb8ef5c06eceebdff163412f7a2bd56ebddc0bce56f29806b`
