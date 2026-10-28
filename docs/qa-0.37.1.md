# QA — alpha 0.37.1（导出功能中文化补漏）

- 版本：`0.37.1`，versionCode **3719**（`0.37.0` 为 3709）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 09:10–09:20（CST）
- 目标：修复 0.30–0.37 新增导出入口/预览在中文界面残留英文的问题；
  跑一次 Ox 文案审计，确认无遗漏。
- 版本规则：仅文案与 QA，无新功能；按 patch 从 0.37.0 → **0.37.1**，
  versionCode 3719（371×10+9）。

## 1. 修改内容

- 中文包 `assets/oxlang/zh-cn.xml` 新增 27 条译文：
  - Kelivo/Chatbox：对话数、项目列表、已存在对话、导出入口/确认/结果、
    单 Agent 导出、多选导出、空选择提示等 17 条；
  - MCP：导出入口、说明、成功/空/失败 5 条；
  - 技能库：导出入口、说明、成功/空/失败 5 条。
- 更新 `OxKelivoNothing` 为“备份里没有可导入的助手或对话”。
- `OX_ZH_PACK_VERSION` 98 → 99，确保设备重新拷贝中文包。
- 无 Java 逻辑/导出格式变化；所有导出的安全边界保持原样（不含 Key/
  headers）。

## 2. 文案审计

用 Python 扫描 `ui/Ox*.java` 与 `ox/*.java` 的 `R.string.Ox*` 引用，
与 zh-cn.xml 的 `<string name>` 集合求差：

- 缺失 Ox 字符串：**0**。

## 3. 设备验证 — PASS

- Ox 设置：导入/导出行显示“导出为 Kelivo / Chatbox 备份”
  “导出选中的智能体”，说明为中文；无英文回退。
- 技能库：“导出技能 zip / 写入 skills/**/SKILL.md，可被 Kelivo、
  Chatbox 或 RikkaHub 导入”。
- MCP 服务：“导出 MCP JSON / 写入 mcpServers JSON，不含请求头或 API Key”。
- Kelivo 导入预览（multi-b 样例）：显示“对话 1 段（2 条消息）”
  “项目：Multi B”，不再出现 `Conversations:` / `Items:`。
- 本次只打开预览并取消，未写入任何 Agent/对话；删除样例 zip。
- 截图：`docs/screenshots/qa-0.37.1-ox-settings.jpg`、
  `qa-0.37.1-skills.jpg`、`qa-0.37.1-mcp.jpg`、
  `qa-0.37.1-kelivo-preview.jpg`。
- 无本包 FATAL/ANR。

## 4. 未覆盖 / 已知边界

- 只审计 `Ox*` 命名的自有字符串；Telegram 原生字符串不在范围内。
- 英文/其他语言包未同步新增这些键；中文包为本次目标。
- 未重新导入/导出数据，功能行为沿用 0.37.0 的验证结果。

## 5. 产物

- `dist/OrionekoX-0.37.1-alpha-arm64-x64.apk`（versionCode 3719），sha256
  `8876b1ce2a07912a37115a45c17c1d3d0ef561e2d8ae63b2ff00a7b425bc8486`
- `dist/OrionekoX-0.37.1-debug-arm64-x64.apk`，sha256
  `b21e0412ad0db228caa0c4eb12d72488bab7906c3d40c9e483e6fe662c7b567d`
