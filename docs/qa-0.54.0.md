# QA — alpha 0.54.0（异步模型蒸馏：抽象摘要与事实）

- 版本：`0.54.0`，versionCode **5409**（`0.53.0` 为 5309）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 18:10–18:35（CST）
- 目标：压缩批次除了本地抽取式摘要，再异步调用 Agent 自己的模型生成
  抽象摘要和原子事实；不阻塞聊天，失败时保留抽取式 fallback。
- 版本规则：新增后台模型抽取/摘要与记忆写入，属于功能迭代，按 minor
  从 0.53.0 → **0.54.0**（5409），patch 清零。

## 1. 实现

### 1.1 异步蒸馏

- 新增 `OxMemoryDistiller`：只在一次压缩实际写入 ≥8 条消息时触发；
  每个批次用 `agent.id + firstTs + size` 去重，后台线程执行。
- 请求使用 Agent 自己的 OpenAI-compatible `/chat/completions`：
  `stream=false`、temperature 0.2、max_tokens 1200，沿用 Agent 的
  baseUrl/apiKey/自定义 headers；不新增 Key，不把 Key 写入日志。
- system prompt 标记 `[MEMORY_DISTILLER]`，要求仅返回：
  `{"summary":"...","facts":[{"text":"...","importance":0.8}]}`；
  摘要 ≤1200 字符、最多 12 条事实、单条 ≤300 字符、importance 0.3–0.95，
  要求解析相对日期、保留纠正、不编造。
- transcript 最多 14,000 字符，逐条截断；响应上限 2 MiB，超时
  20s 连接/90s 读取。解析失败、HTTP 非 200、空内容都静默保留抽取式
  结果，不影响本轮聊天。
- 模型输出经 `OxMemoryStore.ingestModelMemory` 写入：事实走同一套
  subject 合并/失效逻辑，摘要 `source=model`、importance 0.9；随后
  清理失效事实行并合并旧摘要。
- 模型摘要与抽取式摘要可以共存，但 `digest` 最多注入 1 条摘要，优先
  高 importance 的模型摘要；事实仍优先于摘要。

### 1.2 主题键修复

- 模型事实常是第三人称（`Lin Zhou's birthday is March 15`）。原
  `subjectKey` 回退到前三个 token（`lin zhou s`），导致生日和颜色被
  当成同一主题，互相失效并连带清空模型摘要。
- 新增 possessive 规则 `[a-z]+'s ...`；`trimSubject` 在 `is/are/was/
  like/live/work/and` 等词处停止，得到 `birthday`、`favorite color`、
  `name`、`hangzhou` 等稳定主题。
- 复验：模型生日事实有效，颜色事实合并进现有绿色事实，模型摘要保持
  有效；旧 3/14 与 blue 仍失效。

## 2. 设备验证 — PASS

- fixture：60 条消息，含姓名/生日 3/14→3/15、颜色 blue→green、杭州/
  咖啡；Agent 指向本地 mock `/chat/completions`；mock 对
  `[MEMORY_DISTILLER]` 返回固定摘要和两条事实。
- 第一条用户消息触发 45 条压缩；后台蒸馏请求随后到达 mock（日志中
  2 条消息、transcript 开头正确）。数据库：
  - `source=model` 的生日事实有效，subject=`birthday`；
  - 颜色事实被合并进现有绿色事实（不新增重复）；
  - 模型摘要 `MODEL_SUMMARY: ... birthday March 15, green, coffee,
    Hangzhou`，`source=model`、importance 0.9、有效；
  - 抽取式摘要仍存在但 importance 0.8。
- 下一轮 system prompt 只注入：
  - 5 条有效事实（生日 3/15、绿色、杭州、姓名、咖啡）；
  - 1 条 `MODEL_SUMMARY`；
  - 不再出现抽取式摘要或旧 3/14/blue。
- 没有重复模型请求（RUNNING 去重）；消息发送与工具循环不等待蒸馏；
  全程无本包 FATAL/ANR。
- 清理测试 Agent、记忆库行与测试 tgId 的 dialogs/messages，计数均为 0；
  恢复模拟器 HTTP 代理与无障碍；未执行 `pm clear`。

## 3. 未覆盖 / 边界

- 蒸馏只在压缩批次 ≥8 条时触发，不是每轮都调用；使用 Agent 模型会
  消耗 token，具体成本取决于 transcript 与模型。
- 模型输出只做结构/长度校验，没有事实核查；可能产生错误事实。它走
  同一套合并/失效逻辑，但错误内容仍可能进入记忆库，需要后续做
  evidence/自洽校验。
- Provider 必须支持 OpenAI JSON 响应；不支持或返回 markdown 时会
  fallback 到抽取式，不阻塞聊天。
- 仍没有真正的语义 embedding；向量模式仍是本地哈希。Provider 当前
  没有可用 `/embeddings` 模型。
- 模型摘要与抽取式摘要共存于 DB，digest 只显示最新的 1 条；未做
  独立“模型摘要替换抽取摘要”的批标记。
- 失败路径（HTTP 非 200/非法 JSON）为代码审查，未单独用坏端点做设备
  回放。

## 4. 产物

- `dist/OrionekoX-0.54.0-alpha-arm64-x64.apk`（versionCode 5409），sha256
  `1cf7495caf7fbab3e4ea9daa0756db32f53dfb9f6e9eb58b6266f94c57710b66`
- `dist/OrionekoX-0.54.0-debug-arm64-x64.apk`，sha256
  `770f8ddecb3372858f72abf45a1a8cb0a457e86ed74898eef3de7e70b5f335c9`
- 构建日志：`/tmp/ox-build-0540b.log`（最终，
  `BUILD SUCCESSFUL in 1m 57s`；0540 首轮为接口接入）
- 基准：`tools/memory_bench_fixture.py`、`tools/memory_bench_server.py`
  （mock 已支持 `[MEMORY_DISTILLER]` 响应）
