# QA — 0.83.0（OxUI 8.3：群组反向同步）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。
> 日期：2026-10-29 · 分支：`orioneko/0.80-memory-stage-c` · 基线：`9cc4349`（8.3.2）

## 背景

0.77 引入「反向删除同步」：当消息在 Telegram UI 中被删除时，若该对话是 agent 对话，
`MessagesController.deleteMessages` 的 shunt 会置 `cacheOnly` 并调用
`OxAgentsBridge.onNativeMessagesDeleted`，按内容前缀/最近时间戳把对应的 `OxChatEntry`
从本地存储中裁剪掉，使 OxUI 与 Telegram UI 保持同步。

但当时该 shunt **只判断 `isAgentDialog`**，本地群（dialogId<0 且由 OrionekoX 离线创建）
走的是普通删除分支：既不会裁剪 OxUI 群记忆日志（`ox_groups` 的 `log_<chatId>`），
也不会 `cacheOnly`，离线本地群却尝试向服务器发删除请求。这就是 8.3 要补的洞。

`docs/oxui-design.md` 第 136-140 行亦记录：「local-group chat rendering is currently
blocked by an emulator crash when opening those chats」，即群渲染在模拟器里会崩——这是
**测试**层面的障碍，不影响本次反向同步的代码正确性，但意味着端到端取证暂不能做。

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 8.3.1 接入本地群删除反向同步 | `MessagesController.deleteMessages` 中为本地群（dialogId<0 且 `OxGroupStore.isLocalGroup(-dialogId)`）新增 `else if` 分支：置 `cacheOnly=true` 并调用 `onNativeMessagesDeleted` | 完成 |
| 8.3.2 裁剪后刷新会话行 | `onNativeMessagesDeleted` 末尾调用 `refreshDialogRow(dialogId)`，agent/本地群裁剪后会话列表即时更新 | 完成 |

## 实现要点

- `MessagesController.java`（约 9318-9331 行）的 shunt：
  ```java
  if (org.telegram.ox.OxAgentsBridge.isAgentDialog(dialogId)) {
      cacheOnly = true;
      org.telegram.ox.OxAgentsBridge.onNativeMessagesDeleted(dialogId, messages);
      ...
  } else if (dialogId < 0 && org.telegram.ox.OxGroupStore.isLocalGroup(-dialogId)) {
      cacheOnly = true;
      org.telegram.ox.OxAgentsBridge.onNativeMessagesDeleted(dialogId, messages);
      ...
  }
  ```
- `OxGroupStore.isLocalGroup(long chatId)` 以 `chat_` 前缀 key 判定是否为本地群；
  真实 Telegram 群（即便含 Ox agent）无该 key，不命中分支，删除仍走服务器路径，行为不变。
- `onNativeMessagesDeleted` 本身已支持群路径：用 `OxGroupStore.loadChat` /
  `OxGroupStore.removeEntry` 按内容前缀或最近时间戳（媒体无 caption 时）裁剪。
  本次仅在裁剪循环后追加 `refreshDialogRow(dialogId)`，与 agent 路径体验对齐。
- `refreshDialogRow` 按 `uid` 查 `messages_v2`，无剩余消息时早退，对 agent 与群均安全。

## 行为边界

- 仅裁本地群记忆日志，不触碰网络；离线本地群的删除现在是纯本地操作（`cacheOnly`）。
- 删除判定依赖 `messages_v2` 行在删除前的 `date/data`：文本按内容前缀（前 24 字）匹配，
  媒体按 ±10 分钟内最近时间戳匹配——与 0.77 agent 路径完全一致。
- 旧群数据缺失 `log_<chatId>` 时 `loadChat` 返回空列表，方法早退，无副作用。

## 构建

- `taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug` → BUILD SUCCESSFUL
  （8.3.1 约 1m29s，8.3.2 约 50s）。两次增量构建均通过。

## 诚实缺口（⚠️ 待端到端验证）

- **未做模拟器端到端取证**：打开本地群聊天页在 `emulator-5554` 中曾致 qemu 退出，
  无法稳定复现「建本地群→发消息→Telegram UI 删除→验证 OxUI 群日志被裁剪」全流程。
- 本项以 **BUILD SUCCESSFUL + 代码审查** 收口：`onNativeMessagesDeleted` 群路径与
  agent 路径共用同一裁剪逻辑（仅存储后端不同），且新增 shunt 分支与既有 agent 分支
  结构一致。逻辑正确性与既有 agent 反向同步对齐，但**缺少运行期实证**，正式标注待验证。
- 下一步应在稳定模拟器环境（或无 qemu 退出的真机/容器）复验本地群删除反向同步，
  并补 `dist/iter-shots/oxui-8.3-*.png` 实证截图。
