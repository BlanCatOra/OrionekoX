# 酒吧炒饭全流程测试报告（独立测试 A：建档、Provider、记忆与工作区）

## 0. 环境与结论摘要

- 设备：`emulator-5554`，1080×2400，Android（targetSdk 36）
- 包名：`io.github.blancatora.orionekox`（release）
- 版本：versionName `0.8.103`，versionCode `1189`
- 设备时间：2026-10-26 14:09 — 15:26（CST）
- 测试范围：A-SKILL.md 全流程 + 专项加深（建档、Provider、记忆、工作区）
- 截图目录：`/tmp/bar-rice-A/`（报告中均为绝对路径前缀省略，文件名对应）
- 崩溃：全程 `FATAL EXCEPTION` 0 次，`ANR in io.github.blancatora.orionekox` 0 次
- **最终结论：未通过全流程。** 主要步骤大多可执行，但存在 4 个 P1 缺陷：会话重开后消息列表空白、建群丢失成员、图片按文件发送且模型无法读图、导入 .oxagent 与原件共用 tg_id 造成身份冲突。

## 1. 全流程逐步结果

### 1.1 基线

| 项 | 结果 | 证据 |
|---|---|---|
| App 启动、聊天列表 | PASS | `/tmp/bar-rice-A/00-home.png` |
| 底部 聊天/联系人/设置/我的/终端 五 Tab | PASS | 同上；坐标来自界面 dump |
| versionName/versionCode | PASS | `versionName=0.8.103 versionCode=1189` |
| `adb logcat -c` | PASS | 14:09 清空 |

### 1.2 建立测试 Agent

| 步骤 | 结果 | 证据/说明 |
|---|---|---|
| 联系人 → 新建 → 添加智能体 | PASS | `/tmp/bar-rice-A/02-new-agent.png` |
| 直接新建选择 DeepSeek | 受限 | 编辑器 API Key 为空，测试连接返回 `HTTP 401 Authentication Fails`（`02-testconn.png`） |
| 参考 Yuki 配置 | PASS | Yuki 使用 `OpenDesign` / `https://amr-link.open-design.ai/v1` / `deepseek-v4.1-flash`（`ox-agents.png`） |
| 实际创建方式（偏差） | PASS（有偏差） | 长按 Yuki → **复制** 生成副本，再改名为 `酒吧炒饭A`；未读取或抄写任何密钥。复制出的 Provider/Key 已由系统屏蔽显示（`dup-edit.png`） |
| 名称 `酒吧炒饭A` | PASS | `ui-name-filled` / `02-agent-saved.png` |
| 简介 `深夜酒吧炒饭主厨` | PASS | `02-agent-saved.png` |
| 系统提示词（AGENTS.md） | PASS | 已替换为“你是「酒吧炒饭」深夜主厨。中文回答，先确认忌口，再给 15 分钟单锅配方。” |
| Provider 连通性 | PASS | `连接成功 · HTTP 200`（`02-testconn3.png`） |
| 保存并出现在 Ox 智能体列表 | PASS | `02-agent-saved.png`、`ox-agents.png` |
| 出现在联系人列表 | FAIL | 保存后联系人列表没有 `酒吧炒饭A`（`02-contacts-new.png`）。打开其聊天后仍未出现；最终联系人只出现导入副本 `酒吧炒饭A-copy`（`10-chatlist-final.png` 前一步 dump）。 |
| 出现在聊天列表 | PASS | `03-chatlist.png` |

偏差说明：直接新建 Agent 需要 API Key，而密钥禁止读取/输出；因此用“复制 Yuki”继承已配置 Provider。这仍然生成了新的 Agent 记录，但不是从空白编辑器手填 Provider。

### 1.3 单聊、记忆与工作区

| 步骤 | 结果 | 证据 |
|---|---|---|
| 发送长消息（14:33:10 左右） | PASS | `03-msg1-final.png`；`OxBridge interceptOutgoing ...` |
| 真实模型回复 | PASS | 14:33:42 `onRound content=1180`、`finishTurn len=1180`；回复含“忌口已记录：不吃香菜”和 15 分钟单锅流程（`03-reply1.png`） |
| 记住“不吃香菜” | PASS | 14:33:26 `tool remember done: remembered: User does not eat cilantro (香菜) — avoid cilantro in all recipes.` |
| 请求保存工作区文件 | PASS | 发送后出现权限弹窗“允许调用工具？ … write_file({"path": "bar-fried-rice.md", ...})”（`03-msg2-wait.png`） |
| 允许工具调用 | PASS | 14:35:34 `permission for write_file: true`、`wrote 2252 bytes to bar-fried-rice.md` |
| 回复告知路径 | PASS | “配方已保存。路径：`bar-fried-rice.md` … 本次写入 2252 字节” |
| 工作区文件存在且内容正确 | PASS | `bar-fried-rice.md` 2.2 KB，预览含“忌口：不吃香菜”、用量、备料、时间表（`03-file-preview.png`、`03-workspace-agentA.png`） |
| 新回合回忆忌口 | PASS | 14:37:53 发送，14:37:56 `content=11`；冷启动后可见回复“香菜（你也不吃香菜）。”（`04-chat-after-cold.png`） |
| 返回列表再进入，会话仍在 | **FAIL** | 重新进入后聊天区完全空白，无任何消息气泡；需 force-stop 冷启动后才恢复（`03-chat-reopen.png`、`03-reopen2.png`、`03-scroll2.png` 对照 `03-yuki-history.png`）。详见问题 P1-1 |
| 记忆仍在（不经 UI 历史） | PASS | 记忆页含 `2026-10-26 14:33 User does not eat cilantro (香菜)...`（`03-memory-page.png`） |

### 1.4 媒体

| 步骤 | 结果 | 证据 |
|---|---|---|
| 生成本地 640×480 JPEG 并 push | PASS | `bar-rice-A.jpg` 13308 B，`BAR FRIED RICE` 文字 |
| 附件发送 | FAIL（缩略图） | 发送后是**文件气泡**：橙色文档图标 + `bar-rice-A.jpg / 13.0 KB JPG`，无图片缩略图（`04-chat-after-cold.png`）。日志 `persistAndShow media type=9 mediaClass=TL_messageMediaDocument photoNonNull=false`，`startTurn ... image=false` |
| 无 X / 0KB / 下载箭头 / 红叹号 | PASS | 气泡显示 13.0 KB、已查看；无上述异常标记 |
| 模型可查看图片 | FAIL | 模型调用 `read_file` 读到 JPEG 原始字节，回复“我打不开这张图……只能按文本读取，看不到画面内容”（`04-image-after-allow.png`、`04-chat-after-cold.png`） |
| 长按图片消息出现菜单 | PASS | 菜单：回复、转发、保存到下载、编辑、转发、删除（`04-image-longpress.png`） |
| 仅查看不删除 | PASS | 按返回退出 |

### 1.5 本地群、成员与群回复

| 步骤 | 结果 | 证据 |
|---|---|---|
| 新建群组，选择 `酒吧炒饭A` + `Yuki Waifu` | PASS | 已选 2 / 200000（`05-group-selected.png`） |
| 命名 `酒吧炒饭A测试群` | PASS | `05-group-name2.png` |
| 群资料成员行正确（BlanCat 创建者 + 2 agent） | **FAIL** | 实际只有 BlanCat（创建者）+ Yuki Waifu，`酒吧炒饭A` 消失；标题却是“2 位成员, 2 人在线”（`05-group-info.png`）。详见 P1-2 |
| 群内发问，等待本地 agent 回复 | PASS（仅 Yuki） | 14:47:16 发送；14:47:25 Yuki 回复“我这边不加香菜，两人份就留一半香菜单独放边上，谁想吃自己加～”，无红叹号（`05-group-reply.png`） |
| 添加 Echo Assistant | PASS | 确认文案“您确定要添加 Echo Assistant 到 酒吧炒饭A测试群 吗？/ 向 Echo Assistant 显示最后100条消息”；成员数 2→3，Echo 仅一行（`05-after-add-echo2.png`） |
| 长按 Echo → 从群组中移除 | PASS | 移除后立即恢复为 2 位成员，Echo 消失 |
| 长按群消息出现菜单 | PASS | 回复/转发/复制/转发/删除（`05-group-msg-menu2.png`） |

### 1.6 设置与扩展抽查

| 项 | 结果 | 证据 |
|---|---|---|
| Ox 设置逐项查看（模式、语言、本地会员、引导、智能体、MCP、工具、技能库、工作区） | PASS | `06-ox-settings.png` 等；未改开关 |
| MCP 服务列表/详情/新增入口 | PASS | QA MCP `http://10.0.2.2:8878/mcp` 显示 HTTP 502（环境/服务端离线）；DeepWiki 3 个工具；新增表单打开后取消（`06-mcp.png`、`06-mcp-add.png`） |
| 技能库示例 SKILL.md 预览可关闭 | PASS | `06-skill-preview.png`；点“确定”正常关闭 |
| 技能预览可滚动 | 无法验证 | 示例内容过短（约 4 行），无滚动余量；不计 PASS/FAIL |
| 终端 TTY 1/2/3 执行 pwd | PASS | 三个标签均显示 `/storage/emulated/0/Download` 与命令回显（`06-tty1b.png`、TTY2 dump、`06-tty3.png`） |
| 设置 → 代理与 VPN 只读查看 | PASS | 使用代理开、SOCKS5、10.0.2.2:7890；无 VPN 配置；未修改（`06-proxy.png`） |
| 文案/翻译/UI 检查 | 有问题 | 见第 3 节 P2/P3 项 |

### 1.7 崩溃与清理

- `adb logcat -d | grep -E "FATAL EXCEPTION|ANR in io.github.blancatora.orionekox"`：0 条（14:09—15:26）。
- 未删除任何用户数据；未改真实账号设置。
- 保留的测试对象（供主代理清理）：
  - Agent `酒吧炒饭A`（原始测试 Agent，Provider: OpenDesign / deepseek-v4.1-flash）
  - Agent `酒吧炒饭A-copy`（`.oxagent` 导入副本，@agent_15）
  - 群 `酒吧炒饭A测试群`（现成员：BlanCat、Yuki Waifu）
  - 工作区文件 `bar-fried-rice.md`、`inbox/1792996894268_bar-rice-A.jpg`
  - `/sdcard/Download/`：`bar-rice-A.jpg`、`bar-rice-A-avatar.png`、`note-B.txt`、`____A.oxagent`

## 2. 专项加深结果

### 2.1 Agent 编辑器字段逐项修改与持久化

| 字段 | 结果 | 证据 |
|---|---|---|
| 名称 | PASS | `酒吧炒饭A`；冷启动后仍在 |
| 备注/简介 | PASS | `深夜酒吧炒饭主厨`；Ox 列表、聊天标题均显示 |
| 头像 | PASS | 选择本地纯色 PNG（红底金边）；保存、冷启动后聊天列表仍显示该头像（`10-cold-final.png`、`09-avatar-check.png`） |
| Provider/接口地址 | PASS | OpenDesign，`https://amr-link.open-design.ai/v1` |
| 模型 | PASS | `deepseek-v4.1-flash` |
| 温度 | PASS | 0.7 → 0.9，保存后重进仍为 0.9 |
| 最大输出 Token | PASS | 128000 → 64000，保存后重进仍为 64000 |
| 系统提示词 | PASS | 重进仍为指定文本 |
| 技能 | PASS | 示例技能 1 个→关闭保存（未启用）→重开保存（已启用 1 个），状态持久化 |
| 工具 | PASS（仅全局） | 编辑器无工具列表；“工具与权限”按 Agent 设置。`get_time` 默认·自动→询问，退出重进仍为“询问”；但无法回到“默认”，见 P3-2 |
| 记忆 | PASS | 见 2.3 |
| 用户名 | 无法执行 | Agent 编辑器无用户名字段；用户名由系统生成（副本为 `@agent_15`），无编辑入口 |

### 2.2 模型拉取/连通性

- OpenDesign 连接测试：`连接成功 · HTTP 200`（`02-testconn3.png`）。
- “从 API 获取模型列表”：成功返回 13 个模型，含 `deepseek-v4.1-flash`（`09-model-list.png`）。
- 直接新建 DeepSeek（无 Key）：`HTTP 401 Authentication Fails (governor)`；未保存无效配置。
- 未输出任何密钥。

### 2.3 记忆编辑器

- 新增 `最爱的宵夜是酒吧炒饭`（时间戳 15:05），保存后重进仍在（`ui-memory-recheck.xml`）。
- 随后删除该测试记忆并保存；重进仅保留既有记忆，`不吃香菜` 条目未被破坏。
- 记忆页条目格式为“时间戳 + 文本”，可直接编辑；本次编辑过程中曾因光标未在文末造成一次错位，已撤销并重建，未遗留脏数据。

### 2.4 工作区操作

| 操作 | 结果 | 证据 |
|---|---|---|
| 新建嵌套文本文件 | PASS | `testdir/note-A.txt`，16 B（`08-nf-saved.png`；界面只显示完整相对路径，没有独立目录行） |
| 重命名 | PASS | `testdir/note-A.txt` → `testdir/note-B.txt` |
| 移动 | PASS | 通过重命名把根目录 `note-B.txt` 移到 `testdir/note-B-moved.txt`；根行消失 |
| 复制 | PASS | 生成 `testdir/note-B (copy).txt` |
| 导出到设备 | PASS | SAF 保存到 `/sdcard/Download/note-B.txt`，16 B（`08-export.png`） |
| 导入文件 | PASS | 从 Downloads 导入 `note-B.txt` 到工作区根目录（`08-imported.png`） |
| 路径不越界 | PASS | 重命名为 `../escape-B.txt` 被拒绝，提示“请输入工作区内的相对路径（不能包含 .. 或以 / 开头）。”（`08-traversal-toast.png`） |
| 失败有提示 | PASS | 同上 toast |
| 清理 | PASS | 3 个测试文件已删除；保留 `bar-fried-rice.md` 与 inbox 图片 |

说明：界面没有“新建文件夹”按钮；目录通过“新建文本文件”时输入 `dir/file` 相对路径隐式创建，列表也不呈现目录节点。这是功能缺口/交互不直观（P3-1）。

### 2.5 .oxagent 导出/导入

| 项 | 结果 | 证据 |
|---|---|---|
| 导出 | PASS | 保存为 `/sdcard/Download/____A.oxagent`（6292 B） |
| 包内容 | PASS | zip 内：`agent.json`（967 B）、`avatar.jpg`（1936 B）、`workspace/bar-fried-rice.md`（2252 B）、`workspace/inbox/1792996894268_bar-rice-A.jpg`（13308 B） |
| 人设/参数保留 | PASS | `agent.json` 含 name、agents_md、remark、provider_id、model、temperature 0.9、max_tokens 64000、skill_ids |
| 密钥安全 | PASS | `agent.json` 中 `api_key` 为空字符串；未泄漏密钥 |
| 记忆保留 | PASS | 导入副本记忆页仍含 favorite color/number 与 cilantro 条目 |
| 工作区保留 | PASS | 导入副本工作区含 `bar-fried-rice.md` 与 inbox 图片（`ui-ws-copy.xml`） |
| 不覆盖原 Agent | PASS（生成副本） | Ox 智能体列表出现第二条记录；原记录仍在 |
| 导入幂等 | 未验证重复导入 | 需求允许“幂等或生成副本”，本次为生成副本；未重复导入同一文件 |
| 身份唯一性 | **FAIL** | 导出文件 `agent.json` 的 `tg_id=555000015` 与原件相同；导入后 Ox 列表有 2 条，但聊天列表仍只有 1 条、联系人只显示 `酒吧炒饭A-copy`，原 `酒吧炒饭A` 不在联系人列表。详见 P1-3 |
| 文件名/i18n | 有问题 | 中文名导出为 `____A.oxagent`；导入副本未自动加“副本”后缀，两个 Agent 初始同名（P2-4） |

### 2.6 冷启动复查

- force-stop 后重启：Ox 智能体列表仍有两条测试 Agent；头像、简介、Provider、模型仍在。
- 记忆仍在；工作区 `bar-fried-rice.md` 和 inbox 图片仍在；会话历史在冷启动后才恢复显示。
- 结论：数据持久化整体 PASS，但“会话重开空白”是独立的渲染缺陷（P1-1），不能视为数据丢失。

## 3. 问题清单（复现、期望/实际、级别）

### P1-1 重开 Agent 会话后消息列表空白，冷启动才恢复

- 复现：打开 `酒吧炒饭A` 单聊，发送“我在酒吧后厨……”并收到回复；返回聊天列表；再次进入同一会话。聊天区为空白背景，无任何气泡；期间日志 `OxBridge` 已持久化多轮（`persistAndShow`、`finishTurn`）。force-stop 后重启，历史正常显示。
- 期望：重开会话立即显示已持久化历史。
- 实际：历史不渲染，只能看到顶部标题与“在线”，用户会误以为消息/记忆丢失。
- 证据：`/tmp/bar-rice-A/03-chat-reopen.png`、`03-reopen2.png`、`03-scroll2.png`；对照 `03-yuki-history.png`（Yuki 会话正常）；恢复后 `04-chat-after-cold.png`。
- 级别：P1。

### P1-2 建群时选中的 `酒吧炒饭A` 未加入，成员数与成员行不一致

- 复现：聊天列表 → 新建群组 → 选择 `酒吧炒饭A` 与 `Yuki Waifu`（显示“已选 2”）→ 命名 `酒吧炒饭A测试群` → 创建 → 打开群资料。
- 期望：显示 BlanCat（创建者）+ Yuki Waifu + 酒吧炒饭A，共 3 人。
- 实际：只显示 BlanCat + Yuki Waifu；标题“2 位成员, 2 人在线”（计数不含创建者）。`酒吧炒饭A` 无法在群内回复。
- 证据：`05-group-selected.png`、`05-group-info.png`、`05-group-reply.png`（仅 Yuki 回复）。
- 级别：P1（静默丢成员）。

### P1-3 `.oxagent` 导入与原件共用 `tg_id`，聊天/联系人身份冲突

- 复现：把 `酒吧炒饭A` 导出为 `.oxagent` → 在同一设备导入 → Ox 智能体列表出现两条同名人设；把副本改名为 `酒吧炒饭A-copy`。此时联系人列表只显示 `酒吧炒饭A-copy`，原 `酒吧炒饭A` 消失；聊天列表仍只有一条旧标题 `酒吧炒饭A 深夜酒吧炒饭主厨`。
- 证据：导出包 `agent.json` 中 `id=dup1a6046`、`tg_id=555000015` 与原件相同；`ui-agents14.xml` 两条记录；`ui-contacts-final.xml` 仅副本；`10-chatlist-final.png` 仅旧标题。
- 期望：导入副本获得独立 tg_id/会话，或明确提示“共用同一会话/身份”。
- 实际：两个 Agent 配置共用同一 Telegram 本地用户/会话，联系人/聊天列表只呈现其中一个，名称与配置不一致。
- 级别：P1（身份/数据一致性问题）。

### P1-4 图片按“文件”发送，无缩略图，模型无法查看图片

- 复现：在单聊附件 → 文件 → 选择 `/sdcard/Download/bar-rice-A.jpg` → 发送。
- 期望：图片气泡显示缩略图；模型可通过视觉读取图片内容。
- 实际：显示为文档气泡（橙色文档图标，`13.0 KB JPG`），无缩略图；`startTurn image=false`；模型调用 `read_file` 读取到 JPEG 原始字节，回复“我打不开这张图……只能按文本读取”。
- 证据：`/tmp/bar-rice-A/04-chat-after-cold.png`、`04-image-after-allow.png`；logcat `persistAndShow media type=9 mediaClass=TL_messageMediaDocument photoNonNull=false`、`tool read_file done: JFIF...`。
- 级别：P1（核心媒体能力不可用；无 X/0KB/红叹号，但“缩略图”要求未满足）。

### P2-1 复制 Agent 后欢迎语仍用旧名称

- 复现：长按 Yuki → 复制 → 改名为 `酒吧炒饭A` 并保存；首次进入该会话。
- 期望：欢迎语使用新名称。
- 实际：欢迎语为“你好，我是 🤖 Yuki Waifu · 副本。直接发消息即可开始对话。”
- 证据：`03-agent-chat.png`。
- 级别：P2（文案/身份不一致）。

### P2-2 `.oxagent` 导出文件名丢失中文；导入副本同名

- 实际：`酒吧炒饭A` 导出为 `____A.oxagent`（中文被替换为下划线）；导入后副本与原件显示名相同，只能手动改名区分。
- 期望：保留中文文件名（或转写为拼音且不丢信息）；导入时自动追加“副本”后缀。
- 证据：`09-export-agent.png`、`ui-agents13.xml`。
- 级别：P2（可用性/文件名规范）。

### P2-3 新建 Agent 后联系人列表不显示（原 Agent）

- 实际：新建/复制并保存 `酒吧炒饭A` 后，联系人列表长时间无此行；打开会话后仍无；最终只出现导入副本。
- 期望：保存后联系人列表立即可见。
- 证据：`02-contacts-new.png`、`ui-contacts-final.xml`。
- 级别：P2（与 P1-3 可能同源，但影响独立可见性）。

### P2-4 群成员计数语义混乱且可被误读

- 实际：群资料显示“2 位成员, 2 人在线”，但下方列出 BlanCat（创建者）+ Yuki Waifu；创建者不计入“成员”数，与常规 Telegram 语义不一致。
- 期望：显示总人数（含创建者）或明确写“2 个智能体”。
- 证据：`05-group-info.png`。
- 级别：P2（文案/计数）。

### P3-1 工作区没有“新建目录”，只靠路径隐式创建

- 实际：工作区仅有“新建文本文件/导入文件”；通过文件名 `testdir/note-A.txt` 隐式创建子目录，列表仍按完整路径平铺显示。
- 期望：提供新建文件夹入口，或明确提示“输入 `目录/文件` 可创建子目录”。
- 证据：`08-nf-saved.png`、`ui-wsA2.xml`。
- 级别：P3（功能可完成，但交互不直观）。

### P3-2 工具权限无法恢复“默认”

- 实际：`get_time` 从“默认 · 自动”点击后依次变为“自动 → 询问 → 禁止 → 自动”，没有回到“默认”的入口。
- 期望：提供“恢复默认/继承”选项。
- 证据：`06-tools.png`、`ui-toolnow.xml`。
- 级别：P3。

### P3-3 技能预览无法验证滚动

- 实际：示例 SKILL.md 内容仅 4 行左右，弹窗内无滚动余量；关闭正常。
- 期望：示例技能提供足够长的内容以验证滚动，或明确说明预览为只读固定高度。
- 级别：P3（测试覆盖缺口，非明确缺陷）。

### 环境/既有问题（非本次改动导致）

- MCP `QA MCP`（`http://10.0.2.2:8878/mcp`）显示 `HTTP 502`，当前服务不可达。
- 聊天列表既有 `QA2` 的 `HTTP 401 Authentication Fails` 历史错误消息（测试前已存在）。
- 直接新建 DeepSeek Agent 因无 API Key 返回 401（符合预期，未保存）。

## 4. 未执行项及原因

| 项 | 原因 |
|---|---|
| 直接新建 Agent 时手填 Provider + Key | 规则禁止读取/输出密钥；改用复制 Yuki 继承已配置 Provider |
| 用户名编辑 | Agent 编辑器无用户名字段；用户名由系统生成，无入口 |
| 技能预览滚动验证 | 示例内容过短，无滚动余量 |
| 重复导入同一 `.oxagent` 验证幂等 | 需求允许“幂等或生成副本”，已确认生成副本、不覆盖；未做第二次导入 |
| 删除测试 Agent/群 | 规则要求保留并写清名称，供主代理清理 |

## 5. 最终结论

**未通过（FAIL）。**

- 可正常完成：Agent 创建（经复制继承配置）、Provider 连通性/模型拉取、真实模型回复、记忆写入与回忆、工作区写文件与路径越界防护、群内单 Agent 回复、成员增删、终端、MCP/技能/代理只读抽查、`.oxagent` 导出与字段/工作区保留。
- 阻断性问题：会话重开后空白（P1-1）、建群静默丢成员（P1-2）、导入 `.oxagent` 身份冲突（P1-3）、图片按文件发送且模型不可读（P1-4）。
- 无崩溃/ANR；失败均有截图或 logcat 证据，未将失败伪装为通过。

## 6. 关键证据索引

- 基线：`/tmp/bar-rice-A/00-home.png`
- Agent 创建与连接：`02-agent-saved.png`、`02-testconn3.png`、`09-model-list.png`、`09-avatar-check.png`
- 单聊与回复：`03-msg1-final.png`、`03-reply1.png`、`04-chat-after-cold.png`
- 会话空白缺陷：`03-chat-reopen.png`、`03-reopen2.png`、`03-yuki-history.png`
- 工作区：`03-file-preview.png`、`03-workspace-agentA.png`、`08-nf-saved.png`、`08-traversal-toast.png`
- 媒体缺陷：`04-chat-after-cold.png`、`04-image-after-allow.png`、`04-image-longpress.png`
- 群缺陷/成员：`05-group-selected.png`、`05-group-info.png`、`05-group-reply.png`、`05-after-add-echo2.png`
- 扩展：`06-mcp.png`、`06-skill-preview.png`、`06-tty1b.png`、`06-tty3.png`、`06-proxy.png`
- 专项：`07-advanced.png`、`07-memory.png`、`08-export.png`、`09-export-agent.png`、`10-cold-final.png`
- 导出包：`/sdcard/Download/____A.oxagent`（`agent.json` 中 `api_key` 为空）
- 关键 logcat：
  - `14:33:26 tool remember done: remembered: User does not eat cilantro (香菜) ...`
  - `14:33:42 onRound content=1180` / `finishTurn len=1180`
  - `14:35:34 permission for write_file: true` / `tool write_file done: wrote 2252 bytes to bar-fried-rice.md`
  - `14:37:56 onRound content=11`
  - `14:41:34 persistAndShow media type=9 mediaClass=TL_messageMediaDocument photoNonNull=false`
  - `14:47:25 onRound content=30`（群 Yuki 回复）
  - 全程 `FATAL EXCEPTION`=0、`ANR`=0
