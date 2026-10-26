# QA — new-agent form and MCP catalogue (0.8.87, 2026-10-26 11:38–11:42)

Build: `io.github.blancatora.orionekox` versionName 0.8.87 (versionCode 1029),
emulator-5554 with `-gpu off`. Local account; no agents, MCP servers or contacts
were created or removed during this pass.

## New agent form

- 智能体 → 新建智能体 opens the native editor with: avatar picker, 资料 (name +
  remark), 技能 row (未启用技能), 模型服务 (DeepSeek · deepseek-v4-flash), 接口地址
  (`https://api.deepseek.com`), API Key, 模型 (`deepseek-v4-flash`), 从 API 获取
  模型列表, 选择预设模型, 获取 API Key, 测试连接 (未测试), 高级参数
  (温度 0.7 · Top P 1.0), 自定义请求头 / 添加请求头, and AGENTS.md.
- The save action is the top-right checkmark; exiting with back discards the draft.
  After closing, the agent list still contains the same 7 agents (no accidental
  creation).

## MCP catalogue

- MCP services page lists the existing test server “QA MCP” (HTTP 502) and
  “DeepWiki” (3 tools).
- MCP 插件商城 shows the explanatory header, remote one-tap entries DeepWiki and
  Context7, and Operit catalogue entries such as Playwright MCP, 水彩旅行, Figma MCP
  and Tavo MCP Server, each marked 本地 STDIO.
- Tapping the Playwright entry opens a native dialog with the description/repo link
  and buttons 打开仓库 / 复制配置 / 取消; no install or uninstall was triggered.
- Copy is natural; “snapshot” in the Playwright description is an MCP term and is
  acceptable.

## State

- No OrionekoX FATAL/ANR. The QA MCP entry with HTTP 502 is a pre-existing local test
  server and was left untouched.
