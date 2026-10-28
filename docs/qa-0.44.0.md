# QA — alpha 0.44.0（Chatbox / Kelivo 备份携带长期记忆）

- 版本：`0.44.0`，versionCode **4409**（`0.43.2` 为 4329）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 12:20–12:43（CST）
- 目标：把 Agent 的长期记忆写入 Chatbox v2 备份，并在导入时恢复；
  同时让中文用户升级后真正看到新文案。
- 版本规则：新增用户可见的备份字段与恢复行为，并调整本地化包版本，
  按 minor 从 0.43.2 → **0.44.0**，versionCode 4409（440×10+9）。

## 1. 实现

### 1.1 导出

- Agent 有聊天时写入 session，无聊天时写入 copilot；两者都新增
  `orionekoMemory` 字段，仅在记忆非空时写入。
- 记忆从 `OxAgentStore.loadMemory` 读取尾部：
  - 单 Agent 上限 64 KiB，超出保留最近部分并在结果 notes 记录；
  - 全部 Agent 合计上限 256 KiB，超出部分截断并记录；
  - `Result.memories` 统计实际带记忆导出的 Agent 数。
- 记忆不改变原有隐私边界：API Key、Provider 地址、Authorization 头仍不导出。

### 1.2 导入

- `memoryOf` 读取 `orionekoMemory`，trim 后为空则忽略，最长保留尾部
  200,000 字符。
- 预览按 Agent 去重统计 `Preview.memories`，并新增提示
  “将恢复 N 个智能体的长期记忆（覆盖现有记忆）。”。
- 实际导入在 copilot 助手和会话两条路径都调用
  `OxAgentStore.saveMemory`，结果新增“已恢复 N 个智能体的长期记忆”。
- 没有该字段的外部 Chatbox / Kelivo 备份行为不变。

### 1.3 导出结果文案

- 主行改为“已导出 %1$d 个智能体（含记忆 %2$d 个）、…”，记忆数并入
  第一行，避免 Toast 第二行被系统截断。
- 单 Agent 导出文案同样加入记忆数；移除独立的记忆行。

### 1.4 本地化包版本

- 发现 `LocaleController.OX_ZH_PACK_VERSION` 仍为 99 时，升级安装不会
  覆盖 `files/ox_zh-cn.xml`，即使 APK 内已更新中文，界面仍显示旧文案。
- 将包版本提升到 100；启动后确认 `ox_zh_version=100`，设置页说明行、
  导出确认框和 Toast 均显示“长期记忆”。

## 2. 设备验证 — PASS

### 2.1 单 Agent 记忆回环

- 测试 Agent `QA2`（id `598671f7`，tgId 555000007），聊天 556 B。
- 写入 157 B / 73 字符记忆：
  `长期记忆回环测试 0.44.0`、回答偏好、项目代号与测试日期。
- 导出选中该 Agent：1,626 B zip；`sessions/598671f7/session.json` 含
  `orionekoAgentId=598671f7` 与完整 73 字符 `orionekoMemory`；
  `copilots.json` 为空。
- 解压后扫描 `apiKey` / `api_key` / `baseUrl` / `base_url` /
  `Authorization` / `sk-`：0 命中。
- 删除 `memory.txt` 后导入同一 zip：
  - 预览：“将恢复 1 个智能体的长期记忆（覆盖现有记忆）。”
  - 结果：“已恢复 1 个智能体的长期记忆”“已存在的 1 段对话历史已替换”。
  - 恢复文件 157 B，sha256
    `a7744d01530f1ddabd35f481129a472991cebea62fc0a3f973c97c09bc672910`。
- 再次导入同一 zip：文件 hash 不变，Agent 目录数保持 25，chat.json 仍
  556 B，无重复 Agent 或重复消息。

### 2.2 全量导出与文案

- 最终 4409 构建执行“导出为 Kelivo / Chatbox 备份”，得到 482,642 B zip。
- Toast 完整显示：
  “已导出 21 个智能体（含记忆 14 个）、12 段对话、245 条消息
  （12 个文件）”；截图 `docs/screenshots/qa-0.44.0-export-toast.png`。
- zip 内 12 个 session（10 个带记忆）与 9 个 copilot（4 个带记忆），
  合计 14 个带记忆 Agent，与 Toast 一致。
- 重新扫描敏感字段：0 命中。
- 设置页说明行与确认框均显示“长期记忆”；截图
  `docs/screenshots/qa-0.44.0-export-dialog.png`。

### 2.3 稳定性与清理

- 主 Tab 聊天 / 联系人 / 设置 / 我的 / 终端切换正常，Ox 设置可进入并
  滚动到导出区；无本包 FATAL/ANR。
- 清理：删除 QA2 被注入的 `memory.txt`（恢复无记忆状态），删除
  `/sdcard/Download/orioneko-selected-backup.zip` 与
  `/sdcard/Download/orioneko-chatbox-backup.zip` 两个测试导出包；
  未执行 `pm clear`，未删除 Agent 目录或其他用户数据，未给真实联系人发消息。
- 重新安装 release 后已重新启用
  `io.github.blancatora.orionekox/org.telegram.ox.OxAccessibilityService`，
  留在 release 4409 运行。

## 3. 未覆盖 / 已知边界

- `orionekoMemory` 是 OrionekoX 扩展字段；真实 Chatbox / Kelivo 客户端会
  忽略它，因此只验证了 OrionekoX 自身的导出→导入回环。
- 有无聊天两种 Agent 都会导出记忆；本轮只对“有聊天”的 Agent 做了完整
  删除→恢复回环，无聊天 copilot 的记忆导入未单独回环。
- 记忆按字符截断且只保留尾部；超出上限时旧内容会丢失，notes 会提示。
- 导入会覆盖现有记忆（预览已明确提示），没有合并或撤销。
- 未与真实 Chatbox 客户端互导，也未在真机/GPU 环境复验。

## 4. 产物

- `dist/OrionekoX-0.44.0-alpha-arm64-x64.apk`（versionCode 4409），sha256
  `6acdb24b00fb8b61f2ed93931341ba5217a676b7ab4433d4a01310fe0359a880`
- `dist/OrionekoX-0.44.0-debug-arm64-x64.apk`，sha256
  `855e454aa2092fd747be7453b980fc83a620e6d5fe4aeae86a9946c4cd176ef6`
