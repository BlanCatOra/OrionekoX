# QA — alpha 0.61.0（OxUI root 切换 + Agent 列表/搜索/详情/记忆）

- 版本：`0.61.0`，versionCode **6109**（`0.60.0` 为 6009）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-28 22:40–23:05（CST）
- 目标：在 0.60.0 OxUI preview 基础上做第二轮前端迭代：真正的 root
  切换（选择 OxUI 后普通启动直接进入 OxUI，可切回 Telegram UI）、Agent
  列表/搜索/详情/本地记忆库，以及消息长按复制；仍与 Telegram UI 共用
  本地 Agent、聊天与发送路径。
- 版本规则：新增 root 行为、导航与详情页，属于功能迭代，按 minor 从
  0.60.0 → **0.61.0**（6109），patch 清零。

## 1. 实现

### 1.1 Root 切换

- `LaunchActivity` 在 `super.onCreate`/主题初始化后检查：
  `interface_mode=oxui` + `isAgentOnly()` + 普通 launcher 启动（无
  ACTION_VIEW/SEND 等）→ 启动 `OxUiPreviewActivity` 并 finish
  LaunchActivity。深链/分享仍走 Telegram 路径。
- OxUI 以 `oxui_root=true` 启动时为 root 模式：
  - 顶部 **Telegram UI**：把 `interface_mode` 切回 `telegram`，用
    `FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK` 启动 LaunchActivity；
  - 返回键：先关 overlay，再 `moveTaskToBack(true)`，不再露出
    Telegram UI。
- Ox Settings → Interface 选择 OxUI preview：设置偏好并清任务重启，
  LaunchActivity 随即进入 OxUI root；选择 Telegram UI 则恢复原 root。
- 引导页选择 OxUI 后点 Start：同样清任务重启进入 OxUI root。

### 1.2 Agent 列表/搜索/详情/记忆

- 头部新增 **Agents**；打开全屏 Agent 面板：
  - 搜索框按名称/备注过滤；自动获得焦点；
  - 行显示 emoji、名称、Harness、备注或最近一条消息摘要；
  - 点击进入对话，长按打开详情。
- 详情页：名称、Harness/模型、备注、`OxMemoryStore.stats()` 与最近记忆
  （`recent`），以及 Chat 按钮。没有记忆时显示明确空态。
- 消息长按复制到剪贴板（用户/助手气泡）；工具条不参与复制。

### 1.3 视觉/布局修复

- 第一版详情页的 header 在 LinearLayout 中被拉满高度并垂直居中；改为
  显式 44dp header 高度 + TOP gravity，详情内容恢复顶部对齐。
- Agent 搜索自动弹出键盘导致详情页被 resize 错位；详情打开前显式
  隐藏键盘，搜索仍可点击输入。
- 列表摘要剥离 `**`/反引号并压缩换行，避免 Markdown 原样出现在副标题。
- 消息改为非 selectable，使长按触发复制而不是文本选择工具条。

## 2. 设备验证

### 2.1 Root 启动

- 将 `interface_mode` 设为 `oxui`，强制停止后从 launcher 启动：
  `topResumedActivity=OxUiPreviewActivity`，LaunchActivity 不再停留；
  OxUI 直接显示合成 `Studio Test` 与真实历史。截图
  `docs/screenshots/qa-0.61.0-oxui-root.png`。
- 点顶部 Telegram UI：`interface_mode` 变为 `telegram`，
  `topResumedActivity=LaunchActivity`。
- 再通过 Ox Settings → Interface → OxUI preview：任务重启后
  `topResumedActivity=OxUiPreviewActivity`，`interface_mode=oxui`。
- 测试结束后恢复 `interface_mode=telegram`，不改变用户默认界面。

### 2.2 Agent 列表 / 搜索 / 详情 / 记忆

- 合成 Agent `Studio Test`（tgId 555000017，pinned，合成历史，
  `oxmemory.db` 预置一条事实“偏好安静的深色工作界面”）。
- 打开 Agents：所有本地 Agent 列出；输入 `Studio` 后只剩 `Studio Test`，
  截图 `docs/screenshots/qa-0.61.0-oxui-search.png`。
- 长按 Studio Test 行：详情页显示名称、`API 直连`、备注、
  `Memory library · 1 条 · 长期 1 · 短期 0 · 事实 1` 与最近记忆，Chat
  按钮可回对话；截图 `docs/screenshots/qa-0.61.0-oxui-detail.png`。
- 测试 Agent、chat、dialog/messages、memory 行均已删除，计数 0；
  内置 Echo 的 `OxUI test 0600` 仍作为 0.60 的同步证据保留。

### 2.3 构建/稳定性

- release 0.61.0（6109）覆盖安装启动，无本包 FATAL/ANR；无障碍重新
  绑定，代理保持 `10.0.2.2:7890`。
- 桥接 15 个测试仍通过；本轮未改 Harness/桥接代码。
- 构建日志：`/tmp/ox-build-0610-final.log`。

## 3. 产物

- release：`dist/OrionekoX-0.61.0-alpha-arm64-x64.apk`，sha256
  `62c0664029de7942a722919f0679ee16a0377b2568d2afdbb55b97c44ba509f3`
- debug：`dist/OrionekoX-0.61.0-debug-arm64-x64.apk`，sha256
  `6e6cc172662318e38725dfd5c7fea12297e5ddb2f57da38d786b247999287e54`

## 4. 边界

- Root 切换只对普通 launcher 启动生效；深链/分享保持 Telegram UI。
- Agent 搜索只搜本地 Agent 名称/备注，不是聊天内容搜索。
- 详情页记忆是只读最近条目 + 统计，没有编辑/清空/完整记忆页。
- 群聊、附件/图片/文件、会话搜索、设置页、Agent 编辑、平板双栏、
  动态主题、reduced-motion 与完整无障碍仍未做；OxUI 仍不能宣称已完全
  替代 Telegram UI。
- 长按复制已接入，但本轮没有抓取剪贴板截图/自动回读，未把它列为
  “设备验证”项。
- 外部 Harness 仍是回合结束后落盘，OxUI 不是 token 级流式；Claude
  403、OxLinux exec 限制未变；第三轮 A/B/C 未启动。
