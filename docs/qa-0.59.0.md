# QA — alpha 0.59.0（外部 Harness 显式只读记忆镜像）

- 版本：`0.59.0`，versionCode **5909**（`0.58.0` 为 5809）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-28 21:33–21:50（CST）
- 目标：外部 Harness 默认仍不注入本地摘要；新增默认关闭的显式开关，把
  Harness 的 user/assistant/tool 回合以只读 mirror 写入本地
  `OxMemoryStore`，不反注入 Harness，并能在记忆页统计/查看；切回 API
  后可被本地 recall 注入。API 直连继续使用本地 compact。
- 版本规则：新增用户可见开关、持久化字段、镜像写入与召回交接，属于功能
  迭代，按 minor 从 0.58.0 → **0.59.0**（5909），patch 清零。

## 1. 实现

### 1.1 显式开关

- `OxAgent.harnessMirror`（JSON `harness_mirror`，默认 `false`）。
- Agent 资料页新增 **Mirror harness turns to local memory** 行，仅对四个
  桥接 Harness 显示；默认 Off，点击切换；`buildFromFields` 持久化。
- Chatbox `orionekoAgent` 导出/导入同步 `harness_mirror`；`.oxagent`
  通过 `toJson` 自动携带（非敏感字段）。
- 已有 Agent 缺省 false，不迁移、不改变行为。

### 1.2 只读镜像

`OxMemoryStore.ingestHarnessMirror(agentId, userText, assistantText, tools, ts)`：

- user turn → `kind=turn, role=user, source=harness-mirror`；
- assistant turn → `kind=turn, role=assistant, source=harness-mirror`；
- tool entries（App 实际收到的 `tool_call/tool_result`）→
  `kind=tool, source=harness-mirror-tool`；
- user 文本走现有 `extractFacts`，事实行 source 也为 `harness-mirror`；
- 镜像行使用**不含时间戳的稳定 hash**，重复发送/重试/重启后同一文本只
  保留一行；assistant 文本不同则各保留一行；
- 写库在独立线程，不阻塞回复；失败回合（`⚠` 开头）不镜像；
- 外部 Harness 的 `appendMemoryContext` 仍提前返回，镜像行不会注入
  Harness；只有用户把该 Agent 切回 `harness=api` 后，本地 digest/recall
  才可能使用这些行。
- 记忆页统计增加 `· 镜像 N`；Memory library 对话框的最近条目带
  “镜像对话/镜像工具”标签。镜像不是注入，也不是 Harness 的 compact。

### 1.3 空闲监控竞态修复

本轮还修复了监控器在 dispatch 后与调度器写回 `enabled=1` 的竞态：
观察到投递后，连续 12 秒重复 `disable()`，再进入冷却，避免同一轮工作
期间被二次投递。监控服务已重启，当前 automation 为 `enabled=0`。

## 2. 设备验证

测试对象：`mirror_test_590`（tgId 555000015），
`harness=openclaw`，`harness_mirror=true`，`memory_mode=off`。

1. 连续两次发送 `MIRROR-0590 ALPHA-590`。每次 App 日志
   `harness mirror agent=mirror_test_590 rows=2`；SQLite 中
   `source=harness-mirror` 共 3 行：1 条 user（完全相同的文本只保留
   1 条，稳定 hash 去重）、2 条 assistant（两次回复“记住了/还在”不同）。
2. 强制停止并重启 App，`oxmemory.db` 行数仍为 3；记忆页显示
   `3 条 · 长期 0 · 短期 3 · 事实 0 · 镜像 3`，Memory library 对话框
   显示 3 条“镜像对话”，截图
   `docs/screenshots/qa-0.59.0-memory-library.png`。
3. 资料页镜像开关显示 On（截图
   `docs/screenshots/qa-0.59.0-mirror-toggle.png`）。
4. 切回 API 验证召回：把同一 Agent 改为 `harness=api`、
   `base_url=http://127.0.0.1:18999/v1`、`model=mock`、
   `memory_mode=normal`；本机 mock 记录到 1 次请求，system prompt
   包含：
   ```
   # 与当前消息相关的记忆（按遗忘曲线排序）
   1. ... 用户：MIRROR-0590 ALPHA-590
   2. ... 助手：MIRROR-0590 ALPHA-590
   3. ... 助手：MIRROR-0590 ALPHA-590
   ```
   相关度 0.89/0.81/0.80，证明镜像行在切换后本地可召回。镜像本身没有
   被写回 Harness（外部模式在 digest/recall 之前返回）。
5. 清理：删除 `mirror_test_590`，删除其 `oxmemory.db` memories 与
   memory_state 行；tgId 555000015 的 dialogs/messages 为 0；桥接与
   mock 停止、临时 token/state 删除、adb reverse 移除。

## 3. 构建与发布

- release：`dist/OrionekoX-0.59.0-alpha-arm64-x64.apk`，sha256
  `6e444203111d4b11a2ccf12794e242818922dbd1faa4249a8b7883965962f0fa`
- debug：`dist/OrionekoX-0.59.0-debug-arm64-x64.apk`，sha256
  `664bdb43b858860fc2200704e7ca07147f003e5d8009c516a823f7fd76b5c85e`
- 构建日志：`/tmp/ox-build-0590-final.log`；桥接 15 个测试通过。
- release 覆盖安装后 versionCode/Name 为 5909/0.59.0，启动无本包
  FATAL/ANR；无障碍重新绑定，代理保持 `10.0.2.2:7890`。

## 4. 边界

- OpenClaw 的 `--json` 不暴露工具结果，所以 OpenClaw 的镜像目前通常只有
  user/assistant；tool 镜像只在 App 实际收到 `tool_call/tool_result`
  的 Harness（CommandCode/OpenCode）上生效。
- 镜像行进入 SQLite 明文库，安全边界与现有本地记忆库相同；没有单独加密
  或按条删除。
- “镜像 ≠ 注入”：外部模式不读这些行；只有切回 API 才参与 digest/recall。
- `should_compact=true` 的真实场景仍未构造；Claude 仍 403；OxLinux 仍受
  Android app-data exec 限制；第三轮 A/B/C 未启动。
