# QA — OxUI 8.3 / 8.4 / 9.4 端到端取证（0.88.0）

日期：2026-10-30
设备：emulator-5554（AVD `orioneko_test`，1080x2400 @ 420dpi，x86_64）
包：`io.github.blancatora.orionekox.debug` 0.87.0-debug (versionCode 8709)
构建：`assembleAfatDebug`，BUILD SUCCESSFUL

这三项此前一直挂着 `⚠️ 待端到端验证`，本次全部取得实证。下面记录**每一项卡在哪里、怎么绕过去**，避免下一轮重新踩坑。

---

## 0. 取证环境的两处关键修正

### 0.1 emulator 图形栈：`-gpu off` 进群页必崩

现象：打开任意本地群聊页，qemu **确定性 SEGV**，adb 立即掉线。

```
$ coredumpctl info <pid>
TID: 4139965 (RenderThread)
Signal: 11 (SEGV) si_code: SEGV_MAPERR
Command Line: ... -avd orioneko_test -no-window -gpu off -no-audio -no-boot-anim
Module libvulkan_lvp.so without build-id.
```

崩溃点在 **emulator 自己的 RenderThread**（不是 app 线程，不是 AndroidRuntime）。两次崩溃指纹完全一致（同偏移），是 `-gpu off` 软件渲染路径下的 Lavapipe/Vulkan bug，渲染聊天页墙纸纹理时踩中。

**这不是 OrionekoX 的代码缺陷。** 换 `-gpu swiftshader_indirect` 或 `-gpu guest` 后可正常进入群页（本例最终用 `swiftshader_indirect`）。

> 后遗症：崩溃会让 adb 掉线 + 应用进程重启。重启后 `OxUiPreviewActivity` 会因 `not exported` 无法用 `am start` 直接拉起，必须走 `LaunchActivity`。

### 0.2 两个「终端」不是同一个东西

| | OxUI 面板内终端 | Telegram UI 的 Terminal tab |
|---|---|---|
| 实现 | `OxUiPreviewActivity` 内建 | `OxTerminalActivity`（BaseFragment） |
| 后端 | 固定命令集：`help/agents/groups/search/theme/version/telegram/clear/echo` | **真 shell**：`ProcessBuilder("/system/bin/sh")` |
| ANSI 渲染 | ❌ `tv.setText(line)` 纯文本 | ✅ `ansiToSpannable()` |

9.4 的着色只在**右侧那一列**生效。在 OxUI 面板终端里 `cat` 一个带 ANSI 的文件，得到的是 `command not found: cat`——因为那根本不是 shell。

---

## 1. 8.3 群组反向同步 — 通过

### 前置：本地群此前从未被创建过

`ox_groups.xml` 一直是空的 `<map/>`。8.3 的实现（`MessagesController.deleteMessages` 拦截分支）早在 0.83.0 就提交了，但**从来没有人真正建过一个本地群**，所以每次都只能碰到空列表，自然取不到证。

建立路径（agent-only 模式下）：

1. Telegram UI → `New Message`(FAB) → `New Group`
2. 选 2 个 agent（Sakura Waifu、Yuki Waifu）
3. 命名 → `Done`

触发 `OxBridge: createLocalGroup id=1800000001 agents=2`。

> 坑：选中一个 agent 后布局会重排（行整体下移 132px），**必须每点一次就重新 dump 取坐标**，否则第二次点击会落在已位移的位置上、把选中状态取消掉。

### 验证步骤

1. 群里发一条 `sync-probe-message` → `messages_v2` 出现 100013，`ox_groups.log_1800000001` 出现 user 条目
2. OxUI Agents 面板 → `Groups` 分组 → 点群行进入 OxUI 群对话
3. 长按气泡 → `Delete message` → 确认框点 `Delete`

### 结果

```
OxBridge: onNativeMessagesDeleted did=-1800000001 mids=1 removed=0
OxBridge: deleteMessages local-group did=-1800000001 count=1 cacheOnly=true
OxBridge: deleteOxEntry did=-1800000001 role=user store=true mids=1
```

`log_1800000001` 从 2 条 → 1 条：`user:sync-probe-message` 被精确裁掉，`assistant:HTTP 402...` 保留。
UI 同步：`sync-probe-message` 气泡消失，只剩 Sakura 的回复。

`cacheOnly=true` 证明这条删除**没有发往 Telegram 服务器**（本地群无服务端对应）。

截图：`dist/iter-shots/oxui-8.3-group-delete-synced.png`

---

## 2. 8.4 搜索导航 — 通过

代码早在 0.67.0 (`0f842b4`) 就随分支携带，但从未单独验证。

步骤：群里发 `needle alpha probe` / `needle beta probe` → 点 header 的 `⌕`（contentDescription = `Search this chat`）→ 输入 `needle`。

结果：

| 操作 | 计数 | 当前命中 |
|---|---|---|
| 输入 `needle` | `1/2` | `needle alpha probe` |
| 点 `↓` | `2/2` | `needle beta probe` |

计数、导航、滚动定位、命中气泡 SIGNAL 闪烁全部正常。

截图：`dist/iter-shots/oxui-8.4-search-nav-2of2.png`

---

## 3. 9.4 终端 ANSI-16 着色 — 通过

此前记为「ESC 无法经软键盘/adb 输入」，所以拍不到着色。**这个判断不准确**——不是输入不了，而是当时在错的终端里试。

### 取证方法（可复用）

adb 无法输入 ESC(0x1B)，但可以往设备写文件再用真 shell `cat`：

```bash
# 1. 本地生成带 SGR 的文件（16 色前景 + 4 色背景 + 粗体 + 下划线 + 非 SGR CSI）
python3 -c "
esc=chr(27)
lines=[]
for i in range(8):
    lines.append(f'{esc}[{30+i}m█ F{30+i}  normal {esc}[0m   {esc}[{90+i}m█ F{90+i} bright {esc}[0m')
lines.append(f'{esc}[1mBOLD{esc}[0m {esc}[4mUNDERLINE{esc}[0m')
lines.append(f'{esc}[41m R-BG {esc}[0m {esc}[42m G-BG {esc}[0m {esc}[44m B-BG {esc}[0m {esc}[45m M-BG {esc}[0m')
lines.append(f'{esc}[2J{esc}[1;1H>>> CSI stripped <<<')
open('/tmp/ansi-test.txt','w').write('\n'.join(lines)+'\n')
"

# 2. 推到设备（用无空格短路径，adb input text 的 / 和空格都不稳）
adb push /tmp/ansi-test.txt /data/local/tmp/a.txt

# 3. Telegram UI → Terminal tab → 输入（空格用 %s 转义）
cat%s/data/local/tmp/a.txt
```

### 结果

16 色前景（F30–F37 / F90–F97）、4 色背景（红/绿/蓝/品红）、BOLD 更粗、UNDERLINE 带下划线，全部正确渲染。
最后一行 `>>> CSI stripped <<<` 干净无残留，证明 `ESC[2J` / `ESC[1;1H` 这类非 SGR 的 CSI 被正确剥离而非当成文本打印。

截图：`dist/iter-shots/oxui-9.4-ansi16-rendered.png`

---

## 4. 顺带发现、未修的缺陷

### 4.1 本地群在 Telegram UI 里读不出消息（数据在库、UI 为空）

`ChatActivity` 打开本地群时：

```
load unknown dialog -1800000001
processLoadedMessages size 0 in chat -1800000001 ... last_message_id 100014
```

但数据库里明明有：

```
dialogs:      did=-1800000001  last_mid=100014
messages_v2:  100012(out=1) 100013(out=1) 100014(out=0)
chats:        uid=1800000001  ✓
```

`dialogs.last_mid` 是对的、`chats` 表有记录、SQL 手工执行能取到 3 条——但 `processLoadedMessages` 拿到的 `size=0`。表现为聊天页永远显示「You have created a group」欢迎卡片。

未定位到确切过滤点（可能是 `loadUnknownDialog` → `messages.getPeerDialogs` 在 agent-only 模式下永不返回，导致 dialog 未进内存 `dialogs_dict`）。

**影响**：Telegram UI 侧本地群不可用；OxUI 侧（`OxGroupStore.loadChat`）完全正常。8.3 的删除同步已用 OxUI 路径取证，不受此阻塞。

### 4.2 OxUI 面板终端的 `echo` 不着色

`OxUiPreviewActivity` 有完整的 `ansiToSpannable` 在 `OxTerminalActivity` 里，但面板终端的 `renderTerminalLog()` 用的是 `tv.setText(line)`，从不调 `ansiToSpannable`。两个终端的 ANSI 能力不一致。

---

## 5. 结论

| 项 | 之前 | 现在 |
|---|---|---|
| 8.3 群组反向同步 | ⚠️ 待验证 | ✅ 端到端实测通过 |
| 8.4 搜索导航 | ⚠️ 待验证 | ✅ 端到端实测通过 |
| 9.4 ANSI-16 着色 | ⚠️ 缺截图 | ✅ 端到端实测通过 |

三项的实现代码此前都已存在且正确，缺的都是**取证条件**（没建过本地群 / emulator GPU 崩 / 在错的终端里试）。
