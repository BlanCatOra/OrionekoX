# 酒吧炒饭第二轮独立测试报告（A：全流程 + 回归 + A 专项）

## 0. 环境与结论摘要

| 项 | 值 |
|---|---|
| 设备 | emulator-5554，Android 14，1080×2400/420dpi |
| 安装包 | io.github.blancatora.orionekox（release） |
| 版本 | versionName 0.8.115，versionCode 1309 |
| 测试账号 | 本地测试账号（账号页含手机号，本报告不记录任何号码/密钥） |
| 开始时间 | 2026-10-26 21:25:46 |
| 结束时间 | 2026-10-26 23:17 |
| 截图目录 | `/tmp/bar-rice-r2-A/` |
| 本轮对象 | Agent `酒吧炒饭R2A改名`（@agent_25）；群 `酒吧炒饭R2A测试群` |
| 结论 | 全流程可走通，无 P0/P1；发现 4 个 P2、若干 P3，详见第 4 节 |

说明：第一轮 A/B/C 的 Agent 和群未被修改/删除。本轮额外保留对象：
`Yuki Waifu · 副本`（复制过程中的第一份）、`酒吧炒饭R2A改名 · 副本`（.oxagent 导入验证用），
供主代理统一清理。

### 测试对象建立方式说明

- 新建智能体编辑器可用，但“API Key”为空时无法获得可用模型；不允许读取/抄写 Yuki 的密钥。
- 因此按本轮回归口径“复制/新建 Agent 后立即建群”，使用 **复制 Yuki Waifu** 创建本地 Agent，
  再改名 `酒吧炒饭R2A`、简介 `深夜酒吧炒饭主厨`、系统提示词替换为
  `你是「酒吧炒饭」深夜主厨。中文回答，先确认忌口，再给 15 分钟单锅配方。`
- 继承配置为 OpenDesign（`https://amr-link.open-design.ai/v1`，`deepseek-v4.1-flash`），
  未打开、未抄写、未输出任何 API Key。
- 该“复制创建”路径在本报告中记为 **PASS（等效路径）**；未执行项见第 6 节。

---

## 1. 第二轮六项回归

| # | 项目 | 结果 | 证据 |
|---|---|---|---|
| 1 | 新建/复制 Agent 后立即建群（该 Agent + Yuki），3 位成员，该 Agent 能回复；`@agent_<id>` 仅目标回复 | **PASS** | `08h-group-created.png`（3 位成员/3 人在线）、`09-group-profile.png`（TestUser 创建者 + Yuki + R2A 各一行）、`11a-group-replies.png`（Yuki 回复）、`12a-at25-reply.png`（@agent_25 后仅 R2A 回复） |
| 2 | 群内发带可识别文字图片，缩略图/无红叹号，Agent 能描述 | **PASS** | `14-image-sent.png`：图片预览正常、无红叹号；R2A 回复“蓝底测试卡，上面写着「BAR FRIED RICE」和「R2A TEST IMAGE 2026-10-26」” |
| 3 | 回聊天列表再进 Agent：历史立即显示、输入框正常、无“开始Bot” | **PASS**（有偶发空白，见 P2-1） | `15b-r2a-chat.png`、`21e-r2a-reopen.png`；无“开始Bot”按钮 |
| 4 | 改名后欢迎语同步新名字 | **PASS** | `16c-chatlist-renamed.png` 列表预览、`16d-renamed-chat.png` 欢迎气泡均变为“酒吧炒饭R2A改名” |
| 5 | Ox 设置→OxLinux 第 6 步：页面居中、标题不裁切、按钮“开始使用 OrionekoX” | **PASS**（入口有 P3 问题） | `31e-oxlinux-switch.png`：第 6 步/共 6 步，标题完整居中，底部按钮文字正确 |
| 6 | MCP JSON 导入：预览/取消/确认/重复导入不重复；删除测试服务器 | **PASS** | `34a-mcp-preview.png`（将导入 1 个，跳过 0 个）、`35-mcp-imported.png`（r2a-test 仅一行）、`36b-mcp-dup-preview2.png`（提示“将导入 0 个服务器，跳过 1 个”，列表仍一行）；测试后已删除 |

### 回归细节

- 群创建后普通消息（未 @）只有 Yuki 回复，R2A 未回复；发送
  `@agent_25 请用一句话确认：R2A 在群里，并说明两人份炒饭是否加香菜。`
  后仅 R2A 回复：“收到，R2A 在群里报到：两人份炒饭香菜照加，若那位不吃就给ta留一半不加。”
  → 定向 @ 路由正确；普通消息的“单 Agent 回复”行为记录为 P3-4。
- 群图片由文件入口选择 `bar-rice-A.jpg`（640×480，含 `BAR FRIED RICE` 文字），
  发送后气泡为图片预览，无 X/0KB/下载箭头/红叹号，Agent 能准确读出两行文字。

---

## 2. 全流程逐步结果

### 1.1 基线 — PASS

- 启动 app，底部 聊天/联系人/设置/我的/终端 五个 Tab 均可见。
- `adb logcat -c` 于 21:25:46 执行。
- 截图：`00-home.png`、`01-contacts.png`。

### 1.2 建立测试 Agent — PASS（等效路径）

- 在 联系人→添加智能体 中检查了“选择连接方式”（API Key/局域网/OxLinux）以及新建编辑器的
  名称、简介、Provider、接口地址、API Key、模型、请求头、AGENTS.md 等字段。
- 因无法提供 API Key，改用 智能体列表→长按 Yuki Waifu→复制，随后编辑：
  名称 `酒吧炒饭R2A`（后改为 `酒吧炒饭R2A改名`）、简介 `深夜酒吧炒饭主厨`、系统提示词为指定文本。
- 保存后出现在 智能体列表、联系人列表、聊天列表。证据：`06-agent-saved.png`、`59c-contacts-r2a.png`。
- 头像使用本地生成的纯色 JPG（`r2a-avatar.jpg`），未使用隐私图片。

### 1.3 单聊、记忆与工作区 — PASS

| 子项 | 结果 | 证据/说明 |
|---|---|---|
| 发送材料与忌口问题，真实模型回复 | PASS | 21:59:41 发送，约 15s 内出现完整分段时间轴配方，先确认“忌口已记住：不吃香菜”；`17b-msg1-reply.png` |
| 保存配方到工作区 `bar-fried-rice.md` | PASS | 22:01 发送；首次工具调用弹窗“允许调用工具？”（write_file，含内容预览），点“允许”；`18b-tool-allowed.png` |
| 工作区文件存在且内容为炒饭步骤 | PASS | 工作区选择该 Agent 后显示 `bar-fried-rice.md` 2.3 KB；预览开头为“# 酒吧炒饭（两人份 · 单锅 15 分钟）”；`19e-r2a-workspace.png`、`19f-file-preview.png` |
| 新轮次回忆忌口 | PASS | 22:09 发送“我忌口什么？只回答忌口。”，回复“香菜（不吃香菜；葱可以正常放）。”；`21e-r2a-reopen.png` |
| 返回列表再进入，会话/记忆仍在 | PASS | `21e-r2a-reopen.png`；冷启动后仍可见（见 A6） |

### 1.4 媒体 — 部分 PASS

| 子项 | 结果 | 证据/说明 |
|---|---|---|
| 生成/推送 640×480 JPEG | PASS | `bar-rice-A.jpg`，15.9 KB，含 `BAR FRIED RICE` |
| 附件入口（文件）发送 | **FAIL（期望缩略图）** | 单聊中经“文件”入口发送后为 JPG 文档卡片（文件图标，无图片缩略图），无红叹号/0KB；`23-single-image.png` |
| 相册入口发送 | PASS | 经“相册”选择同一图片，气泡显示正常缩略图；`24e-photo-sent3.png` |
| 长按图片消息菜单 | PASS | 出现 1选中/编辑/复制/转发/删除 及 回复/转发；仅查看后返回；`25-photo-longpress.png` |

### 1.5 本地群、成员与群回复 — PASS

| 子项 | 结果 | 证据 |
|---|---|---|
| 新建群选择 R2A + Yuki，命名 `酒吧炒饭R2A测试群` | PASS | `08h-group-created.png` |
| 群资料成员数/成员行正确 | PASS | 3 位成员、3 人在线；TestUser（创建者）、Yuki、R2A 各一行；`09-group-profile.png` |
| 群内提问，本地 Agent 回复且无红叹号 | PASS | 普通消息仅 Yuki 回复；@R2A 后 R2A 回复；`11a-group-replies.png`、`12a-at25-reply.png` |
| 添加 Echo Assistant，成员数 +1 且仅一行 | PASS | 3→4 位成员；`28b-echo-added2.png` |
| 长按 Echo 移除，成员数恢复 | PASS | 4→3 位；`29b-remove-state.png` |
| 长按群消息菜单 | PASS | 回复/转发/编辑/复制/删除；`30-group-chat-again.png` 前的操作 |

### 1.6 设置与扩展抽查 — PASS

- Ox 设置逐项只读查看：模式 `Telegram 账号`、语言 `简体中文`、本地会员开关、引导与授权、
  智能体、MCP 服务、工具与权限、技能库、工作区、工作流、OxLinux、终端、关于
  （版本 0.8.115）。证据：`04-ox-settings.png`、`39-tools-perms.png`。
- MCP 服务页：列表 `QA MCP`（HTTP 502，预存）、`DeepWiki`（3 个工具），新增/JSON 导入入口可用；
  导入测试后已删除 `r2a-test`。证据：`35-mcp-imported.png`。
- 技能库：`示例技能` 预览为可滚动 ScrollView，SKILL.md 内容显示正常，点“确定”关闭。证据：`38-skill-preview.png`。
- 终端：TTY 1 执行 `pwd` → `/storage/emulated/0/Download`；TTY 2/TTY 3 执行 `echo ok` → `ok`；
  TTY 标签高亮正常（TTY 2 带历史名 QA2）。证据：`40-terminal.png`。
- 设置→代理与 VPN：只读查看代理开关、`10.0.2.2:7890` SOCKS5、VPN 空配置说明；未改任何开关。
  证据：`41-proxy-vpn.png`。
- 文案/UI 问题记录见第 5 节。

### 1.7 崩溃、清理与报告 — PASS

- `adb logcat -d -b crash` 与主缓冲区均未出现
  `FATAL EXCEPTION` / `ANR in io.github.blancatora.orionekox`；结束时 app PID 10212 正常。
- 模拟器掉线两次（与 app 无关，见第 6 节）；均按 `-gpu off` 冷启动恢复，数据完整。
- 未删除用户数据；未 `pm clear`；未修改第一轮对象。

---

## 3. A 专项（建档、Provider、记忆、工作区）

| 子项 | 结果 | 证据/说明 |
|---|---|---|
| 编辑器字段：名称/简介/头像/技能/Provider/模型/温度/系统提示词 | PASS | 名称、简介、AGENTS.md 已改；头像纯色图；技能 1→2；温度 0.7→0.8；保存后重进均保留；`47b-reopen-verify.png` |
| 用户名/工具/记忆字段 | 部分 | 用户名自动 `@agent_25`，编辑器不可改；工具权限在“工具与权限”按 Agent 配置；记忆为独立编辑器。 |
| 模型连通性测试 | PASS | “测试连接”→“连接成功 · HTTP 200”；`45-test-connection.png` |
| 模型列表拉取 | PASS | “从 API 获取模型列表”→ 13 models；可见 deepseek-v4-flash / v4-flash-vision-exp / v4-pro / v4.1-flash；未改变当前模型；`45c-model-list.png` |
| 记忆新增/重进/删除 | PASS | 新增“最爱的宵夜是酒吧炒饭”；重进仍在；随后删除测试行，原有记忆与“不吃香菜”保留；`49d-memory-verify.png`、`50-memory-deleted.png` |
| 工作区：新建文本文件 | PASS（名字曾异常） | 创建 `r2a-note.md`；首次因焦点问题实际生成 `r2a-note.mdR2A`，见 P3-3 |
| 工作区：重命名 | PASS | 改回 `r2a-note.md`；`52b-rename-typed.png` |
| 工作区：复制 | PASS | 生成 `r2a-note (copy).md`；`53-file-copied.png` |
| 工作区：导出到设备 | PASS | `r2a-note (copy).md` 保存到 `/sdcard/Download/`（0 B，因源文件内容为空），文件存在 |
| 工作区：导入文件 | PASS | `r2a-mcp.json` 从 Download 复制进工作区；`ui-imported-file.xml` |
| 工作区：移动/新建目录 | **未通过/无入口** | UI 仅有新建文本文件、导入文件；文件名输入 `r2a-dir/inner.md` 后无目录、无提示；`54-nested-dir.png` |
| .oxagent 导出 | PASS | 导出 `酒吧炒饭R2A改名.oxagent`，7869 B 到 Download；`55-export-oxagent.png` |
| .oxagent 导入 | PASS（副本，不覆盖原 Agent） | 生成 `酒吧炒饭R2A改名 · 副本`，原 Agent 保留；人设/简介/提示词/头像/技能 2 个/温度 0.8 保留；`57-import-copy-edit.png` |
| .oxagent 记忆保留 | PASS | 副本记忆含 `用户不吃香菜（忌口）`，且不含已删除的测试行 |
| .oxagent 工作区保留 | 部分 | 副本工作区有 `bar-fried-rice.md`（2.3 KB）；`r2a-note*`、`r2a-mcp.json`、inbox 图片未出现在副本中 |
| .oxagent 导入后可用性 | 注意 | 副本的 API Key 为空（导入不会携带密钥），副本无法直接对话；原 Agent 密钥不受影响 |
| 冷启动复查 | PASS | 强制停止后重启：聊天历史、Agent 设置、记忆（含不吃香菜）、工作区 5 个条目均在；`58-coldstart-final.png`、`58b-coldstart-r2a-chat.png`、`ui-ws-cold-final.xml`、`ui-memory-cold-final.xml` |

---

## 4. 问题清单（P0–P3）

### P0
- 无。

### P1
- 无（崩溃缓冲区空；模拟器掉线不计为 app 崩溃）。

### P2

**P2-1 聊天消息区偶发空白/空气泡，历史不渲染**
- 复现：冷启动或从系统分享面板返回后，从聊天列表点进 `酒吧炒饭R2A改名` 或 `酒吧炒饭R2A测试群`；
  只见标题和空白/灰色空气泡占位，无文字与头像；从 Ox 设置→智能体→点该 Agent 进入则恢复。
- 期望：进入会话立即渲染历史；实际：曾持续 >10s 空白，需换入口恢复。
- 证据：`20f-blank-chat-evidence.png`、`21a-coldstart-chat.png`、`21b-group-render2.png`。
- 影响：用户误以为聊天记录丢失。

**P2-2 工具调用结果气泡渲染原始 JSON 与 `\n` 转义，占用整屏**
- 复现：让 Agent 调 `write_file` 保存 `bar-fried-rice.md` 后，聊天出现超长气泡，
  显示 `{"path": ..., "content": "...\n\n..."}` 原文与转义换行。
- 期望：工具调用以折叠卡片/摘要展示；实际：原始转义文本占据大半屏，严重干扰阅读。
- 证据：`17b-msg1-reply.png`、`21e-r2a-reopen.png`。

**P2-3 发送按钮可点区域异常，点击按钮主体不发送**
- 复现：输入文本后点击发送按钮中心（约 929,1435）或上半部无反应，键盘反复弹出；
  只有点击按钮最右缘（约 983–1009,1429–1440）才发送。多次出现。
- 期望：整个圆形按钮均可点击；实际：可点区极小且与输入框无障碍节点重叠。
- 证据：操作记录与 `21e-r2a-reopen.png`；无障碍节点显示“发送”容器 `[798,1377][1061,1493]`，
  实际命中区远小于该范围。

**P2-4 `.oxagent` 导入副本丢失 API Key**
- 复现：导出 `酒吧炒饭R2A改名.oxagent` 后导入，副本编辑器 API Key 为空。
- 期望：导入副本可直接使用，或明确提示需重新填写密钥；实际：无提示，副本无法发起对话。
- 证据：`57-import-copy-edit.png`（API Key 显示“输入 API Key”）。

### P3

1. **OxLinux 行点击无响应**：Ox 设置中点击 `OxLinux` 标题/行主体不跳转；只有点右侧“已启用”开关才进入第 6 步页面。证据：`31e-oxlinux-switch.png`。
2. **工作区功能缺口**：无“移动”“新建目录”入口；在“新建文本文件”输入 `r2a-dir/inner.md` 后无目录生成、无失败提示。证据：`54-nested-dir.png`。
3. **新建文本文件焦点异常**：点“初始内容”后输入的文字被拼到文件名，实际生成 `r2a-note.mdR2A` 且内容 0 B。证据：`51b-note-created.png`。
4. **群普通消息只路由一个 Agent**：未 @ 时仅 Yuki 回复，R2A 静默；@agent_25 才回复。若设计为“每群只允许一个 Agent 响应”，建议在资料页说明。
5. **文本绘制到状态栏**：聊天视图滚动时消息文字/气泡出现在状态栏图标下方并重叠。证据：`17b-msg1-reply.png`、`21e-r2a-reopen.png`。
6. **Agent 列表副标题不一致**：有简介显示简介，无简介显示 `model · endpoint`（同一列表混排）。证据：`05-ox-agents.png`。
7. **MCP 服务页标题重复**：页面标题与分组标题均为“MCP 服务”，视觉层级重复。证据：`35-mcp-imported.png`。
8. **信息架构**：`外观`（强调色/主题导入）放在“智能体”分组下；`OxLinux` 详情只能通过开关进入，缺少可发现的 chevron/说明。证据：`04-ox-settings.png`。
9. **“文件”入口发送 JPG 无缩略图**：作为文档卡片发送；相册入口则有缩略图。若产品期望图片统一预览，需按 MIME/入口统一。证据：`23-single-image.png` 与 `24e-photo-sent3.png`。
10. **预存 QA MCP 不可用**：显示 `HTTP 502`（测试前已存在，非本轮对象）。
11. **群创建服务卡片**：“您创建了一个群组”四条特性卡片常驻且占屏较大，建议可折叠/移除。
12. **名称渲染**：预存 Agent `**Bold** Card` 原名中的 Markdown 星号被原样显示（第一轮对象，未修改）。

---

## 5. 文案/翻译/UI 原生规范问题

- 未发现明显机器翻译或乱码；中文文案整体通顺。
- 存在的规范问题：
  1. 工具调用气泡显示 `\n` 转义（P2-2），属展示层未格式化。
  2. 聊天内容与状态栏重叠（P3-5），缺少顶部 inset/状态栏背景。
  3. OxLinux 入口无 chevron/“配置”提示，开关既承担开关又承担导航（P3-1/8）。
  4. 工作区失败无 toast/错误提示（P3-2/3）。
  5. 发送按钮触摸目标过小（P2-3），不符合 48dp 触摸目标规范。
  6. 重复标题“MCP 服务”（P3-7），可改为“已配置服务器”。

---

## 6. 未执行项及原因

1. **未使用“新建智能体→API Key”填入真实密钥创建 Agent**：无可用密钥，且禁止读取/输出密钥；
   按本轮“复制/新建 Agent”回归口径，用复制 Yuki 的等效路径完成。
2. **未验证 `.oxagent` 重复导入的幂等性**：仅做了单次导入验证（副本命名正确、不覆盖原 Agent）。
3. **工作区移动操作**：UI 无该入口，无法执行。
4. **OxLinux 容器实际安装/启动**：只验证第 6 步页面布局与按钮文案，未安装组件（避免改动容器环境）。
5. **代理/VPN、工具权限、会员开关**：按要求只读，未切换。
6. **真实联系人/通知/通话**：禁止真实联系人消息，未操作；列表中的 Arch 会话未进入/未发送。
7. **MCP 测试服务器连接**：`http://10.0.2.2:9999/mcp` 为占位地址，仅验证导入/去重/删除，不做连通性。
8. **应用内 ADB/Shizuku/ROOT**：只读查看状态，未授权或修改。

### 模拟器掉线记录（非 app 崩溃）

| 时间 | 现象 | 处理 |
|---|---|---|
| ~22:16 | `adb` 报 `device offline`，随后设备列表为空、模拟器进程消失；当时正在打开相册选择器 | 记录后以 `-gpu off -no-window -no-audio -no-boot-anim -no-snapshot-save` 冷启动 AVD `orioneko_test`，22:21 恢复 |
| ~22:25 | 同样 `device 'emulator-5554' not found`；当时在群资料页 | 22:26:50 再次冷启动，22:27 恢复；用户数据完整 |
| 23:11 | 主动 force-stop + 冷启动做 A6 复查 | 数据完整 |

两次掉线期间 app 无 `FATAL EXCEPTION`/ANR；logcat crash 缓冲区为空。

---

## 7. 最终结论

- **全流程：通过**（1.4 的“文件”入口缩略图不达标，但“相册”入口通过；其余步骤 PASS）。
- **第二轮六项回归：全部通过**（含 @agent_25 定向回复、图片识别、欢迎语改名同步、OxLinux 第 6 步、MCP JSON 去重）。
- **A 专项：通过**，但工作区移动/建目录无入口，`.oxagent` 副本不带 API Key。
- **最严重问题**：P2-1 偶发空白聊天渲染、P2-2 工具气泡原始 JSON、P2-3 发送按钮命中区异常、P2-4 导入副本无密钥。
- 无 P0/P1；建议优先修复 P2-1/2/3，并在 `.oxagent` 导入处提示“需重新填写 API Key”。

### 本轮保留对象（供清理）

- Agent：`酒吧炒饭R2A改名`（@agent_25）、`Yuki Waifu · 副本`、`酒吧炒饭R2A改名 · 副本`
- 群：`酒吧炒饭R2A测试群`（3 位成员）
- 工作区文件（R2A）：`bar-fried-rice.md`、`r2a-note.md`、`r2a-note (copy).md`、`r2a-mcp.json`、`inbox/1793024106097_bar-rice-A.jpg`
- 设备文件：`/sdcard/Download/bar-rice-A.jpg`、`r2a-avatar.jpg`、`r2a-mcp.json`、`酒吧炒饭R2A改名.oxagent`、`r2a-note (copy).md`
- MCP：测试服务器 `r2a-test` 已删除，无残留。
