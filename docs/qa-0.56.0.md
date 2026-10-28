# QA — alpha 0.56.0（Harness 所有权路由：外部 compact 与本地记忆隔离）

- 版本：`0.56.0`，versionCode **5609**（`0.55.0` 为 5509）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 19:05–19:20（CST）
- 目标：落实“`/compact` 只适用于 API 接入”的所有权规则。API 直连继续
  使用本地 `OxContextCompactor` + `OxMemoryStore`；OxLinux、Claude Code、
  OpenCode、CommandCode、OpenClaw 等外部 Harness 由各自管理上下文，
  本地不得改写其会话，也不得注入本地压缩摘要或记忆库。
- 版本规则：新增用户可见的 Harness 选择、会话/compact 配置与上下文所有权
  分流，属于功能迭代，按 minor 从 0.55.0 → **0.56.0**（5609），patch 清零。

## 1. 实现

### 1.1 数据模型

`OxAgent` 新增并持久化：

| 字段 | JSON key | 默认值 | 含义 |
|---|---|---|---|
| `harness` | `harness` | `api` | 上下文/compact 的归属方 |
| `harnessSession` | `harness_session` | 空 | `--resume`/session id |
| `harnessCompactCommand` | `harness_compact_command` | 空 | 用户可覆盖的 compact 命令模板 |

兼容旧 Agent：缺省字段按 `api` 处理，不迁移、不改写已有会话。

### 1.2 OxHarnessAdapter

新增统一适配层，登记 `api`、`oxlinux`、`claude`、`opencode`、
`commandcode`、`openclaw`、`hermes`、`codebuddy`、`dsh`、`pi`、
`device`、`generic` 的能力元数据：

- `isLocalApi()`：只有 `api`（含空值）走本地 compact/记忆库。
- `capabilities()`：声明是否有本地 compact、Harness compact、resume、
  工具协议、多模态、非文本事件。
- `defaultCompactCommand()`：只对公开 CLI 已确认的 Claude Code
  （`claude -p "/compact" --resume {session}`）和 CommandCode
  （`commandcode -p "/compact" --resume {session}`）提供模板；其余留空，
  等待实测，不伪造命令。
- `maybeCompact()`：历史超过阈值后，每半个阈值窗口最多触发一次，
  在独立线程执行，不阻塞聊天。
- `requestCompact()`：替换 `{session}`/`{cwd}` 后，仅 OxLinux 通过
  `OxContainerManager.run` 执行；其他传输层未接通时明确返回
  `transport-not-connected`，不假装成功。

### 1.3 上下文分流

`OxAgentsBridge.appendMemoryContext` 在读取阈值后先判断归属：

- **API 直连**：保持 0.55.0 行为——消息数/token 压力触发本地压缩，
  旧消息写入记忆库，system 注入摘要、相关记忆和查证规则。
- **外部 Harness**：不压缩、不写记忆库、不注入摘要/召回，只追加
  “上下文与 compact 由该 Harness 管理”的说明；同时调用
  `maybeCompact()` 请求 Harness 自己执行 compact。

这避免了最危险的情况：本地 compactor 把外部 Harness 的历史改写成
OrionekoX 的摘要，而 Harness 自身仍持有另一份会话状态。

### 1.4 UI 与互操作

- Agent 资料页新增“Harness”分区：Harness 选择器、会话 ID、compact
  命令；使用原生 `SettingsActivity.SettingCell`/AlertDialog，不引入
  自绘控件。
- Chatbox `orionekoAgent` 扩展导出/导入 `memory_mode`、
  `memory_threshold`、`memory_recall`、`harness`、`harness_session`、
  `harness_compact_command`；导入时做枚举/长度校验。
- 中文包 114 → 115，新增 Harness 相关文案。

## 2. 设备验证

### 2.1 外部 Harness 隔离

测试对象：`qa_harness_0560`（tgId 555000124），`harness=claude`，
`harness_session=test-session-1`，`memory_threshold=45`，
`context_length=0`，预置 60 条聊天（`qa_harness_0560_chat.json`），
Agent 指向本地 mock OpenAI `http://10.0.2.2:18999/v1`。

发送 `hello` 后抓包（`/tmp/ox_memory_requests.jsonl`）：

| 检查项 | 结果 |
|---|---|
| 请求消息数 | 22（沿用默认最近窗口，未走本地压缩） |
| `has_compression` | `false` |
| `has_digest` | `false` |
| `has_recall` | `false` |
| system 尾部 | `# 外部 Harness\n上下文与 compact 由「Claude Code」管理，本地不注入压缩摘要或记忆库。` |
| 该 Agent 记忆库行数 | 0 |
| 本包 FATAL/ANR | 无 |

说明：22 条是 `appendMemoryContext` 对非 API Agent 返回
`defaultFrom = size - 20` 的既有窗口行为，不是本地压缩；本轮只验证
“没有走本地 compact/记忆注入”，不把该窗口当作 Harness 会话管理
已经完成，也不据此推断 system/历史/当前消息的精确拆分。

### 2.2 UI

- Harness 设置页：`docs/screenshots/qa-0.56.0-harness-settings.png`
- 原生 Harness 选择器：`docs/screenshots/qa-0.56.0-harness-picker.png`
- 选择器包含 12 个模式；截图记录 Claude Code 选中状态，以及会话 ID
  与 compact 命令两行可见。本轮未单独回放“保存后重进”的持久化，
  字段写入以 `agent.json` 与导出器代码为准。

### 2.3 清理与恢复

- 删除测试 Agent、其记忆库行、测试 tgId 的 dialogs/messages，计数为 0。
- 恢复模拟器 HTTP 代理 `10.0.2.2:7890`；恢复
  `OxAccessibilityService` 与 `accessibility_enabled=1`。
- 未执行 `pm clear`，未向真实联系人发消息。

## 3. 未覆盖 / 边界（重要）

**0.56.0 是路由与隔离基础，不是“所有 Harness 已接通”。**

- 只有 API 直连的本地 compact/记忆路径经过设备验证；OxLinux 的容器命令
  钩子已接线，但没有常驻会话/PTY 测试，不能据此宣称 OxLinux compact 生效。
- Claude Code / OpenCode / CommandCode / OpenClaw 只有能力元数据与可配置
  命令模板；没有 SSH/ACP/Gateway/PTY 传输层，`requestCompact()` 会返回
  `transport-not-connected`。
- 两个默认模板来自 CLI 公开帮助，尚未在真实会话上验证；不能据此宣称
  Claude Code/CommandCode 已支持。
- Hermes、CodeBuddy、DeepSeek Harness、Pi 本机未安装，只有元数据，
  compact 命令和工具协议均待确认。
- 外部 Harness 的工具调用、图片/音频/视频、工具结果、系统事件、命令输出
  尚未做协议转换；当前只保证不把不支持的媒体静默转换成文本。
- 外部 Harness 的阈值目前只用于“是否请求 compact”，没有读取 Harness
  自身压缩状态，也没有按 Harness 分别配置阈值。
- 外部 Harness 模式不注入本地记忆库，因此“外置记忆库 + 外部 Harness”
  的镜像/共享尚未实现；这是下一阶段的明确工作。
- 本地记忆库仍是 SQLite 明文、摘要为抽取式、向量为本地哈希投影，
  Provider 没有可用 embedding 模型。

能力矩阵与后续传输层计划见 `docs/harness-adapters.md`。

## 4. 产物

- `dist/OrionekoX-0.56.0-alpha-arm64-x64.apk`（versionCode 5609），sha256
  `81253aa8933fbeeba5771e55d00a07ab86496029b9f38b507d923227b24dbd92`
- `dist/OrionekoX-0.56.0-debug-arm64-x64.apk`，sha256
  `8a1dd45890bc51bfe34189a91b468477593f0733f0e27a3a0a7936b7f97b3673`
- 构建日志：`/tmp/ox-build-0560.log`
- 抓包证据：`/tmp/ox_memory_requests.jsonl`
- 适配说明：`docs/harness-adapters.md`
