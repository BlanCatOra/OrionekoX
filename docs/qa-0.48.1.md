# QA — alpha 0.48.1（Chatbox 备份携带 Agent 偏好）

- 版本：`0.48.1`，versionCode **4819**（`0.47.1` 为 4719）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 14:50–15:08（CST）
- 目标：让 Chatbox / Kelivo 备份携带每个 Agent 的工具权限、置顶、备注、
  开场白、推理/工具调用参数等偏好，并在回导时恢复；不携带密钥、
  Provider 地址、请求头或 `extra_body`。
- 版本规则：新增用户可见的备份内容与恢复行为，按 minor 从 0.47.1 →
  **0.48.0**（4809）；QA 发现 `context_length` 被 Provider 回退覆盖，
  修复后按 patch 从 0.48.0 → **0.48.1**（4819）。没有把修复继续留在
  0.48.0 产物里。

## 1. 实现

### 1.1 导出

- session 与 copilot 都写私有字段 `orionekoAgent`，但只在该 Agent 至少
  有一项非默认偏好时写入，避免给所有普通 Agent 增加噪声。
- 携带字段：`emoji`、`pinned`、`remark`、`first_mes`、
  `reasoning_effort`、`tool_choice`、`parallel_tool_calls`、
  `context_length`、`presence_penalty`、`frequency_penalty`、`top_k`、
  `seed`、`stop`、`tools_policy`。
- 明确不导出：`api_key`、`base_url`、`provider_id`、`headers`、
  `extra_body`。其中 `headers`/`extra_body` 可能被用户放入认证信息，因此
  即使不是密钥也一律不进入备份。
- 字符串与集合做上限：emoji 16、备注/stop 2 KiB、开场白 16 KiB、
  `tools_policy` 最多 256 项且只接受 `auto`/`ask`/`deny`；数值按 UI
  允许范围收敛。

### 1.2 导入

- 预览统计带 `orionekoAgent` 的 Agent，显示
  “将恢复 N 个智能体的偏好设置（工具权限、置顶、备注、开场白与推理参数）”。
- 导入时在 Provider 接线、模型覆盖、技能关联都处理完之后再应用偏好，
  避免 `context_length` 被 Provider 回退值覆盖。
- `tools_policy` 只接受 `auto`/`ask`/`deny`，未知键/值丢弃；
  数值边界与导出端一致。
- 不覆盖 name、人设、头像、记忆、技能和聊天；只恢复偏好字段。

### 1.3 文案

- 导出说明加入“助手偏好”；全量与单 Agent 确认框注明包含偏好，
  并明确“不含 API Key、Provider 地址与请求头”。
- 中文包 107 → 108。

## 2. 设备验证 — PASS

测试对象（自建，未使用真实联系人）：

- `QA Prefs Session 0480`，session 路径，tgId 555000110，2 条消息。
- `QA Prefs Copilot 0480`，chatless copilot 路径，tgId 555000111。
- 偏好：
  - session：`pinned=true`、备注“偏好回导 0480”、开场白
    “偏好开场白 0480”、`reasoning_effort=high`、
    `tool_choice=required`、`parallel_tool_calls=false`、
    `context_length=32000`、presence 0.5、frequency -0.25、top_k 40、
    seed 4242、stop `END`、工具权限
    `get_time=deny`、`device_info=ask`。
  - copilot：备注“copilot 偏好 0480”、开场白“copilot 开场白 0480”、
    `reasoning_effort=low`、`tool_choice=none`、
    `context_length=64000`、frequency 0.1、工具权限 `get_time=auto`。

### 2.1 导出

- 多选两个 Agent 导出 1,881 B zip：
  `manifest.json`、`settings.json`、`copilots.json`、
  `sessions/qa_prefs_0480_s/session.json`。
- `copilots.json` 与 session 都含完整 `orionekoAgent`；session 的
  `pinned`、`first_mes`、数值与 `tools_policy` 和源 `agent.json` 一致。
- 对 zip 全文扫描 `api_key`、`baseUrl`、`base_url`、`headers`、
  `extra_body`、`Authorization`、`Bearer `、`sk-`、`ox1:`：全部 0 命中。
- Toast 正常显示“智能体 2、工作区 0、对话 1、消息 2、记忆 0、技能 0、
  文件 0”；说明行可见“助手偏好”。

### 2.2 重置后回导

- 用 root 把两个 Agent 的偏好重置为默认值（保留 id、名称、tgId、聊天），
  再从同一 zip 导入。
- 预览：“将恢复 2 个智能体的偏好设置（工具权限、置顶、备注、开场白与
  推理参数）”；结果：“新建助手 0，更新 1 / 已恢复 2 个智能体的偏好设置 /
  已存在的 1 段对话历史已替换”。
- 回读两个 `agent.json`：所有偏好字段与源值一致，包括 session 的
  `context_length=32000`、copilot 的 `context_length=64000`；tgId 仍为
  555000110/555000111，目录仍只有 2 个，没有 `kelivo_` 副本。
- 原生工具权限页截图确认当前 Agent 为 `QA Prefs Session 0480`，
  `get_time=禁止`、`device_info=询问`；Agent 列表顺序确认置顶 session
  排在首位，emoji 为 🧪。
- 再导入同一 zip：仍“已恢复 2 个智能体的偏好设置”、2 个 Agent 目录，
  字段不变，幂等。

### 2.3 QA 发现并修复

- 0.48.0 首次回导时 `context_length` 回到 0：导入代码先应用偏好，之后
  Provider 回退逻辑又用本机 Provider 的 `contextLength` 覆盖了它。
- 0.48.1 把 `applyAgentPrefs` 移到 Provider/模型/技能处理之后，再复验
  两个 Agent 的 32000/64000 均正确恢复；其余字段没有回归。

### 2.4 稳定性与清理

- 导出、回导、重复导入、工具权限页与 Agent 列表检查全程无本包
  FATAL/ANR（`logcat` 过滤 `FATAL EXCEPTION` / `ANR in
  io.github.blancatora.orionekox` 均为 0）。
- 通过 Agent 列表删除两个测试 Agent；`dialogs` / `messages_v2` 对
  555000110/555000111 均为 0；Download 测试 zip 已删除，未留下工作区
  目录。
- 未执行 `pm clear`，未删除其他 Agent 或用户数据，未给真实联系人发消息。
- 重新安装 release 4819 后恢复无障碍服务，留在 release 运行。

## 3. 未覆盖 / 已知边界

- `orionekoAgent` 是 OrionekoX 私有扩展；真实 Chatbox / Kelivo 会忽略，
  未做外部客户端互测。
- 不导出 `headers`、`extra_body`、Provider 地址和密钥，因此换机后认证
  头与额外请求体需要用户重新填写。
- 备注、开场白和 emoji 通过 `agent.json` 回读验证，未逐项截图聊天欢迎语；
  置顶顺序和工具权限已在原生 UI 验证。
- 偏好只覆盖已列出的字段；temperature/topP/maxTokens 仍走原有
  session/copilot 字段，不在 `orionekoAgent` 重复。

## 4. 产物

- `dist/OrionekoX-0.48.1-alpha-arm64-x64.apk`（versionCode 4819），sha256
  `7c454852b0a80955ff7560b57ea14acd74b5a2c9d62eacc33a8d5568af880d42`
- `dist/OrionekoX-0.48.1-debug-arm64-x64.apk`，sha256
  `f14282ef15194991f7e65db17aa67b6763fe612a4013a3e47631226dcce526f6`
- 截图：`docs/screenshots/qa-0.48.1-export-toast.png`、
  `qa-0.48.1-import-preview.png`、`qa-0.48.1-import-result.png`、
  `qa-0.48.1-tools-policy.png`、`qa-0.48.1-agent-list.png`
  （导出/预览/结果三张来自 0.48.0 功能构建，版本号显示 0.48.0；
  工具权限与 Agent 列表两张来自 0.48.1 最终构建）
- 构建日志：`/tmp/ox-build-0480.log`（`BUILD SUCCESSFUL in 2m 17s`）、
  `/tmp/ox-build-0481.log`（`BUILD SUCCESSFUL in 2m 20s`）
