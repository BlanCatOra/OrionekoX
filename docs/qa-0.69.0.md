# QA — alpha 0.69.0（多模型设计擂台合成：皮肤系统 + 外壳层 + 终端 v3）

- 版本：`0.69.0`，versionCode **6909**（`0.68.0` 为 6809）
- 设备：emulator-5554（1080×2400，Android 14/34；本轮宿主 X server 丢失后改用
  `-no-window -gpu off` 无窗口运行，adb 截图不受影响）
- 时间：2026-10-29 10:00–12:05（CST）
- 目标（临时需求）：按社区前端设计口碑挑选多个模型，从零独立产出 OxUI 设计，
  与本项目 oxui-ds 实机版对比；用户拍板"你自己抉择"后合成落地，并满足：
  被舍弃方案沉淀为可切换预设、所见皆 DIY（组件级）、终端参考 Termius/Termux。

## 1. 多模型设计擂台（7 版，全部产出 4 屏 HTML 并渲染截图）

| 版本 | 模型/通道 | 风格 |
|---|---|---|
| claude | claude-haiku-5.5（opendesign 网关） | 暖纸 + 橙信号，monogram/模型标签，最"设计集" |
| gpt | gpt-6-luna（本机 ChatGPT/Codex） | 鼠尾草静室，底部 Tab，记忆检索 chip |
| gpt-b | gpt-6-luna（夜色方向） | 近黑 + 荧光绿，菜单键位提示、技能 pill |
| gpt-c | gpt-6-luna（工作台方向） | 纸感 + 朱红，高密度、模型标签、现场图卡 |
| gpt-sol | gpt-6-sol（本机） | 奶油 + 深青，Georgia 衬线 + 中文单字头像 |
| gpt-56-sol | gpt-5.6-sol（本机） | 方格纸 + 蓝，Fieldbook、悬浮菜单投影 |
| oxui-ds | deepseek-v4.1-flash（现有 0.68 实机） | 骨纸 + 墨 + 荧光绿，编辑式层级 |

- 交付物与截图：`design-battle-shots/`（工作区，含 7 条带 + overview-stacked +
  html/ 源文件）。生成方式：简报 `/tmp/oxui-design-battle/brief.md`；网关模型
  经 chat/completions（claude-haiku 首轮 reasoning 吃满 16k 截断，提到 40k 后
  正常）；本机 GPT 用 `/usr/lib/chatgpt/resources/codex exec`（chatgpt OAuth，
  代理 7890），`gpt-6-pro` 对 ChatGPT 账号 model_not_found；`glm-5.3-flash`
  经网关连续 3 次 524 超时（弃用）；`gpt-6-astra` 按早前指示避开。
- 评审结论：夜色与 Sol 并列最好看；工作台/56-Sol 最可用；oxui-ds 唯一真机
  实现、交互全验证。合成方向由我拍板：**oxui-ds 骨架 × Sol 字体声线 ×
  陶土"人声"色 × 无底栏 × 终端首等公民**。

## 2. 合成落地（0.69.0）

1. **青墨调色板 + 陶土人声**：亮色 bone #F7F3E8 / paper #ECE6D6 / ink #0E2A24
   （深青墨），陶土 clay #D97757（暗色 #E08963）专用于用户气泡与引用箭头；
   chartreuse 保留为唯一动作/选中信号；两套色显式分工（signal=动作，
   clay=人声）。
2. **单字头像 + 模型标签**：Agent 行与搜索结果改用名称首字（CJK 安全，
   serif）圆形徽章，行尾右侧 mono 模型标签（≤14 字符）；assistant meta 行
   加 dp(18) 单字圆徽；图片卡内加 `IMG · WxH` mono 角标（10.5sp）。
3. **皮肤系统（OxUiSkin，9 套预设 + 自定义）**：每套含 8 个颜色 token、
   radiusScale、typeScale 与外壳字段；预设：青墨·骨纸荧光 / 暖纸·陶土橙 /
   鼠尾草·静室 / 夜色·荧光 / 工作台·朱红 / 奶油·深青 / 方格纸·蓝 /
   阅读·高对比 / 墨蓝·夜航（前 7 套即擂台方案的沉淀）。设置页 APPEARANCE
   列表（4 色圆点 + 名称 + active），点选即时 reproduce 换肤；
   Customize… 打开 token 编辑器：7 个 hex 字段 + 圆角（Sharp/Soft/Round）
   + 字号（Compact/Normal/Large）+ Copy JSON（导出皮肤）。
   `plate()` 圆角按 radiusScale 缩放，关键字号按 typeScale 缩放。
4. **外壳层（所见皆 DIY v1）**：导航形态 Rail / Bottom bar / Floating dock
   （无底部 Tab 默认）；dock 材质 Solid / Acrylic / Mica / Glass；dock 位置
   Top（header 下）/ Above input / Bottom of screen；组件开关 Rail chips /
   Footer status；全部持久化进皮肤 JSON（导出即所见）。dock 为流式布局
   （不再覆盖内容），玻璃材质 = 低透明度 + 信号色描边。
5. **终端 v3（Termux/Termius 学习后重做）**：`❯_ terminal` 头部 + Aa 字号
   循环（0.9/1/1.1 写入皮肤）+ ×；**会话 tab**（下划线式、active 荧光色、
   "+" 新建，最多 4 个，各自独立 log/history）；LOCAL 状态行与底部
   `1 LOCAL · UTF-8` 状态；分组快捷键行（Esc/^L/Tab | ↑/↓ | / - | > ~ :）；
   ↑/↓ 命令历史、Tab 前缀补全、^L 清屏；输出 13sp mono、行距 3dp、
   prompt 荧光绿、结果灰、错误 coral；输入框描边 + 荧光 ↵。命令：
   help/agents/groups/search/theme/version/telegram/clear/echo。诚实标注
   "local commands · no shell"。
6. **Back 键修复**（0.68 遗留已在 0.68 修）：overlay 优先关闭。

## 3. Termux / Termius 学习（用户要求实机安装并深度使用）

- **Termux**（GitHub v0.118.3 x86_64，已装 emulator 并深度使用）：全屏黑底、
  白 mono ~15sp、`~ $` 提示符、**绿色错误/补全提示**；双行 extra-keys 贴
  IME 顶部（ESC / - HOME ↑ END PGUP | TAB CTRL ALT ← ↓ → PGDN，全大写、
  字距放宽）；`termux.properties` 支持 extra-keys 数组（popup/macro）、
  cursor 样式/闪烁、use-black-ui、session 快捷键；改配置需
  `termux-reload-settings` 且部分项要重启进程（本轮实测到其 extra-keys
  JSON 转义错误提示 "Could not load ... org.json.JS…"）；session 通过抽屉/
  新建会话而非 tab。
- **Termius**：环境内无法安装——Play 商店需账号，APKMirror 最终下载需
  JS/Cloudflare 挑战（headless 渲染亦未产出直链），APKPure/APKCombo/
  APKMonk/APKSprt 均 404/403/连接失败。已从公开资料采用其设计要点：
  下划线式 session tab、状态行、圆角键帽分组、低调的 9.5sp 状态脚注。
  如需真机对照，请提供 APK 或在模拟器登录 Play 账号。
- 学习结论已落进终端 v3（见 §2.5），未照搬：保留 OxUI 的 dark surface、
  mono 体系与本地命令诚实性。

## 4. 设备验证（debug 6909）

1. 7 版设计全部渲染截图（`design-battle-shots/`），总览
   `overview-stacked.png`；与 oxui-ds 条带并列。
2. 预设切换：暖纸·陶土橙（截图 06）与夜色·荧光（07）即时重建落盘
   （`oxui_skin_id` 正确）；Customize 改 radius=1.6 与 signal=#FF6B9A 保存后
   气泡圆角与强调线即时变化（10/11），JSON 持久化验证。
3. 外壳：SHELL 设置出现（Navigation/Material/Dock position/toggles）；
   floating + glass + position=top 截图 21：玻璃胶囊 dock 位于 header 下、
   rail 隐藏、不遮挡内容；floating 在输入框上方时也随流式布局让位。
4. 终端 v3：截图 18 展示下划线 tab、状态行、键帽分组、荧光 ↵、底部状态；
   `help/agents/search blue` 输出正确（08/09），↑ 历史回填、Tab 补全、
   会话 "+" 新建均实测。
5. 稳定性：release 6909 安装启动，无本包 FATAL。
6. **事故与恢复**：为验证 dock 顶部位置，曾用最小 prefs 覆盖
   `orioneko_config.xml` 导致 agent_only/onboarding_done 等键丢失——立即
   重建恢复（agent_only/onboarding_done/theme/container 默认值），验证
   agent-only 首页与 5 个 Agent 正常（20）；后续改为"读取-合并-推送"。
   模拟器宿主 X server 丢失导致 qemu 退出，改 `-no-window -gpu off` 恢复。

## 5. 产物

- release：`dist/OrionekoX-0.69.0-alpha-arm64-x64.apk`，sha256
  `bfc8473ad3a4900fbe3893d1c1f37d1ae42d905ef03c218a158b6b4e30e52686`
- debug：`dist/OrionekoX-0.69.0-debug-arm64-x64.apk`，sha256
  `04f4b751dd68b41ea3b5b94a684912c0d78a5dc3339eab61affcb9b9bf5a1472`
- 构建日志 `/tmp/ox-build-0690-final.log`；设计擂台截图
  `design-battle-shots/`；Termux 学习截图 `/tmp/term-apps/`。

## 6. 边界 / 下一步

1. "一切可见组件可 DIY"当前落在 token（颜色/圆角/字号）+ 外壳（导航形态/
   材质/位置/rail/footer 开关）；每条消息组件、输入栏形态、气泡样式等
   更细粒度开关列为 0.70+（架构已按 token+shell 分层）。
2. Acrylic/Mica/Glass 为 drawable 近似（半透明+描边），未用 API31
   RenderEffect 真实模糊；真机 GPU 复验后再升级。
3. 终端仍是本地命令控制台（诚实的 no-shell），未接真实 PTY；Termius
   未能安装（见 §3）。
4. dock 为流式插入；bottom 位置在输入框下方时位于 footer 之上，长列表
   滚动表现待真机复验。
5. 皮肤 JSON 导出（Copy JSON）已可用，导入分享未做。
