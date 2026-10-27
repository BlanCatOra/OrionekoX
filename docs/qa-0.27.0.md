# QA — alpha 0.27.0（命名预设预览）

- 版本：`0.27.0`，versionCode **2709**（`0.26.2` 为 2629）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 04:16–04:26（CST）
- 目标：在预设管理里增加“预览”：长按命名预设可先看到它保存的外观，再决定
  是否应用/重命名/删除，避免直接点击就覆盖当前主题。
- 版本规则：新增一项可见的预设管理能力，按 minor 从 0.26.2 → **0.27.0**，
  versionCode 2709（270×10+9）。

## 1. 新增能力

- 外观预览重构为接受任意 `OxChatTheme.Snapshot`：
  - `currentSnapshot()`、`presetSnapshot(name)`、`backgroundDrawableFor()`、
    `typefaceFor()` 对外提供；
  - 当前主题预览与预设预览共用同一绘制逻辑。
- 长按命名预设的菜单改为 `预览 / 重命名 / 删除`。
- 选择“预览”只读取 `files/ox_chat_presets/<name>.json` 并弹出预览，不写入
  当前作用域；取消/关闭不改变任何设置。
- “重命名/删除”仍沿用 0.25.2 的行为。

## 2. 设备验证 — PASS

- 当前全局主题为图片背景（`BundleQA`）；长按 `DarkSea` 预设 → 预览：
  - 弹出“外观预览”，显示 DarkSea 保存的黑色纯色背景、浅绿收/发气泡；
  - 与当前页的图片背景/紫色气泡明显不同，说明读取的是预设快照而非当前主题。
- 关闭预览后，`shared_prefs/ox_chat_theme.xml` 的 mtime 保持 02:20 未变，
  页面仍是原图片背景；当前主题没有被修改。
- 截图：`docs/screenshots/qa-0.27.0-preset-preview.jpg`。
- 无 FATAL/ANR；未新增测试对象。

## 3. 未覆盖 / 已知边界

- 预览仍是轻量示例，不是真实 `MessageCell`；不显示媒体/工具气泡、引用、头像、
  真实换行。
- 预设引用的背景图/字体文件不存在时，预览按当前快照的回退字段绘制，可能
  与原预设导出时不完全一致。
- 不支持在预览中直接编辑/另存；也不支持并排比较多个预设。
- 只验证了 DarkSea 纯色预设；带背景图/字体的预设预览沿用同一路径但未逐项截图。

## 4. 产物

- `dist/OrionekoX-0.27.0-alpha-arm64-x64.apk`（versionCode 2709），sha256
  `84e5b96cf0b703acbc3e39a477ff1827e30912604d90053c2bce0eee2da220a3`
- `dist/OrionekoX-0.27.0-debug-arm64-x64.apk`，sha256
  `bf3de1b5633033f4f07526f8556fd0fad76cda0a349f2744bf490f874c228d56`
