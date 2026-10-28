# QA — alpha 0.50.0（Chatbox 备份携带 Agent 外观主题与字体）

- 版本：`0.50.0`，versionCode **5009**（`0.49.0` 为 4909）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 15:42–16:00（CST）
- 目标：让 Chatbox / Kelivo 备份携带每个 Agent 的按 Agent 外观覆盖
  （颜色、气泡、圆角、边框、文字色、字号、行高、背景模式与图片、
  聊天/代码字体），回导后恢复；不携带密钥或 Provider 配置。
- 版本规则：新增用户可见的备份内容与恢复行为，属于功能迭代，
  按 minor 从 0.49.0 → **0.50.0**（5009），patch 清零。

## 1. 实现

### 1.1 导出

- 仅当 Agent 存在独立的按 Agent 外观文件（`files/ox_chat_agents/
  agent_<tgId>.json`）时，才在 session/copilot 写私有 `orionekoTheme`；
  跟随全局外观的 Agent 不会被伪造成独立主题。
- 主题字段：`mode`、`backgroundMode`、`backgroundArgb`、
  `background2Argb`、`backgroundAlpha`、`backgroundDim`、
  `backgroundBlur`、`amoled`、`gradientIndex`、`inBubbleArgb`、
  `outBubbleArgb`、`bubbleAlpha`、`cornerRadius`、`bubbleStyle`、
  `borderArgb`、`borderWidth`、`textArgb`、`fontScale`、`lineHeight`。
- 绝对路径不进入备份：`backgroundImage`、`fontPath`、`codeFontPath`
  统一清空。背景继续使用既有 `background` 资源；字体写成
  `fonts/<agentId>/chat.ttf` 与 `fonts/<agentId>/code.ttf`。
- 字体上限：单文件 8 MiB、总量 16 MiB、最多 40 个；超出时跳过并记录
  note。导出信息与单 Agent 确认框加入“外观主题（颜色、气泡、字体、
  背景）”。

### 1.2 导入

- 预览统计 `orionekoTheme`，显示“将恢复 N 个智能体的外观主题
  （颜色、气泡、字体与背景；已有主题则保留）”。
- `fonts/` 条目在后台读取，单文件 8 MiB、总量 16 MiB；写盘后用
  `Typeface.createFromFile` 校验，无效字体删除并跳过。
- 导入完成后先对已存在的 Agent 应用主题，再套用背景资源：
  `OxChatTheme.applyImportedAgentTheme` 把备份 JSON 恢复成按 Agent
  覆盖，背景路径与字体路径替换为本机实际文件。
- 已有按 Agent 主题文件时保留本地主题；为跳过主题而临时写出的字体
  会被删除，不留下孤儿文件。
- 结果提示“已恢复 N 个智能体的外观主题（含字体 N 个）”，跳过时追加
  “其余保留已有自定义主题”；中文包 109 → 110。

### 1.3 修复

- 首轮导出只得到 108 KB 的代码字体，2.37 MB 的聊天字体被静默丢弃：
  导出器复用了资源读取函数，后者上限为 2 MiB。
- 新增字体专用读取路径 `readFont` / `readFile(file, maxBytes)`，
  上限 8 MiB；重建后同一 Agent 的 zip 同时包含 2,371,712 B 与
  108,128 B 两个字体，sha256 与源文件一致。

## 2. 设备验证 — PASS

### 2.1 Copilot 路径（无聊天 Agent）

测试对象：`QA Theme 0500`，tgId 555000106，无聊天、有人设；按 Agent
主题：

- 背景图 775 B（`#10203A` 深蓝渐变图），`backgroundDim=40`、
  `backgroundBlur=2`；
- 收气泡 `#263238`、发气泡 `#7C4DFF`、气泡不透明度 230、圆角 20、
  边框 `#00E5FF` 2dp、文字白色、字号 1.12×、行高 1.3×；
- 聊天字体 2,371,712 B（Roboto Regular），代码字体 108,128 B
  （Droid Sans Mono）。

导出单 Agent Chatbox 备份 1,453,353 B：

- `copilots.json` 含完整 `orionekoTheme`，三个路径字段为空；
- `fonts/qa_theme_0500/chat.ttf` 与 `code.ttf` 均存在，sha256 分别为
  `9ca9debb…`、`db19a1fd…`，与源文件一致；
- 背景资源解码后 sha256 `bb278116…`，与源图一致；
- 全文扫描 `api_key`、`base_url`、`baseUrl`、`headers`、
  `Authorization`、`Bearer `、`sk-`、`ox1:` 均 0 命中，未出现
  `/data/data/` 绝对路径。

删除主题文件、两个字体与背景图后回导：

- 预览“将恢复 1 个智能体的外观主题（颜色、气泡、字体与背景；
  已有主题则保留）”；
- 结果“更新 1 / 已恢复 1 个智能体的偏好设置 / 已恢复 1 个智能体的
  外观主题（含字体 2 个）/ 已套用 1 个聊天背景”；
- 回读主题：所有颜色、背景模式、模糊、暗化、圆角、边框、字号、行高
  与源一致（JSON 浮点回读为 1.1200000047/1.2999999523，属 float
  精度），背景与两个字体路径指向新文件；
- 背景 sha256 `bb278116…`、聊天字体 `9ca9debb…`、代码字体
  `db19a1fd…`，均与源一致；
- 打开该 Agent 会话：深蓝渐变背景、暗色收气泡、青色 2dp 边框、
  20dp 圆角与白色文字可见，主题确实应用到原生聊天。

重复导入同一 zip：

- “已恢复 0 个智能体的外观主题；其余保留已有自定义主题”；
- `oxfonts` 中 `chatbox_qa_theme_0500` 文件仍为 2 个，没有孤儿字体；
- 主题内容保持不变（背景套用会重写同一份 JSON，字段与大小不变）。

### 2.2 Session 路径（有聊天 Agent）

测试对象：`QA Theme Session 0500`，tgId 555000107，1 条助手消息；
主题为深绿色背景、`#2E7D32` 收气泡、`#FF8F00` 发气泡、14dp 圆角、
`#B2FF59` 1dp 边框、字号 1.05×、行高 1.15×、聊天字体
Roboto Regular。

- 导出 1,383,082 B，`sessions/qa_theme_s_0500/session.json` 含
  `orionekoTheme` 与 `orionekoAgentId`；`fonts/qa_theme_s_0500/
  chat.ttf` sha256 `9ca9debb…` 与源一致；敏感字段 0 命中。
- 删除主题、字体与背景后回导：预览“对话 1 段（1 条消息）”并提示
  恢复 1 个外观主题；结果“导入对话 1 段 / 已恢复 1 个智能体的外观
  主题（含字体 1 个）/ 已套用 1 个聊天背景”。
- 回读主题颜色、圆角、边框、字号、行高与源一致，背景与字体哈希一致；
  会话历史被正常替换。

### 2.3 稳定性与清理

- 导出、预览、导入、重复导入与原生会话打开全程无本包 FATAL/ANR。
- 清理：删除两个测试 Agent、两个按 Agent 主题文件、三个字体文件
  （原始与恢复）与下载 zip；555000106/555000107 的
  `dialogs`/`messages_v2` 均为 0；未执行 `pm clear`，未删除其他
  Agent 或用户数据。
- 重装 release 5009 后恢复无障碍服务，中文包版本 110。

## 3. 未覆盖 / 已知边界

- `orionekoTheme` 与 `fonts/` 是 OrionekoX 扩展；真实
  Kelivo/Chatbox 客户端未互测，预期会忽略。
- 只备份按 Agent 覆盖；跟随全局外观的 Agent 不写主题，全局主题本身
  仍不在 Chatbox 备份内。
- 只备份按 Agent 主题，按会话（dialog）覆盖不单独携带；导入后会话
  覆盖仍以本机文件为准。
- 已有本地按 Agent 主题时故意保留，不覆盖用户自定义；要恢复备份主题
  需先清除本地覆盖。
- 字体必须是平台可解析的 TTF/OTF；无效文件会删除并跳过。字体文件
  以固定 `.ttf` 名称打包，不保留原文件名/扩展名。
- 不携带视频背景、字体授权信息或字体内部的额外元数据语义；仅复制
  文件字节并校验可解析。
- 主题 JSON 不含绝对路径；背景与字体在导入端重新落盘，因此换机后
  路径会变化。

## 4. 产物

- `dist/OrionekoX-0.50.0-alpha-arm64-x64.apk`（versionCode 5009），sha256
  `4333fadd38652f3ce44f6ff757675608b8a0f8f59f97717e670231105a44461f`
- `dist/OrionekoX-0.50.0-debug-arm64-x64.apk`，sha256
  `e6b7f10b4393e31a459a615f73584964e9b5900ebd5f1200b722ea9fa441285e`
- 截图：`docs/screenshots/qa-0.50.0-import-preview.png`、
  `qa-0.50.0-import-result.png`、`qa-0.50.0-theme-restored.png`
- 构建日志：`/tmp/ox-build-0500.log`（首轮，
  `BUILD SUCCESSFUL in 2m 50s`）、`/tmp/ox-build-0500b.log`
  （字体上限修复后，`BUILD SUCCESSFUL in 1m 54s`）
