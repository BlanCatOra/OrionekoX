# 记忆库架构研究报告：Markdown 本体 + 关键词/向量双检索的可行性

- 日期：2026-10-29
- 对象：用户提出的"混合记忆库"方案（Markdown 原文为记忆本体，关键词检索与
  向量检索共同负责回忆；向量是更强的检索组件而非唯一记忆库）
- 方法：对照本仓库现有实现（OxAgentStore / OxMemoryStore / OxMemoryDistiller
  与对话注入链路）逐条评估，给出差距、映射与分阶段建议。

## 1. 方案摘要

- **记忆本体**：Markdown 原文，带结构化元数据（id/created_at/type/status/
  importance/people/topics/source/supersedes）。
- **检索组件**：精确关键词（日期、原句、名字、事件编号）+ 语义向量（相似
  感受/经历/隐含关联）+ 时间与标签过滤，融合重排后取回少量原文。
- **核心常读块**：CORE / ABOUT_YOU / ABOUT_ME 每次会话固定注入（关系设定、
  称呼、稳定偏好、规则）。
- **版本化**：修改记忆创建新版本并用 `supersedes` 指向旧记录，保留"以前
  怎样、后来为何改变"。
- **逐步演进**：先 Markdown+元数据+索引 → 关键词检索 → 数据量上来后加向量
  索引；索引可重建，原文始终完整可读。

## 2. 现状盘点（本仓库，含证据）

### 2.1 存储：已经是"正文 + 结构化索引"双轨

- `files/oxagents/<id>/memory.txt`：人类可读的长期记忆正文（`OxAgentStore`
  第 211、231 行），对话时以 `loadMemory(agent.id, 4000)` 注入。
- `files/oxmemory.db` 的 `memories` 表（`OxMemoryStore` 第 74–101 行）：
  `kind/role/text/created_at/last_access/access_count/importance/stability/
  difficulty/memory_type/valid_from/invalid_at/subject/source/hash/vec`，
  唯一索引 `(agent_id, hash)` 去重。
- 写入：`OxMemoryDistiller` 异步蒸馏（摘要 + 原子事实）、`rememberFact`、
  `ingestModelMemory`、`ingestHarnessMirror`；`consolidateFact` +
  `invalidateFact`（按 subject 替换旧事实并置 invalid_at）已具备"事实失效"
  语义。

### 2.2 检索：已实现混合打分的雏形

`OxMemoryStore.recall(agentId, query, limit, mode)`（第 245 行起）：

- BM25 关键词打分（`bm25()` 第 358 行，停用词表第 438 行）；
- 可选"向量"余弦（`embed()` 第 1176 行）；
- 遗忘曲线 retention（stability）、importance、access 的加权；
- MMR 去冗余（同主题闲聊不会挤掉需要的那条事实）；
- 时间性查询（`isTemporalQuery`）时才召回已失效事实并标注"历史"；
- 输出带日期/类型/相关度标签，`touch()` 更新访问次数。

每次对话注入：`groupMessages`/1:1 的 system 里调用 `appendMemoryContext`
（记忆条数由 `agent.memoryRecall` 控制，默认 6；压缩阈值
`memoryThreshold=45`）。

### 2.3 关键事实：现有"向量"不是语义向量

`embed()`（第 1176 行）是对 token 做 FNV 哈希后投影到 128 维的**词袋随机
投影**，本质仍是词面相似度，不具备"意思相近但无共同关键词"的语义能力。
当前 `MODE_VECTOR` 名不副实，与硬约束"不要用哈希向量冒充语义向量"冲突；
接入真实 embedding 前不应把 vector 模式当作语义检索宣传。

## 3. 与提案的逐条对照

| 提案要素 | 现状 | 差距 |
|---|---|---|
| Markdown 原文为记忆本体 | memory.txt 已是可读正文；SQLite 行是结构化副本 | 正文与索引双写无一致性约束；memory.txt 是蒸馏追加，非逐条带元数据 |
| 稳定 ID + created_at/type/status/importance/people/topics/source | SQLite 有 id/created_at/kind/memory_type/importance/valid_from/invalid_at/subject/source | **缺 people/topics/tags 与显式 status**；id 为自增行号，跨导出/重建不稳定 |
| supersedes 版本链 | subject + invalid_at 近似"事实替换"，但旧文本只存于内存日志，不可审阅 | **无显式 supersedes 字段与版本链**，无法回答"后来为何改变" |
| CORE / ABOUT_YOU 常读块 | 有 agentsMd（人设）+ memory.txt 截断注入 + digest | **无显式分层常读块**（稳定偏好/称呼/规则 vs 可检索历史混在一起） |
| 关键词检索 | BM25 已实现 | 中文分词质量待评估（现按字符/空白切分）；日期/事件编号这类精确检索未特化 |
| 语义向量检索 | 哈希投影，非语义 | **需真实 embedding provider**；无 endpoint 时应显式回落 normal，而不是伪装 |
| 融合重排 | lexical/vector/retention/importance/access 加权 + MMR | 权重经验值未校准；缺 recall@k/MRR 基准 |
| 时间与标签过滤 | valid_from/invalid_at + 时间性查询 | **无 topics/people 过滤**（提案的标签维度缺失） |
| 取回 5–10 条原文 | memoryRecall 默认 6，clip 600 字符 | 已满足；需要保留记忆 id 以便回答可溯源 |

**结论：方案可行，且与本仓库架构高度兼容——不是推倒重来，而是补齐五个
缺口：①稳定 ID 与 people/topics/status 元数据；②显式 supersedes 版本链；
③常读 CORE 分层；④真实 embedding provider + 诚实回落；⑤recall@k/MRR
基准。**

## 4. 建议架构（映射到本仓库）

```
每次会话固定注入
   CORE（称呼/关系设定/稳定偏好/规则） ← agentsMd 或 memory.txt 的 core 段
        │
用户当前话
   ├─ 关键词：BM25（中文分词改进）+ 日期/编号原句直查
   ├─ 语义：真实 embedding（provider 可用时）→ 余弦
   └─ 过滤：status=active（时间性查询才含 superseded）
        │
   融合重排（现有权重 + MMR，基准校准）
        │
   取回 5–10 条原文（带 memory id，回答可溯源）
```

- **存储**：memory.txt 作为可读本体（每条带 YAML frontmatter），SQLite 作为
  索引/缓存，可由 md 全量重建（当前已有 hash 去重，可加 `source_md_line`）。
- **语义向量**：检测 agent/provider 是否配置 OpenAI-compatible embeddings
  endpoint；有则建索引（可删除重建），无则 normal 模式并在 UI 明确提示；
  哈希投影降级为"词面近似"内部用途，不再当作语义检索对用户宣传。
- **版本化**：每次改写写新行，`status=superseded` + `supersedes=<旧 id>`；
  检索默认只看 active，时间性问题可回看旧版本。

## 5. 分阶段路线（建议）

1. **0.75（小步、立即收益）**：memories 增 `public_id/people/topics/status/
   supersedes`；memory.txt 逐条 frontmatter；CORE 分层常读；检索输出带
   public_id；不改检索算法。可验证：导出与重建索引后检索结果一致。
2. **0.76**：中文分词与精确检索特化（日期/引号原句/事件编号直查优先）；
   构造 50–100 条记忆 × 30 查询的本地基准，测 recall@6 与 MRR
   （normal 模式）。
3. **0.77**：embedding provider 检测 + 真实向量索引（可重建）；同一基准
   对比 normal vs 混合；无 provider 时明确回落，不做哈希冒充。
4. **后续**：topics/people 过滤、记忆冲突可视化、"这份回答依据了哪几条
   记忆"的溯源 UI。

## 6. 风险与验证

- **中文分词**：现 tokenize 以字/空白为主，BM25 对中文效果有限；需引入
  bigram 或轻量词典（本地实现，无网络依赖）。
- **embedding 可用性**：agent-only 离线场景无 endpoint；必须诚实回落，
  否则"语义检索"是假的。托管向量库需要上传文件走 API，不会自动接入本地
  会话——这点用户的理解正确。
- **双写一致性**：md 为准、SQLite 可重建；写入顺序先 md 后索引，失败可
  从 md 修复。
- **token 预算**：固定 CORE + 5–10 条原文（每条 ≤600 字符）需设上限；
  现有 digest/压缩机制可复用。
- **隐私**：向量化只能走本地或用户自配 endpoint；默认不上传。

## 7. 结论

提案方向正确且与现有实现同构：我们已经有 Markdown 正文 + SQLite 结构化
记忆 + BM25/遗忘/MMR 的混合检索雏形，并有 subject+invalid_at 的事实替换
机制。真正缺的是**结构化元数据完整性、显式版本链、常读核心层、真实
embedding 与可度量基准**。建议按 0.75→0.77 分阶段落地，先补数据模型与
可读性，再谈向量；任何阶段都不使用哈希向量冒充语义检索。
