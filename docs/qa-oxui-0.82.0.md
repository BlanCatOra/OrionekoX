# QA — 0.82.0（OxUI 8.2：编辑角标）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。
> 日期：2026-10-29 · 分支：`orioneko/0.80-memory-stage-c` · 基线：`ad76d66`（OxUI 8.1）

## 背景

0.75 起 OxUI 支持编辑用户消息：保存时**原地重写** `OxChatEntry.content` 与 Telegram
`messages_v2` 行（同 mid/时间）。结果 Telegram 侧显示「edited」角标，但 **OxUI 自己
的气泡没有任何已编辑标记**——`docs/oxui-design.md` 第 151-152 行与 parity 1474 行都
记录了这个不对称。8.2 就是补上 OxUI 侧的「已编辑」角标。

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 8.2 编辑角标 | `OxChatEntry.edited`（`chat.json`/群 log 持久化，缺省 false）；编辑保存时置 true；用户/助手文本气泡在时间戳后渲染 ` · edited` | 完成 |

## 实现要点

- `OxChatEntry`：新增 `boolean edited`；`fromJsonArray` 读 `edited`，`toJsonArray` 写 `edited`（只写 true，保持文件紧凑且向后兼容）。
- 持久化置位：`OxAgentStore.updateChatEntry` 与 `OxGroupStore.updateEntry` 在重写 `content` 的同一分支置 `e.edited = true`，覆盖 1:1 与本地群。
- 渲染：`OxUiPreviewActivity`
  - `addUser(..., boolean edited)`、`addAssistant(..., boolean edited)` 新增参数；
  - `userTimestamp(ts, edited)` 输出 `HH:mm · edited`；助手 meta 行输出 `名字 · HH:mm · edited`；
  - 文件气泡（caption 尚不可编辑）与发送中气泡传 `false`，不误标。

## 行为边界

- 只标记「用户主动编辑」的文本消息；助手消息若经同一 `editOxEntry(role="assistant")` 通道改写也会被标记（当前 UI 只对用户气泡开放编辑入口，故实际以用户为主）。
- 图片/文件说明暂不可编辑 → 不显示角标（与 design doc「Media captions are not editable yet」一致）。
- 旧数据缺 `edited` 字段按 false，不误标历史消息。

## 构建

- `taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug` → 结果见提交说明（本轮已跑，见 CHANGELOG/AUTOPILOT 日志）。

## 诚实缺口

1. **未做模拟器截图取证**：本项仅验证编译与构建；「编辑一条消息后气泡出现 · edited」需在 `emulator-5554` 的 OxUI 前端实测截图。逻辑为纯本地字段 + 文本拼接，但按诚信原则不谎称端到端已验证。
2. Telegram 侧与本地的「edited」是两套独立标记（Telegram 由 messages_v2 自身管理，OxUI 由 `OxChatEntry.edited` 管理）；若未来从 Telegram 侧编辑、再回 OxUI，OxUI 不会自动得知——本轮只在 OxUI 内编辑时置位，跨端回写标记留待 8.3 相邻能力。
