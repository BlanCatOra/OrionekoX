# QA — alpha 0.14.1（工具气泡与媒体气泡随主题着色）

- 版本：`0.14.1`，versionCode **1459**
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off`）
- 时间：2026-10-27 21:18–21:26（CST）
- 目标：修复 0.14.0 QA 发现的视觉缺口——Agent 工具调用提示气泡仍是原生白色，
  自定义文字色为白色时正文不可见。

## 1. 根因与修改

- `OxChatTheme.applyToChat` 原先只覆盖四个文字气泡 drawable：
  `chat_msgIn/OutDrawable` 与 `chat_msgIn/OutSelectedDrawable`。
- 媒体气泡 `chat_msgIn/OutMediaDrawable` 与 `...MediaSelectedDrawable` 未覆盖；
  工具提示气泡走这组未被覆盖的绘制路径，因此保持原生白色，而文字已由
  `ThemeDelegate.oxTextColor` 改成白色，形成白底白字。
- 修改：抽出 `applyBubbleOverride` / `clearBubbleOverride`，统一覆盖与恢复全部
  8 个气泡 drawable（in/out、selected、media、mediaSelected），颜色、圆角与边框
  使用同一份 `Snapshot`。退出 Agent 会话时 8 个 drawable 全部恢复。
- 版本规则：本轮只修一个视觉缺陷，按 patch 从 0.14.0 → **0.14.1**。

## 2. 设备验证 — PASS

### 2.1 修复前

- `**Bold** Card` 会话中，主回复气泡为深紫 `#2D2A45`、白字、洋红 1dp 边框。
- 两条工具提示（🔧 `device_info({}) …`、`get_time({}) …`）为白色气泡；工具名与
  参数因白字不可见，只剩彩色扳手图标与灰色时间。

### 2.2 修复后

- 同一会话、同两条历史消息（未发送新消息）：气泡变为深紫 `#2D2A45`，带
  `#FF2D95` 1dp 边框；`device_info({}) …`、`get_time({}) …` 白字完整可读。
- 主回复气泡、发气泡 `#7C5CFF`、背景图与 80% 不透明度均无变化。
- 截图：`/tmp/chat_tool_0141.png`（修复后）。

### 2.3 回归检查

- Agent 会话重开历史正常，无空白；无 FATAL/ANR，进程存活。
- 本次只改绘制覆盖范围，未改消息落库、导入、预设或用户数据。

## 3. 未覆盖

- 图片/文件媒体气泡的视觉回归：新覆盖同样作用于媒体气泡，但本轮未单独构造图片
  消息截图；按同一 drawable 路径推演应一致，留待第三轮测试覆盖。
- 工具气泡的“进行中/失败”状态色未单独设计，仍与普通收气泡同色。

## 4. 产物

- `dist/OrionekoX-0.14.1-alpha-arm64-x64.apk`（versionCode 1459），sha256 `9030915746d2426a3a8fbaaa0efdcd6b9433631dd8080cf64a0509aae779c3d1`
- `dist/OrionekoX-0.14.1-debug-arm64-x64.apk`，sha256 `fc0758b06bc940f0b580324852c59ae0e5b81a88f169da83a55256eaa68b6985`
