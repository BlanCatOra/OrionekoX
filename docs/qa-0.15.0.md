# QA — alpha 0.15.0（消息行高）

- 版本：`0.15.0`，versionCode **1509**
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off`）
- 时间：2026-10-27 21:34–21:56（CST）
- 目标：补齐竞品行高（line height）缺口，让长段落在字号之外还能调整行距，并随主题
  保存、导出与导入。
- 版本规则：新增一项用户可调外观能力，按 minor 从 0.14.1 → **0.15.0**。

## 1. 新增能力

- `OxChatTheme.Snapshot` 增加 `lineHeight`（默认 1.0，允许 0.8–1.8）。
- 外观页“消息字号”下新增“消息行高”，点击在 1.0 / 1.1 / 1.2 / 1.3 / 1.4 / 1.5 间循环，
  显示 `1.4x` 形式。
- 持久化：全局 SharedPreferences `line_height`、按 Agent JSON、命名预设、主题 JSON、
  `.oxtheme` 的 `theme.json` 均带 `lineHeight`；导入识别
  `lineHeight` / `line_height` / `lineSpacing` / `line_spacing`。
- 运行期：`OxChatTheme.lineHeightFor(dialogId)` 只在当前已应用自定义主题的 Agent 会话
  返回倍率；`MessageObject.TextLayoutBlocks` 的正文、引用、代码块 StaticLayout 统一使用
  该倍率，普通 Telegram 会话始终为 1.0。
- 退出 Agent 会话时恢复为 1.0，不污染其他聊天。

## 2. 设备验证 — PASS

### 2.1 设置与渲染

- 全局 Agent 外观设为 1.0 → 连续点按四次“消息行高”，页面显示 `1.4x`。
- 重新进入 `**Bold** Card`：长 Markdown 示例段落行距明显增大，气泡随内容变高；
  代码块、引用块同样获得行距；工具气泡、发气泡、背景图、边框无回归。
- 截图：`/tmp/chat_lineheight_0150.png`（1.4x 渲染）。

### 2.2 导出

- 外观页“导出主题包（.oxtheme）”保存，Download 中生成新文件
  `orioneko-theme (1).oxtheme`（4478 B，未覆盖旧文件）。
- `unzip -l`：`theme.json` 665 B、`background.img` 16360 B。
- `theme.json` 关键字段：
  `fontScale=1.15`、`lineHeight=1.4`、`cornerRadius=16`、`borderStyle=frosted`、
  `backgroundMode=image`、`assets.background=background.img`。

### 2.3 导入预览

- 从同一入口选择 `orioneko-theme (1).oxtheme`，预览文案：
  `背景 ox_chat_bg · 收 #2D2A45 · 发 #7C5CFF · 100% · 16dp · 1.1x · 行高 1.4x · 磨砂 · 边框 1dp · 含背景图`
- 点“取消”后外观不变；预览只读，不写盘。

### 2.4 稳定性

- 无 FATAL/ANR；进程存活；导入/导出未修改用户消息、Agent、记忆或工作区。

## 3. 未覆盖与已知边界

- 0.8 与 1.8 两个端点只在解析层允许，UI 只提供 1.0–1.5。
- 行高修改后需重新进入 Agent 会话生效（页面已有提示），未做会话内实时刷新。
- 同一倍率也作用于引用/代码块；未单独设计“代码块行高独立”选项。
- 本轮未发送新消息，验证均基于既有历史；媒体气泡回归仍待第三轮截图。

## 4. 产物

- `dist/OrionekoX-0.15.0-alpha-arm64-x64.apk`（versionCode 1509），sha256 `c9b84a56eb8b648cc4110f43c4333ab5e4ebb7b77f7d77f3c8456902c5618181`
- `dist/OrionekoX-0.15.0-debug-arm64-x64.apk`，sha256 `00619f056f8a2f8a50e70b36018ad8a659af6e2cb891bfc5096a3e142aa58520`
