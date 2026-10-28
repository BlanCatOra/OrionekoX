# QA — alpha 0.47.1（Chatbox 备份携带 Agent 工作区）

- 版本：`0.47.1`，versionCode **4719**（`0.46.0` 为 4609）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 14:08–14:44（CST）
- 目标：让 Chatbox / Kelivo 备份携带每个 Agent 的工作区文件，并在回导时
  安全恢复；补上 0.47.0 导出完成 Toast 截断工作区计数的问题。
- 版本规则：新增用户可见的备份内容（工作区文件）与恢复行为，按 minor
  从 0.46.0 → **0.47.0**（4709）；随后只修 Toast 文案与中文包，按 patch
  从 0.47.0 → **0.47.1**（4719）。0.47.1 构建两次：首版通过功能 QA 后，
  又发现导出会对所有选中 Agent 调用 `workspaceDir()` 创建空目录，并把
  无聊天/无人设 Agent 的工作区写进 zip 却无法回导；修正为只收集实际
  写入备份的 Agent 且使用只读查找，重建后复验。没有直接覆盖已构建的
  0.47.0 产物。

## 1. 实现

### 1.1 导出

- 对本次实际写入备份的 Agent 遍历 `files/oxworkspace/<id>/`，写入
  `workspace/<agentId>/<相对路径>`；嵌套目录、二进制文件与中文文件名
  都按原字节写入。只读查找目录（`workspaceDirIfExists`），导出本身
  不会创建空工作区目录；无聊天且无人设的 Agent 不参与导出，也不收集
  其工作区，避免“备份里有文件但没有可恢复的 Agent”。
- 每个文件先做 canonical path 校验，必须仍在工作区根目录下；含 `..`
  的路径跳过。上限：最多 200 个文件、单个 2 MiB、合计 8 MiB、目录深度 6；
  空文件与超限文件跳过，超出总量后停止收集。
- 工作区文件数与技能、记忆、附件一样进入导出结果统计；全量、选中和
  单 Agent 导出共用同一套收集逻辑。

### 1.2 导入

- 预览阶段统计 `workspace/` 条目，显示
  “将恢复 N 个工作区文件（覆盖同名文件）”。
- 导入阶段只对备份中已存在、且本机仍有 `agent.json` 的 Agent 恢复；
  逐个写入，同名文件覆盖，其他文件保留。
- 路径校验：Agent id 只允许 `[a-zA-Z0-9._-]+`；相对路径拒绝绝对路径、
  反斜杠、NUL、`.`/`..` 段、空段、超过 8 层或 180 字符；单文件上限
  2 MiB、总数 200、合计 8 MiB。
- 结果新增“恢复工作区文件 N 个”。

### 1.3 文案与 Toast

- 导出说明、确认框、全量/选中/单 Agent 完成文案都加入“工作区”。
- 0.47.0 设备 QA 发现全量导出 Toast 过长，系统只显示两行并在
  “0 个文件，…”处截断，工作区计数完全看不到。0.47.1 将文案压缩为
  “已导出：智能体 N、工作区 N、对话 N、消息 N、记忆 N、技能 N、文件 N”，
  并把工作区放在第二位；单 Agent 文案同样把工作区前置。
- 中文包 106 → 107，避免旧的 `files/ox_zh-cn.xml` 缓存继续显示长文案。

## 2. 设备验证 — PASS

测试对象（自建，未使用真实联系人）：

- `QA Workspace Session 0470`，session 路径，tgId 555000102，2 条消息。
- `QA Workspace Copilot 0470`，chatless copilot 路径，tgId 555000101。
- 工作区文件：

| 文件 | 大小 | sha256（前 16 位） |
|---|---:|---|
| `qa_ws_0470_s/notes.txt` | 20 B | `acb1b60a4914e166` |
| `qa_ws_0470_s/sub/data.bin` | 16 B | `be45cb2605bf36be` |
| `qa_ws_0470_s/sub/deeper/中文.txt` | 33 B | `0f08de6c2caf9703` |
| `qa_ws_0470_c/hello.md` | 23 B | `5285eb1614368fc8` |

### 2.1 导出

- 选中两个 Agent 导出 2,215 B zip，包含 4 个工作区条目，路径与大小
  完全匹配；`notes.txt`、`data.bin`、`中文.txt`、`hello.md` 与源文件
  逐字节相等。
- zip 内没有 `apiKey` / `api_key` / `baseUrl` / `base_url` /
  `Authorization` / `sk-` / `Bearer ` / `ox1:` 命中。
- 0.47.1 再导出（工作区新增 `keep.txt`）为 2,414 B、5 个工作区条目；
  Toast 完整显示“智能体 2、工作区 5、对话 1、消息 2、记忆 0、技能 0、
  文件 0”，不再截断工作区计数。
- 单 Agent 导出 2,055 B、4 个工作区条目，只含该 Agent 的 `qa_ws_0470_s`
  文件；Toast 为“工作区 4、技能 0、记忆 0、消息 2、文…”，工作区计数
  在截断前可见。
- 最终重建（只读查找 + 只收集实际导出的 Agent）复验：勾选有工作区的
  `酒吧炒饭A` 和无工作区的 `PNG Card` 一起导出，zip 14,893 B，只有
  `workspace/dup1a6046/bar-fried-rice.md`（2,252 B）与
  `workspace/dup1a6046/inbox/…jpg`（13,308 B）两条，逐字节与源文件
  一致；`PNG Card` 的 `oxworkspace/cardc1fc5b` 导出前后都不存在，
  没有被导出过程创建。Toast 为“智能体 2、工作区 2、对话 1、消息 13、
  记忆 1、技能 1、文件 1”。

### 2.2 删除后回导

- 删除 3 个原始文件，保留新增的 `keep.txt`，再导入 2,215 B zip。
- 预览：“将恢复 4 个工作区文件（覆盖同名文件）”；结果：
  “恢复工作区文件 4 个”。
- 4 个文件全部恢复，sha256 与源文件一致；`keep.txt` 内容
  `keep me 0470\n` 未被覆盖或删除（同名覆盖、其他文件保留）。
- 两个 Agent 仍是原 id，tgId 仍为 555000102/555000101，没有新增
  `kelivo_...` 副本；对话更新 1 段，消息没有重复。
- 再导入同一 zip：结果仍为“恢复工作区文件 4 个”、2 个 Agent 目录、
  5 个工作区文件（4 个恢复 + `keep.txt`），幂等。

### 2.3 安全边界

- 构造含 `workspace/qa_ws_0470_s/../../evil0470.txt` 的 zip：导入直接
  失败并提示 `Invalid zip entry path: workspace/qa_ws_0470_s/../../evil0470.txt`，
  没有写入任何文件；确认没有生成 `files/evil0470.txt`。
- 构造含 2 MiB + 1 B `workspace/qa_ws_0470_s/big.bin` 的 zip：预览显示
  5 个（不是 6 个）工作区文件，导入结果“恢复工作区文件 5 个”，
  `big.bin` 没有被创建，其余 5 个正常恢复。
- 导出端与导入端使用同一组上限；本轮实测了导入端的超大文件跳过，
  导出端上限按同一常量实现（未单独造 2 MiB 源文件验证）。

### 2.4 稳定性与清理

- 导出、回导、重复导入、恶意 zip、超大文件 zip 以及最终重建后的
  无工作区 Agent 导出，全程没有本包 FATAL/ANR（`logcat` 过滤
  `FATAL EXCEPTION` / `ANR in io.github.blancatora.orionekox` 均为 0）。
- 通过 Agent 列表删除两个测试 Agent；`dialogs` / `messages_v2` 对
  555000101/555000102 均为 0。
- 删除 Agent 只清 `oxagents/<id>`，不会递归删除 `oxworkspace/<id>`；
  本轮测试目录已用 root 手工删除，Download 下的 6 个测试 zip 已清理。
  这是有意的保守行为，避免删除 Agent 时误删用户工作区文件，已记录为
  边界，待产品决策是否在删除对话框中提供“同时删除工作区”。
- 删除 Agent 后联系人页在本次进程内短暂残留该本地 Agent，重启后消失；
  未改代码，记录为缓存刷新边界。
- 未执行 `pm clear`，未删除其他 Agent 或用户数据，未给真实联系人发消息。
- 重新安装 release 4719 后恢复无障碍服务，留在 release 运行。

## 3. 未覆盖 / 已知边界

- `workspace/` 是 OrionekoX 对 Chatbox 备份的扩展；真实 Chatbox / Kelivo
  客户端是否忽略或保留未实测。
- 只携带普通文件内容，不保留空目录、文件权限、时间戳或符号链接；
  空文件被跳过。
- 只恢复备份中已存在且本机仍存在的 Agent；不会为孤立工作区新建 Agent。
- 导出端单文件/总量上限只做了代码审查，未造大文件做设备实测。
- 删除 Agent 不自动删除工作区；联系人页缓存需重启刷新。

## 4. 产物

- `dist/OrionekoX-0.47.1-alpha-arm64-x64.apk`（versionCode 4719），sha256
  `b5fb5ac1936aab80c0601a1c60d1802f6749ea26f20c664681e14d91ed23cea6`
- `dist/OrionekoX-0.47.1-debug-arm64-x64.apk`，sha256
  `f66b459a7d5497a97c85741e5f91eba473daa80c54ee6015177df949413ad2d8`
- 截图：`docs/screenshots/qa-0.47.1-export-toast.png`、
  `qa-0.47.1-agent-toast.png`、`qa-0.47.1-export-final.png`、
  `qa-0.47.1-import-preview.png`、`qa-0.47.1-import-result.png`
- 构建日志：`/tmp/ox-build-0471.log`（首版，`BUILD SUCCESSFUL in 2m 18s`）、
  `/tmp/ox-build-0471-final.log`（最终版，`BUILD SUCCESSFUL in 2m 7s`）。
