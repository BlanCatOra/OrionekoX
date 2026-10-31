# 酒吧炒饭 · 第二轮独立黑盒测试报告（测试 C）

- 日期：2026-10-27（设备本地时间）
- 测试身份：独立第三方黑盒测试 C（未读取 A/B 报告）
- 设备：emulator-5554，1080×2400，Android 14 / SDK 34
- 被测包：`io.github.blancatora.orionekox`，versionName **0.9.0**，versionCode **1329**
- 现有真实 Provider：OpenDesign（`https://amr-link.open-design.ai/v1`，`deepseek-v4.1-flash`），API Key 全程只观察掩码，未读取/复制/导出
- 测试方式：adb input / uiautomator dump / screencap / logcat / 本机生成 PNG/JPEG/ZIP/JSON 后 push
- 证据目录：`/tmp/bar-rice-r2-C/`（截图、XML、`logcat-final.txt`、`emulator-restart*.log`）

## 0. 结论摘要

**总体结论：FAIL（建议进入第三轮独立测试）。**

0.9.0 的核心主链路（智能体复制/记忆/工作区/群成员/长消息/@ 路由/外观自定义/预设/原生重置/.oxagent 独立会话）大多通过，但存在两项 P1：

1. **图片选择与发送内容错误**：在 App 内“附件 → 图片”选择目标 JPEG 时，图片编辑器预览为透明/错位，实际发送出去的是**当前 App 界面截图**（联系人页），不是所选照片。
2. **列表点击命中区域与绘制位置垂直错位**：聊天列表、分享目标列表、建群成员列表、媒体网格均出现“点 A 行打开/选中上一行”的现象；分享目标列表中点击 C2 行曾选中真实联系人 Arch（已在发送前取消），存在误发风险。

其余为 P2/P3：普通群消息只有 1 个 agent 回复、重开后助手消息把 `\n` 当字面量显示、竞品主题 JSON 只导入部分字段、本地智能体无法被搜索到、SillyTavern 卡的 first_mes 未用作开场白、重复导入技能产生重复条目等。

## 1. 逐项结果

| # | 项目 | 结果 | 证据 |
|---|---|---|---|
| 1 | 基线：版本/首页/清日志 | PASS | `00-home.png`；dumpsys 确认 0.9.0/1329 |
| 2 | 建立测试 Agent（复制、改名、简介、提示词、Provider 继承） | PASS | `02-agent-menu.png`、`02-name-pasted.png`、`02-bio-pasted.png`、`02-prompt-pasted.png`、`02-c2-settings-verify.png`、`02-c2-chat.png` |
| 3 | 单聊：真实模型回复、记住忌口 | PASS | `03-msg1-reply.png`；日志 `remember ... User does not eat cilantro (香菜)` |
| 3 | 工作区保存 `bar-fried-rice-C2.md` + 内容校验 | PASS | `03-permission-dialog.png`、`03-save-reply.png`、`03-workspace-preview.png`（1535 B，含忌口与 0–15 分钟步骤） |
| 3 | 新回合回忆忌口 | PASS | `03-memory-recall.png`：“你不吃香菜，所有配方都要避开香菜。” |
| 3 | 返回列表再进会话，历史立即显示（0.9.0 回归） | PASS（有渲染缺陷） | `03-tap610.png`、`09-c2-reopen-boot3.png`；冷启动后也立即显示，但部分助手消息把 `\n` 显示为字面量（见 P2-2） |
| 4 | 附件 → 图片发送 r2c-rice.jpg | **FAIL** | 实际发送的是 App 界面截图（联系人页）：`04-sent-check.png`、`04-selected-state.png`、`04-rice-editor.png` |
| 4 | 缩略图/双勾/无红叹号/无 0KB/无下载箭头 | PASS（针对已发出的那张图） | `04-sent-check.png` |
| 4 | 模型视觉识别 | PARTIAL | 模型准确描述了**实际收到**的截图内容（“本地智能体联系人列表…”），说明视觉链路可用；但未能对目标 `BAR FRIED RICE / OrionekoX test C2` 做识别（图发错）。见 `04-sent-check.png` 与日志 `image=true` |
| 4 | 长按图片消息看菜单后返回（不删除） | PASS | `09-image-menu.png`（编辑/转发/删除，按返回退出） |
| 5 | 新建群 `酒吧炒饭R2C测试群`，成员 创建者+C2+Yuki | PASS | `05-group-created.png`、`05-group-info.png`（3 位成员，3 人在线） |
| 5 | 普通群消息两个 agent 回复 | **PARTIAL/FAIL** | `05-group-msg1-replies.png`；日志只有一次 `runRound`，仅 Yuki 回复，C2 静默（见 P2-1） |
| 5 | `@agent_29` 只 C2 回复、Yuki 不插话 | PASS | `05-group-msg1-replies.png`；C2 回复“不加——你忌香菜…”，Yuki 未插话；用户名为 `@agent_29`（tg 555000029） |
| 5 | 400+ 字长消息、两端换行、末尾 R2C | PASS | `05-long-reply.png`（607 字，完整换行，R2C 未被截断；Yuki 明确确认） |
| 5 | 加 Echo Assistant：成员 +1 且只一行；长按移除恢复 | PASS | `05-echo-added2.png`、`05-group-4members.png`、`05-echo-longpress.png`、`05-echo-remove-confirm.png` |
| 5 | 长按群消息菜单后返回 | PASS | `05-group-msg-menu.png` |
| 6 | Ox 设置逐项查看 | PARTIAL | `06-ox-settings-root.png`；已进入：智能体、外观/Agent 会话外观、MCP、技能库、工作区、终端、代理与 VPN；模式/语言/本地会员/引导与授权/工具与权限/工作流/OxLinux/版本仅确认入口存在 |
| 6 | MCP：列表/详情/新增弹窗取消/文件导入/预览/重复导入 | PASS（重复导入未重复） | `06-mcp.png`、`06-mcp-add.png`、`06-mcp-json-import.png`、`06-mcp-preview.png`（“将导入 1 个服务器，跳过 0 个”）、`06-mcp-imported.png`；再次导入后 `R2C MCP` 仍仅 1 行 |
| 6 | 技能库：导入 zip、列表、预览、删除/幂等 | PARTIAL | `06-skills.png`、`06-skill-imported.png`、`06-skill-preview.png`；重复导入产生两份 `r2c-skill`（`06-skill-longpress.png` 未找到删除菜单） |
| 6 | 终端 TTY 1/2/3：pwd / echo r2c / ls | PASS（不串台） | `06-tty1-pwd2.png`、`06-tty2-echo2.png`、`06-tty3-ls.png` |
| 6 | 工作区：隐式目录、重命名、`../escape.txt` 越界 | PASS | `06-ws-created.png`、`06-ws-renamed2.png`、`06-ws-escape.png`（未生成逃逸文件；错误提示未捕获） |
| 6 | 代理与 VPN 只读 | PASS | `06-proxy2.png`（SOCKS5 10.0.2.2:7890；VPN 无配置；未修改） |
| 7.1 | 原生模式默认 + 原生提示 + 重置入口 | PASS | `07-chat-appearance-native.png` |
| 7.2 | 切自定义，字段齐全 | PASS | `07-chat-appearance-custom.png`（背景/背景图/收/发气泡/透明度/圆角/字号/导入/保存预设/预设/重置） |
| 7.2 | 设 #101114 / #E8F5E9 / #EFFDDE / 86% / 10dp / 1.1x | PASS | `07-custom-values.png`；聊天生效 `07-custom-chat-check.png` |
| 7.2 | 另一个 agent 聊天同样生效（全局） | PASS（当前实现全局，非按会话） | `07-theme-other-agent.png` |
| 7.3 | 保存 `R2C-Dark`，改值（#FDF6E3/18dp/0.9x），应用恢复，长按删除 | PASS | `07-preset-saved2.png`、`07-modified-values.png`、`07-preset-applied.png`、`07-preset-deleted.png` |
| 7.3 | 保存 `R2C-Image` | PASS | `07-presets-list.png` |
| 7.4 | 背景图选择/显示/清除 | PASS | `07-bg-image-set.png`、`07-bg-image-chat.png`（原壁纸花纹仍淡淡叠加）、`07-bg-cleared.png` |
| 7.5 | 导入竞品主题 JSON | **PARTIAL/FAIL** | `07-theme-imported.png`：背景 #1E1B2E、透明度 86%、字号 1.2x 生效；**in_bubble/out_bubble 未生效**（仍 #FFFFFF/#E3F2FD），corner 18→16dp |
| 7.5 | 导入非法 JSON，不破坏现有主题 | PARTIAL | `07-bad-imported.png`、`07-bad-toast2.png`：数值保持不变、无崩溃；未捕获到明确错误提示 |
| 7.6 | 重置为原生，聊天无残留；预设保留 | PASS | `07-native-reset.png`、`07-native-chat.png`、`07-presets-retained.png` |
| 7.6 | 冷启动后外观/预设/背景图持久化 | PARTIAL | 第二次冷启动后自定义值仍在（`07-custom-values` 对比 `07-custom-chat-check`）；第三次冷启动后原生模式仍在（`09-c2-reopen-boot3.png`）。背景图在重置前已清除，未再验证图片持久化；预设未在冷启动后再次打开确认 |
| 8 | `.oxagent` 导出→导入，副本/独立 tg_id/会话 | PASS（API Key 被剥离） | `08-exported.png`、`08-imported.png`、`08-copy-chat.png`（`酒吧炒饭C2 · 副本`，3:13 欢迎语、独立会话） |
| 8 | SillyTavern PNG 卡导入：名称/简介/开场白 | PARTIAL | `08-card-chat.png`（名称 R2C Card，开场白是通用欢迎语而非 first_mes）；`08-card-settings.png`（备注为空）；AGENTS.md 内含 Description/Personality/First message |
| 8 | 主题 JSON | 见 7.5 | — |
| 9 | FATAL/ANR 检查 | PASS（0） | `logcat-final.txt`：`FATAL EXCEPTION`/`ANR in io.github.blancatora.orionekox` 计数 0；无 tombstone、无 dropbox 记录 |
| 9 | 清理临时文件 | PASS | Download 下 `r2c-*` 与导出的 `酒吧炒饭C2.oxagent` 已删除 |

## 2. 问题分级（P0–P3）

### P0
- 无（未发现崩溃、数据丢失或密钥/账号泄露；未读取 API Key）。

### P1
**P1-1 图片选择后被替换为 App 界面截图，发送内容错误**
- 复现：C2 会话 → 附件 → 相册 → 选择 1:51/1:50 的图片（目标 `r2c-rice.jpg`）→ 编辑器预览透明、可见下层界面 → 点发送。
- 期望：发送所选 640×480 JPEG（含 `BAR FRIED RICE`、`OrionekoX test C2`）。
- 实际：发送出一张“联系人页”界面截图（`04-sent-check.png`）；模型也按截图内容回答。缩略图/双勾正常，但内容错误。
- 影响：媒体链路不可信，可能把界面内容（含隐私）发到会话。

**P1-2 列表行点击命中区与绘制位置垂直错位，可能打开/选中错误对象**
- 复现 A（聊天列表）：C2 最近消息置顶后，点击可视的 C2 行（y≈545）两次均打开 **Arch**；点击同一可视行的下缘（y≈610）才进入 C2（`03-reopen-1s.png`、`03-reopen-2.png`、`03-tap610.png`）。
- 复现 B（分享目标）：系统分享 r2c-rice.jpg 到 App → “选择对话” → 点击可视 C2 行，确认条显示 **“改为:Arch”**（`04-share-target.png`），已在发送前返回取消。
- 复现 C（建群成员）：点击 Yuki Waifu 行，实际选中 **Sailor Card · 副本**，导致首次建群成员错误（`05-group-name.png`），后退回并校正（`05-members-corrected.png`）。
- 期望：点击行与绘制行一致。
- 实际：可点击矩形整体下移约 60–110 px（列表滚动/插入后更明显）。
- 影响：可能误发消息给真实联系人，属于隐私/交付风险。

### P2
**P2-1 普通群消息只有 1 个 agent 回复**
- 复现：群内发送普通消息（无 @）两次。
- 实际：日志各只有一次 `runRound dialog=-1800000008`，两次均由 Yuki 回复，C2 全程未回复；@agent_29 时 C2 正常回复。
- 期望：按设计两个 agent 回复或轮询；现状与“两个 agent 都应回复”不一致（需确认产品意图）。

**P2-2 重开会话后，部分助手消息把 `\n` 当字面量显示**
- 证据：`03-msg1-reply.png`（首次渲染正常）→ 重开/冷启动后同一配方气泡显示大量 `\n`（`04-sent-check.png` 顶部、`09-c2-reopen-boot3.png` 顶部）。
- 影响：长文可读性明显下降，疑似历史消息转义/反转义不一致。

**P2-3 竞品主题 JSON 只导入部分字段**
- 输入：`{"background":"#1E1B2E","in_bubble":"#2D2A45","out_bubble":"#7C5CFF","bubble_alpha":220,"corner_radius":18,"font_scale":1.2}`
- 实际：background/#1E1B2E、alpha 86%、font 1.2x 生效；in/out 气泡仍为 `#FFFFFF`/`#E3F2FD`；corner 18→16dp（`07-theme-imported.png`）。
- 期望：六个字段全部映射，圆角保持 18dp 或明确提示取整。

**P2-4 本地智能体无法被搜索到**
- 复现：聊天列表搜索、分享“选择对话”搜索、建群成员搜索输入 `C2`。
- 实际：均显示“未找到相关信息”，但列表中可见该智能体（`03-search-c2` 失败、`04-share-search2.png`、`05-member-search.png`）。
- 影响：智能体多时无法快速定位。

**P2-5 SillyTavern 卡的 first_mes 未作为开场白；简介为空**
- 实际：`R2C Card` 欢迎语为通用“你好，我是 R2C Card…”，不是卡里的 “晚上好，我是 R2C 角色卡测试助手…”; 备注为空，描述只进入 AGENTS.md（`08-card-chat.png`、`08-card-settings.png`）。
- 期望：first_mes 作为首条欢迎语，description 进入简介或明确展示在资料中。

**P2-6 重复导入技能产生重复条目，且未找到删除入口**
- 实际：同一 `r2c-skill.zip` 导入两次后出现两行 `r2c-skill`（`06-skill-imported.png` 后再次导入；计数 2）；长按行无菜单，预览对话框仅“确定”。
- 期望：同名覆盖/提示跳过，且可删除。

### P3
- **P3-1 分享进入会话时历史为空**：分享目标选中后进入的会话不显示历史，返回再进才恢复（`04-after-share-c2.png`、`04-reopen2.png`）。
- **P3-2 自定义背景图仍叠加原生壁纸花纹**：选择 r2c-bg.png 后可见紫色渐变 + 淡淡原生图案（`07-bg-image-chat.png`）。
- **P3-3 自定义背景未覆盖输入栏下方**：自定义模式下聊天底部仍有原生壁纸条（`07-theme-other-agent.png`）。
- **P3-4 MCP 既有项 QA MCP 显示 HTTP 502**（只读观察，非本次写入）：`06-mcp-imported.png`。
- **P3-5 非法主题 JSON 无可见错误提示**：值未变化，但未捕获 toast（`07-bad-toast2.png`）。
- **P3-6 联系人长按无“复制”**：联系人列表长按进入多选（仅删除），智能体复制入口在 Ox 设置 → 智能体长按；与任务描述的路径不一致，建议统一/补文档。

## 3. 0.9.0 回归项结论

| 回归项 | 结果 | 说明 |
|---|---|---|
| 会话重开历史 | **PASS** | 冷启动/返回列表再进均立即显示；但存在 P2-2 的 `\n` 渲染缺陷，不是空白 |
| 建群成员 | **PASS** | 创建后 3 位成员/3 人在线；Echo 添加后 4 位且单行，移除后恢复 3 位 |
| `.oxagent` 身份 | **PASS** | 导入生成 `酒吧炒饭C2 · 副本`，有独立欢迎语/会话；导出会剥离 API Key（导入副本需重新填 Key 才能调用模型） |
| 图片缩略图 + 视觉 | **PARTIAL/FAIL** | 缩略图、双勾、无红叹号/0KB/下载箭头 PASS；但发送内容错误，视觉仅验证了“收到什么描述什么” |
| Markdown/长消息换行 | **PASS** | 607 字长消息气泡内正常换行，尾部 R2C 完整；Yuki 明确确认 |
| 外观自定义/预设/导入/原生重置 | **PASS（导入部分字段 FAIL）** | 自定义、预设保存/应用/删除、背景图、原生重置、预设保留均通过；竞品 JSON 的 in/out 气泡未导入 |

## 4. 关键日志（时间 / tag / 内容）

- `02:14:00.418 I/OxBridge executeTool ... tool=remember` → `remembered: User does not eat cilantro (香菜) — avoid it in all recipes.`
- `02:15:34.183 I/OxBridge permission for write_file: true` → `tool write_file done: wrote 1535 bytes to bar-fried-rice-C2.md`
- `02:23:59.490 I/OxBridge handleOutgoingMedia dialog=555000029 count=1` → `startTurn ... image=true`（即发错图的那次）
- `02:37:29.252 I/OxBridge createLocalGroup id=1800000008 agents=2`
- `02:38:40.460 I/OxBridge runRound dialog=-1800000008 depth=0` → `finishTurn ... len=155`（普通群消息仅 Yuki）
- `02:39:48.640 I/OxBridge runRound dialog=-1800000008 depth=0` → `finishTurn ... len=20`（@agent_29 仅 C2）
- `02:41:27.525 I/OxBridge runRound dialog=-1800000008` → `finishTurn ... len=65`（长消息仅 Yuki）
- 最终扫描：`FATAL EXCEPTION` / `ANR in io.github.blancatora.orionekox` = **0**（`/tmp/bar-rice-r2-C/logcat-final.txt`）

## 5. 环境异常记录（非 App FATAL）

测试期间模拟器进程整体掉线 3 次：
1. 约 02:19（在聊天列表点击/搜索时）；
2. 约 02:47（在 Settings→Chat 切换时）；
3. 约 03:17（在聊天列表点击时）。

三次均为 `adb devices` 变空、模拟器进程消失；随后按指定命令冷启动（未 `-wipe-data`），数据均保留。重启后的 logcat 中无本 App 的 FATAL/ANR，`/data/tombstones` 为空、dropbox 无本包记录，因此按“模拟器/宿主不稳定”记录，不判定为 App 崩溃。三次掉线也造成部分导航/点击步骤重复与顺序调整。

## 6. 未执行项与原因

1. Ox 设置中 **模式、语言、本地会员、引导与授权、工具与权限、工作流、OxLinux、版本** 未逐项进入二级页：仅确认入口存在；版本由 `dumpsys package` 验证。原因：列表滚动 + 点击命中错位导致多次误入其他页，且模拟器三次掉线，时间不足。
2. **背景图 + 冷启动持久化** 未复查：背景图在原生重置前已主动清除；预设列表在冷启动后未再次打开（但“重置为原生后预设保留”已验证）。
3. **非法主题 JSON 的错误提示** 未捕获到（toast 过短/被页面切换覆盖）；仅确认数值未变化、未崩溃。
4. **技能删除** 未找到 UI 入口，未能验证删除链路。
5. **图片视觉识别目标文字** 未完成：因图片选择器发错图；仅验证了“模型能准确描述实际收到的图片”。未使用“文件”方式绕过（避免偏离用例）。

## 7. 清理与保留

已从 `/sdcard/Download/` 删除：`r2c-rice.jpg`、`r2c-bg.png`、`r2c-card.png`、`r2c-theme.json`、`r2c-bad.json`、`r2c-mcp.json`、`r2c-skill.zip`、`r2c-zh.png`、`酒吧炒饭C2.oxagent`。

App 内为本轮测试保留（供主代理处理，未删除既有数据）：
- 智能体：`酒吧炒饭C2`（复制 Yuki，Provider OpenDesign，含 1535B 工作区文件）、`酒吧炒饭C2 · 副本`（.oxagent 导入，API Key 为空）、`R2C Card`（PNG 卡导入）
- 群：`酒吧炒饭R2C测试群`（TestUser + Yuki Waifu + 酒吧炒饭C2）
- 外观：预设 `R2C-Image`（`R2C-Dark` 已按用例删除）；当前为 **Telegram 原生** 模式
- MCP：`R2C MCP`（http://10.0.2.2:8879/mcp）
- 技能：`r2c-skill` 两条（重复导入产物）
- 工作区（C2）：`bar-fried-rice-C2.md`、`r2c-dir/note-renamed.txt`
- C2 会话内保留了测试消息与一张误发的界面截图（未删除，留证）

未修改既有 `Yuki Waifu`、既有会话/记忆/工作区文件；未向任何真实联系人发送消息（分享目标曾误选 Arch，已在确认页返回，未发送）。

## 8. 结论与建议

**结论：FAIL。** 0.9.0 的记忆、工作区、群成员、长消息、@ 路由、外观预设/原生重置、`.oxagent` 独立会话达到预期；但媒体选择器“发错图”和列表点击错位是两项 P1，直接影响内容正确性与误发风险；竞品主题导入与卡片开场白也未达预期。

**是否建议第三轮：建议。** 第三轮应优先：
1. 复现并定位“图片选择器/照片编辑器内容错误”（建议同时用真机/不同 GPU 后端排除模拟器渲染因素，但本次有明确“发送内容为界面截图”的证据）。
2. 修复列表行命中区与绘制位置的垂直错位（聊天列表、分享目标、成员选择、媒体网格），并回归“误选 Arch”场景。
3. 明确群消息 fan-out 产品期望（单回复/轮询/全部回复），并补充对应断言。
4. 校验历史消息 `\n` 转义与竞品主题 `in_bubble/out_bubble` 映射。
5. 在稳定模拟器/真机上复跑本轮未执行的设置子页与冷启动持久化。
