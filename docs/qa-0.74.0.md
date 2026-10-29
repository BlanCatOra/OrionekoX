# QA — alpha 0.74.0（消息删除 + Agent 置顶 + 记忆架构研究报告）

- 版本：`0.74.0`，versionCode **7409**（`0.73.0` 为 7309）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口）
- 时间：2026-10-29 14:05–14:35（CST）
- 目标（OxUI 对标主线）：① 消息删除（OxUI 菜单 + 二次确认 + 本地
  store 与 messages_v2 同步）；② Agent 置顶/取消置顶并跨重启保留；
  ③ 交付记忆库混合方案研究报告（评估，不实现）。

## 1. 实现

1. **消息删除**
   - `OxAgentStore.removeChatEntry(id, ts, role)` /
     `OxGroupStore.removeEntry(chatId, ts, role)`：按 ts+role 删除首条。
   - `OxAgentsBridge.deleteOxEntry(dialogId, ts, role, text)`：先删 store
     条目，再在 messages_v2 里定位 TL 消息并调用
     `MessagesController.deleteMessages(..., cacheOnly=false)`；后者已有
     agent-dialog 分支强制 cacheOnly，因此不会向服务器发请求。
   - TL mid 定位：`uid=dialogId`、date 在 entry ts 前后 300s，反序列化
     `data` 比对正文前 24 字符（用户消息的 TL date 可能比 store ts 早
     十几秒——store 在回合结束时落盘）；无正文匹配时仅在 8s 内按最近
     时间兜底，避免误删。
   - OxUI 菜单：助手行（Copy / Reply(群) / Forward / Delete message）与
     用户气泡（Copy / Forward / Delete）以及图片、文件气泡长按菜单；
     「Delete message」再弹二次确认（Delete / Cancel），删除项用 CORAL
     圆点标记。菜单 accent 参数由 boolean[] 改为 int[] 颜色。
2. **Agent 置顶**：Agent 详情页 Chat / Edit 旁新增 Pin（已置顶时显示
   Unpin）；切换 `OxAgent.pinned` 后 saveAgent、重载列表并刷新 rail；
   `listAgents` 原本已按 pinned 排序。
3. **研究报告**：`docs/memory-architecture-report.md`——对照现有
   OxMemoryStore（SQLite 元数据 + BM25 + 遗忘曲线 + MMR + invalid_at
   事实失效）评估“Markdown 本体 + 关键词/向量双检索”方案，指出五处
   缺口（稳定 ID/people/topics/status、显式 supersedes、CORE 常读层、
   真实 embedding、recall@k/MRR 基准），给出 0.75→0.77 分阶段建议；
   明确现有 embed 是 FNV 哈希投影，不得当作语义向量。

## 2. 设备验证（release 7409）

1. **删除用户消息**：**Bold** Card 发送 `delete074test`（模型约 19s
   后回复）→ 长按用户气泡 → 菜单出现 Delete message（截图 12）→
   二次确认 Delete（截图 13）→ logcat
   `deleteMessages local-only did=555000023 count=1 cacheOnly=true` /
   `deleteOxEntry ... role=user store=true mids=1`；cache4.db 中
   mid=101052 行消失；chat.json 对应用户条目移除；OxUI 气泡消失，
   Toast "Message deleted"。
2. **删除助手消息**：长按长回复 → Delete message → Delete → logcat
   `role=assistant store=true mids=1`；mid=101053 行删除；store 只剩
   两条 tool 记录；Telegram UI 打开该会话仅显示两条工具消息，已删除
   的用户/助手消息不再出现（截图 14）。
3. **Agent 置顶**：Agents 面板长按 API Agent → 详情出现 Pin（截图 15）
   → 点击后按钮变 Unpin、Toast Pinned；agent.json 写入
   `"pinned":true`；rail 顺序变为 API Agent → **Bold** Card →
   barB-nokey（截图 16）；force-stop 重启后顺序不变（截图 17）；再次
   Unpin 后顺序恢复 **Bold** Card 首位、`pinned":true` 计数为 0。
4. **构建**：release/debug 7409 安装启动无本包 FATAL；release 版本名
   0.74.0、lastUpdateTime 14:33。

## 3. 产物

- release：`dist/OrionekoX-0.74.0-alpha-arm64-x64.apk`
  sha256 `c4686060f2cefaa6b2f736c3d7e22a7731b0d54b41c20f3999b8938122c7eaf0`
- debug：`dist/OrionekoX-0.74.0-debug-arm64-x64.apk`
  sha256 `00cf1a3f1782630d453f5149638b232323fe31c2c4127a0506f7b0e8b2edc0dd`
- 截图 `/tmp/s3.png`–`/tmp/s11.png`（菜单、确认、置顶、Telegram 同步）。

## 4. 边界 / 下一步

1. 删除依赖“ts + 正文前 24 字符”定位 TL 行：同会话 5 分钟内出现相同
   开头且无正文匹配时可能删错；无正文（纯图片/文件说明为空）仅在 8s
   内按时间兜底。后续可在 OxChatEntry 持久化真实 mid，彻底消除启发式。
2. 仅覆盖 OxUI 发起的删除；在 Telegram UI 删除不会反向清理 OxUI 的
   chat.json（会由下一次刷新继续显示）——记为下一轮。
3. 置顶只影响 OxUI rail 顺序；Telegram 联系人列表不排序（pinned 字段
   已存在但未用于 TL 列表）。
4. 下一步：消息编辑、表情反应、删除反向同步、会话列表管理
   （置顶/归档/重命名）、视频/GIF 附件。
