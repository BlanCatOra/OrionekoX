# QA — alpha 0.24.0（RikkaHub 聊天数据库文本历史导入）

- 版本：`0.24.0`，versionCode **2409**（`0.23.0` 为 2309）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 02:26–02:38（CST）
- 目标：继续补完 RikkaHub 备份导入：读取备份 zip 内的 `rikka_hub.db`，把
  每个会话按“消息节点当前分支”提取文本历史，追加到对应本地 Agent 的聊天。
- 版本规则：新增一类备份数据（聊天历史）的导入能力，按 minor 从 0.23.0 →
  **0.24.0**，versionCode 2409（240×10+9）。

## 1. 新增能力

- 备份 zip 额外收集根目录/嵌套的 `rikka_hub.db`（上限 64 MB）。
- 预览新增“历史：会话 N 个 · 消息节点 M 条”。
- 导入只读打开数据库：
  - `conversationentity` 按 `create_at` 升序，最多 200 个会话；
  - `message_node` 按 `node_index` 升序，每个会话最多 500 个节点；
  - 每个节点读取 `messages[select_index]`（当前分支），不是所有分支；
  - 只取 `role=user/assistant` 的 `parts[].type=text`，其他 part 跳过；
  - 文本上限 100,000 字符，总消息上限 5000 条。
- `assistant_id` 映射到导入的 Agent id `rikka_<uuid>`；找不到对应 Agent 的
  会话跳过。
- 时间戳使用 `create_at + 节点顺序 × 1000` 生成，保证同一备份重复导入稳定；
  按 `role + content + ts` 去重，重复导入不会追加副本。
- 结果 notes 显示“已导入历史消息 N 条”；数据库读取失败只记录 note，不影响
  助手/技能/MCP 导入。

## 2. 设备验证 — PASS（schema 样例）

构造 SQLite 样例：`conversationentity` 2 个会话、`message_node` 4 个节点；
其中第 3 个节点的 `messages` 有两个分支，`select_index=1` 指向
`Branch B selected`；第二个会话有一条 `Second chat message`。

- 预览：`助手 2 · Provider 1 · MCP 1 · 技能 1`，
  `历史：会话 2 个 · 消息节点 4 条`，并提示只导入文本历史、跳过图片/工具/附件。
- 导入结果：`新建助手 2，更新 0，新增 MCP 1，跳过 MCP 0，导入技能 1`，
  note `已导入历史消息 4 条`。
- `rikka_4444…/chat.json` 为 4 条：
  - `user: Hello from RikkaHub history`
  - `assistant: Hi, imported reply.`
  - `user: Branch B selected`（未导入 Branch A）
  - `user: Second chat message`
- 重复导入：`新建 0，更新 2，新增 MCP 0，跳过 MCP 1，导入技能 1`，
  `chat.json` 仍为 4 条，没有重复。
- 截图：`docs/screenshots/qa-0.24.0-history-result.jpg`。
- 清理：删除两个测试助手、RikkaTestMCP、TestSkill、Downloads 样例与缓存 DB；
  无 Key 泄漏、无 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 只导入当前选中分支；其他编辑分支、被删除分支不导入。
- 图片、文件、工具调用、推理内容等非 text part 全部跳过；附件不复制。
- 同一 assistant 的多个 RikkaHub 会话合并到同一个 OrionekoX Agent 聊天，
  会话标题不保存，无法按原会话分组。
- 时间戳是确定性的合成值（`create_at + order×1000`），不是原消息精确时间。
- 样例是按当前 RikkaHub 源码表结构构造；真实导出 DB（迁移版本/表名/超大数据）
  仍待复测；`conversationentity`/`message_node` 读取失败会降级为 note。
- 未导入 memory、workspace、lorebook、folder 等关联数据。

## 4. 产物

- `dist/OrionekoX-0.24.0-alpha-arm64-x64.apk`（versionCode 2409），sha256
  `c939c48301f7d04686b438749ad43933daa18b9d97e16ce6a439a35afe23f01b`
- `dist/OrionekoX-0.24.0-debug-arm64-x64.apk`，sha256
  `bc7247e7f0b718643042591e9602e13ad80e5ad57a9ec6291c0ee2b4fd499abf`
