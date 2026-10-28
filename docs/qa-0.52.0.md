# QA — alpha 0.52.0（本地记忆库：普通/向量、遗忘曲线、阈值压缩与查证）

- 版本：`0.52.0`，versionCode **5209**（`0.51.0` 为 5109）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 17:00–17:22（CST）
- 目标：为每个 Agent 增加独立的本地记忆库，支持普通关键词与实验性
  向量两种召回模式；上下文达到阈值（默认 45 条消息）时把较早对话
  压缩进记忆库，仅把最近原文 + 摘要 + 相关记忆送入模型；模型不确定
  过去是否提过时，可调用 `memory_search` / `memory_verify` 查证。
- 版本规则：新增用户可见的记忆模式、压缩行为、工具与设置，属于功能
  迭代，按 minor 从 0.51.0 → **0.52.0**（5209），patch 清零。

## 1. 实现

### 1.1 存储

- 新增每个 Agent 独立的 SQLite 记忆库 `databases/oxmemory.db`；
  表 `memories` 保存可读文本、类型（turn/fact/summary）、角色、时间、
  最后访问、访问次数、重要度、稳定度、短期/长期、来源、去重 hash
  与本地向量；`memory_state` 记录已压缩到的聊天条目位置。
- 记忆文本是自然语言（“用户：…”“助手：…”、事实行、按日期分段的
  对话摘要），不是原始 JSON。
- 单 Agent 最多保留 4,000 条；压缩时清理 30 天以上、从未访问、
  重要度低的短期条目。

### 1.2 两种召回

- **普通模式**：查询分词后按词元重合度与整句子串匹配排序，适合精确
  关键词与人名/日期。
- **实验性向量模式**：把文本编码为 128 维本地哈希向量（英文词、
  中文单字与双字组合，带符号哈希、L2 归一化），用余弦相似度与关键词
  混合排序。不调用外部 embedding 服务，离线可用。
- 两种模式都叠加遗忘曲线：短期半衰期 7 天、长期 90 天；综合相关度、
  时间衰减、访问次数与重要度。命中会更新 `last_access`/`access_count`；
  短期条目被访问 2 次或重要度 ≥0.8 时提升为长期并提高稳定度。

### 1.3 阈值压缩

- Agent 设置新增：记忆库模式（关闭/普通/实验性向量）、压缩阈值
  （默认 45 条消息，范围 10–200）、召回条数（默认 6，内部 1–12）。
- 当聊天条目数超过阈值时，压缩较旧条目（保留最近 `max(8, 阈值/3)`
  条原文），将其写入记忆库：
  - 每条对话写成可读的“用户/助手”记忆；
  - 从用户消息抽取含“我叫/我的/喜欢/生日/记住/remember/my/I am”等
    标记的事实，作为高重要度长期记忆；
  - 为该批对话生成按日期分段的摘要；
  - `memory_state` 记录进度，后续只压缩新产生的旧条目，避免重复。
- 系统提示加入“# 上下文压缩”“# 记忆库摘要”“# 与当前消息相关的记忆”
  三个区块，以及“不确定时先 recall/memory_search 查证”的规则。
- 群聊使用同一 Agent 的记忆库，同样按阈值压缩群历史。

### 1.4 工具

- `remember`：写入长期事实（同时保留原有 memory.txt 兼容）。
- `recall`：优先查新记忆库，未命中再查 memory.txt 与聊天历史。
- 新增 `memory_search(query, limit)`：返回带日期、类型与相关度的证据。
- 新增 `memory_verify(query)`：严格匹配，多个关键词至少命中 2 个；
  未找到时明确返回“未找到，不要当作事实”，避免把
  “favorite color”误配到“favorite editor”。

### 1.5 设置与界面

- Agent 设置→高级参数新增“记忆库模式”和“上下文压缩阈值（消息数）”。
- 记忆页新增“记忆库”统计行（总数/长期/短期/事实）、“清空记忆库”
  与只读的最近记忆对话框；原 memory.txt 编辑保留。
- 中文包 112 → 114（两轮：新增文案与统计截断修复）。
- Chatbox 备份的 `orionekoAgent` 增加 `memory_mode`、
  `memory_threshold`、`memory_recall`，回导时校验范围（代码路径已完成，
  本轮未单独做备份回导；记忆库数据库本身不进入备份）。

## 2. 设备验证 — PASS

### 2.1 测试对象

- 自建 `QA Memory 0520`（tgId 555000109），预置 60 条聊天记录，
  其中包含“My name is Lin Zhou / birthday is March 14 / I like black
  coffee”“I live in Hangzhou…”“favorite editor is Neovim”等事实。
- 本地 mock OpenAI-compatible 服务（`10.0.2.2:18999`）记录每轮请求，
  并可自动发出 `memory_search` / `memory_verify` 工具调用，避免真实
  模型的随机性。

### 2.2 普通模式

- 发送“Do you remember my birthday”后，请求只有 17 条消息（system +
  最近 15 条 + 当前用户），system 同时出现“上下文压缩 / 记忆库摘要 /
  相关记忆”标记。
- 模型自动调用 `memory_search`，工具返回：
  `[2026-10-28 · 事实 · 相关 1.10] 用户：My name is Lin Zhou. My
  birthday is March 14. I like black coffee.` 以及对应对话/摘要。
- 数据库：47 条 turn（46 短 + 1 长）、3 条 fact、1 条 summary；
  访问计数已累加，事实与摘要为长期；`memory_state.compressed_count=47`。
- 再次打开记忆页显示“63 条 · 长期 7 · 短期 56 · 事实 3”。

### 2.3 向量模式

- 把 Agent 切换为 `memory_mode=vector` 后发送“black coffee”相关消息：
  system 仍含压缩/摘要/召回区块；`memory_search` 返回生日/咖啡事实，
  相关度 0.61（普通模式为 1.10，因关键词整句命中；向量为混合分，
  属于预期差异）。
- 说明本地哈希向量路径可独立工作，不依赖网络 embedding。

### 2.4 查证与未找到

- `memory_verify("birthday")` 返回带日期的生日事实，并追加“区分事实
  与推断，不要补全未出现细节”。
- `memory_verify("favorite color")` 严格匹配后返回“未找到…不要当作
  已确认的事实”，不再因为共享 “favorite” 而误配 “favorite editor”。

### 2.5 稳定性与清理

- 发送、压缩、工具调用、重复召回、切换模式与记忆页浏览全程无本包
  FATAL/ANR。
- 清理：删除测试 Agent 目录、`oxmemory.db` 中该 Agent 的全部行、
  测试 tgId 的 dialogs/messages；最终 `memories`/`memory_state` 计数
  为 0，Agent 列表不再出现 QA Memory；未执行 `pm clear`，未删除其他
  Agent 或用户数据。
- 测试期间临时关闭模拟器全局 HTTP 代理以访问 `10.0.2.2:18999`，结束后
  已恢复为 `10.0.2.2:7890`；无障碍服务已恢复。

## 3. 未覆盖 / 已知边界

- 向量模式是本地哈希向量，不是语义 embedding；同义改写召回有限，
  后续可接入离线/远程 embedding。
- 压缩摘要为抽取式（选取用户原句），没有调用模型生成抽象摘要；
  摘要质量受原句影响。
- 记忆库只在本机 `databases/oxmemory.db`，尚未进入 Chatbox 备份或
  `.oxagent`；Chatbox 只携带记忆模式/阈值/召回数。
- 旧 `memory.txt` 不会自动迁移进新库；remember 会双写，历史行仍可
  通过 recall 的兼容路径读取。
- 群聊按 Agent 共用记忆；没有按群隔离。
- 阈值按聊天条目数计，不是 token 百分比；默认 45 条。
- 记忆库为应用私有明文 SQLite，未加密；导出/清除通过记忆页操作。

## 4. 产物

- `dist/OrionekoX-0.52.0-alpha-arm64-x64.apk`（versionCode 5209），sha256
  `fdeb1c51c779bd9766ab7a6a4413ae4639511435d88aeff097c311982c4f0cff`
- `dist/OrionekoX-0.52.0-debug-arm64-x64.apk`，sha256
  `8609d8fae589ed741d1f7cda45d221e9ba64ac4e26beb695b0d3e1934a0ce3eb`
- 截图：`docs/screenshots/qa-0.52.0-memory-library.png`（统计行/清空）、
  `qa-0.52.0-memory-settings.png`（模式与阈值）
- 构建日志：`/tmp/ox-build-0520d.log`（最终，
  `BUILD SUCCESSFUL in 2m 20s`；此前 0520a 因 parseInt 参数编译失败，
  0520b/0520c 为功能与严格查证迭代）
