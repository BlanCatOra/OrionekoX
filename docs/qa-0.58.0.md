# QA — alpha 0.58.0（Harness 上下文状态：读取、展示与 compact 判定）

- 版本：`0.58.0`，versionCode **5809**（`0.57.0` 为 5709）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-28 21:10–21:30（CST）
- 目标：0.57.0 已有真实 Harness 传输，但 App 不知道 Harness 自己的上下文
  用量/压缩建议，只能按本地历史条数猜。本轮让桥接记录每个 Harness 最近
  一轮的 usage/context，暴露只读 `/context`，App 在资料页显示，并让
  Harness 自己报告的 `should_compact` 参与 compact 判定。
- 版本规则：新增上下文遥测、`/context` 端点、UI 状态与 compact 判定输入，
  属于功能迭代，按 minor 从 0.57.0 → **0.58.0**（5809），patch 清零。

## 1. 实现

### 1.1 桥接上下文遥测

`Session` 新增 `last_context`（仅用量/预算/模型，不保存 prompt 或回复正文）：

- `status.state=context`（OpenClaw 的 `contextBudgetStatus`）记录
  `should_compact`、`prompt_tokens`、`context_budget`；
- 任意 `status.usage` 记录 token usage；
- `done` 事件把当前 `last_context` 附带为 `context`，App 无需额外请求即可
  更新；
- `GET /v1/sessions/<id>/context` 返回
  `{ok, session_id, harness, context, last_turn_at, native_session}`。
- OpenCode：`GET /api/session/<id>`（私有 `serve`，Basic `opencode:<password>`）
  的 `tokens`/`model`/`cost` 合并进 context；因此即使 `run` 事件没有
  step token，仍能读到会话累计输入/输出/reasoning/cache。
- 所有数据随 `sessions.json`（0600）持久化；仍不保存对话正文。

### 1.2 App

`OxHarnessTransport` 新增：

- `ContextCallback`、`context(agent, cb)`：GET `/context`，后台线程执行；
- 静态 `LAST_CONTEXT`：`done.context` 到达时缓存；
- `shouldCompact(agentId)`：Harness 明确 `should_compact=true` 时返回 true，
  60 秒冷却，避免同一上下文连续触发；
- `contextSummary()`：预算/输入输出/是否建议 compact 的短文本。

`OxHarnessAdapter.maybeCompact` 现在：

1. 若 Harness 自己报告 `should_compact=true`，即使本地历史未到阈值也允许
   请求 compact；
2. 否则仍按本地历史阈值与半窗口限流；
3. 同一历史长度不重复触发。

资料页新增 **Harness context** 行（仅桥接 Harness 可见），点击读取并显示
如 `in 530 · out 179 · 无需 compact`；读取失败用红色错误文本，不伪造数据。
中文字符串包 117→118。

### 1.3 真实性边界

- `should_compact=true` 的真实 Harness 场景没有被强行构造；本轮验证的是
  “读取并参与判定”的代码路径与真实上下文数据，不能宣称自动 compact
  已在真机触发。
- 上下文只代表最近一轮/会话累计用量，不是完整历史，也没有跨 Harness
  统一 tokenizer。
- OpenCode 的 context 依赖私有 `serve`；若启动失败，只有 `harness` 字段，
  App 会显示“暂无 token 统计”。
- Claude 仍受 Provider 403 限制；本轮没有新增 Claude 可用性声明。
- OxLinux 仍受 Android app-data exec 限制；本轮未改动。

## 2. 真实 Harness 上下文证据

桥接工作目录 `<harness-workdir>`，每套 Harness 一条短消息。
原始记录 `/tmp/ox-bridge-smoke/context-0580.log`。

| Harness | Turn 返回 | `/context` 关键值 |
|---|---|---|
| OpenClaw | `CTX-OCL-0580` | `context_budget=1,048,000`、`should_compact=false`、usage `input=503 / output=44 / total=14,627` |
| CommandCode | `CTX-CC-0580` | usage `inputTokens=16,669 / outputTokens=9 / cacheReadTokens=5,504` |
| OpenCode | `CTX-OC-0580` | usage `input=2,916 / output=25 / reasoning=178 / cache_read=4,224`、`model=mimo-v2.6-flash-free` |

协议测试：

```
python3 -m unittest tools/test_ox_harness_bridge.py -v
Ran 15 tests in 1.074s
OK
```

覆盖 fake context 状态、`/context` 空/有数据、done.context、OpenCode
session token 归一化，以及此前的 turn/resume/compact/auth/persistence。

## 3. 设备验证

测试对象：`ctx_test_580`（tgId 555000014），`harness=openclaw`，
`harness_endpoint=http://10.0.2.2:8765`，token 已配置，`memory_mode=off`。

1. 发送 `CTX-APP-0580`，App 日志出现
   `created bridge session oxs_e0113c00a3be47fb native=ox-d3a330ca1fd6`，
   随后 `harness context {"should_compact":false,"context_budget":1048000,
   "usage":{...}}`，回复正常结束。
2. 进入 Agent 资料页，点击 **Harness context**，行值显示
   `in 530 · out 179 · 无需 compact`；截图
   `docs/screenshots/qa-0.58.0-harness-context.png`。
3. 读取走 App 的 `OxHarnessTransport.context`，桥接日志为
   `GET /v1/sessions/.../context 200`；没有把数据写进聊天或伪造状态。
4. release 0.58.0 覆盖安装并启动，versionCode/Name 为 5809/0.58.0，
   无本包 FATAL/ANR；`OxAccessibilityService` 重新绑定，代理保持
   `10.0.2.2:7890`。

## 4. 产物

- release：`dist/OrionekoX-0.58.0-alpha-arm64-x64.apk`，sha256
  `de28f3c366919784f527642d3f63a3f482f909052db8514a95df45462c868ad0`
- debug：`dist/OrionekoX-0.58.0-debug-arm64-x64.apk`，sha256
  `b712d965841f60e6bec86bbbf3e1781c4ae24dc055ff1a3a0f86d778709a40e1`
- 构建日志：`/tmp/ox-build-0580-final.log`
- 清理：删除 `ctx_test_580`；tgId 555000014 的 dialogs/messages 计数 0；
  桥接进程停止，临时 token/state 删除；未 `pm clear`，未向真实联系人发消息。

## 5. 未覆盖 / 下一步

- 外部记忆镜像/所有权交接仍未实现；下一轮可加显式“mirror harness turns
  to local memory”开关，镜像只读、不注入 Harness。
- 按 Harness 配置阈值、Harness 自报 `should_compact=true` 的真机触发、
  OpenClaw 工具结果可见性仍未验证。
- 附件仍是路径交接；音频/视频/文件在 App 侧还没有完整入口。
- 第三轮酒吧炒饭 A/B/C 未启动。
