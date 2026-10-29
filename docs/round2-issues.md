# 第二轮独立测试问题汇总（A/B/C）

状态：A、B、C 均已完成；P1 已由主代理在 0.9.0 上做受控复验，见下。此文档用于排期修复，不替代各测试的完整报告。

## 已完成报告

- A：`docs/bar-rice-round2-A.md`（0.8.115，2026-10-26）
- B：`docs/bar-rice-round2-B.md`（0.9.0，2026-10-27）
- C：`docs/bar-rice-round2-C.md`（0.9.0，2026-10-27）

## P1 受控复验（2026-10-27 03:27–03:30，0.9.0/1329）

C 报告的两项 P1 在受控复验中**未复现**，暂不按 App 代码缺陷处理：

- **图片选择器发错图**：把 `verify-rice.jpg`（640×480，写有 `VERIFY RICE 0.9.1`）扫描进 MediaStore 后，从附件→相册选中首行第二张，编辑器正确显示该图；发送后气泡缩略图正确，日志
  `handleOutgoingMedia count=1` → `image=true` → `persistAndShow media type=1 ... photoNonNull=true sizes=2`。
  C 的失败选中的是相册中一张“联系人页截图”，属于缩略图网格选错项（`-gpu off` 下缩略图渲染不完整，更难辨认），不是发送链路替换了图片。
- **列表点击错位**：用 uiautomator 取 C2 行 bounds `[0,1141][1080,1326]`，点击中心 (540,1233) 与下缘 (540,1315) 均正确进入 C2，未打开 Arch。C 的三次复现都发生在模拟器掉线/重启后，按“模拟器输入或截图时间差”记录；第三轮在稳定模拟器上继续观察。

第三轮仍应覆盖：相册网格在 `-gpu off` 下的缩略图/选中项、模拟器重启后的首次点击。

## 0.9.1 处理状态

- 已修并设备验证：R2-01（重开空白 + 删除对话遮罩）、R2-14（文件入口图片缩略图）、R2-16（主题导入别名）。
- 已修、代码审查：R2-02、R2-06、R2-09、R2-10、R2-11、R2-12、R2-17、R2-18、R2-19、R2-20、R2-05。
- 待稳定设备复验：R2-03（发送命中区）、R2-04（群相册）、R2-05 的群内 @ 图片回忆、R2-17 的旧消息迁移。
- 0.10.0 已处理：R2-15（导入预览）、R2-21（背景层/系统栏；模拟器合成仍留浅纹理，待真机）。
- 仍推迟：R2-07（工作区新建文件夹）、R2-08（状态栏叠字）、R2-13（OxLinux 行点击）。

## P2（按影响排序）

| ID | 来源 | 问题 | 建议修复 | 目标版本 |
|---|---|---|---|---|
| R2-01 | A/B | 从聊天列表重进本地 Agent 会话，消息区空白；冷启动才恢复 | 对 Agent 会话忽略 `diditem` 滚动恢复，强制 `max_id=0 / load_type=2`；若 1.2s 后 `messages` 仍为空，再发一次 cache load；加日志确认 `messagesDidLoad` 是否被 `index == -1` 丢弃 | 0.9.1 |
| R2-02 | A/B | 工具结果以原始 JSON 文本出现在气泡中，含 `\n` 转义 | 工具结果不直接注入可见气泡；若必须显示，解析 JSON 的 `ok/result/error` 并格式化；聊天模型仍收到原始 JSON | 0.9.1 |
| R2-03 | A/B | 发送按钮视觉中心点击无效，仅特定坐标可发 | 复现后用 uiautomator bounds 对比按钮 view 与命中区；检查键盘 inset/自定义字号下的布局；必要时扩大 `sendButton` 触摸区 | 0.9.1 |
| R2-04 | B | 群内“相册”选图后卡在预览/编辑器循环，发不出 | 在 `prepareSendingPhoto → prepareSendingMedia` 的 group 分支加日志；确认编辑器完成回调是否被本地分支 `return` 打断；为群相册增加直接发送路径/关闭编辑器回调 | 0.9.1 |
| R2-05 | B | `@agent` 要求描述上一张群图时收不到像素 | 群历史保存最近图片路径；当前消息含“图/照片/image”且无附件时，把最近一张群图作为 vision part 附加 | 0.10.0 |
| R2-06 | A | `.oxagent` 导入副本无 API Key，首次使用才暴露 | 导入预览/聊天内提示“此副本未包含密钥，请在 Agent 编辑器中重新填写”，不导出密钥 | 0.9.1 |
| R2-16 | C | 竞品主题 JSON 的 `in_bubble`/`out_bubble`/`corner_radius` 未映射 | 导入器补 snake_case 与 Kelivo `ChatBubbleStyleOverrides` 别名；解析失败给可读错误 | 0.9.1 |
| R2-17 | C/B | 重开后助手消息里的 `\n` 显示为字面量 | `finishTurn`/`injectAgentReply` 对双转义换行做反转义（跳过反引号内内容），并优先采用流式文本 | 0.9.1 |
| R2-18 | C | SillyTavern `first_mes` 未作为开场白，`description` 未进简介 | `OxAgent` 增加 `firstMes`；导入时映射；`greetIfEmpty` 优先用它 | 0.9.1 |
| R2-19 | C | 技能重复导入产生重复条目、无删除入口 | 同名覆盖或提示跳过；技能行支持长按删除（确认） | 0.9.1 |
| R2-20 | C | 本地智能体在聊天/分享/成员搜索中搜不到 | 搜索路径合并本地 Agent 列表（按名称/`@agent_x`/备注匹配），或在 `users` 搜索中确保本地用户可命中 | 0.9.1 |
| R2-21 | C | 背景图仍叠加原生壁纸；底部输入栏下方露出壁纸条 | 背景层与气泡透明度解耦；背景图不透明、加独立暗化；背景覆盖列表父容器到底部 | 0.10.0 |

## P3

| ID | 来源 | 问题 | 建议 |
|---|---|---|---|
| R2-07 | A/B | 工作区无“新建文件夹”，目录靠 `dir/file` 隐式创建 | 工作区工具栏加“新建文件夹”；列表显示目录节点 |
| R2-08 | A/B | 状态栏下出现消息文字/叠字（自定义会话） | 检查 chat list 顶部 inset；给列表加 `clipToPadding=false` + 顶部 padding，或背景层不覆盖状态栏文字 |
| R2-09 | B | 应用外观预设 toast 写“已导入外观” | 增加 `OxChatPresetApplied` 字符串 |
| R2-10 | B | MCP 页面重复“MCP 服务”标题 | 去掉重复 header |
| R2-11 | B | 移除成员 toast 缺空格 | 调整格式字符串 |
| R2-12 | B | Yuki 否认自己的 `@agent_11` 用户名 | 群系统提示中加入“你的用户名是 @agent_x”；或群里显示可读 username |
| R2-13 | B | OxLinux 入口行正文不可点击 | 整行点击进入，开关只负责启停 |
| R2-14 | B | 文件入口 JPG 为无缩略图文档卡 | 文件选择器识别图片扩展名时走 photo 气泡（与相册一致） |
| R2-15 | A | 外观 JSON 导入无预览/回滚 | 0.10.0 导入预览对话框 + 解析失败不写入 |

## 0.10.0 美化功能清单（来自用户要求 + 竞品调研）

- 背景：纯色 / 图片 / 双色渐变 + 方向；暗化叠层；AMOLED 纯黑。
- 气泡：收/发颜色、透明度、圆角、边框颜色/宽度/透明度、solid/frosted 切换、文字颜色。
- 字体：聊天字体家族（系统/衬线/等宽/导入 TTF）、代码字体、字号、行高；CJK 回退。
- 按 Agent 保存主题 + “应用到全部 Agent”；群聊沿用全局。
- 预设：保存/应用/删除/重命名、导出 JSON、导入预览与回滚。
- 竞品导入：Kelivo `ChatBubbleStyleOverrides` 字段、Kelivo/RikkaHub CustomTheme、Operit 主题备份（best-effort）。
- 对比度/可读性提示：根据气泡色与文字色计算对比度，低于阈值时警告。
- 原生模式：一键重置 + 跳转 Telegram 原生聊天外观/壁纸设置。

## 0.17.1 回归与修复记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-22 | 0.17.0 QA | 冷启动后第一次进入本地 Agent 会话，按会话主题/背景模糊不生效，需进一次外观页再返回 | `ChatActivity.onResume` 在列表存在时延迟 500ms 重新 `applyToChat` | 0.17.1 已修复并设备验证 |
| R2-23 | 0.17.0 QA | 本地群不应用按会话主题（`applyToChat` 只允许 Agent 会话） | 条件加入 `OxGroupStore.isLocalGroup` | 0.17.1 已修复；R2C 群首开消息与主题均正常 |
| R2-24 | 0.17.1 QA | 会话/Agent 作用域“清除覆盖并跟随上层”实际只删本作用域文件，但提示“已恢复 Telegram 原生外观” | 新增 `OxChatResetScopeDone`，按作用域选择提示 | 0.17.1 已修复并设备验证 |
| R2-25 | 环境 | `-gpu off` 下打开会话时 qemu `RenderThread` SIGSEGV，非应用 FATAL/ANR；禁用 WebView/Chrome、`-feature -Vulkan` 均无效 | 改用 `-gpu guest` 完成群会话 QA；待真机/GPU 复验 | 未关闭（环境限制） |
| R2-26 | 0.17.1 QA | 群会话背景细节被气泡覆盖，未能单独做群聊模糊像素对比 | 群会话已确认使用同一 `makeBackground` 路径且 JSON/页面为“强”；补真机空白壁纸截图 | 待复验 |

- 本轮未向真实联系人发送消息，未清理用户数据；测试后恢复 WebView/Chrome 并重新启用无障碍服务。

## 0.18.0 预设包 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-27 | 0.18.0 QA | 修改 `zh-cn.xml` 后未再次提高 `OX_ZH_PACK_VERSION`，首轮安装仍显示旧文案，新增导出说明回退英文 | 中文包 85→86，重建后验证“导入预设包 / 导出预设包 / 导入这个预设包？ / 导入”均为中文 | 0.18.0 已修复 |
| R2-28 | 0.18.0 QA | 预设包字体资产、单资产 >8MB 跳过、写盘中途回滚未做设备样本 | 代码路径已实现；非法包/重复名/带背景包已验证，字体与回滚留待真机或后续样本 | 待复验 |

- `.oxthemes` 导出/导入设备结果：2 个纯色预设重复导入 0/2；含 16KB 背景图的预设删除后恢复 1/2，资产路径改写并成功应用；非法 13B 文件被拒绝且无写入。
- 测试后删除 Downloads 临时包与非法文件；`BundleQA` 预设及其资产保留为模拟器回归样本。

## 0.19.0 RikkaHub 备份导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-29 | 0.19.0 QA | RikkaHub 导入只覆盖 settings.json，聊天数据库/附件/技能/工作区未导入；独立 Provider、OAuth、SSE MCP、图片头像与开场白也未映射 | 本轮先完成助手/Provider/MCP 预览与幂等导入；其余明确标注为边界，待真实备份与后续轮次 | 部分完成 |
| R2-30 | 0.19.0 QA | 样例备份按 RikkaHub 当前源码 schema 构造，尚未用真机 RikkaHub 导出复测 | 记录字段来源与映射；下一轮优先找真实备份做兼容性回归 | 待复验 |

- 设备结果：预览“助手 2 / Provider 1 / MCP 1”，SSE 跳过 1；导入“新建 2 / 更新 0 / 新增 MCP 1”；重复导入“新建 0 / 更新 2 / 新增 0 / 跳过 1”；非 RikkaHub JSON 被拒绝且无写入。
- Agent JSON 的 model/context/maxTokens/temperature/topP/reasoning/emoji/tg_id 与样例一致；MCP headers 正确；预览、结果与 logcat 均未出现测试 Key。
- 测试后通过 UI 删除两个测试助手和 RikkaTestMCP；Downloads 样例与非法文件已删除。

## 0.20.0 RikkaHub skills 导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-31 | 0.20.0 QA | RikkaHub 技能只导入 SKILL.md 正文，附件/脚本不导入，且 `enabledSkills` 未分配给 Agent | 本轮先把 `skills/*/SKILL.md` 写入私有技能库并按目录名幂等更新；其余记为边界 | 部分完成 |
| R2-32 | 0.20.0 QA | 技能 id 取相对目录并 sanitize/截断，极端情况下可能碰撞；嵌套目录用 `_` 连接 | 记录风险；同名 id 会更新同一技能，不自动合并冲突内容 | 待改进 |

- 设备：预览“助手 2 / Provider 1 / MCP 1 / 技能 1”；导入“新建 2 / 更新 0 / 新增 MCP 1 / 导入技能 1”；TestSkill/SKILL.md 152 B，frontmatter 完整；重复导入“新建 0 / 更新 2 / 新增 0 / 跳过 1 / 导入技能 1”，仍只有 1 个技能目录。
- 清理：测试后 UI 删除两个测试助手与 RikkaTestMCP，删除 TestSkill 目录与 Downloads 样例；无 Key 泄漏、无 FATAL/ANR。

## 0.21.0 外观实时预览 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-33 | 0.21.0 QA | 预览是轻量示例，不是真实 MessageCell；媒体/工具气泡、引用、头像和真实换行未展示 | 用当前快照绘制背景/气泡/边框/字体/行高，满足编辑时看整体效果；复杂消息结构留待后续 | 部分完成 |
| R2-34 | 0.21.0 QA | 自定义 TTF 字体、极亮/极暗文字色与多作用域并排对比未单独验证 | 当前系统字体样本已验证；TTF/极端色待补 | 待复验 |

- 设备：全局图片背景主题下预览显示 `VERIFYRICE0.9.1`、深色收气泡、紫色发气泡、洋红边框、1.15× 字号/1.4× 行高；模糊从“关”切到“强”后预览背景明显抹平，随后恢复 `background_blur=0`。
- 无 FATAL/ANR；未修改消息或联系人，无新增测试对象。

## 0.21.1 RikkaHub 技能分配/请求头映射 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-35 | 0.21.1 QA | 0.20.0 只把技能写入技能库，未按 RikkaHub `enabledSkills` 关联到助手；助手 `customHeaders` 也未导入 | 先导入技能再导入 Agent，按名称/id 匹配 skillIds；customHeaders 映射为 headers；空数组不覆盖原值 | 已修复并设备验证 |
| R2-36 | 0.21.1 QA | 仅映射助手级 headers；Provider 级 headers、图片头像、开场白仍未映射 | 记录边界，待后续备份字段补全 | 待补 |

- 设备：`Rikka QA` 的 `enabledSkills=["Rikka Test Skill"]` → `skill_ids=["TestSkill"]`；`X-Rikka-Assistant: 1` → `headers=[["X-Rikka-Assistant","1"]]`；重复导入仍为同一 Agent，映射保持。
- 清理：删除测试助手、RikkaTestMCP、TestSkill 与 Downloads 样例；无 Key 泄漏、无 FATAL/ANR。

## 0.21.2 RikkaHub Provider header/开场白 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-37 | 0.21.2 QA | 第一版 header 合并把 `{name,value}` 转成数组再读回，设备上 Provider header 丢失（`m=1`/`m=0`） | 改为直接遍历源数组写入 `LinkedHashMap`，不二次往返；复测 `m=2` | 已修复 |
| R2-38 | 0.21.2 QA | 仅导入第一条 assistant 文本作为开场白；图片/工具预设消息与多条 preset 未处理 | 记录边界；`firstMes` 限 4000 字符 | 部分完成 |

- 设备：Provider `X-Rikka-Provider: p` + 助手 `X-Rikka-Assistant: 1` 合并为两条 headers；`presetMessages` 的首条 assistant 文本进入 `first_mes`；重复导入保持 2 个 Agent、字段不变。
- 清理测试助手/MCP/TestSkill/样例；无 Key 泄漏、无 FATAL/ANR。

## 0.22.0 人设推荐外观 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-39 | 0.22.0 QA | 关键词启发式不理解语义；无命中统一落到“深海” | 记录规则；本地、确定性、无网络；后续可加候选预览与语义推荐 | 部分完成 |
| R2-40 | 0.22.0 QA | `applyBuiltIn` 覆盖整份快照，会替换用户已有背景图/字体/行高；无撤销按钮 | 依赖“清除覆盖并跟随上层”恢复；入口仅 Agent 作用域 | 待改进 |

- 设备：`barB-nokey`（人设含深夜/酒吧）推荐“赛博霓虹”，toast 与 `agent_555000016.json` 的渐变/气泡/文字/边框一致。
- 测试后删除该 Agent 覆盖文件，恢复跟随全局；未修改其他 Agent；无 FATAL/ANR。

## 0.23.0 AMOLED 快捷开关 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-41 | 0.23.0 QA | AMOLED 只改背景/系统栏，不自动调整浅色文字/气泡；无定时或跟随系统深色 | 保留当前快照其余字段，用户可用对比度检查调整；记录为边界 | 部分完成 |
| R2-42 | 0.23.0 QA | 开关只作用于当前作用域，不能批量应用到全部 Agent | 与现有作用域模型一致；全局开关需切到全局外观页操作 | 设计如此 |

- 设备：全局图片背景主题开启后 `amoled=true`，预览与 R2B 本地群背景纯黑，气泡/文字/边框正常；关闭后 `amoled=false`，原图片背景保留。
- 无 FATAL/ANR；测试后开关已恢复关闭。

## 0.24.0 RikkaHub 文本历史导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-43 | 0.24.0 QA | 只导入每个节点的当前分支；其他编辑分支、图片/文件/工具/推理 part 与附件均不导入 | 按 `messages[select_index]` 取当前分支，仅提取 text；其余跳过并记录边界 | 部分完成 |
| R2-44 | 0.24.0 QA | 同一 assistant 的多个 RikkaHub 会话合并为一个 Ox 聊天，标题不保存；时间戳为合成值 | 当前 OxAgentStore 无“多会话/标题”模型；使用 create_at+节点序生成稳定 ts 并去重 | 待模型支持 |

- 设备：样例 2 会话/4 节点，预览“历史：会话 2 个 · 消息节点 4 条”；导入 4 条，正确选择 Branch B（未选 Branch A）；重复导入仍 4 条。
- 清理测试助手/MCP/技能/样例与缓存 DB；无 Key 泄漏、无 FATAL/ANR。

## 0.24.1 Ox 界面中文化补漏

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-45 | 0.24.1 文案审计 | Ox 代码引用的 12 个字符串缺少中文，中文模式会回退英文：Operit 导入 6 个、本地会员 2 个、终端 4 个 | 补齐译文，中文包 93→94；设备确认设置页与终端均显示中文 | 已修复 |

- 审计范围：`ui/Ox*.java` 与 `ox/*.java` 的 531 个 `R.string` 引用；Telegram 原生与其他模块未逐条扫描。
- Ox 设置显示“本地会员 / 从 Operit / Operit2 导入”；终端显示“终端 / 清空 / 重启 / 输入命令…”；无 FATAL/ANR。

## 0.25.0 RikkaHub 图片历史 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-46 | 0.25.0 QA | 只匹配 `upload/` 文件名，远程/data URL 与缺失文件跳过；每消息只取第一张图 | 记录边界；图片写入 imagePath 供后续视觉上下文使用 | 部分完成 |
| R2-47 | 0.25.0 QA | 导入图片不以原生照片气泡展示，只在历史中用 `[图片]` 占位 | 现有聊天历史模型无图片气泡路径；保留 imagePath 供模型视觉输入 | 待 UI 支持 |

- 设备：1 会话/3 节点（文本、回复、图片）样例导入 3 条，第 3 条含 `image_path`，PNG 70 B；重复导入仍 3 条。
- 清理测试助手/MCP/技能/上传文件/样例/缓存 DB；无 Key 泄漏、无 FATAL/ANR。

## 0.25.1 人设推荐确认/撤销 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-48 | 0.25.1 QA | 0.22.0 推荐一键覆盖，用户无法确认或恢复 | 增加原生确认框；应用前备份当前作用域快照，提供“撤销上次推荐” | 已修复 |
| R2-49 | 0.25.1 构建 | 英文确认文案 `agent's` 未转义撇号导致 AAPT2 字符串编译失败 | 改为 `agent name`，重建成功 | 已修复 |

- 设备：barB-nokey 推荐“赛博霓虹”前显示确认；应用后生成 Agent 覆盖与 `ox_recommend` 备份；撤销恢复图片背景/80%/24%/AMOLED 关闭，备份删除，toast 正确。
- 边界：撤销写成显式覆盖（Agent 文件仍在），仅一层撤销、无重做；测试后删除测试覆盖文件。

## 0.25.2 命名预设重命名 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-50 | 0.25.2 QA | 命名预设只有保存/应用/删除，无法重命名 | 长按菜单增加重命名；预填名称、sanitize、更新 JSON name、删除旧文件；同名目标拒绝 | 已修复 |

- 设备：RenameQA → RenameQA2，目录仅保留新文件；改名为已存在的 BundleQA 被拒绝，原文件不变；取消对话框无副作用。
- 测试后删除临时预设；无 FATAL/ANR。

## 0.26.0 Kelivo/Chatbox 助手导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-51 | 0.26.0 QA | 只导入助手/人设；Provider 配置与密钥、对话、skills、MCP、工作区、头像背景、presetMessages/regex/skillIds 未导入 | 明确边界；Provider 仅从现有 Agent 继承，无则离线 | 部分完成 |
| R2-52 | 0.26.0 QA | 样例按源码字段构造，真实 Kelivo/Chatbox 备份未复测；`chatModelId` 可能是逻辑模型 id | 记录兼容性风险，待真实备份验证 | 待复验 |

- 设备：样例 zip（1 copilot + 1 assistants_v1）预览 2；导入“新建 2”；Kelivo Assistant 的 prompt/model/0.5/0.9/256/X-Kelivo 正确；重复导入“新建 0，更新 2”，仍 2 个 Agent。
- 清理测试助手与样例；无 Key 泄漏、无 FATAL/ANR。

## 0.26.1 Kelivo Provider 接线 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-53 | 0.26.1 QA | 0.26.0 只把所有 Kelivo 助手接到本机第一个 Provider，忽略备份中的 provider_configs_v1 | 解析 provider id→配置，按 chatModelProvider 使用 baseUrl/apiKey/models；未命中的助手再回退 | 已修复 |

- 设备：`prov-1` 的 baseUrl `https://kelivo.example/v1`、model `kelivo-test-model` 正确写入 Agent；Copilot 无引用时回退本机 Provider；重复导入仍 2 个 Agent；logcat 无测试 Key、无 FATAL/ANR。
- 边界：OAuth/多 Key/代理/provider headers/body/Vertex 特殊配置未导入；仅保存被助手引用的 Provider。

## 0.26.2 Operit 背景模糊映射 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-54 | 0.26.2 QA | Operit `use_background_blur` 只把气泡设为磨砂，未真正模糊壁纸；`background_blur_radius` 未使用 | 映射到 1–3 级背景模糊（≥20 强/≥12 中/其余轻）；磨砂只由 liquid/water glass 触发 | 已修复 |

- 设备：`background_blur_radius=24` 的 Operit JSON 预览显示“模糊强”，Agent JSON `backgroundBlur=3`，预览壁纸明显抹平；测试后删除覆盖。
- 边界：阈值启发式；纯色/渐变无可见模糊；9-patch/输入样式/头像/padding/crop 仍未映射。

## 0.27.0 命名预设预览 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-55 | 0.27.0 QA | 长按预设只能重命名/删除，不能先看效果；点击应用会直接覆盖当前主题 | 预览逻辑改为接受任意 Snapshot；长按菜单增加“预览”，只读预设 JSON，不写入当前作用域 | 已修复 |

- 设备：当前图片背景主题下预览 DarkSea，显示黑色纯色背景与浅绿气泡；关闭后 `ox_chat_theme.xml` mtime 未变，当前主题仍为原图片背景。
- 边界：预览仍是轻量示例；缺失资产按回退字段；不支持预览内编辑/并排比较。

## 0.27.1 Kelivo 技能导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-56 | 0.27.1 QA | Kelivo/Chatbox 备份的技能未导入，预览文案还声称“不含技能” | 读取 zip 的 skills/**/SKILL.md 写入私有技能库；同名更新；修正文案并重建 | 已修复 |

- 设备：1 助手 + KelivoSkill 样例，预览“将导入 1 个技能”；导入后 skill 96 B；重复导入“新建 0/更新 1/技能 1”，仍 1 个目录；清理测试对象。
- 边界：仅 zip 的 skills/；只导入 SKILL.md 正文；脚本/附件、对话、MCP、工作区、头像背景未导入。

## 0.27.2 Kelivo HTTP MCP 导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-57 | 0.27.2 QA | Kelivo/Chatbox 备份的 MCP 服务器未导入，预览文案仍称“不含 MCP” | 解析 mcp_servers_v1/mcpServers，仅导入 HTTP/streamableHttp 到本机 MCP 列表，按 URL 去重；SSE/stdio/inmemory 计数跳过 | 部分完成 |

- 设备：1 助手 + 1 HTTP + 1 SSE 样例，预览“1 个 HTTP MCP / 跳过 1”；导入“新建 1 / 新增 MCP 1 / 跳过 1”，`KelivoHTTP` 的 URL、enabled、`X-Kelivo` header 正确，SSE 不存在；重复导入“新建 0/更新 1/新增 MCP 0/跳过 2”，仍 1 条。
- 边界：SSE/stdio/inmemory 未导入；MCP 工具白名单、OAuth、客户端注册、环境变量未导入；不做连通性测试；真实备份待复验。

## 0.28.0 Kelivo / Chatbox 对话导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-58 | 0.28.0 QA | Kelivo/Chatbox 备份的对话历史未导入；只导入助手/技能/MCP | 解析 Chatbox v2 manifest + sessions/*/session.json、旧 zip sessions 与直接 JSON；每 thread 建本地 Agent 并写 chat.json；导入后 replay 到原生 messages_v2 | 已修复 |
| R2-59 | 0.28.0 QA | 首次导入后聊天界面只显示欢迎语，历史在 chat.json 但不在 SQLite | `importKelivo` 对每段对话调用 `replayImportedHistory`，清理欢迎语并回放历史；重复导入替换历史 | 已修复 |

- 设备：zip 2 个 session（含 1 个 thread）预览 3 段/7 条；导入 3 个 Agent，provider/model/0.4/0.8/128 正确；原生界面显示“当前话题”“2025年10月28日”和三条历史气泡；重复导入 3 段替换，消息仍 2/3/2 条。JSON 样例 1 段/2 条，0.3/0.6/64 与“10月25日”正确。
- 清理：删除 4 个测试 Agent 与样例，dialogs/messages 归零；无本包 FATAL/ANR。
- 边界：图片/文件只留占位，tool 压平为 assistant；旧时间戳导致列表靠后；每 thread 独立 Agent；加密/真实 Kelivo 备份未复测。

## 0.29.0 Kelivo / Chatbox 头像导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-60 | 0.29.0 QA | Kelivo/Chatbox 备份中的头像资源未导入，助手与会话 Agent 仍显示 emoji/字母头像 | 解析 manifest resources 的 avatar/copilot-image，按 originalStorageKeys 映射；支持原始字节与 data-url-base64；写入 Agent 目录并设置 avatar_path | 已修复 |
| R2-61 | 0.29.0 QA | 重复导入可能覆盖用户自定义头像 | 仅当 Agent 当前 avatar_path 为空时才写入备份头像；已有头像保留，重复导入 sha256 不变 | 已修复 |

- 设备：1 助手 + 1 会话、2 个头像资源；预览/结果均提示“2 个头像”；落盘 avatar.png 1310 B/1623 B；原生会话页显示绿色 S1、助手页显示蓝色 AI；重复导入 0 新建/1 更新，头像 sha256 未变；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：仅 avatar/copilot-image；聊天图片、文件、背景、字体不导入；http(s) 头像不下载；HEIC/AVIF 跳过；真实备份待复测。

## 0.30.0 Kelivo / Chatbox 聊天图片导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-62 | 0.30.0 QA | 0.28.0 对话导入把图片只写成 `[图片]` 占位，备份图片资源未落盘，原生聊天看不到照片 | 解析 resources 的 image/图片附件，消息按 storageKey/url 匹配；写入 Agent media/ 并填充 image_path；replay 用 generatePhotoSizes 生成原生照片气泡 | 已修复 |
| R2-63 | 0.30.0 QA | 纯图片消息正文为空，按旧逻辑会被跳过 | 转换/回放允许“空正文 + imagePath”的消息；无说明图片仍生成照片气泡 | 已修复 |

- 设备：1 会话/4 条、2 张图片（原始 PNG + data URL）；预览/结果均提示“2 张”；落盘 img_0.png 2783 B、img_2.png 3272 B；原生界面 PIC1 带“看看这张”说明、PIC2 纯图片气泡；重复导入图片 sha256 不变、messages_v2 仍 4 条；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：每消息仅首图、不生成相册；文件/音视频/网络图片不导入；真实备份待复测。

## 0.31.0 Kelivo / Chatbox 聊天附件导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-64 | 0.31.0 QA | 对话导入中的非图片附件仍只有 `[文件] 名称` 占位，备份文件资源未落盘，原生聊天看不到附件 | 解析 parsed/raw-attachment 非图片资源，消息按 storageKey/rawStorageKey/url 匹配；写入 Agent media/，扩展 OxChatEntry.filePath/fileName；replay 用 buildLocalDocument 生成原生文件气泡 | 已修复 |
| R2-65 | 0.31.0 QA | 纯附件消息正文为空，旧回放逻辑会跳过 | 回放允许“空正文 + filePath”，纯附件仍生成文件气泡；Agent 上下文补 `[文件] 名称` | 已修复 |

- 设备：1 会话/4 条、2 个附件（raw txt + data URL json）；预览/结果均提示“2 个”；落盘 0_notes.txt 35 B、2_data.json 38 B；原生 notes.txt 带“看一下附件”说明、data.json 纯文件气泡；重复导入附件 sha256 不变、messages_v2 仍 4 条；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：每消息仅首附件；音视频按文档展示；网络附件不下载；真实备份待复测。

## 0.32.0 Kelivo / Chatbox 备份导出 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-66 | 0.32.0 QA | 三位一体兼容只有导入方向，无法把本机 Agent/聊天交给 Kelivo/Chatbox | 新增 Chatbox v2 导出器与 Ox 设置入口；有聊天写 session、无聊天写 copilot；头像/图片/附件按 data URL 资源与 sha256 描述导出 | 已修复 |
| R2-67 | 0.32.0 QA | 若同时写 copilot 和 session，回环导入会产生两个 Agent | 有聊天记录的 Agent 只写 session，无聊天只写 copilot；回环后每个源 Agent 只生成一个目标 Agent | 已修复 |

- 设备：seed 1 助手+1 会话（2 头像/1 图片/1 附件）；完整导出 483,866 B，manifest/checksum/stats 通过，无真实密钥；过滤子集 8,306 B 回环导入后 prompt、采样、头像、图片、附件、4 条消息完整；原生界面显示 AV 头像、RT 图片说明与 notes.txt 文件气泡；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：全量导出无选择；回环 id 多一层 `kelivo_` 前缀；不导出 Key/provider URL/工作区/记忆/技能/MCP/主题；外部 Chatbox 未实测。

## 0.33.0 按 Agent 导出 Chatbox 备份 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-68 | 0.33.0 QA | 0.32.0 只能全量导出，无法只把一个 Agent 交给 Kelivo/Chatbox | `exportTo` 增加 onlyIds 过滤；Agent 长按菜单新增 “Export Chatbox backup”，确认后 SAF 导出单个 Agent 的 session/copilot 与资源 | 已修复 |

- 设备：`Single Export` 导入后长按导出 6,190 B；schema 1 session/0 copilot/3 资源，stats/checksum 通过，无其他 Agent、无密钥；回环导入保留 prompt、0.5/0.7/80、头像、图片、notes.txt 与 4 条消息；原生 SE 头像/SE1 图片说明/文件气泡正确；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：单 Agent、无多选；回环 id 多一层前缀；不导出 Key/provider URL/工作区/记忆/技能/MCP/主题；外部 Chatbox 未实测。

## 0.34.0 技能库导出 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-69 | 0.34.0 QA | 技能库只有导入，无法把私有技能交给 Kelivo/Chatbox/RikkaHub | 新增 `OxSkillStore.exportZip` 与 “Export skills zip”，写 `skills/<id>/SKILL.md`、同名 id 加后缀 | 已修复 |
| R2-70 | 0.34.0 QA | 回环导入 `skills/<id>/SKILL.md` 时把容器目录算进 id，生成 `skills_<id>` 重复技能 | `importFromUri` 去掉 `skills/` 前缀后再 sanitize；重建后回环得到准确 `<id>` | 已修复 |

- 设备：QASkill0340 150 B 导入后导出 2,353 B/7 个 SKILL.md；裁剪 272 B 子集删除后回环导入，修正后得到 `files/oxskills/QASkill0340/SKILL.md` 150 B；列表正确、无 `skills_` 重复目录；清理后无残留，无本包 FATAL/ANR。
- 边界：全量导出、无多选；仅 SKILL.md 正文；外部客户端未实测。

## 0.35.0 RikkaHub 图像头像导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-71 | 0.35.0 QA | RikkaHub 备份的 `Avatar.Image` 未导入，助手只有 emoji/默认头像 | 解析 avatar.url，按 basename 匹配备份 `upload/` 文件，写入 Agent 目录并设置 avatar_path；重复导入保留已有头像 | 已修复 |
| R2-72 | 0.35.0 QA | `avatarEmoji` 只认 `type=emoji`，RikkaHub FQN 序列化的 `...Avatar.Emoji` 丢失 emoji | 类型名改为包含 `emoji` 判断，兼容 FQN 与短名 | 已修复 |

- 设备：2 助手样例（Image upload RA + Emoji FQN 🦊）；预览提示 1 个图像头像；导入 2 助手、头像 1 个，avatar.png 2,519 B；原生 RA 头像与 🦊 正确；重复导入 0 新建/2 更新、头像 sha256 不变；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：仅 upload/ 本地图像，网络/content 无文件时跳过；basename 匹配；真实备份待复测。

## 0.36.0 多选 Agent 导出 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-73 | 0.36.0 QA | 0.33.0 只能导出单个 Agent，无法一次选择多个 | Ox 设置新增 “Export selected agents” 复选框列表，勾选后经 SAF 导出 `orioneko-selected-backup.zip`；导出器只接收勾选 id | 已修复 |

- 设备：Multi B（2 消息）+ Single Export（4 消息/头像/图片/附件）勾选导出 6,697 B；manifest 2 sessions/0 copilot/3 资源，仅这两个 Agent；回环导入生成 2 个会话，B 消息/B 回复、图片、notes.txt 与 4 条消息均正确；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：无搜索/全选；不导出 Key/provider URL/工作区/记忆/技能/MCP/主题；外部 Chatbox 未实测。

## 0.37.0 MCP 服务器 JSON 导出 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-74 | 0.37.0 QA | MCP 服务只有导入，无法把本机服务器列表交给 Kelivo/RikkaHub/Claude Desktop | 新增 “Export MCP JSON” 与 `OxMcpManager.exportJson`，写 `mcpServers`（type/url/isActive） | 已修复 |
| R2-75 | 0.37.0 QA | 服务器 headers 可能含 Authorization/token，直接导出会泄露密钥 | 导出层完全不写 headers/OAuth；内部 servers.json 保留原 header，导出文件无 secret | 已修复 |

- 设备：带 `Authorization: Bearer QA_SECRET_0370` 的测试服务器导出 390 B，4 个 HTTP 服务器、无 headers 字段、secret 出现 0 次；内部 servers.json 仍保留 header；删除测试服务器后回环导入恢复 URL、headers 为空，原有 3 个服务器未变；清理后无本包 FATAL/ANR。
- 边界：仅 HTTP、无多选、headers/OAuth 不导出、不做连通性测试；外部客户端未实测。

## 0.37.1 导出功能中文化 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-76 | 0.37.1 QA | 0.30–0.37 新增的对话/导出/多选/MCP/技能导出入口与预览在中文界面仍显示英文 | 中文包新增 27 条译文，更新空备份文案，`OX_ZH_PACK_VERSION` 98→99；审计 `R.string.Ox*` 缺失 0 | 已修复 |

- 设备：Ox 设置、技能库、MCP 导出入口均为中文；Kelivo 预览显示“对话 1 段（2 条消息）/项目：Multi B”，无 Conversations/Items；仅预览后取消，未写数据；无本包 FATAL/ANR。
- 边界：仅审计 Ox 自有字符串；其他语言包未补；无功能/格式变化。

## 0.38.0 备份聊天背景导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-77 | 0.38.0 QA | Kelivo/Chatbox 的 background 资源与会话背景未导入，聊天壁纸仍用全局/默认 | 解析 kind=background 与 session/copilot background 引用，写 Agent 目录 background.<ext>；sync 后 applyImportedBackground 写入按 Agent 主题 | 已修复 |
| R2-78 | 0.38.0 QA | 重复导入可能覆盖用户已设置的 Agent 自定义背景 | applyImportedBackground 检测到不同非空 backgroundImage 时跳过，结果 note 说明“其余保留已有自定义背景” | 已修复 |

- 设备：BG38 背景样例预览 1 个背景；导入 note“已套用 1 个聊天背景”，background.png 14,884 B、agent_555000078.json 指向该文件；原生聊天显示 BG38 壁纸；将背景改为 custom-bg.png 后重复导入，note“已套用 0 个；其余保留已有自定义背景”，原路径未变；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：仅图片背景、仅按 Agent；导出备份暂不含该背景；真实备份待复测。

## 0.39.0 Chatbox 导出包含 Agent 背景 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-79 | 0.39.0 QA | 0.38.0 导入的 Agent 背景不会进入 Chatbox 导出包，往返丢失壁纸 | `OxChatTheme.backgroundForAgent` 暴露路径；导出器写 `kind=background`，有聊天写 session background、无聊天写 copilot background | 已修复 |

- 设备：BG38 Agent 单 Agent 导出 15,580 B，manifest 1 session/1 background 资源、session background.storageKey 正确、2 条消息保留；删除原 Agent/主题后回环导入得到 background.png 14,884 B、backgroundMode=image，原生聊天 BG38 壁纸恢复；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：仅 Agent 图片背景；全局/按会话/视频背景不导出；导出不单独统计背景；外部 Chatbox 未实测。

## 0.40.0 导出按会话背景 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-80 | 0.40.0 QA | 0.39.0 导出只取 Agent 背景，用户给会话单独设置的壁纸不会进入备份 | `OxChatTheme.backgroundForDialog` 读取会话覆盖；导出有聊天的 Agent 时优先会话背景，空则回退 Agent 背景 | 已修复 |

- 设备：Agent 背景 BG38 + 会话覆盖 BG40；原生聊天显示 BG40；导出 14,441 B，资源解码 sha256 与 BG40 一致（非 BG38）；删除原 Agent/主题/会话文件后回环导入，background.png hash 仍为 BG40，原生聊天 BG40 恢复；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：回环后会话背景映射为 Agent 背景，不保留会话 specificity；仅图片；外部 Chatbox 未实测。

## 0.41.0 RikkaHub 文件附件导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-81 | 0.41.0 QA | RikkaHub 历史中的非图片 `document` part 只被跳过，`upload/` 里的附件不会进入本地会话 | 解析 document 的 `url`/`fileName`，匹配 `upload/` 后复制到 `files/rikka_upload_<name>`，写入 `OxChatEntry.filePath/fileName`；纯文件消息允许空正文，结果统计附件数 | 已修复 |
| R2-82 | 0.41.0 QA | RikkaHub 历史已写入 `chat.json`，但导入后原生会话仍只显示开场白，看不到历史消息 | 导入器记录实际新增消息的会话，导入完成后调用 `OxAgentsBridge.replayImportedHistory`，按时间回放原生消息 | 已修复 |

- 设备：1,064 B 最小备份含 1 会话、2 条消息和 37 B `report.txt`；首次导入结果“已导入历史消息 2 条，导入附件 1 个”，`chat.json` 两条且文件路径/文件名正确；修复回放后原生聊天显示 `report.txt` 文件气泡（37 B · TXT，正文“看附件”）与助手“收到附件”；重复导入无新增、无重复气泡；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：每条消息只取第一个 document；仅图片与 document，video/audio 跳过；仅匹配 `upload/`，不下载远程/data URL；文件条目由现有导出逻辑携带，本轮未做导出回环。

## 0.42.0 RikkaHub 多附件与音视频导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-83 | 0.42.0 QA | 0.41.0 每条消息只导入第一个 document，多张图片/多个文件会丢失；video/audio part 被直接跳过；视频/音频文件名会带 `rikka_upload_` 前缀 | 解析单条消息全部媒体（≤20），图片/文件按序拆成独立气泡；视频/音频作为文件气泡导入，名称回退 URL basename；去重键加入媒体 basename，共享节点时间戳 | 已修复 |

- 设备：2,016 B 样例含文字 + 2 PNG + `report.txt` + `clip.mp4` + `clip.mp3`；导入结果“已导入历史消息 6 条 / 导入图片 2 张 / 导入附件 3 个”；原生聊天显示两张照片气泡和 `report.txt`（24 B · TXT）、`clip.mp4`（24 B · MP4）、`clip.mp3`（10 B · MP3）文件气泡，助手回复正常；重复导入仍为 6 条、无重复；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：单条最多 20 个媒体；视频/音频无缩略图/内嵌播放器/时长；仅匹配 `upload/`；远程/data URL 不下载；导出回环未复验。

## 0.43.0 Kelivo / Chatbox 多附件与图文混合导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-84 | 0.43.0 QA | Kelivo/Chatbox 单条消息只导入第一张图片和第一个文件，且存在图片时文件被直接丢弃；混合附件会静默丢失 | `convertMessages` 按 `contentParts` 顺序收集全部可用媒体，首条与正文合并、其余拆成独立条目；移除图片优先丢弃文件的逻辑；沿用 40 图/20 文件上限 | 已修复 |

- 设备：2,392 B 样例（文字 + 2 PNG，其中一张 data URL 编码 + `notes.txt` + `data.json`）导入为 5 条消息、2 张图片、2 个附件；原生聊天显示带正文的绿色照片、橙色照片、`notes.txt`（19 B · TXT）、`data.json`（29 B · JSON）文件气泡和助手回复；重复导入替换历史后仍为 5 条/5 行、无重复；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：每段对话 40 图/20 文件、64 资源文件；拆分媒体共享原时间戳；`info` part 忽略；远程 URL 不下载；导出回环未复验。

## 0.43.1 多附件 Chatbox 导出回环 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-85 | 0.43.1 QA | 导出包里的 `resources/*.json` 附件被导入器的 zip 读取条件 `!n.endsWith(".json")` 排除，回环时 2 个附件只剩 1 个 | 只排除 `resources/` 之外的 JSON 元数据，允许资源目录中的 JSON 作为普通附件读取 | 已修复 |

- 设备：单 Agent 导出 3,578 B，含 5 条消息、2 个 image、2 个 raw-attachment；解码后图片/`notes.txt`/`data.json` 的 sha256 与源文件一致，敏感字符串扫描 0 命中；修复前回环预览“2 图/1 附件”，修复后同一 zip 为“2 图/2 附件”，回环后 5 条 chat 记录与原生两张照片、两个文件气泡完整恢复；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：回环 Agent id 追加 `_current`，保留原 Agent 时会新增副本而非替换；未与真实 Chatbox 客户端互导。

## 0.43.2 Chatbox 回环保持 Agent 身份 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-86 | 0.43.2 QA | 导出 session 的完整 Agent id 在导入时被再次加 `kelivo_chat_` 前缀并追加 `_current`，保留原 Agent 再导入会生成 `kelivo_chat_kelivo_chat_s1_current` 副本 | 导出 session 写 `orionekoAgentId`；导入器用 `Conversation.agentIdOverride` 直接使用该 id，并做 `[a-zA-Z0-9._-]` 清洗、去前导点和 80 字符截断；无字段的外部备份保持原逻辑 | 已修复 |

- 设备：导入样例得到 `kelivo_chat_s1_current`（tgId 555000091）；导出 3,593 B，session id 与 `orionekoAgentId` 均为该 id；保留原 Agent 回环，预览“已存在 1 段对话”，结果替换历史；Agent 目录仍只有 1 个，tgId 不变，chat.json 与原生消息均 5 条；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：`orionekoAgentId` 是 OrionekoX 扩展，真实 Chatbox 客户端会忽略；无聊天 copilot 未验证；非法/超长 id 会清洗或截断。

## 0.44.0 Chatbox 长期记忆与中文包刷新 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-87 | 0.44.0 QA | 导出 Toast 新增记忆行后，第二行被系统截断，用户只能看到主行而看不到记忆数量 | 把记忆数并入 `OxKelivoExportDone` / `OxKelivoExportAgentDone` 主行，删除独立记忆行；最终 Toast 完整显示“21 个智能体（含记忆 14 个）…” | 已修复 |
| R2-88 | 0.44.0 QA | `OX_ZH_PACK_VERSION` 停留在 99，升级安装后 `files/ox_zh-cn.xml` 不会从 APK 覆盖，新的中文说明/确认框/Toast 仍显示旧文案 | 将语言包版本提升到 100；设备确认 `ox_zh_version=100`，设置说明行与导出确认框均显示“长期记忆” | 已修复 |

- 设备：单 Agent 记忆导出 1,626 B，字段 73 字符、无敏感信息；删除后回导恢复 157 B / sha256 `a7744d01…`，重复导入 hash 不变、25 个 Agent 目录不变；最终全量导出 482,642 B，12 个 session 中 10 个、9 个 copilot 中 4 个带记忆，共 14 个，与 Toast 一致；主 Tab 与 Ox 设置切换无本包 FATAL/ANR；清理注入记忆与测试 zip，未执行 `pm clear`。
- 边界：`orionekoMemory` 仅 OrionekoX 识别；无聊天 copilot 的记忆导入未单独回环；导入覆盖而非合并记忆；未与真实 Chatbox/Kelivo 客户端互导。

## 0.45.0 RikkaHub / SillyTavern 角色卡导入 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-89 | 0.45.0 QA | “从 RikkaHub 备份导入”只接受 settings.json 或备份 zip，SillyTavern/RikkaHub 常见的 PNG/JSON 角色卡无法导入 | 按文件签名分流：PNG 解析 `tEXt`/`iTXt` 的 `ccv3`/`chara`，JSON 解析 V2/V3 wrapper 或扁平 V1；人设写 AGENTS.md，first_mes 生成开场白，PNG 存为 avatar.png；稳定 id 保证重复导入更新同一 Agent | 已实现并设备验证 |
| R2-90 | 0.45.0 QA | 角色卡预览沿用 RikkaHub 的“助手/Provider/MCP/技能”统计，出现 MCP 0、技能 0 和“将导入头像”等与卡片无关或与“已有头像保留”矛盾的文案 | 角色卡使用独立摘要“角色卡 1 张”、已有卡提示和卡片说明；头像提示改为“检测到 N 个（已有则保留）”；确认框标题加入“角色卡”；中文包 100→103 | 已修复 |

- 设备：689 B JSON 卡创建 `rikka_card_df478b83`（tgId 555000092），六节人设与开场白正确；同一卡 8,810 B PNG 更新该 Agent，`avatar.png` sha256 `4482184d…` 与源图一致，重复导入仍 1 个目录、hash 不变、1 条开场白；JSON-only 卡创建 `rikka_card_1ac480d6` 且无头像；无 `chara` 的 69 B PNG 正确报错。全程无本包 FATAL/ANR；删除测试 Agent 后对应 dialogs/messages 均为 0，Download 测试卡已清理。
- 边界：不支持压缩 `zTXt`；只导入 `first_mes`，未导入 alternate greetings、character_book、tags、正则；角色卡只导入不导出；导入后离线；未与真实 SillyTavern/RikkaHub 导出卡互测。

## 0.45.1 角色卡导入收拢 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-91 | 0.45.1 QA | Agent 列表自 0.8.114/0.8.115 已有 JSON/PNG 角色卡导入，0.45.0 又在 RikkaHub 设置页重复实现一套，出现两个入口与两份解析逻辑 | Agent 列表卡片入口改为复用 `OxRikkaImporter`，获得 `iTXt`、PNG 头像、稳定 id 与原生开场白；删除旧的随机 id/“· 副本”/本地解析；RikkaHub 行恢复备份专用，选到卡片时提示改用 Agent 列表入口 | 已修复并设备验证 |
| R2-92 | 0.45.1 构建 | 新增英文提示含 `agent list's card entry`，单引号触发 AAPT2 `Invalid unicode escape sequence`，release/debug 资源合并均失败 | 改写为不带所有格的 `agent list card entry`，重新构建 `BUILD SUCCESSFUL in 1m 57s` | 已修复 |

- 设备：Agent 列表导入 8,285 B PNG 生成 `rikka_card_b303c8ed`（tgId 555000094），`avatar.png` sha256 `33f7346e…` 与源图一致，原生开场白正确；同卡 346 B JSON 再导入只更新同一目录、头像 hash 不变、无 `· 副本` 和重复消息；RikkaHub 行选择卡片得到指向 Agent 列表的错误提示；297 B 最小备份 zip 仍能导入 1 个离线助手。全程无本包 FATAL/ANR；删除测试 Agent 后 555000094/555000095 的 dialogs/messages 均为 0，Download 测试文件已清理。
- 边界：同 0.45.0；Agent 列表卡片导入仍无独立预览对话框。

## 0.46.0 Chatbox 技能导出/回导 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-93 | 0.46.0 QA | 0.43.2 只给 session 写 `orionekoAgentId`，chatless copilot 没写；回导 copilot 时被重新加 `kelivo_` 前缀，生成 `kelivo_...` 重复 Agent，技能关联也写到副本上 | 导出 copilot 也写 `orionekoAgentId`；导入端新增 `assistantId()`，优先采用该字段，缺失时才走历史 `kelivo_` 规则。技能关联改为先导入 `skills/` 再按实际成功的 id 恢复 | 已修复并设备验证 |
| R2-94 | 0.46.0 功能 | Chatbox 导出不含 `SKILL.md` 与 Agent 的 `skill_ids`，回导后技能文件和关联都丢失 | 写入 `skills/<id>/SKILL.md` 与 `orionekoSkillIds`；全量导出带所有可见技能，选中导出只带被引用技能；上限 50/512KiB/8MiB；预览与结果新增关联计数 | 已实现并设备验证 |

- 设备：自建 90 B 技能 + 1 个 session Agent + 1 个 copilot Agent，多选导出 1,762 B；zip 内技能 sha256 `6cf9bd70…` 与源一致，copilot/session 均含原 id 与技能关联，`apiKey`/`baseUrl`/`Authorization`/`sk-`/`Bearer`/`ox1:` 扫描 0 命中。删除技能并清空两个 `skill_ids` 后回导：结果“更新 1 / 恢复 2 个技能关联 / 导入技能 1”，两个原 Agent 目录与 tgId 不变、无 `kelivo_` 副本、技能 hash 一致；重复导入仍 2 个目录、同 hash。全程无 FATAL/ANR；删除测试 Agent/技能后 555000098/555000100 的 dialogs/messages 均为 0。
- 边界：`skills/` 与私有字段为扩展，真实 Chatbox/Kelivo 未实测；只传 SKILL.md 正文；全量导出会带未引用技能；单 Agent 导出 Toast 未单独截图。

## 0.47.1 Chatbox 工作区导出/回导 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-95 | 0.47.0 QA | 导出完成 Toast 过长，系统只显示两行并在“0 个文件，…”处截断，工作区计数完全不可见 | 0.47.1 将文案压缩为“已导出：智能体 N、工作区 N、对话 N、消息 N、记忆 N、技能 N、文件 N”，工作区放在第二位；单 Agent 文案同样前置工作区；中文包 106→107 | 已修复并设备验证 |
| R2-96 | 0.47.1 QA | 删除 Agent 只清 `oxagents/<id>`，`oxworkspace/<id>` 文件保留；联系人页在本次进程内短暂残留该本地 Agent，重启后消失 | 不自动删除工作区，避免把用户文件当聊天记录一起删掉；本轮测试目录手工清理，联系人缓存问题记录为边界，待产品决策是否在删除对话框提供“同时删除工作区” | 已记录 |
| R2-97 | 0.47.1 实现 | 导出对每个选中 Agent 调用会 `mkdirs` 的 `workspaceDir()`，没有工作区也会生成空目录；无聊天/无人设的 Agent 工作区会写进 zip 却没有对应助手，回导时被静默跳过 | 新增只读 `workspaceDirIfExists()`；工作区收集移到“有聊天或人设才导出”的判断之后，只收集实际写入备份的 Agent。重建后复验：有/无工作区两个 Agent 一起导出，只出现有工作区 Agent 的 2 条，另一个目录没被创建 | 已修复并设备验证 |

- 设备：自建 session/copilot 两个 Agent（tgId 555000102/555000101）与 4 个工作区文件（20 B 文本、16 B 二进制、33 B 中文嵌套路径、23 B md）。选中导出 2,215 B、4 个 `workspace/` 条目，逐文件 sha256 与源一致，敏感字段扫描 0 命中；0.47.1 加 `keep.txt` 后导出 2,414 B、5 条，Toast 完整显示“工作区 5”；单 Agent 导出 2,055 B、4 条且只含该 Agent 文件。删除 3 个文件并保留 `keep.txt` 后回导，预览/结果均为 4，4 个文件逐字节恢复，`keep.txt` 保留；重复导入仍 4 条、2 个 Agent 目录、无 `kelivo_` 副本、tgId 不变。含 `../../evil0470.txt` 的 zip 被整包拒绝（`Invalid zip entry path`），未写出文件；含 2 MiB+1 B 文件的 zip 预览/结果为 5 而非 6，`big.bin` 未写入。最终重建再验：`酒吧炒饭A`（2 个工作区文件）+ 无工作区的 `PNG Card` 导出 14,893 B，只有前者的 2 条且逐字节一致，`PNG Card` 的工作区目录未被创建。全程无本包 FATAL/ANR；删除测试 Agent 后两个 tgId 的 dialogs/messages 均为 0，Download 测试 zip 已清理。
- 边界：`workspace/` 为扩展布局，真实 Chatbox/Kelivo 未实测；不保留空目录、权限、时间戳或符号链接；导出端上限只做代码审查，未造 2 MiB 源文件；删除 Agent 不自动删除工作区；联系人页缓存需重启刷新。

## 0.48.1 Chatbox 备份携带 Agent 偏好 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-98 | 0.48.0 功能 | Chatbox/Kelivo 备份只带人设、模型、技能、记忆和工作区，Agent 的工具权限、置顶、备注、开场白、推理参数、上下文长度与采样设置换机后全部丢失 | 导出在 session 和 copilot 写私有 `orionekoAgent`，仅在该 Agent 有非默认偏好时写入；导入时在 Provider/模型/技能接线完成后应用，预览与结果报告恢复数量；`headers`/`extra_body`/Provider 地址/密钥结构性排除 | 已实现并设备验证 |
| R2-99 | 0.48.0 QA | 首次回导后 `context_length` 从 32000/64000 变成 0：导入先应用偏好，随后 Provider 回退再用本机 `contextLength` 覆盖 | 0.48.1 把 `applyAgentPrefs` 移到 Provider、模型覆盖、请求头和技能关联之后，复验两个 Agent 的 32000/64000 正确恢复且其余字段无回归 | 已修复并设备验证 |

- 设备：自建 session Agent（tgId 555000110，2 条消息）与 chatless copilot（tgId 555000111），覆盖置顶、备注、开场白、high/required/32k、low/none/64k、penalties、top_k、seed、stop 与 `get_time`/`device_info` 的 deny/ask/auto。多选导出 1,881 B，`copilots.json` 与 session 均含完整 `orionekoAgent`；全文扫描 `api_key`、`baseUrl`、`base_url`、`headers`、`extra_body`、`Authorization`、`Bearer `、`sk-`、`ox1:` 均 0 命中，Toast 显示“智能体 2、工作区 0、对话 1、消息 2…”。重置偏好后回导：预览与结果均为“2 个智能体的偏好设置”，回读 `agent.json` 全部字段一致（含 32000/64000），tgId 与 2 个 Agent 目录不变、无 `kelivo_` 副本；重复导入幂等。原生工具权限页显示 `get_time=禁止`、`device_info=询问`，置顶 session 在列表中排首位。全程无本包 FATAL/ANR；删除两个测试 Agent 后 dialogs/messages 均为 0，Download 测试 zip 已清理，未执行 `pm clear`。
- 边界：`orionekoAgent` 为 OrionekoX 扩展，真实 Chatbox/Kelivo 会忽略且未互测；不导出 `headers`、`extra_body`、Provider 地址与密钥，换机后认证信息需重填；emoji、备注、开场白通过 `agent.json` 回读验证，未逐项截图欢迎语；temperature/topP/maxTokens 继续走原有 session/copilot 字段，不在私有对象中重复。

## 0.49.0 Agent 导出 SillyTavern/RikkaHub 角色卡 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-100 | 0.49.0 功能 | 角色卡自 0.45.0 起只能导入，不能导出，SillyTavern/RikkaHub 双向兼容缺一半；用户无法把本机 Agent 带到其他客户端 | Agent 长按菜单新增“导出角色卡”，原生格式框提供 PNG（头像 + `tEXt`/`chara` 内嵌 V2 JSON）与 JSON（纯文本 V2 包装）；六节 AGENTS.md 逐节还原，其他 Agent 整份人设进 description；无头像生成 512×640 占位图；导出明确排除密钥、Provider 地址与请求头 | 已实现并设备验证 |

- 设备：自建 `QA Card 0490`（tgId 555000103，256×256 头像、六节人设、开场白）与无头像 `QA Card NoImg 0490`。导出 PNG 2,420 B，IHDR/tEXt/sRGB/sBIT/IDAT/IEND 逐块 CRC 通过，`chara` Base64 解码后的 V2 JSON 六节与开场白一致，导出图像素与源头像完全相同；JSON 790 B 字段逐项一致；两者敏感字段扫描 0 命中。回导 PNG 得到 `rikka_card_67bc18d8`（tgId 555000104），姓名、开场白、六节人设（仅小节间空行规范化）与头像逐像素一致；再回导 JSON 只更新同一目录、无副本、头像保留。无头像 Agent 导出 20,888 B 占位 PNG（512×640），内嵌数据正确。全程无本包 FATAL/ANR；删除三个测试 Agent 后 555000103/555000104 的 dialogs/messages 均为 0，Download 卡片与遗留测试 zip 已清理，未执行 `pm clear`。
- 边界：未与真实 SillyTavern/RikkaHub 客户端互测；不导出 alternate greetings、character_book、tags 与正则；原生 Agent 的整份 AGENTS.md 进入 description；emoji 不是 V2 字段，回导为默认 🤖；头像统一缩放重编码，不保留 EXIF/ICC/动画。

## 0.50.0 Chatbox 备份携带 Agent 外观主题与字体 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-101 | 0.50.0 功能 | Chatbox 备份已带背景，但按 Agent 的颜色、气泡、圆角、边框、文字色、字号、行高、模糊/暗化与自定义字体不能往返，换机后需重配 | 导出在 session/copilot 写私有 `orionekoTheme`（仅存在按 Agent 覆盖时），清空绝对路径；背景走既有资源，聊天/代码字体写 `fonts/<agentId>/chat.ttf` 与 `code.ttf`（单 8 MiB、总 16 MiB、40 个）；导入校验字体并在 Agent 存在后应用，已有本地主题保留 | 已实现并设备验证 |
| R2-102 | 0.50.0 QA | 首轮导出只出现 108 KB 代码字体，2.37 MB 聊天字体被静默丢弃：导出器复用了 2 MiB 上限的通用资源读取函数 | 新增字体专用 `readFont`/带上限的 `readFile`（8 MiB），重建后同一 zip 同时包含 2,371,712 B 与 108,128 B 字体，sha256 与源一致 | 已修复并设备验证 |

- 设备：自建 chatless copilot `QA Theme 0500`（tgId 555000106，深蓝背景/dim 40/blur 2/收 #263238/发 #7C4DFF/230 透明度/20dp 圆角/#00E5FF 2dp 边框/1.12× 字号/1.3× 行高/2.37 MB 聊天字体/108 KB 代码字体）与 session Agent `QA Theme Session 0500`（tgId 555000107，1 条消息，绿色系主题、14dp 圆角、1 个字体）。导出分别为 1,453,353 B 与 1,383,082 B；`orionekoTheme` 在 copilot/session 中均存在，三个路径字段为空，背景解码哈希与字体哈希均与源一致，全文扫描 `api_key`/`base_url`/`baseUrl`/`headers`/`Authorization`/`Bearer `/`sk-`/`ox1:` 0 命中且无 `/data/data/` 绝对路径。删除主题/字体/背景后回导：copilot 恢复 1 主题/2 字体/1 背景，session 恢复 1 主题/1 字体/1 背景，颜色、圆角、边框、字号、行高（float 精度内）与全部哈希一致；原生会话可见深色气泡与青色边框。重复导入 copilot 备份为 0 主题并保留本地主题，字体文件数不增加。全程无 FATAL/ANR；清理两个测试 Agent、主题、字体与 zip 后 555000106/555000107 的 dialogs/messages 均为 0，未执行 `pm clear`。
- 边界：`orionekoTheme`/`fonts/` 为 OrionekoX 扩展，真实 Kelivo/Chatbox 未互测；只带按 Agent 覆盖，不带全局主题与按会话覆盖；已有本地主题优先；字体必须可被平台解析；视频背景不携带。

## 0.51.0 Chatbox 备份携带全局外观主题 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-103 | 0.51.0 功能 | 0.50.0 只让 Chatbox 备份携带按 Agent 主题，全局外观（所有 Agent 的默认颜色、气泡、全局背景与字体）仍不能往返，换机后需重配 | 全局自定义时在 `settings.json` 写私有 `orionekoGlobalTheme`，路径清空；全局背景写 scope=global 的 `background` 资源并用 storageKey 引用，全局字体写 `fonts/global/*.ttf`；导入在本地全局非自定义时恢复，已有自定义则保留并删除临时资产 | 已实现并设备验证 |
| R2-104 | 0.51.0 QA | 首次构建把“全局与按 Agent 外观主题、字体”加入 Ox 设置导出说明后，SettingCell 只显示两行，末尾“不含 API Key”被截断 | 缩短中英文 `OxKelivoExportInfo` 为“助手、聊天、记忆、技能、工作区、偏好、主题、字体、头像、附件；无 API Key”，中文包 111→112，重建后两行完整显示 | 已修复并设备验证 |

- 设备：自建 `QA Global 0510`（tgId 555000108）。设备原有全局主题先备份（sha256 `e070ff70…`），临时加入 2,371,712 B 聊天字体与 108,128 B 代码字体、16,360 B 背景。单 Agent 备份 1,457,238 B，`settings.json` 含 `orionekoGlobalTheme` 与背景引用；主题三个路径为空，背景 sha256 `88a527c8…`、字体 `9ca9debb…`/`db19a1fd…` 均与源一致，敏感字段 0 命中、无 `/data/` 绝对路径。删除 prefs 与字体后回导：结果“已恢复全局外观主题（含字体 2 个）”，颜色/圆角/边框/字号/行高与源一致，新背景与字体哈希一致，原生会话显示原全局深蓝背景/磨砂气泡/粉色边框/白字。重复导入为“全局外观已有自定义，保留本地主题”，prefs sha256 `29cba250…` 前后不变、无孤儿字体/背景。全程无 FATAL/ANR；清理测试 Agent、导入资产与 zip 后 555000108 的 dialogs/messages 为 0，恢复原 prefs 并复核 `e070ff70…`，未执行 `pm clear`。
- 边界：全局主题/背景/字体为扩展，真实 Kelivo/Chatbox 未互测；仍不携带按会话覆盖；本地已有自定义全局外观优先；字体必须可解析；视频背景不携带。

## 0.52.0 本地记忆库、遗忘曲线与阈值压缩 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-105 | 0.52.0 功能 | 长期记忆只有 memory.txt，没有检索/遗忘/压缩状态；上下文超过限制后更早对话直接丢失，模型也无法主动查证“以前是否提过” | 新增每 Agent 的 SQLite 记忆库，普通关键词与实验性本地哈希向量两种召回，7/90 天遗忘曲线、短期→长期提升；默认 45 条触发压缩，旧对话写为可读 turn/fact/summary；新增 `memory_search`/`memory_verify`，`remember`/`recall` 接入新库 | 已实现并设备验证 |
| R2-106 | 0.52.0 QA | 记忆页统计值过长，原生 SettingCell 把“本地记忆库”标题截成“本地…” | 标题改为“记忆库”，统计改为“63 条 · 长期 7 · 短期 56 · 事实 3”，重建后完整显示；中文包 113→114 | 已修复并设备验证 |
| R2-107 | 0.52.0 QA | `memory_verify("favorite color")` 因共享单词 “favorite” 误配到 “favorite editor”，把无关记录当成证据 | 严格匹配：多关键词查询至少命中 2 个有效词（去停用词），否则返回“未找到，不要当作事实”；复验 birthday 命中、favorite color 未找到 | 已修复并设备验证 |

- 设备：自建 `QA Memory 0520`（tgId 555000109）预置 60 条聊天，含姓名/生日/咖啡/城市/编辑器事实；用本地 mock OpenAI 服务记录请求并触发工具。普通模式首轮仅 17 条消息，system 含“上下文压缩/记忆库摘要/相关记忆”，`memory_search` 返回生日事实；库内 47 turn（46 短+1 长）/3 fact/1 summary，访问计数与长期提升正常。切换 `memory_mode=vector` 后生日/咖啡事实仍被召回（相关度 0.61，混合分）。`memory_verify("birthday")` 返回带日期证据；`memory_verify("favorite color")` 严格匹配返回未找到。记忆页显示“63 条 · 长期 7 · 短期 56 · 事实 3”。全程无本包 FATAL/ANR；删除测试 Agent、记忆库行与测试 tgId 的 dialogs/messages 后计数均为 0，恢复模拟器代理与无障碍，未执行 `pm clear`。
- 边界：本地哈希向量不是语义 embedding；摘要为抽取式；记忆库未进入 Chatbox/.oxagent 备份；旧 memory.txt 不自动迁移；群聊按 Agent 共用；阈值按消息条数；应用私有 SQLite 未加密。

## 0.53.0 记忆检索 v2（BM25/幂律遗忘/事实失效）QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-108 | 0.53.0 研究 | 0.52.0 的向量只是 128 维哈希投影，普通模式只是 token 重合，遗忘是固定指数半衰期；没有事实合并、矛盾处理、MMR 或时间有效性，效果无法量化 | 对照 Mem0/MemGPT/Graphiti/Generative Agents/FSRS/MemoryBank/LangMem，改为 BM25+向量+retention/importance/access 混合分，加 MMR、幂律遗忘、stability/difficulty、事实 ADD/UPDATE/失效与摘要清理 | 已实现并设备验证 |
| R2-109 | 0.53.0 QA | 英文事实抽取只按中文标点/换行分句，整段“姓名+生日+咖啡”被合并成一条事实，生日更新无法单独失效旧值 | 分句增加英文句号等标点；复验姓名/生日/咖啡分为独立事实，3/14 被 3/15 正确失效 | 已修复并设备验证 |
| R2-110 | 0.53.0 QA | 压缩批次先生成事实再生成摘要，旧摘要仍带着已被失效的 3/14 和 blue；`memory_verify` 也会把旧对话当成当前证据 | 摘要生成后执行 `scrubAllInvalidated` 删除失效事实行；`strictVerify` 事实优先，有事实命中时不再返回旧 turn；复验摘要无旧值、verify 只返回 3/15 与 green | 已修复并设备验证 |

- 设备：自建 60 条消息 fixture（生日 3/14→3/15、blue→green、姓名/咖啡/杭州），mock OpenAI 触发工具。最终有效事实 name/coffee/Hangzhou/birthday 3/15/green，旧 3/14 与 blue 的 `invalid_at` 非 0 且不进入默认召回；摘要旧值被清理。`memory_search` 首条为 3/15 有效事实，`memory_verify("birthday")` 只返回 3/15，`memory_verify("favorite color")` 只返回 green；向量模式 coffee 相关度 0.83，召回后 stability 略升、difficulty 从 4.0 降到 3.6。全程无 FATAL/ANR；测试 Agent/记忆行/chats 清理为 0，恢复代理与无障碍。
- 边界：仍是本地哈希向量非语义 embedding；摘要抽取式；未做图记忆、token 压力预警和模型蒸馏；Provider 当前无 embedding 模型；旧 turn 在历史查询中仍可能出现（已由事实优先和提示词约束）。

## 0.54.0 异步模型蒸馏 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-111 | 0.54.0 功能 | 压缩摘要与事实全由规则抽取，容易被 filler 淹没，缺少模型级抽象摘要与事实抽取 | 新增 `OxMemoryDistiller`：压缩批次 ≥8 条时后台调用 Agent 自己的模型，严格 JSON 返回 summary + facts；不阻塞聊天、失败保留抽取式；模型摘要 importance 0.9，digest 最多注入 1 条摘要且优先模型摘要 | 已实现并设备验证 |
| R2-112 | 0.54.0 QA | 第三人称模型事实 `Lin Zhou's birthday…` 与 `…favorite color…` 的 subject 都回退成 `lin zhou s`，互相失效并连带清空模型摘要 | 新增 possessive `[a-z]+'s` 规则，主题在 is/are/like/live/work/and 等词截断；复验 birthday/favorite color 分开、模型事实与摘要有效、颜色合并进绿色事实 | 已修复并设备验证 |

- 设备：60 条 fixture + mock `[MEMORY_DISTILLER]` 返回固定摘要与两条事实。首轮压缩后后台请求到达 mock，聊天回复不等待；库内模型生日事实有效（subject=birthday），颜色事实合并进现有绿色事实，模型摘要 `source=model`、importance 0.9 有效；下一轮 system 只含 5 条有效事实 + 1 条模型摘要，旧 3/14/blue 与抽取摘要不再注入。无重复蒸馏请求、无 FATAL/ANR；清理测试 Agent/记忆行/chats 为 0，恢复代理与无障碍。
- 边界：仅压缩批次触发并消耗 Agent token；模型输出只做结构/长度校验，未做事实核查；Provider 需支持 JSON 响应；仍无真正语义 embedding；失败路径仅代码审查、未用坏端点回放。

## 0.55.0 开源式 compact QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-113 | 0.55.0 功能 | compact 只有消息数阈值和规则摘要，没有 token 压力、TextRank/Selective Context 或滚动摘要窗口；长上下文可能没有被真正压缩 | 新增 `OxContextCompactor`（token 估算、TextRank、去重/MMR、300-token 摘要）；消息阈值或 contextLength 45% token 压力触发；保留窗口按 55% 预算；递归摘要合并上一版摘要 | 已实现并设备验证 |
| R2-114 | 0.55.0 QA | token 压力下保留窗口仍被固定 `max(8, threshold/3)=15` 覆盖，500-token 上下文保留了 19 条、只压缩 1 条 | token 压力时改用 55% 预算、最低 256 tokens/4 条，不再被固定条数覆盖；复验压缩 13 条、保留 7 条、约 790 tokens | 已修复并设备验证 |

- 设备：60 条 fixture 的 TextRank 摘要 249 字符并含咖啡/杭州；模型 `MODEL_SUMMARY` 111 字符生效，抽取摘要被 cutoff 失效，旧 3/14/blue 不注入。20 条长消息、context_length=500 的 token 压力测试显示“压缩 13 条、保留 7 条、约 790 tokens”。无 FATAL/ANR；测试 Agent/记忆行/chats 清零，代理与无障碍恢复。
- 边界：TextRank 是离线词法近似而非 LLMLingua-2 语义压缩；token 估算非精确 tokenizer；摘要预算 300 tokens；无 70% 预警阶段；外部 Harness 的 compact 路由留待 0.56.0。

## 0.56.0 Harness 所有权路由 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-115 | 0.56.0 架构 | 本地 compact/记忆注入对所有 Agent 生效；外部 Harness（Claude Code、OpenCode、CommandCode、OpenClaw、OxLinux）自己有会话与 compact，本地再压一遍会造成两份分叉历史 | 新增 `OxHarnessAdapter` 能力表与所有权分流：仅 API 直连走本地 compact/写库/注入；外部 Harness 不压缩、不写库、不注入，只提示由该 Harness 管理并通过 `maybeCompact` 请求其 compact；OxLinux 走容器命令，其余无传输层时明确返回 `transport-not-connected` | 已实现并设备验证（路由隔离） |
| R2-116 | 0.56.0 QA | Harness 选择、会话 ID 与 compact 命令若只存在内存，换机/导出后丢失；无默认模板时用户不知道命令格式 | Agent 持久化 `harness`/`harness_session`/`harness_compact_command`；资料页原生选择器与两行输入；Chatbox `orionekoAgent` 导出/导入并校验；仅为公开 CLI 已确认的 Claude Code/CommandCode 提供默认模板，其余留空 | 已实现并设备验证（UI/字段） |

- 设备：`qa_harness_0560`（tgId 555000124，harness=claude，60 条历史，本地 mock）。发送 `hello` 后抓包 22 条消息，`has_compression`/`has_digest`/`has_recall` 均为 false，system 尾部为“# 外部 Harness…由「Claude Code」管理，本地不注入压缩摘要或记忆库。”，该 Agent 记忆库行数 0；设置页与 12 项选择器截图已存档。无本包 FATAL/ANR；测试 Agent/记忆行/chats 清零，代理与无障碍恢复，未执行 `pm clear`。
- 边界：0.56.0 只是路由/隔离基础，不等于所有 Harness 已接通。Claude/OpenCode/CommandCode/OpenClaw 无 SSH/ACP/Gateway/PTY 传输层，默认 compact 模板未在真实会话验证；Hermes/CodeBuddy/DSH/Pi 本机未安装；外部 Harness 的工具协议、多模态、非文本事件与本地记忆镜像均未实现；本地最近 20 条窗口仍在。能力矩阵见 `docs/harness-adapters.md`。
- 边界：TextRank 是离线近似，不是 LLMLingua-2 语义 token 压缩；token 估算非精确 tokenizer；摘要预算 300 tokens；无 70% 预警阶段；外部 Harness 不能使用本 compact，需下一轮 HarnessAdapter 路由。

## 0.57.0 真实 Harness 传输 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-117 | 0.57.0 功能 | 0.56.0 只有 Harness 能力表与路由；Claude Code/CommandCode/OpenCode/OpenClaw 没有可用传输，OxLinux 也只有一次性 `proot -c`，无法验证各自 compact/resume | 新增电脑端认证桥接（HTTP+NDJSON）与四个真实 adapter；App 新增 `OxHarnessTransport`，外部 Harness 回合不再走本地模型；OpenClaw 用 Gateway session + `sessions compact`，CommandCode 用 NDJSON `--resume` + 交互 PTY compact，OpenCode 用 `run --session` + session compact API，Claude 用 print session + `/compact`；OxLinux 改为常驻 proot shell | 已实现；OpenClaw/CommandCode/OpenCode 真实验证；Claude 传输验证但 Provider 403；OxLinux 受 Android exec 限制 |
| R2-118 | 0.57.0 QA | 接入后外部 Harness 因 `base_url` 为空被当回声；`memory_mode=off` 使 `maybeCompact` 不执行；桥接 compact 被空模板提前拦截；Test health 在 worker 线程改 View；重装后 rootfs 仍重复下载 | 外部 Harness 永不进入 echo；compact 判定移到本地记忆开关之前；桥接 compact 先于模板检查并移除未验证默认模板；health 回调切主线程；复用已解包 rootfs 并识别 `/usr/bin/dash` 等真实 shell | 已修复并设备验证 |

- 设备：debug 0.57.0（5709）的 `bridge_test_570`（tgId 555000012）通过
  `10.0.2.2:8765` 连续完成 5 轮 OpenClaw 回复，原 token 在 restart/compact
  后仍保持；历史超过阈值后触发
  `「OpenClaw」原生压缩完成`（`ok=True ms=7837`），同轮回复正常。资料页
  Test 显示 `Connected`，保存包装后的 token 重启仍可连接。
- 真实 CLI：OpenClaw/CommandCode/OpenCode 的 turn1→turn2(resume)→compact→
  turn3 分别返回 `BRIDGE-OCL-731`/`BRIDGE-CC-731`/`BRIDGE-OC-731`；
  Claude 返回 `403 Request not allowed`，compact 命令返回
  `Not enough messages to compact.`。13 个桥接测试通过。
- 边界：Claude 账号被 Provider 拒绝，不宣称支持；OpenClaw 无 token 流和
  工具结果；附件是路径交接，OpenCode 原生 `--file`；桥接无 TLS；
  OxLinux 常驻 shell 被 Android `execve` 策略拒绝；外部记忆镜像、按
  Harness 阈值、Harness 自身压缩状态读取仍未实现。测试 Agent/tgId 的
  dialogs/messages 清理为 0，代理与无障碍保持，未 `pm clear`。

## 0.58.0 Harness 上下文状态 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-119 | 0.58.0 功能 | App 只能按本地历史条数猜何时 compact，不知道 Harness 自己的上下文用量与 `should_compact`；OpenClaw/CommandCode/OpenCode 的 usage 也没有进入 App | 桥接记录每会话 `last_context`（只存 token/预算/模型），`done` 附带 context，新增只读 `GET /context`；OpenCode 从私有 serve 的 session API 补 tokens/model/cost；App 新增 `OxHarnessTransport.context()`、资料页 Harness context 行与 `shouldCompact()`，并让 Harness 自报建议参与 compact 判定 | 已实现并设备验证（读取/展示）；`should_compact=true` 真机触发未构造 |
| R2-120 | 0.58.0 QA | context 读取若只依赖 turn 事件，OpenCode 的 `run` 事件可能没有 token；资料页行值过长会像 0.57 的 Test 行一样挤压 | OpenCode context 额外 GET `/api/session/<id>` 合并 `tokens/model/cost`；UI 只显示短摘要（in/out/无需 compact），错误用红色文本；中文字符串包 117→118 | 已修复并设备验证 |

- 设备：debug 0.58.0（5809）的 `ctx_test_580`（tgId 555000014，OpenClaw）发 `CTX-APP-0580`；App 日志 `harness context {"should_compact":false,"context_budget":1048000,"usage":{...}}`，资料页显示 `in 530 · out 179 · 无需 compact`。真实 `/context`：OpenClaw budget 1,048,000/input 503/output 44；CommandCode inputTokens 16,669/output 9；OpenCode input 2,916/output 25/reasoning 178/cache_read 4,224。15 个桥接测试通过。
- 边界：外部记忆镜像/所有权交接仍未实现；Harness 自报 `should_compact=true` 的真实触发未构造；OpenCode context 依赖私有 serve；Claude 仍 403；附件仍为路径交接。测试 Agent/tgId 的 dialogs/messages 清零，桥接停止、token 删除，代理与无障碍保持，未 `pm clear`。

## 0.59.0 外部 Harness 只读记忆镜像 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-121 | 0.59.0 功能 | 外部 Harness 的对话不会进入本地记忆库；没有显式开关区分“镜像”和“注入”，切换 Harness/API 后无法利用历史 | Agent 新增默认关闭的 `harness_mirror`；把 user/assistant 与 App 收到的 tool 事件写入 `OxMemoryStore`，source 标记 `harness-mirror`/`harness-mirror-tool`；稳定文本 hash 去重；外部模式仍不 digest/recall，切回 API 后可召回；记忆页显示镜像统计/标签；Chatbox 导出/导入该开关 | 已实现并设备验证 |
| R2-122 | 0.59.0 QA/运维 | 监控器投递后先 disable，调度器随后写回 `enabled=1`，本轮工作期间可能二次投递 | 观察到 run 后连续 12 秒重复 disable 再进入冷却；服务重启并核对 automation `enabled=0` | 已修复并重启监控 |

- 设备：debug 0.59.0 的 `mirror_test_590`（tgId 555000015，OpenClaw，mirror=true）两次相同文本，SQLite 3 行（1 user 去重 + 2 assistant），重启后保持；记忆页 `3 条 · 长期 0 · 短期 3 · 事实 0 · 镜像 3`，library 对话框 3 条“镜像对话”。切回 API + 本机 mock 后 system prompt 召回三条镜像（0.89/0.81/0.80）。15 个桥接测试通过，无本包 FATAL/ANR；测试 Agent/memory 行/tgId dialogs/messages 清零，桥接/mock 停止、token 删除，代理/无障碍恢复。
- 边界：OpenClaw `--json` 无工具结果；镜像只写本地 SQLite 明文；镜像不反注入 Harness；未构造真实 `should_compact=true`；Claude 403、OxLinux exec 限制未变。

## 0.60.0 OxUI preview QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-123 | 0.60.0 功能 | 前端只有 Telegram 原生 UI/既有美化；用户要求增加 OxUI(preview)，入口放引导页与 Ox 设置，OxUI 不套用 Telegram UI 但功能要同步 | 新增 `interface_mode` 偏好、Ox Settings Interface 行、引导页第 6 步 Interface 卡；新增独立 `OxUiPreviewActivity`（serif 编辑式对话、Agent rail、ink/ chartreuse 设计 token），读真实 Agent/历史，发送复用 `OxAgentsBridge.sendFromOxUi`；Markdown 转 native spans | 已实现并设备验证（preview） |
| R2-124 | 0.60.0 QA | 第一版助手竖线用 match_parent 高度，被 ScrollView 拉成整屏长线；Markdown 的 `**` 原样显示；字符串只硬编码中文 | 竖线改固定 46dp 短标记；Markdown 经 `Html.fromHtml` 转粗体/代码/标题；所有 OxUI 文案走中英文资源，中文包 119→121；截图复查 | 已修复并设备验证 |

- 设备：debug 6009 的 `Studio Test`（tgId 555000016，合成历史）打开 OxUI；Ox Settings 与引导页入口均可用；内置 Echo 发送 `OxUI test 0600`，OxUI 与 Telegram Echo 会话同时出现用户消息和回复（id 100067/100068）。release 6009 覆盖启动无 FATAL/ANR；测试 Agent/chat/dialog/messages/memory 清零，代理/无障碍保持。
- 边界：OxUI 仍是 preview，应用根未切换；只有单聊，缺群聊/附件/媒体/搜索/设置/记忆页/Agent 详情/平板双栏/动态主题/reduced-motion；外部 Harness 回合结束才落盘，非 token 级流式。下一轮做第二轮视觉评审与 root 切换/导航。

## 0.61.0 OxUI root 与导航 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-125 | 0.61.0 功能 | 0.60 OxUI 只是从设置打开的预览，应用根仍是 Telegram UI；用户要求 root 切换、可切回、Agent 列表/搜索/详情/记忆 | LaunchActivity 对普通 launcher 启动按 `interface_mode` 重定向 OxUI；OxUI root 的 Telegram UI 清任务切回；设置/引导页选择 OxUI 后重启进入 root；新增 Agents 面板/搜索/详情/记忆/长按复制 | 已实现并设备验证 |
| R2-126 | 0.61.0 QA | 详情页 header 被 LinearLayout 拉满并垂直居中；搜索自动键盘导致详情 resize 错位；列表摘要残留 `**`；消息 selectable 抢走长按复制 | 详情 header 显式 44dp + TOP；打开详情前隐藏键盘；摘要剥离 Markdown；消息改非 selectable 由 OnLongClick 复制；root 返回键改为 moveTaskToBack | 已修复并设备验证（复制未做剪贴板回读） |

- 设备：debug 6109，`interface_mode=oxui` 普通启动直接进 OxUI；Telegram UI 按钮把偏好改回 telegram 并进 LaunchActivity；设置再选 OxUI 后清任务重启回 OxUI。合成 `Studio Test`（tgId 555000017）搜索只剩自身，长按详情显示 1 条事实记忆，Chat 返回；截图 root/search/detail 归档。release 6109 无本包 FATAL/ANR；测试 Agent/chat/dialog/messages/memory 清零，偏好恢复 telegram，代理/无障碍保持。
- 边界：root 只对普通 launcher 启动；搜索仅 Agent 名称/备注；详情记忆只读；群聊/附件/媒体/会话搜索/设置/Agent 编辑/平板/动态主题/reduced-motion/无障碍未做；长按复制未做剪贴板自动回读。

## 0.62.0 OxUI 附件与群组 groundwork QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-127 | 0.62.0 功能 | OxUI 只有文字收发；用户无法从独立前端发图片给 Agent | composer ＋ 走 SAF 选图 → 复制到 files/oxui_media → SendingMediaInfo → 1:1 `handleOutgoingMedia` / 群组 `handleOutgoingGroupMedia`；渲染 `imagePath` 圆角图，signature 含 imagePath，纯图片条目可渲染；发送就绪含图片态 | 已实现并设备验证（echo 真实 photo 消息 + 视觉回合） |
| R2-128 | 0.62.0 功能 | OxUI 无群组概念；本地发送路径可能被误用于真实 Telegram 群 | `OxGroupStore.markLocallyCreated/isLocallyCreated` + createLocalGroup 打标；Agents 面板 Groups 区列出本地创建群并支持群对话/发送；rail 群 chip | 已实现；端到端暂缺（createLocalGroup 无调用方，New Group 离线不走本地创建），如实标注 |
| R2-129 | 0.62.0 QA | 子代理视觉评审 P0：MUTED/LINE 对比不足、回复点静止、每次刷新强拉底部、pending 失败无反馈 | MUTED #566259 / LINE 55% ink；呼吸动画受系统动画缩放门控；自动滚动仅近底部/刚发送；120s 未落盘显示 coral “消息未发送 · 点按重试”并可重发 | 已实现并设备验证 |

- 设备：debug 6209，echo Agent + 480x320 测试图，picker→预览→发送，logcat `handleOutgoingMedia dialog=555000018 count=1`、photo 消息 id=100072 与 echo 回复 id=100073 同时出现在 OxUI 与 Telegram UI。15 个桥接测试通过；测试数据清理为 0，`interface_mode` 恢复 telegram。
- 边界：群组创建链路未接通（createLocalGroup 无调用方，New Group 离线停留服务器请求）；附件仅图片；会话搜索/设置/记忆/Agent 编辑/平板/暗色/reduced-motion/无障碍清扫未做。

## 0.63.0 本地群端到端 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-130 | 0.63.0 功能 | New Group 流程在离线本地账号停在服务器请求（ConnectionsManager 丢弃请求且不回调），createLocalGroup 无调用方，群组无法端到端 | createChat 在 syncAgents 后增加 agent-only 分支：全本地 agent → createLocalGroup + chatDidCreated(chatId)；空 agent 集或异常 → chatDidFailCreate 恢复创建页；真实联系人跳过；通知经 runOnUIThread | 已实现并设备验证（创建→列表→发送→同步） |
| R2-131 | 0.63.0 QA | renderGroupList 开头 removeAllViews 清掉 agent 行；冷启动群标题显示 Group <id>；rail 群 chip/agent chip 目标重置在 0.62 重放中丢失 | 去掉多余清空、getChatSync 回退标题、补齐群 chip 与目标重置；验证选群/切回 | 已修复并设备验证 |

- 设备：debug 6309，Telegram UI 创建 `OxUI QA 630`（logcat `createLocalGroup id=1800000000 agents=1`、`created_1800000000=true`），OxUI Groups 区列出、rail ink 群 chip、发送 GROUP-TEST-630（id=100076）+ echo 回复（id=100077），Telegram chats 列表同步显示。15 桥接测试通过；测试 Agent/群数据清理 0，interface_mode 恢复 telegram，无 FATAL/ANR。
- 边界：头像离线上传死锁未解（New Group 选头像会卡，QA 未选头像）；群回复 meta 用群名（无 sender 字段）；“Reply to <agent>” 定向、视觉评审 P1/P2 清单未做；Claude 403、OxLinux exec 限制未变。

## 0.64.0 群头像离线分支与定向回复 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-132 | 0.63.0 遗留/评审 HIGH | New Group 选头像走 FileUploader，agent-only 下上传请求被 ConnectionsManager 丢弃且无回调，创建页转圈死锁 | ImageUpdater.processBitmap agent-only 分支直接回调 didUploadPhoto(null,…,big,small)+cleanup；GroupCreateFinalActivity 上传进度/门槛按 agentOnly 跳过；chatDidCreated → attachLocalChatAvatar：构造 TL_chatPhoto（photo_id=\|chatId\|*31+13、volume_id=-photoId、local 97/99），把 ImageUpdater cache 文件按 getPathToAttach 变体拷贝成反序列化同名文件 | 已实现并设备验证（含 force-stop 重启后头像仍在） |
| R2-133 | 0.63.0 遗留 | 群回复 meta 只有群名，看不出哪位 agent 在说话 | OxChatEntry 新增 sender（JSON，向后兼容缺失）；OxGroupStore.appendChat sender 重载；finishTurn 群分支写 s.agent.id；OxUI meta resolveAgentName 显示 "Name · time" | 已实现并设备验证（Translator · 01:38）；0.64 前历史条目无 sender，不显示归属 |
| R2-134 | 视觉评审 item 11 | 群里无法指定某位 agent 回复 | OxUI 助手行长按菜单 Copy / Reply <name>，回复 chip；OxAgentsBridge.sendLocalGroupTextTo 强制 startGroupTurn(forced)，log groupReply forced agentId=… | 已实现并设备验证（TARGET-640 由 Translator 回复，未触发其他成员） |

- 设备：debug 6409，`OxUI AVA 640`（id=1800000002）创建链路 logcat
  `createLocalGroup id=1800000002 agents=1` →
  `attachLocalChatAvatar attached chatId=1800000002 photoId=55800000075` →
  `persistAndShow shown did=-1800000002`；群聊头部渲染橙色头像，
  force-stop 重启后 chats 列表仍渲染。sender/定向在同一 QA 群验证。
- 坑：软键盘把 ✓ FAB 顶到 (964,1411)（原 (964,2300)），按旧坐标点到键盘；
  根级 `assembleAfatStandalone` 会构建从未使用的 `:TMessagesProj_App` 并在
  media3 variant 解析 1 秒失败，必须模块级调用。
- 边界：改已有群头像仍走服务器路径；定向回复无引用语义；旧群历史无
  sender 不迁移；视觉评审 P1/P2 其余项未做。

## 0.65.0 已有群改/删头像离线分支与定向回复修复 QA 记录

> **R2-134 更正（2026-10-29）**：0.64.0 的 “Reply to \<agent\> 定向已设备验证”
> 结论不成立。`sendMessage` 在 `dispatchSend` 之前调用 `clearReplyTarget()`，
> `dispatchSend` 读到的 `replyAgentId` 恒为 null，强制路径从未生效；当时 TARGET-640
> 由 Translator 回复是因为单成员群普通轮换同样选中它，且 logcat 中并没有出现
> `groupReply forced` 行。0.65.0 修复（clear 后移）并在设备上以
> `groupReply forced agentId=echo` 日志复验。

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-135 | 0.64.0 遗留 | 已有本地群改头像走 `changeChatAvatar`（TL_channels_editPhoto），agent-only 下静默死锁 | `ChatEditActivity.didUploadPhoto` 最前插入 agent-only 分支（全空输入判定）→ `updateLocalChatAvatar`（photoId=millis*1000+chatId%1000，与创建 id 永不碰撞、同毫秒跨群唯一）；不设 hasUploadedPhoto，UI 走 updateInterfaces→setAvatar；首版插在外层 if 链内是死代码，经子代理评审前移修正 | 已实现并设备验证（改+重启持久化） |
| R2-136 | 0.65.0 功能 | 群头像删除在 ChatEditActivity 无入口：ImageUpdater useAttachMenu=true 时 onDelete 回调被 attach sheet 丢弃 | agent-only 且有头像时 `openSetPhotoAlert` 弹 Set New Photo / Remove photo 对话框；Remove → `removeLocalChatAvatar`（TL_chatPhotoEmpty，TL_chat 无条件序列化 photo 故无需清 flag） | 已实现并设备验证（init 头像恢复、无死锁） |
| R2-134' | 0.65.0 修复 | 定向回复强制路径死代码（见上更正） | `clearReplyTarget()` 移到 dispatchSend 之后；设备复验 `groupReply forced agentId=echo` | 已修复并设备验证 |
| R2-137 | 设计注（子代理） | 定向回复无 quote 语义，agent 看不到在回复什么 | OxChatEntry 增 replyToSender/replyToText（快照 ≤160 字，向后兼容不迁移）；groupMessages 在最后一条 user 消息注入 `（回复 @Name：「摘录」）` 前缀；Telegram UI 路径 onGroupMessage 对 agent 消息抽取同一快照；OxUI chip 显示前 24 字、气泡上渲染 MUTED 引用行 | 已实现并设备验证（落盘字段 + 气泡渲染） |
| R2-138 | 视觉三轮评审 | 图片与说明割裂为两气泡；竖图无高度上限；发送后滚动竞争截断末条 | addUserImage 统一 ink 卡片（图上文下）+ 0.6 viewport CENTER_CROP 上限 + fullScroll 后二次对齐 | 已实现并设备验证（CAP-650 图文一体卡） |
| R2-139 | 视觉三轮评审 | 头部 Preview 徽章多余且稀释 chartreuse 信号；rail 无渐隐；强调条不随长文；列表副标题残留 markdown 行首 | 删除徽章+统一 Agents 按钮胶囊；rail 36dp fading edge；强调条 MATCH_PARENT；副标题剥离 `- `/`#`/`1.` | 已实现并设备验证 |

- 设备：debug 6509。评审（子代理，11 张截图）12 项清单本轮落地 7 项；
  遗留：自定义长按菜单、用户气泡时间戳、类型尺度吸附、附件行容器、
  竖长截图卡片渲染异常（疑 pending/历史渲染交互）。
- 评审另发现 backlog：群 emoji 头像创建在 agent-only 下静默丢头像；
  用户头像族 RPC 与 PhotoViewer 主图操作未守护；changeChatTitle 离线回滚。

## 0.66.0 视觉遗留收口与 OxUI 会话搜索 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-140 | 视觉三轮 P1-3 | 长按菜单为系统 AlertDialog，与 OxUI 语言脱节 | menuHost 透明层 + bone 卡片（plate(0xFFF7F3EA,16,LINE)、elevation 12dp、48dp 行高、15sp sans-serif-medium、Ripple、SIGNAL Reply 圆点、锚点定位/返回键/重建自动关闭） | 已实现并设备验证 |
| R2-141 | 视觉三轮 P2-3 | 用户气泡无时间戳、长按仅静默 copy | 两分支右对齐 11sp mono 时间（发送中不显示）+ 长按弹出同款菜单（仅 Copy） | 已实现并设备验证 |
| R2-142 | 视觉三轮 P2-1 | 字号散乱（mono 10.5/11/11.2/11.5、气泡 15.5 vs 16.2 等） | 按审计表 30 处向 11/12/14/16/17/28 吸附；RIPPLE/SENDING_ALPHA 常量；sansMedium helper | 已实现并设备验证 |
| R2-143 | 视觉三轮 P2-4 | 附件行裸贴文件名 | PAPER 胶囊容器 + 文件名 12sp + `WxH · KB` 11sp mono 副行 | 已实现并设备验证 |
| R2-144 | 0.65 遗留 | “竖长截图卡片图文割裂” | 设备 dump 证实 caption 与 ImageView 左缘对齐、垂直零间隙——同一卡内，属读图误判；补图片卡内水平居中 | 已澄清（非 bug）+ 观感修正 |
| R2-145 | 0.66 功能 | 面板搜索只能匹配 agent 名/备注 | 会话搜索 v1：本地聊天扫描（每目标≤3/全局≤12、220ms 防抖、scanGen 防过期）+ Messages 小节 + SIGNAL 高亮 + 点击打开 | 已实现并设备验证 |
| R2-146 | 0.66 QA | 批量替换误伤 sansMedium() 定义 → StackOverflowError 启动崩 | 修复方法体；记录“批量替换必须排除定义处” | 已修复并复测 |

- 设备：debug 6609，1:1 菜单/时间戳、blue 搜索高亮、附件胶囊、群菜单
  Copy/●Reply、chip 引用摘要、groupReply forced -1800000004、气泡引用行。
- 遗留：会话内搜索条/上下导航/图片检索（v1.5）、菜单 >5 行滚动、暗色/
  reduced-motion/平板/可访问性清扫、设置/Agent 编辑 OxUI 化。

## 0.67.0 暗色 + reduced-motion/可访问性 + 会话内搜索 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-147 | 0.67 功能 | OxUI 只有亮色；暗色系统下仍为骨纸底 | 颜色常量改实例字段，resolvePalette 按 night mode 解析双调色板（语义保持：INK=气泡/正文），状态栏图标随暗色反转，菜单纸色并入 MENU_PAPER | 已实现并设备验证（night yes/no） |
| R2-148 | 0.67 功能 | 系统关闭动画时仍有 overlay/首渲染动画 | animationsEnabled() 读 ANIMATOR_DURATION_SCALE，关闭时直接落终态 | 已实现并设备验证（scale 0 面板秒开） |
| R2-149 | 0.67 功能 | 纯符号控件无 a11y 描述 | ✕/＋/↑/⌕/rail chips/菜单行/图片卡 补 contentDescription；dump 验证齐全 | 已实现并设备验证 |
| R2-150 | 0.67 功能 | 会话内无法检索消息 | v1.5：header ⌕ 搜索条 + n/N + ↑/↓ 循环导航 + 80dp 留白滚动 + 900ms SIGNAL 整行闪烁；150ms 防抖；行 ts tag | 已实现并设备验证（blue 1/2→2/2） |

- 设备：debug 6709，暗色/日间截图、reduced-motion、搜索命中导航、
  a11y dump 全部通过；release 无 FATAL。
- 遗留：应用内手动主题切换、跨会话搜索聚合排序、82 行窗口外命中跳转、
  TalkBack 全流程走查、设置/Agent 编辑 OxUI 化。

## 0.68.0 OxUI 设置页与 Agent 编辑 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-151 | 0.68 功能 | 应用内无法切换亮/暗主题（只能跟随系统） | OxMode `oxui_theme` 偏好 + resolvePalette 优先级 + 设置页三行单选，选择即 recreate 换肤 | 已实现并设备验证（dark/light 均落盘） |
| R2-152 | 0.68 功能 | 无 OxUI 设置页 | Agents footer “Settings” 入口；THEME + ABOUT（BuildConfig 版本、local-first 说明）；header ⚙ 因 360dp 换行问题回退 | 已实现并设备验证 |
| R2-153 | 0.68 功能 | Agent 只能看不能改 | 详情 Edit → name/remark/model/persona + SKILLS 多选（OxSkillStore）+ Save 写回 + 列表刷新；名称为空拦截 | 已实现并设备验证（remark/skill_ids 落盘并还原） |
| R2-154 | 0.68 QA | 编辑页按 BACK 直接退出应用 | onBackPressed 增 overlay 优先关闭（菜单→overlay→rootMode） | 已修复并设备验证 |

- 设备：debug 6809，主题即时切换、Agent 编辑回填/保存/回显/取消、
  BACK 行为全部通过；release 无 FATAL。
- 遗留：Agent 编辑未含头像/provider/采样/开场白/工具策略；设置页 v1 只有
  主题与关于；主题切换会丢滚动位置（可接受）。

## 0.69.0 设计擂台合成、皮肤/外壳 DIY 与终端 v3 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-155 | 临时需求 | 需要多模型从零设计 OxUI 并对比 | 7 版设计擂台（claude-haiku/gpt-6-luna×3/sol/56-sol/ds），简报从零、渲染截图、评审提交；产物 design-battle-shots/ | 已完成 |
| R2-156 | 用户 | 舍弃方案应沉淀为预设，预设要优雅多样 | OxUiSkin 9 套预设（前 7 套为擂台方案）+ 设置页 APPEARANCE 列表 + 即时换肤 | 已实现并设备验证 |
| R2-157 | 用户 | 所见皆 DIY：组件级可定制 | 外壳层 v1：Navigation（rail/bottom/floating）、Material（solid/acrylic/mica/glass）、Dock position（top/aboveInput/bottom）、组件开关；全部进皮肤 JSON 可导出 | 已实现并设备验证 |
| R2-158 | 用户 | 陶土色人文气息 | 调色板加 clay（#D97757/#E08963），只用于用户气泡与引用记号；signal 与 clay 语义分工 | 已实现并设备验证 |
| R2-159 | 用户 | 终端要参考 Termius/Termux | Termux 实机安装+深度使用（extra-keys/properties/绿色提示/键帽）；Termius 7.5.5 APK 实装并使用（设置页 Font & colours/Hotkeys/autocomplete/pinch）；终端 v3：会话 tab/状态行/分组键帽/↑↓历史/Tab 补全/^L/Aa/主题三选/捏合缩放 | 已实现并设备验证 |
| R2-160 | 用户 | dock 在输入框上方别扭，位置应可自定义 | dock 改流式插入 + Position 三选（top/aboveInput/bottom）+ 材质 | 已实现并设备验证（top/floating/glass） |
| R2-161 | QA 事故 | 最小 prefs 覆盖导致 agent_only 等键丢失 | 立即重建恢复并验证 agent-only 首页；之后统一"读取-合并-推送" | 已恢复 |
| R2-162 | 环境 | 宿主 X server 丢失致 qemu 退出 | 改 `-no-window -gpu off` 无窗口运行，adb 截图正常 | 已恢复 |

- 设备：debug/release 6909，七版设计截图、预设切换、Customize（radius/signal）、
  SHELL 设置、floating glass dock@top、终端 v3（help/agents/search/历史/会话）
  均实测；release 无 FATAL。
- 遗留：更细粒度组件开关（消息行/输入栏/气泡样式）与真实模糊材质；Termius
  需 APK/Play 账号；终端未接 PTY。

## 0.70.0 组件 DIY、真实模糊与终端配色 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-163 | 0.70 功能 | 组件级 DIY 不足（只能换整皮） | density/timestamps/accent/dockItems 四类组件开关进皮肤 JSON + 设置 COMPONENTS 小节 | 已实现（逐项截图待补） |
| R2-164 | 0.70 功能 | 皮肤无法导入分享 | Customize → Import JSON…（粘贴→fromJson→custom）；与 Copy JSON 对称 | 已实现（端到端待补） |
| R2-165 | 0.70 功能 | 材质只是半透明近似 | floating dock 覆盖层 + 快照 1/6 降采样两遍 box blur + 材质 tint + 圆角裁剪；滚动防抖 | 已实现并设备验证 |
| R2-166 | 用户 | 要内置 Termius 的背景色等设定 | 11 套终端配色（bg/text/dim/accent）全接入并持久化；选项面板可滚动+swatch；浅色 tab 文字色修复 | 已实现并设备验证（Termius Light/Solarized Light/Nord） |

- 遗留：density 未覆盖输入栏/列表；模糊为 box blur 近似；导入无文件分享；
  ANSI 16 色未做。

## 0.71.0 终端配色对比度与 PWD/PST QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-167 | 用户 | Nord 等预设看不清文字 | WCAG 审计 11 套（dim 最低 1.69）；全部重调至 text≥6.59/dim≥4.06/accent≥4.75；输出行不再误判 dim | 已实现并设备验证（Nord 复查） |
| R2-168 | 用户 | Termius 有一键输密码快捷键 | 选项面板 TERMINAL SECRET（本地 prefs，不入皮肤导出）+ PWD 键一键填入；未设置 Toast 引导；另加 PST 粘贴键 | 已实现并设备验证（abc123） |
| R2-169 | 用户 | 钥匙按钮太突兀 | 参考 Termius 全是等宽文本键：emoji 🔑/⧉ 改为 PWD/PST，同字号同键帽并置于行尾 | 已实现并设备验证 |

- 遗留：PWD secret 明文存储（未接入 Keystore）；未实现 Termius 的连接
  密码提示/自动填充（本控制台无 SSH/shell）。

## 0.72.0 OxUI 文件附件对标 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-170 | 主线对标 | OxUI 只能发图片，Telegram UI 可发普通文件 | 1:1 媒体路径新增 document 分支（MIME 映射/本地文档/prompt 占位）；OxChatEntry filePath/fileName 落盘 | 已实现并设备验证 |
| R2-171 | 0.72 功能 | 附件入口只有图片 | ＋ 改为 Photo/File 菜单；File 走 GET_CONTENT */*、原名拷贝、25MB、重名序号；胶囊 📄+名称+大小 | 已实现并设备验证 |
| R2-172 | 0.72 功能 | 历史/失败重试不识别文件 | renderConversation 文件气泡（PAPER 胶囊+说明+时间戳）；pending 与 120s 失败重试保留文件字段 | 已实现并设备验证（重启持久） |

- 设备：debug 7209，Echo 1:1 txt 端到端 + 重启复查；release 无 FATAL。
- 遗留：群组文件在 OxUI 仍显示文本占位（群条目未写 filePath）；文件不可
  预览/打开；25MB 上限。
- 附注：Yuki 会话历史中 `recallnullnull…` 工具名条目属旧构建数据，现行
  流式解析已过滤 null 名片段，非现行 bug。

## 0.73.0 群组文件气泡与转发 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-173 | 0.72 遗留 | 群组文件在 OxUI 显示文本占位而非文件气泡 | handleOutgoingGroupMedia 记录首文件；onGroupMessage/startGroupTurn 文件重载；群条目写 filePath/fileName；复用文件气泡 | 已实现并设备验证 |
| R2-174 | 主线对标 | OxUI 无转发功能 | 助手行菜单加 Forward to…（1:1 同样两行菜单）；用户气泡菜单加 Forward；目标面板列 Agent/本地群；`〔转发〕` 前缀发送 | 已实现并设备验证（Echo 收到并回复） |
| R2-175 | 0.73 QA | 纯文件（无文字）发送键保持禁用——updateSendVisual 只算 pendingImagePath | ready 判定加入 pendingFilePath；复测群文件无文字发送成功 | 已修复并设备验证 |

- 遗留：转发不含媒体、无来源标注；文件气泡无长按菜单；群文件重名序号前缀属预期。

## 0.74.0 消息删除与 Agent 置顶 QA 记录

| ID | 来源 | 问题 | 处理 | 状态 |
|---|---|---|---|---|
| R2-176 | 主线对标 | OxUI 无删除消息能力（Telegram UI 可长按删除） | deleteOxEntry（store + messages_v2 定位 mid，agent dialog cacheOnly）；助手/用户/图片/文件菜单 Delete message + 二次确认 | 已实现并设备验证 |
| R2-177 | 0.74 QA | messages_v2 没有 message 列，首版按正文定位抛 SQLiteException（mids=0） | 改为反序列化 data blob 取正文；窗口 300s 内正文优先、8s 内时间兜底 | 已修复并设备验证 |
| R2-178 | 主线对标 | Agent 无法置顶，rail 顺序固定 | 详情页 Pin/Unpin 切换 OxAgent.pinned，saveAgent + 重载排序 | 已实现并设备验证（重启保持） |

- 遗留：删除依赖 ts+正文前 24 字符启发式；Telegram UI 侧删除不会反向
  清理 OxUI chat.json；置顶只影响 OxUI rail，不影响 Telegram 联系人列表。
