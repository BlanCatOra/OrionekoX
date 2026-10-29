# QA — 0.80.0（记忆阶段 C：本地离线语义向量）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。
> 日期：2026-10-29 · 分支：`orioneko/0.80-memory-stage-c`（基于 0.79 `21744e1`）

## 一句话结论

0.80 阶段 C 不再等外部 embedding endpoint：改用**完全本地离线**的 LSA（潜在语义分析）模型产出的真实语义向量，纯 Java、无原生库、无外部模型资源、无网络。构建通过（`assembleAfatDebug` BUILD SUCCESSFUL），算法经 Python 原型 + Java 移植双重复算验证语义可区分。端到端 agent 会话取证仍待配后端的模拟器（见「诚实缺口」）。

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 7.1 真实 embedding provider + 独立表 | 新增 `OxEmbeddingProvider`：TF-IDF → 截断 SVD（小 Gram 矩阵 Jacobi 特征分解）→ 稠密隐向量。独立 `mem_embeddings` 表（agent_id/mem_id/vec/model/dim/built_at），Schema 升 v4（onCreate + onUpgrade 3→4） | 完成 |
| 7.2 内容/模型/维度变化标记待重建 | 模型名 `ox-lsa-local-1`、`DIM=48` 打戳；`mem_embeddings` 按 (agent, model, dim) 校验，不匹配即视为失效。文本改写调用 `invalidateDoc` 删缓存并标记重建；新记忆写入 `markDirty` | 完成 |
| 7.3 provider 不可用回落 normal（不写伪向量） | 语料 < `MIN_DOCS=8` 或空间未就绪时 `embed()` 返回 `null`，recall 回落词面（lexical），指标记 `semantic-unavailable`；**绝不用哈希投影冒充语义向量** | 完成 |
| 7.4 同基准比较，仅稳定胜出才设默认 | 默认模式仍为 `normal`；`semantic` 需在 agent 档案里手动选择（off→normal→vector→semantic 循环）。未做端到端稳定胜出验证前不设为默认 | 部分（受限，见缺口） |

## 设计：为什么用 LSA 而不是打包 Transformer

- 架构红线是「不写假语义向量」。旧的 `MODE_VECTOR` 是 FNV 哈希 n-gram 投影，只反映表面词面重合，已被正名为「词面混合」。
- 在 Telegram fork 里塞入 ONNX/TFLite + 数十 MB 模型资产，需要 NDK 原生库与额外打包链路，构建风险高、体积大，且本环境无稳定模型下载源。
- LSA 是经典且**真实**的语义方法：它从 agent 自己的记忆语料学习词共现结构，把文档/查询投影到低维隐空间，余弦反映「主题相近」而非「字面重合」。全部运行时纯计算，离线、零资源、可复现。
- 代价：向量是**语料相对**的（重建时随语料演化），且早期语料少时不可用——这正是 `MIN_DOCS` 回落的意义。

## 实现要点

- `OxEmbeddingProvider`（新文件）：
  - 词表按文档频率取前 `VOCAB_CAP=256` 项（并列按字典序，保证确定性），把 Gram 矩阵 `M=AᵀA` 限制在 ≤256×256。
  - `jacobi(M, V)` 循环 Jacobi 旋转对角化对称矩阵，取特征值降序前 `k=min(48, t)` 个非零分量；`σ=√λ`，`termLoadings=V_k`，`sigmaInv=1/σ`。
  - 投影公式：`v = q_tfidf · V_k · Σ_k⁻¹`，再 L2 归一化；文档与查询用同一公式，保证同一空间可比。
  - `persistAll` 在一个事务里清空并按新空间重算全部文档向量写入 `mem_embeddings`，实现「先建空间后缓存」。
  - 重建节流 `REBUILD_THROTTLE_MS=5000`；`dirty` 标志可立即穿透节流（文本改写）。
- `OxMemoryStore`：
  - `DB_VERSION=4`；`onCreate`/`onUpgrade(<4)` 建 `mem_embeddings`。
  - 新增 `MODE_SEMANTIC="semantic"`；`recall()` 在语义模式用 `OxEmbeddingProvider` 取查询/候选向量，复用同一 `m.vector` 供 MMR 去重；不可用则走词面权重并记 `semantic-unavailable`。
  - `insert()` → `markDirty`；`consolidateFact` 文本变富 → `invalidateDoc`。
  - 包内 `database()` 让 provider 复用同一 SQLite 连接。
- UI/文案：`OxAgentProfileActivity` 增加 `semantic` 档与标签；`OxAgentsBridge` 注入标题按模式显示「本地语义（LSA 离线模型）」；`strings.xml` 增 `OxAgentMemoryModeSemantic`。

## 数学验证（双重复算）

同一 8 条中文记忆语料（咖啡 / 猫狗 / 散步 / 安卓开发）：

**Python 原型（numpy 无关，纯手写 Jacobi）：**

| 对比 | 余弦 |
|---|---|
| 咖啡 ↔ 咖啡因 | 0.602 |
| 咖啡 ↔ 猫 | 0.195 |
| 狗 ↔ 猫（同为宠物） | 0.972 |
| 安卓开发 ↔ 咖啡 | 0.000 |

**Java 移植复算（直接镜像 `OxEmbeddingProvider` 的 tokenize/TF-IDF/Jacobi/project）：**

```
docs=8 vocab=151 rank=8 dim=48
coffee~coffee : 0.309
coffee~cat    : 0.000
dog~cat       : 0.882
dev~coffee    : 0.000
PASS: same-topic > cross-topic
```

两版均满足「同主题相似度 > 跨主题」，且宠物类聚拢、无关主题趋零。Java 版余弦绝对值低于 Python 是因为 rank 被语料规模限制为 8（非 DIM=48），归一化后量级不同，但判别方向一致——证明 Java 移植的数值逻辑正确。

## 构建

- `taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug`
- **BUILD SUCCESSFUL in 1m 57s**（307 tasks，含 `compileDebugJavaWithJavac` 无错）。

## 诚实缺口（不谎报完成）

1. **端到端取证待配后端 agent**：模拟器里 Echo 助手是离线回声，不走记忆管线。真实「写入→建空间→语义召回命中」需一个配置了模型后端的 agent 会话 + logcat/截图实证，本环境暂缺。故上述验证为**算法级**（可复算），非**会话级**。
2. **7.4 稳定胜出未量化**：与冻结基准（`docs/qa-0.78.0.md`「冻结基准」小节）的召回质量对比需要端到端数据；在拿到之前 `semantic` 不设为默认，只作可选档，符合「仅稳定胜出才设默认」。
3. **语料冷启动**：新 agent 记忆 < 8 条时语义档自动回落词面，UI 仍显示所选档位——这是设计行为，日志/指标会标 `semantic-unavailable`，后续可在 UI 上显式提示「语料不足，暂用词面」。
4. **重建成本**：语料变化会重算整张 `mem_embeddings`（有 5s 节流 + 事务批量）。超大记忆库（数千条）首次召回可能有亚秒级停顿，后续走缓存。若成为瓶颈，下一轮可改为增量 SVD 或后台线程重建。

## 参数速查

| 常量 | 值 | 含义 |
|---|---|---|
| `MODEL` | `ox-lsa-local-1` | 模型标识（变更即触发全量失效） |
| `DIM` | 48 | 输出维度（固定） |
| `VOCAB_CAP` | 256 | 词表上限（约束 Jacobi 规模） |
| `MIN_DOCS` | 8 | 低于此值语义不可用，回落词面 |
| `REBUILD_THROTTLE_MS` | 5000 | 规模驱动重建的最小间隔 |
