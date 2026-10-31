# P2-6 验收：两个终端的区分

验收时间：2026-11-01 01:18–01:23（Asia/Shanghai）
设备：`emulator-5554`，AVD `orioneko_test`
包：`io.github.blancatora.orionekox.debug`，`versionCode=8909`，`versionName=0.89.0-debug`
APK sha256：`722a967416d8b7eb62c0d7d29f91e1496a87fcd9d865163f51c21885b80ae19a`
语言包：`ox_zh_version=129`

---

## 结论

| 项 | 结果 |
|---|---|
| 假终端标题标注「预览」 | ✅ `❯_ 终端预览` |
| 假终端状态行说清不执行 shell | ✅ `本地 · 仅预览 · 不执行 shell · 真终端请开底栏「终端」` |
| 「打开真终端」按钮存在 | ✅ content-desc = `打开真终端` |
| 按钮跳转到真终端 | ✅ 前台从 `OxUiPreviewActivity` → `LaunchActivity` → 终端界面 |
| 真终端确实执行 shell | ✅ `echo P26_REAL_OK` → 输出 `P26_REAL_OK` |

---

## 1. 假终端（OxUI 面板）

### 改前

```
标题:     ❯_ terminal
状态行:   LOCAL  ·  tty pseudo-session  ·  no shell
```

状态行是诚实的，但标题写 `terminal` 会让人以为是真终端。用户点了发现不执行命令，会当 bug 报。

### 改后

```
标题:     ❯_ 终端预览
状态行:   本地 · 仅预览 · 不执行 shell · 真终端请开底栏「终端」
按钮:     ❯_（content-desc = 打开真终端）
```

### 实测（uiautomator dump）

```
text="❯_ 终端预览"                                          center=(335,199)
text="本地 · 仅预览 · 不执行 shell · 真终端请开底栏「终端」"    center=(540,368)
text="❯_"                                                   center=(673,199)
content-desc="打开真终端"
```

---

## 2. 「打开真终端」跳转链路

### 实现

`OxUiPreviewActivity` 是纯 `Activity`，没有 `presentFragment`，拿不到 Telegram 的 fragment 栈。所以走 Intent：

```
OxUiPreviewActivity
  → Intent(this, LaunchActivity.class).putExtra("ox_open_terminal", true)
  → LaunchActivity 读到 extra，置 oxOpenTerminalOnStart = true
  → 跳过 OxUI 重定向（否则会又跳回 OxUI）
  → actionBarLayout 建好后延迟 400ms presentFragment(new OxTerminalActivity(args))
```

`LaunchActivity` 新增字段 `oxOpenTerminalOnStart`，消费一次就清掉，避免重复触发。

### 实测

| 步骤 | 前台 Activity |
|---|---|
| 点击前 | `OxUiPreviewActivity` |
| 点击后 | `LaunchActivity`（内含 `OxTerminalActivity` fragment） |

### 真终端界面（uiautomator dump）

```
text="终端"
text="sh · /storage/emulated/0/Download"
text="清空" / text="重启"
text="TTY 1" / text="TTY 2" / text="TTY 3"
text="输入命令…"
text="➤"
```

---

## 3. 真终端执行 shell 的硬证据

输入 `echo P26_REAL_OK` 后执行，终端回显：

```
Ox Terminal · TTY 1
工作目录: /storage/emulated/0/Download
输入 help 查看常用命令，点按键盘图标可收起键盘。

/storage/emulated/0/Download
❯ echo
❯ echo P26_REAL_OK
P26_REAL_OK
```

**`P26_REAL_OK` 是 shell 真实输出**，不是 UI 假造。这证明 `OxTerminalActivity` 走的是 `ProcessBuilder("/system/bin/sh")`，与假终端的 `renderTerminalLog`（只 `setText`）完全不同。

截图：`.qa-shots/p2-6-exec.png`

---

## 4. 为什么不是「把假终端改成真终端」

假终端是 OxUI 的皮肤预览组件（可换主题、可调字号、可缩放），它存在的意义是展示 OxUI 的视觉能力，不是执行命令。把它改成真 shell 会破坏「预览」这个定位。

正确做法是**两个都留着，各自说清自己是什么**。

---

## 5. 改动文件

```
TMessagesProj/src/main/java/org/telegram/ui/OxUiPreviewActivity.java   +18 −2
TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java        +17 −1
TMessagesProj/src/main/res/values/strings.xml                          +3
TMessagesProj/src/main/assets/oxlang/zh-cn.xml                         +3
TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java  +1 −1  (128 → 129)
```

---

## 6. 一个踩过的坑（值得记）

第一次构建时把语言包版本留在 128，新加的 3 条词条在设备上**显示英文**——因为 `ensureBundledZhPack()` 只在版本号变化时才覆盖 `files/ox_zh-cn.xml`。

这正是 `AGENTS.md` 里那条「改了源忘了提版本号」的坑。**加词条必须同时升 `OX_ZH_PACK_VERSION`，否则设备上永远用旧包。**

---

## 证据文件

- `.qa-shots/p2-6-state.png` —— 假终端中文界面
- `.qa-shots/p2-6-realterm.png` —— 真终端界面
- `.qa-shots/p2-6-exec.png` —— `echo P26_REAL_OK` 执行结果
