# QA — OxUI 9.2：density（composer / 列表缩放）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。
> 日期：2026-10-30 · 分支：`orioneko/0.80-memory-stage-c` · 基线：`f12fbac` / `dfe6cb6`（0.85 视频附件）

## 背景

`OxUiSkin.density`（`cozy | compact`）以及外观面板里的 `Density` 选项此前**只做了一半**：
`rowGap()` 让气泡行的纵向间距在 compact 下缩到 62%，但 composer（输入区）——占屏幕底部
最长的一块固定高度——完全没有参与缩放。于是「compact = 一屏看更多对话」这个承诺只兑现了
消息之间，没兑现最占地方的那条输入栏。

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 9.2 缩放基准函数 | 新增 `sd(float)`：compact 时所有**盒模型**尺寸乘 0.82（下限 2dp），cozy 时等于 `dp()` | 完成 |
| 9.2 composer 缩放 | `composerWrap` 外边距、`composer` 内边距、＋/↑ 按钮、输入框内边距与 `minHeight`、footer 行、附件行与其 52dp 缩略图全部改走 `sd()` | 完成 |
| 9.2 不缩字号 | 文字一律保持原字号（`setTextSize` 未改）。缩字号换空间是拿可读性做交换，聊天应用不该这么换 | 明确决定 |

## 行为边界

- **只缩负空间与点击区，不缩字。** compact 下输入框高 44dp→36dp、按钮 44dp→36dp，正文与
  气泡文字字号不变。
- **气泡间距仍是原逻辑**：`rowGap()` 的 0.62 系数保持不变，未重复缩放，避免双重缩小。
- **density 只作用于 OxUI composer 与气泡行**，不影响 Telegram 原生界面的密度。

## 构建

- `taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug` → **BUILD SUCCESSFUL**（1m 5s）。
- APK：`TMessagesProj_AppStandalone/build/outputs/apk/afat/debug/app.apk`

## 模拟器实测取证（2026-10-30 09:57–10:01，emulator-5554，包 `io.github.blancatora.orionekox.debug`）

### 方法与说明

先取 compact 前的 **cozy 基线**，再切到 compact 复测同一界面，对比 uiautomator bounds。

皮肤切换方式需诚实说明：外观/皮肤面板的正常入口（`showSettingsPanel()` ← dock `⚙` /
agent 面板 footer 的 `Settings`）在本机当前 skin 下没渲染出可点节点，直接点 `Agents` /
`Telegram UI` 也没命中目标面板。因此**改用等价路径**：按 `OxUiSkin` 的持久化契约直接写入
`orioneko_config` 的 `oxui_skin_id=custom` + `oxui_skin_custom`（`density:"compact"`），
`am force-stop` 后重启，使 `OxUiSkin.active()` 返回 compact 皮肤。这验证的是**渲染层**对
density 的响应（本轮改动所在），**未**验证面板点选交互本身（见下方缺口）。

### 实测数据（uiautomator `bounds`，同分辨率同界面）

| 节点 | cozy | compact | 变化 |
|---|---|---|---|
| 输入框 `Write something…` | `[163,2127][906,2243]` → 高 **116px** | `[133,2157][938,2252]` → 高 **95px** | **−18.1%** |
| ＋ 附件按钮 | `[58,2127][163,2243]` → 105×**116** | `[47,2157][133,2252]` → 86×**95** | **−18.1%** |
| ↑ 发送按钮 | `[906,2127][1022,2243]` → 116×**116** | `[938,2157][1033,2252]` → 95×**95** | **−18.1%** |

输入框与按钮同时变窄/上移，说明 `composerWrap`、`composer` 的内外边距也一并生效；
`+` 与输入框左右各让出 ~30px 给两端留白收紧。数值与设计值 0.82 完全吻合（116×0.82≈95）。

### 截图

| 文件 | 说明 |
|---|---|
| `dist/iter-shots/oxui-9.2-cozy.png` | 基线：cozy，输入栏厚重，＋/↑ 触达区大 |
| `dist/iter-shots/oxui-9.2-compact.png` | compact：输入栏明显变薄，视频气泡下方可见更多内容 |

### logcat

切换并重启后 `logcat -d | grep -c FATAL` → **0**。仅有离线环境噪声（`FirebaseMessaging … FIS_AUTH_ERROR`、
`MediaProvider IOException`、`CorpusConfigHelper MalformedJson`），与本改动无关。

### 补充（0.87.0）：面板点选已取证

上一轮遗留的「`Density` 点选未取证」已闭环。根因不在 `Density` 本身，而在**入口**：

`showAgentPanel()` 里 `listScroll` 用了 `layout_height=0, weight=1`，但整个 `panel` 是
`ScrollView` 的**唯一直接子节点**，测量规格为 `AT_MOST`。Android 在 `AT_MOST` 下算权重分配时
`available = remaining = 0`，加权子项高度被算成 0，于是列表内容溢出画到下面那块「footer」上，
把 `Settings / Terminal / Open Telegram UI` 三个链接盖住——`uiautomator dump` 里这三个节点
根本不存在，点击也落到列表项上。

修复（`OxUiPreviewActivity#showAgentPanel`）：
- `listScroll` 改为 `WRAP_CONTENT` + `setMinimumHeight(dp(150))`，不再依赖权重分配；
- footer 改为两行（提示语一行、三个链接一行），用 `WRAP_CONTENT` 自然撑开。

实测（emulator-5554，构建 `BUILD SUCCESSFUL` 后 `adb install -r` 全新安装）：

| 步骤 | 证据 |
|---|---|
| 打开 Agents 面板 | `uiautomator dump` 首次出现 `Settings [58,1409][197,1448] clk=true`、`Open terminal`、`Open Telegram UI` 三个独立可点节点（修复前三个节点缺失） |
| 点 `Settings` | 打开完整设置面板（THEME / APPEARANCE / SHELL / COMPONENTS / ABOUT 全可见），截图 `dist/iter-shots/oxui-agents-settings-entry.png` |
| 滚到 COMPONENTS，点 `Cozy` | 面板关闭并 `recreate()`，回到会话页；composer 由 compact 尺寸回到 cozy 基线：`＋ [47,2157][133,2252]`（105×116）、输入框 116px 高；截图 `dist/iter-shots/oxui-9.2-density-click-cozy.png` |
| 再次点 footer `Settings` → `Compact` | 输入框 116px→95px、＋ 105×116→86×95；截图 `dist/iter-shots/oxui-9.2-density-click-compact.png` |
| logcat | 两轮点击后 `grep -E "FATAL EXCEPTION\|E AndroidRuntime"` → **无输出** |

结论：`Density` 单选的点击 → 持久化 → `recreate()` → 尺寸生效，全链路已实证。

## 诚实缺口

- ✅ **面板点选已取证**（见上「补充（0.87.0）」，含双向 cozy↔compact 截图）。
- 未做真机（非模拟器）验证；未测其他 preset 下的 compact 组合。
- 附件缩略图 52dp→43dp 的视觉效果未单独截图（本轮界面无待发附件）。
