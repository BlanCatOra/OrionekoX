# QA — alpha 0.62.0（OxUI 图片附件 + 本地群组 groundwork + 视觉 P0 修复）

- 版本：`0.62.0`，versionCode **6209**（`0.61.0` 为 6109）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-28 23:10–00:20（CST）
- 目标：OxUI 第三轮迭代。本轮实现：composer 图片附件（选图→导入→真实
  photo 消息→Agent 视觉回合）、本地群组 listing/对话/发送的 groundwork
  （含“仅本地创建群”标记）、以及子代理视觉评审提出的 P0 修复。按用户的
  token 策略，本轮以 2 个后台子代理并行完成特性审计与视觉评审。
- 版本规则：新增附件、群组数据模型与用户可见修复，按 minor 从 0.61.0 →
  **0.62.0**（6209），patch 清零。

## 1. 实现

### 1.1 图片附件（端到端设备验证）

- composer 新增 **＋** 按钮：`ACTION_GET_CONTENT` + `image/*` +
  `CATEGORY_OPENABLE`（SAF，无需运行时存储权限）。
- 选中 content URI 后复制到 `files/oxui_media/img_<ts><ext>`（上限
  16 MB，按 MIME 判断扩展名），composer 上方出现缩略图预览 + 文件名 +
  × 取消。
- 发送：构造 `SendMessagesHelper.SendingMediaInfo(path, caption)`，1:1 走
  `OxAgentsBridge.handleOutgoingMedia`（真实 outgoing photo 消息 +
  `generatePhotoSizes` + media 归档 + Agent 视觉回合），群组走
  `handleOutgoingGroupMedia`。
- OxUI 渲染：`OxChatEntry.imagePath` 的用户消息显示为圆角图片（≤70%
  屏宽，降采样解码）；纯图片/图片+说明均可；`signature()` 加入
  imagePath，`renderConversation` 不再跳过“只有图片”的条目。
- 发送就绪逻辑：文本或已选图片任一即可发送；发送按钮三种视觉状态
  （enabled signal / disabled paper+stroke+muted 字）。

### 1.2 本地群组 groundwork

- `OxGroupStore.markLocallyCreated/isLocallyCreated`：`createLocalGroup`
  现在会打标，避免把“含 Ox agent 的真实 Telegram 群”当成可本地发送的
  目标（本地发送路径硬编码 `TL_peerChat`，对真实群会静默失败）。
- OxUI Agents 面板新增 **Groups** 区：列出 `isLocallyCreated` 的群，
  标题来自 `MessagesController.getChat`，副标题为群内 Agent 数；点击
  进入群对话（`OxGroupStore.loadChat`），发送走
  `sendLocalGroupText`/`handleOutgoingGroupMedia`；Agent rail 在群目标
  下显示群 chip，点其他 Agent chip 自动切回 1:1。
- 选择/发送路径全部按 `targetKey()`（`g<chatId>` / agentId）区分。

### 1.3 视觉/交互 P0（子代理评审落地项）

- MUTED `#69766E`→`#566259`（BONE 上 4.0→5.4:1），LINE 20%→55% ink
  （输入边界满足 3:1 非文本对比）。
- Agent 正在回复的静态点改为 0.7s 呼吸动画（受系统
  `ANIMATOR_DURATION_SCALE=0` 门控）。
- 自动滚动不再每帧强拉底部：仅当已接近底部或用户刚发送时滚动。
- 失败态：pending 120s 未落盘 → 显示 coral “消息未发送 · 点按重试”
  行，点按重发同一条消息/图片。
- 详情页/面板/对话的辅助行为沿用 0.61 的键盘与返回修复。

## 2. 设备验证

### 2.1 图片附件端到端

- 合成 Agent `Studio Test`（tgId 555000018，echo 模式），测试图
  `oxui-620.png`（480x320）推送到 `/sdcard/Pictures/` 并扫描媒体。
- OxUI composer 点 ＋ → 系统 photo picker → 选中测试图 → composer 上方
  出现缩略图 + `img_*.png` + ×；截图
  `docs/screenshots/qa-0.62.0-oxui-attach-preview.png`。
- 点发送：logcat
  `handleOutgoingMedia dialog=555000018 count=1` →
  `persistAndShow media type=1 ... photoNonNull=true sizes=2` →
  `id=100072 out=true`（photo）+ `id=100073 out=false`（echo 回复）；
  OxUI 显示真实图片气泡 + “用户发送了图片（480x320）” + echo 回复；
  截图 `docs/screenshots/qa-0.62.0-oxui-image-sent.png`。
- 同一 photo 消息在 Telegram UI 中可见（共用 `persistAndShow` 路径）。

### 2.2 群组（部分验证，有已知缺口）

- Agents 面板 Groups 区在“无本地创建群”时正确显示为空。
- **缺口**：`OxAgentsBridge.createLocalGroup` 当前没有任何调用方；
  Telegram UI 的 New Group 流程在离线本地账号上停在服务器请求
  （spinner），不会走到本地创建路径。因此“创建本地群 → 面板列出 →
  群内发送”的端到端链路暂无法用真实 UI 流验证；列表/选择/发送代码为
  已实现但标记为“代码验证 + fixture 前提”，未列为设备验证项。下一轮
  优先把 New Group 流程接到 `createLocalGroup`（离线分支），然后再补
  端到端截图。

### 2.3 构建/稳定性

- release 0.62.0（6209）覆盖安装启动，无本包 FATAL/ANR；无障碍重新
  绑定，代理保持 `10.0.2.2:7890`。
- 桥接 15 个测试通过；构建日志 `/tmp/ox-build-0620-final.log`。

## 3. 产物

- release：`dist/OrionekoX-0.62.0-alpha-arm64-x64.apk`，sha256
  `62c546b0218171e81978dc4811dddbd5e3bc3f654ce9b908d3e0bd3ebd9bc22f`
- debug：`dist/OrionekoX-0.62.0-debug-arm64-x64.apk`，sha256
  `b563e6c62d40b12c72e2b0aa859604e83299913359a6f5796563eb7f9cf3b6cf`

## 4. 清理

- 测试 Agent `oxui_qa_620`（含 chat/dialog/messages）、测试图片、
  `files/oxui_media/` 已删除；`interface_mode` 恢复 `telegram`。未
  `pm clear`，未向真实联系人发消息。

## 5. 边界 / 下一步

- 群组端到端缺口见 2.2；下一轮先把 New Group 离线分支接到
  `createLocalGroup`，再补群组截图。
- 附件目前仅图片；视频/GIF/文件入口未做（群组 handler 已支持）。
- 会话内搜索、设置/记忆/Agent 编辑的 OxUI 化、平板两栏、暗色变体、
  reduced-motion、无障碍清扫未做（子代理评审清单 P1/P2 项）。
- Claude 403、OxLinux exec 限制未变；第三轮 A/B/C 未启动。
