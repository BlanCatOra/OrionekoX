# QA — 0.78.0（记忆阶段 A：Markdown 本体 / 常读层 / 版本链）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。

日期：2026-10-29 · 分支：`orioneko/0.78-memory-stage-a` · 基线：`e393be4`（0.77.0）

## 变更范围

| 文件 | 变更 |
|---|---|
| `ox/OxMemoryOntology.java` | 新增：Markdown 唯一事实来源（`memory.md` ⟦oxmem⟧ 块）；原子写入（append+fsync / temp+rename）；`supersede` / `markSuperseded` / `updateEntry`；`core.md` 常读层（`ensureCore` / `readSection` / `writeSection`）；`migrateLegacy` 幂等迁移 |
| `ox/OxMemoryStore.java` | `DB_VERSION` 2→3（`public_id/people/topics/status/supersedes` 列）；`insert()` 改「先 Markdown 后索引」；`consolidateFact` 写版本链；`ensureOntology`（惰性迁移，挂 recall/digest/insert）；`rebuildFromMarkdown` / `exportLegacyRows` / `upsertFromOntology`；`coreSection` |
| `ox/OxAgentsBridge.java` | `baseMessages` 旧 `memory.txt` 整段注入 → `appendCoreLayers`（CORE/ABOUT_USER/ABOUT_AGENT/CURRENT 固定注入）；「向量模式」标签改「词面混合（哈希投影，非语义向量）」 |
| `gradle.properties` | 770/0.77.0 → 780/0.78.0 |

## 验收标准逐条核对

### 1. 迁移完整（SQLite + memory.txt → Markdown）
- **代码**：`OxMemoryOntology.migrateLegacy`（幂等：`memory.md` 存在即跳过）；SQLite 行经 `OxMemoryStore.exportLegacyRows` 导出，`source=migrated`，保留原 `hash` 防重复；`memory.txt` 旧行迁入 `core.md` 的 ABOUT_USER 段（`ensureCore`）。
- **运行时证据**：模拟器 `files/oxagents/op_operit2_326095665/memory.txt`（25B，内容 `# USER`）确认迁移源存在。
- **待补**：端到端迁移触发需要配置了模型后端的 agent 会话（见下"已知缺口"）。

### 2. 版本可追溯（supersedes 链）
- **代码**：`consolidateFact` 矛盾/冲突分支 → `markSuperseded(old.publicId)` + `pendingSupersedes`（ThreadLocal）→ 新 fact 插入时 `supersedes=<old>`（`OxMemoryStore.insert`）；更丰富替换分支 → `updateEntry` 同步更新 Markdown 正文与 hash，保证"先 Markdown 后索引"的一致性。
- **重建**：`upsertFromOntology` 把 `status/supersedes` 写回派生索引，旧版本 `invalid_at=createdAt`。

### 3. 故障可恢复
- **代码**：写入口径为 Markdown 优先（append+fsync）；SQLite 失败不影响本体；`ensureOntology` 挂在 recall/digest/insert 入口，重启后首次访问即补齐迁移；迁移幂等（hash 防重）。
- **回滚**：`rewriteAll` 走 temp+rename，中途失败不损坏原文件。

### 4. 重建可验证
- **代码**：`rebuildFromMarkdown` → `upsertFromOntology`（全删重建）。派生索引的遗忘曲线计数器（access_count/stability/difficulty）重建后重置为固定默认值——**这是明文记录的降级，不是静默丢数据**（见 #5）。
- **待补**：迁移前后 `tools/memory_bench_fixture.py` 基准对比数据（**冻结基准见下文「冻结基准」小节**，本轮已建档）。

### 5. 数据归属
- 重建时访问计数器使用固定默认值并在本文档明文声明；`created_at` 一律来自原记录（迁移保留原时间戳）。

## 运行时冒烟证据（模拟器 emulator-5554）

- `assembleAfatDebug` **BUILD SUCCESSFUL**（本轮 56s，15 任务重编译）。
- `adb install -r` Success；`LaunchActivity` 启动正常，会话列表截图 `dist/iter-shots/`（本轮 /tmp/ox-r2-main.png：Echo Assistant/Sakura/Yuki/Summarizer/Translator 完整渲染）。
- `logcat -s OxAgentsBridge OxMemoryStore OxMemoryOntology AndroidRuntime:E` **无任何输出**——改动类无异常、无崩溃。
- UI 自动化实测：向 Echo Assistant 发送 "My name is Alice and I like blue"（消息入 chat.json）——echo 为离线回声模式（`base_url=""`），按 `isLocalApi` 分流**正确地不**触发本地记忆管线，行为符合设计。

## 冻结基准（0.78 → 0.79/0.80 对比基线）

> 本轮（0.78 阶段 A 收口）从 `OxMemoryStore` 源码逐行抄录当前检索算法参数，作为 0.79（精确召回+Harness 桥接）与 0.80（真实语义向量）的**对比基线**。0.79/0.80 任何改动都必须先复跑 `tools/memory_bench_fixture.py` 在相同 fixture 上对比，确认指标不退化才合并。

基准 fixture：`python3 tools/memory_bench_fixture.py /tmp/ox-memory-bench-078` → 生成 `agent.json`/`chat.json`/`AGENTS.md`，agent id `qa_mem_bench`，60 条消息，种子事实含 2 组 supersede（生日 Mar14→Mar15、颜色 blue→green）。

### 评分公式（源码 `recall()`，分数 clamp 到 [0, 1.5]）

| 模式 | 公式 |
|---|---|
| 无查询（q 空） | `0.5*retention + 0.3*importance + 0.2*access` |
| 普通模式 | `0.58*lexical + 0.22*retention + 0.12*importance + 0.08*access` |
| 向量模式 | `0.42*lexical + 0.32*cosine + 0.14*retention + 0.08*importance + 0.04*access` |
| 长期记忆加成 | `memoryType=="long"` → `+0.02` |
| 失效降权 | `invalidAt>0` → `score *= 0.55` |
| 低相关剔除 | `!q.empty && lexical<0.04 && cosine<0.10` → `score = 0` |

### 子项与常量

- **BM25**：`k1=1.2`，`b=0.75`；`lexical = bm25_i / maxBm25`（归一化到 [0,1]）。
- **遗忘曲线（retention）**：`retention = 1 / (1 + ageDays / (9.0 * stability))`；`stability = max(0.2, m.stability)`；`ageDays = (now - max(lastAccess, createdAt)) / 86400000`。
- **访问提升（access）**：`access = min(1.0, ln(1+accessCount) / 2.5)`。
- **MMR 多样性**：`adjusted = score - 0.18 * maxSim`；`maxSim` 取已选集合相似度最大值；相似度 = 向量模式用 cosine，普通模式用 `jaccard(tokenize(text))`。
- **遗忘更新（访问时）**：`nextStability = min(3650, max(0.2, stability) * (1 + 1.2*(1-retention)))`；`nextDifficulty = max(1.0, difficulty - 0.2)`；晋升长期条件 `(accessCount+1>=2) || nextStability>=21 || importance>=0.8`。
- **初始值**：`stability`：`long`→`max(30, imp*45)`，否则 `max(1, imp*7)`；`difficulty`：`long`→`4.0`，否则 `6.0`。
- **agent 默认**：`memory_recall=6`，`memory_threshold=45`。

### 诚信声明（关于"向量模式"）

当前向量模式的 `cosine` 由 FNV 哈希投影得到（非真实 embedding），UI 已改正标签为「词面混合（哈希投影，非语义向量）」。**0.80 之前，向量分数的语义有效性为 0**——0.79 对比时若启用向量模式，其 `cosine` 分量不应被当作语义信号；0.80 接入真实 embedding 后需重新冻结此基线。

## 已知缺口（下一轮）

1. **端到端功能验证**：memory.md/core.md 实际生成、supersedes 链落盘、CORE 层注入内容，需要一个配置了模型后端的 agent 会话（当前模拟器所有 agent 均未配置后端）。配置后端后跑一轮对话即可取证。
2. ~~**基准冻结**：`memory_bench_fixture` 迁移前后对比数据。~~ ✅ 已冻结（见上文「冻结基准」小节）。
3. UI 增加 core.md 四段的查看/编辑入口（可选，报告建议）。
