# QA — alpha 0.63.0（New Group 离线分支 → 本地群端到端）

- 版本：`0.63.0`，versionCode **6309**（`0.62.0` 为 6209）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-29 00:20–01:10（CST）
- 目标：0.62.0 遗留的群组缺口——`OxAgentsBridge.createLocalGroup` 没有调用
  方，Telegram UI 的 New Group 流程在离线本地账号上停在服务器请求。本轮把
  New Group 离线分支接到 `createLocalGroup`，并用真实 UI 流完成
  “创建本地群 → OxUI 面板列出 → 群内发送 → Telegram UI 同步”的端到端验证。
- 版本规则：新增用户可见的群组创建路径与行为，按 minor 从 0.62.0 →
  **0.63.0**（6309），patch 清零。

## 1. 实现

`MessagesController.createChat`（`CHAT_TYPE_CHAT && !forImport` 分支）在
`syncAgents(false)` 之后新增 agent-only 分支：

- 仅当 `OxMode.isAgentOnly()` 时生效；普通账号不受影响。
- 把 `selectedContacts` 逐一映射为本地 agent（`agentByTgId`），映射不到的
  （真实联系人）直接跳过——离线无法邀请真实用户；agent 集为空时发布
  `chatDidFailCreate`，让创建页恢复可编辑，而不是像旧行为那样把请求发给
  一个永不回调的连接（ConnectionsManager 在 agent-only 下丢弃所有请求）。
- agent 集非空时调用 `OxAgentsBridge.createLocalGroup(title, agents)`（本轮
  从 private 改为 public；内部打 `markLocallyCreated` 标记、persist
  chat/full、创建 service message、refreshDialogRow），然后
  `runOnUIThread` 发布 `chatDidCreated(chatId)`；整个分支 try/catch，任何
  异常都会让创建页可恢复。
- `runOnUIThread` 发布通知（与 stock 行为一致），避免在 createChat 内
  重入 GroupCreateFinalActivity 的通知处理。

本轮仍按用户 token 策略后台并行了 2 个子代理：一个对离线分支做代码评审
（HIGH 发现：agent-only 下永远不能 fall through 到 sendRequest、混合选择
要干净失败、createAfterUpload+头像上传在离线下会死锁——本轮 QA 未选头像
所以未触发，头像离线分支列为下轮工作），一个做 OxUI 视觉第三轮评审
（产出 12 项 polish 清单，归入后续轮次）。

## 2. 设备验证（端到端，全部真实 UI 流）

1. **创建**：Telegram UI → Chats → FAB → New Group → 勾选本地 agent
   `Studio Test` → 命名 `OxUI QA 630` → ✓。logcat：
   `createLocalGroup id=1800000000 agents=1`、
   `persistAndShow shown did=-1800000000 id=100075 out=true`（create
   service message）；`ox_groups.xml` 出现
   `chat_1800000000=oxui_grp_630` + `created_1800000000=true`；
   ChatActivity 直接呈现“OxUI QA 630 · 2 members”与 create 提示。
2. **OxUI 列表**：切到 OxUI root → Agents 面板：agent 列表照常，
   **Groups** 区出现 `OxUI QA 630 · Agents in chat: 1`（标题在冷启动后
   由 `MessagesStorage.getChatSync` 回填，截图
   `qa-0.63.0-oxui-groups-panel.png`）。
3. **群内发送（OxUI）**：点击群行 → rail 出现 ink 群 chip
   `👥 OxUI QA 630`（选中态）；发送 `GROUP-TEST-630` → logcat
   `runRound dialog=-1800000000`、用户消息 `id=100076 out=true`、群内
   agent（echo）回复 `id=100077 out=false`；OxUI 渲染 ink 用户气泡 +
   chartreuse 回复行（“echo mode” 是该 agent 的诚实离线回复）；
   截图 `qa-0.63.0-oxui-group-chat.png`。
4. **Telegram UI 同步**：Telegram chats 列表顶部出现
   `OxUI QA 630 · 12:57 AM`（即 OxUI 发出的那条）。
5. **0.62 → 0.63 期间发现并修复的回归**：
   - `renderGroupList` 开头的 `removeAllViews()` 会把同列表里已渲染的
     agent 行清掉（面板只剩群组区）；改为只追加。
   - 冷启动时 `MessagesController.getChat` 缓存未就绪导致群标题显示
     `Group 1800000000`；后台线程回退 `MessagesStorage.getChatSync`。
   - rail 群 chip 在 0.62 的 git checkout/重放中丢失、agent chip 点击不
     清除群目标；两处补齐并验证（选群后 rail 出现 ink 群 chip，点
     agent chip 恢复 1:1）。
6. **构建/稳定性**：release 0.63.0（6309）覆盖安装启动，无本包
   FATAL/ANR；无障碍重新绑定，代理保持 `10.0.2.2:7890`；桥接 15 个
   测试通过。

## 3. 产物

- release：`dist/OrionekoX-0.63.0-alpha-arm64-x64.apk`，sha256
  `1160c2ed6b62df5c824fefd2aff7f1128f0bebdb4b7fe1e230d227d04d250cb4`
- debug：`dist/OrionekoX-0.63.0-debug-arm64-x64.apk`，sha256
  `25a4179a82fd20f1cf41c911845beca968919a0cabe416767e6ee342c8455d28`
- 构建日志：`/tmp/ox-build-0630-final.log`

## 4. 清理

- 测试 Agent `oxui_grp_630`（chat/dialog/messages）、QA 群
  `OxUI QA 630`（chats blob、dialogs、messages、`ox_groups.xml` 的
  chat_/created_/log_ 键）已全部删除，计数 0；`interface_mode` 恢复
  `telegram`。未 `pm clear`，未向真实联系人发消息。

## 5. 边界 / 下一步

- 头像：New Group 选头像会走 FileUploader，离线 agent-only 下上传请求
  同样被丢弃（子代理 HIGH 发现），本轮 QA 未选头像；下轮给本地群加
  头像离线分支（`createLocalGroup` 后本地设置 chat.photo）。
- 群回复目前用群名做 meta（`OxChatEntry` 无 sender 字段）；按视觉评审
  item 11，下轮加 sender 归属与“Reply to <agent>”定向。
- 视觉评审其余项（图片+说明一体化、图片高度上限、Preview 徽章去按钮
  化、rail 渐隐、类型尺度归一等）归入后续轮次。
- Claude 403、OxLinux exec 限制未变；第三轮 A/B/C 未启动。
