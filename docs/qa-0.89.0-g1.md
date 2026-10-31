# QA — G1 缺陷收口：本地群消息在库、Telegram UI 群页为空（0.89.0）

日期：2026-11-01
设备：emulator-5554（AVD `orioneko_test`，1080x2400 @ 420dpi，x86_64）
包：`io.github.blancatora.orionekox.debug` 0.89.0-debug (versionCode 8909)
APK sha256：`d90efb7d2e2d9da0270a7713c4b17ba236728d759e3461b51f1300779cadba3c`
构建：`assembleAfatDebug`，BUILD SUCCESSFUL

G1 的后端修复（`224ab0b3` 到 `de141067` 共 5 笔）此前只做到 CP-3，CP-4 到 CP-7 全空。本次把四个检查点全部跑完，其中 CP-4 与 CP-5 各暴露一个真实缺陷并修掉。

---

## 1. 检查点总览

| 检查点 | 状态 | 关键证据 |
|---|---|---|
| CP-3 启动 + UI 可控 | 通过 | `LaunchActivity` 正常起，OxUI 面板可切换 |
| CP-4 既有本地群消息可见 | **通过**（修复后） | 群 `-1800000001`：`processLoadedMessages size 6`，6 条渲染，网络请求 0 |
| CP-5 空历史 `EMPTY_END` 终态 | **通过**（修复后） | 群 `-1800000002`：加载 5 次后停止，空群终态卡片，网络请求 0 |
| CP-6 对照 0.87 缺陷 4.1 | 通过 | 三项现象全部消失 |
| CP-7 回执与台账同步 | 通过 | 本文档 + `TODO.md` |

---

## 2. CP-4：既有本地群消息可见

### 2.1 现象

群 `-1800000001` 在库里有 6 条消息（mid 100012–100018），`ox_groups.xml` 里 `created_1800000001=true`，但打开群页：

```
processLoadedMessages size 0 in chat -1800000001 ...
```

连续 23 次以上，页面停在欢迎卡。SQL 手工验证：`max_id=0` 返回空，`max_id=100018` 返回 6 条。

### 2.2 根因（两层）

**第一层：`max_id=0` 的语义错配。**
`max_id=0` 在服务端语义是「从最新开始」，但本地 SQL 里 `m.mid <= 0` 是字面比较，一行都匹配不到。

**第二层：`getMessagesInternal` 自写的假 hole。**
该函数会写一条 `(0, minMid)` 的 hole 记录，后续查询按 `(mid >= holeEnd OR mid < 0)` 收窄。本地群没有服务端，这条 hole 永远是假的，会把所有真实消息藏住。

第一次尝试用 `Integer.MAX_VALUE` 归一化反而更糟——那个值让 hole 逻辑走了 `(mid >= holeEnd OR mid < 0)` 分支，把全部消息排除。

### 2.3 修法

在 storageQueue 任务里按顺序做三件事：

1. 删掉假 hole
2. 查最新 mid 当锚点
3. 跑原生查询

写屏障保留，原生查询一行没动。

另修一处重读守卫失效：`shouldReread` 存的是「正在重读的 loadIndex」，`clearReread` 在 finally 里清掉，但重试循环本身在递增 `loadIndex`（1→2→…→23），守卫永远放行。改成记录「已服务过的 (dialog, loadIndex)」，每个只重读一次，加 64 次/dialog 上限。因为 minSdk 23，避开 `Collection.removeIf`（API 24）。

### 2.4 验证

```
processLoadedMessages size 6 in chat -1800000001 ... last_message_id 100018 load_type 2
```

6 条消息全部渲染：`needle alpha probe`、`needle beta probe`、3 条 Sakura Waifu 回复。群标题 `Me, 🤖 Sakura Waifu and 🤖`、`3 members, 2 online` 正确。网络请求 **0 次**。

提交 `38d67f04`。

### 2.5 已知遗留

假 hole 在查询后又被原生逻辑写回来了（`0, 100012`）。不影响首屏显示，但翻页到更早消息时可能还会撞上。

---

## 3. CP-5：空历史 `EMPTY_END` 终态

### 3.1 现象

新建本地群 `-1800000002`，删掉它唯一的系统消息 100019 造成空历史。重新打开：

```
processLoadedMessages size 0 in chat -1800000002 ... index 1 ... 379
```

**378 次**，`loadIndex` 一路涨到 379，`messagesDidLoad` 从未发出，页面永远转圈。

### 3.2 根因（两个独立原因）

**原因一：终态空结束是死代码。**

它写在 `else if (oxLocalTarget && isCache && messagesRes.messages.isEmpty())` 里，链在 `if (!isEncrypted && isCache && runServerReload)` 之后。而本地目标 `runServerReload` 被 G1 修复强制设为 `false`，所以那个 `if` 恒假——但 `else if` 也因此永远轮不到执行。

**原因二：`ChatActivity` 把 `isEnd` 读进局部变量后从未使用。**

`boolean isEnd = (Boolean) args[9];` 之后没有任何地方读它。两条重试路径都无视它：

- `postponedScroll` 重试：`if (load_type == 0 && isCache && messArr.size() < count)` —— 空群恒真，每次都重发 `loadMessages`
- 列表底部滚动触发：`if (!endReached[0])` —— 空群 `endReached[0]` 恒 `false`，每次滚动都重发

### 3.3 修法

1. 把终态空结束的处理**提到** `runServerReload` 判断之前，让它可达
2. 给 `postponedScroll` 重试加 `!isEnd` 守卫
3. 在 `load_type == 0` 收到终态空结束时置位 `endReached[0]` / `cacheEndReached[0]`，并清 `loading`

### 3.4 验证

```
processLoadedMessages size 0 in chat -1800000002   → 5 次后停止
```

10 秒后复查仍是 5 次，没有继续增长（修复前是 290+ 且仍在涨）。页面显示空群终态卡片「You have created a group」，无 spinner。网络请求 **0 次**。

提交 `9366db8e`。

---

## 4. CP-6：对照 0.87 缺陷 4.1

0.87 的现象（`docs/qa-oxui-0.88.0-verification.md` §4.1）：`load unknown dialog → processLoadedMessages size 0 → 欢迎卡`。

| 0.87 现象 | 0.89 实际 | 结论 |
|---|---|---|
| `load unknown dialog -1800000001` | 0 次 | 已消除 |
| `processLoadedMessages size 0` | 首屏 `size 6`；另有 2 次 `size 0` 均为正常旁路查询（`count=1 load_type=2 mode=1` 定时消息、`count=50 load_type=0 mode=0` 滚动到底补查） | 已消除（主路径） |
| 停欢迎卡 | 6 条消息正常渲染 | 已消除 |
| `getPeerDialogs`（0.87 推测的过滤点） | 0 次 | 未触发 |

0.87 报告里写「未定位到确切过滤点」，猜测方向（`loadUnknownDialog` → `messages.getPeerDialogs` 永不返回）是对的，但真正的根因是 `max_id=0` 的本地 SQL 语义错配加上假 hole，见 §2.2。

---

## 5. 回归

CP-5 的改动碰了 `ChatActivity` 的加载路径，CP-4 必须复验：

```
processLoadedMessages size 6 in chat -1800000001 ... last_message_id 100018
```

仍是 6 条，网络请求 0 次。CP-4 未被破坏。

---

## 6. 未覆盖

- **写侧屏障**：`createLocalGroup` 的完成顺序没动，创建竞态窗口仍在，只靠读取侧重读兜住
- **`SendMessagesHelper` 发送侧分类治理**：未做
- **假 hole 复写**：见 §2.5

---

## 7. 提交

```
9366db8e  fix(ox): G1 CP-5 — empty local history now reaches a terminal state
38d67f04  fix(ox): G1 CP-4 — local group history now renders (max_id anchor + hole cleanup)
```

均已推送 `autoco-publish/main`。
