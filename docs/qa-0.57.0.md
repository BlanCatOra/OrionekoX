# QA — alpha 0.57.0（真实外部 Harness 传输：桥接、原生 compact、事件归一化）

- 版本：`0.57.0`，versionCode **5709**（`0.56.0` 为 5609）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off`）
- 时间：2026-10-28 20:08–20:57（CST）
- 目标：把 0.56.0 的“所有权路由”推进到真实传输层。Android 进程不能直接
  启动四个 CLI，因此新增电脑端 `tools/ox-harness-bridge.py`，由它运行
  Claude Code、CommandCode、OpenCode、OpenClaw 的原生会话，向 App 返回
  归一化 NDJSON；App 只渲染事件，不再用本地模型兜底。
- 版本规则：新增真实传输、原生 compact、事件归一化和资料页字段，
  属于功能迭代，按 minor 从 0.56.0 → **0.57.0**（5709），patch 清零。

## 1. 实现

### 1.1 桥接协议

`tools/ox-harness-bridge.py`（仅 Python 标准库）：

| 路由 | 作用 |
|---|---|
| `GET /v1/health` | 版本、会话数、每个 Harness 的 binary/version/transport |
| `POST /v1/sessions` | `{harness, cwd, model, agent, system}` → 桥接会话 + native session |
| `POST /v1/sessions/<id>/turns` | NDJSON 事件流 |
| `POST /v1/sessions/<id>/compact` | 该 Harness 自己的 compact |
| `DELETE /v1/sessions/<id>` | 删除桥接映射（不删 Harness 原生会话） |

安全：所有路由要求 Bearer token；默认只绑 loopback；`--host 0.0.0.0`
只用于受信 LAN/模拟器；请求体上限 64 MiB；附件单文件 12 MiB、总量 32 MiB；
token 自动生成到 `~/.config/orionekox/harness-bridge-token`（0600）；
会话登记表 `sessions.json`（0600）不保存对话正文；URL/命令按 argv 执行，
不拼接 shell；日志不打印 token 或 prompt。

### 1.2 原生会话与 compact

| Harness | Turn | Resume | Compact |
|---|---|---|---|
| OpenClaw | `openclaw agent --session-id … --json` | 同一 Gateway session | `openclaw sessions compact <sessionFile> --json` |
| CommandCode | `commandcode -p … --output-format json` | `--resume <sessionId>` | 交互 PTY，等空闲提示后发送 `/compact`，检测“对话已经过压缩” |
| OpenCode | `opencode run --format json --session …` | `--session <ses_…>` | 私有 `opencode serve` + `POST /api/session/<id>/compact`（Basic `opencode:<password>`） |
| Claude Code | `claude -p … --output-format stream-json` | `--session-id`/`--resume <uuid>` | `claude -p "/compact" --resume <id>` |

`turn_count` 之外新增 `native_ready`，避免把“刚生成的 Claude UUID”
误当成可 resume 的会话；`run_json_lines` 有超时 watchdog，避免 CLI 无输出
时永久阻塞。

### 1.3 事件归一化

桥接统一输出 `session`、`text`、`tool_call`、`tool_result`、
`command_output`、`status`、`attachment`、`error`、`done`。App 端新增
`OxHarnessTransport`，在 `OxAgentsBridge.runRound` 中优先于本地模型路径：
外部 Harness 不走 `OxModelHub`、不执行本地工具循环、不做本地 compact。

- 工具事件显示为 `🔧 name(args) …` 与 `↳/⚠ result`，并写入 chat history；
- `status=retrying/notice` 显示 `ℹ`，其余写日志；
- `attachment` 显示 `📎`，不静默下载；
- `error` 用 Harness 自己的错误文本结束回合；
- `done` 用最终文本结束气泡。

资料页新增 Bridge URL、Bridge token（掩码）、工作目录、Harness 模型、
OpenClaw Agent 与“Test harness bridge”；token 与 API Key 一样经
`OxVault` AES-GCM/Keystore 包装后落盘，`.oxagent` 导出时清空。

### 1.4 修复的集成问题

1. 外部 Harness 的 `base_url` 为空时被 `isEchoMode()` 误判为离线回声。
   现在外部 Harness 永不进入回声模式。
2. `memory_mode=off` 会让外部 Harness 跳过 `maybeCompact`。现在 compact
   请求在本地记忆开关之前执行，Harness 所有权与本地记忆开关解耦。
3. `requestCompact` 在检查命令模板前就返回
   `no compact command configured`，导致桥接 compact 永远不会被调用。
   现在桥接 harness 先走原生 compact 端点；同时删除未在真实会话验证的
   Claude/CommandCode 默认模板。
4. 桥接健康检查回调在 worker 线程直接改 View，触发
   `Only the original thread that created a view hierarchy can touch its views`。
   现在回调经 `AndroidUtilities.runOnUIThread`。
5. 重新安装后已存在的 rootfs 仍会重新下载；`isRootfsReady` 只认
   `bin/sh`。现在优先复用已解包 rootfs，并接受 `/usr/bin/dash` 等真实
   shell 路径（绕开部分 SELinux 对 app-data symlink 的 stat 限制）。

### 1.5 OxLinux 常驻会话

`OxContainerManager.runPersistent` 启动一个常驻 `proot` shell，通过
`rootfs/tmp` 下的脚本文件与 `__OX_DONE_<nonce>__<exit>` 哨兵执行命令，
同一进程保留 shell 状态；`OxHarnessAdapter` 的 OxLinux compact 已改为
调用它。**但本机真实结果是**
`proot error: execve("/usr/bin/dash"): Permission denied`：Android 的
app-data 可写目录不允许执行二进制。该限制不是命令拼接或 PTY 实现问题；
不能据此宣称 OxLinux 已可用。

## 2. 真实 Harness 验证

桥接在本机（非模拟器）运行，工作目录
`/home/blancat/orionekox-harness`，逐 Harness 使用真实 CLI；证据保存在
`/tmp/ox-bridge-smoke/*.json`。

| Harness | Turn 1 | Turn 2（resume） | Native compact | Turn 3（compact 后） |
|---|---|---|---|---|
| OpenClaw | `BRIDGE-OCL-731` | `BRIDGE-OCL-731` | `ok=true`，Gateway summary | `BRIDGE-OCL-731` |
| CommandCode | `BRIDGE-CC-731` | `BRIDGE-CC-731` | `ok=true`，PTY 显示“对话已经过压缩” | `BRIDGE-CC-731` |
| OpenCode | `BRIDGE-OC-731` | `BRIDGE-OC-731` | `ok=true`，HTTP 200 `type=compaction` | `BRIDGE-OC-731` |
| Claude Code | Provider 返回 `403 Request not allowed` | 未继续 | 命令执行，返回 `Not enough messages to compact.` | 未继续 |

Claude 的 403 是账号/Provider 侧错误，桥接没有把它包装成成功；App 会显示
`⚠ Failed to authenticate. API Error: 403 Request not allowed`。

单元/协议测试：

```
python3 -m unittest tools/test_ox_harness_bridge.py -v
Ran 13 tests in 1.358s
OK
```

覆盖 fake 流式 turn、附件落盘、错误事件、compact、Bearer 鉴权、会话
持久化/删除，以及 CommandCode/OpenCode/Claude 录制事件形状的解析。

## 3. 设备验证（debug 包）

测试对象：`bridge_test_570`（tgId 555000012），`harness=openclaw`，
`harness_endpoint=http://10.0.2.2:8765`，token 已配置，
`memory_mode=off`，`memory_threshold=4`，系统提示“简洁回答”。

1. 发送 `APP-BRIDGE-0570`，App 日志：
   `created bridge session oxs_3e03df66d6c140df native=ox-2f8bca8b02c1`，
   `harness status starting`，`finishTurn len=19`；气泡显示
   `收到。APP-BRIDGE-0570。`（截图 `qa-0.57.0-bridge-reply.png`）。
2. 继续发送 `SECOND-BRIDGE`、`THIRD-BRIDGE`、`FOURTH-BRIDGE`、
   `FIFTH-BRIDGE`，均由同一 native session 回复；重启 App 后仍复用
   `oxs_bb27e1437ea74f0d` / `ox-3758cb428abd`。
3. 历史超过 `memory_threshold=4` 后发送 `FIFTH-BRIDGE`，后台触发：
   `OxHarnessAdapter compact openclaw -> 「OpenClaw」原生压缩完成：…`，
   桥接日志 `compact session=oxs_bb27e1437ea74f0d harness=openclaw ok=True ms=7837`，
   同时该轮回复正常完成。
4. 资料页 “Test harness bridge” 显示 `Connected`；随后保存（token 被
   `ox1:` 包装）、force-stop/重启、重进资料页再测，仍为 `Connected`，
   证明 Vault 往返可用（截图 `qa-0.57.0-harness-fields.png`）。
5. 桥接健康：`bridge 0.57.0`，四个 CLI 均为 `found=true`，版本分别为
   Claude 2.1.295、CommandCode 1.88.1、OpenCode v2.0.32、OpenClaw
   2026.10.4。

## 4. OxLinux 测试（边界）

测试对象 `oxlinux_test_570`（tgId 555000013），
`harness=oxlinux`，compact 命令为
`echo OX-LINUX-$$; …; cat /tmp/ox570/hits`，预置 8 条历史。第一次触发时
rootfs 已复制到 debug 数据目录，但旧逻辑重新下载并解包后仍报
`rootfs unpacked but /bin/sh missing`；修正 shell 路径探测后：
`OxContainer using existing rootfs shell=/usr/bin/dash`，紧接着真实错误：

```
proot error: execve("/usr/bin/dash"): Permission denied
```

因此本轮只验证到“常驻 proot shell 已启动并尝试执行命令”，不能把它列为
可用的 OxLinux compact 路径。

## 5. 构建与发布

- release：`dist/OrionekoX-0.57.0-alpha-arm64-x64.apk`，versionCode 5709，
  sha256 `64f9cd0f787741187e4895c5c8df4193a2b43ff19fa75e6d6e5a6c513b6df678`
- debug：`dist/OrionekoX-0.57.0-debug-arm64-x64.apk`，
  sha256 `e3af72cf5e109752207e5c799672a9bef3421215f346e2989180bd4ff0c61657`
- 构建日志：`/tmp/ox-build-0570-release.log`、`/tmp/ox-build-0570-debug-final.log`
- release 在 0.56.0 release 上 `adb install -r` 覆盖，versionCode/Name 为
  5709/0.57.0；启动后 pid 正常、无本包 FATAL/ANR。发布截图不包含用户聊天
  列表，因此只保留测试 Agent 的资料页/回复截图。
- 模拟器 HTTP 代理保持 `10.0.2.2:7890`；`OxAccessibilityService` 与
  `accessibility_enabled=1` 保持启用。
- 清理：删除 `bridge_test_570`、`oxlinux_test_570` 及其聊天；测试 tgId
  555000012/555000013 的 `dialogs`/`messages_v2` 计数均为 0；
  桥接进程已停止，临时 token/state 已删除。未执行 `pm clear`，未向真实
  联系人发消息。

## 6. 未覆盖 / 边界（重要）

- **Claude Code 仍不可用**：传输和 `/compact` 已实测，但当前账号被
  Provider 以 403 拒绝；不能宣称 Claude 已支持。
- OpenClaw 的 CLI 路径是请求/响应 JSON，不提供 token 级流式；工具结果
  也不在其 `--json` payload 中，当前只保证文本、mediaUrl、usage 与
  contextBudgetStatus。
- 附件是“桥接落盘 + 路径交给 Harness”，OpenCode 用原生 `--file`；
  其余 Harness 依赖自己的文件工具读取，不等同于原生 inline vision。
- 桥接无 TLS，只适用于 loopback/受信 LAN；App 不会自动启动桥接。
- 外部记忆镜像/所有权交接、按 Harness 配置阈值、读取 Harness 自身
  compaction 状态、embedding Provider 与记忆质量基准仍未实现。
- OxLinux 受 Android app-data exec 限制，常驻 shell 已实现但 rootfs
  二进制无法执行；不得把 `runPersistent` 当成已可用。
- 第三轮酒吧炒饭 A/B/C 仍未恢复；本轮没有并行占用模拟器。
