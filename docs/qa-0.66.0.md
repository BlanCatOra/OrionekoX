# QA — alpha 0.66.0（视觉遗留 5 项 + OxUI 会话搜索）

- 版本：`0.66.0`，versionCode **6609**（`0.65.0` 为 6509）
- 设备：emulator-5554（1080×2400，Android 14/34）
- 时间：2026-10-29 04:05–09:40（CST）
- 目标：0.66.0 首要——消化视觉三轮评审遗留 5 项（自定义长按菜单替换
  AlertDialog、用户气泡时间戳+长按、类型尺度吸附、附件行容器、竖长截图
  卡片渲染异常定位）+ OxUI 会话搜索（面板级消息内容检索）。
- 策略变更：用户 2026-10-29 指示模型切回 opendesign/deepseek-v4.1-flash、
  取消多并发子代理策略；本轮起单主线推进，automation prompt 与 memory
  已同步。

## 1. 实现

1. **自定义长按菜单**（替换三处系统 AlertDialog / 直接 copy）：
   - `menuHost` 透明全屏 FrameLayout 插在 root 与 overlay 之间，点击空白
     关闭；卡片 = plate(0xFFF7F3EA, 16, LINE 1dp) + elevation 12dp，宽
     min(280dp, 屏宽−48dp)，行高 48dp、padding 18/14dp、15sp
     sans-serif-medium INK、Ripple 0x1814211C，行间 1dp LINE 内缩 16dp；
     Reply 行带 dp(7) SIGNAL 圆点。
   - 锚定：`getLocationOnScreen` 差值定位锚点，优先气泡上方（8dp 间隙），
     不够则下方并钳制 16dp 边距；长按前 `hideKeyboard()`。
   - 生命周期：返回键先关菜单；`renderConversation` 重建时自动关菜单；
     `hideOverlay` 不清菜单（菜单在 overlay 之下）。
   - 群 assistant 行 = Copy + Reply <name>；1:1 assistant 行（依原逻辑
     直接 copy）与所有 user 气泡 = 仅 Copy。
2. **用户气泡时间戳**：普通与 quote 两个分支都在气泡下方追加右对齐
   11sp mono MUTED `time(ts)`（发送中不显示）。
3. **类型尺度吸附**（子代理审计表 52 站点 → 30 处修改，全部向
   11/12/14/16/17/28 吸附）：mono 系统一 11（助手 meta/详情 meta/工具块/
   页脚）、chip 与副标题 12、列表/详情正文 14、用户气泡与输入框 16、
   助手正文 17 serif、大标题 28 serif、× 关闭 24；新增 RIPPLE /
   SENDING_ALPHA 常量与 sansMedium() helper（rail chip 两处内联
   Typeface 收敛）。
4. **附件行容器**：attachRow 改为 plate(PAPER, 14, LINE 1dp) 胶囊
   （padding 10/8dp），缩略图 + 两行文本（文件名 12sp + `WxH · KB`
   11sp mono 副行），✕ 保持 20sp。
5. **会话搜索 v1（按设计注实现）**：面板搜索框 hint 改
   “Search agents and messages”；`scanMessages` 在 worker 线程扫描
   `OxAgentStore.loadChat` 与 `OxGroupStore.loadChat`（仅 user/assistant
   角色，每目标 ≤3 条、全局 ≤12 条，新→旧），220ms 防抖 + `scanGen`
   代数丢弃过期结果；命中渲染 “Messages” mono 小节 + 行（徽章、目标名
   12sp medium MUTED、摘录 14sp INK 带 SIGNAL 底色高亮命中子串、右侧
   mono 时间），点击打开对应 1:1/群；空态仅在 agent/群/消息全部无命中时
   出现；`OxUiNoResults` 文案改为 Nothing matches this search.；新增
   `OxUiSearchAll`/`OxUiSearchMessages`，中文包 124→125。纯本地扫描，
   零网络。
6. **竖长截图卡片异常定位结论**：0.65 报告中“图文割裂”为**读图误判**，
   非渲染 bug。设备 dump 证实 ImageView (377,781)-(917,1981) 与 caption
   (377,1981)-(1022,2099) 左边缘对齐、垂直零间隙——caption 本就在同一张
   ink 卡内；视觉上“像独立气泡”是因为 caption 行比图片宽。
   同时做一处观感修正：`card.setGravity(CENTER_HORIZONTAL)` 让图片在
   更宽的卡内水平居中，不再贴左边缘。

## 2. 设备验证（真实 UI 流，debug 6609）

1. **1:1 用户气泡**：时间戳 “17:52” 右对齐显示；长按弹出自定义菜单卡片
   （bone 底、圆角描边、Copy 单行），点击空白关闭 ✓。
2. **会话搜索**：面板输入 “blue” → “Messages” 小节出现两条 Sakura Waifu
   命中，命中词以 SIGNAL 底色高亮、右侧 mono 时间、上下文省略号正确；
   点击命中行可打开对应会话（逻辑复用既有 select）。
3. **附件胶囊**：选图后胶囊显示缩略图 + `img_…png` + `1080x2400 · 302 KB`
   副行；发送后进入统一图文卡；竖长图（576×1280 内容）高度受 0.6 视口
   上限约束、CENTER_CROP 裁切、caption 位于同卡底部（bounds 对齐验证）。
4. **群聊菜单**：长按 echo 回复 → 自定义菜单 `Copy / ● Reply Echo Assistant`
   （SIGNAL 圆点）；点 Reply → chip `Reply → Echo Assistant · 「⚠ echo
   mode: no model ba…」`（引用摘要 24 字）；发送 TARGET-660Q → logcat
   `groupReply forced agentId=echo dialog=-1800000004`，气泡上方渲染
   `↩ Echo Assistant: ⚠ echo mode: no model…` 引用行，echo 回复到达。
5. **类型尺度**：助手正文 17sp serif、meta/页脚 11sp mono、列表副标题
   12sp、头部双胶囊等高，与审计表一致；无回归。
6. **构建/稳定性**：release 6609 启动无本包 FATAL/ANR，代理 10.0.2.2:7890
   保持，无障碍重新绑定。

## 3. 产物

- release：`dist/OrionekoX-0.66.0-alpha-arm64-x64.apk`，sha256
  `f4f3a1bf985fc07f7af369ab335adde20c3370c7ceb371c6198f83892bc121f0`
- debug：`dist/OrionekoX-0.66.0-debug-arm64-x64.apk`，sha256
  `1bda0bd9ee635b6d2ece7b932bc86b1cd48c9f39a623050d340af9beb9008881`
- 构建日志：`/tmp/ox-build-0660-final.log`

## 4. 边界 / 下一步

1. 会话搜索 v1 为面板级消息检索（≤12 条、时间序），**不含**会话内搜索条、
   命中处上下导航、图片/文件名检索（设计注 B7/B8 列为 v1.5）。
2. 菜单卡片为 v1 固定两行；>5 行时需包 ScrollView（当前场景不会出现）。
3. 类型尺度审计中“小节标题 18 档悬空”（Groups/Memory 用 mono-11 标签
   惯例）保持现状，未强行引入 18sp。
4. 竖卡“异常”已澄清为误判——0.65 文档不回改，以本节结论为准。
5. 视觉/交互剩余：暗色变体、reduced-motion、平板双栏、可访问性清扫、
   会话内搜索、设置/Agent 编辑 OxUI 化。
6. 本 rundown 中曾出现一次自造回归：类型尺度批量替换误把 `sansMedium()`
   方法体替换为自调用导致 StackOverflowError（启动即崩），已修复并作为
   教训记录（批量替换必须排除定义处）。

## 5. 清理

- QA 群 OxUI POL 660（1800000004）dialogs/messages/chats/chat_settings
  计数 0，ox_groups 键移除；Sakura（op_operit_1754460057）chat.json 仅
  移除本轮 QA 条目（保留 10-28 既有两条）；oxui_media 与
  /sdcard/Pictures/tall660.png 删除；interface_mode 恢复 telegram；
  未 pm clear。
