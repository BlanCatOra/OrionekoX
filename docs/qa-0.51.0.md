# QA — alpha 0.51.0（Chatbox 备份携带全局外观主题）

- 版本：`0.51.0`，versionCode **5109**（`0.50.0` 为 5009）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 16:03–16:14（CST）
- 目标：0.50.0 已让 Chatbox 备份携带按 Agent 外观，但全局外观
  （所有 Agent 的默认主题、全局背景与字体）仍不能往返。本轮把全局
  主题写入 `settings.json` 并在导入时恢复。
- 版本规则：新增用户可见的备份内容与恢复行为，属于功能迭代，
  按 minor 从 0.50.0 → **0.51.0**（5109），patch 清零。

## 1. 实现

### 1.1 导出

- 仅当全局外观处于自定义模式时，在 Chatbox `settings.json` 写私有
  `orionekoGlobalTheme`；跟随 Telegram 原生外观时不写。
- 主题字段与按 Agent 主题一致：模式、背景模式/双色/透明度/暗化/
  模糊/AMOLED/渐变方向、收/发气泡色与透明度、圆角、气泡样式、边框、
  文字色、字号、行高。
- 绝对路径清空：`backgroundImage`、`fontPath`、`codeFontPath` 均不
  进入 JSON。全局背景写成 `background` 资源（scope=global），
  `settings.json` 用 `background.storageKey` 引用；全局字体写成
  `fonts/global/chat.ttf`、`fonts/global/code.ttf`。
- 字体沿用 8 MiB/文件、16 MiB 总量、40 个上限；全局与按 Agent 字体
  共用同一份计数与字节预算。
- 导出说明与确认框改为“全局与按 Agent 外观主题”。

### 1.2 导入

- 读取 `settings.json` 的 `orionekoGlobalTheme` 与 `background` 引用；
  预览新增“将恢复全局外观主题（颜色、气泡、字体与背景；已有自定义
  全局外观则保留）”。
- 全局背景写入 `files/ox_chat_bg_imported_<ts>.img`；全局字体写盘后
  用 `Typeface.createFromFile` 校验。
- `OxChatTheme.applyImportedGlobalTheme` 仅在本地全局外观不是自定义
  模式时写入 SharedPreferences；已有自定义全局外观时保留本地设置，
  并删除本次为跳过而写出的背景/字体，避免孤儿文件。
- 结果提示“已恢复全局外观主题（含字体 N 个）”或“全局外观已有自定
  义，保留本地主题”；中文包 111 → 112。

### 1.3 文案修复

- 首次构建把“全局与按 Agent 外观主题、字体”塞进 Ox 设置的导出说明
  行后，原生 SettingCell 只显示两行，末尾“不含 API Key”被截断。
- 缩短为“zip · 助手、聊天、记忆、技能、工作区、偏好、主题、字体、
  头像、附件；无 API Key”，重建后完整显示；英文同步缩短。

## 2. 设备验证 — PASS

### 2.1 测试对象与保护

- 自建 `QA Global 0510`（tgId 555000108，人设 + 开场白，无按 Agent
  外观），用于触发导出；不修改其他 Agent。
- 设备原有全局主题（自定义、背景图、无字体）先备份为
  `/tmp/ox_chat_theme_original.xml`，sha256 `e070ff70…`；测试结束后
  原样恢复并复核同一 sha256。
- 为覆盖全局字体，临时在全局 prefs 写入聊天字体 Roboto Regular
  （2,371,712 B）与代码字体 Droid Sans Mono（108,128 B），背景沿用
  原全局图（16,360 B）。

### 2.2 导出

- 单 Agent Chatbox 备份 1,457,238 B；`settings.json` 键为
  `background`、`orionekoGlobalTheme`。
- `orionekoGlobalTheme` 的颜色、背景模式、暗化、模糊、圆角、边框、
  字号、行高与 prefs 一致；三个路径字段为空。
- `fonts/global/chat.ttf` sha256 `9ca9debb…`、`code.ttf`
  `db19a1fd…`，与源字体一致。
- 全局背景资源解码后 sha256 `88a527c8…`，与源 16,360 B 背景一致；
  manifest 中 scope=global。
- 全文扫描 `api_key`、`base_url`、`baseUrl`、`headers`、
  `Authorization`、`Bearer `、`sk-`、`ox1:` 均 0 命中，无 `/data/`
  绝对路径。

### 2.3 重置后回导

- 删除 `shared_prefs/ox_chat_theme.xml` 与临时全局字体，模拟新机/原生
  全局外观。
- 预览显示“将恢复全局外观主题（颜色、气泡、字体与背景；已有自定义
  全局外观则保留）”；确认后结果“更新 1 / 已恢复 1 个智能体的偏好
  设置 / 已恢复全局外观主题（含字体 2 个）”。
- 回读 prefs：`mode=custom`，所有颜色、圆角、边框、透明度、字号、
  行高与备份一致；背景指向
  `files/ox_chat_bg_imported_1793174946033.img`，字体指向
  `files/oxfonts/chatbox_global_chat_*.ttf` 与
  `chatbox_global_code_*.ttf`。
- 背景 sha256 `88a527c8…`、两个字体 sha256 `9ca9debb…`/
  `db19a1fd…`，均与源一致。
- 打开 `QA Global 0510` 会话：原全局深蓝背景、磨砂气泡、粉色边框、
  白色文字正常显示，说明全局主题确实应用到跟随全局的 Agent。

### 2.4 重复导入

- 同一 zip 再导入：结果“全局外观已有自定义，保留本地主题”；
- `ox_chat_theme.xml` 的 sha256 前后均为 `29cba250…`，未变化；
- `chatbox_global_*` 字体仍为 2 个、`ox_chat_bg_imported_*` 数量不变，
  临时写出的背景/字体已被删除，无孤儿文件。

### 2.5 稳定性与清理

- 导出、预览、两次导入、会话打开全程无本包 FATAL/ANR。
- 清理：删除测试 Agent、导入的背景与两个字体、下载 zip；555000108
  的 `dialogs`/`messages_v2` 均为 0；恢复原全局 prefs 并复核 sha256
  `e070ff70…`；未执行 `pm clear`，未删除其他 Agent 或用户数据。
- 重装 release 5109 后恢复无障碍服务，中文包版本 112。

## 3. 未覆盖 / 已知边界

- `orionekoGlobalTheme` 与全局背景/字体是 OrionekoX 扩展；真实
  Kelivo/Chatbox 客户端未互测，预期会忽略。
- 只恢复全局主题与按 Agent 主题；按会话（dialog）覆盖仍不在备份内。
- 本地全局外观已是自定义模式时保留本地设置，不覆盖；要恢复备份主题
  需先恢复 Telegram 原生外观。
- 字体必须是平台可解析的 TTF/OTF；无效文件会删除并跳过。
- 不携带视频背景、全局主题预设库或字体授权元数据。
- 全局主题只写入 `settings.json`，不会改变外部客户端对
  `settings.json` 其他字段的解析。

## 4. 产物

- `dist/OrionekoX-0.51.0-alpha-arm64-x64.apk`（versionCode 5109），sha256
  `8281fc3d3d0c4c2aee82f7703c7e8776293a5d58bcf8ac96c4e84cf7c7bfa1f1`
- `dist/OrionekoX-0.51.0-debug-arm64-x64.apk`，sha256
  `a6bba2603e4c18b0d964349a4dd1a2c6ba026317bdb77c064d2af8aa4f358d77`
- 截图：`docs/screenshots/qa-0.51.0-export-info.png`（完整说明行）、
  `qa-0.51.0-global-theme-chat.png`（恢复后的全局主题会话）
- 构建日志：`/tmp/ox-build-0510.log`（首轮，
  `BUILD SUCCESSFUL in 2m 54s`）、`/tmp/ox-build-0510b.log`
  （文案缩短后，`BUILD SUCCESSFUL in 2m 29s`）
