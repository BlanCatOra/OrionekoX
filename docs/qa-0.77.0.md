# QA — alpha 0.77.0（Telegram UI 删除反向同步，Agent 会话）

- 版本：`0.77.0`，versionCode **7709**（`0.76.0` 为 7609）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口）
- 时间：2026-10-29 15:58–16:12（CST）
- 目标（OxUI 数据一致性）：在 Telegram UI 删除 Agent 会话消息时，同步
  移除 OxUI 的 `OxChatEntry`，不再出现“Telegram 已删、OxUI 仍在”的幽灵。

## 1. 实现

1. `OxAgentsBridge.onNativeMessagesDeleted(dialogId, mids)`：
   - 只处理 Agent 会话（`isAgentDialog`）；
   - 在 MessagesController 真正删库前，按 mid 读取 messages_v2 的
     date + data，反序列化出正文，生成前 24 字符 needle；
   - 在 `OxChatEntry` 列表里按正文前缀匹配对应条目；正文为空（纯媒体）
     时取与消息时间差 ≤10 分钟且最近的条目；
   - 通过 `removeChatEntry(ts, role)` 删除条目并记录
     `onNativeMessagesDeleted did=... mids=N removed=M`。
2. `MessagesController.deleteMessages` 的 agent-dialog 本地分支中，在
   `cacheOnly = true` 之后调用上述方法（先读后删，行还在）。
3. 群组反向同步**未接线**：本地群聊天窗口在本模拟器上打开即导致 qemu
   进程退出（两次复现，`adb devices` 变空），无法完成端到端验证；为
   避免上线未验证行为，最终构建只保留 Agent 会话路径。`onNativeMessagesDeleted`
   内部保留群组支持，供以后接线。

## 2. 设备验证（release 7709）

1. OxUI 向 **Bold** Card 发送 `revdel077_a`（模型回合产生 user 条目 +
   3 条 tool + assistant 回复，均落 chat.json）。
2. Telegram UI 打开该会话 → 长按用户消息 → 右上删除 → 确认“删除”：
   - logcat：`onNativeMessagesDeleted did=555000023 mids=1 removed=1`，
     随后 `deleteMessages local-only ... count=1 cacheOnly=true`；
   - chat.json 中 `revdel077_a` 用户条目消失，assistant 与 tool 条目保留
     （只删了这一条），OxUI 重新打开后不再显示该用户消息。
3. 群组路径尝试：本地群 1800000011（Echo）内发送 `grouprevdel077` 成功，
   但打开该群的 Telegram 聊天窗口时模拟器两次整体退出（qemu 进程消失，
   非 App FATAL），故群组反向同步未验证、未接线。
4. 清理：删除群 1800000011 的 messages_v2/dialogs/chats/
   chat_settings_v2/ox_groups 键；删除本轮 1:1 测试消息
   mid 101068–101071、chat.json ts≥1793260800000 的条目并修正
   dialogs.last_mid；最终截图首页干净（f077.png）。
5. 模拟器本轮共 3 次 qemu 退出（含打开群聊的 2 次），均以
   `-no-window -gpu off` 冷启动恢复；数据持久，无 App 崩溃日志。

## 3. 产物

- release：`dist/OrionekoX-0.77.0-alpha-arm64-x64.apk`
  sha256 `963fe98b68a923927cdad3a0dbae43bcc8883a64ad8adf6be9296b54ac8ae8b8`
- debug：`dist/OrionekoX-0.77.0-debug-arm64-x64.apk`
  sha256 `b61a0d2769af5ace2bd4f462f5ff7e900e3964c1836494ff0b55fbf8896410eb`
- 截图 `/tmp/d2.png`（删除前 Telegram 会话）、`/tmp/f077.png`（清理后）。

## 4. 边界 / 下一步

1. 只覆盖 Agent 会话；本地群的反向同步因模拟器崩溃暂缓（代码内已保留
   群组处理分支，等能稳定打开群聊后再接线）。
2. 只挂在 `MessagesController.deleteMessages`；通过“清空历史”等其他
   路径删除的消息不在覆盖范围。
3. 匹配仍是正文前缀 + 时间近邻的启发式：同会话 5 分钟内相同开头可能
   误删；纯媒体靠 10 分钟时间窗。
4. 编辑/反应历史不会因 Telegram 侧删除而回滚（本来也不存在）。
5. 下一步：会话列表管理（置顶/归档/重命名）、编辑角标、群组反向同步
   在稳定环境复验、视频/GIF 附件。
