# QA — alpha 0.76.0（OxUI 消息表情反应 + 群组列表常刷新）

- 版本：`0.76.0`，versionCode **7609**（`0.75.0` 为 7509）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口）
- 时间：2026-10-29 15:20–15:46（CST）
- 目标（OxUI 对标主线）：① 消息表情反应（1:1 与本地群）；
  ② 修复 OxUI 存活期间在 Telegram UI 新建群组后，返回 OxUI 面板看不到
  新群的问题。

## 1. 实现

1. **表情反应（OxUI 本地）**
   - `OxChatEntry.reaction`（JSON `reaction`，向后兼容）；`OxAgentStore.
     updateReaction` / `OxGroupStore.updateReaction`。
   - `OxAgentsBridge.reactOxEntry(dialogId, ts, role, emoji)`：只写
     OxChatEntry，空字符串表示清除；不写 messages_v2。
   - OxUI：助手、用户文本、图片、文件菜单增加 `React…`；点击弹出锚定
     的 6 键 emoji 条（👍 ❤️ 😂 🎉 🤔 👀），当前反应高亮并在下方多
     一行 `Remove reaction`；再点同一 emoji 即清除。气泡下方渲染
     PAPER 胶囊 `emoji  1`（用户在右、助手在左）。
2. **为什么不同步到 Telegram UI**：本机 Telegram 反应渲染依赖
   `MediaDataController.getReactionsMap()` 里的服务端 reactions 目录
   （含 emoji 图标 document）。离线/本地 agent 环境该目录为空，写入
   messages_v2 后 Telegram UI 只显示空白圆形 chip（本轮实测截图
   r2/r6：用户消息出现无 emoji 的白圆，助手消息同）。因此回退为
   OxUI 本地反应，不污染 Telegram 数据；待有 reactions 图标缓存后可
   再加同步。
3. **群组列表常刷新**：新增 `onResume()` → `loadGroupsAsync()`（有
   groupsLoading 防重入）。此前 Activity 存活期间在 Telegram UI 建的
   群不会出现在 OxUI 面板，必须重启 Activity。

## 2. 设备验证（release 7609）

1. **1:1 反应**：**Bold** Card 发送 `react076_test` → 长按用户气泡 →
   `React…` → 👍：logcat
   `reactOxEntry did=555000023 role=user emoji=👍 store=true`；气泡下
   出现 `👍 1`。助手回复长按 → React… → ❤️：
   `role=assistant ... store=true`，出现 `❤️ 1`。
2. **持久化**：force-stop 重新打开 OxUI，两个反应胶囊仍在；再次打开
   助手选择器时出现 `Remove reaction`，改选 🎉 后胶囊变为 `🎉 1`，
   logcat 无 tl 字段（store-only）。
3. **群组反应**：Telegram UI 新建群 1800000010（Echo）→ 返回 OxUI，
   面板底部 Groups 出现新群（onResume 刷新生效）→ 群内发送
   `groupreact076` → 长按 → React… → 😂：
   `reactOxEntry did=-1800000010 role=user emoji=😂 store=true`，
   群气泡下出现 `😂 1`。
4. **Telegram UI 反例**：把反应写入 messages_v2 后，Telegram UI 显示
   无 emoji 的空白圆形 chip（见 §1.2），确认不可用后已回退。
5. **清理**：删除 QA 群 1800000010（messages_v2/dialogs/chats/
   chat_settings_v2/ox_groups 键）与本轮 1:1 测试消息
   mid 101062/101063、chat.json 中 ts=1793258945709 的条目，修复
   dialogs.last_mid 指向剩余最后一条；重启后首页干净（截图 f076.png）。

## 3. 产物

- release：`dist/OrionekoX-0.76.0-alpha-arm64-x64.apk`
  sha256 `d7889a723d795ded7abb316a3e07f0c75b08992eed8407e9d3a37a55743865a4`
- debug：`dist/OrionekoX-0.76.0-debug-arm64-x64.apk`
  sha256 `7c8269c76b7eaa62211690a85fe0b504f7105a58ba05dbf9723c2f78b81e4c43`
- 截图 `/tmp/r2.png`（Telegram 空白 chip 反例）、`/tmp/f076.png`（清理后）。

## 4. 边界 / 下一步

1. 反应仅在 OxUI 可见，不同步 Telegram UI（原因见 §1.2）；每条消息
   只保留一个反应（单 emoji、计数固定 1），无多人/多表情堆叠。
2. 反应不进入消息正文，不影响模型上下文与记忆。
3. `React…` 与 `Remove reaction` 为英文文案，未进中文资源包。
4. 下一步：会话列表管理（置顶/归档/重命名）、编辑角标、Telegram UI
   侧删除反向同步、视频/GIF 附件。
