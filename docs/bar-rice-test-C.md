# 酒吧炒饭全流程测试报告（C）

- 版本：0.8.103（versionCode 1189），release 包 `io.github.blancatora.orionekox`
- 设备：emulator-5554，Android 14（SDK 34），1080×2400
- 开始时间：2026-10-26 16:52:32 CST
- 结束时间：2026-10-26 18:08 CST
- 测试方式：adb / uiautomator dump / screencap / logcat；未重装、未构建、未提交代码
- 结论：**全流程不通过**。单聊、记忆、工作区、图片、设置、终端、工作流主路径可用；多智能体群与“新建智能体后的状态一致性”存在 P1 问题。

---

## 0. 结论摘要

- 通过的核心能力：真实模型回复、忌口记忆、`write_file` 工具与权限确认、工作区文件、图片发送与视觉识别、MCP/技能/终端/代理 VPN 只读浏览、工作流创建/立即运行/通知/运行记录。
- 失败/严重问题：
  1. **群创建静默丢掉刚创建/复制的本地智能体**：勾选 C+Yuki，创建后群内只有 Yuki，成员数 2（创建者+Yuki），C 无成员行、无回复。
  2. **群 AI 分发只识别 1 个智能体**：重启后手动把 C 加进群，界面显示 3 位成员且 C 有成员行，但群消息仍只有 Yuki 回复；logcat 明确 `ensureGroupParticipants ... agents=1`。
  3. **新建智能体后从聊天列表进入会话历史为空**，从智能体管理页进入却有完整历史；重开会话时输入框被“开始Bot”按钮替代，需额外点击并触发一条新欢迎语。
  4. **OxLinux 引导第 6 步布局错位/裁切**，标题被截断，且已登录状态仍显示“登录 Telegram”大按钮。

---

## 1. 环境、基线与时间线

### 1.1 基线（PASS）

- 启动后底部五个 Tab 可见：聊天、联系人、设置、我的、终端。
- `adb logcat -c` 已清空日志。
- 基线截图：`/tmp/bar-rice-C/00-home.png`。
- 版本确认：versionName=0.8.103，versionCode=1189；`lastUpdateTime=2026-10-26 13:52:07`。

### 1.2 模拟器掉线时间线（环境问题，不计入 app 崩溃）

| 时间 | 现象 | 处理 |
|---|---|---|
| 17:47:35 | `adb: no devices/emulators found`，emulator 进程消失 | 立即记录；用 `-gpu off -no-window -no-audio -no-boot-anim -no-snapshot-load` 冷启动；17:48:27 boot=1 |
| 约 17:57:55 | 再次 `device offline` / 进程消失 | 同上冷启动；17:59:04 boot=1 |
| 约 18:04:20 | 第三次 `device offline` | 同上冷启动；18:05:05 boot=1 |

- 每次冷启动后应用数据、聊天、群、智能体均保留；release 包版本不变。
- emulator 日志末尾只有 Vulkan/Chromium create/destroy 记录，无明确 qemu 报错；宿主侧未发现 app 的 FATAL/ANR。
- 三次掉线均发生在使用 Chrome 作为中文剪贴板中转之后，疑似模拟器 GPU/Chromium 负载导致；**不能算作 app 崩溃**。

---

## 2. 全流程逐步结果

### 1.2 建立测试 Agent —— PARTIAL

| 子步骤 | 结果 | 证据 |
|---|---|---|
| 进入联系人 → 智能体，打开“新建智能体” | PASS | `/tmp/bar-rice-C/09-new-agent.png` |
| 选择 Provider/模型 | PARTIAL | 选择 OpenDesign / deepseek-v4.1-flash 后 API Key 为空，测试连接返回 `连接失败 · HTTP 401`；`/tmp/bar-rice-C/16-test-connection.png` |
| 保存新 Agent | PARTIAL | 未从零保存：因不能读取/抄写密钥，改为长按 Yuki Waifu → “复制”，继承其可用 Provider 配置（密钥保持掩码，未打开） |
| 重命名、简介、系统提示词 | PASS | 名称 `酒吧炒饭C`；简介 `深夜酒吧炒饭主厨`；AGENTS.md=`你是「酒吧炒饭」深夜主厨。中文回答，先确认忌口，再给 15 分钟单锅配方。`；`/tmp/bar-rice-C/21-agent-saved.png` |
| 出现在智能体列表 | PASS | `/tmp/bar-rice-C/21-agent-saved.png` |
| 出现在聊天列表 | PASS（发首条消息后） | `/tmp/bar-rice-C/44-chatlist-with-C.png`、`/tmp/bar-rice-C/53-chatlist2.png` |
| 出现在联系人列表 | FAIL（首次） | 创建后联系人列表不含 C；`/tmp/bar-rice-C/22-contacts-with-C.png`；重启 app 后才出现（17:57 联系人 dump） |

说明：`+ 新建智能体` 路径已打开并验证到 401；因不能读取现有 Key，“复制 Yuki”是保证使用已配置可用模型的折中方案。副本的初始欢迎语仍带旧名“Yuki Waifu · 副本”（见问题 P2-3）。

### 1.3 单聊、记忆与工作区

| 子步骤 | 结果 | 证据 |
|---|---|---|
| 发送配方请求 | PASS | 17:07:51 发送；约 20 秒内收到真实中文长回复，先确认“不吃香菜”，再给 15 分钟单锅配方；`/tmp/bar-rice-C/28-msg1-reply.png` |
| 工具权限提示 | PASS | 弹窗标题“允许调用工具？”，正文含 `write_file({"path":"bar-fried-rice.md","content":...})`，按钮 取消/允许；选择“允许”；`/tmp/bar-rice-C/33-tool-permission.png` |
| 模型告知保存路径 | PASS | 回复“已保存 ✅ 路径：`bar-fried-rice.md`（工作区根目录，1743 字节）”；`/tmp/bar-rice-C/35-msg2-reply.png` |
| 工作区文件存在且内容正确 | PASS | 工作区切到 C，显示 `bar-fried-rice.md · 1.7 KB · 2026-10-26 17:10`；预览含忌口、备料、8 步步骤；`/tmp/bar-rice-C/42-C-workspace.png`、`/tmp/bar-rice-C/43-file-preview.png` |
| 新轮次记忆“忌口” | PASS | 回复仅“不吃香菜（芫荽）。”；`/tmp/bar-rice-C/52-msg3-reply.png` |
| 返回聊天列表再进入，确认会话/记忆仍在 | **FAIL** | 从聊天列表点 C：会话区空白，无历史；但列表预览显示“不吃”，说明消息存在；`/tmp/bar-rice-C/46-C-chat-reopen-wait.png`、`/tmp/bar-rice-C/56-C-from-chatlist2.png` |
| 从智能体管理页进入 C | PARTIAL | 历史完整可见，但底部输入框变成“开始Bot”按钮；点击后才恢复输入并额外发出一条欢迎语；`/tmp/bar-rice-C/48-C-from-agent-list.png`、`/tmp/bar-rice-C/49-after-startbot.png`、`/tmp/bar-rice-C/50-startbot-reply.png` |

### 1.4 媒体

| 子步骤 | 结果 | 证据 |
|---|---|---|
| 生成 640×480 JPEG 并 push | PASS | 本机 PIL 生成，含 `BAR FRIED RICE` 与 `OrionekoX test C`；`/tmp/bar-rice-C/bar-rice-C.jpg`，设备路径 `/sdcard/Download/bar-rice-C.jpg` |
| 发送图片 | PASS | 17:20 发送，气泡显示正常缩略图与双勾，无 X/0KB/下载箭头/红叹号；`/tmp/bar-rice-C/66-media-sent3.png` |
| 模型视觉识别 | PASS | 模型准确描述“深蓝紫底、粉色描边、BAR/FRIED/RICE、左下角 OrionekoX test C” |
| 长按图片菜单 | PASS（有限） | 进入选择模式：顶部 编辑/转发/删除，底部 回复/转发，另有表情反应；图片消息无“复制”；仅查看后返回，未删除；`/tmp/bar-rice-C/67-image-longpress.png` |

### 1.5 本地群、成员与群回复

| 子步骤 | 结果 | 证据 |
|---|---|---|
| 新建群并选择 C 与 Yuki | PASS（选择阶段） | 出现“已选 2 / 200000”，摘要“TestUser、Yuki Waifu 和 酒吧炒饭C”；`/tmp/bar-rice-C/75-group-selected2.png` |
| 群创建结果 | **FAIL** | 群资料页显示“2 位成员, 2 人在线”，成员行只有 TestUser（创建者）与 Yuki；C 被静默丢弃；`/tmp/bar-rice-C/80-group-profile.png`、`/tmp/bar-rice-C/81-group-members.png` |
| 群消息与回复 | **FAIL** | 17:26 发送“群里确认…”，仅 Yuki 回复，C 无回复；无红叹号；`/tmp/bar-rice-C/83-group-replies.png` |
| 添加 Echo | PASS | 确认弹窗“您确定要添加 🤖 Echo Assistant 到 酒吧炒饭C测试群 吗？/ 向 Echo Assistant 显示最后100条消息”；确认后 3 位成员，Echo 仅一行；`/tmp/bar-rice-C/87-after-add-echo.png`、`/tmp/bar-rice-C/88-echo-added.png` |
| 移除 Echo | PASS | 长按 → 从群组中移除，立即恢复 2 位成员、Echo 消失（无二次确认）；`/tmp/bar-rice-C/90-remove-confirm.png`、`/tmp/bar-rice-C/91-after-remove-echo.png` |
| 长按群消息菜单 | PASS | 顶部出现 复制/转发/删除，底部 回复/转发；仅查看后返回；`/tmp/bar-rice-C/92-group-msg-longpress.png` |
| 重启后手动把 C 加回群 | PASS | 显示 3 位成员，成员行包含 C；`/tmp/bar-rice-C/163-C-added.png` |
| 加回后再次群发 | **FAIL** | 18:02 再次发送同样问题，仍只有 Yuki 回复；C 无回复。logcat：`17:59:37.212 I OxBridge: ensureGroupParticipants chat=5000189030 agents=1`；`/tmp/bar-rice-C/167-group-replies2.png` |

结论：群创建时 C 的 peer/联系人注册尚未完成，UI 勾选成功但实际未入群；重启后手动加入可恢复成员行。但即使 C 是正式成员，AI 群分发仍只认为 1 个 agent（`agents=1`），C 不会回复。

### 1.6 设置与扩展抽查

| 子步骤 | 结果 | 证据 |
|---|---|---|
| Ox 设置逐项查看 | PASS | 模式、语言、本地会员、引导与授权、智能体、MCP、工具与权限、技能库、工作区、工作流、OxLinux、终端设置、版本均可见；`/tmp/bar-rice-C/94-ox-settings-top.png`、`/tmp/bar-rice-C/95-mode-page.png` |
| MCP 服务列表/详情/新增 | PASS（查看） | 列表：QA MCP（HTTP 502，环境本地服务未启）、DeepWiki（3 个工具）；新增弹窗仅“名称 + Streamable HTTP 端点”；`/tmp/bar-rice-C/100-mcp.png`、`/tmp/bar-rice-C/101-mcp-add.png` |
| 技能库示例技能 | PASS（查看） | 只有“示例技能 / 测试 Operit SKILL.md 扫描”；弹窗显示 YAML frontmatter（name/description），内容过短无可滚动区域；`/tmp/bar-rice-C/106-skills.png`、`/tmp/bar-rice-C/107-skill-preview.png` |
| 终端 TTY 1/2/3 | PASS | TTY1 `pwd` → `/storage/emulated/0/Download`；TTY2 `echo ok` → `ok`；TTY3 `ls` → Download 文件列表；切回各 TTY 输出不串台；`/tmp/bar-rice-C/130-tty1-pwd3.png`、`/tmp/bar-rice-C/132-tty2-echo-ok.png`、`/tmp/bar-rice-C/133-tty3-ls.png` |
| 代理与 VPN 只读查看 | PASS | 代理：使用代理开，10.0.2.2:7890，SOCKS5；VPN：关闭，可粘贴 vpn/vless/hysteria2/ss 配置；未修改；`/tmp/bar-rice-C/117-proxy-vpn.png` |
| 机翻/乱码/重复图标/文案 | FAIL（多项，见第 5 节） | 典型：语言页“一次性监听”、重复“简体中文”、设备页 `**Telegram Web**` |

### 1.7 崩溃、清理与报告

- `adb logcat -d | grep -E "FATAL EXCEPTION|ANR in io.github.blancatora.orionekox"`：在 16:52–18:05 的多次检查中**无匹配**。
- 三次 `device offline` 为 emulator 进程消失，不是 app 崩溃；冷启动后 app 数据完整。
- 未删除用户数据、未 `pm clear`、未修改真实账号设置；未向任何真实联系人发送消息。
- 测试对象保留，供主代理清理：
  - 智能体：`酒吧炒饭C`（Yuki 副本，username `@agent_18`）
  - 群：`酒吧炒饭C测试群`（当前 3 位成员：TestUser 创建者、Yuki Waifu、酒吧炒饭C）
  - 工作流：`酒吧炒饭C工作流`（已删除）
  - 设备文件：`/sdcard/Download/bar-rice-C.jpg`
  - 截图：`/tmp/bar-rice-C/`

---

## 3. 专项加深结果

### 3.1 设置与 Ox 设置逐行点开 —— PARTIAL

| 页面 | 结果 |
|---|---|
| 账户 | 打开正常（含电话/用户名/生日，截图含隐私，不附；未修改） |
| Ox 设置 → 模式 → “Telegram 账号” | 点击无反应，疑似死行；`/tmp/bar-rice-C/95-mode-page.png` |
| 语言 | 打开正常；翻译开关与语言列表见问题 P2-1、P2-2 |
| 本地会员 | 开关为开；按要求未拨动 |
| 重新运行引导 | **未执行**：会重跑引导并有状态变更风险 |
| 开源协议 | 打开 GitHub LICENSING.md 正常 |
| 智能体 / MCP / 工具与权限 / 技能库 / 工作区 / 工作流 | 均打开正常 |
| 从 Operit / Operit2 导入 | 单击立即执行并弹“导入完成：角色卡 2（Sakura Waifu、Yuki Waifu）”，无预览/确认；导入后智能体列表未产生重复项；`/tmp/bar-rice-C/104-import-page.png` |
| OxLinux | 打开的是引导第 6/6 步，布局错位且显示“登录 Telegram”；`/tmp/bar-rice-C/151-oxlinux.png`、`/tmp/bar-rice-C/152-oxlinux-wait.png` |
| 终端设置（颜色与字号） | 只读查看：5 套配色（Ox/经典/琥珀/浅色/Ubuntu）与 12/14/16/18 sp；未修改；`/tmp/bar-rice-C/150-terminal-settings.png` |
| 版本 / 代码仓库 | 版本 0.8.103 正确；代码仓库入口可打开 GitHub |
| 主设置：聊天设置/隐私/通知/数据/文件夹/设备/省电/语言/代理 VPN | 均正常打开，无空白页；`/tmp/bar-rice-C/110-chat-settings.png`、`/tmp/bar-rice-C/112-notifications.png`、`/tmp/bar-rice-C/113-data-storage.png`、`/tmp/bar-rice-C/114-folders.png`、`/tmp/bar-rice-C/116-power-saving.png`、`/tmp/bar-rice-C/117-proxy-vpn.png` |

### 3.2 MCP 导入入口 —— FAIL（缺失）

- 商城、服务器列表、添加弹窗均无 `mcpServers` JSON 批量导入入口。
- 添加弹窗只有名称与 URL 两个字段；`/tmp/bar-rice-C/101-mcp-add.png`。
- 商城可一键安装远程 MCP（DeepWiki、Context7），本地 STDIO 插件只可查看/复制配置。

### 3.3 技能库 —— PARTIAL

- 可只读浏览 SKILL.md，YAML frontmatter 正确显示。
- 无导入 SKILL.md/技能包入口；示例内容过短，无法验证长文滚动；关闭“确定”正常。

### 3.4 终端 —— PASS

- 三个 TTY 分别执行 `pwd`、`echo ok`、`ls`，输出正确且互不串台。
- 颜色/字号页只读查看正常；未见异常配色或字号错乱。

### 3.5 工作流 —— PASS

- 新建 `酒吧炒饭C工作流`，目标 `酒吧炒饭C`，计划 `每 15 分钟 · 下次 18:08`；`/tmp/bar-rice-C/139-workflow-saved.png`。
- 长按 → 立即运行（17:54:03）；logcat：
  `10-26 17:53:58.177 I ActivityManager: Background started FGS ... act=org.telegram.ox.action.RUN_WORKFLOW ...`
- 通知栏出现：`工作流完成：酒吧炒饭C工作流` / `酒吧炒饭C测试提醒：今夜开火记得不放香菜，只用葱花收尾。`；`/tmp/bar-rice-C/147-notifications2.png`。
- 运行记录：`10-26 17:54 · 成功 · 酒吧炒饭C测试提醒：今夜开火记得不放香菜，只用葱花收尾。`；`/tmp/bar-rice-C/148-run-records.png`。
- 测试后已删除；删除无二次确认，列表随即为空；`/tmp/bar-rice-C/149-workflow-delete-confirm.png`。

### 3.6 代理与 VPN —— PASS

- 仅只读查看代理（开、10.0.2.2:7890、SOCKS5）与 VPN（关、无配置、添加入口），未改动。
- 观察：通知栏出现“VPN 已开启——保持 OrionekoX 连接”，而 VPN 设置为关闭；见问题 P2-6。

### 3.7 中文审计 —— 见第 5 节

---

## 4. 问题清单（复现、期望/实际、严重级别）

### P1-1 群创建静默丢弃刚创建的本地智能体

- 复现：复制 Yuki 得到 C → 立即新建群，勾选 C 与 Yuki（界面显示已选 2）→ 创建。
- 期望：群成员为 TestUser + Yuki + C（3 人），C 有成员行并能回复。
- 实际：群显示“2 位成员”（TestUser + Yuki），C 无成员行；群消息只有 Yuki 回复。
- 重启 app 后 C 出现在联系人列表，手动“添加成员”可成功加入并显示成员行，说明是创建时的注册/缓存竞态。
- 证据：`/tmp/bar-rice-C/75-group-selected2.png`、`/tmp/bar-rice-C/80-group-profile.png`、`/tmp/bar-rice-C/81-group-members.png`、`/tmp/bar-rice-C/83-group-replies.png`。

### P1-2 群 AI 分发只识别 1 个智能体

- 复现：重启后把 C 加回群（3 位成员、C 有成员行）→ 群内发送问题。
- 期望：C 与 Yuki 均回复（或至少 C 能回复）。
- 实际：仅 Yuki 回复；C 始终不回复。
- logcat：`10-26 17:59:37.212 I OxBridge: ensureGroupParticipants chat=5000189030 agents=1`。
- 证据：`/tmp/bar-rice-C/163-C-added.png`、`/tmp/bar-rice-C/167-group-replies2.png`。

### P1-3 新建 Agent 的会话入口不一致、历史为空、输入框被“开始Bot”替代

- 复现：新建/复制 C 并发消息后，返回聊天列表 → 点 C 会话。
- 期望：显示已有会话历史与正常输入框。
- 实际：会话区空白（列表预览仍有“不吃”）；从智能体管理页进入才有完整历史，但底部为“开始Bot”按钮，点击后才恢复输入并额外发送一条欢迎语。
- 说明：重启后联系人注册恢复，但未再验证聊天列表入口（模拟器第三次掉线）。
- 证据：`/tmp/bar-rice-C/46-C-chat-reopen-wait.png`、`/tmp/bar-rice-C/56-C-from-chatlist2.png`、`/tmp/bar-rice-C/48-C-from-agent-list.png`、`/tmp/bar-rice-C/49-after-startbot.png`。

### P1-4 OxLinux 引导第 6 步布局错位、标题裁切、错误登录按钮

- 复现：Ox 设置 → OxLinux（配置你的 OxLinux 容器环境）。
- 期望：完整的容器配置页。
- 实际：页面整体右移，左侧权限栏被裁切；标题显示“配置你的 OxLinux 容…”；底部出现大号“登录 Telegram”按钮（当前已登录）。
- 证据：`/tmp/bar-rice-C/151-oxlinux.png`、`/tmp/bar-rice-C/152-oxlinux-wait.png`。

### P2-1 语言页机翻与重复条目

- “翻译按钮将出现在您对一条短信进行一次性监听时。”——“一次性监听”语义错误（应为长按/单条消息场景）。
- 语言列表出现两个完全相同的“简体中文 / Chinese (Simplified)”。
- 证据：`/tmp/bar-rice-C/98-language2.png`。

### P2-2 设备页原始 Markdown 泄漏

- 原文：“在 Telegram Desktop 或 **Telegram Web** 中 打开二维码。”，星号未渲染。
- 证据：`/tmp/bar-rice-C/115-devices.png`（该页含真实设备会话信息，报告不展开）。

### P2-3 副本欢迎语残留旧名

- 复制 Yuki 并改名为“酒吧炒饭C”后，首条欢迎语仍为“你好，我是 🤖 Yuki Waifu · 副本。直接发消息即可开始对话。”
- 期望：欢迎语使用当前名称，或复制后重建欢迎语。
- 证据：`/tmp/bar-rice-C/57-C-chat-media.png`。

### P2-4 联系人列表刷新滞后

- 新建/复制 C 后，智能体列表和聊天列表可见，但联系人列表不显示；重启 app 后出现。
- 证据：`/tmp/bar-rice-C/22-contacts-with-C.png`（缺失）与 17:57 联系人 dump（出现）。

### P2-5 MCP 缺少 `mcpServers` JSON 批量导入

- 添加 MCP 只有“名称 + URL”；商城也不能导入 JSON。Kelivo/RikkaHub 常见迁移入口缺失。
- 证据：`/tmp/bar-rice-C/101-mcp-add.png`。

### P2-6 VPN 通知与设置状态不一致

- VPN 设置为“启用 VPN 关闭”，通知栏仍出现“VPN 已开启——保持 OrionekoX 连接”。
- 证据：`/tmp/bar-rice-C/147-notifications2.png`、`/tmp/bar-rice-C/117-proxy-vpn.png`。

### P2-7 “从 Operit / Operit2 导入”单击立即执行，无预览/确认

- 期望：先展示将导入的角色卡/MCP/记忆数量并确认。
- 实际：单击即导入 2 张角色卡并弹出结果；导入为 upsert，未产生重复智能体。
- 证据：`/tmp/bar-rice-C/104-import-page.png`。

### P2-8 工作流删除无确认

- 长按 → 删除，立即删除且无确认；误触风险。
- 证据：`/tmp/bar-rice-C/149-workflow-delete-confirm.png`。

### P2-9 “模式”行不可点击

- Ox 设置 → 模式 → “Telegram 账号”点击无响应；可能是纯展示行，但外观与可点击行一致。
- 证据：`/tmp/bar-rice-C/95-mode-page.png`。

### P2-10 智能体回复中的 Markdown 未渲染

- 回复里直接显示 `**不吃香菜**`、`## 备料` 等星号/井号；影响可读性。可能是模型输出约定问题，但用户侧确实看到原始标记。
- 证据：`/tmp/bar-rice-C/28-msg1-reply.png`、`/tmp/bar-rice-C/66-media-sent3.png`。

### P3-1 聊天列表相机悬浮按钮无障碍标签为“邮编”

- uiautomator：`content-desc="邮编"`；与相机/快速拍摄功能不符。
- 证据：基线 UI dump、`/tmp/bar-rice-C/00-home.png`。

### P3-2 时间戳中英混排

- “于 5:07 PM 收到”“于 5:20 PM 发送, 已查看”，中文界面使用英文 AM/PM 与英文逗号。
- 证据：多条消息截图，如 `/tmp/bar-rice-C/28-msg1-reply.png`。

### P3-3 页面标题不一致

- 设置行“聊天设置”打开的页面标题为“对话设置”；“通知”页标题为“通知和声音”；“聊天文件夹”页标题为“文件夹”。
- 证据：`/tmp/bar-rice-C/110-chat-settings.png`、`/tmp/bar-rice-C/112-notifications.png`、`/tmp/bar-rice-C/114-folders.png`。

---

## 5. 文案 / 翻译 / UI 原生规范审计

| 位置 | 原文 | 问题 |
|---|---|---|
| 语言页 | “翻译按钮将出现在您对一条短信进行一次性监听时。” | 机翻，语义错误；“一次性监听”不符合中文习惯 |
| 语言页 | 两个“简体中文 / Chinese (Simplified)” | 重复条目 |
| 设备页 | “在 Telegram Desktop 或 **Telegram Web** 中 打开二维码。” | Markdown 未渲染，星号外露 |
| 聊天页 | “开始Bot” | 中英混排，且替代了消息输入框 |
| 消息时间 | “于 5:07 PM 收到” | 中文界面混用 12 小时制英文 AM/PM |
| 设置页 | 行名“聊天设置” vs 页标题“对话设置” | 命名不一致 |
| OxLinux 引导 | “配置你的 OxLinux 容…” | 标题被裁切，页面横向错位 |
| 引导底部 | “登录 Telegram” | 已登录状态仍显示登录按钮 |
| 聊天列表相机 FAB | 无障碍标签“邮编” | 标签与功能无关 |
| 智能体回复 | `**不吃香菜**`、`## 备料` | Markdown 标记直接显示 |
| 群聊 | 顶部常驻“添加成员”横幅 | 长期遮挡聊天区，需手动关闭 |
| 工作流 | “系统未授予精确闹钟权限…” | 文案本身准确，但属于权限缺失提示，可接受 |

---

## 6. 未执行项及原因

1. **未从零新建 Agent 并保存**：OpenDesign Provider 的 API Key 字段为空，测试连接 401；按约束不得读取/抄写现有 Key，改用 Yuki 副本。已保留 401 证据。
2. **未执行“重新运行引导”**：会重跑引导并可能改变现有配置，只读测试不执行。
3. **未切换开关/单选**：本地会员、代理开关、VPN 开关、工具权限、语言单选、终端配色/字号均只读查看。
4. **未创建“一次性”工作流**：编辑器只提供 手动/每15分钟/每小时/每6小时/每24小时/每天指定时间；使用“每 15 分钟”并立即运行后删除。
5. **未测试 MCP 启用/停用、添加服务器**：会产生配置变更；只验证入口与字段。
6. **未测试技能导入**：UI 无导入入口。
7. **未验证 C 聊天列表入口在重启后的表现**：第三次模拟器掉线发生在该操作点击瞬间；此前未重启时已确认历史为空。
8. **未测试真实 Telegram 联系人/群**：遵守约束，全部对象为本地智能体/仅本地成员群。

---

## 7. 证据索引（关键截图）

- 基线：`/tmp/bar-rice-C/00-home.png`
- 新建/复制智能体：`/tmp/bar-rice-C/09-new-agent.png`、`/tmp/bar-rice-C/16-test-connection.png`、`/tmp/bar-rice-C/21-agent-saved.png`
- 单聊真实回复：`/tmp/bar-rice-C/28-msg1-reply.png`
- 工具权限：`/tmp/bar-rice-C/33-tool-permission.png`
- 工作区文件：`/tmp/bar-rice-C/42-C-workspace.png`、`/tmp/bar-rice-C/43-file-preview.png`
- 记忆：`/tmp/bar-rice-C/52-msg3-reply.png`
- 会话异常：`/tmp/bar-rice-C/46-C-chat-reopen-wait.png`、`/tmp/bar-rice-C/56-C-from-chatlist2.png`、`/tmp/bar-rice-C/48-C-from-agent-list.png`
- 图片发送：`/tmp/bar-rice-C/66-media-sent3.png`、`/tmp/bar-rice-C/67-image-longpress.png`
- 群创建/成员：`/tmp/bar-rice-C/75-group-selected2.png`、`/tmp/bar-rice-C/80-group-profile.png`、`/tmp/bar-rice-C/81-group-members.png`
- 群回复：`/tmp/bar-rice-C/83-group-replies.png`、`/tmp/bar-rice-C/167-group-replies2.png`、`/tmp/bar-rice-C/163-C-added.png`
- 设置：`/tmp/bar-rice-C/94-ox-settings-top.png`、`/tmp/bar-rice-C/95-mode-page.png`、`/tmp/bar-rice-C/98-language2.png`、`/tmp/bar-rice-C/110-chat-settings.png`、`/tmp/bar-rice-C/117-proxy-vpn.png`
- MCP/技能：`/tmp/bar-rice-C/100-mcp.png`、`/tmp/bar-rice-C/101-mcp-add.png`、`/tmp/bar-rice-C/107-skill-preview.png`
- 终端：`/tmp/bar-rice-C/130-tty1-pwd3.png`、`/tmp/bar-rice-C/132-tty2-echo-ok.png`、`/tmp/bar-rice-C/133-tty3-ls.png`、`/tmp/bar-rice-C/150-terminal-settings.png`
- 工作流：`/tmp/bar-rice-C/139-workflow-saved.png`、`/tmp/bar-rice-C/145-workflow-run-now.png`、`/tmp/bar-rice-C/147-notifications2.png`、`/tmp/bar-rice-C/148-run-records.png`
- OxLinux 布局：`/tmp/bar-rice-C/151-oxlinux.png`、`/tmp/bar-rice-C/152-oxlinux-wait.png`
- 冷启动后数据保留：`/tmp/bar-rice-C/134-after-reboot.png`、`/tmp/bar-rice-C/158-after-restart2.png`

---

## 8. 最终结论

**全流程不通过（FAIL）。**

- 单智能体主链路（真实模型、记忆、工具权限、工作区、图片、设置、终端、工作流）基本可用且证据完整。
- 阻断性问题集中在“新建/复制智能体后的 peer 注册一致性”和“多智能体群”：
  - 群创建会静默丢失刚创建的智能体；
  - 即使手动加回，群 AI 分发仍只有 1 个 agent；
  - 聊天列表入口出现空历史，重开会话被“开始Bot”按钮阻塞；
  - OxLinux 引导页布局损坏。
- 建议主代理按第 4 节 P1 问题优先复查，尤其是 `ensureGroupParticipants ... agents=1` 与群创建成员持久化。
