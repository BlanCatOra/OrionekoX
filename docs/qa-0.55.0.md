# QA — alpha 0.55.0（开源式 compact：TextRank + SummaryBufferMemory + token 触发）

- 版本：`0.55.0`，versionCode **5509**（`0.54.0` 为 5409）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 18:40–19:05（CST）
- 目标：把 compact 从“按消息数截断 + 首行摘要”改为可离线运行的
  开源式方案：TextRank 抽取、LangChain SummaryBufferMemory 的
  “滚动摘要 + 近期窗口”、MemGPT 递归摘要、Selective Context 的
  预算裁剪，以及按 token 压力的触发。
- 版本规则：压缩算法、触发条件与摘要策略属于功能迭代，按 minor 从
  0.54.0 → **0.55.0**（5509），patch 清零。

## 1. 调研与取舍

- **LLMLingua / LongLLMLingua**：需要 GPT2-small/LLaMA-7B 级模型做
  token 打分；README 的量化 LLaMA-2-7B 仍假设 <8GB GPU，不适合直接
  放进 APK。**LLMLingua-2** 是 BERT 级 token 分类器，理论上可用
  ONNX 端上运行，但需要模型权重、推理 runtime 与移动端基准，本轮
  未引入。
- **MemGPT**：递归摘要 + 可召回归档；我们已有递归摘要和检索，
  本轮把上一版摘要作为 compact 输入。
- **LangChain SummaryBufferMemory**：摘要 + 最近窗口，达到 token
  上限时把最旧消息移入摘要；本轮实现为“保留最近窗口 + 滚动摘要”。
- **Selective Context**：按自信息/信息量裁剪到预算；端上无小模型，
  用 TextRank + 去重/MMR 作为可离线近似。
- **TextRank**：句子相似度图 + PageRank 选句；用于抽取式 fallback
  和压缩前处理。

## 2. 实现

### 2.1 OxContextCompactor

- token 估算：CJK 每字约 1 token，拉丁字符约 4 字符/token，附加
  消息开销；用于触发和预算，不是 provider 的精确 tokenizer。
- TextRank：句子分词后构建余弦相似度图，阻尼 0.85、20 次迭代；
  选句后按原顺序输出。
- 去重与 MMR：相似度 >0.65 的重复句先去掉；选句时与已选句
  相似度 >0.55 的候选跳过，避免重复 filler 占满预算。
- `summarize(batch, previousSummary, maxTokens)`：把上一版摘要与
  新批次拼接后压缩，输出每句一行的可读摘要；`maxTokens=300`
  （约 1200 字符），比 0.54 的 1200 token 更紧凑。

### 2.2 触发与保留窗口

- 消息数超过 `memory_threshold`（默认 45）仍触发。
- 如果 Agent 设置了 `context_length`，估算 token 超过其 45% 也触发，
  并在 system 中标注“token 压力触发：约 N tokens”。
- token 压力下，保留窗口按 `context_length * 55%` 的预算从尾部
  计算，最低 256 tokens、至少 4 条；不再使用固定的
  `max(8, threshold/3)` 覆盖 token 预算。
- 非 token 压力仍保留 `max(8, threshold/3)` 条。

### 2.3 摘要写入

- `compressHistory` 先取当前有效摘要作为 previousSummary，压缩旧
  批次后写入新的 `source=compression` 摘要；摘要行按句分行，便于
  后续按失效事实清理。
- 异步 `OxMemoryDistiller` 接收 previousSummary，要求模型把旧摘要
  与新 transcript 合并为新的滚动摘要；模型摘要写入时用请求开始时间
  作为 cutoff，失效此前摘要，保证只有一条当前摘要。
- `digest` 最多注入 1 条摘要（优先模型摘要），事实仍排在前面。

## 3. 设备验证

### 3.1 消息数触发

- 60 条 fixture（生日 3/14→3/15、blue→green、姓名/咖啡/杭州），
  默认 45 条触发；TextRank 摘要 249 字符，包含咖啡/杭州等行；
  模型蒸馏随后写入 111 字符 `MODEL_SUMMARY`，抽取式摘要被 cutoff
  失效，下一轮只注入有效事实 + 模型摘要。
- 旧 3/14、blue 不再出现在有效摘要/事实中。

### 3.2 token 压力触发

- 20 条长消息、`context_length=500`、`memory_threshold=45`：
  消息数未到 45，但估算约 790 tokens > 500×45%，触发压缩。
- system 显示“较早的 13 条消息已写入…保留最近 7 条（token 压力
  触发：约 790 tokens）”，说明 55% 预算从尾部只保留 7 条；
  修复前因保留窗口下限 1000 tokens 保留了 19 条。

### 3.3 稳定性与清理

- 压缩、模型蒸馏、工具循环、两次触发路径全程无本包 FATAL/ANR。
- 测试结束删除两个测试 Agent、记忆库行、测试 tgId 的
  dialogs/messages，计数归零；恢复模拟器 HTTP 代理与无障碍；
  未执行 `pm clear`。
- UI 未改，沿用记忆页/设置页既有截图。

## 4. 未覆盖 / 边界

- TextRank/去重是离线近似，不是 LLMLingua-2 的语义 token 压缩；
  LLMLingua-2 需要 BERT/ONNX 资产与移动端基准，留待后续。
- token 估算是近似值，不是模型 tokenizer；极端多语言/代码可能偏差。
- 抽取摘要预算 300 tokens；模型摘要上限 1200 字符。
- token 压力没有 70% 预警阶段，只有 45% 触发一次压缩。
- 外部 Harness（Claude Code/OpenCode/CommandCode/OpenClaw/
  OxLinux 等）不应使用本 compact；其 harness 路由与适配是下一轮
  内容，本轮仍未把本地 compact 应用到这些模式。

## 5. 产物

- `dist/OrionekoX-0.55.0-alpha-arm64-x64.apk`（versionCode 5509），sha256
  `702ac8ab1040b9695549f68db8bb6d7cb27f612b1d0164f817d681919b20c8ab`
- `dist/OrionekoX-0.55.0-debug-arm64-x64.apk`，sha256
  `40d1d0ec47c3f5183a1f0d9bd93dc6d2c6bf7e823a37809670c7b65dedf3a729`
- 构建日志：`/tmp/ox-build-0550h.log`（最终，
  `BUILD SUCCESSFUL in 2m 25s`）
- 基准：`tools/memory_bench_fixture.py`、`tools/memory_bench_server.py`
