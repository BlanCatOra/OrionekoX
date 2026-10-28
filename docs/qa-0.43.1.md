# QA — alpha 0.43.1（多附件 Chatbox 导出回环修复）

- 版本：`0.43.1`，versionCode **4319**（`0.43.0` 为 4309）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 11:36–11:46（CST）
- 目标：0.43.0 已支持多附件导入，本轮验证现有 Chatbox 导出能否把
  2 张图片 + 2 个附件完整导出并回环导入。
- 版本规则：只修复回环中发现的单个解析 bug 并做 QA，按 patch 从 0.43.0 →
  **0.43.1**，versionCode 4319（431×10+9）。

## 1. 发现的问题与修复

- 导出包正常包含 4 个资源：2 个 `image`、2 个 `raw-attachment`；
  但回环预览只显示“2 张图片 / 1 个附件”。
- 原因：`OxKelivoImporter` 读取 zip 时用 `!n.endsWith(".json")` 排除了所有
  JSON 条目，而导出的 JSON 附件路径为 `resources/orioneko-*.json`，因此
  `data.json` 从未进入资源表，随后被静默跳过。
- 修复：只排除 `resources/` 之外的 JSON 元数据，允许
  `resources/*.json` 作为普通附件读取：
  `(!n.endsWith(".json") || n.startsWith("resources/"))`。
- 不影响 `manifest.json` / `settings.json` / `sessions/*.json` 等元数据的
  既有读取顺序。

## 2. 设备验证 — PASS

导出：

- 先导入 2,392 B 混合附件样例，再用 Agent 列表长按“导出 Chatbox 备份”。
- 导出文件 3,578 B，含 5 条消息、4 个资源：
  - 2 个 image（data URL，解码后各 300 B）；
  - 1 个 `raw-attachment` text/plain（解码后 19 B，`notes.txt`）；
  - 1 个 `raw-attachment` application/json（解码后 29 B，`data.json`）。
- 资源解码 sha256 与源文件逐一一致；对 manifest/session 扫描
  `api_key`、`ox1:`、`Authorization`、Provider 地址，命中 0 次。

回环：

- 修复前预览：2 张图片、1 个附件（复现 bug）。
- 修复后同一导出 zip 预览：2 张图片、2 个附件；导入结果
  “导入对话 1 段（5 条消息）”“导入聊天图片 2 张”“导入聊天附件 2 个”。
- 回环后 `chat.json` 5 条：文字 + 绿图、橙图、`notes.txt`、`data.json`、
  助手回复；`media/` 下 2 张图片和 2 个文件均存在。
- 原生聊天（tgId `555000088`）显示带正文的绿色照片、橙色照片、
  `notes.txt`（19 B · TXT）、`data.json`（29 B · JSON）文件气泡和助手
  回复，与源会话一致。
- 截图：`docs/screenshots/qa-0.43.1-roundtrip-chat.jpg`。

清理：

- 删除回环 Agent 和导出/样例 zip 后重启，对应 `dialogs` / `messages_v2`
  均为 0 行。
- 本包无 FATAL/ANR；已重新启用 `org.telegram.ox.OxAccessibilityService`。

## 3. 未覆盖 / 已知边界

- 回环 Agent id 会从 `kelivo_chat_s1_current` 变成
  `kelivo_chat_kelivo_chat_s1_current_current`：导入器会给会话 id 追加
  `_current`。本轮先删除原 Agent 再导入，因此没有重复；若保留原 Agent
  再导入同一导出包，会新增一个 Agent 而不是替换。
- 未与真实 Cherry Studio/Kelivo 客户端互导；导出仍为 Chatbox v2 子集。
- 本轮只验证图片/文本/JSON 附件，未覆盖 2 MB 以上资源、40 图/20 文件上限
  边界和多会话导出。

## 4. 产物

- `dist/OrionekoX-0.43.1-alpha-arm64-x64.apk`（versionCode 4319），sha256
  `9f50bd9b70899eaf331a1979978e08bf8110855ea3461a6a2f99744d18c01dd5`
- `dist/OrionekoX-0.43.1-debug-arm64-x64.apk`，sha256
  `5aeeb564141fdd034f71cf7203b87fcfdd71c2eb7eee1a4ab8a3669420dbc6b9`
