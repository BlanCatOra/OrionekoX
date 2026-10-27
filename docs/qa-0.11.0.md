# QA — alpha 0.11.0（主题资产包 `.oxtheme` 导出/导入）

- 版本：`0.11.0`，versionCode **1419**
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off`）
- 时间：2026-10-27 20:00–20:12（CST）
- 目标：补齐主题迁移时“只带走颜色、丢失背景图与字体”的缺口（Operit #1104 同类问题）。

## 1. 新增能力

- `OxChatTheme.exportPackage(name)`：把当前外观写成 zip：
  - `theme.json`（全部快照字段 + `assets` 映射）
  - `background.<ext>`（当前背景图）
  - `chat-font.ttf` / `code-font.ttf`（当前导入字体）
- `previewBytes(byte[])`：识别 JSON 或 zip，读取 `theme.json` 生成摘要，并标注“含背景图 / 含字体”；不写盘。
- `importBytes(byte[])`：JSON 直接导入；zip 先把背景/字体恢复到 app-private 目录，再重写快照路径并写入当前作用域（全局或 Agent）。
- 外观页“导出主题包（.oxtheme）”使用 SAF `CREATE_DOCUMENT`，默认文件名 `orioneko-theme.oxtheme`；导入仍走“导入主题 / 预设 JSON”入口，支持 `*/*`。

## 2. 设备验证

### 2.1 字体导入 — PASS

- 推送 `/sdcard/Download/qa-sans.ttf`（Adwaita Sans，879796 B）。
- 外观页 → “导入聊天字体（TTF）” → 文件选择器搜索 `qa-sans` → 选中。
- 页面显示 `已导入字体 · chat_1793102842095.ttf`，无崩溃。

### 2.2 背景图 — PASS

- 外观页 → “背景图片” → 选择 `verify-rice.jpg`（640×480）。
- 显示 `ox_chat_bg`，并出现“清除背景图片”行；背景样式为“图片”。

### 2.3 导出 `.oxtheme` — PASS

- 点“导出主题包（.oxtheme）”→ SAF 保存到 Download，文件 464623 B。
- `unzip -l`：
  - `background.img` 16360 B
  - `chat-font.ttf` 879796 B
  - `theme.json` 741 B
- `theme.json` 含 `backgroundMode=image`、`bubbleStyle=frosted`、`borderWidth=1`、`fontScale=1.2`、`fontPath` 与 `assets` 映射；无 API Key、手机号或账号信息。

### 2.4 导入往返 — PASS

- 先把聊天字体切回“系统”，再导入 `orioneko-theme.oxtheme`。
- 预览弹窗：
  `背景 ox_chat_bg · 收 #2D2A45 · 发 #7C5CFF · 86% · 18dp · 1.2x · 磨砂 · 边框 1dp · 含背景图 · 含字体`
- 点“应用”后：
  - 聊天字体恢复为 `imported_chat_1793103043384.ttf`
  - 背景图片恢复为 `ox_chat_bg_imported_1793103043384.img`
  - 背景样式仍为“图片”，并可清除。
- 说明资产已从 zip 恢复到 app-private 目录，不依赖原导出设备的路径。

## 3. 未覆盖

- 导出 code-font（当前未导入代码字体，包内无 `code-font.ttf`）。
- 导入到另一个 Agent 作用域（当前包应用到全局页；Agent 页使用同一 `importBytes` 路径，未单独截图）。
- 旧版纯 JSON 主题导入回归（0.9.1/0.10.0 已验证，本轮未重复）。
- zip 内损坏字体/超大条目的拒绝路径（代码有 8MB/512KB/30 项上限，未构造恶意包）。

## 4. 崩溃与产物

- 无 FATAL/ANR；文件选择器与 SAF 均正常。
- `dist/OrionekoX-0.11.0-alpha-arm64-x64.apk`，sha256 `05b3fd0c6418b278265d5550025a4165c4fdfac9f0af7e80dfa65e013b01b786`
- `dist/OrionekoX-0.11.0-debug-arm64-x64.apk`，sha256 `48b3d668860d82a754ec67eadd16d9936d56cb5cf6164b8c886b97c360f9de8e`
