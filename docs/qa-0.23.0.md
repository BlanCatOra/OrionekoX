# QA — alpha 0.23.0（AMOLED 纯黑快捷开关）

- 版本：`0.23.0`，versionCode **2309**（`0.22.0` 为 2209）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 02:14–02:24（CST）
- 目标：补齐竞品“AMOLED 快捷开关”：在外观页一键把当前作用域聊天背景切为
  纯黑，并同步状态栏/导航栏；关闭后恢复原有背景。
- 版本规则：新增一项可见的外观能力，按 minor 从 0.22.0 → **0.23.0**，
  versionCode 2309（230×10+9）。

## 1. 新增能力

- `OxChatTheme.Snapshot` 新增 `amoled` 布尔值，随全局 prefs、按 Agent/会话
  JSON、预设、`.oxtheme` / `.oxthemes` 一起持久化；导入别名
  `amoled` / `amoledMode` / `amoled_mode`（布尔或 0/1）。
- 外观页“背景模糊”下方新增“AMOLED 纯黑”，副标题“已开启/已关闭”，点击切换。
- 开启时 `makeBackground` 直接返回纯黑 `ColorDrawable`，聊天列表/壁纸层为
  `#000000`；状态栏、导航栏也使用纯黑。关闭后回到原有纯色/渐变/图片背景，
  不丢失原背景配置。
- 外观预览复用同一 `makeBackground`，因此预览也会显示纯黑。
- 气泡、文字、边框、字体与行高不受影响，仍使用当前快照。

## 2. 设备验证 — PASS

- 当前全局主题：图片背景 `ox_chat_bg_preset_...img`、自定义气泡/边框。
- 打开外观页点击“AMOLED 纯黑”：
  - 副标题变为“已开启”，`ox_chat_theme.xml` 中 `amoled=true`；
  - 打开“预览当前外观”，预览背景为纯黑，收/发示例气泡正常显示；
  - 进入 R2B 本地群，聊天背景为纯黑，气泡/文字/边框正常。
- 关闭开关：副标题回到“已关闭”，`amoled=false`；原图片背景保留在快照中。
- 截图：`docs/screenshots/qa-0.23.0-amoled-preview.jpg`、
  `qa-0.23.0-amoled-chat.jpg`。
- 无 FATAL/ANR；测试后开关已恢复关闭状态。

## 3. 未覆盖 / 已知边界

- 只改背景与系统栏；不会自动把浅色主题的文字/气泡改为深色主题。若用户此前
  使用浅色文字色，纯黑背景上仍按原文字色显示，需用对比度检查自行调整。
- 开关只作用于当前打开的作用域（全局/Agent/会话），不会批量应用到全部。
- 不提供定时/跟随系统深色模式；模糊/暗化配置保留但纯黑下不可见。
- 未在真实 AMOLED 屏幕上测量功耗，仅验证像素为 `#000000`。

## 4. 产物

- `dist/OrionekoX-0.23.0-alpha-arm64-x64.apk`（versionCode 2309），sha256
  `311fc6456dbb7b6af458215a107e3f7137610009db3189d1827a261d0093ae9b`
- `dist/OrionekoX-0.23.0-debug-arm64-x64.apk`，sha256
  `b6c48bfa0dd421c861ca70c6035c0fe3fc0ca751041c3d241674ceb1481a63dd`
