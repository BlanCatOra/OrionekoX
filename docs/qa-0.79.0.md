# QA — 0.79.0（记忆阶段 B：精确召回 + Harness 桥接）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。

日期：2026-10-29 · 分支：`orioneko/0.79-memory-stage-b` · 基线：`bd2421f`（0.78 冻结）

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 6.1 分词器 | 本地中文 unigram+bigram、英文词切分（OxMemoryStore.tokenize / addCjkTokens） | 完成 |
| 6.2 精确检索通道 | id: / 引号原句（"..."）/ 姓名；本轮新增 date: after: before: 时间窗口 | 完成 |
| 6.3 people/topics/status 过滤 | who:/people:、topic:/topics:、status:；本轮修复：此前 insert() 把 people/topics 写空串导致过滤器对真实数据无效，现已在写入时本地抽取并落盘（SQLite + Markdown 本体） | 完成 |
| 6.3（本轮补充） | status:historical 仅保留被取代的失效条目用于溯源；status:all 保留全部 | 完成 |
| 6.4 Harness Memory Bridge | 外部 harness 会话镜像进本地记忆，只读、幂等（ingestHarnessMirror） | 完成 |
| 6.5 指标埋点 | metricsJson() 暴露 last_recall_ms / tokens / count / mode / at | 完成 |
| 6.6 与冻结基准对比调权重 | 需配后端 agent 跑端到端基准；当前无可用后端 | 待验证 |

## 精确召回过滤器语法（recall(agentId, query, ...)）

在查询字符串中附加 key:value 指令（空格分隔），与自由文本查询可混用：

| 指令 | 含义 | 示例 |
|---|---|---|
| id:<public_id> 或 mem:<public_id> | 按记忆 ID 精确返回单条 | id:mem_xxx |
| "原句" | 引号内原句命中则置顶（score=1.5） | "我喜欢蓝色" |
| who:<人> / people:<人> / person:<人> | 仅保留含该人物的条目（people 列本地抽取） | who:小明 |
| topic:<主题> / topics:<主题> / tag:<主题> | 仅保留含该主题的条目（topics 列本地抽取） | topic:生日 |
| status:<active\|historical\|all> | active=默认有效；historical=仅失效溯源；all=全部 | status:historical |
| date:YYYY-MM-DD | 仅命中指定当天 | date:2026-03-15 |
| after:YYYY-MM-DD / since: | 不早于该日 | after:2026-01-01 |
| before:YYYY-MM-DD / until: | 不晚于该日（含当日） | before:2026-06-30 |

组合示例：小明 生日 date:2026-03-15（人物 + 主题 + 当天）。

## 人物 / 主题本地抽取（extractPeople / extractTopics）

纯本地、无网络，写入时一次性计算并随条目持久化（重建时由 Markdown 本体经 upsertFromOntology 保留）：

- 人物：显式称呼线索（我叫/我是/my friend/called/named/with/i am/name is 等）、中文姓氏引导的 2-4 字片段、Mr/Ms/Mrs/Dr + 拉丁专有名词。
- 主题：subjectKey（事实主语，如"我的X"） + 领域关键词（生日/偏好/工作/项目/城市/家人/宠物/账户/密码 及对应英文）。

## 构建与验证

- assembleAfatDebug BUILD SUCCESSFUL（本轮 3 次增量构建均通过，约 47s）。
- 精确召回 / 过滤器逻辑为纯 Java 单元可验证；但端到端检索效果（真实 agent 会话内 recall 命中、people/topics 实际落盘、supersedes 溯源）需配置后端 agent 的模拟器会话取证。
- 诚信声明：未做端到端实证的"可用"一律记为待验证，不谎报完成。

## 已知缺口（下一轮）

1. 6.6 基准对比调权重：需在冻结 fixture（/tmp/ox-memory-bench-078）上跑 tools/memory_bench_fixture.py + 本轮新增过滤器，对比 0.78 冻结基线（见 docs/qa-0.78.0.md「冻结基准」），确认指标不退化。待配后端 agent。
2. 端到端记忆落盘取证：memory.md 实际生成、people/topics 写入、supersedes 链、status:historical 溯源展示，需后端 agent 会话。
