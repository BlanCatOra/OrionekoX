# 记忆库架构研究报告：Markdown 本体 + 关键词/向量双检索的可行性

- 日期：2026-10-29（同日修订：缺口由五项修正为七项）
- 对象：用户提出的“混合记忆库”方案（Markdown 原文为记忆本体，关键词检索与
  向量检索共同负责回忆；向量是更强的检索组件而非唯一记忆库）
- 方法：对照本仓库现有实现（OxAgentStore / OxMemoryStore / OxMemoryDistiller
  与对话注入链路）逐条评估，给出差距、映射与分阶段建议。
- 修订说明：初版只看了“检索骨架”，漏掉了**记忆本体不完整**与**外部 Harness
  读不到本地记忆**两条数据通路问题；本版补入并把结论改为七项缺口。

## 1. 方案摘要

- **记忆本体**：Markdown 原文，带结构化元数据（id/created_at/type/status/
  importance/people/topics/source/supersedes）。
- **检索组件**：精确关键词（日期、原句、名字、事件编号）+ 语义向量（相似
  感受/经历/隐含关联）+ 时间与标签过滤，融合重排后取回少量原文。
- **核心常读块**：CORE / ABOUT_USER / ABOUT_AGENT / CURRENT 每次会话固定
  注入（关系设定、称呼、稳定偏好、当前状态）。
- **版本化**：修改记忆创建新版本并用 `supersedes` 指向旧记录，保留“以前
  怎样、后来为何改变”。
- **逐步演进**：先可信本体与常读层 → 精确关键词与 Harness 桥接 → 数据量与
  基准成熟后接入真实向量；索引可重建，原文始终完整可读。

## 2. 现状盘点（本仓库，含证据）

### 2.1 存储：正文与索引并存，但正文不是完整本体

- `files/oxagents/<id>/memory.txt`：人类可读的常驻记忆（`OxAgentStore.
  appendMemory` 第 251 行，单行追加；`loadMemory` 第 268 行，截断注入）。
- `files/oxmemory.db` 的 `memories` 表（`OxMemoryStore` 第 74–101 行）：
  `kind/role/text/created_at/last_access/access_count/importance/stability/
  difficulty/memory_type/valid_from/invalid_at/subject/source/hash/vec`，
  唯一索引 `(agent_id, hash)` 去重。
- 写入：`OxMemoryDistiller` 异步蒸馏（摘要 + 原子事实）、`rememberFact`、
  `ingestModelMemory`、`ingestHarnessMirror`；`consolidateFact` +
  `invalidateFact`（按 subject 替换旧事实并置 invalid_at）已具备“事实失效”
  语义。
- **关键事实**：只有 `remember` 工具会同时写 SQLite 与 `memory.txt`
  （`OxBuiltinTools` 第 442 行附近的 `rememberFact` + `appendMemory`）。
  上下文压缩、模型蒸馏、Harness 镜像走 `OxMemoryStore.insert`（第 615 行），
  **只写 SQLite，不更新 MEMORY.md/memory.txt**。因此当前不是“Markdown 为
  事实来源、SQLite 为索引”，而是：

  ```text
  memory.txt：人工可编辑的常驻记忆（单行追加）
  SQLite：压缩、蒸馏、事实、对话与检索记录（多数内容不在正文里）
  ```

  这直接影响可审阅性、导出、跨 Harness 迁移与“重建索引”能力。

### 2.2 检索：已实现混合打分的雏形

`OxMemoryStore.recall(agentId, query, limit, mode)`（第 245 行起）：

- BM25 关键词打分（`bm25()` 第 358 行，停用词表第 438 行）；
- 可选“向量”余弦（`embed()` 第 1176 行）；
- 遗忘曲线 retention（stability）、importance、access 的加权；
- MMR 去冗余（同主题闲聊不会挤掉需要的那条事实）；
- 时间性查询（`isTemporalQuery`）时才召回已失效事实并标注“历史”；
- 输出带日期/类型/相关度标签，`touch()` 更新访问次数。

每次对话注入：`groupMessages`/1:1 的 system 里调用 `appendMemoryContext`
（`OxAgentsBridge` 第 2540 行起；记忆条数由 `agent.memoryRecall` 控制，
默认 6；压缩阈值 `memoryThreshold=45`）。

### 2.3 关键事实：现有“向量”不是语义向量

`embed()`（第 1176 行）是对 token 做 FNV 哈希后投影到 128 维的**词袋随机
投影**，本质仍是词面相似度，不具备“意思相近但无共同关键词”的语义能力。
但 UI 在选择 `MODE_VECTOR` 后明确显示“向量模式”
（`OxAgentsBridge` 第 2590 行附近），与硬约束“不要用哈希向量冒充语义
向量”冲突。这个名称必须先修正（改为“词面混合”或暂时隐藏）。

### 2.4 关键事实：外部 Harness 当前拿不到本地记忆

`appendMemoryContext` 在非 Local API 路径中直接返回：

```java
if (!OxHarnessAdapter.isLocalApi(agent)) {
    OxHarnessAdapter.maybeCompact(agent, history, threshold);
    system.append("# 外部 Harness\n上下文与 compact 由「…」管理，本地不注入压缩摘要或记忆库。");
    return defaultFrom;
}
```

（`OxAgentsBridge` 第 2546 行附近。）这意味着：

- 本地 API 智能体能使用 `OxMemoryStore`；
- Codex 等外部 Harness 产生的内容可以被 `ingestHarnessMirror` 镜像进数据库
  （第 3325 行附近，默认关闭）；
- 但**这些镜像记忆不会自动回流给下一次 Codex 会话**，Harness 也读不到
  CORE、当前状态或历史召回。

所以还需要一个 **Harness Memory Bridge**：在启动 Harness 会话时只提供
“固定 CORE + 当前状态 + 针对当前消息的少量召回 + recall/memory_verify
入口”，不与 Harness 自己的上下文压缩争夺所有权。

## 3. 与提案的逐条对照

| 提案要素 | 现状 | 差距 |
|---|---|---|
| Markdown 原文为唯一记忆本体 | memory.txt 只是人工常驻记忆；压缩/蒸馏/镜像只进 SQLite | **本体不完整**：多数记忆无法从 Markdown 审阅或重建；双写无一致性约束 |
| 稳定 ID + created_at/type/status/importance/people/topics/source | SQLite 有 id/created_at/kind/memory_type/importance/valid_from/invalid_at/subject/source | **缺 people/topics/tags 与显式 status**；id 为自增行号，跨导出/重建不稳定 |
| supersedes 版本链 | subject + invalid_at 近似“事实替换”，但旧文本只存于内存日志，不可审阅 | **无显式 supersedes 字段与版本链**，无法回答“后来为何改变” |
| CORE / ABOUT_USER / ABOUT_AGENT / CURRENT | 有 agentsMd（人设）+ memory.txt 截断注入 + digest | **无显式分层常读块**（稳定偏好/称呼/规则 vs 可检索历史混在一起） |
| 关键词检索 | BM25 已实现 | 中文分词质量待评估（现按字符/空白切分）；日期/事件编号/ID/引号原句未特化 |
| 语义向量检索 | 哈希投影，非语义 | **需真实 embedding provider**；无 endpoint 时应显式回落 normal，而不是伪装 |
| 融合重排 | lexical/vector/retention/importance/access 加权 + MMR | 权重经验值未校准；缺 recall@k/MRR 基准 |
| 时间与标签过滤 | valid_from/invalid_at + 时间性查询 | **无 people/topics 过滤**（提案的标签维度缺失） |
| 取回 5–10 条原文 | memoryRecall 默认 6，clip 600 字符 | 已满足；需要保留 public_id 以便回答可溯源 |
| 跨 Harness 可达 | Harness 路径跳过本地记忆注入 | **无 Harness Memory Bridge**；镜像记忆不回流，Codex 新会话读不到 |

**结论：方案可行，且与本仓库架构高度兼容——不是推倒重来，而是补齐七个
缺口：**

1. **记忆本体不完整**：Markdown 不是 SQLite 的完整来源，压缩/蒸馏/镜像
   只写库；必须先定主从关系并支持从 Markdown 重建。
2. **结构化元数据不完整**：稳定 public_id、people、topics、status。
3. **缺显式 supersedes 版本链**。
4. **缺 CORE / ABOUT_USER / ABOUT_AGENT / CURRENT 常读分层**。
5. **外部 Harness 读不到本地记忆**：需要 Harness Memory Bridge，只桥接
   记忆、不抢 compact 所有权。
6. **关键词检索未针对中文与精确事实特化**（unigram+bigram、日期、ID、
   引号原句、姓名）。
7. **真实 embedding 缺位**，且 `MODE_VECTOR` 的名称在误导用户；同时缺
   可重复的检索基准。

## 4. 建议架构（映射到本仓库）

```
【写入】
  记忆事件（remember / 压缩 / 蒸馏 / Harness 镜像）
        │
        ▼
  Markdown 记忆本体（唯一事实来源，逐条 frontmatter）
        │  原子写入 + 内容哈希
        ▼
  SQLite 派生索引（public_id 关联；可全量重建）
        │
        ├─ BM25 / 精确检索索引
        ├─ people/topics/status 过滤
        └─ embedding 表（public_id/content_hash/provider/model/dimension/version/vector）

【读取】
  每次会话固定注入
     CORE + ABOUT_USER + ABOUT_AGENT + CURRENT
          │
  用户当前话
     ├─ 精确通道：日期、记忆 ID、引号原句、姓名
     ├─ 关键词：中文 unigram+bigram / 英文词
     ├─ 语义：真实 embedding（provider 可用时）
     └─ 过滤：status=active（时间性查询才含 superseded）
          │
     融合重排（现有权重 + MMR，冻结基准校准）
          │
     取回 5–10 条原文（带 public_id，回答可溯源）

【外部 Harness】
  Harness Memory Bridge：
     CORE + 当前状态 + 针对当前消息的少量召回 + recall/memory_verify 入口
     （只提供记忆；compact/上下文窗口仍由 Harness 自己负责）
```

- **单一事实来源**：Markdown 为主，SQLite 为派生索引；所有写入先落
  Markdown（临时文件 + fsync + rename 原子替换），再增量更新索引；索引
  损坏时可从 Markdown 全量重建。
- **迁移**：现有 SQLite 行（含压缩/蒸馏/镜像）导出为 Markdown 记录并打上
  `source=migrated`，保留原 hash 防重复；`memory.txt` 旧行迁入 CORE/
  ABOUT_USER 或普通记录。
- **版本化**：每次改写写新记录，`status=superseded` + `supersedes=<旧
  public_id>`；检索默认只看 active，时间性问题可回看旧版本。
- **embedding 独立表**：保存 `public_id/content_hash/provider/model/
  dimension/embedding_version/vector`；内容、模型或维度变化时把对应行标记
  待重建；provider 不可用时回落 `normal`，不写伪向量。
- **Harness Memory Bridge**：只在 Harness 回合开始注入上述固定块与少量
  召回；Harness 结束后的镜像按同一 Markdown 本体落盘，下一次 Codex 会话
  即可读到。

## 5. 分阶段路线（建议）

> 版本号说明：报告初版的 0.75/0.76/0.77 已被 OxUI 轮次占用（仓库当前
> 0.77.0）。以下保留原阶段意图，映射到下一个可用版本；实施时以仓库实际
> 版本为准。

1. **阶段 A（原报告 0.75，建议 app 0.78）——可信记忆本体**
   - Schema v3：`public_id/people/topics/status/supersedes/source/
     content_hash`。
   - 明确 Markdown 唯一事实来源；压缩、蒸馏、镜像全部改走“先 Markdown
     后索引”。
   - 建立独立 CORE / ABOUT_USER / ABOUT_AGENT / CURRENT 段落。
   - 迁移现有 `memory.txt` 与 SQLite 记录；原子写入与失败回滚。
   - SQLite 可从 Markdown 完整重建（删除库后重建，检索结果一致）。
   - 冻结当前算法基准（见阶段 B 指标），供后续对比。
   - **立即把 UI 的“向量模式”改为“词面混合”或暂时隐藏。**
2. **阶段 B（原报告 0.76，建议 app 0.79）——精确召回与 Harness 桥接**
   - 中文 unigram + bigram、英文单词分词；日期、记忆 ID、引号原句、姓名
     的精确检索通道；`people/topics/status` 过滤。
   - Harness Memory Bridge：新会话固定读取 CORE，并按当前消息召回历史；
     提供 `recall`/`memory_verify` 访问入口。
   - 指标：Recall@6、MRR、精确事实命中率、已失效事实泄漏率、
     `supersedes` 版本选择正确率、空结果时的虚假记忆率、召回延迟、
     注入 token 数。
3. **阶段 C（原报告 0.77，建议 app 0.80）——真实语义向量**
   - 配置真实 embedding provider；独立 embedding 表与重建标记。
   - Provider 不可用回落 `normal`。
   - 同一份冻结基准比较：BM25 / 精确+BM25 / 真实向量 / 关键词+向量融合；
     只有融合方案稳定胜出才设为默认。
4. **后续**：记忆冲突可视化、“这份回答依据了哪几条记忆”的溯源 UI、
   Markdown 导入导出与跨设备迁移。

## 6. 风险与验证

- **本体迁移风险**：现有 SQLite 中大量压缩/蒸馏/镜像记录没有 Markdown
  正文；迁移必须一次性导出并校验条数/哈希，否则会“升级后丢记忆”。
- **双写一致性**：Markdown 为准、SQLite 可重建；写入顺序先 Markdown 后
  索引，索引失败可从 Markdown 修复；避免两个方向都当权威。
- **Harness 所有权**：Memory Bridge 只注入记忆，不接管 compact；否则会和
  Codex 自己的上下文管理互相打架。
- **中文分词**：现 tokenize 以字/空白为主，BM25 对中文效果有限；需引入
  bigram 或轻量词典（本地实现，无网络依赖）。
- **embedding 可用性**：agent-only 离线场景无 endpoint；必须诚实回落，
  否则“语义检索”是假的。托管向量库需要上传文件走 API，不会自动接入本地
  会话——这点用户的理解正确。
- **向量表拆分**：把 `vec` 从主表拆到独立表，记录 provider/model/
  dimension/version；否则换模型或维度后新旧向量混杂，无法判断重建范围。
- **token 预算**：固定 CORE + 5–10 条原文（每条 ≤600 字符）需设上限；
  现有 digest/压缩机制可复用。
- **隐私**：向量化只能走本地或用户自配 endpoint；默认不上传。

## 7. 结论

提案方向正确且与现有实现同构：我们已经有 SQLite 结构化记忆 + BM25/遗忘/
MMR 的混合检索雏形，并有 subject+invalid_at 的事实替换机制。真正缺的是
**完整可审阅的 Markdown 本体、稳定版本链、CORE 常读层、Harness 可达性、
精确检索与基准、以及最后才接入的真实 embedding**。

优先级应当是：

```text
可审阅的记忆本体
→ 稳定版本链
→ CORE 常读层
→ Codex/Harness 真正能够读取
→ 可重复的检索基准
→ 真实 embedding
```

这条路线能保住现有成果，也能避免做出一个“向量检索看起来很先进，但
Codex 新会话根本读不到”的系统。任何阶段都不使用哈希向量冒充语义检索。
