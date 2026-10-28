# QA — alpha 0.43.2（Chatbox 回环保持 Agent 身份）

- 版本：`0.43.2`，versionCode **4329**（`0.43.1` 为 4319）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 11:50–12:02（CST）
- 目标：导出 Chatbox 备份后，在保留原 Agent 的情况下重新导入，应更新原
  Agent，而不是生成 `_current` 副本。
- 版本规则：这是回环身份 bug 的小范围修复与 QA，按 patch 从 0.43.1 →
  **0.43.2**，versionCode 4329（432×10+9）。

## 1. 问题与修复

- 0.43.1 回环时，导出 session 的 id 已经是完整 Agent id
  （`kelivo_chat_s1_current`），导入器又统一加 `kelivo_chat_` 前缀并追加
  `_current`，实际生成 `kelivo_chat_kelivo_chat_s1_current`；保留原 Agent
  再导入会新增副本。
- 导出器在 session 中增加扩展字段 `orionekoAgentId`（原 Agent id）。
- 导入器新增 `Conversation.agentIdOverride`：存在该字段时，预览计数和
  实际导入都直接使用该 id，不再二次加前缀；仍经过
  `[a-zA-Z0-9._-]` 清洗、去前导点、80 字符截断，避免路径穿越。
- 没有该字段的外部 Chatbox 备份继续走原来的 `kelivo_chat_<session>_current`
  逻辑。

## 2. 设备验证 — PASS

准备：

- 导入 2,392 B 混合附件样例，得到 Agent id `kelivo_chat_s1_current`、
  tgId `555000091`，`chat.json` 5 条。
- 在 Agent 列表长按“导出 Chatbox 备份”，导出 3,593 B：
  session id 与 `orionekoAgentId` 均为 `kelivo_chat_s1_current`，
  5 条消息、2 个 image、2 个 raw-attachment。

回环：

- 保留原 Agent 直接导入导出的 zip。
- 预览显示“已存在 1 段对话，将替换历史”。
- 结果：“已存在的 1 段对话历史已替换”“导入聊天图片 2 张”
  “导入聊天附件 2 个”。
- Agent 目录仍只有 `kelivo_chat_s1_current`，没有 `_current` 副本；
  tgId 仍为 `555000091`；`chat.json` 仍 5 条，原生 `messages_v2` 仍 5 行。
- 原生聊天显示两张照片、`notes.txt`（19 B · TXT）、`data.json`
  （29 B · JSON）和助手回复。
- 截图：`docs/screenshots/qa-0.43.2-stable-id.jpg`。

清理：

- 删除测试 Agent、样例与两个导出 zip 后重启，对应 dialogs/messages 均为
  0 行；无本包 FATAL/ANR；已重新启用
  `org.telegram.ox.OxAccessibilityService`。

## 3. 未覆盖 / 已知边界

- `orionekoAgentId` 是 OrionekoX 扩展字段；真实 Chatbox/Kelivo 客户端会
  忽略它，外部备份仍按原规则生成新 id。
- 本轮只覆盖有会话的 Agent；无聊天的 copilot 导出未验证身份保持。
- 若 Agent id 含非法字符或超过 80 字符，导入时会清洗/截断，可能无法与
  原 id 完全一致。
- 未与真实 Chatbox 客户端互导。

## 4. 产物

- `dist/OrionekoX-0.43.2-alpha-arm64-x64.apk`（versionCode 4329），sha256
  `7e8d41863a035c2e83c464dee3f1f745394a11d15d6d9920892615d166de2db0`
- `dist/OrionekoX-0.43.2-debug-arm64-x64.apk`，sha256
  `a359796b9c270271140b15250ce7bd78908b9ae74503f47040f15eaca577fdc0`
