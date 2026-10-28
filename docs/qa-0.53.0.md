# QA — alpha 0.53.0（记忆检索 v2：BM25 + 向量 + 幂律遗忘 + 事实失效）

- 版本：`0.53.0`，versionCode **5309**（`0.52.0` 为 5209）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 17:30–18:05（CST）
- 目标：根据 Mem0 / MemGPT / Graphiti / Generative Agents / FSRS /
  MemoryBank / LangMem 的实现修正 0.52.0 的检索与遗忘算法；在本地
  可用、无外部 embedding 的前提下，把普通/向量模式都做成可量化、
  可解释的混合检索。
- 版本规则：数据库 schema、打分、事实合并与摘要清理均属算法级功能
  迭代，按 minor 从 0.52.0 → **0.53.0**（5309），patch 清零。

## 1. 调研结论

完整笔记见 `docs/memory-architecture-research.md`。关键点：

- Mem0：LLM 抽取候选事实 → 相似记忆 → ADD/UPDATE/DELETE/NOOP；
  混合 semantic + BM25 + entity 检索；图片/图记忆用实体/关系图。
- MemGPT：system / working context / FIFO + recursive summary；
  ~70% 注入 memory pressure，~100% 驱逐并重写摘要；归档可召回。
- Graphiti：双时间事实（valid_from / invalid_at），旧事实失效但保留；
  图遍历 + 向量 + BM25。
- Generative Agents：recency 指数衰减（0.995/小时）、importance 1–10、
  relevance 余弦，三项归一化后等权相加。
- FSRS：幂律遗忘 `R=(1+factor*t/S)^-decay`，复习提升 stability，
  比固定半衰期更接近人类遗忘。
- MemoryBank：`R=e^(-t/S)`，S 初始 1、每次召回 +1；论文自称简化模型。
- LangMem：semantic/episodic/procedural，hot-path vs background
  formation，相关性 + 重要性 + 记忆强度。

## 2. 实现（Round A）

### 2.1 Schema v2

- `memories` 新增 `difficulty`、`valid_from`、`invalid_at`、`subject`；
  `onUpgrade` 从 v1 原地迁移并回填 `valid_from=created_at`。
- 保留 `vec`（128 维本地哈希向量），短期/长期、importance、stability、
  access_count 等字段继续使用。

### 2.2 混合检索

- 查询与文档统一分词（英文词、中文单字/双字），去停用词。
- 在 Java 内实现 BM25（k1=1.2, b=0.75），按当前 Agent 的文档集计算
  IDF 与平均长度，再对本次查询归一化。
- 普通模式：`0.58*BM25 + 0.22*retention + 0.12*importance + 0.08*access`。
- 向量模式：`0.42*BM25 + 0.32*cosine + 0.14*retention + 0.08*importance
  + 0.04*access`；余弦仍来自本地哈希向量。
- MMR 去重：按 `score - 0.18*maxSimilarity(已选)` 贪心选择，避免重复
  小对话挤掉关键事实；向量模式用余弦，普通模式用 token Jaccard。
- 失效事实默认不参与召回；查询含“以前/原来/过去/上次/used to”等词时
  才允许带 `invalid_at` 的历史条目，并降权 0.55。

### 2.3 幂律遗忘

- 保留率 `R = 1 / (1 + ageDays / (9*S))`（FSRS v4 形式）。
- 新记忆 stability：长期 `max(30, importance*45)` 天，短期
  `max(1, importance*7)` 天；difficulty 长期 4、短期 6。
- 召回时 `S' = S * (1 + 1.2*(1-R))`，`D' = max(1, D-0.2)`；
  短期条目访问 2 次、S≥21 天或 importance≥0.8 时提升为长期。
- 摘要/事实优先，时间衰减只作为一项信号，不再用固定 7/90 天半衰期
  直接乘相关度。

### 2.4 事实合并与摘要清理

- 抽取事实时按中英文句号/问号/分号/换行分句（修复 0.52.0 英文事实
  被整段合并的问题）。
- `subjectKey` 提取“我的 X / my X / I like X”等主题；同主题近似事实：
  - 新事实覆盖旧事实（token 超集）→ 保留更丰富版本；
  - 同主题但内容不同（生日 3/14 → 3/15、blue → green）→ 旧事实
    `invalid_at`，保留历史但不参与默认召回；
  - 数字或否定词冲突同样失效旧事实。
- 失效事实会从其摘要行中删除（`scrubSummaryFact`），摘要生成后再执行
  一次全量清理；摘要超过 6 条时合并最旧 4 条为滚动摘要。
- 记忆摘要开头提示“摘要可能含历史信息，冲突以最新事实为准”。

### 2.5 工具与提示

- `memory_search`、`memory_verify`、`remember`、`recall` 均使用 v2
  检索；`memory_verify` 先看事实，有事实命中时不再返回旧对话，避免
  把历史原话当成当前事实。
- system prompt 的记忆规则增加“同主题冲突时以最新事实为准，旧对话/
  摘要只作历史参考”。

## 3. 设备验证 — PASS

### 3.1 测试对象

- 60 条消息的 `QA Memory v2`，包含：
  - 姓名 Lin Zhou、生日 3/14、黑咖啡；
  - 最爱颜色 blue；
  - 杭州/后端工程师；
  - 生日更新为 3/15；
  - “不再喜欢蓝色，最爱绿色”。
- 本地 mock OpenAI 服务（`tools/memory_bench_server.py`）记录请求并
  触发 `memory_search` / `memory_verify`；fixture 由
  `tools/memory_bench_fixture.py` 生成。

### 3.2 压缩与摘要

- 首轮请求 17 条消息，system 同时含“上下文压缩 / 记忆库摘要 / 相关
  记忆”。
- 数据库 v2 迁移成功（`PRAGMA user_version=2`）。
- 事实表最终：
  - 有效：name、black coffee、Hangzhou、birthday 3/15、favorite color
    green；
  - 失效：birthday 3/14、favorite color blue（`invalid_at` 非 0）。
- 摘要中的 3/14 与 blue 行被清理，只保留中性小对话；摘要头部出现
  “摘要可能包含后来被更新的事实”提示。

### 3.3 召回与查证

- 查询“When is my birthday and what is my favorite color”：
  `memory_search` 第一条是有效事实 `birthday is March 15`；旧的 3/14
  只作为历史对话出现，事实优先。
- `memory_verify("birthday")` 只返回有效的 3/15 事实，不再返回旧对话。
- `memory_verify("favorite color")` 只返回绿色事实，不返回 blue。
- 向量模式查询 “Do I like black coffee”：有效事实相关度 0.83，旧对话
  0.69，摘要 0.31；说明混合分与 MMR 生效。
- 召回后生日事实的 `stability` 从 40.5 略增、`difficulty` 从 4.0 降到
  3.6，access_count=2，符合复习强化方向。

### 3.4 稳定性与清理

- 迁移、压缩、召回、工具循环、模式切换全程无本包 FATAL/ANR。
- 测试结束后删除测试 Agent、记忆库行、测试 tgId 的 dialogs/messages，
  `memories`/`memory_state` 计数为 0；恢复模拟器 HTTP 代理与无障碍；
  未执行 `pm clear`。
- 本轮 UI 未改动，沿用 0.52.0 的记忆页/设置截图。

## 4. 未覆盖 / 下一步

- 向量仍是**本地哈希向量**，不是语义 embedding；同义改写/跨语言召回
  有限。当前 Provider 的 `/embeddings` 无可用模型（opendesign 返回
  model_not_found，opencode-go 403），Round B 会做可配置 embedding
  provider + 维度/模型校验 + 本地 fallback。
- 摘要仍是抽取式；Round B 计划用 Agent 自己的模型异步生成抽象摘要与
  事实抽取（严格 JSON、可取消、失败回退）。
- 未接入图记忆/实体关系；先把扁平混合检索的 recall@k 基线测稳，再决定
  是否引入 Graphiti 式三元组。
- 失效事实的旧对话仍可能出现在 `memory_search` 的历史结果里（已由提示
  词和事实优先约束），后续可对包含失效事实的 turn 做降权/标记。
- 没有 token 压力预警（70%）和 token 预算；当前阈值为消息条数。

## 5. 产物

- `dist/OrionekoX-0.53.0-alpha-arm64-x64.apk`（versionCode 5309），sha256
  `b421454304d0f261e3bf9cb15e167990b08c3939fd7090434ccce2343798137b`
- `dist/OrionekoX-0.53.0-debug-arm64-x64.apk`，sha256
  `f612c6c4f246a4f56bcee79b13f2d01dfa0503ff2ac9c9e4f429f11d482571db`
- 调研：`docs/memory-architecture-research.md`
- 基准脚本：`tools/memory_bench_fixture.py`、`tools/memory_bench_server.py`
- 构建日志：`/tmp/ox-build-0530.log`（`BUILD SUCCESSFUL in 2m 5s`）
