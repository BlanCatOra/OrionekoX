# QA — alpha 0.9.1（第二轮回归修补）

- 版本：`0.9.1`，versionCode **1339**，包 `io.github.blancatora.orionekox`（release）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off`）
- 时间：2026-10-27 03:30–04:10（CST）
- 目的：修补第二轮 A/B/C 报告中可复现的回归与可用性问题，并复核 C 的两项 P1。

## 1. 本版改动

| 编号 | 改动 |
|---|---|
| R2-01 | Agent 会话忽略 `diditem` 旧滚动位置；若首个 cache pass 返回空，350ms 后自动重试一次；Agent 会话不再显示“删除此对话”底部遮罩 |
| R2-02 | 工具调用气泡改为摘要（`write_file` 显示 path + 字数，其他工具最长 180 字），不再倾倒整段 JSON |
| R2-06 | `.oxagent` 导入副本若无 API Key，toast 明确提示需重新填写 |
| R2-09 | 应用预设 toast 改为“已应用预设：%1$s” |
| R2-10 | 去掉 MCP 页重复的“MCP 服务”标题 |
| R2-11 | 中文成员移除提示改为 `**%1$s** 已从 **%2$s** 移除` |
| R2-12 | 群聊系统提示加入“你的群内用户名是 @agent_x” |
| R2-14 | 文件入口发送 JPG/PNG 时走图片气泡（缩略图 + 视觉），不再是无缩略图文档卡 |
| R2-16 | 主题 JSON 导入补 `in_bubble`/`out_bubble`/`corner_radius`/`font_size` 等 snake_case 别名 |
| R2-17 | 助手消息持久化前反转义字面 `\n`/`\t`/`\r`（跳过反引号内内容） |
| R2-18 | SillyTavern 卡 `first_mes` 存为 `OxAgent.firstMes` 并用作开场白；`description` 截断进简介 |
| R2-19 | 同名技能重复导入改为覆盖；技能行长按可删除（仅 app-private 导入目录） |
| R2-20 | 本地 Agent 支持名称子串搜索（如 `C2`），并统一小写匹配 |

## 2. 设备验证

### 2.1 Agent 会话重开（R2-01）— PASS

- 安装 0.9.1 后冷启动，打开 `酒吧炒饭C2`，3 次“返回列表再进入”。
- 每次历史立即显示（24 条），未出现空白，也未出现“删除此对话”遮罩。
- logcat 显示首个 cache pass 可能为 `size=15` + 跟随 `size=9`；若出现空 pass，重试日志为
  `agent chat empty, retry did=555000029`，随后 `messagesDidLoad ... size=9`。
- 首版修补中曾观察到 `loadIndex=2 size=0` 是 **MODE_SCHEDULED** 正常空结果，最终版已把自动重试限制在 `mode == 0`。

### 2.2 文件入口 JPG（R2-14 + 视觉）— PASS

- 附件 → 文件 → `verify-rice.jpg`（640×480，文字 `VERIFY RICE 0.9.1`）。
- 气泡为图片缩略图，双勾，无红叹号；日志
  `handleOutgoingMedia count=1` → `image=true` → `persistAndShow media type=1 ... photoNonNull=true sizes=2`。
- 模型回复准确描述“640×480 深蓝色、洋红/粉色边框、左侧白色小字 VERIFYRICE0.9.1”，视觉链路正常。

### 2.3 主题 JSON 导入别名（R2-16）— PASS

导入 `t991.json`：

```json
{"name":"T991","background":"#1E1B2E","in_bubble":"#2D2A45","out_bubble":"#7C5CFF",
 "bubble_alpha":220,"corner_radius":18,"font_scale":1.2}
```

外观页逐项显示：`#1E1B2E` / `#2D2A45` / `#7C5CFF` / `18 dp` / `1.2x`（透明度 86%），
六项全部映射；随后切回 Telegram 原生清理。

### 2.4 C 的两项 P1 复核 — 未复现

- **图片选择器发错图**：从附件→相册选中 `verify-rice.jpg`（首行第二张），编辑器正确显示
  `VERIFY RICE 0.9.1`；发送后气泡缩略图正确。C 当时选中的是相册里的一张联系页截图，
  属网格选错项（`-gpu off` 下缩略图渲染不完整，难以辨认）。
- **列表点击错位**：用 uiautomator 取 C2 行 bounds `[0,1141][1080,1326]`，点击中心
  (540,1233) 与下缘 (540,1315) 均进入 C2，未打开 Arch。C 的三次复现均在模拟器掉线/重启后，
  按模拟器输入/截图时间差记录，未按 App 代码缺陷处理。

## 3. 仅代码审查、未做设备验证

- R2-02 工具摘要、R2-17 反转义、R2-18 first_mes、R2-19 技能去重/删除、R2-20 搜索子串：
  逻辑简单，但受模拟器在搜索/技能页打开 Chromium 时崩溃影响，未逐项点测。
- 建议第三轮独立测试在稳定设备上覆盖：搜索 `C2`、重复导入技能、SillyTavern `first_mes`、
  含 `\n` 的助手长文重开、工具气泡。

## 4. 仍待 0.10.0

- 背景图与原生壁纸叠加、底部输入栏下方露壁纸（背景层与气泡透明度解耦）。
- 按 Agent 持久化主题、渐变/边框/磨砂/字体家族、导出与导入预览、对比度提示。
- 群内相册发图（B 的 P2-5）仍需在稳定模拟器上复现。

## 5. 产物

- `dist/OrionekoX-0.9.1-alpha-arm64-x64.apk`，sha256 `e22563e1c6e342e001d143a0ffa67183fcdea39623ff9d540df25700c5ea8a5f`
- `dist/OrionekoX-0.9.1-debug-arm64-x64.apk`，sha256 `15c1cc7a73eb1c6f31d2beb6bf6c5c839acdf960a609846d2fdc2ec335b701d7`
- 模拟器最终安装为 0.9.1 release；无障碍服务已重新启用；外观为 Telegram 原生。
