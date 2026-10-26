# 酒吧炒饭测试问题汇总与修复计划

状态：A/B 报告已收到，C 进行中。按用户要求，三份报告齐备后才开始改代码。
目标版本：0.8.104（在 0.8.103 基础上做 P1 修复，再做 P2/P3）。

## 1. 跨报告确认的 P1

| 问题 | A | B | 根因（初步） | 修复方向 |
|---|---|---|---|---|
| Agent 会话重开整屏空白 | 有 | 有 | `refreshDialogRow` 的 `SELECT MAX(date), mid` 没有 `ORDER BY`，`last_mid` 可能指向旧消息；ChatActivity 重开按 last_mid 加载不到窗口 | 用子查询取最新 `mid`；打开本地 Agent 会话时确保 dialog 行与消息缓存一致；复测“返回列表再进入”“工具调用后返回” |
| 本地群 @ 指定 Agent 不生效 | — | 有 | `onGroupMessage` 只匹配显示名，消息里是 `@agent_17`；未命中就回退到 `agents.get(0)`（Yuki） | 解析 `@agent_<n>` 与 `@<name>`，按 `username`/`tgId` 精确选择；未 @ 时的默认策略写清楚 |
| 本地群发图片红叹号 | — | 有 | `SendMessagesHelper` 只在单聊 Agent 路径拦截媒体；负数组 id 落到服务器管线 | 新增本地群媒体拦截：写 `peerChat` 消息、缩略图/文档卡片、触发群回复；不请求服务器 |
| `.oxagent` 导入共用 `tg_id` | 有 | 有（同名/旧欢迎语） | 导入直接保留归档里的 `tg_id`；未调用 `syncAgents`，身份/联系人状态不一致 | 导入时 `tgId=0`、生成新 id、`syncAgents`，必要时自动加“副本”后缀 |

## 2. 其余高价值问题

- **P2** 复制/重命名后欢迎语仍用旧名：改名时更新或删除旧欢迎消息。
- **P2** `.oxagent` 中文文件名变 `____A.oxagent`：导出文件名做 Unicode 安全化并保留可读部分。
- **P2** Markdown 未渲染：本地 Agent 回复中的 `**`、反引号按字面显示；考虑在消息实体里生成 bold/code entities。
- **P2** 文件气泡 `JPG档案/PDF档案/TXT档案`：改“JPG 文件/PDF 文件/文本文件”。
- **P2** 设置页最后一行被底部 Tab 遮挡：设置列表增加底部 padding。
- **P2** 原始异常直接显示（HTTP 401/EOFException/发送错误）：统一人话文案 + 重试入口。
- **P3** 工具权限无法恢复“默认 · 自动”：增加“恢复默认/继承”。
- **P3** 工作区无“新建目录”，路径隐式创建；增加入口或提示。
- **P3** 群 `@` 显示 `@agent_17` 而非显示名。
- **P3** 添加成员确认“添加1成员”缺空格、成员上限格式不统一。

## 3. 修复与验证顺序

1. **0.8.104（P1）**
   - `refreshDialogRow` 最新消息 id；重开 Agent 会话回归。
   - `onGroupMessage` 的 @ 路由；用 `@agent_<B>` 验证只由 B 回复。
   - 本地群图片/文件消息本地持久化；验证无红叹号、缩略图、群回复。
   - `.oxagent` 导入新身份 + `syncAgents`；验证联系人/聊天列表各一条、群成员可加入。
2. **0.8.105（P2）**
   - 欢迎语、导出文件名、Markdown、文件类型词、设置底部 padding、错误文案。
3. **0.8.106（P3/兼容）**
   - 工具默认值、工作区目录、@ 显示名、文案细节。
   - Kelivo/RikkaHub MCP JSON 导入、主题 JSON 导入、SKILL.md 导入（按 `reference-gap-analysis.md` 的顺序）。
4. **回归**
   - 重跑三份“酒吧炒饭”skill 的关键路径；必要时启动第二轮三子代理测试。
   - 每轮更新构建报告、QA 文档、memory，提交并生成补丁；公开仓库只推脱敏镜像。

## 4. 证据

- `docs/bar-rice-test-A.md`、`docs/bar-rice-test-B.md`、`docs/bar-rice-test-C.md`（待生成）。
- 截图：`/tmp/bar-rice-A/`、`/tmp/bar-rice-B/`、`/tmp/bar-rice-C/`。
