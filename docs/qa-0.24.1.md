# QA — alpha 0.24.1（Ox 界面中文化补漏）

- 版本：`0.24.1`，versionCode **2419**（`0.24.0` 为 2409）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 02:40–02:48（CST）
- 目标：对 Ox 自有界面做一次文案审计，补齐中文包中缺失的可见字符串，避免
  中文模式下回退英文。
- 版本规则：仅补翻译/文案，按 patch 从 0.24.0 → **0.24.1**，
  versionCode 2419（241×10+9）。

## 1. 审计方法

- 扫描 `TMessagesProj/src/main/java/org/telegram/ui/Ox*.java` 与
  `org/telegram/ox/*.java` 中所有 `R.string.*` 引用，与
  `assets/oxlang/zh-cn.xml` 的 key 集合比对。
- 共 531 个引用，发现 12 个 key 缺少中文：
  `OxImportDone`、`OxImportGrant`、`OxImportOperit`、`OxImportRunning`、
  `OxImportStorageText`、`OxImportStorageTitle`、`OxLocalPremium`、
  `OxLocalPremiumInfo`、`OxTerminal`、`OxTerminalClear`、`OxTerminalHint`、
  `OxTerminalRestart`。
- 仅补译文，不改功能代码；中文包版本 93 → 94，确保已安装用户重新拷贝。

## 2. 翻译与设备验证 — PASS

- 新增译文：
  - “从 Operit / Operit2 导入”“导入完成”“正在导入…”“打开设置”
  - “需要文件访问权限”“需要访问所有文件才能读取 Operit 导出目录。请在系统
    设置中授权，然后再次点击导入。”
  - “本地会员”“本地解锁 Telegram Premium 功能，无需订阅或服务器请求。”
  - “终端”“清空”“重启”“输入命令…”
- 设备：
  - Ox 设置顶部显示“本地会员”，列表显示“从 Operit / Operit2 导入”，
    不再出现英文回退；
  - 打开终端后显示“终端”“清空”“重启”和输入框提示“输入命令…”。
- 截图：`docs/screenshots/qa-0.24.1-settings.jpg`、
  `qa-0.24.1-terminal.jpg`。
- 无 FATAL/ANR；本轮未修改用户数据。

## 3. 未覆盖 / 已知边界

- 审计只覆盖 Ox 自有 Java 代码引用的字符串；Telegram 原生字符串与其他模块
  未逐条扫描。
- MCP、AMOLED、Provider、`.oxthemes` 等专有名词故意保留英文/缩写。
- 未对中文文案做母语审校，只保证无英文回退且语义准确。

## 4. 产物

- `dist/OrionekoX-0.24.1-alpha-arm64-x64.apk`（versionCode 2419），sha256
  `f5bdcc7047d753742101d0335596af73c7655faaa3cb2d48208bd5e54aaeb4a5`
- `dist/OrionekoX-0.24.1-debug-arm64-x64.apk`，sha256
  `2564d44e7987fba89757b36a700e12e4694ba66542860dcefb0dab813941e944`
