# QA — alpha 0.45.1（角色卡导入收拢到 Agent 列表）

- 版本：`0.45.1`，versionCode **4519**（`0.45.0` 为 4509）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 13:24–13:39（CST）
- 目标：修正 0.45.0 与既有功能的重叠，把 PNG/JSON 角色卡能力合并到
  0.8.114/0.8.115 已有的 Agent 列表导入入口，并让 RikkaHub 设置页恢复
  只处理备份。
- 版本规则：这是导入入口与实现的小范围收拢修复，没有新增独立功能，
  按 patch 从 0.45.0 → **0.45.1**，versionCode 4519（451×10+9）。

## 1. 背景与问题

- 0.8.114 已在 Agent 列表提供“导入角色卡（SillyTavern JSON）”，
  0.8.115 又补了未压缩 PNG `tEXt` 的 `chara`/`ccv3` 读取。
- 0.45.0 在 RikkaHub 设置页又做了一套 PNG/JSON 角色卡导入，虽然新增了
  `iTXt`、PNG 头像、稳定 id 和原生开场白，但形成两个入口与两份解析逻辑；
  Rikka 设置页的“备份导入”也被混入了角色卡职责。
- 0.45.1 的处理：保留 Agent 列表作为唯一角色卡入口；把 0.45.0 的增强
  合并进去；Rikka 设置页恢复备份专用，并明确把角色卡请求指回 Agent
  列表。

## 2. 实现

- Agent 列表“导入角色卡（SillyTavern PNG/JSON）”的 `REQ_CARD` 处理改为：
  1. `OxRikkaImporter.preview` 确认文件确实是角色卡；
  2. `OxRikkaImporter.importFrom` 执行导入（稳定 id、人设六节、`first_mes`、
     PNG 头像、`iTXt` 支持）；
  3. 读取结果中的 Agent 名称并弹 Toast；
  4. `syncAgents(true)` 生成原生欢迎消息并刷新列表。
- 删除 `OxAgentsActivity` 中旧的 `extractCardJson` / `appendCardField` 与
  随机 id、名称“· 副本”逻辑，避免两份实现漂移。
- RikkaHub 设置页：
  - 入口文案恢复为“从 RikkaHub 备份导入”/`settings.json 或备份 zip`；
  - 若选中的是角色卡，预览阶段直接拒绝并提示
    “角色卡请从智能体列表的‘导入角色卡’进入”，不会写入任何数据；
  - 备份 zip / `settings.json` 的预览与导入路径不变。
- 删除不再使用的角色卡预览字符串，中文包 103 → 104。

## 3. 构建

- 首次构建失败：英文提示 `agent list's card entry` 中的单引号触发 AAPT2
  `Invalid unicode escape sequence`。改为不带所有格的
  `agent list card entry` 后重新构建成功。
- 最终日志：`BUILD SUCCESSFUL in 1m 57s`，`EXIT:0`。

## 4. 设备验证 — PASS

### 4.1 Agent 列表 PNG 导入

- 通过 Agent 列表底部“导入角色卡（SillyTavern PNG/JSON）”选择 8,285 B
  的 64×64 PNG（`tEXt` / `chara` / base64 V2 JSON）。
- Toast：“已导入角色卡：QA Card 0451”。
- 生成 `rikka_card_b303c8ed`（tgId 555000094），`avatar.png` 的 sha256
  `33f7346e530b41dc68b8507a986774cdab5d88c47afa5fb16b5ee5da4cbb3481`
  与源 PNG 完全一致；`first_mes` 为“0.45.1 卡片开场白。”。
- 原生聊天显示该开场白，头像为卡片图；无重复气泡。

### 4.2 JSON 同卡更新（幂等）

- 同一张卡的 346 B JSON 再次从 Agent 列表导入，Toast 仍为
  “已导入角色卡：QA Card 0451”。
- 目录仍只有 `rikka_card_b303c8ed`，没有 `· 副本`；`agent.json` mtime
  更新到 13:35:03，`avatar.png` hash 保持不变。
- 原生聊天仍只有一条开场白，说明更新没有重建会话。

### 4.3 RikkaHub 入口边界与备份回归

- 在 Ox 设置“从 RikkaHub 备份导入”中选择同一张 PNG：提示
  “RikkaHub 导入失败：角色卡请从智能体列表的‘导入角色卡’进入”，
  未新增 Agent、未改动已有卡片。
- 297 B 的最小 RikkaHub 备份 zip（1 个 assistant、无 Provider）仍可预览
  “助手 1 个”并成功导入，生成 `rikka_regress451`，提示按离线模式导入，
  证明收拢没有破坏原备份路径。

### 4.4 稳定性与清理

- 普通导入、同卡更新、拒绝路径、备份回归全程无本包 FATAL/ANR。
- 通过 Agent 列表长按删除两个测试 Agent；`dialogs`/`messages_v2` 对
  tgId 555000094 和 555000095 的查询均为 0。
- 删除 Download 中 3 个测试文件；未执行 `pm clear`，未删除其他 Agent
  或用户数据，未给真实联系人发消息。
- 重新安装 release 4519 后已恢复
  `org.telegram.ox.OxAccessibilityService`，留在 release 运行。

## 5. 未覆盖 / 已知边界

- 与 0.45.0 相同：不支持压缩 `zTXt`；只导入 `first_mes`，未导入
  alternate greetings、character_book、tags 或正则；角色卡只导入不导出；
  导入后默认离线；未与真实 SillyTavern/RikkaHub 导出的卡互测。
- Agent 列表导入没有单独的预览对话框；确认由文件选择器 + 结果 Toast
  完成（与 0.8.114 的行为一致）。

## 6. 产物

- `dist/OrionekoX-0.45.1-alpha-arm64-x64.apk`（versionCode 4519），sha256
  `8b68ee503af36cc03e483834126a6b77e4d2b2022f6a73af4d709cb4e4a91757`
- `dist/OrionekoX-0.45.1-debug-arm64-x64.apk`，sha256
  `8b6d9760270e5fcdda028f81c7ef42ec819db441eb587f717c04fb3386f4381d`
- 截图：`docs/screenshots/qa-0.45.1-card-import.png`、
  `qa-0.45.1-card-update.png`、`qa-0.45.1-card-chat.png`
