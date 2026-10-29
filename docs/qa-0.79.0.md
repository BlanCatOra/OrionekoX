# QA — 0.79（阶段 B：精确召回与 Harness 桥接）

> 验收前提：0.78 阶段 A 已落地（Markdown 为唯一事实来源、CORE 常读层、supersedes 版本链、UI
> 已诚实标注「词面混合」）。本阶段在 0.78 之上补齐精确检索通道、结构化过滤、外部 Harness 可达性
> 与可观测指标。

## 已实现子项

| 子项 | 内容 | 落点 |
|------|------|------|
| 6.1 分词器 | 中文 unigram+bigram、英文按词切分（本地实现，无网络依赖） | `OxMemoryStore.tokenize` / `addCjkTokens`（既有，已确认） |
| 6.2 精确检索通道 | `id:`/`mem:` 按 public_id 精确取记忆；`"原句"` 引号短语置顶 | `parseRecallFilters` / `recallById` / recall 内 `exact` 置顶 |
| 6.3 结构化过滤 | `who:`/`people:`、`topic:`/`topics:`、`status:` 在评分前收窄 active 集 | `applyRecallFilters` |
| 6.4 Harness Memory Bridge | 外部 Harness 新会话固定读 CORE+当前状态+针对当前消息的少量召回+recall/memory_verify 入口；**不抢 compact 所有权** | 新增 `OxMemoryBridge.harnessContext`；`OxAgentsBridge` 非本地 API 分支注入 |
| 6.5 指标埋点 | 记录最近一次 recall 的耗时/命中数/注入 token 数/模式 | `OxMemoryStore.metricsJson()` + `recordRecall` |

## 验收证据

- 构建：0.79 三项改动各提交前均 `assembleAfatDebug` **BUILD SUCCESSFUL**（r1 1m10s / r2 1m2s / r3 51s）。
- 提交：
  - `dce119b` 6.4 Harness Memory Bridge
  - `f2c24ce` 6.5 recall 指标
  - `d686fea` 6.2/6.3 精确通道 + 过滤
- 版本号：0.78.0 → **0.79.0**（code 780 → 790）。

## 已知缺口（下一轮 / 诚实标注）

1. **6.6 与冻结基准对比**：qa-0.78.0.md 已冻结 0.78 的 BM25/遗忘/MMR 权重与阈值。0.79 引入精确通道与过滤后
   的量化收益（Recall@6 / MRR / 精确事实命中率 / 失效事实泄漏率）**需要一次配了模型后端的 agent 会话跑
   `tools/memory_bench_fixture.py` 才能度量**。当前模拟器所有 agent 均未配后端，故 6.6 暂记为待端到端数据。
2. **端到端运行验证**：memory.md/core.md 实际生成、supersedes 落盘、Harness Bridge 注入内容的实机取证，
   同样依赖一个配后端的 agent 会话（参见 0.78 已知缺口）。自动化工作流会在你提供测试端点后补取证据。

## 下一步

- 0.80 阶段 C：真实 embedding 接入（独立 embedding 表，provider 不可用回落 normal，不写伪向量）。
- 配后端后补 6.6 基准对比与端到端取证。
