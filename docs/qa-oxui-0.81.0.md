# QA — 0.81.0（OxUI 8.1：会话列表管理）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。
> 日期：2026-10-29 · 分支：`orioneko/0.80-memory-stage-c` · 基线：`780701b`（0.80 阶段 C）

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 8.1 置顶 | 已有能力（`agent.pinned` + 长按菜单 Pin/Unpin + 行尾图钉图标） | 沿用/确认 |
| 8.1 归档 | 新增 `agent.archived`（`agent.json` 持久化，缺省 false）；长按菜单「归档/取消归档」；主列表隐藏，底部「Archived (N)」可点开展开 | 完成 |
| 8.1 重命名 | 长按菜单「重命名」→ 输入框（空名拒绝并提示），保存后 `syncAgents` 同步合成会话名，列表刷新 | 完成 |
| 排序 | `listAgents` 排序改为：未归档优先 → 置顶优先 → 内置优先 → 名称字典序 | 完成 |

## 行为说明

- 归档不删除任何数据：会话、记忆、配置都保留，只是从主列表折叠到底部归档区。
- 归档区标题显示数量，点击切换展开/收起（`showArchived` 状态，页面存活期内保留）。
- 取消归档把 agent 移回主列表，并按其 `pinned` 状态决定是否回到置顶段。
- 重命名只改 `name`（显示名 + 合成会话用户名镜像来源），不改 `id`，因此记忆库、工作区文件路径都不受影响。
- 兼容：旧 `agent.json` 无 `archived` 字段时按 false 处理，不会误归档。

## 构建

- `taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug`
- **BUILD SUCCESSFUL in 3m 44s**（含 `compileAfatDebugJavaWithJavac` 无错）。

## 诚实缺口

1. **未做模拟器截图/交互取证**：本轮只验证编译与构建；「点开归档区、长按重命名、归档后回到主列表」的实际 UI 行为需在 `emulator-5554` 上手动或脚本取证。归档/重命名是纯本地状态 + 列表重绘，逻辑简单，但按诚信原则不谎称已端到端验证。
2. **重命名后的会话名同步**依赖 `OxAgentsBridge.syncAgents(false)`，需在本地账号模式下确认合成用户 last name 真的更新（属 8.3「群组反向同步」相邻能力，下轮可一并验）。
3. 归档区目前是单一折叠层，未做「归档分组/文件夹」，若 agent 数量大，后续可考虑分组（非 8.1 范围）。
