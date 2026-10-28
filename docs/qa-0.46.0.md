# QA — alpha 0.46.0（Chatbox 备份携带技能与技能关联）

- 版本：`0.46.0`，versionCode **4609**（`0.45.1` 为 4519）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 13:47–14:07（CST）
- 目标：让 Chatbox / Kelivo 备份同时携带 `SKILL.md` 技能库和每个 Agent 的
  技能关联；并在回导时恢复两者。
- 版本规则：新增用户可见的备份内容（技能文件与关联）及导入恢复行为，
  按 minor 从 0.45.1 → **0.46.0**，versionCode 4609（460×10+9）。

## 1. 实现

### 1.1 导出

- 全量导出收集可见技能（应用私有 `oxskills` 与 Download/Operit 技能），
  写入 `skills/<id>/SKILL.md`；选中 Agent 导出只写这些 Agent 引用到的
  技能，避免把无关技能带进单次分享。
- 上限：最多 50 个技能、单个 512 KiB、合计 8 MiB；超限跳过并记录。
- 有聊天/无聊天的 Agent 都写私有字段 `orionekoSkillIds`，只包含本次
  实际写入 zip 的技能 id，避免回导后指向缺失文件。
- 同时给 copilot 写入 `orionekoAgentId`（0.43.2 只给 session 写了）。
  这是本轮 QA 中发现的真实问题：copilot 回导时因缺少原 id，被重新加上
  `kelivo_` 前缀并生成重复 Agent。
- `Result.skills` / `Result.skillLinks` 统计技能数与关联数；导出 Toast
  增加“技能 N 个”。

### 1.2 导入

- 先导入 `skills/`（确保后续关联有文件可指），再处理助手与对话。
- copilot 与 session 都读取 `orionekoSkillIds`；只保留本次实际导入成功
  的技能 id，写入 `OxAgent.skillIds`。没有该字段的外部备份保持原行为。
- copilot 的 `orionekoAgentId` 优先于历史 `kelivo_` 前缀规则；没有该字段
  仍按旧规则生成新 id。
- 预览新增“将恢复 N 个智能体的技能关联（覆盖现有）”；结果新增
  “已恢复 N 个智能体的技能关联”。

### 1.3 文案

- 导出说明、确认框、全量/单 Agent Toast 都加入“技能”。
- 中文包 104 → 105。

## 2. 设备验证 — PASS

测试对象（自建）：

- 技能 `QASkill0460`，90 B `SKILL.md`，sha256
  `6cf9bd7053c026c05d864352f372cfe2dc6e73dc7506a0d71f06444e5a3f25c7`。
- `QA Skill Session 0460`（带 2 条聊天，session 路径，tgId 555000100）。
- `QA Skill Copilot 0460`（无聊天，copilot 路径，tgId 555000098）。
- 两者 `skill_ids=["QASkill0460"]`。

### 2.1 导出

- 多选两个 Agent 导出，zip 1,762 B，Toast：
  “已导出 2 个智能体（含记忆 0 个、技能 1 个）、1 段对话、2 条消息
  （0 个文件）”。
- zip 内容：`manifest.json`、`settings.json`、`copilots.json`、
  `sessions/qa_skill_0460_s/session.json`、
  `skills/QASkill0460/SKILL.md`。
- `copilots.json` 同时含 `id`、`orionekoAgentId`、
  `orionekoSkillIds=["QASkill0460"]`；session 含
  `orionekoAgentId` 与 `orionekoSkillIds`。
- 技能文件与源文件 sha256 一致。
- 扫描 `apiKey` / `api_key` / `baseUrl` / `base_url` / `Authorization` /
  `sk-` / `Bearer ` / `ox1:`：0 命中。

### 2.2 删除后回导

- 删除技能目录，并把两个 Agent 的 `skill_ids` 清空（保留 tgId）。
- 预览：“助手 1 个 / 对话 1 段（2 条消息）”“已存在 1 个，将更新”
  “将恢复 2 个智能体的技能关联（覆盖现有）”“将导入 1 个技能”。
- 结果：“新建助手 0，更新 1”“已恢复 2 个智能体的技能关联”
  “导入技能 1 个”。
- 技能文件恢复，sha256 与源文件一致。
- 两个 Agent 目录仍是原 id，没有新增 `kelivo_...` 副本；tgId 仍为
  555000100/555000098；`skill_ids` 均恢复为 `["QASkill0460"]`。
- 再次导入同一 zip：仍为 2 个目录、同一技能 hash、更新 1，无重复。

### 2.3 稳定性与清理

- 导出、回导、重复导入全程无本包 FATAL/ANR。
- 通过 Agent 列表删除两个测试 Agent，删除自建技能与 Download 测试文件；
  `dialogs`/`messages_v2` 对 555000098/555000100 均为 0。
- 未执行 `pm clear`，未删除其他 Agent 或用户数据，未给真实联系人发消息。
- 重新安装 release 4609 后恢复无障碍服务，留在 release 运行。

## 3. 未覆盖 / 已知边界

- `skills/` 与 `orionekoSkillIds` / `orionekoAgentId` 是 OrionekoX / Chatbox
  扩展布局；真实 Chatbox / Kelivo 客户端是否忽略或保留未实测。
- 只导出 `SKILL.md` 正文，不含技能目录下的脚本、附件或可执行内容。
- 全量导出会携带所有可见技能（即使没有被任何 Agent 引用）；选中导出
  只携带被引用技能。
- 没有技能附件的引用关系；没有技能市场或远程同步。
- 单 Agent 导出的 Toast 已加入技能计数，但本轮截图验证的是多选导出。

## 4. 产物

- `dist/OrionekoX-0.46.0-alpha-arm64-x64.apk`（versionCode 4609），sha256
  `ffb1167497905baac91081a67266f6e3f86bea0cb5563b29a7f8dd28370f402e`
- `dist/OrionekoX-0.46.0-debug-arm64-x64.apk`，sha256
  `df097cf624ec220dcbac0d8e453c02e13e54c95848de478a1ab7739a6c768530`
- 截图：`docs/screenshots/qa-0.46.0-export-toast.png`、
  `qa-0.46.0-import-preview.png`、`qa-0.46.0-import-result.png`
