# QA — alpha 0.17.1（真实背景模糊 + 首次应用修正）

- 版本：`0.17.1`，versionCode **1719**（`0.17.0` 为 1709）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-27 22:25–23:20（CST）
- 目标：补齐竞品“真实背景模糊”缺口，并修复 0.17.0 首次进入会话时
  按会话主题/模糊不生效的问题；同时把按会话外观从 Agent 会话扩展到本地群。
- 版本规则：`0.17.0` 新增背景模糊（minor）；`0.17.1` 只含首次应用、群作用域
  与提示文案修复（patch，versionCode 1719）。

## 1. 0.17.0 新增能力

- `OxChatTheme.Snapshot` 新增 `backgroundBlur`（0–3，默认 0）。外观页
  “背景模糊”在 关 / 轻 / 中 / 强 之间循环；持久化到全局 prefs
  （`background_blur`）、按 Agent JSON、按会话 JSON、预设与 `.oxtheme`。
- 导入识别 `backgroundBlur` / `background_blur` / `blur` / `blurSigma` /
  `blur_sigma`，钳制到 0–3；外观摘要追加“模糊强/中/轻”。
- 渲染：仅图片背景走真实模糊。`Utilities.stackBlurBitmapWithScaleFactor`
  按级别使用 3×/5×/8× 降采样，结果按“路径 + mtime + 级别”缓存，避免
  `applyToChat` 多次重建背景时重复计算；纯色/渐变背景不受影响。
- 关闭（0）时仍走原来的 `BitmapFactory.decodeFile`，不改变旧主题行为。

## 2. 0.17.0 设备证据（背景模糊）

- 工具调用 `**Bold** Card` 的会话背景设为 `ox_chat_bg` 后，把背景模糊从
  “关”切到“强”，右侧空白壁纸条带的灰度标准差由 **29.54** 降到
  **0.49–0.54**，边缘能量由 **2.1** 降到 **0.03**；粉色壁纸边框被明显抹平。
  对比裁片：`docs/screenshots/qa-0.17.0-blur-margin-compare.png`（左：关，
  右：强）。
- 模糊状态在外观页重启后仍为“强”，JSON 中 `backgroundBlur` 为 3。
- 0.17.0 同时暴露两个问题：冷启动后第一次进入会话时，主题/模糊要进一次
  外观页再返回才生效；本地群根本不会应用按会话主题。两项在 0.17.1 修复。

## 3. 0.17.1 修复

### 3.1 首次进入会话即应用

- 原因：`ChatActivity.onResume` 第一次执行时 `getChatListView()` 可能尚未
  创建，原逻辑直接跳过 `applyToChat`，之后没有重试。
- 修复：在 `onResume` 里无条件安排一次 500 ms 后的重试，列表存在且页面
  未结束时再次 `applyToChat`；保留原有 500 ms/1600 ms 的壁纸与系统栏复核。
- 设备：`am force-stop` 冷启动后第一次点开 `酒吧炒饭R2B改名`，2 秒内即为
  1.15× 字号、1.4× 行高、深色收气泡、`#FF2D95` 1dp 边框与模糊背景，无需
  进入外观页。截图：`docs/screenshots/qa-0.17.1-agent-firstopen.jpg`。

### 3.2 本地群按会话外观

- 原因：`applyToChat` 只允许 `OxAgentsBridge.isAgentDialog(dialogId)`，
  本地群被提前 return。
- 修复：条件改为“本地 Agent 会话 **或** `OxGroupStore.isLocalGroup`”。
- 设备：冷启动后第一次打开 `酒吧炒饭R2C测试群`，消息完整渲染，长文 R2C
  气泡与 Yuki 回复均在，会话配色、边框、字号与背景立即生效。
  截图：`docs/screenshots/qa-0.17.1-r2c-group-firstopen.jpg`。
- 同一会话的外观页标题为 `会话外观 · 酒吧炒饭R2C测试群`，背景样式“图片”、
  模糊“强”、收气泡 `#2D2A45`、发气泡 `#7C5CFF`，背景图 `ox_chat_bg`；
  `files/ox_chat_dialogs/dialog_-1800000008.json` 含 `"backgroundBlur":3`。
  截图：`docs/screenshots/qa-0.17.1-r2c-group-scope.jpg`。

### 3.3 会话作用域清除提示

- 发现：会话/Agent 作用域的清除行文案是“清除覆盖并跟随上层”，但点击后
  提示仍是全局的“已恢复 Telegram 原生外观”，与行为不符（实际只删除当前
  作用域文件并回退到上层）。
- 修复：按 `scopeDialogId != 0 || agentTgId != 0` 选择新文案
  `OxChatResetScopeDone`（“已清除覆盖，现跟随上层外观”），中文包版本
  82 → 83；全局作用域仍提示“已恢复 Telegram 原生外观”。
- 设备：在 `酒吧炒饭R2B测试群` 设置模糊后点清除，提示正确；随后
  `dialog_-1800000007.json` 被删除，只剩 Agent 与 R2C 群两个作用域文件。
  截图：`docs/screenshots/qa-0.17.1-reset-scope-toast.jpg`。

## 4. 设备与运行环境

- 安装：`adb install -r dist/OrionekoX-0.17.1-alpha-arm64-x64.apk` 成功；
  设置页底部显示 `v0.17.1 (171)`。
- 本次 QA 期间 `-gpu off` 多次在打开会话时令 qemu 宿主进程 SIGSEGV
  （`RenderThread`，coredump 记录 22:50 / 22:53 / 22:55 / 23:08）；
  应用本身没有 FATAL/ANR。已排除 WebView/Chromium：禁用
  `com.google.android.webview` / `com.android.chrome` 并加
  `-feature -Vulkan` 后仍崩溃。
- 为完成群会话验证，后续改用 `-gpu swiftshader_indirect` 与
  `-gpu guest`（均为软件渲染）启动同一 AVD；`-gpu guest` 下完成了
  群会话、外观页、清除提示与 Agent 会话的连续操作。这是对“QA 用
  `-gpu off`”约定的临时偏离，原因与复现条件已记录，待真机/GPU
  环境回归。
- 测试期间代理开关曾在应用内被打开（`10.0.2.2:7890`），导致本地群
  标题显示“正在连接代理”、消息不加载；已通过“设置 → 代理与 VPN”关闭，
  `proxy_enabled=false`。直接改 XML 会在应用启动时被覆盖。
- 未向真实联系人发送消息；未清理用户数据；测试后恢复了 WebView/Chrome
  包状态，并重新启用 `OxAccessibilityService`。

## 5. 未覆盖 / 已知边界

- 群会话的“模糊视觉”没有单独的像素对比：`ox_chat_bg` 的主要细节被
  气泡覆盖，可见壁纸条带接近纯色；但群会话已确认走同一个
  `makeBackground` 路径，且 JSON/外观页均为“强”。Agent 会话的模糊
  像素测量见第 2 节。真机/GPU 环境应补一张群聊空白壁纸区域的对比图。
- `-gpu off` 下打开带媒体/长文的本地群仍可能令 qemu 整机崩溃；这是
  模拟器宿主渲染问题，不是应用崩溃，需真机或可用 GPU 环境复验。
- 0.17.0/0.17.1 未新增真实模型调用；按会话导出/导入与预设复用既有路径，
  未逐项重复截图（0.11/0.15/0.16 已覆盖）。
- 第三轮 A/B/C（酒吧炒饭）仍未启动：需先完成真机/GPU 背景复验且无
  P0/P1。

## 6. 产物

- `dist/OrionekoX-0.17.1-alpha-arm64-x64.apk`（versionCode 1719），sha256
  `ac1f54faaa9270fd155d4826b157d1536bfe546138d2352670f5a668be4fd49b`
- `dist/OrionekoX-0.17.1-debug-arm64-x64.apk`，sha256
  `a4630952a316ae33eb0a0330882fb92f364a1a8e3c7f337ffbedfb1faf76a491`
- `dist/OrionekoX-0.17.0-alpha-arm64-x64.apk`，sha256
  `3d435273af27f9f7381136d30e50dc2f94a94c427606ebe0fd36150a5919d0e0`
- `dist/OrionekoX-0.17.0-debug-arm64-x64.apk`，sha256
  `a26cbb6198cf3343054b0bc8ed8922cf41ea7b0bf02f1d3e29d5b7ffcf28270a`
