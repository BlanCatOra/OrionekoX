# QA — 0.84.0（OxTerminal 支持 ANSI-16 颜色：9.4）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。
> 日期：2026-10-30 · 分支：`orioneko/0.80-memory-stage-c` · 基线：`08ce123`（OxUI 8.3）

## 背景

`OxTerminalActivity` 的终端输出是一个纯 `TextView`（`outputView`）。早期 shell
程序（如 `ls --color`、`grep --color=auto`、`git` 着色、`tree`、`neofetch`）发出的
ANSI 转义序列会以字面乱码直接显示（如 `[0;31m`），既难看也影响可读性。9.4 的目标
是把这些序列解析成真实着色。

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 9.4 ANSI-16 | `OxTerminalActivity` 新增 SGR 解析 + `Spannable` 渲染：16 色前景/背景、粗体、下划线；其余 CSI（光标/清屏）与 OSC（窗口标题）序列剥离 | 完成（构建通过） |

## 实现要点

- 新增 `ansiToSpannable(String, AnsiState)`：扫描原始输出，遇到 `ESC[…m` 按 SGR
  解释并切换当前样式；其余 `ESC[…` CSI 与 `ESC]…BEL` OSC 直接丢弃，避免污染显示。
- `applyAnsiSgr` 支持：`0` 复位、`1` 粗体、`4` 下划线、`22/24` 关闭、`30-37/90-97`
  前景、`40-47/100-107` 背景、`39/49` 复位前景/背景；对扩展色 `38;5;n`、`38;2;r;g;b`
  （256/真彩）做参数消费并忽略（沿用上一样式而非乱码）。
- 调色板取 xterm / Windows Terminal 暗色 16 色；前景/背景缺省（-1）时不加 span，
  回落到 `applyAppearance` 设定的 `schemeFg`/`schemeBg`，因此四种配色方案（含浅色
  方案 3）下未着色文本仍正常。
- 跨流式分块沿用着色状态：`appendToView` 复用实例字段 `ansiState`，使一行末尾未复位
  的着色在下一行延续；`refreshOutputFromSession` 用全新状态整体重排，并把末尾状态回写
  `ansiState`；`clear`/`restart`/`start(clearBuffer)` 时 `ansiState.reset()`。
- 渲染产物为 `SpannableStringBuilder`，按样式分段加 `ForegroundColorSpan` /
  `BackgroundColorSpan` / `StyleSpan(BOLD)` / `UnderlineSpan`。

## 行为边界

- 仅处理 SGR 着色类序列；光标移动（`ESC[H`、`ESC[C`）与清屏（`ESC[2J`、`ESC[K`）
  被剥离而不真正执行——对滚动日志型终端可接受，不会再以乱码出现。
- 256/真彩扩展色不在 9.4 范围内（标注为「ANSI-16」），按忽略处理，文本保持上一前景色。
- `\r` 回车（进度条类输出）仍由底层 `readLine()` 在按行读取时丢弃，属既有行为，
  不在 9.4 改动范围内。

## 构建

- `taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug`
  → **BUILD SUCCESSFUL**（提交 `29bd53a`）。

## 模拟器实测取证

- ⚠️ 待端到端验证：ANSI 转义序列无法经软键盘 / `adb input text` 输入（仅支持可打印
  字符），亦无法在自动化里便捷注入带 `ESC` 的命令。因此「真实着色渲染」尚未在
  `emulator-5554` 上取得截图实证。解析算法经代码审查与逐段推演确认（含 `ls --color`
  的 `ESC[0;32m…ESC[0m`、git 的 `ESC[31m`、组合 `ESC[1;31m` 等常见形态）。
- 终端 Activity 本身可正常打开（非本地群聊天页，无 qemu 退出问题），配色方案的
  切换（0-4）与字号调节在先前迭代已验证可用；本次仅新增着色层，不影响既有控件。

## 诚实缺口

1. 真机/模拟器上的「带色输出截图」缺实证，标注 ⚠️；需一个有配色输出的命令（如
   `git log --oneline` 在已初始化仓库、或 `ls --color`）经真实 shell 触发才能取证。
2. 扩展色（256/真彩）渲染未做，仅做安全剥离；若后续需要，可接入 `38;5/38;2` 映射。
