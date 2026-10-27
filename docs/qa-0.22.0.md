# QA — alpha 0.22.0（按 Agent 人设推荐外观）

- 版本：`0.22.0`，versionCode **2209**（`0.21.2` 为 2129）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 02:00–02:12（CST）
- 目标：补齐“按人设推荐外观”缺口：在 Agent 外观页根据名称与 AGENTS.md
  关键词选择一款内置主题，一键套用到当前 Agent 作用域。
- 版本规则：新增一项可见的个性化能力，按 minor 从 0.21.2 → **0.22.0**，
  versionCode 2209（220×10+9）。

## 1. 新增能力

- `OxChatTheme.recommendBuiltIn(persona)`：对名称 + AGENTS.md 做小写关键词
  评分，返回六款内置主题之一；无命中时默认“深海”。
- 关键词：
  - 午夜墨黑：墨黑/极简/黑/暗/amoled/midnight/dark
  - 纸墨：纸/书/墨/文/历史/古典/诗/paper/ink/book
  - 深海（默认）：深海/海洋/海/水/蓝/冷静/科技/代码/程序/blue/ocean/code
  - 樱花：樱花/樱/粉/少女/可爱/恋爱/甜/sakura/pink
  - 森林：森林/自然/植物/树/叶/治愈/green/forest/茶
  - 赛博霓虹：赛博/霓虹/未来/cyber/neon/深夜/酒吧/调酒/夜店
- Agent 外观页新增“从人设推荐外观”行（仅 Agent 作用域显示）；点击调用
  `applyBuiltIn` 写入当前 Agent 快照，toast 显示推荐主题名。
- 评分与选择完全本地、确定性，不调用模型或网络。

## 2. 设备验证 — PASS

- 打开 `barB-nokey` 的“Agent 会话外观”，点击“从人设推荐外观”：
  - toast：“已套用推荐外观：赛博霓虹”；
  - 页面立即变为渐变背景 `#0A0014 → #1B0033`、收气泡 `#1B0033`、
    发气泡 `#7C00FF`、文字 `#00F0FF`、洋红 `#FF2D95` 2dp 边框、8dp 圆角；
  - 生成 `files/ox_chat_agents/agent_555000016.json`，内容与赛博霓虹一致。
- 截图：`docs/screenshots/qa-0.22.0-recommend.jpg`。
- 测试后删除该测试覆盖文件，Agent 恢复为跟随全局；未修改其他 Agent。
- 无 FATAL/ANR。

## 3. 未覆盖 / 已知边界

- 关键词启发式不是语义理解；人设不含上述词时统一落到“深海”。
- `applyBuiltIn` 会覆盖整个快照，包括背景图、字体、模糊、行高等；用户已有
  自定义会被替换（可用“清除覆盖/跟随上层”或重新设置恢复）。
- 入口只出现在 Agent 作用域，不适用于全局或按会话作用域。
- 未提供“推荐前预览候选/撤销上一步”，也未见测试英文人设与极短人设。
- 当前只覆盖六款内置主题，不涉及在线主题市场。

## 4. 产物

- `dist/OrionekoX-0.22.0-alpha-arm64-x64.apk`（versionCode 2209），sha256
  `50c97563bd68c93e9d02e63691d5487f03b01552e68ccbd92be3c53d64b3a093`
- `dist/OrionekoX-0.22.0-debug-arm64-x64.apk`，sha256
  `de3f88606aa30b881a721cad64e6f862792d6877f421f133f5d19c897c4b63d1`
