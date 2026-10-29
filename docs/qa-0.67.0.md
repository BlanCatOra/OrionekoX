# QA — alpha 0.67.0（暗色变体 + reduced-motion/可访问性 + 会话内搜索 v1.5）

- 版本：`0.67.0`，versionCode **6709**（`0.66.0` 为 6609）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-29 09:40–10:15（CST）
- 目标：0.67.0 首要——暗色变体、reduced-motion/可访问性清扫、会话内搜索
  条与命中导航（v1.5）。

## 1. 实现

1. **暗色调色板**：颜色常量由 `static final` 改为实例字段，`resolvePalette()`
   在 `onCreate` 按系统 night mode（`UI_MODE_NIGHT_MASK`）解析：
   - 亮色保持 0.66 原值；暗色 BONE `#141917`、PAPER `#1E2521`、
     INK `#EDE6D6`、MUTED `#9AA69D`、LINE `8C EDE6D6`、RIPPLE `18 EDE6D6`、
     MENU_PAPER `#202724`、CORAL `#E07A62`，SIGNAL 两套相同
     （`#C8EF6A`，单一信号色规则延续）。
   - 语义保持一致：INK 仍是"气泡底/正文"色 → 暗色下变为奶油色气泡 +
     深色文字（角色反转但层级不变）；状态栏/导航栏图标随 `darkPalette`
     去掉 LIGHT_* 标志。
   - 菜单卡 `0xFFF7F3EA` 字面量并入 MENU_PAPER。
2. **reduced-motion**：`animationsEnabled()` 读取系统
   `ANIMATOR_DURATION_SCALE`；两处 overlay 淡入与首渲染 fade 在动画关闭时
   直接落到终态。
3. **可访问性清扫**：为纯符号控件补 contentDescription——回复取消
   "Cancel reply"、附件移除 "Remove attachment"、附件按钮 "Attach image"、
   发送 "Send message"、header 搜索 "Search this chat"、rail 群/agent chip
   （名称）、菜单行（文案）、图片卡（caption 或 "Image"）；前一轮已有
   back/close 描述。
4. **会话内搜索 v1.5**：header 新增 ⌕ 按钮（描边胶囊，contentDescription
   "Search this chat"）；点击在 header 与 rail 之间展开搜索条（输入框 +
   "n/N" 计数 + ↑/↓ + ✕）。查询 150ms 防抖扫描**当前会话**的
   user/assistant 条目（大小写不敏感包含），命中按时间顺序存 ts；
   导航为循环切换，滚动到该行（顶部留 80dp）并在动画开启时给整行
   900ms SIGNAL 底色闪烁；行在 addUser（三路径）/addAssistant 时以 ts
   作为 tag，82 行渲染窗口外的命中不跳转（计数仍准确）。关闭清空
   查询与命中。

## 2. 设备验证（debug 6709）

1. **暗色**：`cmd uimode night yes` 重启进入 OxUI——炭黑背景、奶油墨泡/
   正文、SIGNAL 强调保留、选中 rail chip 奶油底深字、状态栏图标变亮色、
   ⌕/Agents/Telegram UI 三胶囊可辨；截图 `01-dark.png`。
2. **日间回归**：`cmd uimode night no` 重启后与 0.66 视觉一致，无颜色残留
   （截图 `05-light.png`）。
3. **reduced-motion**：`animator_duration_scale 0` 重启，打开 Agents 面板
   0.6s 内直接可见（无淡入），无 FATAL；恢复 scale=1。
4. **会话内搜索**：Sakura 会话点 ⌕ → 输入 "blue" → 计数 "1/2"、跳转到
   首条命中；点 ↓ → "2/2"，滚动到 assistant 命中且整行 SIGNAL 高亮闪烁
   （截图 `03-hit2.png`）；↑ 循环回 1/2；✕ 关闭并清空。
5. **可访问性**：uiautomator dump 确认 "Search this chat" / "Previous
   match" / "Next match" / "Close search" / "Attach image" /
   "Remove attachment" / "Send message" 等 content-desc 全部存在。
6. **构建/稳定性**：release 6709 启动无本包 FATAL/ANR，代理与无障碍保持。

## 3. 产物

- release：`dist/OrionekoX-0.67.0-alpha-arm64-x64.apk`，sha256
  `32a58b343168ad5bebaf4a3a32e80186ca1e7e2572c9bad937bd84276749c2d8`
- debug：`dist/OrionekoX-0.67.0-debug-arm64-x64.apk`，sha256
  `97c3c988eb96ac278df5cc626c496d4334ad99ac9580d487eb98cb65a56d25b1`
- 构建日志：`/tmp/ox-build-0670-final.log`

## 4. 边界 / 下一步

1. 暗色跟随系统 night mode，无应用内手动切换（需求未提，接口后续可加）。
2. 会话内搜索只扫当前会话、无跨会话聚合排序；82 行窗口外命中不跳转；
   图片等非文本条目不在检索范围。
3. reduced-motion 只覆盖 OxUI 的代码动画；原生 Telegram UI 由系统设置
   自行处理。
4. 可访问性为符号控件清扫，未做 TalkBack 全流程走查与焦点顺序优化。
5. 优先事项剩余：设置/Agent 编辑 OxUI 化（0.68 首要候选）。

## 5. 清理

- 本轮未创建群/未发送消息（搜索为只读）；无遗留 QA 数据；
  `interface_mode=telegram`、night mode=no、animator scale=1 均已恢复；
  未 pm clear。
