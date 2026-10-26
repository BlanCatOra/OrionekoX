# 参考项目兼容与美化分析：Operit / Kelivo / RikkaHub

本文记录 OrionekoX 对三个参考项目的调研、兼容格式和落地顺序。实现状态会随版本更新；
未验证的格式明确标为“待验证”，不把推测写成事实。

参考对象：

- [Operit](https://github.com/AAswordman/Operit) / [Operit2](https://github.com/AAswordman/Operit2)
- [Kelivo](https://github.com/Chevey339/kelivo)（Flutter，AGPL-3.0）
- [RikkaHub](https://github.com/rikkahub/rikkahub)（Kotlin/Compose，AGPL-3.0）

## 1. 已有能力与缺口

| 能力 | Operit | Kelivo | RikkaHub | OrionekoX 现状 |
|---|---|---|---|---|
| Provider/模型 | 多 Provider | OpenAI/Gemini/Claude/兼容端点，多 Key 负载均衡 | OpenAI/Google/Anthropic 兼容 | 已有 32 预设、兼容端点、模型拉取 |
| 角色/助手 | 角色卡 | assistants + 世界书 + 记忆 | agents + SillyTavern 角色卡 | 本地 Agent、系统提示词、记忆、技能 |
| 群聊 | 有 | 多助手会话 | 有 | 仅本地 Agent 群，成员增删已本地化 |
| MCP | 有 | HTTP/SSE/STDIO + OAuth | MCP | Streamable HTTP；stdio 待补 |
| 技能 | ToolPkg/可执行 Skills | `SKILL.md` 文件夹、md/zip/GitHub 导入 | Skills | 只读 `SKILL.md`，按 Agent 注入 |
| 终端/沙箱 | PRoot Ubuntu | PRoot/iSH/桌面 shell + 8 个文件工具 | proot workspace | 多 TTY；PRoot 受 W^X 阻塞 |
| 工作流 | 有 | 定时任务 | 有 | 间隔/每日定时 Agent |
| 导入 | 自有格式 | Cherry Studio、Chatbox、备份 | SillyTavern 角色卡、Provider QR | `.oxagent` |
| 美化 | 主题/卡片 | 自定义主题 JSON、气泡磨砂/纯色、壁纸、字体 | Material 3 Expressive | Telegram 原生主题 + Ox 设置 |

## 2. 导入/导出兼容

### Kelivo

- **MCP JSON**：`lib/core/services/mcp/mcp_config_import.dart` 接受顶层
  `{"mcpServers": {...}}` 或直接以服务器名为键的对象；每项支持
  `type` = `stdio|http|streamablehttp|streamable-http|sse`，以及
  `command`、`args`、`env`、`url`/`baseUrl`、`headers`、`cwd`/`workingDirectory`、
  `workspaceId`、`disabled`/`isActive`。导入是“只新增不覆盖”，不导入缓存工具或 token。
  → OrionekoX 可直接复用这个 schema 做“从 JSON 导入 MCP”，stdio 需先补齐运行时。
- **Skills**：每个技能是一个含 `SKILL.md` 的目录；来源支持粘贴、文件、GitHub；记录
  启用状态、使用次数、安装/更新时间（`lib/core/models/skill_record.dart`）。
  → 可先实现 `SKILL.md`/zip 导入到 Agent 工作区，再按现有注入机制生效。
- **主题**：`lib/theme/custom_theme.dart` 的主题 JSON 为
  `{id, name, primaryColorArgb, secondaryColorArgb?, tertiaryColorArgb?}`，并显式兼容
  RikkaHub 的同名字段；气泡另有 `ChatBubbleStyleOverrides`（背景/边框/文字 ARGB、
  圆角、边框宽度/透明度、磨砂 sigma/透明度）。
- **备份**：仓库有本地快照、WebDAV、S3 与 Cherry Studio/Chatbox 导入器；具体归档
  schema 仍需逐文件核对，暂不对外承诺字段级兼容。

### RikkaHub

- **SillyTavern 角色卡**：JSON/PNG 角色卡导入，可映射到本地 Agent 的名称、头像、
  人设与首条消息。
- **Provider 配置**：支持 QR 导入/导出；具体编码尚未核对，先做 JSON 配置导入。
- **备份**：README 未描述通用聊天备份格式，需读源码后再定；不推测。
- **MCP/Skills**：与 Kelivo 类似，优先复用 `mcpServers` 与 `SKILL.md` 两条通路。

### Operit

- 已有 `.oxagent` 导出/导入（记忆、技能、头像、工作区），下一步补角色卡、ToolPkg
  与工作区目录的兼容；ToolPkg 属于可执行扩展，必须带权限提示与沙箱边界。

### 统一导入原则

1. 先预览（文件类型、条目数、冲突项），用户确认后才写入。
2. 幂等：同一来源重复导入不产生重复 Agent/MCP/技能。
3. 不覆盖：ID 冲突时创建副本或跳过，永不静默替换用户数据。
4. 可回滚：导入前生成快照，失败时恢复到导入前状态。
5. 安全：拒绝路径穿越、超大文件、可执行脚本未经确认直接运行。

## 3. 插件/扩展兼容

三个项目的“插件”实际上分成三类，OrionekoX 按风险从低到高实现：

1. **MCP 服务器**（HTTP/SSE/STDIO）：统一成内部 `McpServerConfig`；STDIO 需要
   进程管理、环境变量白名单、超时与输出上限，并在首次运行时提示命令与参数。
2. **`SKILL.md` 技能包**：只读提示词注入风险最低，先支持文件/zip 导入、启用开关、
   按 Agent 绑定；可执行脚本作为后续独立权限项。
3. **ToolPkg/可执行扩展**：需要明确的能力声明与用户授权，默认不自动执行；与现有
   逐工具 auto/ask/deny 权限模型合并。

## 4. 美化方案

### Operit

- 角色卡/会话的卡片化信息层级、状态标签与分组列表。
- 设置项用“标题 + 说明 + 当前值”的三段式，减少纯图标入口。
- 落地：Ox 设置与 Agent 编辑器继续用 `SettingCell`，补分组说明与状态值，不新造列表。

### Kelivo

- 自定义主题：一个主色 + 可选 secondary/tertiary，生成亮/暗两套色板。
- 聊天气泡：默认/磨砂/纯色，可调背景、边框、文字、圆角、模糊与透明度。
- 壁纸/渐变背景、UI 与代码字体分开、动态取色。
- 落地优先级：主题 JSON 导入 → 聊天气泡圆角/透明度 → 壁纸；全部复用 Telegram
  原生 `Theme`/`ChatActivity` 绘制路径，避免自绘气泡破坏选择、转发和媒体行为。

### RikkaHub

- Material 3 Expressive 的动态色、较大圆角、层级阴影与状态动效。
- 落地：只借鉴比例与层级，不直接搬 Material 组件；保持 Telegram 原生手势与列表。

## 5. 实施顺序（按用户价值/风险）

1. **MCP JSON 导入**（Kelivo/RikkaHub/Claude Desktop 通用 `mcpServers`），先 HTTP/SSE，
   stdio 在运行时就绪后开放。
2. **`SKILL.md`/zip 技能导入 + 启用开关**，复用现有技能注入。
3. **主题 JSON 导入**（`primaryColorArgb` 等）+ 气泡圆角/透明度设置。
4. **SillyTavern 角色卡导入**（RikkaHub 路径）与 `.oxagent` 双向补全。
5. **Kelivo 备份/Cherry Studio/Chatbox 导入**：先做只读预览与字段映射，再写入。
6. **Operit ToolPkg/可执行 Skills**：最后做，必须先有权限与沙箱。

每一项都要有：格式样例、预览、幂等测试、失败回滚、QA 记录。
