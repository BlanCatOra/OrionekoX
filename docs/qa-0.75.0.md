# QA — alpha 0.75.0（OxUI 消息编辑 + 群组面板冷启动修复）

- 版本：`0.75.0`，versionCode **7509**（`0.74.0` 为 7409）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口）
- 时间：2026-10-29 14:45–15:12（CST）
- 目标（OxUI 对标主线）：① 用户文本消息编辑（1:1 与本地群），
  store 与 messages_v2 同步；② 修复 Agents 面板冷启动时 群组列表
  可能缺失的竞态。

## 1. 实现

1. **编辑消息**
   - `OxAgentStore.updateChatEntry(id, ts, role, content)` /
     `OxGroupStore.updateEntry(chatId, ts, role, content)`：按 ts+role
     改写首条内容。
   - `OxAgentsBridge.editOxEntry(dialogId, ts, role, oldText, newText)`：
     先改本地条目，再用与删除相同的 mid 定位逻辑找到 messages_v2 行，
     原地重写 `data`（message 文本、entities 清空、FLAG_15 + edit_date），
     不改变 mid/date，历史顺序不变；随后 `replaceMessagesObjects` +
     `dialogsNeedReload` 通知 Telegram UI，并刷新 dialog 行。
   - OxUI：用户文本气泡菜单变为 Copy / Forward to… / Edit message /
     Delete message（发送中的 pending 消息不显示 Edit）；Edit 打开
     in-design 编辑器（预填原文、多行、Cancel/Save），空文本拒绝保存，
     未修改直接关闭。
2. **面板冷启动竞态**
   - `loadGroupsAsync` 增加 `groupsLoading/groupsLoaded` 防重入；
     `showAgentPanel` 在未加载时主动触发加载；加载完成后若面板仍打开
     则重绘一次，避免“先开面板、后加载完”导致 Groups 区缺失。
   - 加载异常不再静默：`Log.e("OxUI", "loadGroups failed", t)`。

## 2. 设备验证（release 7509）

1. **1:1 编辑**：**Bold** Card 发送 `edit075_original` → 长按 →
   `Copy / Forward to… / Edit message / Delete message`（截图 e0/e1）→
   Edit → 编辑器预填 → 末尾追加 `_edited` → Save：
   - logcat `editOxEntry did=555000023 role=user store=true mid=101054 tl=true`；
   - OxUI 气泡即时变为 `edit075_original_edited`；
   - 首次保存时 `MessageObject` 构建抛 NPE（`applyEntities` 不接受
     entities=null），TL 已写入但 UI 未刷新；改为空 ArrayList 后重新编辑
     成功，logcat 无异常（见 R2-180）。
2. **Telegram UI 同步**：打开 Telegram UI → **Bold** Card 会话，显示
   `edit075_original_edited2` 且带“已编辑”标记，无崩溃；说明 data
   blob 原地重写与 replace 通知均生效（截图 e2）。
3. **群组编辑**：Telegram UI 新建本地群（id=1800000009，**Bold** Card）
   → OxUI Agents 面板底部 Groups 选中 → 发送 `groupedit075_original` →
   长按 → Edit → 追加 `_g` → Save：
   - logcat `editOxEntry did=-1800000009 role=user store=true mid=101060 tl=true`；
   - OxUI 气泡变为 `groupedit075_original_g`；ox_groups.xml 中
     log_1800000009 条目内容同步更新。
4. **面板竞态**：冷启动后打开 Agents 面板并滚到底，Groups 区与测试群
   可见；此前版本需重新打开面板才出现（在 0.75 实现前观察到）。
5. **清理**：删除 QA 群 1800000009（messages_v2/dialogs/chats/
   chat_settings_v2/ox_groups 键）与本轮 1:1 测试消息
   mid 101054–101059、chat.json 中 ts≥1793256000000 的条目；保留此前
   的工具历史。重启后 OxUI 首页干净（截图 final.png）。

## 3. 产物

- release：`dist/OrionekoX-0.75.0-alpha-arm64-x64.apk`
  sha256 `131a6414bf10e85dd0353ce8a61653a92e315fab000aedfca555e6696984a8a5`
- debug：`dist/OrionekoX-0.75.0-debug-arm64-x64.apk`
  sha256 `e567d8b4b87ce66bf4984d431fbd435ed300c129628f6796049efbc6b0e34fd4`
- 截图 `/tmp/e0.png`–`/tmp/e11.png`、`/tmp/final.png`。

## 4. 边界 / 下一步

1. 仅支持用户**文本**消息编辑；图片/文件的说明（caption）编辑未做，
   助手消息不可编辑（与 Telegram“只能编辑自己消息”一致）。
2. OxUI 气泡本身不显示“已编辑”角标；Telegram UI 有。后续可在
   OxChatEntry 增加 edited 标记并渲染。
3. mid 定位仍是 ts+正文前 24 字符的启发式，与删除共用；同会话 5 分钟
   内相同开头存在误配风险，长期应在 OxChatEntry 持久化真实 mid。
4. 编辑不会重跑该轮对话，也不会回写记忆蒸馏结果——只改展示与后续
   上下文原文。
5. 下一步：表情反应、Telegram UI 侧删除反向同步、编辑角标、会话列表
   管理（置顶/归档/重命名）。
