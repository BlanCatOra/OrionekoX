# QA — alpha 0.60.0（OxUI preview：独立前端、引导页/Ox 设置入口、真实收发）

- 版本：`0.60.0`，versionCode **6009**（`0.59.0` 为 5909）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-28 22:05–22:30（CST）
- 目标：在现有 Telegram UI 和美化之外增加 **OxUI(preview)** 独立前端，
  入口放到引导页与 Ox 设置；OxUI 不使用 Telegram 的 cell/action bar，
  但读取同一批本地 Agent/聊天，并通过同一发送路径与 Telegram UI 同步。
- 版本规则：新增用户可见的界面模式、独立前端与入口，属于功能迭代，按
  minor 从 0.59.0 → **0.60.0**（6009），patch 清零。

## 1. 设计与实现

### 1.1 设计方向

设计记录见 `docs/oxui-design.md`。参考本机 Claude `frontend-design` skill、
Claude Academy 的设计 skill 说明，以及 GitHub 上的 oil-ui、superdesign、
Claude Code Frontend Design Toolkit 等社区方案。设计 token：

- Bone `#F1EBDD`、Paper `#E5DCCB`、Ink `#14211C`、Muted `#69766E`、
  Signal `#C8EF6A`、Coral `#CE6549`（仅错误/危险）。
- 标题/助手正文用 serif，控件用 humanist sans，元数据/工具输出用 mono。
- 头部 wordmark + 横向 Agent rail + 单列编辑式对话 + 悬浮 composer；
  助手消息用短 chartreuse 竖线而非气泡，用户消息用 ink pill。
- 有意避开 Claude 默认的 cream + terracotta 组合，改用 chartreuse 信号色；
  不做统一圆角卡片墙、渐变装饰或逐个 section 的淡入。

### 1.2 入口与偏好

- `OxMode.getInterfaceMode()/setInterfaceMode()`：`telegram`（默认）/
  `oxui`，写入 `orioneko_config`。
- Ox Settings → Mode 新增 **Interface** 行，显示当前模式；选择 OxUI
  preview 会打开预览 activity。
- 引导页第 6 步新增 Interface 卡：Telegram UI / OxUI preview 两行 +
  “Open OxUI preview” 链接；选择立即持久化，不改变当前 onboarding 完成
  流程。
- 中文包 119→120（入口文案），随后 Markdown/空态文案 120→121。

### 1.3 OxUI 预览 activity

`OxUiPreviewActivity`（独立 `Activity`，主题 `Theme.TMessages.OxUi`，无
ActionBar）：

- 读取 `OxAgentStore.listAgents()` 和 `OxAgentStore.loadChat()`，展示真实
  本地 Agent 与真实历史；不复制聊天数据。
- 发送调用新增的 `OxAgentsBridge.sendFromOxUi()`，复用
  `handleOutgoingText` 的本地持久化 + Agent 回合，因此消息同时出现在
  Telegram UI。
- 每 1.2 秒比较历史签名刷新；发送时立即显示 pending 用户消息与
  “Agent 正在回复…”，回复落盘后替换。
- Markdown（`**bold**`、`` `code` ``、`#` 标题）转 native spans，避免把
  星号原样显示。
- 空态、组件、页脚均走中英文字符串资源；品牌 wordmark 保留英文。

## 2. 设备验证

### 2.1 OxUI 主界面

自建测试 Agent `Studio Test`（tgId 555000016，pinned，合成历史）：

- Ox Settings → Interface → OxUI preview 可打开；
- 主界面显示 wordmark、Preview/Telegram UI、Agent rail、真实历史、
  serif 助手正文与 chartreuse 竖线、ink 用户气泡、composer；
- 截图：`docs/screenshots/qa-0.60.0-oxui-preview.png`。
- 测试 Agent 及其 chat/dialog/messages/memory 已删除，计数 0。

### 2.2 发送与 Telegram UI 同步

用内置 Echo Assistant 在 OxUI 发送 `OxUI test 0600`：

- OxUI 显示用户气泡与 Echo 回复；
- 日志：`persistAndShow shown did=555000002 id=100067 out=true` /
  `id=100068 out=false`；
- Telegram UI 的 Echo 会话在 10:13 PM 显示同一条 `OxUI test 0600` 与
  同一条 Echo 回复，证明两个前端共用同一消息路径。
- 该测试消息保留在内置 Echo 的本地会话中作为同步证据。

### 2.3 入口截图

- Ox Settings Interface 行：`docs/screenshots/qa-0.60.0-oxui-settings.png`
- 引导页第 6 步 Interface 卡：
  `docs/screenshots/qa-0.60.0-oxui-onboarding.png`

### 2.4 构建/稳定性

- release 0.60.0（6009）覆盖安装并启动，无本包 FATAL/ANR；
  `OxAccessibilityService` 已重新绑定，代理保持 `10.0.2.2:7890`。
- 桥接 15 个测试仍通过；OxUI 不改变桥接/Harness 代码。
- 构建日志：`/tmp/ox-build-0600-final2.log`。

## 3. 产物

- release：`dist/OrionekoX-0.60.0-alpha-arm64-x64.apk`，sha256
  `af6eae6d0e85306b043d108f1641e7fc402403b9f54677ebd47bc4f28181ad82`
- debug：`dist/OrionekoX-0.60.0-debug-arm64-x64.apk`，sha256
  `fc3a837a7a83a9f23db0de0f4b0f27c784bd98b48b30b29be0223a1c828df76f`
- 设计说明：`docs/oxui-design.md`

## 4. 边界（重要）

- **OxUI 仍是 preview**：应用根仍是 Telegram UI；选择 OxUI 只打开预览
  activity，不替换 MainTabs。不能宣称“OxUI 已完整替代 Telegram UI”。
- OxUI 目前只有单聊：没有群聊、附件/媒体、搜索、设置、记忆页、Agent
  详情、平板双栏；动态主题、reduced-motion、完整无障碍巡检尚未做。
- Agent rail 读真实 Agent；发送读/写真实本地聊天。外部 Harness 回复在
  回合结束后才落盘，因此 OxUI 不是 token 级流式（Telegram UI 才有流式
  气泡）。
- OxUI 视觉为第一版；下一轮应按设备截图做第二轮视觉评审，并优先补
  导航/群聊/附件与 root 切换。
- Claude 仍 403、OxLinux 仍受 Android app-data exec 限制；第三轮
  酒吧炒饭 A/B/C 未启动。
