# QA — alpha 0.45.0（RikkaHub / SillyTavern PNG/JSON 角色卡导入）

> 修正说明（0.45.1）：Agent 列表自 0.8.114/0.8.115 起已有角色卡导入。
> 0.45.0 在 RikkaHub 设置页重复提供了入口；0.45.1 已把增强合并回 Agent
> 列表并把 RikkaHub 行恢复为备份专用。详见 `docs/qa-0.45.1.md`。

- 版本：`0.45.0`，versionCode **4509**（`0.44.0` 为 4409）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 12:50–13:18（CST）
- 目标：让“从 RikkaHub 备份导入”同时接受 SillyTavern / RikkaHub 的
  PNG/JSON 角色卡，把角色人设、开场白与 PNG 头像导入为本地智能体。
- 版本规则：新增一种外部导入格式和对应 UI 行为，按 minor 从 0.44.0 →
  **0.45.0**，versionCode 4509（450×10+9）。

## 1. 实现

### 1.1 格式识别

- `OxRikkaImporter.readArchive` 先读取文件签名：
  - ZIP：继续按 RikkaHub 备份读取 `settings.json`、`skills/`、DB 与 `upload/`；
  - PNG：解析 `tEXt` / `iTXt` 块中的 `ccv3`（优先）或 `chara` 载荷，
    base64 或原始 JSON 均可；
  - JSON：先按 RikkaHub settings 校验；不含 `assistants`/`providers`/
    `mcpServers` 时再尝试角色卡结构。
- 支持 V2/V3 包装（`spec` + `data`）与扁平 V1 卡；V1 回退字段包括
  `char_name`、`char_persona`、`world_scenario`、`char_greeting`、
  `example_dialogue`。
- 非法 PNG / JSON 统一报“这个 PNG/JSON 里没有可用的角色卡数据”。

### 1.2 字段映射

- `description`、`personality`、`scenario`、`system_prompt`、
  `post_history_instructions`、`mes_example` 依次写入 `AGENTS.md` 的分节；
  单字段最多 32 KiB，提示词总长最多 64 KiB。
- `first_mes` 写入合成的 `presetMessages`，沿用现有导入器映射到
  `OxAgent.firstMes`；导入完成 `syncAgents(true)` 后生成原生欢迎消息。
- PNG 文件原样保存为 Agent 目录下的 `avatar.png`，通过合成 `upload/`
  条目接入现有头像导入逻辑；已有头像不覆盖。
- 稳定 id 为 `rikka_card_<hash(name, description, first_mes)>`，重复导入
  更新同一个 Agent，不产生副本。
- 安全上限：PNG ≤ 8 MiB、文本载荷 ≤ 4 MiB；IHDR 宽高各 ≤ 4096 且总像素
  ≤ 16,000,000，避免解码炸弹。压缩的 `zTXt` 不支持。

### 1.3 UI

- Ox 设置入口改为“从 RikkaHub 备份 / 角色卡导入”，副标题列出
  `settings.json、备份 zip 或 PNG/JSON 角色卡`。
- 角色卡预览只显示“角色卡 1 张”、名称、已有卡提示和卡片说明，不再显示
  MCP/技能为 0 的通用统计；确认框标题包含“角色卡”。
- 导入结果保留“新建/更新助手”等计数，并提示卡片以离线模式导入、
  Provider 可在编辑器中接线。
- 中文包版本 100 → 103，确保升级后新文案立即生效。

## 2. 设备验证 — PASS

### 2.1 JSON 角色卡

- 689 B V2 卡 `QA Card 0450`：预览“角色卡 1 张 / 助手：QA Card 0450”，
  无头像提示；结果为“新建助手 1，更新 0”。
- 生成 Agent `rikka_card_df478b83`，tgId 555000092；`agents_md` 494 B，
  含 Description / Personality / Scenario / System prompt /
  Post-history instructions / Example dialogue 六节；
  `first_mes` 为“卡片开场白：我在这里，桌上只有一本笔记。”；
  `avatar_path` 为空，按离线模式导入。
- 原生聊天显示该开场白，标题为“🤖 QA Card 0450”。

### 2.2 PNG 角色卡与头像

- 8,810 B PNG（64×64，`tEXt` keyword `chara`，base64 V2 JSON）导入后
  更新同一 Agent：结果“新建 0，更新 1”，`avatar.png` 8,810 B，sha256
  `4482184da9675ca2eef91364c4c21ed22dd106e0509cd29327e23b38d7b8d5e2`，
  与源 PNG 一致；头像缓存也生成 240/800 两档圆图。
- 再次导入同一 PNG：仍为 1 个 Agent 目录、`avatar.png` hash 不变、
  原生聊天仍只有一条开场白，无重复消息或副本。
- 最终 0.45.0 构建复验：预览显示“已存在这张角色卡，将更新”，
  确认后“更新 1”，头像 hash 不变。

### 2.3 JSON-only 卡与错误路径

- 336 B JSON-only 卡 `QA Card JSON 0450`：预览无头像提示；结果为
  “新建 1”；生成 `rikka_card_1ac480d6`（tgId 555000093），
  `avatar_path` 为空，原生聊天显示“JSON 角色卡开场白。”。
- 69 B 无 `chara` 块的 PNG：导入失败并显示
  “RikkaHub 导入失败：这个 PNG/JSON 里没有可用的角色卡数据”。

### 2.4 稳定性与清理

- 导入/更新/重复导入/错误路径全程无本包 FATAL/ANR。
- 通过 Agent 列表长按删除两个测试 Agent；删除后
  `dialogs`/`messages_v2` 对 tgId 555000092/555000093 的查询均为 0。
- 删除 Download 中的 4 个测试卡文件；未执行 `pm clear`，未删除其他
  Agent 或用户数据，未给真实联系人发消息。
- 重新安装 release 4509 后已恢复
  `org.telegram.ox.OxAccessibilityService`，留在 release 运行。

## 3. 未覆盖 / 已知边界

- 仅支持未压缩的 `tEXt` / `iTXt`；`zTXt` 压缩角色卡会报无数据。
- 只导入 `first_mes`，未导入 `alternate_greetings`、`character_book`
  （世界书）、tags 或正则脚本。
- 角色卡只导入、不导出；没有 JSON 内嵌头像 URL/data URL 的解析。
- 导入后默认离线，需在 Agent 编辑器接 Provider 才能对话。
- 用合成的 V2 卡验证，尚未与真实 SillyTavern / RikkaHub 导出的卡互测；
  未在真机/GPU 环境复验。

## 4. 产物

- `dist/OrionekoX-0.45.0-alpha-arm64-x64.apk`（versionCode 4509），sha256
  `6e3e9d4905490d566e775e0d3c6215d61002e918ad818149a7112d669892e4b2`
- `dist/OrionekoX-0.45.0-debug-arm64-x64.apk`，sha256
  `f6da9de9957057cd4c2a61716839726e9b75c48cfbd65b3272171a133b1ddf08`
- 截图：`docs/screenshots/qa-0.45.0-card-preview.png`、
  `qa-0.45.0-card-result.png`、`qa-0.45.0-card-chat-png.png`、
  `qa-0.45.0-card-chat-json.png`
