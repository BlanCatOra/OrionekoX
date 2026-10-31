# 酒吧炒饭全流程测试报告（B：群聊、媒体与工具权限）

## 0. 元信息

| 项 | 值 |
|---|---|
| 版本 | OrionekoX 0.8.103（versionCode 1189），release 包 `io.github.blancatora.orionekox` |
| 设备 | emulator-5554，1080×2400，Android 模拟器 |
| 开始时间 | 2026-10-26 15:32（本地） |
| 结束时间 | 2026-10-26 16:48（本地） |
| 测试者 | 独立测试子代理 B |
| 报告 | `docs/bar-rice-test-B.md` |

### 结论摘要

- 全流程 **未完全通过**。核心单聊、记忆、工作区文件、单聊媒体、设置抽查、终端可用。
- 3 个最严重问题：
  1. **群内 @ 指定智能体不生效**：`@agent_17`（酒吧炒饭B）被 `Yuki Waifu` 抢答，且群内纯文本/长文本/图片消息永远只有 Yuki 回复，B 在群里从未回复（P1-1）。
  2. **本地群发图片失败**：气泡显示缩略图但右下角红色感叹号，文案“发送错误”，聊天列表也出现红叹号（P1-2）。
  3. **Agent 会话消息列表会整屏空白**：完成工作区工具调用或发送文件后，会话内容不再渲染，退出重进仍空白，只有重启 app 才恢复（已复现 2 次，P1-3）。
- 另有 Markdown 未渲染、`PDF档案/TXT档案` 机器翻译、重命名后欢迎语仍显示旧名、设置页最后一行被底栏遮挡等问题（P2/P3，见第 3 节）。
- 测试期间 **模拟器进程两次自行消失**（16:22、16:43），冷启动后继续；app 侧未捕获 FATAL EXCEPTION / ANR，详见第 6 节。

### 测试方法说明（非产品问题）

- 本机 `adb shell input text` 无法输入中文（`NullPointerException: Attempt to get length of null array`）。为了精确输入中文，测试时在宿主机起了一个仅本机可见的 HTML 剪贴板页（`http://localhost:8765`），用 Chrome 打开后 `Ctrl+A / Ctrl+C` 复制，再在 app 内 `KEYCODE_PASTE` 粘贴。未安装任何额外 APK、未改应用数据。
- 新建智能体页要求填入 API Key。为遵守“不读取/抄写密钥”的约束，`酒吧炒饭B` 采用「复制 Yuki Waifu → 改名/改简介/改 AGENTS.md」的方式建立，模型配置为 OpenDesign / `deepseek-v4.1-flash` / `https://amr-link.open-design.ai/v1`。密钥未显示、未读取、未写入报告。
- 首次通过复制 `酒吧炒饭A-copy` 得到的副本没有密钥（发送报 401）；该副本保留为 `barB-nokey`，用于记录 401 行为（见 1.3）。

---

## 1. 全流程逐步结果

### 1.1 基线 — PASS

- 启动 app，聊天列表正常；底部 `聊天 / 联系人 / 设置 / 我的 / 终端` 五个 Tab 可见。
- 版本：`0.8.103` / `1189`（`dumpsys package` 与 Ox 设置页“关于”一致）。
- 启动后执行 `adb logcat -c` 清空日志。
- 截图：`/tmp/bar-rice-B/00-home.png`（基线）、`/tmp/bar-rice-B/60-ox-bottom.png`（版本 0.8.103）。

### 1.2 建立测试 Agent — PASS（方法有偏差，见说明）

- 新建页字段：名称“API 智能体”、备注、技能、模型服务、接口地址、API Key、模型、AGENTS.md；provider 列表包含 OpenDesign。
- 直接新建需要 API Key，且“获取 API Key”按钮会打开 open-design.ai 网站首页（`/tmp/bar-rice-B/20-get-key.png`），不是密钥获取页；为不读取密钥，改用复制 Yuki Waifu 的方式。
- 结果：Agent `酒吧炒饭B`，简介“深夜酒吧炒饭主厨”，AGENTS.md 为
  `你是「酒吧炒饭」深夜主厨。中文回答，先确认忌口，再给 15 分钟单锅配方。`
  模型服务 OpenDesign / deepseek-v4.1-flash，密钥沿用自 Yuki（界面仅显示掩码，未读取）。
- 出现在联系人列表与聊天列表：
  - `/tmp/bar-rice-B/47-working-b-saved.png`（智能体列表）
  - `/tmp/bar-rice-B/48-bworking-chat.png`（聊天页头部）
  - `/tmp/bar-rice-B/140-after-cancel-list.png`（聊天列表）
- 注：另留下 keyless 的 `barB-nokey`，见第 7 节清理对象。

### 1.3 单聊、记忆与工作区

#### 1.3.1 发送首条消息 — PASS

消息原文：`我在酒吧后厨，只有一口锅、隔夜米饭、2 个鸡蛋、葱、酱油、油。做两人份酒吧炒饭。先记住：我不吃香菜。给出步骤。`
- 发送成功，3:50 PM，界面显示“已查看”。
- 证据：`/tmp/bar-rice-B/49-bworking-sent.png`

#### 1.3.2 真实模型回复 — PASS

- 首轮真实回复约 15 秒（15:50:30 发送，15:50:45 完成）。
- 回复先确认“不吃香菜”，给出备料与 0–15 分钟时间轴，不是固定模板；日志显示自动调用了 `remember`、`get_time` 两个工具：
  - `15:50:34.419 executeTool tool=remember done: remembered: 用户不吃香菜…`
  - `15:50:34.421 executeTool tool=get_time done`
  - `15:50:45.545 onRound tools=0 content=900 / finishTurn len=900`
- 证据：`/tmp/bar-rice-B/50-b-reply1.png`

#### 1.3.3 保存配方到工作区文件 — PASS

- 首次发送后 2 秒出现 `⚠ EOFException`（服务端流中断，见 P2-5），重试后正常。
- 重试时弹出权限确认“允许调用工具？”，内容为 `write_file({"path": "bar-fried-rice.md", ...})`，点“允许”后：
  - `15:52:39.629 tool write_file done: wrote 1937 bytes to bar-fried-rice.md`
  - `15:52:41.952 tool list_dir done: f bar-fried-rice.md 1937B`
- 回复中给出相对路径 `bar-fried-rice.md`（Markdown 未渲染，字面显示反引号）。
- 证据：`/tmp/bar-rice-B/51-workspace-request-sent.png`（EOF 错误）、`/tmp/bar-rice-B/53-workspace-reply.png`（权限弹窗）、`/tmp/bar-rice-B/55-workspace-done.png`（保存成功回复）

#### 1.3.4 工作区文件确认 — PASS

- Ox 设置 → 工作区 → 当前 Agent 选择 `酒吧炒饭B`，文件列表显示 `bar-fried-rice.md`，`1.9 KB · 2026-10-26 15:52`。
- 点开预览可见完整炒饭步骤与“忌口：不吃香菜”。
- 入口不在 Agent 会话菜单里，只能从 Ox 设置进入（体验问题，P3）。
- 证据：`/tmp/bar-rice-B/61-workspace-page.png`（空工作区/入口）、`/tmp/bar-rice-B/63-b-workspace.png`（文件行）、`/tmp/bar-rice-B/64-file-preview.png`（内容预览）

#### 1.3.5 记忆回忆 — PASS

- 新开一轮发送：`我忌口什么？只回答忌口。`
- 回复：`不吃香菜。`（3:56 PM）
- 证据：`/tmp/bar-rice-B/72-restart-chat.png`（重启后可见该问答）

#### 1.3.6 返回列表再进入，确认会话仍在（不重启 app） — FAIL

- 从工作区返回会话后，消息列表整屏空白（背景正常、输入框正常、头部在线正常），退出到聊天列表再进入仍空白。
- 只有 `adb shell am force-stop` 后重启 app 才恢复全部消息与记忆。
- 数据没有丢（日志持续 `persistAndShow`，聊天列表有预览），但界面不可读。
- 证据：`/tmp/bar-rice-B/70-reenter-b.png`（空白）、`/tmp/bar-rice-B/72-restart-chat.png`（重启后恢复）

### 1.4 媒体

#### 1.4.1 生成并推送 JPEG — PASS

- Python/PIL 生成 640×480、带黄色 `BAR FRIED RICE` 字样的 JPEG；`adb push` 到 `/sdcard/Download/bar-rice-B.jpg`（17882 B）。
- 证据：`/tmp/bar-rice-B/bar-rice-B.jpg`（宿主）

#### 1.4.2 单聊发送图片 — PASS（但“文件”入口会按文档发送）

- 从附件 → 文件发送：气泡是文档卡片（橙色文件图标，`bar-rice-B.jpg 17.5 KB JPG`），**无缩略图**；日志 `persistAndShow media type=9 mediaClass=TL_messageMediaDocument photoNonNull=false sizes=-1`。
- 触发媒体扫描后，从附件 → 相册选择同一张图发送：气泡显示正常缩略图，右下角双勾，无 X、无 0KB、无下载箭头、无红叹号；Agent 回复能准确描述图片（“深蓝底，黄色字 BAR FRIED RICE，下面一个白色线框的碗形图案”）。
- 证据：`/tmp/bar-rice-B/77-image-sent.png`（文档卡片）、`/tmp/bar-rice-B/96-photo-try6.png`（照片缩略图 + 回复）、`/tmp/bar-rice-B/159-group-photo-result.png`（群内失败对照）

#### 1.4.3 长按图片消息看菜单 — PASS

- 长按图片进入选择态（“1选中”），顶部：复制、转发、删除；底部：回复、转发；未执行删除。
- 证据：`/tmp/bar-rice-B/81-media-menu.png`

### 1.5 本地群、成员与群回复

#### 1.5.1 新建群组 — PASS

- 聊天列表右上三点菜单 → 新建群组，选择 `酒吧炒饭B` 与 `Yuki Waifu`，命名 `酒吧炒饭B测试群`，创建成功。
- 创建后头部显示 `3 位成员, 3 人在线`。
- 证据：`/tmp/bar-rice-B/102-group-members-selected.png`、`/tmp/bar-rice-B/105-group-name-set.png`、`/tmp/bar-rice-B/106-group-created.png`

#### 1.5.2 群资料成员 — PASS

- 资料页：`3 位成员, 3 人在线`；成员行：`TestUser（创建者，在线）`、`Yuki Waifu（本地智能体）`、`酒吧炒饭B（本地智能体）`。
- 证据：`/tmp/bar-rice-B/107-group-profile.png`

#### 1.5.3 群内提问 — PASS（仅 Yuki 回复）

- 发送：`群里确认：两人份炒饭要不要加香菜？各自用一句话回答。`
- Yuki Waifu 回复：“要加～毕竟香菜是绿的，正好是我的幸运色，第七片叶子归我 🌿”，无红叹号。
- `酒吧炒饭B` 未在群里回复（问题 1 的组成部分）。
- 证据：`/tmp/bar-rice-B/109-group-msg-sent.png`、`/tmp/bar-rice-B/110-group-replies.png`

#### 1.5.4 添加 Echo Assistant — PASS

- 群资料 → 添加成员 → 选择 Echo Assistant → 确认弹窗“您确定要添加 Echo Assistant 到 酒吧炒饭B测试群 吗？”（含“向 Echo Assistant 显示最后100条消息”开关）→ 添加。
- 成员数变为 `4 位成员, 4 人在线`，Echo 只有一行。
- 证据：`/tmp/bar-rice-B/112-echo-selected.png`、`/tmp/bar-rice-B/113-echo-added.png`、`/tmp/bar-rice-B/114-echo-member-count.png`

#### 1.5.5 移除 Echo — PASS

- 长按 Echo 成员行 → 菜单含“发送消息/添加成员标记/设为管理员/修改权限/从群组中移除”；点移除后立即恢复 `3 位成员, 3 人在线`，Echo 消失（无二次确认）。
- 证据：`/tmp/bar-rice-B/115-echo-menu.png`、`/tmp/bar-rice-B/116-echo-remove-confirm.png`

#### 1.5.6 长按群消息 — PASS

- 长按 Yuki 回复 → “1选中”工具栏（复制/转发/删除），底部回复/转发；仅查看后返回，未执行删除或举报。
- 证据：`/tmp/bar-rice-B/118-group-msg-menu.png`

### 1.6 设置与扩展抽查

| 项 | 结果 | 证据 |
|---|---|---|
| Ox 设置逐项（模式/语言/本地会员/引导与授权/智能体/MCP/工具与权限/技能库/工作区） | PASS（只读，未改开关） | `/tmp/bar-rice-B/119-ox-settings-top.png`、`/tmp/bar-rice-B/136-language.png`、`/tmp/bar-rice-B/61-workspace-page.png` |
| MCP 服务页 | PASS（只读）：`QA MCP`（HTTP 502）、`DeepWiki`（3 个工具）、`＋ 添加 MCP 服务器` | `/tmp/bar-rice-B/121-mcp.png` |
| 技能库 | PASS：示例技能 SKILL.md 预览可关闭；内容较短，无需滚动 | `/tmp/bar-rice-B/123-skills.png`、`/tmp/bar-rice-B/124-skill-preview.png` |
| 终端 TTY 1/2/3 | PASS：TTY1 `pwd` → `/storage/emulated/0/Download`；TTY2 `echo ok` → `ok`；TTY3 `pwd` → 同路径 | `/tmp/bar-rice-B/127-tty1-pwd2.png`、`/tmp/bar-rice-B/131-tty2-echo.png`、`/tmp/bar-rice-B/132-tty3-pwd.png` |
| 代理与 VPN | PASS（只读）：代理服务器 10.0.2.2:7890，SOCKS5；VPN 未配置。设置页最后一行被底栏遮挡，改从聊天列表菜单进入 | `/tmp/bar-rice-B/135-proxy-vpn.png`、`/tmp/bar-rice-B/180-settings-bottom-crop.png` |
| 翻译/乱码/重复图标/文案 | FAIL（发现多处，见 5 节） | `/tmp/bar-rice-B/136-language.png`、`/tmp/bar-rice-B/166-pdf-after.png` |

备注：“模式”在 Ox 设置中是一个分组标题，其下只有 `Telegram 账号`，不是可进入的独立页面。

### 1.7 崩溃、清理与报告

- `adb logcat -d | grep -E "FATAL EXCEPTION|ANR in io.github.blancatora.orionekox"`：**未发现**（多次检查，包括重启后的缓冲）。
- 未删除任何用户数据；未删除 A 的 Agent/群；A 的 `酒吧炒饭A`、`酒吧炒饭A-copy`、`酒吧炒饭A测试群` 未修改。
- `barB-nokey` 与 `酒吧炒饭B`、`酒吧炒饭B测试群` 保留，供主代理清理（见第 7 节）。

---

## 2. 专项加深：群聊、媒体与工具权限

### 2.1 建群全流程（含取消） — PASS

- 完整走过：选人（勾选/取消勾选）→ 命名 → 创建 → 打开资料；另起一次选人后在返回，聊天列表未出现新群（`/tmp/bar-rice-B/140-after-cancel-list.png`）。
- 本地群表现为本地 dialog（`5550000xx` 段 ID），logcat 未出现 `messages.createChat`/`CreateChat` 相关请求或错误；没有捕获到任何服务器请求证据（也未做抓包，见第 5 节限制）。
- 建群页“已选 2 / 200000”“最高 200000 位成员”与资料页“最多 200,000 位成员”格式不一致（P3）。

### 2.2 群内 @ 两个 Agent、纯文本、图片、长文本 — FAIL

| 子项 | 结果 | 说明/证据 |
|---|---|---|
| 纯文本 | PASS（气泡正常） | Yuki 回复，无红叹号：`/tmp/bar-rice-B/110-group-replies.png` |
| @Yuki Waifu（@agent_11） | PASS | Yuki 回复：“不加，两人份里我那份请留白…”：`/tmp/bar-rice-B/149-mention-yuki-reply.png` |
| @酒吧炒饭B（@agent_17） | **FAIL** | 消息明确 `@agent_17`，实际回复人却是 **Yuki Waifu**：“要加——我那份多一点…seventh heaven…”；B 完全未回复：`/tmp/bar-rice-B/150-mention-b-sent.png`、`/tmp/bar-rice-B/151-mention-b-reply.png` |
| 长文本 | PASS（气泡完整换行） | 约 160 字中文，Yuki 回复确认；B 未回复：`/tmp/bar-rice-B/152-group-long-sent.png`、`/tmp/bar-rice-B/153-group-long-reply.png` |
| 群内图片 | **FAIL** | 相册选图后气泡有缩略图，但右下角 **红色感叹号**，会话内文案“发送错误”；聊天列表该群显示红叹号与“您: 图片”：`/tmp/bar-rice-B/159-group-photo-result.png`、`/tmp/bar-rice-B/162-chatlist.png` |

结论：群内无法按 @ 路由到指定 Agent；且本地群图片发送必现失败。群里的 @ 建议被截图为 `@agent_11` / `@agent_17` 用户名，而不是显示名。

### 2.3 工具权限 auto / ask / deny — PASS

- 默认：`read_file` 为“默认 · 询问”。发送 txt 时弹出的确认框（`/tmp/bar-rice-B/167-txt-permission.png`）文本：
  `允许调用工具？ 该智能体请求执行以下工具： read_file({"path": "inbox/…bar-rice-B.txt"})`，按钮“取消/允许”。
- 设为“自动”：Ox 设置 → 工具与权限 → 当前 Agent 选 `酒吧炒饭B` → `read_file` 点一次变“自动”。再次发送 txt，**无弹窗**，Agent 直接读取并讨论文件内容。
- 设为“禁止”：再点一次变“禁止”。发送同一 txt，**无弹窗**，Agent 回复“这次失败，是权限在那之后变了”，并建议“直接把文字贴进对话”或“去 App 里把文件读取权限打开”；未出现红色的工具错误气泡。
- 已恢复：再点一次 → `默认 · 询问`。
- 证据：`/tmp/bar-rice-B/169-tools-b.png`（B 的工具列表）、`/tmp/bar-rice-B/170-readfile-tap1.png`（自动）、`/tmp/bar-rice-B/172-after-restart2.png`（自动读取成功）、`/tmp/bar-rice-B/173-deny-read.png` / `/tmp/bar-rice-B/174-deny-read-after.png`（禁止后的回复）。

### 2.4 媒体：图片、PDF、文本文件 — PASS（群图片除外）

| 媒体 | 结果 | 证据 |
|---|---|---|
| 图片（单聊） | PASS：相册发送显示缩略图，双勾，无异常标记；Agent 能看图并描述 | `/tmp/bar-rice-B/96-photo-try6.png` |
| PDF（单聊） | PASS：文件卡片 `bar-fried-rice-B.pdf`、`58.0 KB PDF`、双勾；无下载箭头/X/红叹号。Agent 能读结构但读不到图片型 PDF 的正文（P3 限制） | `/tmp/bar-rice-B/166-pdf-after.png` |
| TXT（单聊） | PASS：文件卡片 `bar-rice-B.txt`、`149 B TXT`、双勾；Agent 读到内容并能复述 | `/tmp/bar-rice-B/168-txt-after.png`、`/tmp/bar-rice-B/172-after-restart2.png` |
| 图片（群） | FAIL：红色感叹号 + “发送错误” | `/tmp/bar-rice-B/159-group-photo-result.png` |

补充：通过“文件”入口发 JPEG 会按 document 发送（`mediaClass=TL_messageMediaDocument`，`photoNonNull=false`），无缩略图；通过“相册”入口才是图片消息。建议文档里说明或修正入口行为。

### 2.5 长按自己的消息 / Agent 消息 / 媒体消息 — PASS

- 自己的消息：选择态“1选中”，顶部 **铅笔/复制/转发/删除**，底部回复/转发（`/tmp/bar-rice-B/161-own-msg-menu.png`）。
- Agent 消息：同样有 **铅笔/复制/转发/删除** + 回复/转发（`/tmp/bar-rice-B/160-agent-msg-menu.png`）。
- 媒体消息：顶部只有 **复制/转发/删除**（无铅笔），底部回复/转发（`/tmp/bar-rice-B/81-media-menu.png`、`/tmp/bar-rice-B/97-photo-menu.png`）。
- 另观察到一种不受选择态影响的菜单（回复/复制/转发/置顶/举报/删除），在同一 Agent 长消息上点按出现（见 `/tmp/bar-rice-B/154-group-photo-preview.png` 的 dump 对应时刻）。未执行举报/删除。

### 2.6 重复添加/移除成员 — PASS

- 第二次加 Echo：`4 位成员, 4 人在线`，Echo 仅一行（`/tmp/bar-rice-B/178-add-echo2.png` dump）。
- 第二次移除：`3 位成员, 3 人在线`，成员行为 TestUser/ Yuki/酒吧炒饭B，Echo 消失（`/tmp/bar-rice-B/179-remove-echo2.png`）。
- 计数、在线数、成员行三者一致。

---

## 3. 问题清单（按严重级别）

### P1-1 群内 @ 指定 Agent 不生效，B 在群里从不回复

- 复现：
  1. 进入 `酒吧炒饭B测试群`（成员：TestUser、Yuki Waifu、酒吧炒饭B）。
  2. 输入 `@`，在候选里选择 `酒吧炒饭B`（插入 `@agent_17 `）。
  3. 输入 `用一句话回答：两人份炒饭要不要加香菜？` 发送。
- 期望：只有被 @ 的 `酒吧炒饭B` 回复，或至少由 B 回复。
- 实际：`Yuki Waifu` 回复（“要加——我那份多一点…”），B 全程未回复；纯文本、长文本、@Yuki 场景也都只有 Yuki 回复。
- 证据：`/tmp/bar-rice-B/151-mention-b-reply.png`、`/tmp/bar-rice-B/110-group-replies.png`、`/tmp/bar-rice-B/153-group-long-reply.png`
- 影响：多 Agent 群聊的核心路由失效，用户无法指定 Agent。

### P1-2 本地群发送图片失败（红色感叹号 + 发送错误）

- 复现：群内附件 → 相册 → 选择 JPEG → 发送。
- 期望：与单聊一致，显示缩略图、双勾、无错误。
- 实际：缩略图出现，右下角红色感叹号，会话显示“发送错误”，聊天列表出现红叹号与“您: 图片”；Agent 未收到该图片。
- 证据：`/tmp/bar-rice-B/159-group-photo-result.png`、`/tmp/bar-rice-B/162-chatlist.png`
- 影响：群聊媒体功能不可用。

### P1-3 Agent 会话消息列表整屏空白，需重启 app 才恢复

- 复现 A：单聊完成 `write_file` 工具调用并收到回复 → 去 Ox 设置/工作区 → 返回该会话 → 空白；退出重进仍空白；`force-stop` 重启后恢复。
- 复现 B：单聊连续发送 TXT 文件并触发 `read_file` 后，会话立即空白；重启后恢复。
- 期望：会话消息持续可见。
- 实际：消息区域全空，仅背景/头部/输入框正常；数据仍在（日志 `persistAndShow`、聊天列表有预览）。
- 证据：`/tmp/bar-rice-B/70-reenter-b.png`、`/tmp/bar-rice-B/171-auto-read.png`、`/tmp/bar-rice-B/72-restart-chat.png`、`/tmp/bar-rice-B/172-after-restart2.png`
- 影响：用户以为历史丢失，必须重启 app。

### P2-1 Markdown 未渲染

- Agent 回复中的 `**粗体**`、`` `代码` ``、`## 标题` 全部按字面显示（星号/反引号可见）。
- 证据：`/tmp/bar-rice-B/50-b-reply1.png`、`/tmp/bar-rice-B/55-workspace-done.png`、`/tmp/bar-rice-B/166-pdf-after.png`

### P2-2 机器翻译/术语问题

- 文件气泡类型词：`JPG档案` / `PDF档案` / `TXT档案`（应类似“JPG 文件”“PDF 文件”）。
  - 证据：`/tmp/bar-rice-B/77-image-sent.png`、`/tmp/bar-rice-B/166-pdf-after.png`、`/tmp/bar-rice-B/168-txt-after.png`
- 语言页说明：`翻译按钮将出现在您对一条短信进行一次性监听时。` —— 语义不通（“一次性监听”明显误译）。
  - 证据：`/tmp/bar-rice-B/136-language.png`

### P2-3 复制/重命名后欢迎语保留旧名

- 复制 Yuki 生成的新 Agent 改名为 `酒吧炒饭B` 后，会话首条欢迎语仍显示“你好，我是 🤖 Yuki Waifu · 副本”或“酒吧炒饭A-copy · 副本”。
- 证据：`/tmp/bar-rice-B/48-bworking-chat.png`、`/tmp/bar-rice-B/28-b-chat.png`
- 期望：欢迎语使用当前 Agent 名，或改名时同步更新。

### P2-4 设置页最后一行被底部悬浮栏遮挡

- `设置` 页滚动到底，`代理与 VPN` 行（bounds y≈2251–2313）与底部 Tab 栏（y≈2255–2300）重叠；点击该行会切换 Tab，无法进入。测试改从聊天列表三点菜单进入代理页。
- 证据：`/tmp/bar-rice-B/180-settings-bottom-crop.png`

### P2-5 原始错误直接显示在聊天气泡

- keyless Agent：`⚠ HTTP 401: {"error":{"code":"missing_api_key",...}}`（完整 JSON）。
- EOF 错误：`⚠ EOFException`。
- 群图片：`发送错误`。
- 期望：用户可读提示 + 重试入口；实际为原始异常文本，且无重试按钮。
- 证据：`/tmp/bar-rice-B/41-reply-wait.png`、`/tmp/bar-rice-B/52-workspace-retry.png`、`/tmp/bar-rice-B/159-group-photo-result.png`

### P3-1 工具调用气泡信息过载

- 工具气泡显示完整 JSON 参数（`write_file` 的 1.9KB 正文全部铺在确认弹窗与气泡里），长文本场景非常长；缺少折叠/摘要。
- 证据：`/tmp/bar-rice-B/53-workspace-reply.png`、`/tmp/bar-rice-B/160-agent-msg-menu.png`

### P3-2 `read_file` 读取二进制文件时把原始字节打进 logcat

- 发送 JPEG 后 Agent 调用 `read_file(inbox/…jpg)`，logcat 出现大段二进制乱码（`I/OxBridge`），污染日志并可能泄露内容。
- 证据：logcat 16:00:45.729（`OxBridge` 二进制行）；见第 6 节日志摘录。

### P3-3 文案空格/格式

- `添加1成员`（缺空格）、`已选 2 / 200000`（空格不一致）、`最高 200000 位成员` 与 `最多 200,000 位成员` 不统一。
- 证据：`/tmp/bar-rice-B/113-echo-added.png`、`/tmp/bar-rice-B/102-group-members-selected.png`、`/tmp/bar-rice-B/101-new-group.png`

### P3-4 工作区入口隐蔽

- Agent 会话菜单（静音/搜索/清空/报告/删除）没有“工作区”，只能去 Ox 设置 → 工作区 → 手动切换 Agent。建议在 Agent 资料页增加入口。

### P3-5 其他

- MCP 服务页直接显示 `HTTP 502`，无“重新连接”说明。
- `新建智能体` 没有本地/离线模板，必须手动填 API Key；“获取 API Key”跳到 open-design.ai 首页而非密钥页。
- 图片型 PDF 无法解析正文（Agent 能看图片消息、能读 PDF 结构，但读不到正文），属于能力限制，建议在文档中说明。

---

## 4. 文案 / 翻译 / UI 原生规范问题汇总

| 页面 | 原文/现象 | 建议 |
|---|---|---|
| 文件气泡 | `JPG档案`、`PDF档案`、`TXT档案` | 改为 `JPG 文件` / `PDF 文件` / `文本文件`，或仅显示扩展名 |
| 语言页 | `翻译按钮将出现在您对一条短信进行一次性监听时。` | 重译为“翻译按钮会在您点按某条消息时出现” |
| 文件气泡 | `bar-rice-B.jpg 17.5 KB JPG`（大小与类型顺序） | 统一为 `JPG · 17.5 KB` |
| 群创建/资料 | `添加1成员`、`已选 2 / 200000`、`最高 200000 位成员` vs `最多 200,000 位成员` | 补空格/统一千分位 |
| Agent 消息 | `**粗体**`、`` `路径` `` 字面显示 | 渲染 Markdown 或转义 |
| 设置 | 最后一行被底栏遮挡 | 增加列表底部 padding |
| 代理与 VPN | 显示 `10.0.2.2:7890`、`HTTP 502` 等原始值 | 错误态提供人话说明 |
| 群内 @ | 插入 `@agent_11`/`@agent_17` 用户名，不显示显示名 | 在气泡里显示可读显示名 |

---

## 5. 未执行项及原因

| 项 | 原因 |
|---|---|
| 直接“新建智能体”并手填 Provider/Key | 不想读取/抄写密钥；新建页必需 API Key。改用复制 Yuki 的配置 |
| 群内图片“成功送达”验证 | 群图片必现“发送错误”，无法完成 |
| 群内两个 Agent 都回复 | 实际只有 Yuki 回复，B 从不回复；已按 FAIL 记录 |
| 网络抓包证明“本地群不发服务器请求” | 未做抓包；仅依据 logcat 无 `createChat` 请求/错误、群为本地 dialog 判断 |
| SKILL.md 长内容滚动 | 示例技能内容过短，一屏显示完；只验证了预览与关闭 |
| 群内“图片”的下载状态卡片 | 发送失败，无法进入下载态 |
| 视频/语音/位置/联系人等附件 | 不在本轮清单内 |
| 真实 Telegram 联系人消息 | 按约束禁止 |

---

## 6. 崩溃、ANR 与环境异常

- **app 崩溃/ANR**：反复执行
  `adb logcat -d | grep -E "FATAL EXCEPTION|ANR in io.github.blancatora.orionekox"`
  未命中；未发现 Java 崩溃或 ANR。
- **模拟器异常（非 app FATAL）**：
  - 16:22 左右 `adb` 报 `device 'emulator-5554' not found`，宿主无 qemu 进程；冷启动 AVD 后继续。
  - 16:43 左右再次 `error: device offline`，宿主无 qemu 进程；再次冷启动。
  - 模拟器日志（`/tmp/bar-rice-B/emulator.log`、`emulator2.log`）末尾为 SwiftShader/Vulkan `UNSUPPORTED` 警告与 Chromium `VkDevice` 创建；宿主内存充足（30GB，约 12GB 空闲），非 OOM。
  - 这两次中断不计为 app 的 P0；但会干扰测试，建议主代理用更稳定的 GPU 模式复测，并观察 app 内 WebView/页面（“获取 API Key”会打开内置浏览器）是否触发驱动问题。
- **应用内异常日志摘录**（均为业务错误，不是崩溃）：
  - `15:43:56.173 I/OxBridge: onError dialog=555000016: HTTP 401: {"error":{"code":"missing_api_key"...}}`
  - `15:51:37.970 I/OxBridge: onError dialog=555000017: EOFException`
  - `15:52:39.629 I/OxBridge: tool write_file done: wrote 1937 bytes to bar-fried-rice.md`
  - `15:52:41.952 I/OxBridge: tool list_dir done: f bar-fried-rice.md 1937B`
  - `16:00:45.729 I/OxBridge: <read_file 读取 JPEG 后输出的大段二进制乱码>`

---

## 7. 遗留测试对象（供主代理清理，未删除）

- Agent：
  - `酒吧炒饭B`（有效，OpenDesign/deepseek-v4.1-flash，AGENTS.md 为酒吧炒饭提示词）
  - `barB-nokey`（keyless 副本，用于 401 复现；建议删除）
- 群：`酒吧炒饭B测试群`（TestUser、Yuki Waifu、酒吧炒饭B；Echo 已移除）
- 单聊文件与工作区：
  - `酒吧炒饭B` 工作区：`bar-fried-rice.md`（1937 B）、`inbox/1793001589003_bar-rice-B.jpg`、`inbox/1793003655325_bar-fried-rice-B.pdf`、多个 `inbox/1793…_bar-rice-B.txt` 副本
  - 上传到设备的测试文件：`/sdcard/Download/bar-rice-B.jpg`、`bar-rice-B.txt`、`bar-fried-rice-B.pdf`
- 临时工具：`/tmp/bar-rice-B/`（截图与图片）；宿主机 `python3 -m http.server 8765`（报告写完可关闭）
- 权限：`酒吧炒饭B` 的 `read_file` 已恢复为“默认 · 询问”。
- 未改动：Yuki Waifu 原 Agent；A 的 `酒吧炒饭A`、`酒吧炒饭A-copy`、`酒吧炒饭A测试群` 及其内容。

---

## 8. 最终结论

**未通过全流程。**

- 单聊、记忆、工作区、单聊媒体（相册图片/PDF/TXT）、设置抽查、终端、建群/成员管理、工具权限 auto/ask/deny 均可用。
- 阻塞项集中在群聊与稳定性：
  1. 群内 @ 路由错误（指定 B 却由 Yuki 回复，B 从不回复）——P1；
  2. 本地群发图片必现“发送错误”红叹号——P1；
  3. Agent 会话在工具/文件交互后整屏空白，需重启 app——P1。
- 修好这 3 个 P1 后再复测；P2 的 Markdown 渲染、术语翻译、欢迎语旧名与设置页遮挡建议同批修复。
