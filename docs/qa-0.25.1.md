# QA — alpha 0.25.1（人设推荐确认与撤销）

- 版本：`0.25.1`，versionCode **2519**（`0.25.0` 为 2509）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 03:06–03:16（CST）
- 目标：为 0.22.0 的人设推荐补上“应用前确认”和“撤销上次推荐”，避免一键
  覆盖用户现有外观后无法恢复。
- 版本规则：只补既有推荐的确认/撤销交互，按 patch 从 0.25.0 → **0.25.1**，
  versionCode 2519（251×10+9）。

## 1. 新增能力

- 点击“从人设推荐外观”先弹出原生确认框：
  “根据当前 Agent 的名称与 AGENTS.md，建议使用「X」。是否应用？”
  取消不修改任何状态。
- 应用前把当前作用域快照备份到
  `files/ox_recommend/<scope>.json`（global / agent_… / dialog_…）；
  重复推荐不会覆盖第一份备份。
- 应用后页面出现“撤销上次推荐”行；点击恢复备份快照并删除备份文件，toast
  “已恢复推荐前的外观”。
- 撤销行只在当前作用域存在备份时显示；备份按作用域隔离。

## 2. 设备验证 — PASS

- 打开 `barB-nokey` 的 Agent 会话外观，点击推荐：确认框显示
  “建议使用「赛博霓虹」”。
- 应用后生成 `agent_555000016.json` 与
  `files/ox_recommend/agent_agent_555000016.json`，“撤销上次推荐”行出现。
- 点击撤销：toast“已恢复推荐前的外观”，页面恢复图片背景
  `ox_chat_bg_preset_…img`、背景不透明度 80%、暗化 24%、AMOLED 关闭；
  `ox_recommend` 文件被删除。
- 截图：`docs/screenshots/qa-0.25.1-recommend-dialog.jpg`、
  `qa-0.25.1-undone.jpg`。
- 测试后删除测试生成的 Agent 覆盖文件；无 FATAL/ANR。

## 3. 构建问题记录

- 首次构建失败：英文 `OxChatRecommendMessage` 使用 `agent's` 未转义撇号，
  AAPT2 报 “Invalid unicode escape sequence / does not contain a valid string
  resource”；改为 `agent name` 后重建成功。没有把失败构建当作产物。

## 4. 未覆盖 / 已知边界

- 撤销恢复的是“推荐前快照”，会写成当前作用域的显式覆盖（Agent 文件仍存在），
  不是删除覆盖、回到“跟随上层”；需要彻底回退时用“清除覆盖并跟随上层”。
- 只有一层撤销/无重做；备份按作用域保存，离开页面后仍可撤销。
- 推荐入口仍只在 Agent 作用域；确认框的取消按钮未单独截图（原生 AlertDialog）。
- 没有候选主题并排预览，仍只显示单个推荐结果。

## 5. 产物

- `dist/OrionekoX-0.25.1-alpha-arm64-x64.apk`（versionCode 2519），sha256
  `6d34eb6bb7d0f6aaaea0040dadad8a6551d40afdecdd5aebec6ea487645858be`
- `dist/OrionekoX-0.25.1-debug-arm64-x64.apk`，sha256
  `dae337e6a87057a51e45367afeafa419d6d01ad7409842dffd6f7ae484a352dc`
