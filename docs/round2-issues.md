# 第二轮独立测试问题汇总（A/B/C）

状态：A、B 已完成；C 进行中。此文档用于在 C 报告到达后直接排期修复，不替代各测试的完整报告。

## 已完成报告

- A：`docs/bar-rice-round2-A.md`（0.8.115，2026-10-26）
- B：`docs/bar-rice-round2-B.md`（0.9.0，2026-10-27）
- C：`docs/bar-rice-round2-C.md`（待生成，0.9.0）

## P2（按影响排序）

| ID | 来源 | 问题 | 建议修复 | 目标版本 |
|---|---|---|---|---|
| R2-01 | A/B | 从聊天列表重进本地 Agent 会话，消息区空白；冷启动才恢复 | 对 Agent 会话忽略 `diditem` 滚动恢复，强制 `max_id=0 / load_type=2`；若 1.2s 后 `messages` 仍为空，再发一次 cache load；加日志确认 `messagesDidLoad` 是否被 `index == -1` 丢弃 | 0.9.1 |
| R2-02 | A/B | 工具结果以原始 JSON 文本出现在气泡中，含 `\n` 转义 | 工具结果不直接注入可见气泡；若必须显示，解析 JSON 的 `ok/result/error` 并格式化；聊天模型仍收到原始 JSON | 0.9.1 |
| R2-03 | A/B | 发送按钮视觉中心点击无效，仅特定坐标可发 | 复现后用 uiautomator bounds 对比按钮 view 与命中区；检查键盘 inset/自定义字号下的布局；必要时扩大 `sendButton` 触摸区 | 0.9.1 |
| R2-04 | B | 群内“相册”选图后卡在预览/编辑器循环，发不出 | 在 `prepareSendingPhoto → prepareSendingMedia` 的 group 分支加日志；确认编辑器完成回调是否被本地分支 `return` 打断；为群相册增加直接发送路径/关闭编辑器回调 | 0.9.1 |
| R2-05 | B | `@agent` 要求描述上一张群图时收不到像素 | 群历史保存最近图片路径；当前消息含“图/照片/image”且无附件时，把最近一张群图作为 vision part 附加 | 0.10.0 |
| R2-06 | A | `.oxagent` 导入副本无 API Key，首次使用才暴露 | 导入预览/聊天内提示“此副本未包含密钥，请在 Agent 编辑器中重新填写”，不导出密钥 | 0.9.1 |

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
