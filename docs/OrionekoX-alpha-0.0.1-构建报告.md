# OrionekoX alpha 0.0.1 构建报告

> 构建日期：2026-10-24 · 基座：DrKLO/Telegram @ `0aae959`（官方 13.0.3 / 7183）· 结果：**BUILD SUCCESSFUL（12m47s，341 tasks）**

## 1. 交付物

| 文件 | 说明 |
|---|---|
| `OrionekoX/dist/OrionekoX-0.0.1-alpha-arm64.apk` | 38MB · arm64-v8a 单 ABI · R8 压缩 + 资源收缩 |
| `OrionekoX/dist/OrionekoX-0.0.1-alpha-arm64.apk.sha256` | `c6c7ca2a649b6e534c450ff5bf78cf4f7d70904b6dca529237fc47d38e9f1c9c` |
| `OrionekoX/patches/0001-*.patch`、`0002-*.patch` | 对上游的全部改动（目录化补丁，M0 纪律的第一次实践） |
| `OrionekoX/TMessagesProj/config/orioneko.keystore` | 签名密钥（密码 `orioneko-alpha`，alias `orioneko`） |

**APK 实测信息**（aapt/apksigner 验证）：

```
package:          sh.orioneko.messenger
versionName:      0.0.1
versionCode:      19   （= APP_VERSION_CODE 1 × 10 + flavor abiVersionCode 9，上游公式，正常）
application-label: OrionekoX
native-code:      arm64-v8a
签名证书:          CN=OrionekoX, OU=Alpha, O=Orioneko （SHA-256 a9eac6e0...4472c）
```

## 2. 构建环境（本机持久化，可复用）

| 组件 | 位置 | 版本 |
|---|---|---|
| JDK | `~/orioneko-toolchain/jdk17` | Temurin 17.0.21 |
| Android SDK | `~/orioneko-toolchain/android-sdk` | cmdline-tools 11076708 |
| 已装组件 | — | platform-tools · platforms;android-36 · build-tools;36.0.0 · **ndk;27.2.12479018** · **cmake;3.22.1** |
| 源码 | `workspace/OrionekoX`（分支 `orioneko/alpha-0.0.1`） | 上游 master @ 0aae959 |

**复现构建命令**：

```bash
cd OrionekoX
export JAVA_HOME=~/orioneko-toolchain/jdk17
export PATH=$JAVA_HOME/bin:$PATH
taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
# 产物: TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/app.apk
```

（`taskset -c 0-7` 限制 8 核，防止原生编译吃满 18 核导致内存不足；本机 30GB 内存下构建峰值安全。）

## 3. 相对上游的全部改动（即 patches/0001+0002）

| 改动 | 文件 | 目的 |
|---|---|---|
| `APP_VERSION_CODE=1`、`APP_VERSION_NAME=0.0.1`、`APP_PACKAGE=sh.orioneko.messenger` | `gradle.properties` | 独立包名（可与官方 Telegram 共存）+ alpha 版本号 |
| `RELEASE_KEY_*` → `orioneko-alpha/orioneko` | `gradle.properties` | 指向自有签名密钥 |
| `storeFile` → `config/orioneko.keystore`（×2） | `TMessagesProj_AppStandalone/build.gradle` | 弃用仓库自带的公共 release.keystore |
| 删除 `applicationIdSuffix ".web"`（×2） | 同上 | 最终包名即 `sh.orioneko.messenger`（否则带 `.web` 后缀） |
| `afat` flavor `abiFilters` 收窄为 `arm64-v8a` | 同上 | 原生编译量 ÷4（策略同 Pelegram） |
| `AppName` → `OrionekoX`（10 个语言文件） | `TMessagesProj/src/main/res/values*/strings.xml` | 应用名（含全部本地化） |
| `USE_CLOUD_STRINGS=false`、`CHECK_UPDATES=false`、`SUPPORTS_PASSKEYS=false` | `BuildVars.java` | 关闭官方云字符串（会覆盖品牌名）、官方更新检查（会提示升级官方版）、fork 不支持的一键通行证 |
| 新增 `sh.orioneko.messenger` client 条目 | `TMessagesProj_AppStandalone/google-services.json` | 让 google-services 插件通过包名匹配校验（FCM 不可用，见下） |

## 4. 已知限制（alpha 范围内接受，后续里程碑解决）

1. **`APP_ID=4 / APP_HASH=014b35...` 沿用上游公开默认值**。登录可用，但属于共享 api_id，长期/多设备使用有风控风险。**建议**：到 [my.telegram.org](https://my.telegram.org/auth) → API development tools 申请自己的 id/hash，替换 `BuildVars.java:29-30` 后重建（M0 应改为从 `local.properties` 注入，与 NagramXF 构建指南一致）。
2. **无 FCM 推送**：未建 Firebase 项目，google-services 条目是占位。App 会退化为统一推送后台连接（更耗电）。alpha 可接受。
3. **图标仍为 Telegram 原版**：品牌图标待 alpha 后续版本（需替换各密度 mipmap/adaptive icon）。
4. **签名密钥与密码在 git 仓库内**：仅适合本地个人构建。公开发布前必须将 keystore 与密码移出仓库（.gitignore + local.properties 注入），并公示证书指纹。
5. 仅 arm64-v8a；armeabi-v7a/x86 设备不在 alpha 支持范围。

## 5. 模拟器自测结果（2026-10-24，构建后追加）

**环境**：本机 KVM + 官方 emulator 37.2.12，AVD `orioneko_test`（Pixel 6 机型，API 34 / Android 14，`google_apis;x86_64` 镜像）。该镜像自带 `libndk_translation.so` native bridge，**交付的 arm64-v8a APK 无需重编直接安装运行**（`primaryCpuAbi=arm64-v8a` 实测确认）。AVD 实际写入 `~/.config/.android/avd`（新版 avdmanager 的 XDG 行为），启动时需 `export ANDROID_AVD_HOME=$HOME/.config/.android/avd`。

**冒烟测试链路（全部通过，全程 0 FATAL / 0 ANR）**：

| 步骤 | 结果 |
|---|---|
| `adb install -r` | Success，包信息与构建产物一致（0.0.1 / 19 / targetSdk 36） |
| 冷启动 LaunchActivity | 进程 `sh.orioneko.messenger` 常驻（RSS ~260MB，正常水平） |
| 欢迎页 | 大标题 **OrionekoX** + "Start Messaging"（截图 `dist/test-screenshot-0-welcome.png`） |
| 点击 Start Messaging → 电话确认权限预说明页 | 文案 "Please allow **OrionekoX** to receive calls…"（截图 `test-screenshot-1-permission.png`） |
| 系统权限对话框 | "Allow **OrionekoX** to make and manage phone calls?" —— 应用标签正确 |
| 拒绝权限 → 手机号输入页 | 国家码/号码输入/国家选择 UI 全部正常（截图 `test-screenshot-2-phone.png`） |
| logcat 全程扫描 | 0 次 FATAL EXCEPTION、0 次 ANR |

**自测发现并已修复**：欢迎页大标题与权限说明文案中的 "Telegram" 来自 6 个独立字符串（`Page1Title`、`AllowReadCall`、`AllowReadCallAndLog`、`AllowFillNumber`、`NotificationHiddenName/ChatName`），首轮构建未覆盖。已按"仅替换品牌词"原则在全部语言文件修复（33 行 / 8 文件，补丁 `0003`），增量重建 5m58s 后复测通过。**未登录验证**：完整登录需真实手机号收码，超出无人值守自测范围；MTProto 连接将随真实登录在 M1 期间补充真机测试。

**最终产物**（自测修复后）：`dist/OrionekoX-0.0.1-alpha-arm64.apk`，`sha256 c94aa74a…16 12b`（以此为准，替代首轮 `c6c7ca2a…`）。

**模拟器复用**：

```bash
export ANDROID_AVD_HOME=$HOME/.config/.android/avd
~/orioneko-toolchain/android-sdk/emulator/emulator -avd orioneko_test -no-window -gpu swiftshader_indirect &
~/orioneko-toolchain/android-sdk/platform-tools/adb install -r OrionekoX/dist/OrionekoX-0.0.1-alpha-arm64.apk
```

## 5.1 登录后修复轮（2026-10-24，用户真机登录后追加）

用户在窗口化模拟器完成真实登录后报告：**切换中文不生效**。

**根因**（双因素叠加）：① standalone 构建 `localeFilters += ["zz"]` 会把 APK 内全部本地化资源剥离（官方设计如此，翻译靠云端语言包）；② 首轮改造为保护品牌关闭了 `USE_CLOUD_STRINGS`，导致云端语言包也不加载——语言切换两头落空，只剩英文。

**修复**（补丁 0004）：
1. 恢复 `USE_CLOUD_STRINGS = true`——官方同款全语言支持（语言包从 Telegram 服务器下载）；
2. 新增**品牌保护钩子** `LocaleController.applyOrionekoBrand()`：在 `getLocaleFileStrings()`（所有语言包字符串的唯一解析入口）返回前，对 7 个品牌关键 key（AppName/Page1Title/AllowReadCall/AllowReadCallAndLog/AllowFillNumber/NotificationHiddenName/ChatName）做 "Telegram"→"OrionekoX" 定点替换——保留原翻译质量的同时守住品牌，不会误伤 "Telegram Premium" 等合法服务名引用；
3. 顺带完成 M0 项——**APP_ID/HASH 注入机制**：`TELEGRAM_APP_ID`/`TELEGRAM_APP_HASH` 写入 `local.properties` 即生效（buildConfigField 注入），缺省回落上游默认值 4。**建议尽快申请自有凭据替换**（my.telegram.org）。

**复测结果**：`adb install -r`（登录态保留）+ 冷启动后，设置页全中文（"账号/聊天设置/隐私与安全/通知/数据与存储/聊天文件夹"），语言页"简体中文"在列——用户此前的语言偏好被保留并正常生效。全程零崩溃。

**最新产物**：`dist/OrionekoX-0.0.1-alpha-arm64.apk`，`sha256 11f70b9e…f83065`（以此为准）。

## 5.2 alpha 0.0.2 —— 品牌完整化 + 隧道移植（2026-10-24）

**本版新增**（补丁 0005/0006，基于 0001-0004）：

1. **嵌入式 VLESS 隧道（M1 核心提前落地）**：从 Pelegram（GPLv2，同源合规衍生）整体移植 sing-box 隧道栈——`vpn/` 包 8 类 + `VpnListActivity/VpnSettingsActivity` + 预编译 `libbox-lx.aar`（18.7MB）；tgnet 原生层 5 文件小改（`native_setVpnTunnelActive` 标志位 + 地址轮转/端口循环联动）；支持 `vless://`(REALITY/XHTTP)、`hysteria2://`、`ss://`、AmneziaWG `vpn://` 密钥；应用级隧道（本地 SOCKS5 → `setProxySettings`，无 VpnService）；自动切换（tgnet 流量驱动）、开机自恢复、内置浏览器走隧道、登录页按钮直达。设置入口：数据与存储 → VPN。
2. **品牌图标全套**：PIL 生成"猎户座环"设计（深海军蓝渐变 + 倾斜轨道环 + 三星带 + 闪光），替换默认图标全部链路（standalone `ic_launcher_sa` + 自适应前景 `icon_foreground_sa` + 背景 shape + 单色 `icon_plane` 矢量）及 5 套备选图标（6 色系主题）。
3. **品牌残留清扫**：`PLAYSTORE_APP_URL` → GitHub；商店评分弹窗不再指向官方商店页。
4. 版本 0.0.2 (versionCode 29)。

**实测**（模拟器，用户登录态保留）：VPN 分区/密钥管理/Add VPN 输入页（密钥粘贴+QR 扫码）全部正常渲染；桌面图标视觉模型确认"深蓝底+轨道环+星星，无纸飞机"；冷启动 VpnController.load() 零崩溃。截图：`dist/test-screenshot-0.0.2-*.png`。

**已知限制（0.0.2）**：
- **真实隧道连通未测**：需真实 VLESS/Hysteria2 节点（用户自备密钥后即用；`am start -d "vless://..."` 深链快速验证）。
- **通话走隧道暂缓**：Pelegram 的 VoIP 原生 SOCKS5 UDP 转发（Socks5UdpSocket 等）未移植；设置开关存在但为无害空操作（默认关）。
- 分段二维码（Amnezia 多码）连扫降级为手动粘贴（pelegram 对扫码器的 3 处补丁未移植，单个 QR 正常）。
- VPN 页面字符串为英文（自定义 key 不在官方语言包中，后续随 ox 语言文件补中文）。
- `VpnUpdateAvailable` 等更新检查字符串指向占位（无自有更新通道）。

**产物**：`dist/OrionekoX-0.0.2-alpha-arm64.apk`（42MB 含 libbox，`sha256 45582ceb…bb61e8`），补丁序列 0001-0006。

## 5.3 alpha 0.0.3 —— 隧道真实联测通过 + 双 ABI + 中文化（2026-10-24）

**端到端隧道联测（本机真实 sing-box 服务器）**：宿主机跑 sing-box v1.14.3 VLESS 服务端（127.0.0.1:12345），模拟器经 10.0.2.2 访问。灌入密钥 `vless://…@10.0.2.2:12345?security=none` 并启用后，服务端日志出现 `outbound connection to 149.154.167.41:443`（**Telegram DC2 数据中心**）并持续收发 20+ 秒——完整链路 App → 本地 sing-box → VLESS 隧道 → 服务器 → Telegram DC **全通**，进程零崩溃。

**过程中发现并修复的两个真问题**：

1. **模拟器 ARM 转译层崩溃（SIGILL）**：pelegram 预编译 AAR（仅 arm64）在 x86_64 模拟器上由 ndk_translation 转译执行，Go 运行时触发 fatal abort（tgkill+SIGILL，singbox-connect 线程）。对策：**自建 libbox AAR**（sing-box-lx 源码 + sagernet gomobile v0.1.13，`GOARM64=v8.0` 基线 + **双 target arm64/amd64**）——模拟器原生执行 x86_64 Go，真机 arm64 兼容所有 v8.0 设备。构建坑：①sing-box-lx 需全量子模块（utls 等 7 个，官方脚本只拉了 wireguard）；②gomobile 收尾需 JDK 在 PATH；③target 名是 `android/amd64` 不是 x86_64；④自建 AAR 的 libbox 接口比预编译版新，补齐 4 个方法（writeDNSQuery / cancelNotification / usePlatformBridge / createBridge）。
2. **深链导入被 DEBUG 门控**：pelegram 的 vless:// 分享链接导入在 release 构建无效（handleVpnTestIntent 有 `!BuildVars.DEBUG_VERSION` 早退）。已移除门控——release 支持 `am start … --es vpn_key "vless://…"` 一键导入连接。

**其他**：VPN 页 29 条字符串中文化（新建 values-zh-rCN + localeFilters 加 zh-rCN）；APK 双 ABI 92MB（arm64-v8a + x86_64，模拟器/Chromebook 全速原生）。**已知代价**：APK 体积 +40MB；生产分发版后续可回归 arm64 单 ABI 或按 ABI 拆分。

**本机测试环境（保留运行中）**：sing-box 服务端 `~/orioneko-toolchain/singbox/sing-box run -c /tmp/ox-singbox-server.json`（UUID 见 /tmp/ox-vless-uuid）；本地客户端联测配置 /tmp/ox-singbox-client.json（host 直跑验证协议用，已通过 HTTP 200）。

**产物**：`dist/OrionekoX-0.0.3-alpha-arm64-x64.apk`（92MB，versionCode 39，`sha256 f93f429f…`），补丁 0001-0007。

**事故记录**：本轮联测中误执行 `pm clear` 清除了用户登录态（本无必要），需用户重新登录收码。已记录教训：涉及用户数据的安装/测试动作前先确认不清数据。

## 5.4 alpha 0.1.0 —— 包名 GitHub 格式 + tg_read_chat + agent 管理（2026-10-24）

**包名迁移**：应用身份从 `sh.orioneko.messenger` 改为 **`io.github.blancatora.orionekox`**（GitHub 个人开发者命名惯例，取自 gh 认证账号 BlanCatOra，Java 包名规范全小写）。改动面：gradle.properties `APP_PACKAGE` + AppStandalone `google-services.json` client 条目，共两处。**代价：新包名 = 全新应用身份，需重新登录、重灌 VPN 密钥；旧 0.0.7（sh.orioneko.messenger）在模拟器上原样保留作后备。** versionCode 89（8×10+9），versionName 0.1.0。

**本轮四个功能**：

1. **`tg_read_chat` 工具**（默认 ask 权限）：按标题（精确→唯一包含匹配）读私聊/群/频道最近 N 条消息（1-50，默认 20）。主线程查 getAllDialogs 定位会话 → `TL_messages_getHistory` 网络请求 → 回调里从 res.users/res.chats 建发送者映射，倒序输出成 `[MM-dd HH:mm] 发送者: 文本`（服务消息/媒体占位、频道带 👁阅读数、单条截断 500 字）。找不到时返回可用会话列表让模型自纠；未登录返回友好错误。与 tg_list_chats 组成"先列后读"工作流。
2. **agent 行长按菜单**（主聊天列表）：置顶/取消置顶（agent.json 新增 `pinned` 字段，列表排序 pinned→非内置→名称）、编辑设置（直达聊天页并自动弹出设置弹窗，OxAgentChatActivity 新增 `openSettingsOnStart`）、删除（仅自建 agent，二次确认）。置顶行显示 📌 前缀；操作后经 adapter.notifyDataSetChanged() 重建 itemInternals 即时生效。OxAgentsActivity 行长按同样升级为该菜单。
3. **`.oxagent` 导入/导出**：导出走系统 `ACTION_CREATE_DOCUMENT`（文件名净化），**永不包含 API Key**（api_key 置空、built_in 置 false、附 format 标记）；导入走 `ACTION_OPEN_DOCUMENT`，name 必填校验、id 冲突自动改写（imp+随机后缀）。经 BaseFragment.onActivityResultFragment 回调，IO 在工作线程。
4. **摘要官 seed 升级**：AGENTS.md 现在明确驱动 `tg_list_chats` → `tg_read_chat` → 中文要点摘要工作流（含作者引用规则）。新装（正好是包名迁移后的全新安装）生效。

**QA（免登录项，包名迁移后登录态待用户恢复）**：aapt2 badging 确认新包名/89/0.1.0；10 条新字符串 EN+zh-rCN 双份在 APK 资源表（OxPinAgent=置顶 等）；dex 内确认 tg_read_chat 工具与新版摘要官 AGENTS.md；装机启动无崩溃；应用级中文（`cmd locale set-app-locales … zh-CN`）在引导页/登录页全量生效。**待登录后补测**：agent 行长按菜单实操、tg_read_chat 真实会话读取、导入导出文件选择器流程（QA 用 .oxagent 已放在模拟器 /sdcard/Download/qa-import.oxagent；mock 服务器已加 tg_read_chat 分支待联）。构建坑：google-services 插件按 client 包名匹配，改 APP_PACKAGE 必须同步改 google-services.json 否则 processXxxGoogleServices 直接失败。

**产物**：`dist/OrionekoX-0.1.0-alpha-arm64-x64.apk`（89MB，versionCode 89，`sha256 92af99fe…`），补丁 0001-0013。

## 5.5 alpha 0.2.0 —— 免登录智能体模式 + 远程 MCP 桥（2026-10-24）

**免登录智能体模式（agent-only）**：应用可在**完全不登录 Telegram** 的状态下使用——不碰好友/群聊体系，只接入本地与云端 agent。实现：IntroActivity 新增"🤖 先用智能体（免登录）"按钮 → `OxMode`（prefs 持久化）置位 → LaunchActivity `getClientNotActivatedFragment()` 拦截，冷启动直接落到 OxAgentsActivity 主页（内置三 agent、新建、导入、MCP 全部可用）；右上角常驻"登录 Telegram"入口（outline_add_account 图标），点击清除标志回到正常登录流。**tg_\* 工具在无账号时从工具清单隐藏**（OxMcpManager.tools() 统一注册表过滤，实测 TOOLS_OFFERED 无 tg_*）。已登录用户一切如旧（抽屉另有 MCP 入口）。

**远程 MCP 桥（Operit/Operit2 插件生态兼容）**：调研确认 Operit 生态的扩展通道是"远程 MCP"（插件市场分发端点），本轮实现完整 MCP 客户端：**Streamable HTTP 传输**（POST JSON-RPC 2.0，响应 JSON 与 SSE 双模式解析、Mcp-Session-Id 会话头、initialize→notifications/initialized→tools/list→tools/call 全握手、headersJson 额外鉴权头）。`OxMcpManager` 把每个启用服务器的工具包装成 OxTool（默认 ask 权限、inputSchema 原样透传给模型、与内置工具同一条权限确认链路）；进程内懒刷新（首次用到时后台 initialize）。管理页 OxMcpActivity：添加/启停（点击行）/长按刷新删除/工具计数，入口在智能体页右上角（免登录可用）+ 侧边抽屉（登录后）。Operit 的本地 QuickJS ToolPkg 插件包属另一通道，留 M4（需 JS 运行时桥）。

**QA（全部免登录完成，零崩溃）**：① Intro 跳过按钮 → 智能体主页；② 冷启动持久化直达主页；③ MCP 页添加 MockMCP（10.0.2.2:8878/mcp）→ 服务器日志完整握手 + "2 个工具"（SSE 响应解析验证）；④ 新建 MCPtest agent 接 mock OpenAI → 发 "mcp" → 权限弹窗允许 → 🔧 echo_tool → `↳ echo: hello-mcp` → 终答 MCP_BRIDGE_OK——**TOOLS_OFFERED 证实内置 4 工具+MCP 2 工具合并、tg_* 正确隐藏**；⑤ .oxagent 经系统文件选择器导入成功（QA导入助手出现）；⑥ 长按菜单置顶生效（📌 前缀 + 排序置顶，管理页与会话列表双处显示）。UI 自动化坑：键盘弹起后对话框输入框上移约 410px，必须二次 dump 重定位。

**产物**：`dist/OrionekoX-0.2.0-alpha-arm64-x64.apk`（versionCode 99，`sha256 79aeaa5e…`），补丁 0014。测试环境：/tmp/ox-mock-openai.py（:8877）与 /tmp/ox-mock-mcp.py（:8878，Streamable HTTP mock，SSE/JSON 混合）常驻。

## 5.6 alpha 0.3.0 —— 真 Telegram UI 本地模式（参考私有仓库架构）+ 猫耳图标（2026-10-24）

**架构转向（用户明确要求）**：不再用自绘 agent 管理页当主界面，"智能体模式"直接复用整套 Telegram UI——去掉在线/服务器依赖，而不是去掉 Telegram 界面。参考用户私有仓库（Orioneko 的 OrionekoBootstrap/SendMessagesHelper 拦截/OrionekoConfig 常量、OrionX 的"界面跟随 Telegram"设计哲学），移植其核心架构：

1. **伪造本地账号**（OxAgentsBridge.ensureLocalAccount）：SELF_ID=777000001 的合成 TL_user → UserConfig.setCurrentUser + saveConfig + setUserId → 整个 dialogs/chat/contacts 栈离线可用，主界面是真 MainTabsActivity（Chats/Contacts/Settings/Profile 四标签）。
2. **agent → 合成联系人**：每个 OxAgent 分配 tgId（AGENT_BASE=555000000+seq），TL_user 落 MessagesStorage/MessagesController（flags 位必须覆盖全部设置字段），INSERT dialogs 行 + 问候语注入（按 messages_v2 计数判断，替代一次性 flag）。
3. **发送拦截**（SendMessagesHelper.sendMessage 内、全部上游守卫之前）：agent 对话（555000000-655000000 区间）的纯文本发送被 OxAgentsBridge.interceptOutgoing 捕获——本地持久化 out 消息 + OxModelHub 工具循环 → 回复作为真消息写回。echo/云模型/MCP 工具（含权限弹窗，LaunchActivity 上下文）全部在真聊天页内工作。
4. **消息写入管线**：TL_message(flags=256, nextLocalIncomingId 序列) → putMessages（storage queue）→ **refreshDialogRow**（从 messages_v2 重算 dialogs 行 date/last_mid——putMessages 不维护该行，last_mid=0 的会话行在列表里不显示，这是本轮排障的主坑）→ 主线程构造 MessageObject（非主线程构造不安全）→ updateInterfaceWithMessages + dialogsNeedReload；流式回复用 replaceMessagesObjects 原位更新气泡。
5. **关键修复——onLogout 守卫**：冷启动后 ~0.5s tgnet 连真实 DC 收到 AUTH_KEY_UNREGISTERED 类响应 → native 回调 ConnectionsManager.onLogout → clearConfig+performLogout 把伪造账号抹掉（此前每轮 QA"复活 Intro"的真凶）。agent-only 模式下该回调直接忽略。
6. **抽屉裁剪**：本地模式下 ⋮ 菜单只剩 夜间模式/智能体/MCP 服务/登录 Telegram（登录项经 performLogout(0) 纯本地登出回 Intro）；新建群组/Saved Messages/钱包/附件机器人/代理全部隐藏。旧自绘 agent 行（DialogsAdapter VIEW_TYPE_OX_AGENT）在本地模式让位给真会话行（管理页保留，行点击直达真 ChatActivity）。

**猫耳剪影图标**：icon_foreground_sa PNG 全 5 密度重绘（深蓝 #091026 底 + 白色猫头剪影：圆头 + 双直立三角耳，设计以猫耳为主、耳位宽置带出 ox 联想但不向牛角靠）+ monochrome 向量 icon_neko_mono（Android 13 主题图标同款猫耳）+ legacy PNG 同步。启动器实测（Pixel Launcher 圆形遮罩）清晰优雅。

**QA 全程实录（模拟器）**：冷启动 → 真 Telegram UI 稳定落地（onLogout 被守卫，连测两轮）；会话列表 5 个 agent 全部以原生行显示（彩色渐变头像+emoji+未读徽标+排序随活动时间）；QA导入助手 echo 链路（发出→已读→回声回复，原生气泡）；MCPtest → mock 后端 → echo_tool 权限弹窗 → `echo: hello-mcp` → 终答——**TOOLS_OFFERED 含 MCP 工具、tg_* 无账号隐藏**，全程零 FATAL。排障期间修掉三个隐藏问题：①本地账号被 onLogout 抹除（上）②dialogs 行 last_mid 不刷新（上）③MCP 工具首条消息竞态（OxModelHub 在 worker 线程同步刷新 enabled 服务器）。

**产物**：`dist/OrionekoX-0.3.0-alpha-arm64-x64.apk`（versionCode 109，`sha256 91ff1a22…`），补丁 0001-0015。

## 5.7 alpha 0.4.0 —— 智能体资料页 + 头像 + 双耳图标（2026-10-24）

**图标重设计（用户明确规格）**：剪影改为**只有猫耳**——米黄（#F3E9D2）圆底 + 碳灰（#33312E）双耳（对称、外倾三角），弃用此前的整头剪影。同步更新：adaptive 前景 PNG 全 5 密度、背景 shape、monochrome 向量（Android 13 主题图标）、legacy PNG，以及**通知小图标**（R.drawable.notification webp 全 5 密度重绘为白色双耳 alpha 剪影——消息通知与 VPN 前台服务共用该图标）。启动器实测清晰。

**智能体资料页（OxAgentProfileActivity）**：对标私有仓库 Orioneko 的"添加好友"表单——头像（系统图片选择器 → 圆形裁剪）、昵称、备注、端点预设（DeepSeek/Ollama/LM Studio/opencode serve，后者两个即"局域网其他设备的 agent"入口）、Base URL / API Key / 模型、AGENTS.md、删除（自建）。入口：管理页长按菜单"编辑设置" + 聊天页（自绘版）齿轮。保存后 syncAgents 重镜像合成联系人。

**头像进真 UI 的三个坑（核心排障）**：
1. `putUser(u, fromCache=true)` 在内存字典已有该用户且非 min 时**不替换对象**（MessagesController.putUser 分支链），头像/改名永不生效——改 `putUser(u, false)` 直接替换；
2. 头像文件写入 FileLoader 缓存路径（合成 TL_userProfilePhoto + TL_fileLocationToBeDeprecated，`<volume>_<local>.jpg`，cache 目录）后，图片加载器仍不发起请求——放弃纯文件路线，改用 **`photo.strippedBitmap`**（ImageReceiver.setForUserOrChat 的即时渲染通道，BitmapDrawable 直接绘制，零文件依赖）——实测会话列表/聊天页头像立即可见。缓存文件仍写入作为大图来源。
3. TL_user 状态字段：`expires = currentTime + 大值` 会 int 溢出为负 → "last seen 27.05.22"古董日期；改 `expires = Integer.MAX_VALUE` 恒显示 online。

**备注镜像**：OxAgent 新增 remark 字段 → 合成用户 last_name → 聊天页标题/资料页显示（实测 "MCPtest QA"）。

**打字指示器**：流式回复期间向 `MessagesController.printingUsers` 注入本地 PrintingUser（TL_sendMessageTypingAction），4s ticker 续期 + finishTurn 清除，updatePrintingStrings() 改 public 后复用官方格式化。已知限制：每条消息落地时 `updatePrintingUsersWithNewMessages` 会清掉打印状态（原生行为），且流式期间 header 副标题实测仍显示 online——注入链路已按官方数据结构实现，视觉验证留待后续（可能被消息落地清除逻辑压制）。

**产物**：`dist/OrionekoX-0.4.0-alpha-arm64-x64.apk`（versionCode 119，`sha256 4475f7a8…`），补丁 0001-0016。QA：冷启动零崩溃、资料页编辑保存闭环、头像/备注/online 全部在真 UI 生效、双耳图标上屏。

## 5.8 alpha 0.4.1 —— 图标 AI 外包重设计（2026-10-24）

用户裁定手绘双耳"太丑"，图标设计外包给免费文生图服务 **Pollinations.ai**（flux 模型，免 key，限流 45s/张）。4 个候选生成后选定**纸艺猫面具**方案（米黄+碳灰品牌配色、闭目、构图居中、小尺寸可读）。Prompt 要点：logo/no face/no person/flat vector（第一版无约束跑偏成人物插画，水印用裁切去除）。加工管线：裁水印 → 全出血 adaptive 前景（5 密度）→ legacy 圆形遮罩 PNG → 背景色取图缘（#D9D2C2）→ monochrome 猫头向量（Android 13 主题图标）→ notification.webp 白色猫头 alpha glyph（消息通知+VPN 前台共用）。候选图存档 dist/icon-ai-candidate-*.jpg 供比选。产物 dist/OrionekoX-0.4.0-alpha-arm64-x64.apk（sha256 bdcb00fb…），补丁 0017。

## 5.9 alpha 0.4.2 —— 图标终版：灵动圆润双耳线条（2026-10-24）

AI 候选全数被否（"我要的是简约线条"），转向程序化绘制：先出 5 个线条变体（圈头双耳/只留耳线/眯眯眼/实心耳/细线版）做成对比预览页交用户挑选，用户定方向"**灵动、圆润、只要耳朵**"。终版：两只独立的圆角三角耳轮廓线（无头圈、无底边直线）——均匀线宽用 mask 内外圈法实现（外圈填充 + 内圈向质心收缩后挖空）；圆角用主顶点 + rounded_poly（大半径）直接成型（贝塞尔采样点上做圆角无效；细环也不耐受 blur+threshold 形态学圆角，会溶解）；"灵动"来自左右耳不同高度与外倾姿态；米黄 #F3E9D2 底 + 碳灰线，构图放大至圆内约 70%。通知小图标改用白色实心圆角耳（细线在 24dp 不可读）。产物 dist/OrionekoX-0.4.0-alpha-arm64-x64.apk（sha256 247c0ed1…），补丁 0018。过程教训：AI 文生图不适合精确 logo 场景（主体跑偏、水印、风格不可控）；程序化矢量 + 用户选图确认才是正确管线。

## 5.10 alpha 0.4.3 —— 图标定稿：FLUX 候选 A 裁耳版（2026-10-24）

用户从 FLUX.1-schnell（[Hugging Face Space](https://huggingface.co/spaces/black-forest-labs/FLUX.1-schnell)，免费 Gradio API 直调）三候选中选定 A（无脸奶油猫），并要求去掉眼睛/鼻子/嘴/胡须只留猫耳。实现走**像素级裁剪**而非重生成：逐行统计非背景像素，耳朵区（y355-495）与五官区（y505-645）之间存在干净空行 y500，以此为切线裁出耳条带（含粉色耳内），重组至 88% 宽正方形居中构图。通知小图标与 Android 13 主题图标由"非背景像素掩膜→白色剪影"派生；adaptive 背景色取图缘 #FDECD4。坑：drawable 下 icon_neko_mono 新旧 xml/png 同名 resource duplicate；adaptive XML 引用位图写成 @mipmap 致 link 失败。产物 dist/OrionekoX-0.4.0-alpha-arm64-x64.apk（sha256 44003066…），补丁 0019。

## 5.11 alpha 0.5.0 —— 照片到智能体 + 打字指示器修复（2026-10-24）

**照片到智能体**：在 `SendMessagesHelper.prepareSendingMedia` 顶部加路由 seam（agent 对话的媒体整体改道走桥，不再进入服务器上传管线）。桥端 `handleOutgoingMedia`：`generatePhotoSizes(path)` 产出本地已落盘的 TL_photo → 构建真实 outgoing 照片消息（flags HAS_FROM_ID|HAS_MEDIA、TL_messageMediaPhoto）→ persistAndShow；原图归档进 `oxagents/<id>/media/`（未来视觉模型/工具可用）；agent 应答"📷 收到 N 张图片"。实测：媒体选择器选图 → handler 触发 → 归档成功 → 应答气泡出现；**已知小问题：照片气泡的实时首帧渲染缺失，重进聊天（DB 重载）后完整显示**——DB 序列化路径全通，live-add 路径待查。

**打字指示器三重根因修复**（此前 header 始终显示 online/Connecting）：① Telegram 原生 PrintingUser 5.9s 过期清理 vs 4s 刷新间隔——续期赶不上清理节奏，改 **2s ticker**；② tgnet 在 agent-only 模式永远连不上、连接状态反复触发 `ChatAvatarContainer.updateCurrentConnectionState` 用 "Connecting" **强占 subtitle**——加 agent-only 门；③ `updatePrintingStrings` 的 printingStrings swap 经 runOnUIThread **异步**应用，而桥的通知同步发出——header 每次都读到上一帧的空数据——把通知也排进同一队列（swap 之后执行）。实测流式期间 header 实时显示 "··· typing"。

**MCP 服务器 headers 编辑**：添加对话框新增可选 headers JSON 输入（带 JSON 校验），支持带鉴权头的远程 MCP 端点。

**产物**：`dist/OrionekoX-0.5.0-alpha-arm64-x64.apk`（sha256 b0d97436…），补丁 0023。

## 5.12 alpha 0.6.0 —— M4 容器 PoC + 视觉消息 + 规划收口（2026-10-25）

**照片实时渲染**：复核确认 0.5.0 的"首帧缺失"为误判——照片气泡实时显示正常（TYPE_PHOTO 检测正确，diagnostic 证实 type=1）。诊断日志保留在 persistAndShow。

**视觉消息接入模型**：OxChatEntry 增加 imagePath；照片入历史时记录归档路径；baseMessages 对带图条目构建 **OpenAI 多模态 content-parts**（text + image_url data-URI base64）。mock 服务器加了多模态日志（MULTIMODAL parts/hasBase64Image）。端到端聊天 UI 触发受模拟器自动化掣肘未能闭环（发送按钮坐标漂移+模拟器卡顿），wire format 按标准实现待真机首用验证。

**其它收口**：keystore 移出 git（保留本地）；`tg_read_chat` 增加 contains/since_hours 过滤参数；分段 QR 连扫核实为 0.0.x 已完整实现（AmneziaQR 多段协议：逐段累积/进度提示/相机保持打开）。

**M4 容器 PoC（重点）**：
- 基础设施全通：Termux 版 proot 5.1.107（x86_64，NDK 构建）+ libtalloc + libandroid-shmem 随 APK 进 jniLibs（nativeLibraryDir 可执行，W^X 合规）→ **proot 在 Android 上成功运行**；`OxContainerManager` 运行时下载 Ubuntu base 22.04 rootfs（29.8MB）→ toybox tar 解压 → `proot -r rootfs -0 /bin/sh -c` 执行；新增 `shell_in_container`（ask）与 `container_status`（auto）工具，实测工具链触发/权限/下载/解压全通，`uname -a`/os-release 在 adb 环境验证 Ubuntu 22.04.5 LTS 运行。
- **平台墙（关键发现）**：guest 二进制（dash/ld-linux）位于应用数据目录，Android 10+（targetSdk≥29）的 **W^X 策略禁止应用执行自身数据目录内的文件**（app 域 SIGSYS/ENOENT，adb shell 域可跑）。这正是规划中"容器可执行文件以 lib*.so 进 APK"要绕开的约束。
- **完整解法（下一里程碑）**：独立"容器宿主"伴生 APK（targetSdk 28，Termux 同款豁免）经本地 socket/HTTP 提供 exec API——恰好契合"可选安装的容器"产品形态；或经 Shizuku/root 通道执行（权限体系既定规划）。工具遇到该墙时优雅返回 proot 错误。
- 技术细节：termux proot 动态链接 → LD_LIBRARY_PATH=nativeLibraryDir；SONAME `libtalloc.so.2` 不匹配 APK 的 lib*.so 提取规则 → 二进制补丁 .dynstr 改为 `libtalloc.so`；`PROOT_TMP_DIR`+`PROOT_NO_SECCOMP=1`。

**磁盘事故（教训）**：`git add -A` 历史累积了 8 个版本 ×90MB APK 与构建产物 → `.git` 膨胀至 **117GB**；`git format-patch` 又生成 7-19GB/个的二进制补丁（86GB）。处置：dist 移出跟踪 + .gitignore 补全 + 历史重写（filter-branch 删 dist/patches）+ gc：`.git` 117GB→849MB，磁盘可用 108MB→170GB。**今后：提交前先看 .gitignore，dist/build 永不入库。**

**产物**：dist/OrionekoX-0.6.0-alpha-arm64-x64.apk（sha256 43231996…，versionCode 129），补丁 0001-0028（重建系列）。

## 5.13 alpha 0.7.0/0.7.1 —— Operit 式六步首次引导（2026-10-25）

按需求实现完整引导，采用 Telegram 原生设计语言（ViewPager + BottomPagesView 圆点 + 底部圆角主按钮，与 IntroActivity 同款动效与主题色）：
1. **开源与 AI 编码**：GPL-2.0 说明、仓库地址（可点开）、AI 编码声明。
2. **法律与免责**：仅面向非中国大陆地区用户；开发者不对 Agent/工具/容器/VPN 造成的资料丢失、数据损坏、隐私泄露等负责。
3. **登录询问**：「登录你的 Telegram 账号，让 Agent 进驻你的好友列表吗？（你可以在 Ox 设置中随时更改）」→ 前往登录 / 不是现在（以 Ox 模式启动）。
4. **配置 Agent**：API Key 直连（建 agent 并进资料页）、OxLinux 容器配置（跳第 6 步）、其他设备 Agent（预填 LAN 端点）。
5. **权限配置**：普通权限 / 无障碍 / 应用内 ADB 无线调试 / Shizuku / 设备管理员 / ROOT 六项，实时检测（绿色已授权、灰色待授权、未检测到），点击灰项发起申请——实测状态全部真实（USB ADB·已授权、Shizuku 未安装、ROOT 未检测到、设备管理员未授权）。新增空实现 OxAccessibilityService 与 OxDeviceAdminReceiver（manifest 注册）使这些通道真实可申请。
6. **OxLinux 容器环境**：镜像源（官方/TUNA/USTC/阿里云）、系统版本（22.04/24.04）、组件勾选（Node.js/Python 3/Git/curl/常用工具），配置持久化，附宿主组件说明。

**通用能力**：步骤指示「第 x 步 / 共 6 步 · 还剩 y 步」（盘古之白排版）、可跳过（第 3 步与末步除外）并注明「之后可在 Ox 设置中重新运行本引导」；首启自动进入引导（LaunchActivity 路由），完成后按选择进入登录或 Ox 模式；新增 **Ox Settings** 页（当前模式/重新运行引导/Agents/MCP/容器配置），抽屉与管理页均有入口。实测：六页全部走通、重入引导成功、完成后落在 Ox 模式主页。

**顺带修复**：pm clear 后遗留的孤儿 agent 会话行（DB 中 tgId 无对应 agent）→ syncAgents 增加区间清理，实测「Deleted Account」幽灵行消失；上游字符串 UnconfirmedAuthMultipleFrom_other 的非定位多参数与 Telegram 撇号转义问题（aapt2 全量重编译时暴露）。

**产物**：dist/OrionekoX-0.7.1-alpha-arm64-x64.apk（sha256 70f2916e…，versionCode 149），补丁至 0029。

## 5.14 alpha 0.8.0 —— 引导逻辑纠错 + 全面中文化 + Ox 页面原生化（2026-10-25）

这一轮针对 0.7.x 的实机反馈做系统性修正，流程图见仓库 `docs/ox-flows.md`。

**引导页逻辑（按流程图重写）**
- 第 3 步两个选项不再直接结束引导：只记录 `mode`，继续第 4–6 步；最后一步主按钮按模式变成「登录 Telegram」或「开始使用」。
- 第 4 步容器入口不再跳到第 6 步，只置 `container_enabled`；步数固定 6 步，不再跳变。
- 第 4 步不再预创建占位 Agent：改为草稿编辑器，保存才落盘，取消/返回不留垃圾。
- 第 2 步增加「我已阅读并同意」勾选，未勾选时主按钮置灰并给出提示。
- 底部操作区位置固定：主按钮 48dp 固定槽位；次级行左「退出引导」右「跳过此步」；不可用时用 `INVISIBLE` 占位。第 1 步隐藏返回箭头，第 2–6 步可回退；重入引导完成后返回 Ox 设置，不再叠主页。
- 动效：`ScaleStateListAnimator` 按钮按压、ViewPager 视差淡入、步数淡入、勾选/权限状态缩放；首屏使用启动图标同款米黄猫耳品牌图。

**导航栈**
- 新增 `LaunchActivity.resetToFragment/resetToMain/resetToLogin`；模式切换统一走 `OxMode.setPendingMode + performLogout + appDidLogout`，由 LaunchActivity 重建单根栈。
- 首次运行：登录 → 仅 LoginActivity；Ox → 仅 MainTabsActivity。重入且模式不变 → `finishFragment()` 回设置。禁止从二级页直接 present Dialogs/MainTabs。

**中文化**
- 内置 `assets/oxlang/zh-cn.xml`（11,594 键，~925 KB），注册内置 `简体中文` LocaleInfo；中文系统启动自动选中，也可在语言列表手动切换。
- 核心旧键复用社区官方译文；其余用本地 OPUS-MT en→zh（Argos 1.9，CTranslate2 int8）离线翻译，按 token 分段以保护 `%1$s`、`**`、`<a>`、URL、`un1` 与品牌名。语言列表不再显示「(自定义)」。
- 引导页/Ox 设置/Agents/MCP/资料页全部中文；主界面实测「搜索聊天 / 聊天 / 联系人 / 设置 / 个人资料」。

**Ox 页面原生化**
- Ox 设置、Agents 列表、MCP 列表统一灰底白卡 + 分区标题 + `TextSettingsCell`/原生行高/72dp 分隔线；Agents 用 50dp 圆头像 + BOT 徽标。
- Agent 编辑页：Provider 选择器含 28 个预设与 26 个品牌图标（simple-icons / lobe-icons，矢量路径已用 svgelements 规范化）、接口地址/API Key/模型分区标签、密钥眼睛开关、获取 Key、模型预设、测试连接、自定义请求头键值增删改、AGENTS.md、保存勾选。
- MCP 页支持启用/停用、长按刷新/删除、添加对话框（URL + headers JSON 校验）。

**Ox 模式彻底离线**
- `ConnectionsManager.init` 在 agent-only 下传 `userId=0 / enablePushConnection=false / hasNetwork=false` 并立即 `native_pauseNetwork`；`setUserId`/`checkConnection`/`setPushConnectionEnabled`/`sendRequestInternal` 全部短路；语言包远程下载短路。
- 实测：强制停止并重启后 `ss -tnp` 中 Telegram DC（95.161/185.45/91.108/149.154）连接数为 **0**，应用功能正常。

**授权**
- 保留上游 `LICENSE`（GPL-2.0）；新增 `LICENSE-AGPL-3.0` 与 `docs/LICENSING.md`：OrionekoX 原创代码以 AGPL-3.0 授权，因与 GPL-2.0-only 上游组合，分发 APK 整体仍为 GPL-2.0；两份授权范围写清楚。README 与设置页 License 行同步为 AGPL-3.0。

**已知限制**
- 非核心/新功能的机器译文可能生硬或有误；核心 UI 走官方旧译文 + 术语表。
- 首次运行清空数据路径未重复测试（避免清掉测试数据）；重入引导、模式选择、返回栈均已实测。
- 真实账号 ↔ Ox 模式的切换会登出并清理该账号数据，代码要求二次确认；未在真实账号上执行破坏性联调。
- OxLinux 宿主组件（targetSdk 28 companion app / Shizuku/root）仍是 M4-next。

**产物**：dist/OrionekoX-0.8.0-alpha-arm64-x64.apk（versionCode 159，sha256 ccc194e0…），补丁至 0030。

## 5.15 alpha 0.8.1 —— 反馈修复：GPL-2.0、引导细节、Provider 完整化、离线骨架（2026-10-25）

本版覆盖用户对 0.8.0 的第二轮反馈；5.14 中的 AGPL/GPL 双授权方案作废，统一为 GPL-2.0。

**授权与首屏**
- 仓库、应用、文档全部改为 **GPL-2.0**；删除 `LICENSE-AGPL-3.0`，重写 `docs/LICENSING.md`，设置页 License 显示 GPL-2.0。
- 首屏文案改为：“OrionekoX 基于开源的 Telegram Android 客户端构建，采用 GPL-2.0 授权。”
  和“本项目含大量 AI Coding 代码，开发者不对 AI 代码负责。”

**引导页**
- 跳过提示只在第 4–6 步显示，文案改为“你可以先跳过此设置；之后可在 Ox 设置 → 重新运行引导 中修改。”
- 内容区与底部栏改为垂直布局，底部栏不再覆盖页面；权限/容器页最后一行可以完整滚入视野。
- 第 4 步重新归类：只保留“API Key（云端 + 局域网 Ollama/LM Studio/vLLM/opencode 等）”
  和“OxLinux 容器（Bot 兼容）”；后者只在第 3 步选“登录 Telegram”时显示，
  Ox 模式下隐藏，避免未登录使用 Telegram Bot 兼容接入。
- 容器副标题改为“可选，为 Agent 准备一个离线 Ubuntu 环境。”（逗号）。
- 修复 LayoutHelper 的 dp 二次换算（提示行 32dp、AGENTS.md 150dp、底部栏高度），
  底部操作区显著变矮但仍保持固定槽位。

**Provider / Agent 编辑**
- 模型名按当前服务商实际情况更新（DeepSeek V4、OpenAI GPT-5.x、Claude 5、Gemini 3.8、
  Kimi K3、Qwen 3.8、GLM 5.3、MiniMax M3、Grok 4.7 等）。
- 新增 **OpenCode Zen、CommandCode、OpenDesign、自定义（BYOK）**；补齐 CommandCode/
  OpenCode 图标，共 32 个预设、28 个独立品牌图标。
- OpenRouter 自动预置 `HTTP-Referer`/`X-Title`，Anthropic 自动预置 `anthropic-version`。
- Provider 选择器改为 **半屏底部弹层 + 可滚动列表**，不再全屏铺满。
- 新增“从 API 获取模型列表”（GET `/models`，带 API Key 与自定义头，过滤 embedding/
  whisper/tts 等非聊天模型）；实测 OpenCode Zen 成功拉取并选择。
- 新增高级参数：Temperature、Top P、最大输出 tokens、上下文长度（0=自动，用于历史
  消息粗略裁剪）；保存到 agent JSON 并写入 `/chat/completions` 请求。

**Ox 模式离线**
- 聊天列表的 Flicker 骨架来自 `DialogsAdapter` 在 `isDialogsEndReached=false` 时插入的
  占位行，不是 Telegram 网络连接；agent-only 下不再插入。
- 原生 MTProto 仍保持 `userId=0` + `native_pauseNetwork` + 请求短路；强制停止后冷启动
  连续采样 3 秒，Telegram DC 连接数 **0**；界面无骨架、无 Connecting 状态。

**产物**：dist/OrionekoX-0.8.1-alpha-arm64-x64.apk（versionCode 169，sha256 待补），补丁至 0031。
已知限制：非核心新功能机器译文可能生硬；真实账号 ↔ Ox 模式切换的破坏性路径未实测。

## 5.16 alpha 0.8.4 —— 人化改造 + Operit 差距补齐 + 全流程 QA（2026-10-25）

本版按“简单易用 + 强大硬核”重做 Ox 自写界面，并参考 Operit/Operit2 补齐用户最缺的能力。
授权统一为 GPL-2.0（5.15 已完成）。

**设计 skill 收集（9 个 / 3 个方向）**：`design-skills/` 保存 9 个公开 agent skill 的
SKILL.md 与文本参考：移动视觉（ui-ux-pro-max、mobile-app-ui-design、mobile-app-design）、
UX/交互（oil-ui、superdesign、ui-audit）、设计系统/控件（design-system、james-design、
claude-design-skill）。`docs/design-brief.md` 固化了 4/8/12/16/24 间距、12/16/24 圆角、
28/22/17/15/13/12 字号、44/48 触控、120/180/220ms 动效等 token。

**引导页重做**：灰色页面 + 白色圆角卡片；顶部固定 6 段进度条 + “第 x 步 / 共 6 步”；
品牌大图标 + 米黄光晕；标题 28sp、正文 15sp；选项为 44dp 图标磁贴 + 16sp 标题 + 13sp 副标题；
法律正文/同意/权限/容器全部卡片化；底部操作区与内容区垂直分离，不再覆盖内容；
提示只在第 4–6 步显示；隐藏 Bot 兼容容器入口在 Ox 模式、显示在登录模式；
局域网归入 API Key。按钮/勾选/进度/hero 入场均有动效。

**Agent 编辑重做**：头像 hero 卡片；Provider 半屏弹层（32 预设、28 品牌图标、搜索）；
接口地址/API Key/模型标签、Key 眼睛、获取 Key、测试连接；从 `/models` 拉取模型；
高级参数默认折叠（Temperature/Top P/max_tokens/context/presence/frequency/top_k/seed/
stop/reasoning_effort/tool_choice/parallel_tool_calls/额外 JSON），保存后写入请求；
自定义请求头增删改；草稿取消不落盘。

**Operit 差距补齐**：新增 `工具与权限` 页面（Android 权限状态 + 每个 Agent 的逐工具
auto/ask/deny 策略，MCP 工具同样可管，策略持久化）；新增内置工具 `remember`/`recall`
（长期记忆注入 system prompt）、`list_dir`/`read_file`/`write_file`（每 Agent 私有
workspace，路径逃逸防护）、`notify`/`open_url`。`docs/operit-gap-analysis.md` 给出
与 Operit/Operit2 的逐项对比和“用户需要吗”的判断：UI 自动化、ToolPkg/Skills、工作区/
终端、工作流、浏览器/OCR/语音、角色卡/群聊等列为下一步。

**QA（模拟器 API34，0.8.4）**：逐页/逐控件走查，详见 `docs/qa-report-0.8.4.md`。
关键结果：冷启动 0 Telegram socket、无骨架；引导 6 页、模式选择、进入登录页、备份/恢复；
Agents 新建/保存/删除；Provider 搜索/模型拉取/高级折叠；聊天 + `remember`/`write_file`
工具链与权限弹窗；记忆文件与 workspace 文件落盘；MCP 空态/添加/错误提示；工具策略切换
持久化。**修复**：重复欢迎语（storage 队列锁 + 已问候集合）、ADB 状态截断、工具描述
英文化、Provider 弹层全屏无搜索、聊天列表 Flicker 骨架。

**产物**：dist/OrionekoX-0.8.4-alpha-arm64-x64.apk（versionCode 19，sha256 51269aa3…），
补丁至 0032。

## 5.17 alpha 0.8.5 —— 登录页卡死的诊断与超时保护（2026-10-25）

**现象**：模拟器登录页输入号码后，右下角箭头变成无限转圈，无法进入验证码页。

**诊断**：`agent_only=false`，请求没有被 Ox 离线守卫拦截；`logcat` 显示
`ConnectivityService validation failed`、Google/Mozilla DNS 与 Telegram DC 全部连接失败——
模拟器本身没有可用外网（或未配置代理/VPN），`auth.sendCode` 一直在等网络，所以 UI 永远是 spinner。
**这不是登录逻辑被 Ox 模式写坏，也不能在无网环境完成 Telegram 认证。**

**修复**：在 `LoginActivity` 的 `auth.sendCode` 路径增加 25 秒 UI 超时。超时后：
- 取消该请求、停止 spinner、恢复右下角按钮；
- 弹 Telegram 原生提示“无法连接 Telegram，请检查网络或代理后重试。”；
- 迟到的回调被 `loginTimedOut` 丢弃，不会在超时后又跳页。

实测（0.8.5，模拟器无外网）：点击箭头后 spinner 正常转，25 秒后自动停止并出现上述提示，
按钮恢复可再次点击；有网环境仍走原生登录流程。**真机/模拟器需先配置可访问 Telegram 的网络或代理。**

**产物**：dist/OrionekoX-0.8.5-alpha-arm64-x64.apk（versionCode 20，sha256 3f59ba37…），补丁至 0033。

## 5.18 alpha 0.8.6 —— 删除 Intro 路由 + 登录页本地模式入口 + 模拟器代理打通（2026-10-25）

**Intro 页面来源**：上游 `IntroActivity`。当 `onboarding_done=true`、`agent_only=false` 且没有激活账号时，
`getClientNotActivatedFragment()` 返回它；它不是我们的引导页，之前只额外加过“使用本地模式”按钮。

**修正**：
- 未登录且引导已完成 → 直接 `LoginActivity`，不再走 Intro；登出后的默认落点也改为 LoginActivity。
  `IntroActivity` 类保留但已不在任何路由里（无 `new IntroActivity` 引用）。
- 登录页手机号输入框下方新增“使用本地模式”，点击即启本地账号并重置到 MainTabs；
  用户不再需要经过 Intro 才能进 Ox 模式。

**模拟器代理配置（本机 FlClash）**：
- 主机 FlClash 混合端口 `7890`（HTTP/SOCKS5），已确认可访问 `api.telegram.org`。
- 模拟器全局 HTTP 代理：`settings put global http_proxy 10.0.2.2:7890`。
- App 内 MTProto 代理（mainconfig）：`proxy_enabled=true`、`proxy_ip=10.0.2.2`、`proxy_port=7890`、
  `proxy_type=0`（SOCKS5）。
- 实测：登录页点箭头后 App 建立到 `10.0.2.2:7890` 的连接，`auth.sendCode` 成功返回，
  进入验证码页（提示验证码已发到另一台设备）；未输入任何验证码，随后恢复 Ox 本地模式。
- 说明：模拟器侧代理指向宿主机 `10.0.2.2`；真机需配置真机可访问的代理/VPN。

**产物**：dist/OrionekoX-0.8.6-alpha-arm64-x64.apk（versionCode 21，sha256 27ad0219…），补丁至 0034。

## 5.19 alpha 0.8.7 —— 统一术语为“Ox 模式”（2026-10-25）

用户要求把所有“本地模式”统一为“Ox 模式”。已更新全部用户可见文案：
- 登录页入口：使用本地模式 → **使用 Ox 模式**
- 模式选择/退出/切换说明：Ox 本地模式 → **Ox 模式**
- Intro 入口文案（类已不在路由）：使用 Ox 模式
- 文档/QA 报告同步；不再残留“本地模式”或“local mode”的用户可见字样。

**产物**：dist/OrionekoX-0.8.7-alpha-arm64-x64.apk（versionCode 22，sha256 f084a509…），补丁至 0035。

## 5.20 alpha 0.8.8 —— 登录页“删除键失灵”修复（2026-10-25）

**现象**：登录页把号码删完后，输入框里仍显示“555 120 0000”，看起来像删除键无效。

**根因**：这不是删除键失效，而是输入框为空时显示的国家号码**占位提示**。上游把国家格式里的
`X` 替换成 `0`（`hint.replace('X','0')`），于是提示看起来像真实号码；继续按删除当然不会变化。

**修复**：占位提示改为下划线形式（把数字/`X` 替换为 `_`，如 `___ ___ ____`），空框一眼可辨；
实际删除逻辑未改。实测：连续退格可把 `555 123 4567` 清空，输入框回到空状态；
点“使用 Ox 模式”可正常回 Ox 主页。

**产物**：dist/OrionekoX-0.8.8-alpha-arm64-x64.apk（versionCode 23，sha256 43cb5799…），补丁至 0036。

## 5.21 alpha 0.8.9–0.8.13 —— 登录态全流程走查与代理/聊天修复（2026-10-25）

**环境**：模拟器 API34，真实 Telegram 账号已登录，FlClash 代理 `10.0.2.2:7890`。

**走查范围（未发送任何真实消息、未打开真实会话）**：首页四 Tab、抽屉全部入口、Ox 设置、
工具与权限（逐项权限状态、逐工具 auto/ask/deny 循环与持久化）、Agents 列表/长按菜单/
资料页/Provider 半屏搜索/高级参数折叠、MCP 空态与添加、引导重入、搜索输入、代理设置页、
快速连按返回、横竖屏旋转、Home/回到前台。无崩溃，返回栈正常。

**发现并修复**：
1. **资料页标题被全局代理状态覆盖**：`OxAgentProfileActivity` 设了
   `setAllowOverlayTitle(true)`，登录态“正在连接到代理”的全局 overlay 直接盖掉“智能体资料”。
   改为 `false`，标题恢复；其它 Ox 页本就用 false。
2. **登录态虚拟 Agent 行打开原生 ChatActivity 会永久转圈**：合成用户/会话只在本机 DB，
   不在服务器 dialog 列表，原生 ChatActivity 等 `messages.getHistory` 永不返回。
   已回退到可用的 `OxAgentChatActivity`，并把它重做成接近原生的聊天：ActionBar 头像/名称/在线、
   灰底壁纸、左右气泡/尾巴圆角/时间戳、工具气泡胶囊、输入栏 + 蓝色发送键、生成中“正在输入…”。
3. **为未来原生聊天铺路**：`interceptOutgoing`、媒体路由、typing 清理不再要求 agent-only；
   登录态向合成 Agent 发消息也会被本地拦截，不再尝试发往 Telegram 服务器。
   **剩余缺口**：登录态原生 ChatActivity 需要把合成 dialog/message 注入 `MessagesController`
   的 dialogs_dict/dialogMessage 并避免服务器 history 覆盖，尚未实现；当前用原生风格本地聊天页。（已在 0.8.14–0.8.16 解决，见 5.22）

**隐私**：本次没有向任何真实联系人或群组发送消息，没有打开真实会话；报告不记录账号/会话名称。

**产物**：dist/OrionekoX-0.8.13-alpha-arm64-x64.apk（versionCode 28，sha256 50b850d4…），补丁至 0037。

## 5.22 alpha 0.8.14–0.8.16 —— 本地智能体复用原生 Telegram 聊天（2026-10-25）

**目标**：回答并落地“本地智能体为什么不能复用 Telegram 聊天界面/系统，非要自己写”。

**做法**：不再自绘聊天页，直接把合成 Agent 接入 stock `ChatActivity`：

1. **会话注入**：`OxAgentsBridge.ensureNativeDialog()` 在打开前把合成用户写入
   `users` 表与 `MessagesController.users`，并把最后一条本地消息注入 `dialogs_dict`，
   原生聊天页因此能立即解析 peer、加载缓存历史，不再等服务器 `messages.getHistory`。
2. **历史完全本地化**：`MessagesController.loadMessagesInternal` 对 Agent 会话的
   服务器刷新直接返回；缓存通道照常投递历史，既不转圈也不把伪造 user id 发给 Telegram。
3. **RPC 兜底**：`ConnectionsManager.sendRequestInternal` 新增 `isAgentRequest()`，
   凡 peer/to_peer/id 指向 Agent 段的请求（history、readHistory、setTyping 等）
   一律本地丢弃——登录态也不会向服务器发送任何伪造用户操作。原生删除消息单独走
   `cacheOnly`：`messages.deleteMessages` 按裸 id 删除、无 peer，本地 id 可能误伤
   服务器上同 id 的真实消息；清空历史也改为本地删除并立即回调。
4. **实时消息**：本地注入消息后补发 `NotificationCenter.didReceiveNewMessages`，
   原生 ChatActivity 才能把新气泡插到当前列表；流式回复首帧同样补发该通知，
   后续用 `replaceMessagesObjects` 原位刷新。
5. **身份修复**：ChatActivity 的“未知号码显示电话”分支对 Agent 会话禁用，
   标题恢复为 `emoji + 名字`，副标题为“在线”；点击头像/标题改为打开
   `OxAgentProfileActivity`，不再请求 `users.getFullUser`。
6. **消息 id 修复**：改用持久化的 `local_msg_seq` 生成单调 id。此前用
   `UserConfig.getNewMessageId()`，每次启动都从 -210000 重来，跨会话 REPLACE 会
   覆盖旧消息；实测 alpha/beta 跨重启均保留。
7. **输入区离线化**：Agent 会话隐藏 Telegram 云端“AI 编辑器”按钮（其底层会走
   Telegram AI 服务）；非文本消息（语音/文件/位置等）不再漏到服务器，改为本地
   提示“暂不支持向智能体发送这类内容”。
8. **删除自绘聊天页**：`OxAgentChatActivity` 已无入口，删除；Agents 管理页点击
   也统一走 `ensureNativeDialog` + 原生 `ChatActivity`。
9. **中文修正**：`ScheduledHint`（“长按可定时发送消息。”）、`AIEditorHint`、
   `ScheduledOrNoSoundHint` 三处机翻修正，zh 包版本 10→11。

**实测**（模拟器 API34，登录态，只与本地 Echo 智能体交互）：
标题 `🔁 Echo Assistant`/在线、头像、壁纸、原生输入栏与发送键均正常；
发送 `ping/test2/alpha/beta` 均即时出现绿色气泡与白色回声气泡，跨重启消息不丢；
返回聊天列表无重复行；AI 编辑器按钮不再出现。

**产物**：dist/OrionekoX-0.8.16-alpha-arm64-x64.apk（versionCode 319），补丁 0038。

## 5.23 alpha 0.8.17 —— `.debug` 并存测试包 + Ox 模式三处实测修复（2026-10-25）

**新增测试包**：`io.github.blancatora.orionekox.debug`（桌面名 OrionekoX Debug，
versionName 0.8.17-debug，versionCode 329，debug 构建不混淆、保留完整日志），
与正式包同签名、不同包名，可同时安装、数据完全隔离。调试包首次启动会走引导，
因此可以独立测试 Ox 模式和登录模式，不再需要把正式包登出。
`TMessagesProj_AppStandalone/build.gradle` 的 debug buildType 增加
`applicationIdSuffix '.debug'`、`versionNameSuffix '-debug'`；新增
`src/debug/res/values/strings.xml` 覆盖 AppName；`google-services.json` 补
`.debug` client 条目（否则 Google Services 插件按包名匹配失败）。

**用测试包跑双模式后发现并修复**：

1. **Ox 模式聊天列表空列表（竞态）**：首次引导完成后 `DialogsActivity` 的缓存
   读取发生在 `greetIfEmpty()` 写入欢迎消息之前，`last_mid=0` 的 dialog 行被跳过；
   随后 `loadDialogs(fromCache=false)` 把 `loadingDialogs` 置 true 后请求被
   agent-only 丢弃，标志永远不复位，后续缓存重载全部被挡。修复：agent-only 下
   `loadDialogs` 的服务器分支直接标记 `dialogsEndReached/serverDialogsEndReached`
   并返回；`syncAgents` 在欢迎语落库后延迟重读本地 dialog 列表（0ms/1200ms 两次）。
   实测冷启动和模式切换回来后 3 个智能体都立即显示。
2. **Ox 模式下仍弹通知/联系人/媒体权限**：引导完成后 `DialogsActivity` 的
   afterSignup 权限流程和 `askForPermissons` 会连续弹系统权限框。修复：agent-only
   时整段跳过通知/联系人/存储权限流程（本地智能体不需要这些权限）。
3. **运行时切回 Ox 模式后 tgnet 继续连 Telegram**：进程先前为登录模式，native
   client 已联网；点“Use Ox mode”只设置了 prefs 和本地账号，没有重新暂停网络，
   logcat 可见 dc2/dc4 connecting。修复：`ensureLocalAccount()` 在 agent-only 下
   调用 `setAppPaused(true, false)` 重新暂停，并补发
   `didUpdateConnectionState` 清掉残留的 “Connecting…” 标题。实测切回后
   `tgnet: pausing network and timers`，连接全部 suspend/disconnected，无新增连接。
4. **首次引导期间完全不联网**：此前引导页阶段 `agentOnly=false`，即使还没选模式，
   tgnet 也会连 DC（实测首启 8~10 条 connecting）。现在 `ConnectionsManager.init`
   在“引导未完成且无已激活账号”时按离线初始化（`oxOfflineMode`），`setAppPaused`
   /checkConnection/setUserId 同样尊重该标志；引导选“登录 Telegram”时调用
   `resumeAfterOnboarding()` 恢复网络。实测引导全程 0 条 tgnet 连接，选登录后
   dc2/dc4 正常连接并进入验证码页；再点“Use Ox mode”后
   `pausing network and timers`，连接全部 suspend。
5. **模式往返**：⋮ → Sign in to Telegram → OK → 登录页 → Use Ox mode → 回到
   Ox 主页，本地账号重建、列表恢复、聊天收发正常；正式包登录态未受影响。

**产物**：`dist/OrionekoX-0.8.17-alpha-arm64-x64.apk`（versionCode 329，
sha256 `99c33b60…`）；`dist/OrionekoX-0.8.17-debug-arm64-x64.apk`（同版本，
sha256 `3b4907e6…`）；构建/测试说明见 `docs/debug-build.md`；补丁 0039。

## 5.24 alpha 0.8.18 —— 资料/翻译/设置重构、消息媒体、原生资料页、终端（2026-10-25）

**翻译修正（内置 zh 包 v12）**：`恒星数/恒星`→`Stars`（`Telegram Stars` 不再叫“Telegram 恒星数”）；
个人资料页 `员额`→`帖子`（`存档帖子`、`还没有帖子…`、`公开帖子`）；`Bio/生物`→`简介`
（账户副标题改为“号码、用户名、简介”）；`QR代码`→`二维码`；`18年龄`→`18岁`；
`设定照片`→`设置头像`；`公众照片`→`公开照片`；附件菜单 `条款`→`文章`；`核对表`→`待办清单`；
`配置页面`→`个人资料页`。

**设置页重构**：Settings 里新增 `Ox 设置`（副标题“模式、本地大会员、Agent 与工具”）、
`代理设置`、`VPN 设置` 三个独立入口；代理/VPN 从“数据与存储”移除；移除 Telegram Premium、
Stars、TON、Business、Send a Gift、Wallet 等商业入口（设置页、个人资料页菜单、聊天列表
溢出菜单同步移除）；新增“本地大会员”开关：`OxMode.isLocalPremium` → `UserConfig.isPremium()`
返回 true，本地解锁 Premium UI，不订阅、不请求服务器。

**智能体完全复用 Telegram 列表/资料页**：删除 `DialogsAdapter` 的 `OxAgentCell` 虚拟行，
两种模式下智能体都以 stock `DialogCell`（最后一条消息、时间、已读勾、在线点）出现；
点击走 `ensureNativeDialog`。聊天头部标题/头像点击打开 stock `ProfileActivity`
（注入 `TL_userFull`、展开头像、简介“more”展开）；智能体用户设 `bot=true` 隐藏 Gift/电话，
头部副标题仍显示“在线”；资料页操作沿用 Telegram 原生（Message/Mute/Share/Stop）。

**媒体与消息动作**：照片走原生 photo 气泡 + 归档 + 视觉输入（base64 content parts），
有模型时可直接看图回答；视频/GIF/文件/语音/音乐/贴纸走 `TL_messageMediaDocument`
（本地 `attachPath`，文件气泡显示名称/大小，GIF 显示缩略图）；位置为原生地图气泡 +
经纬度提示；联系人/投票/待办清单生成本地媒体气泡并把结构化内容传给 Agent；
文件复制到工作区 `inbox/` 供 `read_file/list_dir` 工具读取。长按“回复”会生成原生回复
头和引用，并把引用文本（截断 300 字）传给 Agent；删除对话框对智能体隐藏“同时为…”
复选框（本地删除无服务器语义）。非文本/不支持类型不再漏到 Telegram。

**终端**：底部悬浮栏最右新增“终端”Tab（静态图标 `ox_terminal_24`），`OxTerminalActivity`
用持久 `/system/bin/sh` 运行命令，输出等宽字体、深色面板、清空/重启、命令历史；
自动避开 ActionBar 与底部 Tab；当前为应用内 shell（无 root），后续可接 Shizuku/容器。

**图标**：默认图标替换为 NagramXF 的蓝色 N（512 源图生成 adaptive foreground/legacy 全密度）；
删除 Vintage/Aqua/Premium/Turbo/Nox 五个备用图标 alias、`LauncherIconController` 枚举与
相关 PNG/XML 资源，只保留默认图标。

**实测**（debug 包 Ox 模式 + 正式包登录态）：智能体以 stock 聊天行出现；stock 资料页
头像/简介展开、无 Gift/电话；发送照片显示原生气泡并收到“用户发送了图片（640x480）”，
GIF 显示 GIF 气泡与文件名，mp4 文件显示文件气泡（名称/大小）且回复包含工作区路径，
位置显示地图气泡并回复经纬度；回复带引用；删除无复选框；终端 `pwd` 输出
`/data/data/.../files/oxterm`；设置页 Ox/代理/VPN 入口与本地大会员开关正常，
中文副标题为“号码、用户名、简介”“帖子/存档帖子”“文章/待办清单”。

**尚未逐一 E2E**：联系人（系统通讯录为空）、投票/待办（创建 UI 自动输入未命中）、
语音/音乐/贴纸（需媒体选择器逐项操作）走同一 `handleOutgoingSpecial`/`SendMessagesHelper`
路径，代码已接入但未截图验收；真实模型视觉/文件读取需配置后端后验证。

**产物**：`dist/OrionekoX-0.8.18-alpha-arm64-x64.apk`（versionCode 339，sha256 `c22c11e2…`）；
`dist/OrionekoX-0.8.18-debug-arm64-x64.apk`（sha256 `d075beea…`）；补丁 0040。

## 5.25 alpha 0.8.19 —— Operit / Operit2 数据迁移实测（2026-10-25）

**目标**：在模拟器安装 Operit 与 Operit2，配置角色卡（waifu）、MCP、历史记录和
OpenDesign `deepseek-v4.1-flash` API Key，再让 Ox 扫描两者的导出目录并迁移。

**环境与配置**：
- Operit v1.12.2（`com.ai.assistance.operit`，arm64 + ndk translation）已安装；
  完成引导，OpenAI (Generic) → `https://amr-link.open-design.ai/v1`，
  Key/模型 `deepseek-v4.1-flash`，Test Connection 的 Chat/Tool Call 均 Success；
  创建角色卡 Sakura Waifu，发消息生成 1 个会话/2 条消息；新增 MCP `oxmock`。
- Operit2 v2.0.0（`app.operit`，第三方 arm64 构建）已安装，但该构建只打包
  `assets/android-runtime/arm64-v8a/rootfs.tar.gz.bin`，在 x86_64 模拟器上启动即
  报 `FileNotFoundException: android-runtime/x86_64/rootfs.tar.gz.bin`，无法进入 UI。
  改用官方 **Operit2 CLI x86_64** 在宿主机完成等价配置：OpenDesign Provider +
  Key、上下文 1048000（max mode）、角色卡 Yuki Waifu、会话/消息、MCP oxmock，
  `operit2 cli export snapshot` 生成快照并推到
  `Download/Operit2/backup/operit2_snapshot.zip`。
- Operit 的公共导出：`Download/Operit/backup/character_cards/*.json`、
  `Download/Operit/backup/chat/*.json`、`Download/Operit/mcp_plugins/mcp_config.json`。

**实现**：
- 新增 `OxOperitImporter`：扫描 `Download/Operit` 与 `Download/Operit2`；
  解析 Operit 角色卡/聊天 JSON、MCP JSON；解析 Operit2 快照 zip 内的
  `character_cards.preferences.json`、`operit2.sqlite`、`mcp_config.json`、
  `model_configs.preferences.json`（含 endpoint/apiKey/上下文）和 `USER.md`。
- 角色卡映射为 OxAgent（人设合并 characterSetting/description/otherContent/
  advancedPrompt），Provider 用 Operit2 快照里的 OpenDesign Key，模型
  `deepseek-v4.1-flash`，contextLength=1048000，maxTokens=128000；聊天消息
  清除 `<think>`/tool 标记后写入 Ox chat，并 replay 到原生 messages_v2，
  打开 Agent 即可看到迁移后的气泡。
- MCP 按 URL 去重写入 `OxMcpManager`；记忆写入 Agent memory。
- Ox 设置新增“从 Operit / Operit2 导入”入口；未授权时引导到“所有文件访问权限”。
- 顺带修复：流式 tool_call 里 `name:null`/`arguments:null` 被拼成
  `recallnullnull...` 的解析 bug；`recall` 在长期记忆为空时回退搜索聊天历史；
  导入行过长副标题导致的布局重叠；终端 Tab 图标改圆形以匹配其它底栏图标。

**实测结果**：
- 导入汇总：角色卡 2 · 消息 4 · MCP 1 · 记忆 1；聊天列表出现 Yuki Waifu /
  Sakura Waifu 两个 stock DialogCell，打开后能看到 Operit2/Operit 的历史消息。
- 对 Yuki 发送 “What is my favorite color?”，Ox 以 OpenDesign
  `deepseek-v4.1-flash`（API Key 模式、上下文 1048000）回答
  “Your favorite color is green. 🌿 I remember.”，证明迁移的人设/历史/Provider
  全部生效。
- MCP `oxmock` 已进入 Ox 的 MCP 列表（同 URL 重复导入不重复添加）。

**已知限制**：Operit2 第三方 arm64 APK 无法在 x86_64 模拟器启动（缺 x86_64
rootfs）；Ox 的导入按“用户主动导出到 Download”的路径设计，不读取应用私有目录。
官方 Operit2 Android x86_64 构建或 arm64 设备可直接使用同一套导入。

**产物**：`dist/OrionekoX-0.8.19-alpha-arm64-x64.apk`（versionCode 349，sha256 `617f5925…`）；
`dist/OrionekoX-0.8.19-debug-arm64-x64.apk`（sha256 `602c39de…`）；补丁 0041；
迁移说明见 `docs/operit-import.md`。

## 5.26 alpha 0.8.20 —— 终端 Tab 图标定稿 + Settings 双点击音效（2026-10-25）

- 终端 Tab 图标改为 **Ubuntu Circle of Friends 的细线版本**（去掉自绘圆环和
  simple-icons 的实心粗弧）。几何基准：环中心线直径 18dp（与其它图标圆形
  直径对齐）、环内外径差 1.6dp（与 Settings 等图标的线宽对齐）、三个节点直径
  **4.8dp**（由 7.8dp 半径版本按 9/7.8 等比放大，不再出现“环放大、球没放大”）。
  整体外接约 19.6dp，视觉重心居中。
- 点击动效核实后只保留**反色**：Tab 切换时 ViewPager 本身已经对 Tab 做了
  shrink/scale 动画，之前额外加的 imageView 0.86 收缩会造成“收缩两次”，已删除。
  静态图标在选中状态变化时把颜色临时切到相反状态（140ms）再恢复，即
  `updateColors()` 的 selected↔unselected 反色；不再有旋转或放大。
- 修复点击 Settings Tab 出现两次点击音效：`MainTabsLayout.dispatchTouchEvent`
  先给 `ClickHelper` 处理，Settings 是唯一没有加入 `tabsWithIgnoreClick` 的
  Tab，ClickHelper 在 ACTION_UP 播放一次 `ViewUtils.onClick`，GlassTabView
  自身又播放一次；把 Settings 也加入 ignore-click 列表后，只保留 tab 自己的
  点击音效（Chats/Contacts/Profile/Calls/Terminal 原本就是这样）。

**产物**：`dist/OrionekoX-0.8.20-alpha-arm64-x64.apk`（versionCode 359，
sha256 `841a6801…`）；`dist/OrionekoX-0.8.20-debug-arm64-x64.apk`
（sha256 `be458f24…`）；补丁 0042。

## 5.27 alpha 0.8.21 —— 品牌、群组智能体、终端与 MCP 商城（2026-10-25）

**品牌与 Tab**
- 聊天列表顶部不再绘制 Telegram 字标矢量，改为 OrionekoX 文本标题；Stories
  顶部词标替换为按 Noto Sans Bold 生成的 `orioneko_logo_2.xml`。设置页版本
  脚注改为“OrionekoX · 本地优先的智能体客户端 / v0.8.21 (36)”。
- 底部“个人资料”改为“我的”；自定义中文包版本升到 v16。
- 终端 Tab 图标改用官方 Circle of Friends 的完整单路径（simple-icons 几何），
  环和三个节点随 Viewport 等比缩放；删除静态图标的 140ms 临时反色，改为与
  原生 Lottie Tab 相同的“选中即保持选中色”，不再出现闪一下的反色。

**智能体详情与联系人**
- 为每个 agent 预生成并持久化 `UserFull`，资料页稳定显示“本地智能体”副标题
  与“本地智能体 · 由 <model> 模型在你的设备上驱动”简介；联系人列表状态也从
  “不可存取消息”改为“本地智能体”。
- 修正资料页机翻：`AddToGroupOrChannel` → “添加到群组或频道”，
  `ProfileActionsStop`/`BizBotStop` → “停止”，`BotAddToGroupOrChannelInfo`
  改为“这个智能体可以管理一个群组或频道。”
- 联系人页右上角新增智能体入口（bot 图标），替代原聊天列表抽屉里的
  “智能体 / MCP”入口；MCP 保留在 Ox 设置中。

**群组智能体（新增）**
- 新 `OxGroupStore` 以群/频道 id 持久化本地 agent 成员关系。
- `ConnectionsManager` 拦截 `messages.addChatUser` / `channels.inviteToChannel`
  中的合成 agent：本地记录成员、合成 `updateChatParticipants`、回调原始
  request delegate，UI 立即显示成员数变化。
- `messages.createChat` 支持 agent：纯 agent 群创建为本地 `TL_chat`；混合群
  去掉 agent 后发真实建群请求，再本地补成员。
- `SendMessagesHelper` 对含 agent 的群消息触发一次群聊回合，回复以
  `peerChat/peerChannel + 该 agent 的 from_id` 注入原生 ChatActivity；
  `chatInfoDidLoad` 观察者会在每次服务端 full chat 刷新后重新并入本地 agent。
- 实测：把 QA2 加入 “Hh” 群，群标题变为“2 位成员，2 人在线”；发消息后
  出现 QA2 头像与气泡（该 agent 自身 API Key 返回 HTTP 401，故气泡内容为
  错误提示，链路本身已验证）。

**终端**
- 默认工作目录改为 `/storage/emulated/0/Download`，`ls` 可直接看到真实文件；
  保留 app 私有目录回退。
- 三个独立持久 shell（TTY 1/2/3），工具栏 TTY 按钮循环切换，各自保留回滚；
  切换/重启不再重复打印 banner。
- 输入法可用工具栏键盘图标、输入框右侧键盘图标或触摸输出区收起；IME inset
  参与底部 padding，键盘弹出时输入行不再被遮挡。
- `clear`、`restart`、`help` 均可用；新增 Ox 设置 → 终端设置子页：5 套配色
  （Ox/经典/琥珀/浅色/Ubuntu）与 12/14/16/18sp 字号，实时生效。
- 注意：仍是 FIFO 管道而非分配 `/dev/ptmx`，所以 `tty` 命令不会报告真实
  `/dev/pts/N`；TTY 切换是三个独立 shell 会话。

**设置与 MCP 商城**
- 主设置顺序改为：账户 → Ox 设置 → 聊天设置；Ox 设置行位于账户之下。
- 代理与 VPN 合并为“代理与 VPN”入口（新 `ox_network_24` 盾牌+地球图标），
  子页保留两个独立配置入口。
- 移除帮助分组（提问/FAQ/功能/隐私政策）与对应点击分支；底部文案重写。
- 新增 MCP 插件商城：拉取
  `https://static.operit.app/market/v2/lists/type/mcp/updated/page-1.json`，
  展示 38 个 Operit MCP 插件；远程 HTTP 条目可一键安装，本地 stdio 条目显示
  “本地 STDIO”并可复制配置。内置 DeepWiki / Context7 两个可直接使用的远程
  MCP。实测 DeepWiki 安装后刷新出 3 个工具。
- 已知限制：Operit 商城目前全部是本地 stdio（Python/Node/npx）插件；
  OxLinux rootfs 未安装且没有 Python/Node 运行时，因此这些条目只能浏览/复制，
  不能在本版本直接执行。远程 MCP 与“添加 MCP 服务器”不受影响。

**产物**
- `dist/OrionekoX-0.8.21-alpha-arm64-x64.apk`（versionCode 369，sha256
  `ad1941b6…`）；`dist/OrionekoX-0.8.21-debug-arm64-x64.apk`（sha256
  `ea733336…`）；补丁 `0043-alpha-0.8.21-branding-groups-terminal-market.patch`。
- 已用 `tools/push-latest-apk.sh` 安装 release/debug 到模拟器、推送
  Download，并在桌面（抽屉外）重新固定两个图标。

## 5.28 alpha 0.8.22 —— 原生设置复用、统一网络、TTY 标签/智能体绑定、技能库（2026-10-26）

**设置全部改用 Telegram 原生组件**
- Ox 设置、代理与 VPN、终端设置、MCP 服务、技能库不再自绘 `ScrollView + TextView`
  列表，统一改为 `UniversalRecyclerView` + `UniversalAdapter` + `SettingsActivity.SettingCell`，
  原生分组卡片、28dp 渐变图标、标题/副标题/蓝色 value、CheckCell 开关、Radio 列表、
  Shadow 说明行。视觉与主设置页一致。
- 主设置仍保留 账户 → Ox 设置 → 聊天设置 的顺序；帮助组已移除。

**图标清理**
- 去掉不相干/重复图标：代理类型、添加 VPN、添加 MCP、版本、代码仓库、终端设置
  改为无图标或语义图标；VPN 用盾牌、MCP 商城用商店、技能库用文件夹、打开终端用
  Ubuntu 圆环；不再把灯泡/邀请好友等图标硬套到设置项上。

**底部导航对齐**
- `MainTabsLayout` 在主 Tab 模式下把 5 个 Tab 强制等宽（取最宽文本并受总宽限制），
  「聊天 / 联系人 / 设置 / 我的 / 终端」图标和文字全部居中，不再因为「联系人」三字
  导致「我的」偏位。

**夜间模式菜单**
- `SwitchThemeToDay` 机翻「日记」改为「日间模式」；切换后菜单正确显示「日间模式/夜间模式」。
- 聊天列表溢出菜单的代理项改为「代理与 VPN」，点击打开同一个 `OxNetworkActivity`。

**代理与 VPN 合并为一页**
- `OxNetworkActivity` 内联代理开关、服务器/端口、类型、编辑对话框；内联 VPN 开关、
  节点列表、添加 vpn:// / vless:// / hysteria2:// / ss:// 配置；长按节点删除。
- 设置入口和聊天页右上角菜单都进入这一页，不再分两个子页面；旧的 ProxyList/VpnList
  仍保留用于兼容，但主入口不再使用。

**本地会员解锁**
- 本地会员开启时，资料颜色页的转售礼物按钮改为「使用 · 本地会员已解锁」，
  选中后直接把礼物的 `peer_color` 应用到本地 `me.color/profile_color` 并保存，
  不再走服务器购买表单。

**终端 TTY 像浏览器标签页**
- TTY 1/2/3 改为顶部等宽标签，点击直接切换，不再循环。
- 长按标签打开智能体选择器；选中后标签显示「TTY 2 · QA2」并持久化到
  `ox_terminal` prefs；已验证切换、重启后标签仍保留。
- 保留键盘切换、清空、重启、配色/字号设置；终端设置页改为原生 Radio 列表。

**技能库（Operit 生态）**
- 新增 `OxSkillStore`：扫描 `Download/Operit/skills` 与 `Download/Operit2/skills`
  下的 `SKILL.md`，解析 frontmatter 的 name/description；未授权所有文件访问时
  技能页显示原生「授予所有文件访问权限」入口。
- 新增 `list_skills` / `read_skill` 内置工具，智能体可读取 Operit 技能文档；
  Ox 设置 → 技能库可只读浏览，点按查看全文。
- `docs/operit-gap-analysis.md` 增补 0.8.21 深度对照：已补齐原生设置、统一网络、
  TTY 智能体绑定、技能库、MCP 商城、群聊智能体、本地会员外观；仍缺 UI 自动化、
  可执行 ToolPkg、stdio MCP 运行时、工作流、浏览器/OCR/语音。

**QA 发现并修复**
- 夜间模式菜单「日记」→「日间模式」。
- 「删除和块」→「删除并屏蔽」。
- 「开除」→「放弃」；「更改配置颜色」→「更改个人资料颜色」；
  「复制链接到配置文件」→「复制个人链接」。
- 全局修正 TON 机翻「东顿」、购买按钮「买吧」、加密货币/低手续费等用语。
- OxLinux 行副标题原本误用 Ox 设置简介，改为「可选，为 Agent 准备一个离线 Ubuntu 环境」。
- 终端 TTY 切换 banner 重复、presented 终端顶部双倍间距、键盘遮挡输入行均已修复。
- 全量点击/长按/滑动走查主 Tab、聊天列表与会话菜单、联系人、我的资料、设置、Ox 设置、
  MCP/商城、技能库、终端、代理与 VPN；未出现 FATAL/ANR。

**产物**
- `dist/OrionekoX-0.8.22-alpha-arm64-x64.apk`（versionCode 379，sha256
  `3c90c82d…`）；`dist/OrionekoX-0.8.22-debug-arm64-x64.apk`（sha256
  `a9d4f518…`）；补丁 `0044-alpha-0.8.22-native-settings-qa.patch`。
- 已用 `tools/push-latest-apk.sh` 安装 release/debug 并重新固定桌面图标。

### 0.8.22 实测补充（2026-10-26 00:20–00:26）

- **真实模型聊天**：重新从 Operit/Operit2 导入 2 张角色卡（Yuki/Sakura）、4 条消息、1 条记忆；
  Yuki Waifu 使用导入的 OpenDesign `deepseek-v4.1-flash` 真实 API Key，提问
  “What is my favorite color?” 后自动调用 `remember`/`recall` 工具，并回复
  “Your favorite color is green. 🌿”。工具气泡与最终回复都在原生 ChatActivity 中正常显示。
- **实际容器**：`shell_in_container` 触发后，Ubuntu base rootfs 已成功下载并解压，
  `container_status` 返回 `state=ready, rootfs=true`；但 proot 启动 guest loader 时报
  “foreign binary / qemu not specified / loader was not found”，即 targetSdk 36 的
  W^X/SELinux 限制，和 0.8.0 PoC 的结论一致。需要 targetSdk 28 伴生宿主 APK 或
  Shizuku/root 通道；当前版本容器只能完成安装/状态检查，不能执行 guest 命令。
- **TTY 绑定**：长按 TTY 2 分配给 QA2，标签显示 `TTY 2 · QA2`；切到 TTY 2 后 banner 正确，
  分配关系持久化。
- **MCP 商城**：DeepWiki 远程条目安装后刷新出 3 个工具；Operit 目录 38 条本地 stdio 条目
  正常展示并标注不支持直接执行。
- **图标清理**：按语义重新选取，去掉代理类型/添加 VPN/添加 MCP/版本/仓库/终端设置等
  不相干或重复图标；设置页不再复用灯泡、邀请好友等图标。

## 5.29 alpha 0.8.24 —— 原生工具页、agent 建群实测、群选择器与机翻修复（2026-10-26）

- `OxToolsActivity` 也改为原生 `UniversalRecyclerView` + `SettingCell`：Android 权限
  分组、Agent 选择行（bot 图标）、每个工具的原生标题/描述/策略值，点击循环
  Auto/Ask/Deny 后列表原位刷新。
- 实测“新建群组”只选一个 agent：`createLocalGroup id=1800000000 agents=1`，进入群聊
  显示“2 位成员，2 人在线”，群内消息触发 agent 回合（QA2 自身 Key 返回 401，链路正常）。
- 修复群选择器/建群页：
  - 成员状态「机器人」→「本地智能体」（`GroupCreateUserCell`）。
  - 默认群名机翻「BlanCat(单位:千美元)🤖 QA2」→「BlanCat 和 🤖 QA2」；
    `GroupCreateMembersTwo/Three/Four/Five` 全部改为中文枚举。
  - 「已选 1 / 200000」：`MembersCountSelected`、`Of`、`OfS` 的「编号」机翻修正。
- 批量修复「信件」→「消息」、「编号」相关机翻、自动删除文案、`AutoDelete1Day`
  「1天(以千美元计)」→「1 天」、`AutoDeleteAfter1Week` 乱码等。
- 新增工作区 `.zcode/config.json`：本地自动压缩 `bufferTokens=262144`，按 1M 上下文
  约 72% 触发（50–75% 区间），对新会话生效。
- 已创建每 5 分钟自动续作任务：检查构建/QA 是否在跑，空闲则继续全量点击、真实模型、
  Operit 差距补齐，并更新报告/memory/提交。

**产物**：`dist/OrionekoX-0.8.24-alpha-arm64-x64.apk`（versionCode 399，sha256
`5b90cd9f…`）；debug `456a3b80…`；补丁 `0045-alpha-0.8.24-native-tools-group-fixes.patch`；
release/debug 已安装并重新固定桌面图标。

## 5.30 alpha 0.8.25 —— Android UI 自动化工具（Operit 最核心缺口）（2026-10-26）

- `OxAccessibilityService` 从空壳升级为真正的自动化桥：
  - 服务配置开启 `canPerformGestures`、`canTakeScreenshot`、`flagReportViewIds`、
    `flagRetrieveInteractiveWindows`。
  - `ui_dump`：递归读取当前窗口的无障碍节点树，输出 class/text/desc/clickable/
    scrollable/editable/bounds，最多 240 个节点。
  - `ui_tap` / `ui_swipe`：用 `GestureDescription` 派发真实点击/滑动（像素坐标，
    滑动时长 50–2000ms）。
  - `ui_screenshot`：Android 11+ `takeScreenshot`，把 PNG 写到
    `files/oxauto/screen-<ts>.png` 并返回绝对路径。
  - `ui_key`：执行 back/home/recents/notifications/quick_settings/power 全局动作。
- 五个工具都进入 `OxBuiltinTools`，默认策略 **ASK**，在 `工具与权限` 页以原生
  SettingCell 显示；描述已补中英文。
- 实测（真实 OpenDesign 模型，Yuki Waifu）：
  - `ui_dump` 返回当前聊天页标题、正在输入、输入框占位、返回/附件/表情的 content-desc。
  - `ui_screenshot` 生成 436 KB PNG；
  - `ui_key=home` 把前台切到 launcher；
  - `ui_tap(540,1458)` 成功派发手势。
- 构建坑：新英文描述里的 `screen's` 撇号未转义，aapt2 报 “Invalid unicode escape
  sequence”；改为 “the accessibility tree of the active screen” 后通过。
- `docs/operit-gap-analysis.md` 已把 UI automation 从 high-value gap 改为 done v1；
  下一步是 click-by-text、scroll-until-found、节点缓存和 ADB/root 任意按键。

**产物**：`dist/OrionekoX-0.8.25-alpha-arm64-x64.apk`（versionCode 409，sha256
`b9aea0e1…`）；debug `78716d30…`；补丁 `0046-alpha-0.8.25-ui-automation.patch`；
release/debug 已安装并重新固定桌面图标。

## 5.31 alpha 0.8.26 —— ui_tap_text 按文字点击 + 窗口回退（2026-10-26）

- 新增 `ui_tap_text` 工具：按可见文字/content-desc 查找控件并执行
  `ACTION_CLICK`；先做大小写不敏感精确匹配，再做包含匹配；只接受能向上找到
  可点击父节点的匹配，避免点到不可点击的文本。
- `OxAccessibilityService.activeRoot()`：`getRootInActiveWindow()` 在权限弹窗/
  输入法动画期间可能返回 null，回退到 `getWindows()` 里最新的
  `TYPE_APPLICATION` 窗口，显著降低“no active window”。
- 实测：Yuki Waifu（真实 OpenDesign 模型）调用 `ui_tap_text`，成功点击聊天页
  顶部「🤖 Yuki Waifu」，原生 ProfileActivity 打开并显示“本地智能体”。
- 说明：重装 APK 后 Android 会清掉无障碍服务授权，需要重新在系统设置里开启；
  这是系统行为，已在测试中确认。
- `ui_tap_text` 已加入 `工具与权限` 的原生工具列表，默认 Ask。

**产物**：`dist/OrionekoX-0.8.26-alpha-arm64-x64.apk`（versionCode 419，sha256
`75316186…`）；debug `0d25f50a…`；补丁 `0047-alpha-0.8.26-ui-tap-text.patch`；
release/debug 已安装并重新固定桌面图标。

## 5.32 alpha 0.8.27 —— 滚动查找文字 / 滚动后点击（2026-10-26）

- 新增 `ui_find_text`：在当前可滚动容器里按文字查找，未命中就对第一个
  `isScrollable` 节点执行 `ACTION_SCROLL_FORWARD`，最多 12 次（默认 5），
  返回 label/bounds/clickable，不点击。
- 新增 `ui_scroll_tap_text`：同样的滚动查找，但命中后沿父链找到可点击节点并
  执行 `ACTION_CLICK`。
- 匹配规则与 `ui_tap_text` 一致：先精确（忽略大小写）再包含；滚动后重新取
  `activeRoot()`，避免节点失效。
- 无障碍授权在每次 `adb install -r` 后会被系统清掉；实测中先启用服务、
  再被 push 脚本重装覆盖，导致 `ui_find_text` 首次返回 “accessibility service
  is not enabled”。这是系统行为，不是工具逻辑错误；发布后需重新在系统设置里
  开启一次。
- 工具已加入原生 `工具与权限` 列表，默认 Ask；本轮完成 release/debug 构建、
  安装、桌面固定与提交。
- 现场实测补充：`ui_find_text` 成功找到聊天页顶部「Yuki Waifu」，返回
  bounds `[137,0][943,275]`、`clickable=false`（该节点本身不可点击，但
  `ui_tap_text` 会沿父链点击）。`ui_scroll_tap_text` 的滚动分支仍需下一轮
  在长列表上做一次完整 E2E。

**产物**：`dist/OrionekoX-0.8.27-alpha-arm64-x64.apk`（versionCode 429，sha256
`a92654bd…`）；debug `10768a9a…`；补丁 `0048-alpha-0.8.27-scroll-automation.patch`。

## 5.33 alpha 0.8.28 —— 滚动点击优先可点击节点（2026-10-26）

- `ui_scroll_tap_text` 修复一个实际匹配缺陷：旧逻辑找到第一个包含查询串的节点就返回，
  若该节点是消息气泡等不可点击文本，会直接报 “found but click failed”，不再继续
  搜索/滚动。新逻辑收集所有匹配，点击时逐个尝试可点击父节点；不可点击则继续滚动，
  直到找到可点击目标或达到 max_scrolls。
- `ui_find_text` 现场已验证：在聊天页找到「Yuki Waifu」header，bounds
  `[137,0][943,275]`，0 次滚动。
- `ui_scroll_tap_text` 的完整滚动 E2E 仍受状态影响：测试时 `ui_key back` 先只关闭了
  键盘，页面仍在聊天页，导致 “not found and no scrollable container”；这不是滚动
  实现错误，下一轮会在明确的聊天列表状态重测。
- 0.8.28 release/debug 已构建、安装、桌面固定。

**产物**：`dist/OrionekoX-0.8.28-alpha-arm64-x64.apk`（versionCode 439，sha256
`61d45212…`）；debug `0ea30f79…`；补丁在提交后生成。

## 5.34 alpha 0.8.29 —— 滚动方向参数 / 反向查找（2026-10-26）

- `ui_find_text` 与 `ui_scroll_tap_text` 新增可选 `direction=backward`，用于向上
  滚动查找（默认 forward）；滚动动作相应改为 `ACTION_SCROLL_BACKWARD`。
- 工具 schema 增加 `direction` 字段，中英文描述已更新；默认仍为向下。
- E2E 测试方法遇到一个自指问题：把查询词写进聊天消息后，当前刚发送的消息本身
  就包含该查询串，工具会先命中这条可见消息，无法触发滚动。下一轮应改为先
  `ui_swipe` 把当前消息移出屏幕，或用固定的 content-desc（如“返回”）作为目标。
- 本轮 release/debug 构建、安装、推送完成；首次 `push-latest-apk.sh` 更新
  launcher DB 时遇到 `database is locked (5)`，重试后成功固定图标。

**产物**：`dist/OrionekoX-0.8.29-alpha-arm64-x64.apk`（versionCode 449，sha256
`48ef1bf3…`）；debug `28a881a9…`；补丁在提交后生成。

## 5.35 alpha 0.8.30 —— Agent 编辑器/列表文案收口（2026-10-26）

- Agent 编辑器标题从「智能体资料」改为「智能体设置」，与“编辑设置”语义一致；
  打开后仍保留头像、名称、备注、模型服务、接口地址、API Key、模型、测试连接、
  高级参数、自定义请求头、AGENTS.md、删除智能体等完整字段。
- Agent 列表右上角徽标从英文「BOT」改为本地化「本地」/「LOCAL」（新增
  `OxAgentBadgeLocal`，中文包 v26）。
- 实测：列表徽标显示「本地」，编辑器标题显示「智能体设置」。
- 0.8.30 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.30-alpha-arm64-x64.apk`（versionCode 459，sha256
`d0192721…`）；debug `8fe857a0…`；补丁在提交后生成。

## 5.36 alpha 0.8.31 —— Agent 工作区浏览器（2026-10-26）

- Ox 设置 → 智能体分组新增「工作区」：原生 `UniversalRecyclerView` + `SettingCell`，
  顶部可切换 Agent，递归列出该 Agent 的 `files/oxworkspace/<id>` 文件，显示相对
  路径、大小、修改时间。
- 点按文本文件打开预览（最多 20KB，支持 md/json/txt/js/py 等）；非文本/超大文件
  显示路径和大小。长按文件弹原生删除确认，删除后列表即时刷新。
- 新增语义化 `ox_file_24` 文档图标；文件夹/技能仍用文件夹图标，避免复用不相干图标。
- 实测（Yuki Waifu 工作区）：创建 `hello.md`、`data.json`、`notes/note.txt` 后列表
  正确显示；`hello.md` 预览显示 `# Hello from Yuki`；长按删除 `data.json` 后列表与
  文件系统同步只剩 `hello.md`、`notes/`。
- 0.8.31 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.31-alpha-arm64-x64.apk`（versionCode 469，sha256
`3deaf56e…`）；debug `3a2f57b1…`；补丁在提交后生成。

## 5.37 alpha 0.8.32 —— 工作区新建/编辑文本文件（2026-10-26）

- Agent 工作区新增「新建文本文件」：输入相对路径（如 `notes/todo.md`）和初始内容，
  保存到 `files/oxworkspace/<agentId>`，自动创建父目录；路径校验拒绝 `..` 和
  绝对路径。
- 文本预览对话框新增「编辑」「复制路径」「关闭」：编辑用等宽多行输入，保存后
  立即回写并刷新列表；复制路径写入系统剪贴板。
- 实测：工作区列表显示 `hello.md`、`notes/note.txt` 等；新建文件对话框可创建
  文件（测试输入时把内容误敲进文件名字段，生成了 `notes/ui.mdcreated from UI`，
  说明保存链路正常，纯属 adb 输入坐标问题）；长按删除后列表和文件系统同步；
  `hello.md` 预览显示内容，编辑对话框正确带出原文并可保存。
- 0.8.32 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.32-alpha-arm64-x64.apk`（versionCode 479，sha256
`7c387700…`）；debug `6b82fa63…`；补丁在提交后生成。

## 5.38 alpha 0.8.33 —— Agent 记忆查看/编辑（2026-10-26）

- Agent 长按菜单新增「记忆」：打开 `OxAgentMemoryActivity`，等宽多行编辑
  `files/oxagents/<id>/memory.txt`，保存回写、清空需确认。
- `OxAgentStore.saveMemory` 支持整文件覆盖；记忆仍由 `remember` 工具按行追加，
  智能体每轮把该文件注入 system prompt。
- 实测：Yuki Waifu 记忆页正确显示
  `2026-10-26 00:20 # USER / 00:22 User's favorite color is green.`；
  保存 checkmark 可退出并回写（adb 自动输入未追加成功，属于测试输入问题）。
- 0.8.33 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.33-alpha-arm64-x64.apk`（versionCode 489，sha256
`32b4dc2a…`）；debug `68f73e66…`；补丁在提交后生成。

## 5.39 alpha 0.8.35 —— 旧本地群名机翻修复（2026-10-26）

- `OxGroupStore.chatIds()` 枚举本地 agent 群；`syncAgents` 启动时以及 2.5s/7s
  延迟调用 `sanitizeLocalGroupTitles()`，把旧群名里的「(单位:千美元)」替换为「、」。
- 实测：DB 中群名从 `BlanCat(单位:千美元)🤖 QA2` 修正为 `blancat、🤖 qa2`；
  重启后聊天列表显示「BlanCat、 🤖 QA2」。
- 当前会话的 DialogCell 不会立即刷新，需要下次启动或重新加载列表；DB 已持久化。
- 0.8.35 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.35-alpha-arm64-x64.apk`（versionCode 509，sha256
`a7ba0d2a…`）；debug `907c7148…`；补丁在提交后生成。

## 5.40 alpha 0.8.36–0.8.37 —— Agent 技能选择与提示词注入（2026-10-26）

- Agent 编辑器新增「技能」区块：列出 `Download/Operit/skills` / `Operit2/skills`
  的 SKILL.md 包，多选复选框；保存到 `agent.json` 的 `skill_ids`。
- `baseMessages` / `groupMessages` 会把已启用技能的正文（每项最多 4000 字符，
  总计 8000 字符）注入 system prompt，私有聊天和群聊都生效。
- 修复 `buildFromFields()` 丢弃 `skillIds` 的 bug：保存其它字段时不会再把技能清空。
- E2E（真实 OpenDesign `deepseek-v4.1-flash`）：给 Yuki 启用「示例技能」后提问
  “What does the demo skill say? Answer with the exact sentence.”，Yuki 回答
  「这是 OrionekoX 的技能库测试文件。」，证明技能注入生效。
- 0.8.36/0.8.37 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.37-alpha-arm64-x64.apk`（versionCode 529，sha256
`ba27ff41…`）；debug `2a5699ac…`；补丁在提交后生成。

## 5.41 alpha 0.8.38 —— 屏幕 OCR 工具（2026-10-26）

- 新增 `ui_ocr`：先调用无障碍 `takeScreenshot` 截屏，再用 Google Play Services
  `TextRecognizer` 做端上文字识别，返回识别文本块及大致 bounds；结果上限 8000 字符。
- 工具默认 Ask，进入原生「工具与权限」列表，中文描述已补。
- 实测：连续两次调用都返回 `error: screenshot failed (1)`（Android 14 模拟器上
  `AccessibilityService.takeScreenshot` 内部错误），OCR 主体代码未真正跑到；
  这是截图通道问题，不是 TextRecognizer 逻辑。需在真机或换模拟器镜像复测。
- 0.8.38 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.38-alpha-arm64-x64.apk`（versionCode 539，sha256
`32656fc0…`）；debug `698584b1…`；补丁在提交后生成。

## 5.42 alpha 0.8.39 —— Agent 导出包含记忆（2026-10-26）

- `.oxagent` 导出 JSON 新增 `memory` 字段（最多 20 万字符），导入时写入新 Agent 的
  `memory.txt`；技能 `skill_ids` 原本已在 agent.json 中一并导出。
- 这样导出/导入一个 Agent 会保留人设、模型配置、工具策略、记忆和已启用技能；
  API Key 仍按既有安全策略置空。
- 本轮未做文件选择器级 E2E（导出/导入都走系统 SAF），代码路径编译通过；
  下一轮可在模拟器 Downloads 里验证往返。
- 0.8.39 release/debug 已构建、安装、推送、固定桌面图标。

**产物**：`dist/OrionekoX-0.8.39-alpha-arm64-x64.apk`（versionCode 549，sha256
`c93873df…`）；debug `a2cd5878…`；补丁在提交后生成。

## 5.43 alpha 0.8.39 —— OCR 诊断 + Agent 导出实测（2026-10-26 03:15–03:23）

- **截图 API 结论**：在模拟器连续调用截图/手势后，`takeScreenshot` 开始返回
  `error: screenshot failed (1)`，uiautomator 也出现 “null root node”；完整重启
  AVD 后截图恢复正常。说明这是模拟器渲染/无障碍子系统退化，不是应用代码或
  FLAG_SECURE。重启后 `ui_ocr` 不再报截图错误，而是报
  “OCR model is not available yet (Google Play Services may still be downloading it)”——
  Play Services OCR 模型未随镜像安装，需真机或带 Play Store 的镜像下载模型。
- **Agent 导出实测**：长按 Yuki → 导出 `.oxagent` → Downloads，
  `Yuki_Waifu.oxagent` 799 字节；JSON 含 `memory`（75 字符）、`skill_ids=['demo']`、
  `api_key` 已置空，符合导出策略。
- 测试中把本地会员开关误关，已重新打开；当前为开启状态。
- 本轮未改代码，产物仍为 0.8.39；导入往返 E2E 留待下一轮。

## 5.44 alpha 0.8.39 —— .oxagent 导入往返实测（2026-10-26 03:24–03:27）

- 在 Downloads 选中 `Yuki_Waifu.oxagent` 完成导入：列表出现第二个 Yuki Waifu。
- 检查导入生成的 `files/oxagents/impeee365/agent.json`：
  - `name = Yuki Waifu`
  - `skill_ids = ['demo']`
  - `api_key = ''`
  - `memory.txt` 恢复为 75 字符（含 `User's favorite color is green.`）
- 导入后长按删除重复 Agent，列表恢复为 1 个 Yuki，`impeee365` 目录已清理。
- 结论：`.oxagent` 导出/导入往返保留人设、模型、技能与记忆，API Key 按策略不导出。
- 本轮无代码改动，版本仍为 0.8.39。

## 5.45 alpha 0.8.40 —— 工作流 / 定时任务（2026-10-26 03:28–04:20）

- **入口**：Ox 设置 → 智能体 → 工作流，使用原生 `UniversalRecyclerView` +
  `SettingsActivity.SettingCell`，语义图标为时钟；页面含空状态、说明、点按/长按提示。
- **模型**：`OxWorkflow`（名称、提示词、智能体、间隔、启用、上次/下次时间、上次结果），
  `OxWorkflowStore` 持久化到 `shared_prefs/ox_workflows.xml`。
- **调度**：`OxWorkflowScheduler` 用 AlarmManager 为每个工作流排一个
  `RUN_WORKFLOW` 闹钟；有精确闹钟权限时 `setExactAndAllowWhileIdle`，否则
  `setAndAllowWhileIdle`，并在页面提示可能延迟。BOOT_COMPLETED 和进程启动时重排，
  逾期任务 30 秒后补跑。
- **执行**：`OxWorkflowService` 前台服务（dataSync）串行处理队列：注入提示词到智能体会话
  （复用 `OxAgentsBridge.sendUserPrompt`），轮询会话历史等待回复落库，完成后发
  `ox_workflows` 渠道的结果通知；通知点击回到应用。队列串行是为了避开 Android
  单 Service 实例导致的字段串话。
- **实测**：
  - 手动运行（Echo Assistant）：提示词和回声回复出现在会话内，结果通知正文为回声文本。
  - 同一工作流切到真实模型 Yuki Waifu：`finishTurn len=11`，通知正文为模型返回的
    `workflow ok`，行内“上次”同步更新（无需离开页面）。
  - 暂停会取消闹钟（dumpsys alarm 无下一次 RUN_WORKFLOW），恢复后重新排期。
  - 手动类型（间隔=0）不出现在暂停菜单里，可立即运行；删除后列表与存储同步清理。
  - 缺名称/提示词时保存会提示“请先填写名称、提示词并选择智能体”，对话框不关闭。
  - 深色模式渲染正常；未授权精确闹钟时显示延迟提示。
  - **冷启动实测**：04:07 root 杀掉进程（pid 8108），04:09:15 闹钟拉起新进程 8892，
    FGS 被系统放行，真实模型 9 秒后完成，结果通知出现，下一次闹钟自动排到 04:24，
    服务随后退出（无残留前台服务）。
- **限制**：只支持间隔（15 分钟 / 每小时 / 6 小时 / 每天 / 手动），没有固定时刻和多步
  工作流图；用户“强行停止”应用后系统会清掉闹钟，重新打开应用或重启后会补排。

**产物**：`dist/OrionekoX-0.8.40-alpha-arm64-x64.apk`（versionCode 559，sha256
`e7a590ee…`）；debug `dist/OrionekoX-0.8.40-debug-arm64-x64.apk`（sha256 `cba6f05c…`）。

## 5.46 alpha 0.8.41 —— 工作流固定时刻（2026-10-26 04:20–04:30）

- 工作流的“运行计划”新增 **每天指定时间**：复用 Telegram 原生
  `AlertsCreator.createTimePickerDialog`（24 小时制 BottomSheet、时/分滚轮、“选择”按钮），
  不再只有相对间隔。
- 模型新增 `daily_minute`（0–1439，-1 表示不用）；调度器 `computeNextRun` 用本地
  Calendar 计算“今天该时刻若已过则明天”，跨 DST 由 Calendar 处理；运行完成后排下一次。
- 计划列表区分“每 24 小时”（相对间隔）与“每天 09:00”（固定时刻），避免歧义；
  行内同时显示“下次 09:00”和“上次：…”。
- 实测（0.8.41）：
  - 新建 Daily test（Echo Assistant）→ 计划选“每天指定时间…”→ 原生时间滚轮默认 09:00 →
    “选择”→ 保存；行显示“每天 09:00 · 下次 09:00”，状态“已启用”。
  - `dumpsys alarm` 的 RUN_WORKFLOW `origWhen=2026-10-26 09:00:00.000`，说明排的是
    墙上时钟 09:00 而不是“当前时间 + 24h”。
  - 长按立即运行：Echo 回复进入会话，通知标题“工作流完成：Daily test”，行内“上次”
    即时刷新。
  - 删除 Daily test 后无下一次 RUN_WORKFLOW 闹钟；测试残留仅保留已暂停的 Morning test。
- **限制**：仍没有多步工作流图/条件分支；固定时刻依赖系统闹钟，未授权精确闹钟时
  可能延迟一个窗口；用户强行停止应用后闹钟被清，重开或重启补排。

**产物**：`dist/OrionekoX-0.8.41-alpha-arm64-x64.apk`（versionCode 569，sha256
`8afd3533…`）；debug `dist/OrionekoX-0.8.41-debug-arm64-x64.apk`（sha256 `b21522d2…`）。

## 5.47 alpha 0.8.42 —— 工作流运行记录（2026-10-26 04:30–04:36）

- 每个工作流的 JSON 新增 `runs` 数组（最多 10 条，字段 `ts/ok/text`），由
  `OxWorkflowService.finishRun` 在每次运行结束时写入；成功、失败、超时都会记录。
- 长按工作流菜单新增 **运行记录**：按时间倒序列出
  “MM-dd HH:mm · 成功/失败 · 摘要”，点按某条打开完整输出；无记录时提示
  “还没有运行记录。”
- 运行记录是独立日志，不会因切换智能体/修改提示词而清空；行内“上次”仍只显示
  最近一次结果。
- 实测（0.8.42）：
  - 首次打开运行记录显示空态提示。
  - 立即运行 Morning test（Yuki，真实模型）后，记录出现
    `10-26 04:34 · 成功 · workflow ok`，点按后完整文本对话框正常。
  - 工作流保持暂停，`dumpsys alarm` 无下一次 RUN_WORKFLOW；无 FATAL/ANR。
- 说明：仍然只保留最近 10 次，避免 prefs 无限增长；历史不包含完整请求参数或工具轨迹。

**产物**：`dist/OrionekoX-0.8.42-alpha-arm64-x64.apk`（versionCode 579，sha256
`00bc0243…`）；debug `dist/OrionekoX-0.8.42-debug-arm64-x64.apk`（sha256 `175c9092…`）。

## 5.48 alpha 0.8.43 —— .oxagent 导出包含工作区（2026-10-26 04:37–04:47）

- `.oxagent` 导出升级：有工作区文件的智能体写成 **zip**，包含 `agent.json` 和
  `workspace/...`；没有工作区文件时仍写旧的纯 JSON，保持向后兼容。
- 导入同时接受 zip 与旧 JSON：zip 先解析 `agent.json`，创建/重命名 Agent 后再恢复
  工作区文件（自动建父目录、路径越界拒绝）。上限：200 个文件、单文件 8 MB、
  总计 32 MB；导入文件本身上限 64 MB。
- 导出/导入提示区分是否带工作区：
  “已导出 …（含 2 个工作区文件，不含 API 密钥）” / “已导入 …（含 2 个工作区文件）”。
- 实测（0.8.43）：
  - Yuki Waifu 工作区有 `hello.md` 与 `notes/note.txt`；导出得到 946 B zip，
    `unzip -l` 显示 `agent.json`、`workspace/hello.md`、`workspace/notes/note.txt`；
    `agent.json` 内 `api_key=''`、`memory` 75 字符、`skill_ids=['demo']`。
  - 通过 SAF 导入该 zip，生成 `impc733b3`：`agent.json` 字段正确、`memory.txt` 恢复、
    两个工作区文件（含嵌套目录）内容一致。
  - 再导入旧的 799 B 纯 JSON `Yuki_Waifu.oxagent` 也成功，证明向后兼容。
  - 两个重复 Agent 通过长按删除，遗留的 `files/oxworkspace/impc733b3` 手动清理；
    聊天列表无重复 Yuki 行；原始 Yuki 工作区未受影响；无 FATAL/ANR。
- 说明：zip 导出文件名冲突时系统会自动生成 `Yuki_Waifu (1).oxagent`，不是应用行为。

**产物**：`dist/OrionekoX-0.8.43-alpha-arm64-x64.apk`（versionCode 589，sha256
`a11378b1…`）；debug `dist/OrionekoX-0.8.43-debug-arm64-x64.apk`（sha256 `4bb40500…`）。

## 5.49 alpha 0.8.44 —— Operit 重复导入幂等（2026-10-26 04:48–05:02）

**修复**
- 根因：`OxOperitImporter.createAgent` 的 id 把 `System.currentTimeMillis()` 也算进哈希，
  每导入一次就生成新 id，于是重复导入会复制出同名 Agent；记忆又用 `appendMemory`
  无条件追加，重复导入会让 `# USER` 行越积越多。
- 现在按 `op_<source>_` 前缀 + 精确名称查找已有 Agent，命中就复用原对象（保留
  `tg_id`、置顶、备注、头像、技能），只更新人设与 provider；新 Agent 的 id 改为
  source+名称的确定性哈希。
- 记忆导入先检查现有记忆是否已包含同一段文本，包含则跳过。
- `OxAgentStore.deleteAgent` 改为递归删除，避免带 `media/` 子目录时留下半删目录。

**验证**
- 连续两次执行 Operit 导入：Agent 目录数保持 8（含 1 个历史遗留目录）、列表仍为
  7 个 Agent；Yuki/Sakura 的 `agent.json` mtime 更新说明是原地更新；Yuki 的
  `memory.txt` 保持 75 字符，技能 `['demo']` 与 API Key 均保留；无 FATAL/ANR。

**事故与恢复（必须记录）**
- 本轮误触了“从 Operit 导入”，生成了两个重复 Agent。清理时按行位置长按删除，
  但列表里同名行相邻，实际删掉的是**原始 Yuki 和 Sakura**，而不是两个新副本。
  `onAgentDeleted` 同时移除了它们的本地会话记录，最近的聊天历史无法恢复。
- 已恢复的部分：把导入副本作为存续 Agent（配置、接口地址、API Key 完整）；
  Yuki 的记忆从先前 `.oxagent` 导出恢复（75 字符），技能恢复为 `demo`，原工作区
  `hello.md` / `notes/note.txt` 复制到新 Agent；Sakura 以导入副本存续。
- 不可恢复：原始两个 Agent 的最近聊天历史、原始 tgId 的会话、Sakura 的原记忆
  （没有备份）。原始 Yuki 的半删目录（含 media）保留未动，未再执行任何删除。
- 恢复后实测：在 Yuki 会话问 “What is my favorite color?”，真实模型返回
  “Green 🌿 — it's saved in my memory.”，证明 API Key、记忆与技能仍然可用。
- 防复发：同名 Agent 不能按行号判断；必须用文件 mtime/id 或打开编辑器确认。
  下轮 QA 删除任何 Agent 前先 `ls -lt files/oxagents` 对照。

**产物**：`dist/OrionekoX-0.8.44-alpha-arm64-x64.apk`（versionCode 599，sha256
`2e15ebde…`）；debug `dist/OrionekoX-0.8.44-debug-arm64-x64.apk`（sha256 `f113d0e9…`）。

## 5.50 alpha 0.8.48 —— 长列表滚动点击 E2E + 三个修复（2026-10-26 05:05–05:32）

**目标**：让真实模型调用 `ui_scroll_tap_text`，在 Ox 设置→工具与权限的长列表里滚动找到
并点击 `ui_ocr`（目标初始在首屏之外）。

**发现的三个真实缺陷**
1. **最后一次滚动不搜索**：循环在每轮搜索后滚动，最后一轮滚动完成后直接退出，
   目标恰好在最后一屏出现时必然漏掉。改为最后一轮只搜索、不再滚动。
2. **节点上限截断长列表**：`collect` 上限 400 节点，Telegram 长设置列表在到达目标行
   之前就被截断。上限提到 2000，并先用框架索引
   `findAccessibilityNodeInfosByText` 取匹配，再合并手动 exact/contains 匹配。
3. **Telegram 列表行没有 clickable 祖先**：`UniversalRecyclerView` 由 RecyclerListView
   统一处理点击，行 View 本身不可点击，`ACTION_CLICK` 永远失败。命中文本后若找不到
   可点击祖先，改为对文本中心做坐标点击（仅当完整 bounds 在屏幕内）。

**实测**
- 用 Yuki 的“QA scroll”工作流在后台运行，前台稳定停在工具页，主机通过
  `am start-foreground-service` 直接拉起工作流服务（避开页面切换竞态）。
- 日志：`tool ui_scroll_tap_text done: tapped "ui_ocr" after 2 scroll(s)
  bounds=[86,1977][743,2034] (coordinate)`。
- `ui_ocr` 行的策略值从“默认 · 询问”变为“自动”，证明坐标点击作用在真实行上；
  测试后把 ui_ocr 策略恢复为默认。
- 同时验证了诊断日志 `OxA11y step/rootPkg/nodes/sample/scrollable/scrollAction`，
  保留在代码里便于后续排障。
- 清理：两个测试工作流已删除、`ox_workflows` 为空、无 RUN_WORKFLOW 闹钟；
  Yuki 的 `ui_scroll_tap_text` 临时 Auto 策略已从 agent.json 移除；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.48-alpha-arm64-x64.apk`（versionCode 639，sha256
`549645c1…`）；debug `dist/OrionekoX-0.8.48-debug-arm64-x64.apk`（sha256 `bfc6d237…`）。
0.8.45–0.8.47 为本轮中间构建，均已覆盖。

## 5.51 alpha 0.8.50 —— UI 自动化长按（2026-10-26 05:33–05:49）

**新增**
- `ui_long_press(x, y, duration_ms)`：坐标长按，默认 900 ms（400–3000 限制），
  走无障碍 `dispatchGesture`。
- `ui_long_press_text(text, duration_ms)`：查找可见文本/内容描述并长按中心；
  先尝试 `ACTION_LONG_CLICK`，再退回坐标长按；带屏幕内 bounds 校验。
- 工具页中文描述、权限三态沿用现有机制，默认 Ask。

**实测**
- 坐标长按：工作流后台调用 `ui_long_press(500,835,900)`，聊天列表的 Echo 行
  进入多选模式（顶部出现 X / 计数 1 / 置顶 / 静音 / 删除），日志
  `tool ui_long_press done: long-pressed 500,835`。
- 按文字长按：在智能体管理列表调用 `ui_long_press_text("Echo Assistant")`，
  命中 `Rect(196,1180-954,1237)` 并弹出长按菜单（置顶/编辑设置/记忆/导出 .oxagent）。
- 权限弹窗关闭瞬间执行手势会被吞（首次坐标长按未生效）；将测试策略临时设为 Auto
  后页面稳定，手势正常。真实使用中 Ask 弹窗后建议模型稍等再操作，或把该工具设为 Auto。
- **限制**：Telegram 聊天列表的 `DialogCell` 是自绘行，无障碍树里没有可读文本，
  `ui_long_press_text` 在聊天列表上找不到目标；此类界面需用 `ui_dump` 取坐标后
  调用 `ui_long_press`。设置页/智能体列表等 TextView 行工作正常。
- 测试用的 Auto 策略已从 agent.json 移除，测试工作流已删除，无闹钟、无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.50-alpha-arm64-x64.apk`（versionCode 659，sha256
`f6dce836…`）；debug `dist/OrionekoX-0.8.50-debug-arm64-x64.apk`（sha256 `bcc86bf0…`）。
0.8.49 为中间构建，已覆盖。

## 5.52 alpha 0.8.50 —— 聚焦 QA（2026-10-26 05:50–05:53）

- 工具与权限页滚到 `ui_*` 区域：`ui_long_press` / `ui_long_press_text` 两行均存在，
  中文描述为“按屏幕坐标长按。”/“查找屏幕上的文字并长按。”，策略值“默认 · 询问”。
- 终端设置：切换 `Ox · 炭黑底绿字` 与 `18 sp`，单选状态即时更新；随后恢复为
  `Ubuntu · 茄紫底` + `16 sp`，用户原设置未被留在测试值。
- 工作流页：测试条目删除后为空态，`ox_workflows` 为 `[]`，无 `RUN_WORKFLOW` 闹钟。
- 最近 ~900 行日志无 FATAL/ANR。
- 已知问题（同 5.51）：聊天列表 `DialogCell` 自绘无无障碍文本，需 `ui_dump` + 坐标
  长按；Ask 弹窗关闭瞬间的手势可能被吞。
- 本轮无代码改动，版本保持 0.8.50；QA 记录在 `docs/qa-0.8.50.md`。

## 5.53 alpha 0.8.54 —— 聊天列表无障碍窗口调查（2026-10-26 05:54–06:14）

**目标**：让 `ui_tap_text` / `ui_long_press_text` 能按名字操作聊天列表行。

**调查**
- 聊天列表前台时，AccessibilityService 只看到一个全屏 `FrameLayout` 窗口；
  `getRootInActiveWindow()` 与 `getWindows()` 返回同一个根。
- 原配置下该根只有 **30 个节点**；加 `flagIncludeNotImportantViews` 后升到 **70 个**，
  但标签仍然只有 `OrionekoX`、更多选项、搜索聊天、五个底栏标签和少量框架描述，
  **没有任何聊天行**。`DialogCell` 的 `onInitializeAccessibilityNodeInfo` 虽然会
  `setContentDescription`，这些行并未进入服务可见的树。
- 结论：这是 Telegram `RecyclerListView`/`ViewPager` 的无障碍暴露方式导致的，
  不是匹配或滚动 bug。仅靠现有工具无法按文字定位聊天行；聊天列表必须用
  `ui_dump` 取坐标后调用 `ui_tap` / `ui_long_press`。

**保留的两个改进**
- `activeRoot()` 增加“必须覆盖屏幕中心”的判断：若焦点窗口是底部悬浮条/覆盖层，
  改选覆盖中心的最大应用窗口；弹窗与全屏页面仍优先（它们覆盖中心）。
- 无障碍服务配置加入 `flagIncludeNotImportantViews`，更多节点（30→70）对 `ui_dump`
  可见，便于后续诊断。
- 回归：智能体管理列表的 `ui_long_press_text("Echo Assistant")` 仍然正常
  （nodes=95、命中并弹出长按菜单），未被 `activeRoot` 改动破坏。

**产物**：`dist/OrionekoX-0.8.54-alpha-arm64-x64.apk`（versionCode 699，sha256
`8046e63e…`）；debug `dist/OrionekoX-0.8.54-debug-arm64-x64.apk`（sha256 `184c5bc2…`）。
0.8.51–0.8.53 为本轮中间构建，均已覆盖。测试工作流已删除、策略已还原、无闹钟/崩溃。

## 5.54 alpha 0.8.55–0.8.56 —— 本地群消息发送与群历史（2026-10-26 06:17–06:40）

**验证到的 bug**
- 新建的本地群（只有合成智能体成员）里，用户消息气泡显示**红色感叹号**：消息仍走了
  服务器发送，而本地群在服务器不存在。智能体回复本身正常（Yuki 先 `recall` 再答
  “Green, of course.”）。

**修复**
- `SendMessagesHelper` 在 `interceptOutgoing` 之后增加本地群分支：`peer<0` 且
  `OxGroupStore.isLocalGroup(peer)` 时，调用新的
  `OxAgentsBridge.sendLocalGroupText`，本地落库、标记已发送、触发群内智能体，然后
  `return`，不再走服务器；真实群（含 agent 但服务器存在）仍保持原服务器发送 + 本地回复。
- `OxGroupStore` 新增 `isLocalGroup` 与每群聊天记录（`log_<chatId>`，最多 40 条）。
- `groupMessages` 把该群最近记录作为上下文传给模型；`startGroupTurn` 记录用户消息，
  `finishTurn` 对群聊只写群日志，不再把群消息写进该智能体的 1:1 历史。

**验证**
- **已验证**（0.8.55，重启前）：同一群发送 “Are you sure?” 后气泡显示双勾、无红叹号，
  Yuki 在群内正常回复；修复前的旧消息仍保留红叹号（历史数据，未改动）。
- **已验证**（0.8.56，06:40–06:43 补测）：在新启动的模拟器上关闭系统动画后测试通过：
  - 发送 “My favorite number is 7.”，Yuki 调用 `remember` 并回复已记住；
  - 发送 “The group codeword is pineapple. Do not use tools”，Yuki 未调用工具并确认暗号；
  - 再问 “What is the group codeword? Do not use tools”，Yuki 回答 “是「pineapple」呀～🍍
    我记着呢，没用工具。”，日志 `onRound tools=0`——暗号从未写入长期记忆，答案只能
    来自新加的群历史，证明上下文修复生效；
  - 所有新消息气泡均为双勾、无红叹号；系统动画已恢复为 1。
- 之前多次崩溃是模拟器渲染/稳定性问题（普通、headless、`-gpu off` 均在进入聊天页后
  掉线，宿主内存充足、无应用 FATAL），不作为应用结论；测试群保留，未删除 Agent 或
  真实联系人会话。

**产物**：`dist/OrionekoX-0.8.56-alpha-arm64-x64.apk`（versionCode 719，sha256
`2689e975…`）；debug `dist/OrionekoX-0.8.56-debug-arm64-x64.apk`（sha256 `76e16808…`）。
0.8.55 为中间构建，已覆盖。

## 5.55 alpha 0.8.57–0.8.58 —— 全量中文包机翻清理（2026-10-26 06:45–06:55）

**发现**
- 对 `assets/oxlang/zh-cn.xml` 做全量词条扫描，发现 **69 条**把 `and`/逗号/`m`/冠词
  误翻成 `(单位:千美元)` 的字符串，覆盖赠送活动、Premium、通话邀请、Story 通知、
  Stars 解锁、钱包恢复短语等；另有 `页:1`（8 条）、`磁带`（13 条）、`天赋`
  （17 条）、`学更多`（13 条）、`星号`（34 条）、`投诸法`、`剖面照片`、
  `生物测量` 等高频机翻词。
- 占位符审计发现 5 条登录/短信倒计时把 `%2$02d` 弄成 `% 2 美元 02d`，会丢失秒数
  并显示“美元”。

**修复**
- 69 条按英文原文逐条重写，保留全部 `%N$s/%d` 占位符；例如
  `Give %2$s and **%1$d** more friends...` → “让 %2$s 和另外 **%1$d** 位朋友…”；
  `a Telegram Premium subscription` → “一份 Telegram Premium 订阅”。
- 30 条赠送/礼物/赠送活动词条、8 条 `页:1`、8 条编号/待办词条按语境重写；
  机械修正 `磁带→点按`、`学更多→了解更多`、`星号→星`、`投诸法→投票`、
  `剖面照片→个人资料照片`、`生物测量→生物识别`。
- 5 条短信/通话倒计时恢复为 `%1$d:%2$02d`。
- `OX_ZH_PACK_VERSION` 37→39，确保运行时重新拷贝。

**验证**
- 构建后在设备上读取运行时包 `files/ox_zh-cn.xml`：上述坏词均为 0；
  `赠送活动` 8 处、`%1$d:%2$02d 后请求短信` 1 处；无 FATAL/ANR。
- 占位符复核：剩余 7 处为 `*_one` 复数条目主动加入“1 条/1 张”计数，属自然中文，
  其余英文与中文占位符一致。

**产物**：`dist/OrionekoX-0.8.58-alpha-arm64-x64.apk`（versionCode 739，sha256
`4ba4da84…`）；debug `dist/OrionekoX-0.8.58-debug-arm64-x64.apk`（sha256 `549c8304…`）。
0.8.57 为中间构建，已覆盖。

## 5.56 alpha 0.8.58 —— 媒体发送尝试（未完成，2026-10-26 06:58–07:05）

- 目标：向 Yuki 发送 `Download/ox_photo.png`，验证原生照片气泡、工作区归档和视觉请求。
- 尝试 1：附件 → 照片网格，首个磁贴打开的是相机预览（未拍照/未发送），已退出。
- 尝试 2：附件 → 文件 → 内部存储器 → Download → `ox_photo.png`；文件可见，
  但点击选择的瞬间模拟器整个掉线，未进入预览/发送页。
- 重启后核查：Yuki 会话最后一条仍是 06:11 的工具测试，`messages_v2` 无新媒体行，
  `oxagents/<id>/media` 与 `inbox` 无新文件——**没有发出任何媒体消息**。
- 结论：媒体发送链路本轮**未验证**；这是模拟器在聊天/媒体操作时的渲染稳定性问题
  （无应用 FATAL），需要稳定模拟器或真机复测。无代码改动，版本保持 0.8.58。
- 记录：`docs/qa-media-attempt.md`。

## 5.57 alpha 0.8.59 —— 工作区导入文件（2026-10-26 07:08–07:17）

**新增**
- 工作区页面新增“导入文件”：通过 SAF `ACTION_OPEN_DOCUMENT` 选择设备上的任意文件，
  复制到当前 Agent 的私有工作区；文件名做路径清理，重名自动追加 ` (1)`，单文件上限
  20 MB，复制过程在后台线程执行并提示结果。
- 文案中英文与 oxlang 包同步（包版本 40）；无新增图标，避免图标重复。

**实测**
- Ox 设置 → 工作区（API Agent）→ 导入文件 → Downloads → `ox_file.txt`（45 B）：
  列表出现 `ox_file.txt · 45 B`，点按预览显示 “hello agent, this is a local file.”
  与第二行内容，复制路径/关闭/编辑按钮正常；长按删除后列表恢复空态，磁盘目录为空。
- 全程无 FATAL/ANR；未触碰聊天、媒体或真实联系人数据。

**产物**：`dist/OrionekoX-0.8.59-alpha-arm64-x64.apk`（versionCode 749，sha256
`4c48b582…`）；debug `dist/OrionekoX-0.8.59-debug-arm64-x64.apk`（sha256 `c7248fbb…`）。

## 5.58 alpha 0.8.60 —— 工作区导出文件（2026-10-26 07:20–07:28）

**新增**
- 工作区文件长按由“直接删除确认”改为原生菜单：**预览 / 导出到设备 / 删除**。
- “导出到设备”通过 SAF `ACTION_CREATE_DOCUMENT` 保存，按扩展名选择 MIME、默认沿用原名；
  文件复制在后台线程执行并提示结果。

**实测**
- 选择 Yuki Waifu 的工作区，长按 `hello.md`（18 B）→ 导出到设备 → Downloads：
  `/sdcard/Download/hello.md` 生成，内容为 `# Hello from Yuki`，与工作区一致。
- 测试导出文件已删除；Yuki 的工作区（`hello.md`、`notes/note.txt`）未改动；
  全程无 FATAL/ANR，未触碰聊天或真实联系人数据。

**产物**：`dist/OrionekoX-0.8.60-alpha-arm64-x64.apk`（versionCode 759，sha256
`4ee6421f…`）；debug `dist/OrionekoX-0.8.60-debug-arm64-x64.apk`（sha256 `aca32cd8…`）。

## 5.59 alpha 0.8.61 —— 工作区重命名（2026-10-26 07:29–07:38）

**新增**
- 工作区文件长按菜单变为 **预览 / 重命名 / 导出到设备 / 删除**。
- 重命名弹窗预填当前相对路径，可同时改名或移动到工作区内的子目录；校验空路径、
  绝对路径和 `..`，目标已存在时拒绝；优先 `renameTo`，失败则复制后删除原文件。

**实测**
- Yuki 工作区长按 `hello.md` → 重命名 → `hello2.md`：列表和磁盘均更新，内容
  `# Hello from Yuki` 保持不变。
- 再重命名回 `hello.md`，磁盘仅剩 `hello.md`（18 B）与 `notes/`，工作区恢复原状。
- 中文/英文/oxlang 包（版本 42）同步；无 FATAL/ANR，未触碰聊天或真实联系人数据。

**产物**：`dist/OrionekoX-0.8.61-alpha-arm64-x64.apk`（versionCode 769，sha256
`14b53a6b…`）；debug `dist/OrionekoX-0.8.61-debug-arm64-x64.apk`（sha256 `805f4a86…`）。

## 5.60 alpha 0.8.61 —— 媒体发送第二次尝试（未完成，2026-10-26 07:42–07:46）

- 为排除图片解码因素，改用纯文本文件 `Download/ox_file.txt`（45 B）：附件 → 文件 →
  内部存储器 → Download → 选中文件。
- 选择文件的同一瞬间模拟器再次掉线（`adb` 变为 `offline`），与选择 `ox_photo.png`
  时完全一致；因此崩溃点是从系统/文件选择器切换到 Telegram 附件预览/发送页，
  与具体媒体类型无关，也没有应用 FATAL（整个 qemu 进程消失）。
- 重启后核查：Yuki 会话最后一条仍是 06:11 的工具测试，无新 `messages_v2` 媒体行，
  `oxagents/<id>/media` 与 `inbox` 均无新文件——**没有发出任何消息**。
- 结论：媒体发送链路本轮仍未验证，需要真机或不同 GPU/镜像。记录已更新到
  `docs/qa-media-attempt.md`；无代码改动，版本保持 0.8.61。

## 5.61 alpha 0.8.62 —— 中文包乱码清理（2026-10-26 07:47–07:54）

- 二次扫描发现约 **42 条乱码/损坏字符串**：`⁇` 吞掉了“星/年/秒/订阅”等词，
  以及 `SuggestionAgreementReachedAdmin1PastSimple` 被替换成下划线、
  `UsernameAlso` 变成 `ưμ㼯A%1$s`、`StakeDicePlaceholder` 只剩 ` ⁇ `。
- 逐条按英文原文重写：Stars/礼物转换、Premium 到期、贴纸提示、钱包 Grams、
  代理轮换秒数、日期时间拼接、投票/用户名建议等；全局清理 `⁇ 星` → ` 星`。
- 修复 `ForceSetPasswordAlertMessage_one` 丢失的 `%1$s`，保存为
  “每 %1$s 天只能通过短信登录一次”。
- 占位符复核：除 7 条 `*_one` 复数文案主动显示“1 条/1 张”外，英文/中文占位符一致。
- `OX_ZH_PACK_VERSION` 42→43；设备运行时包完整（942 KB、11831 条），
  `⁇`/`�`/下划线占位/此前坏词均为 0，抽样新文案均存在；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.62-alpha-arm64-x64.apk`（versionCode 779，sha256
`4d3606c5…`）；debug `dist/OrionekoX-0.8.62-debug-arm64-x64.apk`（sha256 `e3c07c42…`）。

## 5.62 alpha 0.8.63 —— 智能体复制（2026-10-26 07:55–08:05）

**新增**
- 智能体长按菜单新增 **复制**（置顶之后）：以原智能体的完整配置生成副本，名称使用
  “原名 · 副本”，复制人设、模型/provider、采样参数、工具策略、技能与长期记忆；
  副本 `built_in=false`、`tg_id=0`（由 syncAgents 重新分配）、不置顶、不共享头像文件。
- 副本可正常编辑、聊天、删除，删除时走既有 `onAgentDeleted` 清理会话。

**实测**
- 长按 Echo Assistant → 复制：列表出现 `Echo Assistant · 副本`；磁盘生成 `dup39032a`
  目录，`agent.json` 中 `built_in=false`、`tg_id=555000012`、人设/参数完整。
- 长按副本 → 删除 → 确认：列表只剩原始 Echo，`dup*` 目录已移除；无 FATAL/ANR。
- 首版名称是“Echo Assistant副本”，已改为带分隔的“Echo Assistant · 副本”并重新构建验证。

**产物**：`dist/OrionekoX-0.8.63-alpha-arm64-x64.apk`（versionCode 789，sha256
`55418164…`）；debug `dist/OrionekoX-0.8.63-debug-arm64-x64.apk`（sha256 `a12cb453…`）。

## 5.63 alpha 0.8.64 —— 工作区复制文件（2026-10-26 08:06–08:20）

**新增**
- 工作区文件长按菜单新增 **复制一份**：在文件所在目录生成 `名称 (copy).扩展名`，
  重名自动追加 ` (1)`；后台线程复制，提示结果。
- 菜单顺序：预览 / 重命名 / 复制一份 / 导出到设备 / 删除；中英文与 oxlang 包（45）同步。

**实测**
- Yuki 工作区长按 `hello.md` → 复制一份：生成 `hello (copy).md`（18 B），内容与原文
  一致 `# Hello from Yuki`；列表同时显示两份。
- 长按副本 → 删除 → 确认：目录恢复为 `hello.md` + `notes/`，无残留；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.64-alpha-arm64-x64.apk`（versionCode 799，sha256
`47d41f85…`）；debug `dist/OrionekoX-0.8.64-debug-arm64-x64.apk`（sha256 `23d6afb9…`）。

## 5.64 alpha 0.8.65 —— 工作流复制（2026-10-26 08:22–08:32）

**新增**
- 工作流长按菜单新增 **复制**（立即运行之后）：复制名称、提示词、智能体、间隔/每日
  时刻，名称使用 “原名 · 副本”；副本默认**暂停**，避免悄悄重复消耗模型额度。
- 复制不继承上次结果与运行记录；中英文与 oxlang 包（46）同步。

**实测**
- 新建 `Copy test`（Echo，手动）→ 长按 → 复制：列表出现 `Copy test · 副本`；
  `ox_workflows` 中两条记录，原条与副本均为 `enabled=false`、`interval=0`。
- 依次长按删除两条测试工作流：prefs 恢复 `[]`，无 RUN_WORKFLOW 闹钟；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.65-alpha-arm64-x64.apk`（versionCode 809，sha256
`29738bd7…`）；debug `dist/OrionekoX-0.8.65-debug-arm64-x64.apk`（sha256 `8f37985e…`）。

## 5.65 alpha 0.8.66 —— 权限弹窗中文清理（2026-10-26 08:33–08:38）

- 扫描发现 9 条权限说明仍是混合文案，例如
  “**Telegram**需要麦克风才能发送语音信息。Tap 设置 ->权限,并转**麦克风**继续”。
- 已逐条改为自然中文：“**Telegram** 需要麦克风权限才能发送语音消息。请点按 设置 →
  权限，并开启 **麦克风** 权限。”，覆盖音乐/音频、麦克风、蓝牙、摄像头、联系人、
  位置、存储、头像与媒体权限。
- `OX_ZH_PACK_VERSION` 46→47；设备运行时包为 942,875 字节，抽样 9 条新文案均存在，
  检查内容中 `Tap ` 为 0；无 FATAL/ANR。
- 说明：`adb shell cat` 读取大文件会截断到约 751 KB，本次用 `ls` 的文件大小 + 抽样
  内容校验，文件本身完整。

**产物**：`dist/OrionekoX-0.8.66-alpha-arm64-x64.apk`（versionCode 819，sha256
`53a74f9a…`）；debug `dist/OrionekoX-0.8.66-debug-arm64-x64.apk`（sha256 `bcfc51d3…`）。

## 5.66 alpha 0.8.67 —— .oxagent 包含头像（2026-10-26 08:39–08:48）

**新增**
- 导出时若智能体有有效头像文件，`.oxagent` 写为 zip，包含 `agent.json` 与
  `avatar.<ext>`；`agent.json` 的 `avatar_path` 置空、新增 `avatar_name`，避免把
  旧设备的绝对路径带到新设备。
- 导入时把头像写入新智能体目录，回填 `avatar_path` 并再次保存；若旧 JSON 里的
  `avatar_path` 在当前设备不存在则自动清空。无头像且无工作区时仍输出旧的纯 JSON。

**实测**
- 复制 Echo Assistant，测试环境用 root 将 `ox_photo.png` 放到副本目录并写入
  `avatar_path`；导出得到 1740 B zip，`unzip -l` 含 `agent.json`(682) 与
  `avatar.png`(2745)，`avatar_path=''`、`avatar_name='avatar.png'`、API Key 为空。
- 导入该文件生成 `imp2a7dbf`：`avatar.png`(2745 B) 恢复，`agent.json` 的
  `avatar_path` 指向 `/data/user/0/.../imp2a7dbf/avatar.png`（设备上存在）。
- 删除两个测试副本与导出文件后，只保留原始 Echo Assistant；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.67-alpha-arm64-x64.apk`（versionCode 829，sha256
`5d0f08bc…`）；debug `dist/OrionekoX-0.8.67-debug-arm64-x64.apk`（sha256 `536cdbd4…`）。

## 5.67 alpha 0.8.68 —— 打开应用工具（2026-10-26 08:50–09:00）

**新增**
- 内置工具 `app_open(package | name)`：按包名精确匹配，或按启动器标签精确/包含匹配，
  用 LAUNCHER intent 启动已安装应用；默认 Ask。
- Manifest `<queries>` 增加 MAIN/LAUNCHER intent，使 `queryIntentActivities` 能看到
  桌面应用（未申请 QUERY_ALL_PACKAGES）。
- 工具页中文描述“按包名或名称打开已安装的应用。”；oxlang 包 48。

**实测**
- 工作流 “Open app test”（Yuki，后台运行）调用
  `app_open(package=com.android.settings)`：工具返回
  `opened Settings (com.android.settings)`，`topResumedActivity` 变为
  `com.android.settings/.Settings`，证明跨应用启动成功。
- 随后回到 OrionekoX，删除测试工作流、清空 `app_open` 临时 Auto 策略：
  `ox_workflows` 为 `[]`、无闹钟、无 FATAL/ANR；未触碰真实联系人或媒体。

**产物**：`dist/OrionekoX-0.8.68-alpha-arm64-x64.apk`（versionCode 839，sha256
`6eb5bb90…`）；debug `dist/OrionekoX-0.8.68-debug-arm64-x64.apk`（sha256 `8bb79725…`）。

## 5.68 alpha 0.8.69 —— 等待界面文字（2026-10-26 09:01–09:09）

**新增**
- 内置工具 `ui_wait_text(text, timeout_ms)`：每 400 ms 查询当前窗口，支持精确/包含
  匹配与 `findAccessibilityNodeInfosByText` 索引，最多等待 15 s；返回文字与 bounds
  或超时信息。默认 Ask。
- 工具页中文描述“等待屏幕上的文字出现。”；oxlang 包 49。

**实测**
- 工作流 “Wait text test”（Yuki 后台）串联两个工具：
  `app_open(package=com.android.settings)` → `opened Settings (com.android.settings)`；
  紧接着 `ui_wait_text(text=Settings, timeout_ms=8000)` →
  `found "Settings" bounds=[63,401][408,528]`；`topResumedActivity` 为
  `com.android.settings/.Settings`。
- 说明跨应用自动化链路（打开应用 → 等待目标界面 → 后续可继续点击/输入）已打通。
- 删除测试工作流并清空 `app_open`/`ui_wait_text` 临时 Auto 策略：prefs `[]`、
  无闹钟、无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.69-alpha-arm64-x64.apk`（versionCode 849，sha256
`e5a0af4f…`）；debug `dist/OrionekoX-0.8.69-debug-arm64-x64.apk`（sha256 `f3295261…`）。

## 5.69 alpha 0.8.70 —— 输入框文字（2026-10-26 09:10–09:23）

**新增**
- 内置工具 `ui_input_text(text, target)`：按文字/描述/提示查找可编辑控件（精确 →
  包含 → 焦点输入框 → 第一个输入框），先 `ACTION_FOCUS` 再 `ACTION_SET_TEXT` 写入；
  返回 `typed into "标签"` 或明确错误。默认 Ask。
- 工具页中文描述“在焦点或第一个输入框中输入文字。”；oxlang 包 50。

**实测**
- 0.8.70 alpha/debug 已安装到 emulator-5554，`versionName=0.8.70`，
  `tools/push-latest-apk.sh` 成功；无 FATAL/ANR。
- 工作流 “Input text test”（Yuki 后台）三连调用：
  `app_open(package=com.android.settings)` → `opened Settings (com.android.settings)`；
  `ui_tap_text("Search settings")` → `clicked "Search settings" bounds=[200,624][553,695]`；
  `ui_input_text("battery")` → `typed into "Search settings"`。
  `uiautomator dump` 确认搜索框 `EditText text='battery'`，界面出现 Battery Saver、
  Battery usage、Adaptive Battery 等过滤结果，跨应用“打开 → 点击 → 输入”链路打通。
- 首次运行返回 `error: accessibility service is not enabled`：重新安装或 force-stop
  后系统会解绑无障碍服务（`settings get secure enabled_accessibility_services`
  变为 null），重新启用后立即通过；已记录为测试环境注意事项，非工具缺陷。
- 清理：Yuki 的 `tools_policy` 恢复原状（移除临时的 app_open/ui_tap_text/
  ui_input_text/ui_wait_text Auto），`ox_workflows` 恢复 `[]`，无待执行
  RUN_WORKFLOW 闹钟，无障碍服务恢复启用；未触碰真实联系人与媒体。

**产物**：`dist/OrionekoX-0.8.70-alpha-arm64-x64.apk`（versionCode 859，sha256
`9b9fc3d9…`）；debug `dist/OrionekoX-0.8.70-debug-arm64-x64.apk`（sha256 `10bd0acd…`）。

## 5.70 alpha 0.8.71 —— 终端底部安全区与提示文案（2026-10-26 09:24–09:34）

**修复**
- 终端作为底部 Tab 打开时，输入命令行被悬浮主 Tab 栏遮住：只预留了
  `MAIN_TABS_HEIGHT_WITH_MARGINS + 8`，没有加系统导航栏高度；从 Ox 设置单独
  打开的终端也同样缺少导航栏安全区。现在初始 padding 与 WindowInsets 监听都
  加上 `navigationBars().bottom`，键盘弹出时仍只用 IME inset，避免双重留白。
- 终端欢迎语中的 `⌨` 在等宽字体里显示为方框，改为
  “点按键盘图标可收起键盘。”，文案更明确且不再依赖缺字字体。

**实测**
- 0.8.71 alpha/debug 已安装，`versionName=0.8.71`；终端页 `EditText` 输入框
  bounds `[98,2010][821,2127]`，主 Tab 栏 `[810,2255][990,2300]`，二者不再
  重叠；截图确认输入行、发送与键盘按钮全部可见可点。
- 顺带点击 QA：设置页（滚动到底后代理与 VPN 行完整可见）、Ox 设置、工具与权限
  （get_time 自动 → 询问 → 禁止循环正常，随后恢复）、MCP 服务、技能库、工作区；
  无 FATAL/ANR，未触碰真实联系人与媒体。测试中 API Agent 的显式
  `get_time=auto` 被循环成默认 auto，已恢复为显式值。

**产物**：`dist/OrionekoX-0.8.71-alpha-arm64-x64.apk`（versionCode 869，sha256
`5a83b305…`）；debug `dist/OrionekoX-0.8.71-debug-arm64-x64.apk`（sha256 `0e24cc65…`）。

## 5.71 alpha 0.8.72 —— 时间与状态文案清理（2026-10-26 09:36–09:44）

**修复**
- 消息状态的无障碍描述出现“于 于 9:20 AM 发送”：`TodayAt` 已是“于”，而
  `AccDescrSentDate`/`AccDescrReceivedDate` 又写成“于 %s 发送/收到”。改为
  “%s 发送”“%s 收到”，聊天页读作“于 9:20 AM 发送”，会话列表读作“9:20 发送”。
- `AccDescrScheduledDate` 由机翻“%s的约定”改为“定时于 %s”。
- `TodayAtFormatted` 由“时间:%1$s”改为“%1$s”，避免“上次更新：时间:9:20”这类
  重复；`TodayAtFormattedWithToday` 仍保留“今天 %1$s”。
- oxlang 包 50→51。

**实测**
- 0.8.72 安装后打开 Yuki 会话，无障碍文本为“于 9:20 AM 发送, 已查看”和
  “于 9:20 AM 收到”，不再出现双“于”；运行时包抽样三处新文案均正确。

**产物**：`dist/OrionekoX-0.8.72-alpha-arm64-x64.apk`（versionCode 879，sha256
`aae5a256…`）；debug `dist/OrionekoX-0.8.72-debug-arm64-x64.apk`（sha256 `b6e4a4e7…`）。

## 5.72 alpha 0.8.73 —— 出生日期本地化格式（2026-10-26 09:44–09:52）

**修复**
- “我的”页出生日期原显示“7月 14, 2008 (18岁)”，是英文 `MMM dd, yyyy` 格式
  与中文月份混排。`formatterBoostExpired` 改为 `yyyy年M月d日`，年龄模板改为
  “%2$s（%1$d 岁）”/“🎂 %2$s（%1$d 岁）”，使用中文全角括号与空格。
- oxlang 包 51→52。

**实测**
- 0.8.73 安装后“我的”页显示“出生日期: 2008年7月14日（18 岁）”，无障碍与
  可见文本一致；运行时包抽样两条模板均正确；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.73-alpha-arm64-x64.apk`（versionCode 889，sha256
`1cc17892…`）；debug `dist/OrionekoX-0.8.73-debug-arm64-x64.apk`（sha256 `0ceb14bb…`）。

## 5.73 alpha 0.8.74 —— 智能体会话隐藏“屏蔽用户”（2026-10-26 09:48–09:51）

**修复**
- 长按本地智能体会话进入多选 →“更多选项”仍显示“屏蔽用户”。本地智能体是合成
  用户，屏蔽既不生效也会产生无意义的服务器请求。`updateCounters` 计算
  `cantBlockCount` 时加入 `OxAgentsBridge.isAgentDialog(selectedDialog)`，与
  自己、官方账号同等处理。
- 实测长按 Yuki Waifu → 更多选项，只剩“归档 / 标记为未读 / 清空历史记录”，
  “屏蔽用户”消失；群聊与真实用户会话逻辑不受影响。

**产物**：`dist/OrionekoX-0.8.74-alpha-arm64-x64.apk`（versionCode 899，sha256
`0daa593f…`）；debug `dist/OrionekoX-0.8.74-debug-arm64-x64.apk`（sha256 `5fffedc1…`）。

## 5.74 alpha 0.8.75 —— 中文包残留品牌与同步文案（2026-10-26 09:51–09:57）

**修复**
- 中文包残留 30 处旧品牌 “ChatAir”，覆盖多账号、通话权限、屏蔽提示、缓存、
  视频通话、Passport、通知隐藏名等；全部替换为 “OrionekoX”。
- 二维码登录页原本是“链接 *Telegram桌面 * 或 *Telegram网页*QR.”这类机翻，
  改为“在 **Telegram Desktop** 或 **Telegram Web** 中打开二维码。”；下载提示
  改为“在电脑上安装 Telegram Desktop 并登录同一帐号。”。
- 新建联系人页“同步联系人到电话”改为“同步联系人到手机”；Passport 大小写统一。
- oxlang 包 52→53。

**实测**
- 运行时包 `ChatAir` 计数为 0；抽样“清除 OrionekoX 缓存”“同步联系人到手机”
  “在电脑上安装 Telegram Desktop…”均正确；新建联系人页可见文案已是“同步联系人
  到手机”；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.75-alpha-arm64-x64.apk`（versionCode 909，sha256
`dc88577b…`）；debug `dist/OrionekoX-0.8.75-debug-arm64-x64.apk`（sha256 `638e1888…`）。

## 5.75 alpha 0.8.75 —— 本地群回复回归与搜索崩溃限制（2026-10-26 09:58–10:03）

**实测**
- 在既有本地群 “BlanCat 和 🤖 Yuki Waifu” 发送
  “Group check: reply with the word kiwi, do not use tools.”：消息 10:02 发出并
  显示“已查看”，Yuki 未调用工具（`onRound tools=0`），并凭群历史指出暗号是
  pineapple 而不是 kiwi，说明本地群落库、群历史注入与回复链路在 0.8.75 正常。
- 回归 0.8.74：长按 Yuki 会话 → 更多选项只剩归档 / 标记为未读 / 清空历史记录，
  “屏蔽用户”未出现。
- 新建联系人面板显示“同步联系人到手机”，只查看未创建；联系人列表仍为 7 个
  本地智能体，用户数据无增删。
- 环境限制：点聊天列表搜索框并输入会让 qemu 整体消失（两次复现，`adb devices`
  为空，无应用 FATAL/ANR）；重启 AVD 后数据完好。本轮改用行坐标打开群聊，
  不再依赖搜索框。
- 详细记录见 `docs/qa-0.8.75.md`。本轮无代码改动，沿用 0.8.75 产物。

## 5.76 alpha 0.8.76 —— 群组服务消息机翻清理（2026-10-26 10:03–10:08）

**修复**
- 清理 37 条群组服务消息的中文机翻，覆盖礼物/抽奖（Stars 支付、升级价值、
  中标）、入群来源（社区）、邀请链接删除/修改/撤销、语音聊天结束与邀请、
  转发权限、群主转让、聊天回应变更、生日建议等。
- 统一占位符与语序：“un1 送给 un2 一份礼物，花费 %d Stars”“你与 un2 分享了
  一个频道”“un1 将邀请链接 un2 修改为 un3”等；去掉残留的旧字体标记。
- oxlang 包 53→54。

**实测**
- 运行时包抽样 `ActionGiftChannel_other`、`ActionGiftStarsSubtitle`、
  `ActionJoinedFromCommunityYou`、`ActionNewCreatorPending`、
  `ActionGroupCallEndedBy`、`ActionSuggestBirthday` 均为新文案；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.76-alpha-arm64-x64.apk`（versionCode 919，sha256
`dba99ea7…`）；debug `dist/OrionekoX-0.8.76-debug-arm64-x64.apk`（sha256 `b22cdcc7…`）。

## 5.77 alpha 0.8.77 —— 括号/价格/AI 样式提示修正（2026-10-26 10:08–10:13）

**修复**
- 修复 18 条括号不配对的价格/隐私/投票/直播文案，例如
  “你会得到的%1$s选定费用(~)%2$s$),每一封来电.” 改为
  “每收到一条消息，你将获得所选费用的 %1$s（约 %2$s$）。”；
  “实时流完成( E)%s)” 改为 “直播已结束（%s）”。
- 联系人 &amp; 迷你应用、Everyone (-%d)、Show Original 等统一为中文全角括号。
- AI 样式编辑器的“Pilate”/“海象”等错误示例改为“海盗”和自然的风格描述。
- oxlang 包 54→55。

**实测**
- 运行时包抽样 `GroupMessagesPriceInfo`、`AppsTabInfoText`、
  `AIEditorStyleTitleHint`、`ActionGroupCallEndedByYou` 均正确；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.77-alpha-arm64-x64.apk`（versionCode 929，sha256
`1680f588…`）；debug `dist/OrionekoX-0.8.77-debug-arm64-x64.apk`（sha256 `30748573…`）。

## 5.78 alpha 0.8.78 —— 隐私页与相册文案清理（2026-10-26 10:14–10:21）

**修复**
- 隐私和安全页出现 “请检查url=值 (帮助)Telegram户口.”，按英文原文改为
  “查看已登录你 Telegram 帐号的设备列表。”。
- 隐私项 “请” 改为 “邀请”（`PrivacyInvites`），“组合和频道” 改为 “群组和频道”，
  帐号删除说明改为自然的第二人称表述。
- 13 处 “画廊” 统一改为 “相册”，并重写 “自动保存所有新媒体从私人聊天到您的
  手机\ 画廊” 等破碎句；11 处 “私人聊天” 改为 “私聊”，
  “私人聊天声” → “私聊提示音”，“组” → “群组”。
- oxlang 包 55→56。

**实测**
- 0.8.78 安装后隐私页副标题显示 “查看已登录你 Telegram 帐号的设备列表。”；
  运行时包抽样 `PrivacyInvites=邀请`、`SaveToGalleryHintUser=自动将私聊中的
  所有新媒体保存到手机相册。`；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.78-alpha-arm64-x64.apk`（versionCode 939，sha256
`86a9c1e2…`）；debug `dist/OrionekoX-0.8.78-debug-arm64-x64.apk`（sha256 `f2c8dd43…`）。

## 5.79 alpha 0.8.79 —— 私有区乱码字符串清理（2026-10-26 10:21–10:28）

**修复**
- 全包扫描私有区字符（U+E000–F8FF）与替换符，发现 16 条字符串会显示为方框或
  乱码，覆盖：隐私消息说明与 “或 Telegram Premium?”、二维码邀请（群组/频道/
  文件夹/群组通话）、“我明白了”、Story 可见范围“所有人”、聊天空状态问候、
  反应通知、会员过期提示、“这是你”、位置距离动作、`DayAgo`。
- 逐条按英文原文重写并清理空格/标点，例如
  “你可以将接收消息范围限制为仅联系人和 Premium 用户。**什么是 Telegram Premium？**”
  “任何 Telegram 用户都可以扫描此二维码加入你的群组。”。
- 顺带修正 Premium 助力文案的语序（“只有 **Telegram Premium** 订阅者才能为频道助力。”）。
- oxlang 包 56→57。

**实测**
- 0.8.79 安装后打开 隐私和安全 → 消息：页面显示“所有人 / 我的联系人和 Premium
  用户 / 消息收费”，说明文字完整且无方框；运行时包抽样 `PrivacyMessagesInfo`、
  `IUnderstand`、`QRCodeLinkHelpGroup`、`RestorePremiumHintMessage` 均正确；
  全包私有区字符计数为 0；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.79-alpha-arm64-x64.apk`（versionCode 949，sha256
`80e5ea78…`）；debug `dist/OrionekoX-0.8.79-debug-arm64-x64.apk`（sha256 `5e16d913…`）。

## 5.80 alpha 0.8.80 —— 归档往返与归档说明文案（2026-10-26 10:29–10:40）

**实测**
- 会话多选“归档 → 已归档对话 → 取消归档”往返通过：长按本地群聊 → 更多选项 →
  归档，提示“对话已归档。”，列表顶部出现“已归档对话”行并保留未读 1；进入
  归档页可见该群聊，再次多选 → 取消归档后群聊回到主列表，历史消息完整。
- 过程中打开归档页时模拟器曾整体掉线一次（无应用 FATAL/ANR），重启后归档状态
  仍在，说明是 AVD 渲染问题；已恢复原状（无归档会话）。
- QA 中还发生过一次误触：在“隐私 → 消息”子页长按时把“谁能给我发消息”切到
  “我的联系人和 Premium 用户”，已立即改回“所有人”并核对单选项；后续长按前
  先确认已回到聊天列表。

**修复**
- 归档说明页多条机翻改为自然中文：“这里是您的归档”→“这里是你的归档”，
  “将任何聊天移到您的档案中, 然后在档案中擦掉。”→“在任意聊天上滑动，即可将
  其移入或移出归档。”，“未同步的聊天”改为“未静音的聊天”，并修正隐藏归档、
  故事归档与“已归档聊天”标题。
- 关于 Gradle daemon CPU：`ps -ef` 里的 ~100% 是进程生命周期平均值（构建期多核
  编译拉高），并非空闲空转；按 `/proc/<pid>/stat` 采样 5 秒，空闲增量为 0。此前
  据此误加 `org.gradle.vfs.watch=false`，复核后已回退，避免无依据的配置改动。
- oxlang 包 57→58。

**产物**：`dist/OrionekoX-0.8.80-alpha-arm64-x64.apk`（versionCode 959，sha256
`041a05d9…`）；debug `dist/OrionekoX-0.8.80-debug-arm64-x64.apk`（sha256 `530d76b3…`）。

## 5.81 alpha 0.8.81 —— 高可见度错译与遗留字体标记（2026-10-26 10:41–10:48）

**修复**
- 按英文原文修正 18 条明显错译：会话菜单 “Clear for me” 从“我没事”改为
  “为我清除”；生日通知“今天是生日”→“🎂 今天是你的生日”；
  “Two-Step 验证”→“已启用两步验证…”；“VIEW STICKERS / VIEW EMOJI /
  VIEW STYLE”改为“查看贴纸集 / 查看表情包 / 查看样式”；
  “In- App Pop”改为“应用内弹窗提示”；SIM/Fragment 绑定说明、反垃圾说明、
  WebView 不可用提示、频道礼物说明、Fragment 转 TON、置顶礼物上限、资料视频
  建议与联盟链接文案一并重写。
- 复核上一轮 Gradle daemon CPU 结论：`ps` 的 %CPU 是生命周期平均值，按
  `/proc/<pid>/stat` 采样空闲增量为 0；已回退 `org.gradle.vfs.watch=false`，
  并在报告与 memory 中更正。
- oxlang 包 58→59。

**遗留**
- 扫描发现 49 条字符串仍带 `{\fn...}` 旧字体标记，且多为礼物/Boost/生日多联系人
  等机翻内容（如“我很高兴见到你”“师曰背景”）。这些字符串占位符复杂，本轮
  不冒险批量替换，已列入下一轮专项清理。

**实测**
- 0.8.81 已安装，`versionName=0.8.81`；运行时包抽样 `ClearForMe=为我清除`、
  `BirthdayToday=🎂 今天是你的生日`、`OpenStickerSet=查看贴纸集`、
  `LoginPasswordTextShort`、`Gift2ExportTONFragmentText` 均正确；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.81-alpha-arm64-x64.apk`（versionCode 969，sha256
`d59c86ca…`）；debug `dist/OrionekoX-0.8.81-debug-arm64-x64.apk`（sha256 `40d11cd0…`）。

## 5.82 alpha 0.8.82 —— 49 条旧字体标记字符串清理（2026-10-26 10:48–10:53）

**修复**
- 上一轮列出的 49 条带 `{\fn...}` 旧字体标记字符串全部按英文原文重写，覆盖：
  礼物（可收集编号、频道过期礼物、制作背景/符号概率、出价过期/拒绝、礼物描述）、
  Boost 服务消息与助力提示、生日多联系人标题、营业时间、两步验证短信提醒、
  转发来源、标签修改、文件夹链接加入、贴纸选择状态、会员头像/文件夹上限、
  定时发布格式、Star 评分/反应、建议帖子、会员状态、自定义头像提示等。
- 同时把 `PublishDayAt`、`StartShortDayYearAt`、`StartShortTodayAt` 三条日期格式
  改成中文 pattern（`M月d日 HH:mm` 等）；`IsChoosingSticker` 去掉无意义的
  “瓳”乱码。
- 全包 `{\fn...}` 计数由 49 降为 0；XML 解析 11,842 条通过；占位符审计只剩
  2 条 `_one` 主动带 `%1$d` 的已知差异。
- oxlang 包 59→60。

**实测**
- 0.8.82 已安装，`versionName=0.8.82`；运行时包 `{\fn` 计数 0；抽样
  `BoostingBoostsGroupByUserServiceMsgCount_other`、`GiftOfferOfferedTextStarsRejectedOut`、
  `PublishDayAt`、`UserCustomPhotoSeted` 均为新文案；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.82-alpha-arm64-x64.apk`（versionCode 979，sha256
`07904eac…`）；debug `dist/OrionekoX-0.8.82-debug-arm64-x64.apk`（sha256 `58f2f206…`）。

## 5.83 alpha 0.8.82 —— 群回复回归与媒体发送打通（2026-10-26 10:54–11:02）

**环境根因**
- 近期“进入聊天/归档/搜索即整机掉线”并非应用崩溃，而是 SwiftShader 渲染问题。
  改用 `emulator -avd orioneko_test -gpu off` 后，聊天页、附件选择器与媒体预览
  全部稳定，所有掉线现场都没有 OrionekoX FATAL/ANR。后续交互 QA 使用 `-gpu off`。

**实测**
- 本地群 “BlanCat 和 🤖 Yuki Waifu” 发送
  “Version check: reply with one word, do not use tools.”：10:57 发出并显示
  “已查看”，Yuki 单条回复，`onRound tools=0`，群历史与本地投递正常。
- **媒体发送首次成功**：Yuki 1:1 → 文件 → 内部存储器 → Download → `ox_photo.png`
  （2.7 KB）→ 预览 → 发送；气泡显示“于 11:01 AM 发送, 已查看”，日志为
  `persistAndShow media type=9 mediaClass=TL_messageMediaDocument`；Yuki 随即调用
  `list_dir({"path":"inbox"})`，返回 `1792983667815_ox_photo.png 2745B`，
  并回复文件已保存在 `inbox/`。
- 外观限制：气泡缩略图仍显示 X 与 “0 KB / 2.7 KB”，原因是注入的本地 document
  没有可下载的缩略图/file location；字节已送达 agent，后续可把文件复制进附件
  缓存或补一个本地 photo size。
- 详细记录见 `docs/qa-media-0.8.82.md`。本轮无代码改动，沿用 0.8.82 产物。

## 5.84 alpha 0.8.83–0.8.84 —— 本地媒体气泡状态诊断（2026-10-26 11:04–11:14）

**尝试**
- 0.8.83：`buildLocalDocument` 写入 `document.localPath = path`，让
  `FileLoader.getPathToAttach` 直接返回已写入的文件，而不是按远端下载处理。
- 0.8.84：进一步把本地 document 标记为 `id = Integer.MIN_VALUE`、
  `dc_id = Integer.MIN_VALUE`，与 Telegram 本地文档惯例一致。

**结果**
- 每次构建后重新发送 `ox_photo.png`，刚发出的气泡仍显示 X 与
  “已下载 0 KB / 2.7 KB”；但重启应用后，同一批气泡（11:01 / 11:08）都会正常
  显示为“ox_photo.png, 2.7 KB PNG”文档卡片。
- 结论：文件与布局没有问题，剩余的是被拦截消息上的“上传/下载进度”状态未被
  清除（消息没有走真实上传流程）；下一步应在 `persistAndShow` 后显式清理进度/
  发送状态，而不是继续改 document 元数据。
- 媒体送达仍然正常：每次发送都在 agent 的 `inbox/` 生成新文件，Yuki 能通过
  `list_dir` 找到并回复；无 FATAL/ANR。
- 详细记录见 `docs/qa-media-0.8.84.md`。

**产物**：`dist/OrionekoX-0.8.83-alpha-arm64-x64.apk`（versionCode 989，sha256
`e2806c05…`）；0.8.84 alpha（versionCode 999，sha256 `c4e607ed…`），对应 debug
`a557c535…` / `29f93cb9…`。两个版本均已安装验证，最终停留在 0.8.84。

## 5.85 alpha 0.8.85–0.8.86 —— 文件选择器媒体持久化（2026-10-26 11:15–11:27）

**根因**
- 通过“文件”发送时走 `handleOutgoingSpecial`，注入的是选择器原始 document：
  没有本地标记，`attachPath` 指向发送后即被清理的临时路径，因此气泡一直处于
  下载态。

**修复**
- 0.8.85：给该 document 打上 `id/dc_id = Integer.MIN_VALUE` 与
  `localPath = p.path`；X 与“0 KB / size”文字消失，但临时路径已不存在，气泡仍
  显示下载箭头。
- 0.8.86：先把文件归档到 agent 工作区 `inbox/`，再把 `attachPath` 与
  `localPath` 指向这个持久绝对路径。新气泡显示文档卡片与“2.7 KB PNG”，
  不再出现 X/0 KB；下载箭头图标要到应用重启后才变成普通文档图标。
- 媒体送达不变：每次发送继续在 `inbox/` 生成新文件，Yuki 能列出并回复。
- 后续打磨：在 `persistAndShow` 后主动刷新一次 cell/media 状态，让图标立即
  正确，而不是等下次启动。

**产物**：`dist/OrionekoX-0.8.85-alpha-arm64-x64.apk`（versionCode 1009，sha256
`23f330c4…`），debug `2c0b29ce…`；`dist/OrionekoX-0.8.86-alpha-arm64-x64.apk`
（versionCode 1019，sha256 `a51cd810…`），debug `28aabe15…`。两版均已安装，
最终停留在 0.8.86。

## 5.86 alpha 0.8.87 —— 本地媒体气泡实时状态（2026-10-26 11:27–11:32）

**修复**
- `persistAndShow` 构造 `MessageObject` 后，若 `attachPath` 指向的文件真实存在，
  立即设置 `attachPathExists = true`、`mediaExists = true`。此前这两个字段由
  构造函数按旧路径/缓存计算，导致实时气泡显示下载箭头，直到重启才重新计算。
- 实测 0.8.87 发送 `ox_photo.png`：气泡当场显示橙色文档图标与“2.7 KB PNG”，
  没有 X、没有“0 KB”、没有下载箭头，无需重启；agent 仍在 `inbox/` 收到文件并回复。
- 本地媒体气泡链路至此完整：选择文件 → 归档到工作区 → 实时正确渲染 → agent
  可通过工具读取。

**产物**：`dist/OrionekoX-0.8.87-alpha-arm64-x64.apk`（versionCode 1029，sha256
`ce430df8…`）；debug `dist/OrionekoX-0.8.87-debug-arm64-x64.apk`（sha256 `ed07148c…`）。

## 5.87 alpha 0.8.87 —— 终端 TTY 回归（2026-10-26 11:35–11:39）

**实测**
- TTY 1/2/3 直接点按切换正常；TTY 2 运行 `echo tty2-ok` 输出正确，TTY 3 运行
  `pwd` 得到 `/storage/emulated/0/Download`，TTY 1 运行 `help` 列出
  `ls / cd / pwd / cat / echo / clear / TTY` 及中文说明。
- 长按 TTY 2 弹出“分配智能体到这个 TTY”，列表显示各智能体、QA2 已勾选，并有
  “取消分配 / 取消”；本次只查看未改动。
- 键盘弹出时输入行 bounds `[98,1384][821,1501]`，仍在悬浮 Tab 栏（y=2255）之上，
  0.8.71 的底部安全区修复保持有效。
- 无 FATAL/ANR；未删除文件，仅三个内存 TTY 会话的输出变化。记录见
  `docs/qa-terminal-0.8.87.md`。本轮无代码改动，沿用 0.8.87 产物。

## 5.88 alpha 0.8.87 —— 新建智能体与 MCP 商城（2026-10-26 11:38–11:42）

**实测**
- 智能体 → 新建智能体：原生编辑器含头像、资料/备注、技能、模型服务
  （DeepSeek · deepseek-v4-flash）、接口地址、API Key、模型、从 API 获取列表、
  预设模型、获取 Key、测试连接、高级参数、自定义请求头与 AGENTS.md；保存为
  右上角对勾，返回键丢弃草稿。关闭后智能体列表仍为 7 个，没有误创建。
- MCP 服务页显示既有测试服务器 “QA MCP”（HTTP 502）与 “DeepWiki”（3 个工具）；
  插件商城含 DeepWiki/Context7 远程一键安装与 Playwright、水彩旅行、Figma、Tavo
  等 Operit 本地 STDIO 条目；点开 Playwright 显示描述、仓库链接与
  “打开仓库 / 复制配置 / 取消”，未执行安装或卸载。
- 文案自然（“snapshot”为 MCP 术语，保留）；无 FATAL/ANR，未改动任何 MCP 配置。
  记录见 `docs/qa-ox-0.8.87.md`。本轮无代码改动，沿用 0.8.87 产物。

## 5.89 alpha 0.8.88 —— 抽奖日期格式本地化（2026-10-26 11:42–11:46）

**修复**
- 扫描“中文值与英文完全相同”的字符串，只剩 9 条：其中 6 条是 Telegram Premium
  产品名，3 条是抽奖日期格式 `dd MMM yyyy` / `MMMM dd` / `MMMM dd, yyyy`。
- 三条格式改为中文 `yyyy年M月d日` / `M月d日` / `yyyy年M月d日`，避免出现
  “26 10月 2026”这类混排；oxlang 包 60→61。

**实测**
- 0.8.88 已安装，`versionName=0.8.88`；运行时包抽样
  `formatterGiveawayCard=yyyy年M月d日`、`formatterGiveawayMonthDay=M月d日`；
  无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.88-alpha-arm64-x64.apk`（versionCode 1039，sha256
`998e126d…`）；debug `dist/OrionekoX-0.8.88-debug-arm64-x64.apk`（sha256 `e292fb6c…`）。

## 5.90 alpha 0.8.89 —— 编辑资料页文案（2026-10-26 11:47–11:53）

**修复**
- 编辑资料页的姓名字段原为“你的名字( E)”，去掉机翻残留 “( E)” 后改为“你的名字”。
- “您的信息”统一为“你的信息”。
- 生日可见性说明原为“只有你的联系人才能看到你的生日**变动>**”，改为
  “只有你的联系人可以看到你的生日。**更改 >**”；另一条改为
  “在**设置**中选择谁可以看到你的生日。”。
- oxlang 包 61→62。

**实测**
- 0.8.89 安装后打开 我的 → 编辑信息：字段显示“你的名字”，分组标题为“你的信息”，
  生日说明为“只有你的联系人可以看到你的生日。更改 >”；运行时包抽样两条均正确；
  无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.89-alpha-arm64-x64.apk`（versionCode 1049，sha256
`9bf04175…`）；debug `dist/OrionekoX-0.8.89-debug-arm64-x64.apk`（sha256 `1c022102…`）。

## 5.91 alpha 0.8.90 —— 退出登录/客服文案（2026-10-26 11:53–12:01）

**修复**
- 账号操作页把“退出登录”误写成“注销”：“注销将会销毁所有的加密聊天记录。”改为
  “退出登录会使所有加密聊天失效。”；“注销无助于解决问题”改为
  “退出登录通常并不能解决问题。”；“联系志愿者客服”改为“联系客服”。
- 会话终止文案统一：“您确定要注销所有其它设备上的会话吗？”→
  “你确定要终止其他设备上的所有会话吗？”；“注销所有其它设备上的会话”→
  “终止其他设备上的所有会话”。
- oxlang 包 62→63。

**实测**
- 0.8.90 已安装，`versionName=0.8.90`；运行时包抽样 `LogOutInfo`、
  `ContactSupportInfo` 均为新文案；账号操作页本身在 0.8.89 已核对过布局，
  本轮未再触发登出流程；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.90-alpha-arm64-x64.apk`（versionCode 1059，sha256
`14034c0f…`）；debug `dist/OrionekoX-0.8.90-debug-arm64-x64.apk`（sha256 `112cbeb7…`）。

## 5.92 alpha 0.8.91 —— 智能体资料页隐藏“停止”（2026-10-26 12:02–12:10）

**实测**
- 账号操作页实测已显示“联系客服”“遇到问题请告诉我们；退出登录通常并不能解决问题。”
  与“退出登录会使所有加密聊天失效。”，0.8.90 文案修复可见生效。
- 打开 Yuki 资料页发现动作行是 消息 / 静音 / 共享 / **停止**。该“停止”走
  `ProfileActionsView.KEY_STOP → onBlockContactClicked(true)`，对本地合成智能体
  无意义，可能触发服务器屏蔽请求。

**修复**
- `ProfileActionsView` 增加 `hideStop` 标记；MODE_BOT 下为 true 时不再加入
  KEY_STOP。`ProfileActivity.setActionsMode` 在
  `isBot && OxAgentsBridge.isAgentDialog(userId)` 时设置该标记。
- 实测 0.8.91 的 Yuki 资料页动作行只剩 消息 / 静音 / 共享，“停止”消失；
  真实 bot 的停止功能不受影响。

**产物**：`dist/OrionekoX-0.8.91-alpha-arm64-x64.apk`（versionCode 1069，sha256
`bf9a7b52…`）；debug `dist/OrionekoX-0.8.91-debug-arm64-x64.apk`（sha256 `16b3570a…`）。

## 5.93 alpha 0.8.92–0.8.93 —— 智能体资料菜单清理（2026-10-26 12:10–12:22）

**实测**
- Yuki 资料页右上角菜单出现两条重复“分享”，以及“报告”“删除并屏蔽”。原因是
  bot 分支同时加入 `BotShare` 与 `ShareContact`，而中文包里 `ShareContact` 被误译
  成“分享”；报告/屏蔽对本地合成智能体也无意义。

**修复**
- 0.8.92：对 `isBot && OxAgentsBridge.isAgentDialog(userId)` 的资料页隐藏
  BotShare、ShareContact、BotPrivacy、ReportBot、DeleteAndBlock/BotRestart；
  菜单只剩“自动删除 / 添加到主屏幕”。真实 bot 与真实联系人不受影响。
- 0.8.93：`ShareContact` 中文从“分享”改为“分享联系人”，同时修复真实联系人
  资料菜单里的重复标签；oxlang 包 63→64。
- 实测 0.8.92 的 Yuki 资料菜单只剩两项；0.8.93 运行时包
  `ShareContact=分享联系人`；无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.8.92-alpha-arm64-x64.apk`（versionCode 1079，sha256
`c5c4c2dc…`），debug `1c2d1742…`；`dist/OrionekoX-0.8.93-alpha-arm64-x64.apk`
（versionCode 1089，sha256 `ce8dc320…`），debug `1e1787e5…`。

## 5.94 alpha 0.8.94 —— 智能体资料页动作精简（2026-10-26 12:22–12:25）

**修复**
- 0.8.92 的 `shareAction` 守卫去掉“共享”后，实测 Yuki 资料页动作行只剩
  消息 / 静音，按钮按原生两等分布局。
- 资料页下方“添加到群组或频道 / 这个智能体可以管理一个群组或频道”是管理型
  机器人的入口，对本地智能体不适用；0.8.94 在 `isBot` 且
  `OxAgentsBridge.isAgentDialog(userId)` 时不再创建该行，也不显示说明。
- 实测资料页已无“添加到群组或频道”及说明行，无 FATAL/ANR；真实 bot 与真实
  联系人不受影响。

**产物**：`dist/OrionekoX-0.8.94-alpha-arm64-x64.apk`（versionCode 1099，sha256
`c020685b…`）；debug `dist/OrionekoX-0.8.94-debug-arm64-x64.apk`（sha256 `deaf015e…`）。

## 5.95 alpha 0.8.95–0.8.98 —— 本地群资料页（2026-10-26 12:25–12:47）

**问题**
- 打开本地群“BlanCat 和 🤖 Yuki Waifu”的资料页只有头像、标题、成员数和
  消息/静音/离开，成员列表与“添加成员”全部为空。原因是创建群时 ChatFull 只
  `putChatFull` 到内存，没有写入存储；重启后 `ProfileActivity` 从
  `getChatFull` 和 `loadChatInfo` 都拿不到，且本地群无法从服务器补拉。

**修复**
- 0.8.95：新增 `OxAgentsBridge.ensureLocalGroupFull(chatId)`：按
  `OxGroupStore.agents` 重建 ChatFull（自己为创建者 + 各智能体成员），
  `putChatFull` 并 `MessagesStorage.updateChatInfo` 持久化；创建群时也立即持久化。
  `ProfileActivity` 在加载 ChatFull 前对本地群调用该入口。
- 0.8.97/0.8.98：尝试把当前用户对象与真实 client user id 加入 ChatFull，解决
  创建者行空白；重建时不再因旧 ChatFull 已存在而提前返回。
- 实测 0.8.95 起资料页出现“添加成员”、成员数 2、Yuki Waifu（本地智能体）行；
  动作行 消息/静音/语音聊天/离开。**遗留**：创建者自己那一行仍显示为空，仅有
  “创建者”徽标，未继续深挖 `ProfileActivity` 对 self participant 的解析；
  其余本地群功能不受影响。

**产物**：`dist/OrionekoX-0.8.95-alpha-arm64-x64.apk`（versionCode 1109，sha256
`fd5985e6…`），debug `59172a51…`；`dist/OrionekoX-0.8.97-alpha-arm64-x64.apk`
（versionCode 1129，sha256 `32e57036…`），debug `5e1eac3f…`；
`dist/OrionekoX-0.8.98-alpha-arm64-x64.apk`（versionCode 1139，sha256 `7bd6f227…`），
debug `7285a26f…`。0.8.96 为未分发的中间构建。

## 5.96 alpha 0.8.99 —— 本地群创建者行诊断（2026-10-26 12:47–12:55）

**诊断**
- 在 `ensureLocalGroupFull` 加入日志并安装 0.8.99 后打开群资料页，日志为：
  `chat=1800000001 selfId=6527689286 selfUser=true selfResolved=true participants=2`。
  说明创建者 id 与用户对象都已正确解析，成员数 2 也正确，空白行不是数据缺失。
- 因此“创建者”行空白更可能来自 ProfileActivity 对 self participant 的成员 cell /
  sortedUsers 定位与布局处理，而不是 ChatFull 内容；本轮未继续深挖该 UI 细节。
- 群资料页的主功能（成员数、Yuki 行、添加成员、消息/静音/语音聊天/离开）正常。

**产物**：`dist/OrionekoX-0.8.99-alpha-arm64-x64.apk`（versionCode 1149，sha256
`4e7b8509…`）；debug `dist/OrionekoX-0.8.99-debug-arm64-x64.apk`（sha256 `9b030bd7…`）。

## 5.97 alpha 0.8.100 —— 本地群创建者行修复（2026-10-26 12:55–13:00）

**修复**
- 0.8.99 的日志证明 selfId/用户对象/成员数都正确，继续排查发现
  `UsersController` 缓存里已有当前用户的 `TL_userEmpty` 占位对象；
  `putUser(self, false)` 不会覆盖它，导致成员 cell 取到空用户、只显示“创建者”徽标。
- 0.8.100 改为 `putUser(self, true)` 强制写入真实用户对象。
- 实测群资料页现在显示“BlanCat · 在线 · 创建者”与“🤖 Yuki Waifu · 本地智能体”，
  标题下为“2 位成员, 2 人在线”；创建者行不再空白。

**产物**：`dist/OrionekoX-0.8.100-alpha-arm64-x64.apk`（versionCode 1159，sha256
`0187783c…`）；debug `dist/OrionekoX-0.8.100-debug-arm64-x64.apk`（sha256 `a8b7f30e…`）。

## 5.98 alpha 0.8.100 —— 群管理与消息菜单 QA（2026-10-26 12:58–13:05）

**实测**
- 群资料页显示“BlanCat · 在线 · 创建者”“🤖 Yuki Waifu · 本地智能体”，标题下
  “2 位成员, 2 人在线”；动作行 消息/静音/语音聊天/离开。
- 添加成员页：顶部“通过链接邀请他人加入群组”，下列全部本地智能体；Yuki 的开关
  为已选中且禁用，说明已有成员过滤生效。本次未选择/确认任何成员，返回退出。
- 长按群消息的菜单为 回复/复制/转发/置顶/举报/删除；仅查看后返回，未执行举报或
  删除（这两项可能发服务器请求）。
- 全程 `-gpu off` 稳定，无 FATAL/ANR，未改动成员、消息或设置。记录见
  `docs/qa-group-0.8.100.md`。本轮无代码改动，沿用 0.8.100 产物。

## 5.99 alpha 0.8.100 —— 技能库与终端设置 QA（2026-10-26 13:03–13:07）

**实测**
- 技能库页说明“只读浏览 Operit/Operit2 导出的 SKILL.md 包。智能体可调用
  list_skills 和 read_skill。”，列表只有“示例技能 · 测试 Operit SKILL.md 扫描”；
  点开显示原始 SKILL.md（含 YAML frontmatter）与“确定”，符合只读定位。
- 终端设置页列出 5 个配色（Ox · 炭黑底绿字 / 经典 / 琥珀 / 浅色 / Ubuntu）、
  4 个字号（12/14/16/18 sp）及“Shell 从共享存储启动…”说明；只查看未改动。
- 无 FATAL/ANR，`-gpu off` 稳定。记录见
  `docs/qa-skills-terminal-0.8.100.md`。本轮无代码改动，沿用 0.8.100 产物。

## 5.100 alpha 0.8.101–0.8.102 —— 本地群添加成员走本地分支（2026-10-26 13:10–13:35）

- 背景：在已有本地群里点“添加成员”会走 Telegram 的 `messages.addChatUser`，
  本地群没有对应的服务器会话，请求无法真正改变本地成员。0.8.101 在
  `MessagesController.addUsersToChat` 入口增加本地分支，交给
  `OxAgentsBridge.addLocalGroupMembers`：重建 ChatFull、只追加尚未加入的智能体、
  写 `OxGroupStore`、更新群成员数并持久化，最后发 `chatInfoDidLoad`。
- 0.8.101 实测发现两个问题，0.8.102 修复：
  - `chatInfoDidLoad` 第 4 个参数传 `null`，`TopicsFragment` 按 Boolean 拆箱时
    NPE；改为 `false`。
  - `ProfileActivity` 的 `onComplete` 会把 `onAddUser` 回报的用户再追加一次，
    与桥接层已更新的 ChatFull 重复，出现两行 Echo 和“4 位成员”；本地分支不再
    调用 `onAddUser`。
- 复测：添加 Echo Assistant 后群资料为“3 位成员, 3 人在线”，成员行
  BlanCat / Yuki / Echo 各一行；日志 `addLocalGroupMembers chat=1800000001
  count=3`，无 failed，无 `addChatUser` 请求。随后用备份把测试群恢复为仅 Yuki，
  冷启动后群聊头部与资料页均回到“2 位成员, 2 人在线”。
- 记录见 `OrionekoX/docs/qa-group-0.8.102.md`。

**产物**：`dist/OrionekoX-0.8.102-alpha-arm64-x64.apk`（versionCode 1179，sha256
`4924bdbad3c8…`）；debug `dist/OrionekoX-0.8.102-debug-arm64-x64.apk`（sha256
`60d873469fdc…`）。两个 APK 均已安装，并执行 `tools/push-latest-apk.sh`。

## 5.101 alpha 0.8.103 —— 本地群移除成员走本地分支（2026-10-26 13:36–13:53）

- `deleteParticipantFromChat` 现在对本地群的非本人 agent 成员提前返回，由
  `OxAgentsBridge.removeLocalGroupMember` 从 `OxGroupStore` 删除该 agent、重建并
  持久化 ChatFull，再发 `chatInfoDidLoad`；本人“离开群组”仍走 Telegram 原路径。
- 实测添加 Echo 后长按成员行，菜单含“从群组中移除”；移除后无需返回重进，资料页
  立即显示“2 位成员, 2 人在线”，列表只剩 BlanCat 与 Yuki；日志
  `removeLocalGroupMember ... count=2`，无 `deleteChatUser` 请求。
- 同时修正本地群头部刷新顺序：`updateListAnimated` 先重算在线数，再
  `updateProfileData(true)`，避免“2 位成员, 3 人在线”的旧缓存。
- 记录见 `OrionekoX/docs/qa-group-0.8.103.md`。

**产物**：`dist/OrionekoX-0.8.103-alpha-arm64-x64.apk`（versionCode 1189，sha256
`f74e42248c96…`）；debug `dist/OrionekoX-0.8.103-debug-arm64-x64.apk`（sha256
`aa9987a9e28b…`）。

## 5.102 alpha 0.8.103 —— 公开仓库发布与三参考调研（2026-10-26 13:55–14:20）

- 创建公开仓库 <https://github.com/BlanCatOra/OrionekoX>，默认分支 `main`，
  保留 125 个提交的迭代历史。公开镜像在独立副本中完成脱敏：移除
  `release.keystore` 与全部 `google-services.json`，把签名密码替换为
  `CHANGE_ME`，将早期 `OrionekoX Builder` 提交身份统一改写为
  `blancatora <blancatora@agent.qq.com>`；上游提交作者保持不变。
- 按 DSH 规范补齐 `README.md`/`README.en.md`（顶部 AI 声明、真实截图、功能表、
  构建说明）、`AGENTS.md`、`llms.txt`、`NOTICE`、`docs/RELEASING.md`，并加入
  Kelivo/RikkaHub/Operit 兼容与美化分析 `docs/reference-gap-analysis.md`。
- 公开 clone 构建：`TMessagesProj_AppStandalone/build.gradle` 在缺少私有 keystore
  时回退 debug 签名、缺少 `google-services.json` 时跳过 Firebase 插件；子模块
  说明已写入 README。本地 0.8.103 构建通过；公开镜像原生编译在 `/tmp` 16G
  tmpfs 配额耗尽处停止（环境问题，非仓库配置）。
- 已生成三份“酒吧炒饭”独立测试 skill（A 建档/记忆、B 群聊/媒体、C 设置/终端），
  子代理 A 已启动；B、C 将按 A 报告完成后顺序启动。
- Kelivo 已核对：`mcpServers` JSON（stdio/http/sse）、`SKILL.md`/zip 技能、
  主题 JSON（`primaryColorArgb`，兼容 RikkaHub）、备份类别与 overwrite/merge
  模式；RikkaHub 已确认 SillyTavern 角色卡、Provider QR、MCP/Skills、Material 3
  Expressive。具体导入实现待三份测试报告后按优先级落地。

## 5.103 alpha 0.8.104 —— 三份酒吧炒饭报告后的 P1 修复（2026-10-26 18:25–18:50）

- 三份独立测试报告已归档：A `bar-rice-test-A.md`（会话空白、群丢成员、导入身份、
  文件式图片）、B `bar-rice-test-B.md`（群 @ 抢答、群图片红叹号、会话空白）、
  C `bar-rice-test-C.md`（新建 Agent 入群丢失、群分发 agents=1、会话空白/开始Bot、
  OxLinux 引导布局）。
- 0.8.104 修复并验证：
  - `MessagesController.createChat` 构建 `req.users` 前先 `syncAgents(false)`，刚复制
    的 Agent 不再因用户缓存缺失被静默跳过；新建群 `handleCreateChat users=2 agents=2
    real=0`、`createLocalGroup id=1800000005 agents=2`、3 位成员。
  - `onGroupMessage` 先解析 `@agent_<id>` 再匹配显示名；`@agent_17`/`@agent_19`
    分别由正确的 Agent 回复。
  - `SendMessagesHelper` 把本地群媒体交给 `handleOutgoingGroupMedia`，以 `peerChat`
    持久化照片/文档并触发群回复；实测 `mediaClass=TL_messageMediaPhoto`、无红叹号。
  - `refreshDialogRow` 改为按 `date DESC, mid DESC LIMIT 1` 取最新消息；修复会话
    重开空白；`ChatActivity` 对本地 Agent 隐藏“开始Bot”按钮。
  - `.oxagent` 导入清空 `tg_id`、生成新 id、同名加“副本”并 `syncAgents`；导出文件名
    保留中文。
  - 设置页底部增加 64dp padding，代理与 VPN/版本脚注不再被底部 Tab 遮挡。
  - 中文包 65：文件类型“%s 文件”、翻译说明改写。
  - 已加入 Markdown `**bold**`/`code`/`##` 到原生 entities 的转换（模型未配合输出
    标记，运行期渲染仍需固定样例复测）。
- 仍需后续：群内图片视觉、复制后欢迎语旧名、OxLinux 引导布局、MCP/技能/主题导入。
- 记录见 `OrionekoX/docs/qa-0.8.104.md`。

**产物**：`dist/OrionekoX-0.8.104-alpha-arm64-x64.apk`（versionCode 1199，sha256
`e5c69cf1d3bd…`）；debug `dist/OrionekoX-0.8.104-debug-arm64-x64.apk`（sha256
`6b0a86c68e54…`）。

## 5.104 alpha 0.8.105 —— MCP mcpServers JSON 导入（2026-10-26 18:55–19:10）

- 按 Kelivo/RikkaHub/Claude Desktop 通用的 `mcpServers` 结构，在 Ox 设置 → MCP 服务
  增加“从 JSON 导入”：支持 `url`/`baseUrl`、`headers`、`disabled`/`isActive`，
  按 URL 去重；stdio 计入“暂不支持”，SSE 跳过（运行时仅 Streamable HTTP）。
- 新字符串已加中英文资源，中文包 66；实测入口、原生导入对话框与多行输入框正常。
- 受模拟器限制：`adb shell input text` 会吞掉双引号，无法端到端粘贴合法 JSON；
  导入计数未在设备上观察，已在 QA 文档如实标注，下一轮补固定 fixture。
- 记录见 `OrionekoX/docs/qa-0.8.105.md`。

**产物**：`dist/OrionekoX-0.8.105-alpha-arm64-x64.apk`（versionCode 1209，sha256
`1c8c656c65f2…`）；debug `dist/OrionekoX-0.8.105-debug-arm64-x64.apk`（sha256
`991d7c35394c…`）。

## 5.105 alpha 0.8.106 —— SKILL.md / zip 技能导入（2026-10-26 19:00–19:12）

- 技能库新增“导入 SKILL.md / zip”：系统文档选择器、应用私有
  `files/oxskills/<id>/SKILL.md`、无需 MANAGE_EXTERNAL_STORAGE；普通 Markdown 单文件
  导入，zip 扫描最多 200 项/50 个 SKILL.md；2MB/512KB 上限，不使用归档路径落盘。
- `OxSkillStore.list()` 先扫描私有技能目录，再扫 Operit/Operit2 Downloads；导入后
  `UniversalAdapter.update(true)` 刷新列表。
- 实测推送 `orioneko-test-skill.md`（中文 frontmatter）→ 技能库导入 → 列表出现
  “测试导入技能” → 原生预览显示 frontmatter/正文；示例技能仍在。zip 路径未实测；
  按 Agent 启用/注入未重跑。
- 记录见 `OrionekoX/docs/qa-0.8.106.md`。

**产物**：`dist/OrionekoX-0.8.106-alpha-arm64-x64.apk`（versionCode 1219，sha256
`ca292e81d9c4…`）；debug `dist/OrionekoX-0.8.106-debug-arm64-x64.apk`（sha256
`209af54cc9c3…`）。

## 5.106 alpha 0.8.107 —— Kelivo/RikkaHub 主题 JSON 导入与 Ox 外观（2026-10-26 19:12–19:20）

- Ox 设置新增“外观”页：当前强调色、从 JSON 导入（Kelivo/RikkaHub
  `primaryColorArgb`）、6 个预设、恢复 Telegram 默认；全部复用
  UniversalRecyclerView/SettingCell/原生 ActionBar。
- `OxTheme` 解析数字或 `#RRGGBB`/`#AARRGGBB`，保存 primary/secondary/tertiary 与名称；
  强调色只作用于 Ox 自有界面（默认终端配色的 prompt/send/keyboard、外观入口图标），
  不修改 Telegram 原生主题。
- 实测导入 `Kelivo 紫` 显示 `#6750A4`，重置恢复“Telegram 默认强调色”；中文包 68。
- 局限：不重绘 Telegram 气泡/按钮；当前终端为非默认配色，未在该配色上覆盖自定义色。
- 记录见 `OrionekoX/docs/qa-0.8.107.md`。

**产物**：`dist/OrionekoX-0.8.107-alpha-arm64-x64.apk`（versionCode 1229，sha256
`167e8f7566a6…`）；debug `dist/OrionekoX-0.8.107-debug-arm64-x64.apk`（sha256
`6ba0937521be…`）。

## 5.107 alpha 0.8.108 —— OxLinux 引导页布局修复（2026-10-26 19:22–19:45）

- C 报告中的“第 6 步整体右移、左侧露出第 5 步、标题裁切、已登录仍显示登录 Telegram”
  复现成功：ViewPager 每页被额外平移 345px（`translationX=345`），滚动本身正确。
- 修复：页面变换去掉横向 translation（只保留 alpha），start page 对齐时清空子页
  translation 并强制 `scrollTo(target * width, 0)`；登录后最后一步主按钮改为“开始
  使用 OrionekoX”，未登录登录模式仍显示“登录 Telegram”。
- 实测第 6 步标题完整居中、卡片全宽、无第 5 步残影；按钮文案正确；无 FATAL/ANR。
- 记录见 `OrionekoX/docs/qa-0.8.108.md`。

**产物**：`dist/OrionekoX-0.8.108-alpha-arm64-x64.apk`（versionCode 1239，sha256
`d97bc5b0c44a…`）；debug `dist/OrionekoX-0.8.108-debug-arm64-x64.apk`（sha256
`5b86c98f6575…`）。

## 5.108 alpha 0.8.109 —— 本地群图片视觉（2026-10-26 19:50–20:06）

- `handleOutgoingGroupMedia` 保留首张图片路径并传入群回合；`groupMessages` 对带图
  用户消息使用 `visionContent`（OpenAI-compatible image_url），与单聊照片一致。
- `prepareSendingDocuments` 对本地群补分流：从“文件”入口选 JPEG 也走本地照片 +
  视觉，而不是落服务器管线产生红叹号。
- 实测 3 人群发送“GROUP VISION 42”测试图：照片气泡有缩略图、双勾、无红叹号；
  Yuki 准确描述“深蓝底、黄色矩形边框、白色小字 GROUP VISION 42”。
- 限制：多图只取首张入视觉；既有失败文档气泡仍是历史红标。记录见
  `OrionekoX/docs/qa-0.8.109.md`。

**产物**：`dist/OrionekoX-0.8.109-alpha-arm64-x64.apk`（versionCode 1249，sha256
`ce3677908450…`）；debug `dist/OrionekoX-0.8.109-debug-arm64-x64.apk`（sha256
`596da3cb2840…`）。

## 5.109 alpha 0.8.110 —— 改名同步欢迎语（2026-10-26 20:10–20:19）

- 复制/重命名 Agent 后，旧欢迎语仍显示“Yuki Waifu · 副本”。
- `OxAgentProfileActivity` 保存前记录旧名；`OxAgentsBridge.onAgentRenamed` 在存储队列
  读取该 Agent 最近 200 条消息，反序列化后把以“你好，我是 ”开头的助手气泡改写为
  新名字+emoji，`putMessages` 覆盖并通过 `replaceMessagesObjects` 刷新。
- 实测把“Yuki Waifu · 副本”改为 `RenamedCopy`：日志
  `onAgentRenamed updated greetings did=555000019 count=1`，会话气泡显示
  “你好，我是 🤖 RenamedCopy”，其他消息不变；无 FATAL/ANR。
- 记录见 `OrionekoX/docs/qa-0.8.110.md`。

**产物**：`dist/OrionekoX-0.8.110-alpha-arm64-x64.apk`（versionCode 1259，sha256
`0dccf6a2aeb8…`）；debug `dist/OrionekoX-0.8.110-debug-arm64-x64.apk`（sha256
`95ebbe08db27…`）。

## 5.110 alpha 0.8.111 —— MCP 文件导入与技能 zip 端到端验证（2026-10-26 20:24–20:35）

- MCP “从 JSON 导入”增加“从文件”中性按钮：系统文档选择器读取 ≤256KB UTF-8 JSON，
  复用粘贴解析。实测 `kelivo-mcp.json`（1 个 HTTP + headers、1 个 stdio）：
  `files/oxmcp/servers.json` 写入 TestImport 及其 headers，stdio 被跳过，列表刷新显示
  该服务器；长按删除后恢复原状。
- 技能库 zip 路径端到端验证：`zip-skill.zip` 内 `ZipSkill/SKILL.md` 导入后列出
  “Zip 导入技能”，私有目录存在 `files/oxskills/ZipSkill/SKILL.md`。
- 无 FATAL/ANR；MCP 测试服务器已删除，测试技能保留为合成产物。
- 记录见 `OrionekoX/docs/qa-0.8.111.md`。

**产物**：`dist/OrionekoX-0.8.111-alpha-arm64-x64.apk`（versionCode 1269，sha256
`b683cbbc8ebb…`）；debug `dist/OrionekoX-0.8.111-debug-arm64-x64.apk`（sha256
`02c832ec05c1…`）。

## 5.111 alpha 0.8.112 —— MCP 导入预览与幂等复测（2026-10-26 20:40–20:47）

- MCP JSON 导入先解析成计划：唯一 HTTP/streamable 服务器列出，重复 URL/skip、stdio
  单独计数；原生 Dialog 预览“将导入 N 个、跳过 M 个”+前 8 个名称/URL，确认后才写入，
  取消不改动。
- 实测 `kelivo-mcp.json`：预览 1 个导入、1 个 stdio 跳过，确认后仅一条 TestImport
  （headers 保留）；再次导入同一文件无预览、servers.json 仍只有一条。测试服务器已删除。
- 记录见 `OrionekoX/docs/qa-0.8.112.md`。

**产物**：`dist/OrionekoX-0.8.112-alpha-arm64-x64.apk`（versionCode 1279，sha256
`bf1007887d1b…`）；debug `dist/OrionekoX-0.8.112-debug-arm64-x64.apk`（sha256
`27ce6578064d…`）。

## 5.112 alpha 0.8.113 —— 工具权限恢复默认（2026-10-26 20:45–20:58）

- 工具行保留点击切换 自动/询问/禁止；新增长按删除该 Agent 的覆盖、恢复工具默认，
  并加页脚提示。实测 `get_time` 从“自动”长按后变为“默认 · 自动”，提示“已恢复默认策略”。
- Markdown 运行期仍无法确定性验证：输入层的 `**bold**` 在发送前被消化，模型也不主动
  输出标记；助理侧 `applyMarkdown` 仅完成代码审查，未宣称视觉通过。
- 中文包 71；无 FATAL/ANR。记录见 `OrionekoX/docs/qa-0.8.113.md`。

**产物**：`dist/OrionekoX-0.8.113-alpha-arm64-x64.apk`（versionCode 1289，sha256
`50a55e0ef61c…`）；debug `dist/OrionekoX-0.8.113-debug-arm64-x64.apk`（sha256
`bab445258e90…`）。

## 5.113 alpha 0.8.114 —— SillyTavern JSON 角色卡导入（2026-10-26 21:00–21:10）

- Agent 列表新增“导入角色卡（SillyTavern JSON）”：解析 `data` 包装或旧版扁平对象，
  把 description/personality/scenario/system_prompt/post_history_instructions/
  mes_example/first_mes 映射进 persona；新 id/tg_id，同名加“副本”。
- Provider 继承优先选择 URL 与 Key 都存在的 Agent，否则退回任意有 URL 的 Agent。
  实测第二张卡继承 `opendesign / deepseek-v4.1-flash /
  https://amr-link.open-design.ai/v1`，key 非空。
- 仅支持 JSON；PNG 角色卡未解析；`first_mes` 作为 persona 的“First message”段落，
  原生欢迎语仍为通用文案。测试卡片 Agent 保留。记录见 `docs/qa-0.8.114.md`。

**产物**：`dist/OrionekoX-0.8.114-alpha-arm64-x64.apk`（versionCode 1299，sha256
`150b9e934495…`）；debug `dist/OrionekoX-0.8.114-debug-arm64-x64.apk`（sha256
`71579abbcbe7…`）。

## 5.114 alpha 0.8.115 —— SillyTavern PNG 角色卡导入（2026-10-26 21:12–21:20）

- 角色卡导入扩展 PNG：遍历 PNG chunk，读取 `tEXt` 中的 `chara`/`ccv3`，Base64 解码后
  复用 JSON 映射；普通 JSON 卡片保持兼容。
- 实测生成的 `png-card.png`（内嵌 “PNG Card” 卡片）：导入后 Agent 人设字段正确，
  并继承 OpenDesign/deepseek-v4.1-flash 与非空 Key。
- 限制：仅未压缩 `tEXt`，`zTXt`/`iTXt` 未处理；PNG 图片本身未用作头像。记录见
  `docs/qa-0.8.115.md`。

**产物**：`dist/OrionekoX-0.8.115-alpha-arm64-x64.apk`（versionCode 1309，sha256
`ae6ed77bd1bf…`）；debug `dist/OrionekoX-0.8.115-debug-arm64-x64.apk`（sha256
`e8dedd2ae5f0…`）。

## 5.115 alpha 0.8.115 —— Markdown 运行期确定性验证（2026-10-26 21:22–21:25）

- 模型不主动输出 Markdown、输入层又会吞掉用户输入的 `**`，此前无法验证。
- 改用合法路径：导入名为 `**Bold** Card` 的 SillyTavern 角色卡，欢迎语由 Agent 名字
  生成并经 `injectAgentReply → applyMarkdown`。
- 聊天标题仍显示字面 `**Bold** Card`（标题不走 Markdown）；欢迎语气泡显示
  “你好，我是 🤖 Bold Card。直接发消息即可开始对话。”，其中 Bold 为粗体、星号已移除。
- 说明 `applyMarkdown` 的助理消息路径运行期可用；无需改代码。记录见
  `docs/qa-0.8.115-markdown.md`。

## 5.116 alpha 0.9.0 —— Agent 会话外观双模式与版本规则修正（2026-10-26 22:10–23:30）

- 版本规则修正：较大功能迭代进位 minor，本轮从 0.8.x 升至 **0.9.0**（versionCode
  1329）；patch 只用于小修/QA，major 用于里程碑。规则写入 `AGENTS.md` 与
  `docs/RELEASING.md`。
- 新增 Ox 设置→外观→Agent 会话外观：自定义/Telegram 原生双模式；自定义背景色、
  背景图片、收/发气泡色、透明度、圆角、消息字号；命名预设保存/应用/删除；JSON
  导入；一键重置原生。`OxChatTheme` 仅在本地 Agent 会话打开期间覆盖 Telegram 颜色并
  在 onPause 恢复；`MessageDrawable` 增加圆角覆盖。
- 实测自定义：背景 #101114、收 #E8F5E9、发 #EFFDDE、86% 透明、10dp 圆角、1.1x
  字号，Agent 会话截图确认生效；预设 DarkSea 保存成功。UI 重置后路径与 JSON 导入
  交由第二轮 B 复测。
- 竞品功能矩阵与差距写入 `docs/beautification-parity.md`：已覆盖背景/气泡/透明度/
  圆角/字号/预设/颜色导入；磨砂、边框、渐变、字体家族、按 Agent 持久化等列为后续。
- 第二轮 A 报告（0.8.115）完成：全流程与六项回归通过，无 P0/P1，P2 历史偶发空白/
  工具气泡 JSON/发送按钮命中区/.oxagent 副本无 Key；已归档。

**产物**：`dist/OrionekoX-0.9.0-alpha-arm64-x64.apk`（versionCode 1329，sha256
`617cff8bf2c9…`）；debug `dist/OrionekoX-0.9.0-debug-arm64-x64.apk`（sha256
`456f0ec5315f…`）。

## 5.117 alpha 0.9.1 —— 第二轮 A/B/C 回归修补（2026-10-27 03:30–04:10）

- 归档第二轮 B、C 独立报告与问题汇总：B 5 PASS / 1 PARTIAL / 1 FAIL，C 外观专项 6/7 通过
  但报告 2 项 P1；主代理对两项 P1 做受控复验，均未复现（图片是相册网格选错项；列表点击
  用 uiautomator bounds 中心/下缘均正确），按测试/模拟器因素记录。
- 修复 Agent 会话重开空白：忽略旧 `diditem` 滚动位置；首个 cache pass 为空时 350ms 后
  有界重试；Agent 会话不再显示“删除此对话”底部遮罩。3 次返回重进均立即显示历史。
- 文件入口 JPG/PNG 改走图片气泡：实测 `verify-rice.jpg` 有缩略图、双勾，模型准确读出
  `VERIFYRICE0.9.1`。
- 主题导入补 snake_case 别名：`in_bubble`/`out_bubble`/`corner_radius`/`font_size` 等
  六项全部映射（实测 T991 主题）。
- 另有：工具气泡摘要、`\n` 反转义、SillyTavern `first_mes` 开场白、技能同名覆盖/长按删除、
  本地 Agent 子串搜索、`.oxagent` 无 Key 提示、预设 toast、MCP 重复标题、成员移除文案、
  群内 @ 用户名提示、群内“描述上一张图”自动附带最近图片。
- 产物：`dist/OrionekoX-0.9.1-alpha-arm64-x64.apk`（versionCode 1339，sha256
  `e22563e1c6e3…`）；debug `dist/OrionekoX-0.9.1-debug-arm64-x64.apk`（sha256
  `15c1cc7a73eb…`）。详见 `docs/qa-0.9.1.md`。

## 5.118 alpha 0.10.0 —— 按 Agent 主题、边框/字体/渐变与导出预览（2026-10-27 04:00–05:20）

- 外观模型升级为快照（`Snapshot`）：全局 SharedPreferences + 按 Agent 的
  `files/ox_chat_agents/agent_<tgId>.json`；智能体长按菜单新增「Agent 会话外观」，
  支持「应用到全部 Agent」，未覆盖时回退全局。实测 C2 浅色/白气泡与 R2C Card 全局
  深色并存，隔离生效。
- 背景支持纯色 / 双色渐变（3 方向）/ 图片，独立背景不透明度与暗化；状态栏/导航栏
  跟随主题。新增 `ThemeDelegate.setOxWallpaperDrawable` 直接替换聊天壁纸 drawable。
- 气泡支持实心/磨砂、边框颜色与 1–3dp 宽度、独立文字颜色；新增 WCAG 对比度提示。
- 字体支持聊天/代码字体（系统/衬线/等宽/导入 TTF）与 0.9–1.3× 字号；导入字体校验后
  存入 `files/oxfonts/`。
- 导入改为主线程摘要预览，确认后才写入；新增当前主题 JSON 导出（SAF）；原生模式新增
  「打开 Telegram 聊天主题」入口。
- 设备验证：按 Agent 隔离、边框/磨砂/衬线、原生入口与重置均通过；`-gpu off` 模拟器
  下 C2 浅色背景仍隐约透出 emoji 壁纸纹理（合成限制），列入真机复验。
- 产物：`dist/OrionekoX-0.10.0-alpha-arm64-x64.apk`（versionCode 1409，sha256
  `4a08b638e9c5…`）；debug `dist/OrionekoX-0.10.0-debug-arm64-x64.apk`（sha256
  `19faa4252589…`）。详见 `docs/qa-0.10.0.md`。

## 5.119 alpha 0.11.0 —— 主题资产包 `.oxtheme` 导出/导入（2026-10-27 20:00–20:12）

- 背景图的“只导颜色、丢资源”问题：新增 zip 主题包，内含 `theme.json`、
  `background.img`、`chat-font.ttf`/`code-font.ttf`；导入时先恢复资产到
  app-private 目录再重写快照路径，支持全局与按 Agent 作用域。
- 导入预览会显示“含背景图 / 含字体”；非法 JSON/zip 不写入现有主题。
- 设备实测：导入 Adwaita Sans（`chat_1793102842095.ttf`）、设置
  `verify-rice.jpg` 背景 → 导出 464623 B 的 `.oxtheme`（theme.json 741 B、
  background.img 16360 B、chat-font.ttf 879796 B）→ 切回系统字体 → 导入同一包，
  预览显示资产信息，应用后字体与背景均恢复为包内副本。
- 产物：`dist/OrionekoX-0.11.0-alpha-arm64-x64.apk`（versionCode 1419，sha256
  `05b3fd0c6418…`）；debug `dist/OrionekoX-0.11.0-debug-arm64-x64.apk`（sha256
  `48b3d668860d…`）。详见 `docs/qa-0.11.0.md`。

## 5.120 alpha 0.12.0 —— 内置主题一键套用与文字色修复（2026-10-27 20:15–20:32）

- 外观页新增六款内置主题（午夜墨黑/纸墨/深海/樱花/森林/赛博霓虹），
  一键写入完整快照到当前作用域；解决竞品“预设市场/一键套用”缺口。
- 修复自定义文字色无效：`ChatMessageCell` 绘制时用主题色覆盖
  `chat_msgTextPaint`；现在 `ChatActivity.ThemeDelegate.getColor()` 对
  `key_chat_messageTextIn/Out` 优先返回 `oxTextColor`，应用/恢复时成对设置/清空。
- 实测赛博霓虹：渐变 #0A0014→#1B0033、收 #1B0033、发 #7C00FF、文字 #00F0FF、
  边框 #FF2D95 2dp、圆角 8dp；修复前文字显示黑色，修复后为青色。AMOLED 主题
  纯黑背景 + 白字 + #333 边框正常。C2 的旧 Agent 覆盖仍优先于全局主题。
- 产物：`dist/OrionekoX-0.12.0-alpha-arm64-x64.apk`（versionCode 1429，sha256
  `b26932a9e6a9…`）；debug `dist/OrionekoX-0.12.0-debug-arm64-x64.apk`（sha256
  `83783c9dd30f…`）。详见 `docs/qa-0.12.0.md`。

## 5.121 alpha 0.13.0 —— 从背景自动配色（2026-10-27 20:38–20:47）

- 新增“壁纸气泡联动/动态取色”：背景图降采样到 ≤64px 求平均 RGB，渐变取两色
  50% 混合；由平均色生成高饱和发气泡、浅色收气泡、自动对比文字与细边框，
  暗背景自动加 60/255 暗化。
- 实测深蓝 `verify-rice.jpg`：生成收 `#DCDDE5`、发 `#A3A6C2`、边框 `#BDBFD3`，
  文字深色可读，背景图保留；应用至 R2C Card 正常，无 FATAL/ANR。
- 版本：0.12.0 → 0.13.0（minor，新功能）。
- 产物：`dist/OrionekoX-0.13.0-alpha-arm64-x64.apk`（versionCode 1439，sha256
  `08a983b1e39a…`）；debug `dist/OrionekoX-0.13.0-debug-arm64-x64.apk`（sha256
  `14ba073efeb6…`）。详见 `docs/qa-0.13.0.md`。

## 5.122 alpha 0.14.0 —— Operit 外观偏好 JSON 导入（2026-10-27 20:50–21:02）

- `OxChatTheme.parseJsonObject` 解包 Operit 备份的嵌套 `values`，映射 AI/用户气泡色、
  AI/用户文字色、`font_scale`、`background_image_opacity`、`theme_mode`、玻璃/模糊
  开关与 `custom_primary_color`（边框）；沿用预览→确认→写入链路。
- 设备：`operit-appearance.json` 预览为 `收 #2D2A45 · 发 #7C5CFF · 80% · 16dp ·
  1.1x · 磨砂 · 边框 #FF2D95 1dp`；应用后外观页逐项一致，Agent 会话显示紫色气泡、
  白字、洋红细边框，背景图继续沿用。
- 导入按“当前快照 + 命中字段覆盖”合并，预览会显示继承的背景图/字体；未覆盖 Operit
  9-patch、头像/字体 URI、内边距与图片裁剪。兼容性上只在命中 Operit 专有键时进入该
  分支，缺少 `theme_mode` 不改背景基色，避免误伤 Kelivo/RikkaHub JSON。
- 已知缺口：工具调用状态气泡仍为原生白色，未随自定义气泡着色（0.14.1 修复）。
  无 FATAL/ANR。

**产物**：`dist/OrionekoX-0.14.0-alpha-arm64-x64.apk`（versionCode 1449，sha256
`5c7a27b98bfa…`）；debug `dist/OrionekoX-0.14.0-debug-arm64-x64.apk`（sha256
`2807a1dc3197…`）。详见 `docs/qa-0.14.0.md`。

## 5.123 alpha 0.14.1 —— 工具/媒体气泡随主题着色（2026-10-27 21:18–21:26）

- 0.14.0 只覆盖 `chat_msgIn/OutDrawable` 与选中文字气泡；工具调用提示气泡走媒体
  drawable，保持原生白色，而自定义文字色为白色时正文不可见。
- 抽出 `applyBubbleOverride` / `clearBubbleOverride`，统一覆盖并恢复全部 8 个气泡
  drawable（in/out、selected、media、mediaSelected），颜色、圆角、边框共用同一
  快照；退出 Agent 会话时全部还原。
- 设备：`**Bold** Card` 的两条历史工具提示 `🔧 device_info({}) …`、`get_time({}) …`
  由白底白字变为深紫 `#2D2A45` + 洋红 `#FF2D95` 1dp 边框，白字完整可读；主回复与
  发气泡不变，无需发送新消息。无 FATAL/ANR。
- 版本规则：单一视觉缺陷修复，按 patch 从 0.14.0 → 0.14.1。

**产物**：`dist/OrionekoX-0.14.1-alpha-arm64-x64.apk`（versionCode 1459，sha256
`9030915746d2…`）；debug `dist/OrionekoX-0.14.1-debug-arm64-x64.apk`（sha256
`fc0758b06bc9…`）。详见 `docs/qa-0.14.1.md`。

## 5.124 alpha 0.15.0 —— 消息行高（2026-10-27 21:34–21:56）

- `OxChatTheme.Snapshot` 新增 `lineHeight`（默认 1.0，导入允许 0.8–1.8）；外观页
  字号下方新增“消息行高”，循环 1.0–1.5×；全局、按 Agent、预设、JSON 与 `.oxtheme`
  全部持久化。
- 运行期 `lineHeightFor(dialogId)` 仅在当前自定义主题 Agent 会话返回倍率，
  `MessageObject.TextLayoutBlocks` 的正文/引用/代码块统一使用；退出会话恢复 1.0。
- 设备：设为 1.4× 后重进 `**Bold** Card`，长 Markdown 段落行距明显增大、气泡变高，
  工具气泡/发气泡/背景无回归；导出新 `.oxtheme` 的 `theme.json` 含
  `lineHeight=1.4`，导入预览显示“行高 1.4x · 含背景图”，取消不写入。
- 版本规则：新增可调外观能力，按 minor 从 0.14.1 → 0.15.0。

**产物**：`dist/OrionekoX-0.15.0-alpha-arm64-x64.apk`（versionCode 1509，sha256
`c9b84a56eb8b…`）；debug `dist/OrionekoX-0.15.0-debug-arm64-x64.apk`（sha256
`00619f056f8a…`）。详见 `docs/qa-0.15.0.md`。

## 5.125 alpha 0.16.0 —— 按会话主题（2026-10-27 22:00–22:18）

- 新增会话作用域 `files/ox_chat_dialogs/dialog_<dialogId>.json`，读取优先级为
  会话覆盖 → Agent 覆盖 → 全局；聊天页右上角新增“会话外观”图标（本地 Agent 会话
  与本地群），进入带标题的作用域页，清除按钮显示“清除覆盖并跟随上层”。
- 设备（Agent 路径）：`**Bold** Card` 会话内改收/发气泡与文字色，聊天立即使用
  会话配色；`R2C Card` 仍为全局深色，隔离成立；`am force-stop` 重启后覆盖仍在；
  清除后恢复全局 `#2D2A45`/白字。
- 本地群入口通过 `OxGroupStore.isLocalGroup` 接入，但模拟器两次在打开本地群时
  触发 Chromium/Vulkan 导致 qemu 整机掉线（`-gpu off` 已知限制），群路径只完成
  代码审查，未做设备截图，待真机/GPU 复验。
- 已知边界：会话继承的文字色不会随浅色气泡自动调整，页面内对比度检查可发现。
- 版本规则：新增结构级外观作用域与入口，按 minor 从 0.15.0 → 0.16.0。

**产物**：`dist/OrionekoX-0.16.0-alpha-arm64-x64.apk`（versionCode 1609，sha256
`3e3880ecca14…`）；debug `dist/OrionekoX-0.16.0-debug-arm64-x64.apk`（sha256
`9ac4028fb6a7…`）。详见 `docs/qa-0.16.0.md`。

## 5.126 alpha 0.17.1 —— 真实背景模糊与按会话应用修正（2026-10-27 22:25–23:20）

- 0.17.0 实现图片背景真实模糊：`backgroundBlur` 0–3，`stackBlurBitmapWithScaleFactor`
  按 3×/5×/8× 降采样，结果按路径 + mtime + 级别缓存；外观页新增“背景模糊”，
  JSON/prefs/预设/`.oxtheme` 与导入别名同步；纯色/渐变不受影响。
- 设备测量：主题会话右侧壁纸条带灰度标准差由 29.54（关）降到 0.49–0.54（强），
  边缘能量 2.1 → 0.03；见 `docs/screenshots/qa-0.17.0-blur-margin-compare.png`。
- 0.17.1 修复冷启动首次进入会话时 `getChatListView()` 尚不存在导致主题/模糊
  被跳过：`onResume` 增加 500 ms 重试；`applyToChat` 放行
  `OxGroupStore.isLocalGroup`，本地群也可使用按会话外观；会话/Agent 作用域
  的清除提示改为“已清除覆盖，现跟随上层外观”。
- 设备：0.17.1 安装后底部为 `v0.17.1 (171)`；冷启动首次打开 R2B Agent 会话
  立即显示 1.15× 字号、1.4× 行高、深色气泡、洋红边框与模糊背景；首次打开
  R2C 本地群消息完整渲染并应用会话主题，外观页显示“背景模糊 强”，
  `dialog_-1800000008.json` 含 `backgroundBlur=3`；群作用域写入/清除后
  `dialog_-1800000007.json` 被删除，提示文案正确。
- 环境：本次 QA 期间 `-gpu off` 多次在打开会话时令 qemu `RenderThread`
  SIGSEGV（非应用 FATAL/ANR）；禁用 WebView/Chrome 与 `-feature -Vulkan`
  无效，最终用 `-gpu swiftshader_indirect` / `-gpu guest` 完成群会话验证，
  偏离原因与待复验项已写入 QA。代理曾在应用内被打开导致本地群不加载消息，
  已通过设置关闭。未向真实联系人发消息，未清理用户数据。
- 版本规则：0.17.0 新增能力（minor）；0.17.1 仅修复首次应用/群作用域/文案
  （patch，versionCode 1719）。

**产物**：`dist/OrionekoX-0.17.1-alpha-arm64-x64.apk`（versionCode 1719，sha256
`ac1f54faaa92…`）；debug `dist/OrionekoX-0.17.1-debug-arm64-x64.apk`（sha256
`a4630952a316…`）。0.17.0 产物保留在 `dist/`。详见 `docs/qa-0.17.1.md`。

## 5.127 alpha 0.18.0 —— 主题预设包 .oxthemes（2026-10-27 23:25–23:58）

- 新增 `.oxthemes` 预设包：zip 内 `bundle.json`（格式/版本/预设数组）加每个
  预设的背景图、聊天字体、代码字体资产；导出全部命名预设，导入前预览数量、
  名称、同名跳过数与资产数。
- 导入整包先校验再写盘：最多 100 个预设、单资产 8 MB、总资产 40 MB、zip 条目
  300；同名预设跳过不覆盖；资产恢复到 app-private 新文件并改写快照路径；
  写入失败删除本次创建的资产与预设文件。非法/损坏包直接拒绝且不写入。
- 设备：`DarkSea`/`R2C-Image` 导出 506 B 包，重复导入提示 0/2，文件不变；
  新建含背景图的 `BundleQA` 导出 4713 B（含 16360 B `background.img`），删除后
  再导入 1/2，`BundleQA.json` 指向新资产，应用预设后背景正常；13 B 纯文本
  `.oxthemes` 被拒绝，预设与资产均未变化。无 FATAL/ANR。
- 修复中文包版本遗漏：修改字符串后未再提高 `OX_ZH_PACK_VERSION`，首轮安装显示
  旧文案/英文；包版本 85→86 重建后，导入/导出提示、预览标题与确认按钮均为中文。
- 版本规则：新增可携带资产的多预设包能力，按 minor 从 0.17.1 → 0.18.0，
  versionCode 1809。

**产物**：`dist/OrionekoX-0.18.0-alpha-arm64-x64.apk`（versionCode 1809，sha256
`d9dbb96c518f…`）；debug `dist/OrionekoX-0.18.0-debug-arm64-x64.apk`（sha256
`6cd3a82e130d…`）。详见 `docs/qa-0.18.0.md`。

## 5.128 alpha 0.19.0 —— RikkaHub 备份 settings.json 导入（2026-10-28 00:10–00:35）

- Ox 设置新增“从 RikkaHub 备份导入”：SAF 选择备份 zip 或 `settings.json`，
  预览助手数、实际引用的 Provider 数、可导入 MCP 数、已存在数量和前 8 个助手名。
- 映射 RikkaHub 的 `providers[]`（openai/google/claude，baseUrl/apiKey/models）、
  `assistants[]`（systemPrompt、chatModelId、emoji 头像、temperature、topP、
  maxTokens、reasoningLevel）和 `mcpServers[]`（streamable_http URL/name/
  enable/headers）。聊天数据库、附件、技能目录、OAuth token 与 SSE MCP 不导入。
- 幂等：Agent id 固定为 `rikka_<assistant UUID>`，重复导入更新而不是新建；
  MCP 按 URL 去重；更新时保留 `tg_id` 与自定义头像。Provider 解析顺序为
  助手模型 → 全局 `chatModelId` → 第一个有 Key 的启用 Provider。
- 设备（schema 样例 zip）：预览“助手 2 · Provider 1 · MCP 1”，SSE 跳过 1；
  导入“新建 2，更新 0，新增 MCP 1”；Agent JSON 的 model/context/maxTokens/
  temperature/topP/reasoning/emoji/tg_id 与备份一致，MCP headers 正确；
  再次导入“新建 0，更新 2，新增 MCP 0，跳过 1”，Agent 目录仍为 2 个；
  非 RikkaHub JSON 提示“不是有效的 RikkaHub 备份”且无写入。
- 安全：预览/结果不显示 API Key；`logcat` 检索测试 Key 命中 0；无 FATAL/ANR。
  测试后通过 UI 删除两个测试助手与测试 MCP，Downloads 临时文件已清理。
- 版本规则：新增外部备份格式的导入能力，按 minor 从 0.18.0 → 0.19.0，
  versionCode 1909。

**产物**：`dist/OrionekoX-0.19.0-alpha-arm64-x64.apk`（versionCode 1909，sha256
`e869a33fdcd6…`）；debug `dist/OrionekoX-0.19.0-debug-arm64-x64.apk`（sha256
`b5391295cc83…`）。详见 `docs/qa-0.19.0.md`。

## 5.129 alpha 0.20.0 —— RikkaHub 备份 skills/ 导入（2026-10-28 00:50–01:05）

- `OxRikkaImporter` 在同一备份 zip 内扫描 `skills/<name>/SKILL.md`，与
  `settings.json` 的助手/Provider/MCP 一起预览和导入；单技能上限 512 KB、
  最多 50 个、zip 条目仍限 1000。
- 技能 id 取 `skills/` 后相对目录（嵌套用 `_` 连接），经
  `OxSkillStore.skillId()` sanitize 后写入 `files/oxskills/<id>/SKILL.md`；
  同名更新，不生成副本，不删除本地其他技能。`OxSkillStore.importSkill`
  为新入口。
- 预览新增“技能 N 个”、前 8 个技能 id、已存在技能数；结果新增
  “导入技能 N”。直接选择 `settings.json` 时技能数为 0。
- 设备：含 `skills/TestSkill/SKILL.md` 的样例 zip 预览
  “助手 2 · Provider 1 · MCP 1 · 技能 1”；导入
  “新建 2，更新 0，新增 MCP 1，跳过 0，导入技能 1”，文件 152 B 且
  frontmatter 完整；重复导入“新建 0，更新 2，新增 0，跳过 1，导入技能 1”，
  仍只有 1 个 TestSkill 目录。无 Key 泄漏、无 FATAL/ANR。
- 清理：测试后删除两个测试助手、RikkaTestMCP、TestSkill 目录与 Downloads 样例。
- 版本规则：新增第三方备份技能包导入能力，按 minor 从 0.19.0 → 0.20.0，
  versionCode 2009。

**产物**：`dist/OrionekoX-0.20.0-alpha-arm64-x64.apk`（versionCode 2009，sha256
`cae7554226d5…`）；debug `dist/OrionekoX-0.20.0-debug-arm64-x64.apk`（sha256
`79f538d0d2d3…`）。详见 `docs/qa-0.20.0.md`。

## 5.130 alpha 0.21.0 —— 外观编辑器实时预览（2026-10-28 01:10–01:22）

- 自定义外观页新增“预览当前外观”：原生 AlertDialog 内用当前作用域快照绘制
  壁纸（含图片、暗化、背景模糊）、收/发示例气泡、圆角、边框、文字色、聊天
  字体、字号与行高；原生模式不显示入口。
- 新增 `OxChatTheme.currentBackgroundDrawable()` / `currentTypeface()`，预览
  与聊天页复用同一套背景/字体逻辑；磨砂气泡按聊天一致压到 200/255 透明度。
- 设备：全局图片背景主题下预览显示 `VERIFYRICE0.9.1` 背景、深色收气泡、
  紫色发气泡、洋红边框、1.15× 字号/1.4× 行高；把背景模糊从“关”切到“强”
  后再次预览，背景文字/边框明显抹平，证明实时读取当前快照；随后恢复模糊为
  “关”（`background_blur=0`）。无 FATAL/ANR。
- 边界：预览是轻量示例，不含媒体/工具气泡、引用、头像与真实换行；磨砂仍为
  透明度模拟；自定义 TTF 预览未单独截图。
- 版本规则：新增可见的编辑器能力，按 minor 从 0.20.0 → 0.21.0，
  versionCode 2109。

**产物**：`dist/OrionekoX-0.21.0-alpha-arm64-x64.apk`（versionCode 2109，sha256
`aa148b515f6c…`）；debug `dist/OrionekoX-0.21.0-debug-arm64-x64.apk`（sha256
`248150fa0f6c…`）。详见 `docs/qa-0.21.0.md`。

## 5.131 alpha 0.21.1 —— RikkaHub 技能分配与助手请求头（2026-10-28 01:25–01:35）

- RikkaHub 导入顺序改为先写技能库、再导入 Agent；按技能名称/id 建立映射，
  助手 `enabledSkills` 非空时匹配到的 id 写入 `agent.skillIds`，为空不覆盖。
- 助手 `customHeaders` 的 `{name,value}` 数组映射为 Agent
  `headers` 的 `[[name,value]]`；非空才写入，空数组保留原值。
- 设备：样例 `Rikka QA` 带 `enabledSkills=["Rikka Test Skill"]` 与
  `X-Rikka-Assistant: 1`；导入后 `agent.json` 为
  `skill_ids=["TestSkill"]`、`headers=[["X-Rikka-Assistant","1"]]`；
  重复导入“新建 0，更新 2，跳过 MCP 1，导入技能 1”，Agent 仍 2 个且映射不变。
- 清理：删除两个测试助手、RikkaTestMCP、TestSkill 与 Downloads 样例；
  无 Key 泄漏、无 FATAL/ANR。
- 版本规则：仅补既有导入字段映射，按 patch 从 0.21.0 → 0.21.1，
  versionCode 2119。

**产物**：`dist/OrionekoX-0.21.1-alpha-arm64-x64.apk`（versionCode 2119，sha256
`af4c78660ea1…`）；debug `dist/OrionekoX-0.21.1-debug-arm64-x64.apk`（sha256
`9944a2c2c005…`）。详见 `docs/qa-0.21.1.md`。

## 5.132 alpha 0.21.2 —— RikkaHub Provider 请求头与开场白（2026-10-28 01:36–01:48）

- Provider `customHeaders` 与助手 `customHeaders` 合并（名称去重，助手覆盖），
  写入 Agent `headers`；助手 `presetMessages` 的第一条 assistant 文本写入
  `firstMes`（上限 4000 字符）。
- QA 发现第一版把 header 对做了一次 JSON 数组往返，设备上 `optJSONArray`
  读回失败，导致 Provider header 丢失（`m=1`/`m=0`）；改为直接遍历源
  `{name,value}` / `[name,value]` 写入 `LinkedHashMap`，复测 `m=2`。
- 设备：`Rikka QA` 导入后 `first_mes="Welcome from RikkaHub preset."`、
  `headers=[["X-Rikka-Provider","p"],["X-Rikka-Assistant","1"]]`、
  `skill_ids=["TestSkill"]`；重复导入仍为 2 个 Agent，字段保持一致。
- 清理测试助手/MCP/技能/样例；无 Key 泄漏、无 FATAL/ANR。
- 版本规则：仅补既有导入字段映射，按 patch 从 0.21.1 → 0.21.2，
  versionCode 2129。

**产物**：`dist/OrionekoX-0.21.2-alpha-arm64-x64.apk`（versionCode 2129，sha256
`16b01215b881…`）；debug `dist/OrionekoX-0.21.2-debug-arm64-x64.apk`（sha256
`3475b1bfb461…`）。详见 `docs/qa-0.21.2.md`。

## 5.133 alpha 0.22.0 —— 按 Agent 人设推荐外观（2026-10-28 02:00–02:12）

- `OxChatTheme.recommendBuiltIn(persona)` 对 Agent 名称 + AGENTS.md 做本地
  关键词评分，从六款内置主题中选择一款；无命中默认“深海”。
- Agent 外观页新增“从人设推荐外观”行（仅 Agent 作用域）；点击套用内置主题
  到当前 Agent 并 toast 推荐名称，完全离线、确定性。
- 设备：`barB-nokey`（人设含“深夜/酒吧”）点击推荐后提示
  “已套用推荐外观：赛博霓虹”，页面变为 `#0A0014 → #1B0033` 渐变、收
  `#1B0033`、发 `#7C00FF`、文字 `#00F0FF`、洋红 2dp 边框、8dp 圆角；
  `agent_555000016.json` 与赛博霓虹一致。测试后删除该覆盖，恢复跟随全局。
- 边界：关键词启发式、非语义；applyBuiltIn 会覆盖整份快照（含背景图/字体）；
  仅 Agent 作用域；无候选预览/撤销。无 FATAL/ANR。
- 版本规则：新增可见的个性化能力，按 minor 从 0.21.2 → 0.22.0，
  versionCode 2209。

**产物**：`dist/OrionekoX-0.22.0-alpha-arm64-x64.apk`（versionCode 2209，sha256
`50c97563bd68…`）；debug `dist/OrionekoX-0.22.0-debug-arm64-x64.apk`（sha256
`de3f88606aa3…`）。详见 `docs/qa-0.22.0.md`。

## 5.134 alpha 0.23.0 —— AMOLED 纯黑快捷开关（2026-10-28 02:14–02:24）

- `OxChatTheme.Snapshot` 新增 `amoled`；persist 到 prefs/Agent/会话 JSON/预设/
  主题包，导入识别 `amoled`/`amoledMode`/`amoled_mode`（布尔或 0/1）。
- 外观页新增“AMOLED 纯黑”开关，副标题显示已开启/已关闭；开启时
  `makeBackground` 返回纯黑 ColorDrawable，聊天背景与状态栏/导航栏均为
  `#000000`，关闭恢复原背景配置；预览同步显示纯黑。
- 设备：全局图片背景主题下开启后 `amoled=true`，预览与 R2B 本地群背景均为
  纯黑，气泡/文字/边框正常；关闭后 `amoled=false`，原图片背景保留。无
  FATAL/ANR。
- 边界：只改背景/系统栏，不自动改文字色；只作用于当前作用域；无定时/系统
  深色联动；未在真实 AMOLED 屏测功耗。
- 版本规则：新增可见外观能力，按 minor 从 0.22.0 → 0.23.0，versionCode 2309。

**产物**：`dist/OrionekoX-0.23.0-alpha-arm64-x64.apk`（versionCode 2309，sha256
`311fc6456dbb…`）；debug `dist/OrionekoX-0.23.0-debug-arm64-x64.apk`（sha256
`b6c48bfa0dd4…`）。详见 `docs/qa-0.23.0.md`。

## 5.135 alpha 0.24.0 —— RikkaHub 聊天数据库文本历史导入（2026-10-28 02:26–02:38）

- 备份 zip 额外读取 `rikka_hub.db`（≤64 MB）；预览显示“历史：会话 N 个 ·
  消息节点 M 条”，导入结果附“已导入历史消息 N 条”。
- 只读查询 `conversationentity` 与 `message_node`：每个节点取
  `messages[select_index]` 当前分支，仅提取 user/assistant 的 text parts；
  上限 200 会话/500 节点/5000 消息、单条 100k 字符；按
  `role+content+ts` 去重，重复导入不追加。
- 设备（schema 样例）：2 会话/4 节点，分支 `Branch B selected` 被正确选中，
  导入 4 条历史；重复导入仍 4 条。清理测试对象；无 Key 泄漏、无 FATAL/ANR。
- 边界：不导入其他分支/图片/文件/工具/推理；同 Agent 多会话合并；标题不保存；
  时间戳为合成值；真实 RikkaHub 导出 DB 待复测。
- 版本规则：新增聊天历史导入能力，按 minor 从 0.23.0 → 0.24.0，
  versionCode 2409。

**产物**：`dist/OrionekoX-0.24.0-alpha-arm64-x64.apk`（versionCode 2409，sha256
`c939c48301f7…`）；debug `dist/OrionekoX-0.24.0-debug-arm64-x64.apk`（sha256
`bc7247e7f0b7…`）。详见 `docs/qa-0.24.0.md`。

## 5.136 alpha 0.24.1 —— Ox 界面中文化补漏（2026-10-28 02:40–02:48）

- 扫描 Ox 自有 Java 代码的 531 个 `R.string` 引用，发现 12 个可见 key 缺少
  中文：Operit 导入（标题/说明/导入中/完成/授权）、本地会员（标题/说明）、
  终端（标题/清空/重启/输入提示）。
- 补齐中文译文，中文包 93 → 94；仅改文案，不改功能代码。
- 设备：Ox 设置显示“本地会员”“从 Operit / Operit2 导入”；终端显示“终端”
  “清空”“重启”“输入命令…”，无英文回退；无 FATAL/ANR。
- 版本规则：仅补翻译/文案，按 patch 从 0.24.0 → 0.24.1，versionCode 2419。

**产物**：`dist/OrionekoX-0.24.1-alpha-arm64-x64.apk`（versionCode 2419，sha256
`f5bdcc7047d7…`）；debug `dist/OrionekoX-0.24.1-debug-arm64-x64.apk`（sha256
`2564d44e7987…`）。详见 `docs/qa-0.24.1.md`。

## 5.137 alpha 0.25.0 —— RikkaHub 聊天图片附件导入（2026-10-28 02:50–03:05）

- 备份 zip 读取 `upload/` 图片（最多 40 个、单文件 4 MB、总 40 MB）；历史导入
  遇到 image part 时按文件名匹配，写入 `files/rikka_upload_<name>` 并记录
  `OxChatEntry.imagePath`；纯图片消息用 `[图片]` 占位。
- 现有 `OxAgentsBridge` 在组装 user 历史时会把 `imagePath` 作为 inline base64
  视觉输入，图片因此可参与后续多模态对话。
- 设备：1 会话/3 节点（文本、回复、图片）样例预览正确；导入 3 条，`chat.json`
  第 3 条含 `image_path`，本地文件 70 B；重复导入仍 3 条。清理测试对象；
  无 Key 泄漏、无 FATAL/ANR。
- 边界：只匹配 `upload/` 文件名；远程/data URL、其他文件类型、每消息第二张
  以后图片跳过；聊天中不以原生照片气泡展示；真实 RikkaHub 备份待复测。
- 版本规则：新增聊天图片资产导入能力，按 minor 从 0.24.1 → 0.25.0，
  versionCode 2509。

**产物**：`dist/OrionekoX-0.25.0-alpha-arm64-x64.apk`（versionCode 2509，sha256
`31eba8aed98f…`）；debug `dist/OrionekoX-0.25.0-debug-arm64-x64.apk`（sha256
`95b6640e3a03…`）。详见 `docs/qa-0.25.0.md`。

## 5.138 alpha 0.25.1 —— 人设推荐确认与撤销（2026-10-28 03:06–03:16）

- 推荐改为原生确认框（显示候选主题名，取消不改状态）；应用前把当前作用域
  快照备份到 `files/ox_recommend/<scope>.json`，应用后出现“撤销上次推荐”。
- 撤销恢复备份快照并删除备份文件；备份按全局/Agent/会话作用域隔离且只保留
  第一份，重复推荐不会覆盖。
- 设备：barB-nokey 推荐“赛博霓虹”前弹出确认；应用后生成 Agent 覆盖与备份，
  撤销后恢复图片背景/80%/24%/AMOLED 关闭，备份删除，toast 正确。
- 构建问题：英文确认文案里的 `agent's` 撇号触发 AAPT2 字符串错误，首轮构建
  失败；改为 `agent name` 后重建成功。
- 版本规则：仅补推荐的确认/撤销交互，按 patch 从 0.25.0 → 0.25.1，
  versionCode 2519。

**产物**：`dist/OrionekoX-0.25.1-alpha-arm64-x64.apk`（versionCode 2519，sha256
`6d34eb6bb7d0…`）；debug `dist/OrionekoX-0.25.1-debug-arm64-x64.apk`（sha256
`dae337e6a870…`）。详见 `docs/qa-0.25.1.md`。

## 5.139 alpha 0.25.2 —— 命名预设重命名（2026-10-28 03:18–03:32）

- 长按预设改为原生菜单“重命名 / 删除”；重命名对话框预填当前名称，保存时
  按既有规则 sanitize 文件名、更新 JSON `name`、删除旧文件；同名目标拒绝并
  提示“同名预设已存在”，不覆盖已有预设。
- 设备：临时预设 RenameQA 成功改为 RenameQA2，目录仅保留新文件；改名为已存在
  的 BundleQA 被拒绝且原文件不变；取消对话框无副作用。测试后删除临时预设。
- 版本规则：仅补预设管理小功能，按 patch 从 0.25.1 → 0.25.2，
  versionCode 2529。

**产物**：`dist/OrionekoX-0.25.2-alpha-arm64-x64.apk`（versionCode 2529，sha256
`454b52f3f044…`）；debug `dist/OrionekoX-0.25.2-debug-arm64-x64.apk`（sha256
`c0027b7821f9…`）。详见 `docs/qa-0.25.2.md`。

## 5.140 alpha 0.26.0 —— Kelivo / Chatbox 备份助手导入（2026-10-28 03:34–03:48）

- Ox 设置新增 Kelivo/Chatbox 备份导入；解析 zip 的 `copilots.json` 与
  `settings.json` 的 `assistants_v1`（JSON 字符串/数组/映射），也支持直接
  JSON；预览助手数量、名称与已存在数。
- 映射 name、systemPrompt/prompt→AGENTS.md、temperature/topP/maxTokens、
  customHeaders，`chatModelId` 覆盖模型名；Provider 从现有第一个有
  baseUrl+apiKey 的 Agent 继承（无则离线）。Agent id `kelivo_<id>`，重复导入
  更新并保留 tg_id/头像。
- 设备（schema 样例）：1 个 copilot + 1 个 assistant，预览 2；导入
  “新建 2”，Kelivo Assistant 的人设/模型/0.5/0.9/256/X-Kelivo 均正确；
  重复导入“新建 0，更新 2”，仍 2 个 Agent。清理测试对象；无 Key/FATAL/ANR。
- 边界：只导入助手；Provider 密钥、对话、skills、MCP、工作区、头像背景、
  presetMessages/regex/skillIds 未导入；真实 Kelivo 备份待复测。
- 版本规则：新增第三方备份助手导入能力，按 minor 从 0.25.2 → 0.26.0，
  versionCode 2609。

**产物**：`dist/OrionekoX-0.26.0-alpha-arm64-x64.apk`（versionCode 2609，sha256
`bdf2b2a2d03e…`）；debug `dist/OrionekoX-0.26.0-debug-arm64-x64.apk`（sha256
`f02f1a18c1ea…`）。详见 `docs/qa-0.26.0.md`。

## 5.141 alpha 0.26.1 —— Kelivo Provider 配置接线（2026-10-28 03:50–04:02）

- 解析 `provider_configs_v1`（JSON 字符串/数组/映射）建立
  provider id→配置；助手 `chatModelProvider` 命中时使用备份的 baseUrl/apiKey，
  模型取 `chatModelId` 或 models 首个；未命中回退本机 Provider，再没有则离线。
- 预览/结果增加“使用备份中的 N 个 Provider 配置”提示；不显示 Key。
- 设备（schema 样例）：Claude Assistant 关联 `prov-1` 后
  `base_url=https://kelivo.example/v1`、`model=kelivo-test-model`；Copilot
  正确回退本机 Provider；重复导入仍 2 个 Agent；logcat 测试 Key 命中 0。
- 边界：Provider OAuth/多 Key/代理/headers/body/Vertex 特殊配置未导入；
  仅保存被助手引用的 Provider；models 只取第一个；真实备份待复测。
- 版本规则：仅补既有导入的 Provider 映射，按 patch 从 0.26.0 → 0.26.1，
  versionCode 2619。

**产物**：`dist/OrionekoX-0.26.1-alpha-arm64-x64.apk`（versionCode 2619，sha256
`18cd44f4e3d3…`）；debug `dist/OrionekoX-0.26.1-debug-arm64-x64.apk`（sha256
`e0e1eb8209ab…`）。详见 `docs/qa-0.26.1.md`。

## 5.142 alpha 0.26.2 —— Operit 背景模糊映射修正（2026-10-28 04:04–04:14）

- Operit 外观 JSON 的 `use_background_blur` / `background_blur_radius` 现在映射
  到 1–3 级真实背景模糊（≥20 强、≥12 中、其余轻），不再误把
  `use_background_blur` 当作磨砂气泡；liquid/water glass 仍映射磨砂。
- 设备：导入 `background_blur_radius=24` 的 Operit JSON，预览摘要显示“模糊强”，
  Agent JSON `backgroundBlur=3`，预览壁纸文字/边框被抹平；测试后删除覆盖。
- 边界：半径阈值是启发式；纯色/渐变无可见模糊；9-patch、输入样式、头像、
  padding、crop/repeat/scale、视频背景仍未映射。无 FATAL/ANR。
- 版本规则：仅修既有导入字段映射，按 patch 从 0.26.1 → 0.26.2，
  versionCode 2629。

**产物**：`dist/OrionekoX-0.26.2-alpha-arm64-x64.apk`（versionCode 2629，sha256
`033b4c544e66…`）；debug `dist/OrionekoX-0.26.2-debug-arm64-x64.apk`（sha256
`3ed2cc9252bd…`）。详见 `docs/qa-0.26.2.md`。

## 5.143 alpha 0.27.0 —— 命名预设预览（2026-10-28 04:16–04:26）

- 预览逻辑改为接受任意 Snapshot；新增 `currentSnapshot`/`presetSnapshot`/
  `backgroundDrawableFor`/`typefaceFor`。长按预设菜单改为
  `预览 / 重命名 / 删除`，预览只读取预设 JSON，不写入当前作用域。
- 设备：当前为图片背景主题，预览 DarkSea 预设显示其黑色纯色背景与浅绿气泡；
  关闭后 `ox_chat_theme.xml` mtime 未变，当前主题仍为原图片背景。
- 边界：预览仍是轻量示例，不含媒体/工具/引用/头像；缺失资产会按回退字段绘制；
  不支持预览内编辑或并排比较。无 FATAL/ANR。
- 版本规则：新增可见的预设管理能力，按 minor 从 0.26.2 → 0.27.0，
  versionCode 2709。

**产物**：`dist/OrionekoX-0.27.0-alpha-arm64-x64.apk`（versionCode 2709，sha256
`84e5b96cf0b7…`）；debug `dist/OrionekoX-0.27.0-debug-arm64-x64.apk`（sha256
`bf3de1b56330…`）。详见 `docs/qa-0.27.0.md`。

## 5.144 alpha 0.27.1 —— Kelivo 备份技能导入（2026-10-28 04:28–04:40）

- Kelivo/Chatbox 备份 zip 读取 `skills/**/SKILL.md`（≤50 个、单 512 KB），
  经 OxSkillStore 写入私有技能库；同名更新，不删除其他技能。
- 预览/结果增加技能数量；修正“导入助手但不含技能”的旧文案为
  “助手、人设与技能；不含对话、Provider 密钥与 MCP”，并重建复验。
- 设备：样例 1 助手 + 1 技能，预览“将导入 1 个技能”；导入“新建 1 / 技能 1”；
  `files/oxskills/KelivoSkill/SKILL.md` 96 B；重复导入“新建 0 / 更新 1 /
  技能 1”，仍 1 个技能目录。清理测试对象；无 FATAL/ANR。
- 边界：仅 zip 的 `skills/`；只导入 SKILL.md 正文；不含对话/Provider 密钥/
  MCP/工作区；真实备份待复测。
- 版本规则：仅补既有 Kelivo 导入的技能包，按 patch 从 0.27.0 → 0.27.1，
  versionCode 2719。

**产物**：`dist/OrionekoX-0.27.1-alpha-arm64-x64.apk`（versionCode 2719，sha256
`39cb7424a9e8…`）；debug `dist/OrionekoX-0.27.1-debug-arm64-x64.apk`（sha256
`26769a56fb6a…`）。详见 `docs/qa-0.27.1.md`。

## 5.145 alpha 0.27.2 —— Kelivo / Chatbox HTTP MCP 导入（2026-10-28 04:42–04:58）

- Kelivo/Chatbox 备份 `mcp_servers_v1`（兼容 `mcpServers`）中的 HTTP /
  streamableHttp 条目写入 `files/oxmcp/servers.json`：name、url、headers、
  enabled；按 URL 去重，重复导入不新增。
- SSE、stdio/command、inmemory 条目只计数跳过，不执行外部命令；SSE 等后续
  再评估。预览/结果增加 HTTP MCP 数量与跳过数量。
- 总览文案修正为“助手、人设、技能与 HTTP MCP；不含对话与 Provider 密钥”。
- 设备：样例 1 助手 + 1 HTTP + 1 SSE；预览“1 个 HTTP MCP / 跳过 1”；导入
  “新建 1 / 新增 MCP 1 / 跳过 1”，`KelivoHTTP` URL、enabled、`X-Kelivo` header
  均落盘，SSE 不存在；重复导入“新建 0 / 更新 1 / 新增 MCP 0 / 跳过 2”，仍
  只有 1 条。清理测试助手、MCP 条目与样例；无 FATAL/ANR。
- 边界：仅 HTTP；不导入工具白名单/OAuth/客户端注册/环境变量；不做连通性
  测试；Kelivo 对话、工作区、头像/背景仍未导入；真实备份待复测。
- 版本规则：仅补既有 Kelivo 导入的 MCP 包，按 patch 从 0.27.1 → 0.27.2，
  versionCode 2729。

**产物**：`dist/OrionekoX-0.27.2-alpha-arm64-x64.apk`（versionCode 2729，sha256
`82b76f4e3a69…`）；debug `dist/OrionekoX-0.27.2-debug-arm64-x64.apk`（sha256
`0e070cc8bed2…`）。详见 `docs/qa-0.27.2.md`。

## 5.146 alpha 0.28.0 —— Kelivo / Chatbox 对话历史导入（2026-10-28 05:15–05:45）

- Chatbox v2 zip 读取 `manifest.json` + `sessions/*/session.json`；兼容旧包的
  `sessions/**/*.json` 与直接 JSON 的 `chat-sessions-list` / `session:<id>`。
- 每个 session 的 threads 各导入为一段对话；`session.messages` 作为当前话题，
  与 thread 消息 id 集合相同时不重复。
- 每段对话生成稳定 id 的本地 Agent（`kelivo_chat_<session>_<thread|current>`），
  首条 system 作为人设，继承 provider/model/temperature/topP/maxTokens；
  消息保留原始时间戳，tool/system/附件转为可读文本。
- 导入后调用 `replayImportedHistory` 写入原生 messages_v2，聊天界面可看到
  备份历史而非只有欢迎语；重复导入替换历史且不重复消息。
- 设备：zip 样例 3 段/7 条，预览与结果一致；重复导入仍 3 个 Agent、消息
  2/3/2 条；原生界面正确显示“当前话题”与三条历史气泡。JSON 样例 1 段/2 条，
  0.3/0.6/64 配置与“10月25日”历史正确。清理 4 个测试 Agent 与样例后
  dialogs/messages 归零；无本包 FATAL/ANR。
- 边界：不复制图片/文件资源，只留占位；tool 压平为 assistant；旧时间戳使
  对话在列表中靠后；每 thread 独立 Agent，无父子会话切换。
- 版本规则：新增可见的备份对话历史导入能力，按 minor 从 0.27.2 → 0.28.0，
  versionCode 2809。

**产物**：`dist/OrionekoX-0.28.0-alpha-arm64-x64.apk`（versionCode 2809，sha256
`7cff4b8b9b36…`）；debug `dist/OrionekoX-0.28.0-debug-arm64-x64.apk`（sha256
`149ed3bd0483…`）。详见 `docs/qa-0.28.0.md`。

## 5.147 alpha 0.29.0 —— Kelivo / Chatbox 备份头像导入（2026-10-28 05:55–06:05）

- Chatbox v2 备份 `manifest.json.resources[]` 中 `kind=avatar` /
  `copilot-image` 的图片按 `originalStorageKeys`/资源 id 建映射，支持原始字节
  与 `data-url-base64` 两种载体。
- 助手头像读取 `avatar.storageKey`/`avatar.url`/`picUrl`；会话头像读取
  session meta 的 `assistantAvatarKey`/`picUrl`，其次 session 的 `picUrl`。
- 命中资源时写入 `files/oxagents/<agentId>/avatar.<ext>` 并设置
  `avatar_path`；重复导入保留已有头像，不覆盖用户自定义头像。
- 上限 64 个候选文件、单文件 2 MB、总计 24 MB；只接受 PNG/JPEG/GIF/WebP
  魔数，不做网络下载。
- 设备：1 助手 + 1 会话、2 个头像资源；预览/结果均提示 2 个头像，落盘
  avatar.png 1310 B/1623 B；原生会话页显示绿色 `S1` 与蓝色 `AI` 头像；
  重复导入头像 sha256 不变、仍 2 个 Agent；清理后 dialogs/messages 归零，
  无本包 FATAL/ANR。
- 边界：仅头像资源；聊天图片/文件/背景/字体不导入；http(s) 头像跳过；
  HEIC/AVIF 不支持；真实备份待复测。
- 版本规则：新增可见的备份头像导入能力，按 minor 从 0.28.0 → 0.29.0，
  versionCode 2909。

**产物**：`dist/OrionekoX-0.29.0-alpha-arm64-x64.apk`（versionCode 2909，sha256
`660e383315c2…`）；debug `dist/OrionekoX-0.29.0-debug-arm64-x64.apk`（sha256
`58214581a4f9…`）。详见 `docs/qa-0.29.0.md`。

## 5.148 alpha 0.30.0 —— Kelivo / Chatbox 聊天图片导入（2026-10-28 06:20–06:38）

- Chatbox v2 `resources[]` 增加 `kind=image`（及图片类 attachment）解析，按
  `originalStorageKeys`/资源 id 映射；消息图片按 `storageKey`/`url` 命中。
- 导入时写入 `files/oxagents/<agentId>/media/img_<n>.<ext>`，写入
  `OxChatEntry.imagePath`；有文字作为照片说明，纯图片正文为空，其余图片保留
  `[图片]` 占位。
- `replayImportedHistory` 用 `SendMessagesHelper.generatePhotoSizes` 生成原生
  照片气泡；失败退回文字，不阻塞其余历史。
- 每会话最多 40 张图片；单张 2 MB、资源总量 24 MB；仅 PNG/JPEG/GIF/WebP，
  不下载网络图片。预览/结果增加图片计数。
- 设备：1 会话/4 条、2 张图片（原始 PNG + data URL）；预览/结果均提示 2 张；
  落盘 2783 B/3272 B，chat.json 的 image_path 正确；原生界面显示 PIC1 带
  说明、PIC2 纯图片气泡；重复导入图片 sha256 不变、messages_v2 仍 4 条；
  清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：每消息仅首图，不生成相册；文件/音视频/网络图片不导入；真实备份待
  复测。
- 版本规则：新增可见的聊天图片导入与原生照片气泡能力，按 minor 从 0.29.0 →
  0.30.0，versionCode 3009。

**产物**：`dist/OrionekoX-0.30.0-alpha-arm64-x64.apk`（versionCode 3009，sha256
`199d7c5e2361…`）；debug `dist/OrionekoX-0.30.0-debug-arm64-x64.apk`（sha256
`e1af87f999cd…`）。详见 `docs/qa-0.30.0.md`。

## 5.149 alpha 0.31.0 —— Kelivo / Chatbox 聊天附件导入（2026-10-28 06:42–06:52）

- `OxChatEntry` 增加 filePath/fileName 并持久化到 chat.json；旧记录兼容。
- Chatbox v2 `parsed-attachment`/`raw-attachment` 的非图片资源进入附件映射，
  消息 file part 按 storageKey/rawStorageKey/url 匹配；导入复制到 Agent
  media/<index>_<safeName>，原名用于显示。
- 每会话最多 20 个附件；有文字作为说明，纯附件允许空正文；其余附件保留
  `[文件]` 占位；Agent 上下文补 `[文件] 名称`，不执行文件内容。
- `replayImportedHistory` 用 `buildLocalDocument` 生成原生文件气泡（名称、
  大小、MIME、localPath），失败退回文字。
- 设备：1 会话/4 条、2 个附件（raw txt + data URL json）；预览/结果均提示
  2 个；落盘 35 B/38 B，chat.json 字段正确；原生界面显示 notes.txt 带说明、
  data.json 纯文件气泡；重复导入附件 sha256 不变、messages_v2 仍 4 条；
  清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：每消息仅首附件；音视频按文档展示；网络附件不下载；真实备份待复测。
- 版本规则：新增可见的聊天附件导入与原生文件气泡能力，按 minor 从 0.30.0 →
  0.31.0，versionCode 3109。

**产物**：`dist/OrionekoX-0.31.0-alpha-arm64-x64.apk`（versionCode 3109，sha256
`b64728dd51ab…`）；debug `dist/OrionekoX-0.31.0-debug-arm64-x64.apk`（sha256
`8699b26da48a…`）。详见 `docs/qa-0.31.0.md`。

## 5.150 alpha 0.32.0 —— 导出 Kelivo / Chatbox 备份（2026-10-28 07:00–07:18）

- Ox 设置新增“Export Kelivo / Chatbox backup”，确认后 SAF 创建
  `orioneko-chatbox-backup.zip`。
- 导出非内置 Agent：有聊天写 session（system prompt+消息，不再重复 copilot），
  无聊天写 copilot（prompt/模型/采样/头像）；资源含 avatar/image/
  raw-attachment，data URL 编码，global/session 作用域与 storage key 映射。
- 明确不导出 API Key、provider baseUrl、Authorization、工作区、记忆、技能、
  MCP、主题；资源上限 64 个/2 MB/24 MB。
- 设备：seed 导入 1 助手+1 会话（2 头像/1 图片/1 附件）；导出 483,866 B，
  manifest/checksum/stats 校验通过，无 `ox1:`/`sk-`/`test-key`；过滤子集
  8,306 B 回环导入后 prompt、采样、头像、图片、附件、4 条消息均恢复；
  原生界面显示 AV 头像、RT 图片说明与 notes.txt 文件气泡；清理后
  dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：全量导出无选择；有聊天只写 session、无聊天只写 copilot，回环 id 会
  多一层前缀；外部 Chatbox 未实际导入验证。
- 版本规则：新增可见的 Chatbox v2 备份导出能力，按 minor 从 0.31.0 →
  0.32.0，versionCode 3209。

**产物**：`dist/OrionekoX-0.32.0-alpha-arm64-x64.apk`（versionCode 3209，sha256
`c358fd23483d…`）；debug `dist/OrionekoX-0.32.0-debug-arm64-x64.apk`（sha256
`5e0a54de54e0…`）。详见 `docs/qa-0.32.0.md`。

## 5.151 alpha 0.33.0 —— 按 Agent 导出 Chatbox 备份（2026-10-28 07:24–07:35）

- `OxKelivoExporter.exportTo(context, uri, onlyIds)` 支持指定 Agent 集合；
  全量导出仍走原入口。
- Agent 长按菜单（非内置）新增 “Export Chatbox backup”，确认框说明范围与
  “不含 API Key/provider URL”，SAF 默认文件名 `<名称>-chatbox-backup.zip`。
- 只导出该 Agent：有聊天写 session、无聊天写 copilot；结果 toast 汇总消息与
  文件数。
- 设备：`Single Export` 导入后长按导出 6,190 B；schema 1 session/0 copilot/
  3 资源，stats/checksum 通过，无其他 Agent、无密钥；回环导入保留 prompt、
  0.5/0.7/80、头像、图片、notes.txt 与 4 条消息；原生 SE 头像、SE1 图片说明、
  文件气泡正确；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：单 Agent、无多选；回环 id 多一层前缀；外部 Chatbox 未实测。
- 版本规则：新增可见的单 Agent 导出能力，按 minor 从 0.32.0 → 0.33.0，
  versionCode 3309。

**产物**：`dist/OrionekoX-0.33.0-alpha-arm64-x64.apk`（versionCode 3309，sha256
`b291c20d7863…`）；debug `dist/OrionekoX-0.33.0-debug-arm64-x64.apk`（sha256
`bb8d787220ee…`）。详见 `docs/qa-0.33.0.md`。

## 5.152 alpha 0.34.0 —— 技能库导出 SKILL.md zip（2026-10-28 07:40–07:55）

- 技能库新增 “Export skills zip”，SAF 创建 `orioneko-skills.zip`；
  `OxSkillStore.exportZip` 遍历私有与 Download 技能，写
  `skills/<id>/SKILL.md`，同名 id 加后缀，单技能 512 KB。
- 只导出 SKILL.md 正文，不含脚本/附件/密钥；结果 toast 计数。
- 修正导入侧 `skills/<id>/SKILL.md` 的容器前缀：不再生成 `skills_<id>`，
  回环导入得到准确 id。
- 设备：QASkill0340 150 B 导入后导出 2,353 B/7 个 SKILL.md；裁剪 272 B
  子集删除后回环导入，修正后得到 `files/oxskills/QASkill0340/SKILL.md`
  150 B；列表显示正确、无重复 skills_ 目录；清理后无残留，无本包
  FATAL/ANR。
- 边界：全量导出、无多选；仅 SKILL.md 正文；外部客户端未实测。
- 版本规则：新增可见的技能库导出能力，按 minor 从 0.33.0 → 0.34.0，
  versionCode 3409。

**产物**：`dist/OrionekoX-0.34.0-alpha-arm64-x64.apk`（versionCode 3409，sha256
`79be212983be…`）；debug `dist/OrionekoX-0.34.0-debug-arm64-x64.apk`（sha256
`092601ccd828…`）。详见 `docs/qa-0.34.0.md`。

## 5.153 alpha 0.35.0 —— RikkaHub 图像头像导入（2026-10-28 08:00–08:18）

- 解析 RikkaHub settings.json 助手 `avatar` 的 `Avatar.Image` url，取
  basename 匹配备份 `upload/` 文件，写入 Agent 目录 avatar.png/jpg/webp/gif；
  不下载网络 URL，重复导入保留已有头像。
- `avatarEmoji` 改为类型名包含 `emoji`，兼容 FQN 序列化的
  `Avatar.Emoji`。
- 预览增加“将导入 N 个助手图像头像”，总览补充图像头像；结果 note
  “导入头像 N 个”。
- 设备：2 助手样例（Image upload RA + Emoji FQN 🦊）；预览提示 1 个图像
  头像；导入 2 助手、头像 1 个，avatar.png 2,519 B；原生 RA 头像正确，
  Emoji 🦊 正确；重复导入 0 新建/2 更新、头像 sha256 不变；清理后
  dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：仅 upload/ 本地图像，网络/content 无文件时跳过；basename 匹配；
  真实备份待复测。
- 版本规则：新增可见的 RikkaHub 图像头像导入能力，按 minor 从 0.34.0 →
  0.35.0，versionCode 3509。

**产物**：`dist/OrionekoX-0.35.0-alpha-arm64-x64.apk`（versionCode 3509，sha256
`7c1aa6d7d77e…`）；debug `dist/OrionekoX-0.35.0-debug-arm64-x64.apk`（sha256
`1c3b982d2590…`）。详见 `docs/qa-0.35.0.md`。

## 5.154 alpha 0.36.0 —— 多选 Agent 导出 Chatbox 备份（2026-10-28 08:22–08:38）

- Ox 设置新增 “Export selected agents”：原生复选框列表（所有非内置 Agent，
  默认不勾选），确认后 SAF 创建 `orioneko-selected-backup.zip`。
- 复用 `OxKelivoExporter.exportTo(..., onlyIds)`，只写勾选的 session/copilot
  与资源；空选择提示且不写文件；取消 SAF 清空待导出列表。
- 设备：Multi B（2 消息）+ Single Export（4 消息/头像/图片/附件）勾选导出
  6,697 B；manifest 2 sessions/0 copilot/3 资源，仅这两个 Agent；回环导入
  生成 2 个会话，B 消息/B 回复、图片、notes.txt 与 4 条消息均正确；清理后
  dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：无搜索/全选；不导出 Key/provider URL/工作区/记忆/技能/MCP/主题；
  外部 Chatbox 未实测。
- 版本规则：新增可见的多选 Agent 导出能力，按 minor 从 0.35.0 → 0.36.0，
  versionCode 3609。

**产物**：`dist/OrionekoX-0.36.0-alpha-arm64-x64.apk`（versionCode 3609，sha256
`3f4b52ac9866…`）；debug `dist/OrionekoX-0.36.0-debug-arm64-x64.apk`（sha256
`2a6607834fa3…`）。详见 `docs/qa-0.36.0.md`。

## 5.155 alpha 0.37.0 —— MCP 服务器 JSON 导出（2026-10-28 08:48–09:02）

- MCP 服务页新增 “Export MCP JSON”，SAF 创建 `orioneko-mcp.json`；输出
  `mcpServers` 映射（type=streamableHttp、url、isActive），只含 http/https。
- 明确不导出 headers/Authorization/API Key、OAuth、stdio/SSE 与工具白名单；
  同名 key 加后缀；结果 toast 计数，无服务器时不写文件。
- 设备：带 `Authorization: Bearer QA_SECRET_0370` 的测试服务器导出 390 B，
  含 4 个服务器但无 headers，文件中 secret 出现 0 次；内部 servers.json
  仍保留 header，证明是导出剔除；删除后回环导入恢复 URL、headers 为空，
  原有 3 个服务器未变；清理测试服务器与 JSON，无本包 FATAL/ANR。
- 边界：仅 HTTP、无多选、不导出认证 header、不做连通性测试；外部客户端
  未实测。
- 版本规则：新增可见的 MCP JSON 导出能力，按 minor 从 0.36.0 → 0.37.0，
  versionCode 3709。

**产物**：`dist/OrionekoX-0.37.0-alpha-arm64-x64.apk`（versionCode 3709，sha256
`7b1b8ebd7528…`）；debug `dist/OrionekoX-0.37.0-debug-arm64-x64.apk`（sha256
`e6d3b625262a…`）。详见 `docs/qa-0.37.0.md`。

## 5.156 alpha 0.37.1 —— 导出功能中文化补漏（2026-10-28 09:10–09:20）

- 中文包新增 27 条译文：Kelivo/Chatbox 对话/导出/单选/多选 17 条、MCP
  导出 5 条、技能导出 5 条；`OxKelivoNothing` 改为“助手或对话”；中文包
  98 → 99。
- 文案审计：`ui/Ox*.java` 与 `ox/*.java` 的全部 `R.string.Ox*` 引用对
  zh-cn.xml 求差，缺失 0。
- 设备：Ox 设置、技能库、MCP 页导出入口均为中文；Kelivo 预览显示“对话
  1 段（2 条消息）/项目：Multi B”，不再有 Conversations/Items 英文；
  仅预览后取消，未写数据；无本包 FATAL/ANR。
- 边界：仅审计 Ox 自有字符串；其他语言包未补；无功能/格式变化。
- 版本规则：仅文案与 QA，按 patch 从 0.37.0 → 0.37.1，versionCode 3719。

**产物**：`dist/OrionekoX-0.37.1-alpha-arm64-x64.apk`（versionCode 3719，sha256
`8876b1ce2a07…`）；debug `dist/OrionekoX-0.37.1-debug-arm64-x64.apk`（sha256
`b21e0412ad0d…`）。详见 `docs/qa-0.37.1.md`。

## 5.157 alpha 0.38.0 —— Chatbox / Kelivo 聊天背景导入（2026-10-28 09:40–09:55）

- 解析 Chatbox v2 `kind=background` 资源与 session/助手 `background`/
  `backgroundImage` 引用（storageKey/url/字符串），写 Agent 目录
  `background.<ext>`。
- syncAgents 后经 `OxChatTheme.applyImportedBackground` 写入按 Agent 主题
  （mode=custom/backgroundMode=image）；已有不同自定义背景时跳过并在结果
  note 说明。
- 设备：BG38 背景样例预览提示 1 个背景；导入后 note“已套用 1 个聊天背景”，
  background.png 14,884 B、agent_555000078.json 指向该文件；原生聊天显示
  BG38 壁纸与气泡；将主题背景改为 custom-bg.png 后重复导入，note“已套用
  0 个；其余保留已有自定义背景”，原路径未被覆盖；清理后 dialogs/messages
  归零，无本包 FATAL/ANR。
- 边界：仅图片背景、仅按 Agent；导出备份暂不含该背景；真实备份待复测。
- 版本规则：新增可见的备份聊天背景导入能力，按 minor 从 0.37.1 → 0.38.0，
  versionCode 3809。

**产物**：`dist/OrionekoX-0.38.0-alpha-arm64-x64.apk`（versionCode 3809，sha256
`a99da68291cd…`）；debug `dist/OrionekoX-0.38.0-debug-arm64-x64.apk`（sha256
`c6616f5f1be8…`）。详见 `docs/qa-0.38.0.md`。

## 5.158 alpha 0.39.0 —— Chatbox 导出包含 Agent 背景（2026-10-28 10:04–10:14）

- `OxChatTheme.backgroundForAgent` 暴露 Agent 主题背景路径；导出器把背景写成
  `kind=background` 资源：有聊天写 session scope + session `background`，
  无聊天写 global + copilot `background`。
- 资源沿用 data URL/sha256/size 与 2 MB/总量上限；不导出 headers/Key。
- 设备：BG38 Agent 单 Agent 导出 15,580 B，manifest 1 session/1 background
  资源、session background.storageKey 正确、2 条消息保留；删除原 Agent/
  主题后回环导入得到 background.png 14,884 B、backgroundMode=image，
  原生聊天 BG38 壁纸恢复；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：仅 Agent 图片背景；全局/按会话/视频背景不导出；导出不单独统计背景；
  外部 Chatbox 未实测。
- 版本规则：新增可见的导出背景能力，按 minor 从 0.38.0 → 0.39.0，
  versionCode 3909。

**产物**：`dist/OrionekoX-0.39.0-alpha-arm64-x64.apk`（versionCode 3909，sha256
`e06f40bbe691…`）；debug `dist/OrionekoX-0.39.0-debug-arm64-x64.apk`（sha256
`56f4494076be…`）。详见 `docs/qa-0.39.0.md`。

## 5.159 alpha 0.40.0 —— 导出按会话背景（2026-10-28 10:20–10:36）

- `OxChatTheme.backgroundForDialog` 读取会话覆盖背景；导出有聊天的 Agent 时
  优先会话背景，空则回退 Agent 背景；copilot 仍用 Agent 背景。
- 设备：Agent 背景 BG38 + 会话覆盖 BG40；原生聊天显示 BG40；单 Agent 导出
  14,441 B，资源解码 sha256 与 BG40 一致（非 BG38）；删除原 Agent/主题/
  会话文件后回环导入，background.png hash 仍为 BG40，原生聊天 BG40 恢复；
  清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：回环后会话背景映射为 Agent 背景，不保留会话 specificity；仅图片；
  外部 Chatbox 未实测。
- 版本规则：新增可见的“导出实际会话背景”能力，按 minor 从 0.39.0 →
  0.40.0，versionCode 4009。

**产物**：`dist/OrionekoX-0.40.0-alpha-arm64-x64.apk`（versionCode 4009，sha256
`e4805cf78f54…`）；debug `dist/OrionekoX-0.40.0-debug-arm64-x64.apk`（sha256
`4334c6285c4f…`）。详见 `docs/qa-0.40.0.md`。

## 5.160 alpha 0.41.0 —— RikkaHub 文件附件导入（2026-10-28 10:44–11:00）

- `OxRikkaImporter` 解析 `type=document` part：匹配备份 `upload/` 文件后复制到
  `files/rikka_upload_<name>`，写入 `OxChatEntry.filePath/fileName`；纯文件消息
  允许空正文；结果新增附件计数与“导入附件 N 个”note。
- 同时修复 RikkaHub 历史导入后原生会话不更新的问题：记录实际新增消息的
  会话并调用 `OxAgentsBridge.replayImportedHistory`；此前 `chat.json` 有历史，
  原生聊天却只显示开场白。
- 设备：1,064 B 最小备份（1 会话 / 2 消息 / 37 B report.txt）导入后得到
  `chat.json` 两条记录与 37 B 私有附件；原生聊天显示 `report.txt` 文件气泡
  （37 B · TXT，正文“看附件”）和助手“收到附件”。重复导入无历史 note、
  `chat.json` 仍 2 条、无重复消息；清理后 dialogs/messages 归零，无本包
  FATAL/ANR。
- 边界：0.41.0 每条消息只取第一个 document；仅图片与 document；
  video/audio 跳过；仅匹配 `upload/`，不下载远程/data URL；文件条目由现有
  Chatbox 导出逻辑携带，但本轮未做导出回环复验。
- 版本规则：新增可见的“RikkaHub 文件附件导入”能力，按 minor 从 0.40.0 →
  0.41.0，versionCode 4109。

**产物**：`dist/OrionekoX-0.41.0-alpha-arm64-x64.apk`（versionCode 4109，sha256
`aff7743a17de…`）；debug `dist/OrionekoX-0.41.0-debug-arm64-x64.apk`（sha256
`0eca64a72f54…`）。详见 `docs/qa-0.41.0.md`。

## 5.161 alpha 0.42.0 —— RikkaHub 多附件与音视频导入（2026-10-28 11:09–11:20）

- `OxRikkaImporter` 改为解析单条消息的全部媒体 part（每条最多 20 个）：
  图片成为照片气泡；document/video/audio 成为文件气泡。首条媒体与正文组成
  主气泡，其余媒体按顺序拆成独立气泡；纯媒体允许空正文。
- 视频/音频文件名取 `url` basename，不再显示 `rikka_upload_` 前缀；结果新增
  “导入图片 N 张”，附件计数包含文件/视频/音频。
- 去重键加入图片/文件 basename，同一节点的多附件不会互相覆盖；多附件共享
  节点时间戳，避免后续节点整体错位，并兼容 0.41.0 已有的单附件记录。
- 设备：2,016 B 样例（1 会话 / 2 节点；文字 + 2 PNG + report.txt + mp4 +
  mp3）导入为 6 条消息、2 张图片、3 个附件；原生聊天显示两张照片气泡与
  `report.txt`/`clip.mp4`/`clip.mp3` 文件气泡，助手回复正常。重复导入仍为
  6 条、无重复；清理后 dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：单条最多 20 个媒体；视频/音频无缩略图/内嵌播放器与时长；仅匹配
  `upload/`；远程/data URL 不下载；导出回环未复验。
- 版本规则：新增可见的“多附件拆分”和“视频/音频导入”能力，按 minor 从
  0.41.0 → 0.42.0，versionCode 4209。

**产物**：`dist/OrionekoX-0.42.0-alpha-arm64-x64.apk`（versionCode 4209，sha256
`c6ebf364723a…`）；debug `dist/OrionekoX-0.42.0-debug-arm64-x64.apk`（sha256
`0a1307066d15…`）。详见 `docs/qa-0.42.0.md`。

## 5.162 alpha 0.43.0 —— Kelivo/Chatbox 多附件与图文混合导入（2026-10-28 11:24–11:33）

- `OxKelivoImporter.convertMessages` 改为按 `contentParts` 顺序收集全部可用
  媒体，不再只取第一张图片/第一个文件，也不再“有图就丢文件”。首条媒体与
  正文组成主气泡，其余媒体按顺序拆成独立条目；每段对话仍限制 40 张图片 /
  20 个文件。
- 图片写入 Agent `media/img_<n>.<ext>`，文件写入 `media/<n>_<name>`；成功匹配
  媒体后移除正文中的 `[图片]`/`[文件] name` 占位符；预览新增拆分说明并按
  实际条目统计图片/附件数。
- 设备：2,392 B 样例（文字 + 2 PNG，其中一张 data URL 编码 + notes.txt +
  data.json）导入为 5 条消息、2 张图片、2 个附件；原生聊天显示绿色照片
  （正文“混合附件测试”）、橙色照片、19 B TXT 与 29 B JSON 文件气泡，助手
  回复正常。再次导入提示替换历史，仍 5 条/5 行无重复；清理后
  dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：沿用 40 图/20 文件、64 资源文件限制；拆分媒体共享原时间戳；
  不解析 info part；远程 URL 不下载；导出回环未复验。
- 版本规则：新增可见的“单消息多附件/图文混合拆分”能力，按 minor 从
  0.42.0 → 0.43.0，versionCode 4309。

**产物**：`dist/OrionekoX-0.43.0-alpha-arm64-x64.apk`（versionCode 4309，sha256
`420018ff5ba6…`）；debug `dist/OrionekoX-0.43.0-debug-arm64-x64.apk`（sha256
`afb28a66240c…`）。详见 `docs/qa-0.43.0.md`。

## 5.163 alpha 0.43.1 —— 多附件 Chatbox 导出回环修复（2026-10-28 11:36–11:46）

- 导出包 3,578 B，含 5 条消息、2 个 image 与 2 个 raw-attachment；解码后的
  2 张图片、`notes.txt`、`data.json` sha256 与源文件逐一一致，且未包含
  API Key/Provider 地址。
- 回环时发现 JSON 附件丢失：`OxKelivoImporter` 的 zip 读取条件排除了所有
  `.json` 条目，导致 `resources/orioneko-*.json` 不进入资源表。改为仅排除
  `resources/` 之外的 JSON 元数据，允许资源目录里的 JSON 附件。
- 修复前预览“2 张图片 / 1 个附件”，修复后同一 zip 为“2 张图片 / 2 个附件”；
  回环后 5 条 chat 记录与原生照片/文件气泡完整恢复。
- 边界：回环 Agent id 会追加 `_current`，本轮先删除原 Agent 再导入，
  未验证保留原 Agent 时是否新增副本；未与真实 Chatbox 客户端互导。
- 版本规则：单点解析 bug 修复与回环 QA，按 patch 从 0.43.0 → 0.43.1，
  versionCode 4319。

**产物**：`dist/OrionekoX-0.43.1-alpha-arm64-x64.apk`（versionCode 4319，sha256
`9f50bd9b7089…`）；debug `dist/OrionekoX-0.43.1-debug-arm64-x64.apk`（sha256
`5aeeb564141f…`）。详见 `docs/qa-0.43.1.md`。

## 5.164 alpha 0.43.2 —— Chatbox 回环保持 Agent 身份（2026-10-28 11:50–12:02）

- 回环时导出 session 的完整 Agent id 被导入器再次加 `kelivo_chat_` 前缀并追加
  `_current`，保留原 Agent 再导入会生成副本。
- 导出 session 增加 `orionekoAgentId`；导入器用 `Conversation.agentIdOverride`
  在预览和实际导入中原样使用该 id，并做 `[a-zA-Z0-9._-]` 清洗、去前导点与
  80 字符截断；无该字段的外部备份继续走原 `_current` 规则。
- 设备：导入样例得到 `kelivo_chat_s1_current`（tgId 555000091），导出 3,593 B
  且字段正确；保留原 Agent 回环，预览“已存在 1 段对话”，结果替换历史；
  Agent 目录仍只有 1 个，tgId 不变，chat.json 与原生消息均 5 条；清理后
  dialogs/messages 归零，无本包 FATAL/ANR。
- 边界：扩展字段仅 OrionekoX 识别；真实 Chatbox 客户端忽略；无聊天 copilot
  未验证；非法/超长 id 会清洗或截断。
- 版本规则：回环身份 bug 的小范围修复与 QA，按 patch 从 0.43.1 → 0.43.2，
  versionCode 4329。

**产物**：`dist/OrionekoX-0.43.2-alpha-arm64-x64.apk`（versionCode 4329，sha256
`7e8d41863a03…`）；debug `dist/OrionekoX-0.43.2-debug-arm64-x64.apk`（sha256
`a359796b9c27…`）。详见 `docs/qa-0.43.2.md`。

## 5.165 alpha 0.44.0 —— Chatbox / Kelivo 备份携带长期记忆（2026-10-28 12:20–12:43）

- Chatbox 导出此前完全不包含 Agent 长期记忆，换机或回环后记忆丢失。
  导出器现在在 session（有聊天）和 copilot（无聊天）写入
  `orionekoMemory`，单 Agent 上限 64 KiB、合计 256 KiB，超出保留尾部并
  在结果中提示；`Result.memories` 统计带记忆的 Agent 数。
- 导入器按同一字段恢复记忆（上限 200,000 字符），预览新增覆盖警告，
  结果新增恢复数量；无字段的外部备份保持原行为。
- 导出 Toast 把记忆数并入主行，避免第二行被截断；同时发现中文语言包
  版本仍为 99，升级不会覆盖 `files/ox_zh-cn.xml`，导致 APK 内新文案
  不显示。将 `OX_ZH_PACK_VERSION` 提升到 100。
- 设备：单 Agent 导出 1,626 B，`orionekoMemory` 73 字符且无敏感字段；
  删除记忆后导入恢复 157 B，sha256 `a7744d01…`，重复导入 hash 不变、
  Agent 目录数保持 25。最终构建全量导出 482,642 B，Toast 为“21 个智能体
  （含记忆 14 个）、12 段对话、245 条消息”；12 个 session 中 10 个、
  9 个 copilot 中 4 个带记忆，合计 14 个，与 Toast 一致。设置说明行、
  确认框均显示“长期记忆”；无本包 FATAL/ANR；清理注入记忆与测试 zip，
  未执行 `pm clear`，无障碍服务已恢复。
- 边界：`orionekoMemory` 为 OrionekoX 扩展，真实 Chatbox/Kelivo 客户端
  会忽略；无聊天 copilot 记忆导入未单独回环；导入覆盖而非合并记忆。
- 版本规则：新增用户可见的备份字段与恢复行为，并调整本地化包版本，
  按 minor 从 0.43.2 → 0.44.0，versionCode 4409。

**产物**：`dist/OrionekoX-0.44.0-alpha-arm64-x64.apk`（versionCode 4409，
sha256 `6acdb24b00fb8b61f2ed93931341ba5217a676b7ab4433d4a01310fe0359a880`）；
debug `dist/OrionekoX-0.44.0-debug-arm64-x64.apk`（sha256
`855e454aa2092fd747be7453b980fc83a620e6d5fe4aeae86a9946c4cd176ef6`）。
详见 `docs/qa-0.44.0.md`。

## 5.166 alpha 0.45.0 —— RikkaHub / SillyTavern 角色卡导入（2026-10-28 12:50–13:18）

> 修正（0.45.1）：Agent 列表自 0.8.114/0.8.115 已有角色卡导入。0.45.0 在
> RikkaHub 设置页重复提供了入口；0.45.1 已把增强合并回 Agent 列表并将
> RikkaHub 行恢复为备份专用。

- “从 RikkaHub 备份导入”此前只接受 `settings.json` 或备份 zip；SillyTavern
  与 RikkaHub 常见的 PNG/JSON 角色卡无法从这个入口进入。
- `OxRikkaImporter` 现在按文件签名分流：PNG 解析 `tEXt`/`iTXt` 的
  `ccv3`/`chara`（base64 或原始 JSON），JSON 在非 settings 结构时按
  V2/V3 wrapper 或扁平 V1 卡解析。人设字段写入 `AGENTS.md`，`first_mes`
  生成开场白，PNG 原图保存为 `avatar.png` 并接入原生头像缓存。
- 稳定 id 为 `rikka_card_<hash>`，重复导入更新同一 Agent；PNG ≤8 MiB、
  文本 ≤4 MiB、单字段 32 KiB、提示词 64 KiB，IHDR 宽高 ≤4096 且总像素
  ≤16M，防止解码炸弹。预览改为角色卡专用文案，中文包 100→103。
- 设备：JSON 卡生成 `rikka_card_df478b83`（tgId 555000092），六节人设、
  开场白与原生欢迎消息正确；同一卡的 8,810 B PNG 更新该 Agent，
  `avatar.png` sha256 `4482184d…` 与源图一致；再次导入仍只有 1 个目录、
  头像 hash 不变、无重复消息。JSON-only 卡生成
  `rikka_card_1ac480d6`，无头像、开场白正常；69 B 无 `chara` 的 PNG
  正确报“没有可用的角色卡数据”。全程无 FATAL/ANR；删除两个测试 Agent
  后对应 dialogs/messages 均为 0，Download 测试卡已清理。
- 边界：不支持压缩 `zTXt`；只取 `first_mes`，未导入 alternate greetings、
  character_book、tags 或正则；角色卡只导入不导出；导入后离线，需接
  Provider；未与真实 SillyTavern/RikkaHub 导出卡互测。
- 版本规则：新增外部格式导入与 UI 行为，按 minor 从 0.44.0 → 0.45.0，
  versionCode 4509。

**产物**：`dist/OrionekoX-0.45.0-alpha-arm64-x64.apk`（versionCode 4509，
sha256 `6e3e9d4905490d566e775e0d3c6215d61002e918ad818149a7112d669892e4b2`）；
debug `dist/OrionekoX-0.45.0-debug-arm64-x64.apk`（sha256
`f6da9de9957057cd4c2a61716839726e9b75c48cfbd65b3272171a133b1ddf08`）。
详见 `docs/qa-0.45.0.md`。

## 5.167 alpha 0.45.1 —— 角色卡导入收拢到 Agent 列表（2026-10-28 13:24–13:39）

- 0.8.114/0.8.115 已在 Agent 列表提供 SillyTavern JSON/PNG 角色卡导入；
  0.45.0 又在 RikkaHub 设置页实现了一套，造成两个入口与两份解析逻辑。
- 0.45.1 把 Agent 列表卡片入口改为复用 `OxRikkaImporter`：先校验确为
  角色卡，再导入稳定 id、六节人设、`first_mes`、`iTXt` 与 PNG 头像，最后
  `syncAgents(true)` 生成原生欢迎消息；删除旧的随机 id、名称“· 副本”和
  本地解析辅助方法。
- RikkaHub 设置页恢复“从 RikkaHub 备份导入 / settings.json 或备份 zip”，
  选中角色卡时提示改用 Agent 列表入口，不写入数据；备份 zip 路径不变。
  中文包 103→104。
- 构建：首次因英文提示中的单引号触发 AAPT2
  `Invalid unicode escape sequence`；改写为不带所有格的文案后
  `BUILD SUCCESSFUL in 1m 57s`。
- 设备：Agent 列表导入 8,285 B PNG 生成 `rikka_card_b303c8ed`
  （tgId 555000094），`avatar.png` sha256 `33f7346e…` 与源图一致，原生
  开场白“0.45.1 卡片开场白。”正确；同卡 JSON 再导入只更新同一目录，
  头像 hash 不变、无 `· 副本`、无重复消息。RikkaHub 行选择角色卡得到
  指向 Agent 列表的提示；297 B 最小备份 zip 仍能导入
  `rikka_regress451`。全程无 FATAL/ANR；删除两个测试 Agent 后
  dialogs/messages 对 555000094/555000095 均为 0。
- 边界：同 0.45.0；不支持 `zTXt`、alternate greetings、character_book；
  卡片只导入不导出；导入后离线；未与真实客户端互测。
- 版本规则：入口与实现收拢的小范围修复，按 patch 从 0.45.0 → 0.45.1，
  versionCode 4519。

**产物**：`dist/OrionekoX-0.45.1-alpha-arm64-x64.apk`（versionCode 4519，
sha256 `8b68ee503af36cc03e483834126a6b77e4d2b2022f6a73af4d709cb4e4a91757`）；
debug `dist/OrionekoX-0.45.1-debug-arm64-x64.apk`（sha256
`8b6d9760270e5fcdda028f81c7ef42ec819db441eb587f717c04fb3386f4381d`）。
详见 `docs/qa-0.45.1.md`。

## 5.168 alpha 0.46.0 —— Chatbox 备份携带技能与关联（2026-10-28 13:47–14:07）

- Chatbox 导出此前不包含技能库，也不保存 Agent 的 `skill_ids`；回导后
  技能文件与关联都会丢失。
- 导出器写入 `skills/<id>/SKILL.md`，并在 session/copilot 写
  `orionekoSkillIds`；全量导出携带所有可见技能，选中导出只携带被选中
  Agent 引用的技能。上限 50 个、单个 512 KiB、合计 8 MiB。
- 导入器先导入技能，再按 `orionekoSkillIds` 恢复 `OxAgent.skillIds`，
  只保留实际导入成功的 id；预览/结果新增关联计数。
- 同时修复 copilot 身份：0.43.2 只给 session 写 `orionekoAgentId`，导致
  copilot 回导被加 `kelivo_` 前缀并生成副本。导出器现在也给 copilot
  写该字段，导入器优先采用；无字段的外部备份保持旧规则。
- 设备：自建 90 B 技能 + 1 个 session Agent + 1 个 copilot Agent 多选
  导出 1,762 B，zip 内技能 sha256 `6cf9bd70…` 与源一致，copilot/session
  均含 id 与技能关联，敏感字段扫描 0 命中。删除技能并把两个 Agent 的
  `skill_ids` 清空后回导：结果“更新 1 / 恢复 2 个技能关联 / 导入技能 1”，
  两个原 Agent 目录和 tgId 不变、无 `kelivo_` 副本、技能 hash 一致；
  重复导入仍 2 个目录、同 hash。全程无 FATAL/ANR；删除测试 Agent/技能后
  对应 dialogs/messages 均为 0。
- 边界：`skills/` 与私有字段为扩展，真实 Chatbox/Kelivo 未实测；只导出
  SKILL.md 正文，不含脚本/附件；全量导出会带所有可见技能。
- 版本规则：新增用户可见的备份内容与恢复行为，按 minor 从 0.45.1 →
  0.46.0，versionCode 4609。

**产物**：`dist/OrionekoX-0.46.0-alpha-arm64-x64.apk`（versionCode 4609，
sha256 `ffb1167497905baac91081a67266f6e3f86bea0cb5563b29a7f8dd28370f402e`）；
debug `dist/OrionekoX-0.46.0-debug-arm64-x64.apk`（sha256
`df097cf624ec220dcbac0d8e453c02e13e54c95848de478a1ab7739a6c768530`）。
详见 `docs/qa-0.46.0.md`。

## 5.169 alpha 0.47.1 —— Chatbox 备份携带 Agent 工作区（2026-10-28 14:08–14:44）

- 0.46.0 的 Chatbox 备份已带记忆、技能和技能关联，但仍不含
  `files/oxworkspace/<agentId>/`；换机或重装后工作区文件全部丢失，
  而 `.oxagent` 只能单 Agent 导出。
- 0.47.0 在导出 zip 增加 `workspace/<agentId>/<相对路径>`：嵌套目录、
  二进制和中文文件名按原字节写入。上限 200 个文件、单个 2 MiB、合计
  8 MiB、深度 6；空文件/超限文件跳过，路径做 canonical containment
  与 `..` 校验。
- 导入器预览统计“将恢复 N 个工作区文件（覆盖同名文件）”，只在 Agent
  仍存在时恢复；同名覆盖、其他文件保留。导入端同样限制 200 个文件、
  单文件 2 MiB、合计 8 MiB，并拒绝绝对路径、反斜杠、`.`/`..` 段。
- 0.47.0 设备 QA 发现完成 Toast 过长，系统两行截断，工作区计数不可见。
  0.47.1 把文案压缩为“已导出：智能体 N、工作区 N、对话 N、消息 N、
  记忆 N、技能 N、文件 N”，工作区前置；单 Agent 文案同样前置工作区。
  中文包 106→107。
- 设备：自建 session/copilot 两个 Agent（tgId 555000102/555000101）和
  4 个工作区文件（20 B 文本、16 B 二进制、33 B 中文嵌套路径、23 B md）。
  选中导出 2,215 B、4 个工作区条目，逐文件 sha256 与源一致，敏感字段
  扫描 0 命中；0.47.1 加 `keep.txt` 后导出 2,414 B、5 条，Toast 完整
  显示“工作区 5”。单 Agent 导出 2,055 B、4 条，只含该 Agent 文件。
  删除 3 个文件并保留 `keep.txt` 后回导：预览/结果均为 4，4 个文件
  逐字节恢复，`keep.txt` 保留；重复导入仍 4 条、2 个 Agent 目录、无
  `kelivo_` 副本。含 `../../evil0470.txt` 的 zip 被整包拒绝
  （`Invalid zip entry path`），未写出文件；含 2 MiB+1 B 文件的 zip
  预览/结果为 5 而非 6，`big.bin` 未写入。全程无本包 FATAL/ANR；
  删除测试 Agent 后两个 tgId 的 dialogs/messages 均为 0。
- 0.47.1 首版功能 QA 后又发现：导出会对所有选中 Agent 调用
  `workspaceDir()`，无工作区也会建空目录；且无聊天/无人设 Agent 的
  工作区会进 zip 却没有对应助手，导入端无法恢复。最终重建改为只对
  实际写入备份的 Agent 收集工作区，并使用只读 `workspaceDirIfExists`。
  复验：`酒吧炒饭A`（2 个工作区文件）+ 无工作区的 `PNG Card` 一起导出
  14,893 B，只有该 Agent 的 2 条 `workspace/` 条目且逐字节一致，
  `PNG Card` 的工作区目录导出前后均不存在；Toast 仍完整显示工作区 2。
- 边界：`workspace/` 是扩展布局，真实 Chatbox/Kelivo 未实测；不保留
  空目录、权限、时间戳或符号链接；导出端上限只做代码审查；删除 Agent
  不自动删除工作区，联系人页缓存需重启刷新。
- 版本规则：新增用户可见的备份内容与恢复行为，按 minor 从 0.46.0 →
  0.47.0（4709）；Toast 文案与导出收集范围修复按 patch 从 0.47.0 →
  0.47.1（4719）。

**产物**：`dist/OrionekoX-0.47.1-alpha-arm64-x64.apk`（versionCode 4719，
sha256 `b5fb5ac1936aab80c0601a1c60d1802f6749ea26f20c664681e14d91ed23cea6`）；
debug `dist/OrionekoX-0.47.1-debug-arm64-x64.apk`（sha256
`f66b459a7d5497a97c85741e5f91eba473daa80c54ee6015177df949413ad2d8`）。
详见 `docs/qa-0.47.1.md`。

## 5.170 alpha 0.48.1 —— Chatbox 备份携带 Agent 偏好（2026-10-28 14:50–15:08）

- 0.47.1 的 Chatbox 备份已带人设、聊天、记忆、技能、工作区和头像，
  但每个 Agent 的工具权限、置顶、备注、开场白、推理/工具调用参数仍会
  在换机后丢失。
- 0.48.0 在 session/copilot 写私有 `orionekoAgent`（仅当存在非默认偏好）：
  `emoji`、`pinned`、`remark`、`first_mes`、`reasoning_effort`、
  `tool_choice`、`parallel_tool_calls`、`context_length`、
  `presence_penalty`、`frequency_penalty`、`top_k`、`seed`、`stop`、
  `tools_policy`。字符串/集合有上限，`tools_policy` 只接受
  auto/ask/deny。
- 明确不导出 `api_key`、`base_url`、`provider_id`、`headers`、
  `extra_body`；其中 headers/extra_body 也可能含认证信息，统一排除。
- 导入在 Provider、模型和技能处理之后应用偏好，避免被 Provider 回退值
  覆盖；预览/结果新增“偏好设置”计数。导出说明加入“助手偏好”，确认框
  注明不含密钥、Provider 地址与请求头；中文包 107→108。
- 设备：自建 session/copilot 两个 Agent（tgId 555000110/555000111），
  session 置顶、备注/开场白、reasoning=high、toolChoice=required、
  并行关闭、context 32000、presence 0.5、frequency -0.25、topK 40、
  seed 4242、stop END、get_time=deny/device_info=ask；copilot 使用
  reasoning=low、toolChoice=none、context 64000、get_time=auto。
  多选导出 1,881 B，两个对象均含完整 `orionekoAgent`，敏感字段扫描
  0 命中。
- 重置偏好后回导：预览/结果均为“恢复 2 个智能体的偏好设置”，字段全部
  恢复、tgId 不变、无副本；原生工具权限页显示 get_time 禁止、
  device_info 询问；置顶 session 在列表首位；重复导入幂等。
- QA 发现 0.48.0 回导后 `context_length` 为 0：偏好先写入、随后
  Provider 回退覆盖。0.48.1 把应用时机移到 Provider/模型/技能之后，
  复验 32000/64000 均正确，其余字段无回归。
- 边界：`orionekoAgent` 为私有扩展，真实 Chatbox/Kelivo 未实测；不导出
  请求头/extra_body/Provider/密钥；备注与开场白仅做 agent.json 回读，
  未逐项截图欢迎语。
- 版本规则：新增用户可见的备份内容与恢复行为，按 minor 从 0.47.1 →
  0.48.0（4809）；Provider 覆盖修复按 patch 从 0.48.0 → 0.48.1
  （4819）。

**产物**：`dist/OrionekoX-0.48.1-alpha-arm64-x64.apk`（versionCode 4819，
sha256 `7c454852b0a80955ff7560b57ea14acd74b5a2c9d62eacc33a8d5568af880d42`）；
debug `dist/OrionekoX-0.48.1-debug-arm64-x64.apk`（sha256
`f14282ef15194991f7e65db17aa67b6763fe612a4013a3e47631226dcce526f6`）。
详见 `docs/qa-0.48.1.md`。

## 5.171 alpha 0.49.0 —— Agent 导出 SillyTavern/RikkaHub 角色卡（2026-10-28 15:20–15:33）

- 0.45.0/0.45.1 已支持导入 SillyTavern PNG/JSON 角色卡，但一直不能
  反向导出，形成“只进不出”的双向兼容缺口。
- Agent 列表长按菜单新增“导出角色卡”（仅非内置 Agent），原生格式选择框
  提供 PNG（含头像）与 JSON（纯文本）；两者都明确不含 API Key、
  Provider 地址与请求头。
- JSON 输出 `chara_card_v2` / `2.0` 的 V2 包装，含 name、description、
  personality、scenario、first_mes、mes_example、system_prompt、
  post_history_instructions、alternate_greetings、character_book、
  tags、creator、character_version、extensions、avatar；六节 AGENTS.md
  逐节还原，其他 Agent 的整份人设写入 description；顶层保留 V1 兼容别名。
- PNG 以头像为底图（最大边 1024 px、160 万像素），把 V2 JSON 以 Base64
  写入 IHDR 之后的 `tEXt` 块（关键字 `chara`，CRC32 按规范计算）；
  无头像/头像不可读时生成 512×640 占位卡片，绘制 emoji 与名称。
- 设备：自建 `QA Card 0490`（tgId 555000103，256×256 头像、六节人设、
  开场白）。导出 PNG 2,420 B，IHDR/tEXt/sRGB/sBIT/IDAT/IEND 逐块 CRC
  通过，嵌入 JSON 的六节与开场白一致，导出图与源头像逐像素相同；
  JSON 导出 790 B，字段一致；敏感字段扫描 0 命中。
- 回导 PNG 得到 `rikka_card_67bc18d8`（tgId 555000104），姓名、开场白、
  六节人设与头像均正确；再回导 JSON 只更新同一目录、不产生副本、
  头像保留。无头像 Agent 导出 20,888 B 占位 PNG（512×640），嵌入
  数据正确。全程无本包 FATAL/ANR。
- 清理三个测试 Agent、下载的卡片与遗留测试 zip；测试 tgId 的
  dialogs/messages 均为 0，未执行 `pm clear`。
- 边界：未与真实 SillyTavern/RikkaHub 互测；不导出 alternate greetings、
  character_book、tags、正则；原生 Agent 的整份 AGENTS.md 进入
  description；emoji 不随卡保存，回导使用默认 🤖；头像重编码不保留
  EXIF/ICC/动画。
- 版本规则：新增用户可见的导出格式、入口与 PNG 资产写入，属于功能迭代，
  按 minor 从 0.48.1 → **0.49.0**（4909），patch 清零。

**产物**：`dist/OrionekoX-0.49.0-alpha-arm64-x64.apk`（versionCode 4909，
sha256 `94e8615fd8ec67a22ef3d85761a367d85cbd92656a8465f7ce6d4f781ef5ab98`）；
debug `dist/OrionekoX-0.49.0-debug-arm64-x64.apk`（sha256
`c649e810d6ba255b72082ea6c5c3db7291d134b823a442c68522dbbd1a7dad88`）。
详见 `docs/qa-0.49.0.md`。

## 5.172 alpha 0.50.0 —— Chatbox 备份携带 Agent 外观主题与字体（2026-10-28 15:42–16:00）

- 0.39.0/0.40.0 已导出聊天背景，但按 Agent 的颜色、气泡、圆角、边框、
  文字色、字号、行高、模糊/暗化与自定义字体仍不能在 Chatbox 备份中
  往返，换机后需要重新配置。
- 0.50.0 在 session/copilot 写私有 `orionekoTheme`，仅当 Agent 存在
  独立按 Agent 外观文件时写入；绝对路径统一清空，背景继续走既有
  `background` 资源，聊天/代码字体写成
  `fonts/<agentId>/chat.ttf`、`code.ttf`。
- 字体上限为单文件 8 MiB、总量 16 MiB、最多 40 个；导入后先写盘并用
  `Typeface.createFromFile` 校验，再在 Agent 存在后应用主题。已有本地
  按 Agent 主题时保留，不覆盖；为跳过主题临时写出的字体会删除。
- 预览/结果新增外观主题计数与字体数；导出信息与单 Agent 确认框加入
  “外观主题（颜色、气泡、字体、背景）”；中文包 109→110。
- QA 发现首个构建只导出 108 KB 代码字体，2.37 MB 聊天字体被 2 MiB
  资源读取上限静默丢弃。新增字体专用读取上限后重建，同一 zip 同时
  包含 2,371,712 B 聊天字体与 108,128 B 代码字体，sha256 与源一致。
- Copilot 设备验证：tgId 555000106，深蓝背景、dim 40、blur 2、收
  #263238/发 #7C4DFF、230 透明度、20dp 圆角、#00E5FF 2dp 边框、
  字号 1.12×、行高 1.3×。导出 1,453,353 B，主题路径为空、背景解码
  哈希与两个字体哈希均与源一致，敏感字段 0 命中。删除主题/字体/背景
  后回导：恢复 1 个主题、2 个字体、1 个背景；颜色、圆角、边框、字号、
  行高与源一致，原生会话可见深色气泡与青色边框。重复导入为 0 个主题
  并保留本地主题，字体文件不增加。
- Session 设备验证：tgId 555000107，带 1 条消息；导出 1,383,082 B，
  session 含 `orionekoTheme` 与 `fonts/.../chat.ttf`；删除后回导恢复
  1 个主题、1 个字体、1 个背景，主题字段与哈希一致，历史正常替换。
- 全程无本包 FATAL/ANR；两个测试 Agent、主题文件、字体与 zip 已清理，
  测试 tgId 的 dialogs/messages 均为 0，未执行 `pm clear`。
- 边界：`orionekoTheme`/`fonts/` 为扩展，真实 Kelivo/Chatbox 未互测；
  只备份按 Agent 主题，不携带全局主题与按会话覆盖；已有本地主题优先；
  字体必须是平台可解析的 TTF/OTF；视频背景不携带。
- 版本规则：新增用户可见的备份内容与恢复行为，属于功能迭代，
  按 minor 从 0.49.0 → **0.50.0**（5009），patch 清零。

**产物**：`dist/OrionekoX-0.50.0-alpha-arm64-x64.apk`（versionCode 5009，
sha256 `4333fadd38652f3ce44f6ff757675608b8a0f8f59f97717e670231105a44461f`）；
debug `dist/OrionekoX-0.50.0-debug-arm64-x64.apk`（sha256
`e6b7f10b4393e31a459a615f73584964e9b5900ebd5f1200b722ea9fa441285e`）。
详见 `docs/qa-0.50.0.md`。

## 5.173 alpha 0.51.0 —— Chatbox 备份携带全局外观主题（2026-10-28 16:03–16:14）

- 0.50.0 已让 Chatbox 备份携带按 Agent 外观，但全局外观（所有 Agent
  的默认主题、全局背景与字体）仍不能往返；换机后需重新配置。
- 0.51.0 在 Chatbox `settings.json` 写私有 `orionekoGlobalTheme`
  （仅全局自定义模式），绝对路径清空；全局背景写成 scope=global 的
  `background` 资源并用 `storageKey` 引用，全局字体写成
  `fonts/global/chat.ttf`、`code.ttf`，沿用 8 MiB/文件、16 MiB 总量。
- 导入读取 settings 的全局主题与背景引用，预览/结果新增全局主题提示；
  背景与字体落地后校验，`applyImportedGlobalTheme` 仅在本地全局外观
  非自定义时写入；已有自定义全局主题则保留并删除临时资产。
- 首轮构建后 Ox 设置导出说明行只显示两行，末尾“不含 API Key”被截断；
  缩短为“助手、聊天、记忆、技能、工作区、偏好、主题、字体、头像、
  附件；无 API Key”，中文包 111→112，重建后完整显示。
- 设备：自建 `QA Global 0510`（tgId 555000108）；原有全局主题先备份
  （sha256 `e070ff70…`）。临时加入 2,371,712 B 聊天字体与 108,128 B
  代码字体。单 Agent 备份 1,457,238 B，`settings.json` 含全局主题与
  背景引用；主题路径为空，背景 sha256 `88a527c8…`、字体
  `9ca9debb…`/`db19a1fd…` 均与源一致，敏感字段 0 命中、无绝对路径。
- 删除 prefs 与字体后回导：结果“已恢复全局外观主题（含字体 2 个）”，
  prefs 颜色/圆角/边框/字号/行高与备份一致，新背景与字体哈希一致；
  打开会话可见原全局深蓝背景、磨砂气泡、粉色边框与白字。重复导入为
  “全局外观已有自定义，保留本地主题”，prefs hash 不变、无孤儿字体/
  背景。全程无 FATAL/ANR。
- 清理测试 Agent、导入资产与 zip，555000108 的 dialogs/messages 为 0；
  恢复原全局 prefs 并复核 sha256 `e070ff70…`，未执行 `pm clear`。
- 边界：全局主题/背景/字体为扩展，真实 Kelivo/Chatbox 未互测；仍不
  携带按会话覆盖；本地已有自定义全局外观优先；字体必须可解析；视频
  背景不携带。
- 版本规则：新增用户可见的备份内容与恢复行为，属于功能迭代，
  按 minor 从 0.50.0 → **0.51.0**（5109），patch 清零。

**产物**：`dist/OrionekoX-0.51.0-alpha-arm64-x64.apk`（versionCode 5109，
sha256 `8281fc3d3d0c4c2aee82f7703c7e8776293a5d58bcf8ac96c4e84cf7c7bfa1f1`）；
debug `dist/OrionekoX-0.51.0-debug-arm64-x64.apk`（sha256
`a6bba2603e4c18b0d964349a4dd1a2c6ba026317bdb77c064d2af8aa4f358d77`）。
详见 `docs/qa-0.51.0.md`。

## 5.174 alpha 0.52.0 —— 本地记忆库、遗忘曲线与阈值压缩（2026-10-28 17:00–17:22）

- 现有长期记忆只有 `memory.txt`，没有检索、遗忘或压缩状态；上下文只是
  按 token/最近 20 条截断，更早内容直接丢失。
- 0.52.0 新增每个 Agent 独立的 SQLite 记忆库（`databases/oxmemory.db`），
  保存可读对话、抽取事实与按日期摘要，记录时间、访问次数、重要度、
  稳定度、短期/长期与去重 hash；单 Agent 上限 4,000 条。
- 普通模式用词元重合/子串匹配；实验向量模式用 128 维本地哈希向量
  余弦 + 关键词混合，不调用外部 embedding。两种模式都叠加 7 天/90 天
  半衰期的遗忘曲线；访问 2 次或高重要度的短期记忆会提升为长期。
- Agent 设置新增记忆库模式、压缩阈值（默认 45 条消息，10–200）与召回
  条数；聊天超过阈值时保留最近 `max(8, 阈值/3)` 条原文，把更早条目
  写入记忆库并生成摘要，`memory_state` 记录进度避免重复压缩。
- 系统提示新增“上下文压缩 / 记忆库摘要 / 相关记忆”区块与“不确定时
  先查证”规则；新增 `memory_search`、`memory_verify` 工具，升级
  `remember`/`recall`。`memory_verify` 严格匹配多关键词，避免
  “favorite color”误配“favorite editor”。
- 记忆页新增统计行、最近记忆对话框与清空记忆库；Agent 设置新增模式与
  阈值行；Chatbox `orionekoAgent` 增加记忆模式/阈值/召回数。中文包
  112→114（新增文案与统计截断修复）。
- 设备：自建 60 条历史（含姓名/生日/咖啡/城市/编辑器事实）的
  `QA Memory 0520`（tgId 555000109），用本地 mock OpenAI 服务记录请求。
  普通模式首轮只发送 17 条消息，system 同时含压缩/摘要/相关记忆；
  `memory_search` 返回生日事实。数据库 47 turn（46 短+1 长）/3 fact/
  1 summary，访问计数与长期提升正常。切换向量模式后同一查询仍召回
  生日/咖啡事实（相关度 0.61）。`memory_verify("birthday")` 返回带日期
  证据；`memory_verify("favorite color")` 严格匹配返回未找到。
- 记忆页统计显示“63 条 · 长期 7 · 短期 56 · 事实 3”；首次布局因统计
  过长把“本地记忆库”截成“本地…”，缩短文案后复验完整。
- 清理测试 Agent、记忆库行与测试 tgId 的 dialogs/messages，计数均为 0；
  恢复模拟器 HTTP 代理与无障碍；全程无本包 FATAL/ANR。
- 边界：本地哈希向量不是语义 embedding；摘要为抽取式；记忆库尚未进入
  Chatbox/.oxagent 备份；旧 memory.txt 不自动迁移；群聊按 Agent 共用；
  阈值按消息条数而非 token 百分比；私有 SQLite 未加密。
- 版本规则：新增用户可见的记忆模式、压缩、工具与设置，属于功能迭代，
  按 minor 从 0.51.0 → **0.52.0**（5209），patch 清零。

**产物**：`dist/OrionekoX-0.52.0-alpha-arm64-x64.apk`（versionCode 5209，
sha256 `fdeb1c51c779bd9766ab7a6a4413ae4639511435d88aeff097c311982c4f0cff`）；
debug `dist/OrionekoX-0.52.0-debug-arm64-x64.apk`（sha256
`8609d8fae589ed741d1f7cda45d221e9ba64ac4e26beb695b0d3e1934a0ce3eb`）。
详见 `docs/qa-0.52.0.md`。

## 5.175 alpha 0.53.0 —— 记忆检索 v2：BM25 + 向量 + 幂律遗忘 + 事实失效（2026-10-28 17:30–18:05）

- 0.52.0 的检索是固定权重的词重合/哈希余弦，遗忘曲线是固定指数半衰期，
  事实只追加、不合并；对照 Mem0、MemGPT、Graphiti、Generative Agents、
  FSRS、MemoryBank 与 LangMem 后重做核心。
- schema v2 新增 difficulty、valid_from、invalid_at、subject，并从 v1
  原地迁移；事实按句号/问号/分号分句，修复英文事实整段合并。
- 检索改为 BM25 + 向量/retention/importance/access 的混合分，加入 MMR
  去重；普通模式以 BM25 为主，向量模式加入本地哈希余弦。失效事实默认
  不召回，只有“以前/原来/used to”等历史查询才降权返回。
- 遗忘改为幂律 `R=1/(1+ageDays/(9*S))`；召回提升 stability、降低
  difficulty，S≥21 天或访问 2 次提升为长期；摘要超过 6 条时滚动合并。
- 事实合并采用 Mem0 思路：同主题近似事实，新事实覆盖旧事实则保留更
  丰富版本；生日/颜色等值变化则把旧事实 invalid_at 并保留历史；失效
  事实会从摘要行中清理，摘要头部标注历史信息。
- `memory_verify` 优先返回事实，有事实命中时不再返回旧对话；system
  prompt 明确“同主题冲突以最新事实为准，旧对话/摘要仅历史参考”。
- 设备 60 条消息 fixture：生日 3/14→3/15、blue→green；最终有效事实为
  name/coffee/Hangzhou/birthday 3/15/color green，旧 3/14 与 blue 失效；
  摘要中的旧值被清理。`memory_search` 首条为有效 3/15 事实，
  `memory_verify` 只返回有效事实；向量模式 coffee 相关度 0.83，
  stability/difficulty 随召回更新。全程无 FATAL/ANR，测试对象已清理。
- 调研与可复现基准：`docs/memory-architecture-research.md`、
  `tools/memory_bench_fixture.py`、`tools/memory_bench_server.py`。
- 边界：向量仍是本地哈希，不是语义 embedding；摘要仍为抽取式；未做
  图记忆、token 压力预警与模型异步蒸馏；Provider 当前没有可用
  `/embeddings` 模型（opendesign model_not_found、opencode-go 403）。
- 版本规则：数据库 schema、打分、事实合并与摘要清理属于算法级功能迭代，
  按 minor 从 0.52.0 → **0.53.0**（5309），patch 清零。

**产物**：`dist/OrionekoX-0.53.0-alpha-arm64-x64.apk`（versionCode 5309，
sha256 `b421454304d0f261e3bf9cb15e167990b08c3939fd7090434ccce2343798137b`）；
debug `dist/OrionekoX-0.53.0-debug-arm64-x64.apk`（sha256
`f612c6c4f246a4f56bcee79b13f2d01dfa0503ff2ac9c9e4f429f11d482571db`）。
详见 `docs/qa-0.53.0.md`。

## 5.176 alpha 0.54.0 —— 异步模型蒸馏：抽象摘要与事实（2026-10-28 18:10–18:35）

- 0.53.0 的压缩摘要和事实仍是规则抽取，容易被小对话淹没。0.54.0 新增
  `OxMemoryDistiller`：压缩批次 ≥8 条时后台调用 Agent 自己的模型，
  生成抽象摘要与原子事实；不阻塞聊天，失败保留抽取式 fallback。
- 请求沿用 Agent 的 baseUrl/apiKey/headers，`stream=false`、temperature
  0.2、max_tokens 1200；system prompt 标记 `[MEMORY_DISTILLER]`，要求
  严格 JSON（summary ≤1200 字符、最多 12 条事实、单条 ≤300 字符），
  不新增 Key、不记录 Key；transcript 上限 14k 字符，响应上限 2 MiB。
- 模型事实走现有的 subject 合并/失效逻辑；模型摘要 source=model、
  importance 0.9。digest 最多注入 1 条摘要，优先模型摘要；事实仍优先。
- QA 发现第三人称事实的 subject 回退成 `lin zhou s`，生日与颜色互相
  失效并连带清空模型摘要；新增 possessive 规则并在 is/are/like/and 等
  词处截断，得到 birthday / favorite color 等主题后复验通过。
- 设备：60 条 fixture + mock 返回 `MODEL_SUMMARY` 与两条事实。后台请求
  成功写入模型生日事实与模型摘要；颜色事实合并进现有绿色事实；下一轮
  system 只含 5 条有效事实 + 1 条模型摘要，不再出现旧 3/14、blue 或
  抽取摘要。无重复请求、聊天不等待蒸馏、无 FATAL/ANR；测试 Agent/
  记忆行/chats 清零，代理与无障碍恢复。
- 边界：仅压缩批次触发，消耗 Agent token；模型输出只做结构/长度校验，
  无事实核查；Provider 需支持 JSON 响应；仍无真正语义 embedding；
  失败路径仅代码审查、未用坏端点回放。
- 版本规则：新增后台模型抽取/摘要与记忆写入，属于功能迭代，按 minor
  从 0.53.0 → **0.54.0**（5409），patch 清零。

**产物**：`dist/OrionekoX-0.54.0-alpha-arm64-x64.apk`（versionCode 5409，
sha256 `1cf7495caf7fbab3e4ea9daa0756db32f53dfb9f6e9eb58b6266f94c57710b66`）；
debug `dist/OrionekoX-0.54.0-debug-arm64-x64.apk`（sha256
`770f8ddecb3372858f72abf45a1a8cb0a457e86ed74898eef3de7e70b5f335c9`）。
详见 `docs/qa-0.54.0.md`。

## 5.177 alpha 0.55.0 —— 开源式 compact：TextRank/SummaryBuffer/token 触发（2026-10-28 18:40–19:05）

- 0.54.0 的 compact 仍是“按消息数截断 + 规则摘要 + 可选模型摘要”，
  没有 token 压力、TextRank/Selective Context 或滚动摘要窗口。
- 调研 LLMLingua/LLMLingua-2、MemGPT、LangChain SummaryBufferMemory、
  Selective Context、TextRank：LLMLingua 原版需 7B 级模型，LLMLingua-2
  需 BERT/ONNX，端上暂不引入；采用可离线的 TextRank + 去重/MMR +
  滚动摘要 + token 预算。
- 新增 `OxContextCompactor`：CJK/拉丁 token 估算、TextRank 句子图
  （阻尼 0.85/20 次）、相似句去重（>0.65）、选句 MMR（>0.55）、
  每句一行输出；摘要预算 300 tokens（约 1200 字符）。
- 触发：消息数 > 默认 45，或设置 context_length 时估算 tokens >
  45%；token 压力下保留窗口按 55% 预算（最低 256 tokens、至少 4 条），
  修复了此前保留窗口被固定 15 条覆盖的问题。system 标注
  “token 压力触发：约 N tokens”。
- 递归摘要：`compressHistory` 把上一版摘要传入 compactor；异步蒸馏
  也接收 previousSummary 并合并；模型摘要用请求 cutoff 使旧摘要失效，
  digest 最多注入 1 条摘要。
- 设备：60 条 fixture 的 TextRank 摘要 249 字符并含咖啡/杭州；模型
  `MODEL_SUMMARY` 111 字符生效，抽取摘要失效；旧 3/14/blue 不注入。
  20 条长消息、context_length=500 的 token 压力测试显示“压缩 13 条、
  保留 7 条、约 790 tokens”，符合 55% 预算。无 FATAL/ANR，测试对象
  与 chats 清理为 0，代理/无障碍恢复。
- 边界：TextRank 是离线近似，不是 LLMLingua-2 语义 token 压缩；
  token 估算非精确 tokenizer；摘要预算 300 tokens；无 70% 预警阶段；
  **外部 Harness 不应使用本 compact**，harness 路由/适配留待下一轮。
- 版本规则：压缩算法、触发条件与摘要策略属于功能迭代，按 minor 从
  0.54.0 → **0.55.0**（5509），patch 清零。

**产物**：`dist/OrionekoX-0.55.0-alpha-arm64-x64.apk`（versionCode 5509，
sha256 `702ac8ab1040b9695549f68db8bb6d7cb27f612b1d0164f817d681919b20c8ab`）；
debug `dist/OrionekoX-0.55.0-debug-arm64-x64.apk`（sha256
`40d1d0ec47c3f5183a1f0d9bd93dc6d2c6bf7e823a37809670c7b65dedf3a729`）。
详见 `docs/qa-0.55.0.md`。

## 5.178 alpha 0.56.0 —— Harness 所有权路由：外部 compact 与本地记忆隔离（2026-10-28 19:05–19:20）

- 0.55.0 把所有 Agent 都送进本地 compact；对 Claude Code、OpenCode、
  CommandCode、OpenClaw、OxLinux 等外部 Harness，这等于本地改写了
  另一份会话历史，Harness 自身仍持有原文，双方状态会分叉。
- 新增 `OxHarnessAdapter`，登记 API 直连、OxLinux、Claude Code、
  OpenCode、CommandCode、OpenClaw、Hermes、CodeBuddy、DeepSeek
  Harness、Pi、其他设备、通用 Harness 的能力：是否本地 compact、
  是否 Harness compact、resume、工具协议、多模态、非文本事件。
- 所有权规则落到 `appendMemoryContext`：只有 `api` 使用本地
  TextRank/SummaryBuffer、写记忆库并注入摘要/召回；外部 Harness
  不压缩、不写库、不注入，只追加“上下文与 compact 由该 Harness
  管理”，并通过 `maybeCompact()` 请求其自行 compact。
- `maybeCompact` 每半个阈值窗口最多触发一次，独立线程执行；
  `requestCompact` 只对 OxLinux 经 `OxContainerManager.run` 执行，
  其他传输层未接通时返回 `transport-not-connected`。只对公开 CLI
  已确认的 Claude Code/CommandCode 提供默认模板，其余留空待实测。
- Agent 新增 `harness`/`harness_session`/`harness_compact_command`；
  资料页新增原生 Harness 选择器、会话 ID、compact 命令三行；Chatbox
  `orionekoAgent` 同步导出/导入并校验；中文包 114→115。
- 设备：`qa_harness_0560`（harness=claude，60 条历史）发送 hello，
  mock 抓包 22 条消息，`has_compression/has_digest/has_recall` 全为
  false，system 尾部为“# 外部 Harness …由「Claude Code」管理…”，
  该 Agent 记忆库行数 0；选择器与设置页截图见
  `docs/screenshots/qa-0.56.0-harness-settings.png`、
  `qa-0.56.0-harness-picker.png`。无本包 FATAL/ANR；测试对象/会话
  清零，代理与无障碍恢复。
- 边界：0.56.0 只是路由/隔离基础，**不是所有 Harness 已接通**。
  Claude/OpenCode/CommandCode/OpenClaw 没有 SSH/ACP/Gateway/PTY
  传输层，默认模板未在真实会话验证；Hermes/CodeBuddy/DSH/Pi 本机
  未安装；外部 Harness 的工具调用、多模态、非文本事件转换与本地
  记忆镜像均未实现；外部 Harness 的 20 条本地窗口仍在，不代表其
  会话管理已完成。能力矩阵见 `docs/harness-adapters.md`。
- 版本规则：新增用户可见的 Harness 选择、会话/compact 配置与上下文
  所有权分流，属于功能迭代，按 minor 从 0.55.0 → **0.56.0**（5609），
  patch 清零。

**产物**：`dist/OrionekoX-0.56.0-alpha-arm64-x64.apk`（versionCode 5609，
sha256 `81253aa8933fbeeba5771e55d00a07ab86496029b9f38b507d923227b24dbd92`）；
debug `dist/OrionekoX-0.56.0-debug-arm64-x64.apk`（sha256
`8a1dd45890bc51bfe34189a91b468477593f0733f0e27a3a0a7936b7f97b3673`）。
详见 `docs/qa-0.56.0.md`。

## 5.179 alpha 0.57.0 —— 真实外部 Harness 传输：桥接、原生 compact、事件归一化（2026-10-28 20:08–20:57）

- 0.56.0 只有所有权路由，没有传输层；在 Android 进程内也无法直接启动
  Claude Code/CommandCode/OpenCode/OpenClaw。新增电脑端
  `tools/ox-harness-bridge.py`：认证 HTTP + NDJSON，运行真实 CLI 与各自
  原生会话，App 通过 `OxHarnessTransport` 消费统一事件。
- 四个 adapter：Claude `-p --output-format stream-json` +
  `--session-id/--resume`；CommandCode `-p --output-format json` +
  `--resume` + 交互 PTY `/compact`；OpenCode `run --format json --session`
  + 私有 `serve` 的 session compact API；OpenClaw Gateway
  `agent --session-id --json` + `sessions compact`。
- 真实验证：OpenClaw/CommandCode/OpenCode 均完成两轮 resume、原生
  compact、compact 后第三轮仍能答出原 token（`BRIDGE-OCL-731`、
  `BRIDGE-CC-731`、`BRIDGE-OC-731`）。Claude 传输可运行，但 Provider
  返回 403，不能宣称可用。
- 事件归一化：text、tool_call、tool_result、status、attachment、error、
  done；App 显示工具调用/结果、重试状态与附件 URL，错误用 Harness 自己
  的文本结束回合，不伪造成功。
- 资料页新增 Bridge URL、Bridge token（AES-GCM/Keystore 包装）、工作
  目录、Harness 模型、OpenClaw Agent、Test harness bridge；断开时明确
  显示 `transport-not-connected`，不回退 API/回声。修复了 echo 误判、
  `memory_mode=off` 跳过 compact、桥接 compact 被模板检查拦截、健康检查
  UI 线程违规、rootfs 重复下载等问题。
- OxLinux：`runPersistent` 已用常驻 proot shell + 脚本/哨兵通道替代一次性
  命令，但真机错误为 `proot error: execve("/usr/bin/dash"): Permission
  denied`（Android 禁止执行 app-data 二进制），只可作为已实现的受限路径，
  不得列为可用。
- 测试：桥接 13 个单元/HTTP 测试通过；debug 包上 OpenClaw 端到端回复、
  resume、原生 compact（`ok=True ms=7837`）、Vault token 往返与
  `Connected` UI 均验证；测试 Agent/tgId 清理为 0，代理/无障碍保持。
- 版本规则：新增真实传输与用户可见配置，按 minor 从 0.56.0 →
  **0.57.0**（5709），patch 清零。

**产物**：`dist/OrionekoX-0.57.0-alpha-arm64-x64.apk`（versionCode 5709，
sha256 `64f9cd0f787741187e4895c5c8df4193a2b43ff19fa75e6d6e5a6c513b6df678`）；
debug `dist/OrionekoX-0.57.0-debug-arm64-x64.apk`（sha256
`e3af72cf5e109752207e5c799672a9bef3421215f346e2989180bd4ff0c61657`）。
详见 `docs/qa-0.57.0.md` 与 `docs/harness-adapters.md`。

## 5.180 alpha 0.58.0 —— Harness 上下文状态：读取、展示与 compact 判定（2026-10-28 21:10–21:30）

- 0.57.0 的桥接只把回复带回 App，App 不知道 Harness 自己的上下文用量/压缩
  建议。桥接 `Session` 新增 `last_context`（只存 usage/预算/模型，不存
  prompt），从 OpenClaw `contextBudgetStatus`、CommandCode `usage`、
  OpenCode 私有服务 `GET /api/session/<id>` 的 `tokens/model/cost`、
  Claude result usage 汇总；`done` 事件附带 `context`，并新增
  `GET /v1/sessions/<id>/context`。
- App 新增 `OxHarnessTransport.context()`、上下文缓存和 `shouldCompact()`；
  资料页新增 **Harness context** 行，显示如
  `in 530 · out 179 · 无需 compact`。`maybeCompact` 允许 Harness 自报
  `should_compact=true` 时绕过本地历史阈值（60 秒冷却），否则仍按本地
  阈值/半窗口限流。
- 真实数据：OpenClaw budget 1,048,000、should_compact=false、input 503 /
  output 44；CommandCode inputTokens 16,669 / outputTokens 9；
  OpenCode input 2,916 / output 25 / reasoning 178 / cache_read 4,224。
- 设备：`ctx_test_580`（tgId 555000014）OpenClaw 一轮回复，App 日志读到
  `harness context`，资料页显示 `in 530 · out 179 · 无需 compact`；
  release 0.58.0 覆盖安装启动无 FATAL/ANR，无障碍重新绑定，代理保持。
- 测试：桥接 15 个单元/HTTP 测试通过；测试 Agent/tgId 清理为 0，桥接
  进程和临时 token 已删除。
- 边界：真实 `should_compact=true` 场景未强行构造，不能宣称自动 native
  compact 已在真机触发；OpenCode context 依赖私有 serve；Claude 仍 403；
  外部记忆镜像/所有权交接仍待下一轮。

**产物**：`dist/OrionekoX-0.58.0-alpha-arm64-x64.apk`（versionCode 5809，
sha256 `de28f3c366919784f527642d3f63a3f482f909052db8514a95df45462c868ad0`）；
debug `dist/OrionekoX-0.58.0-debug-arm64-x64.apk`（sha256
`b712d965841f60e6bec86bbbf3e1781c4ae24dc055ff1a3a0f86d778709a40e1`）。
详见 `docs/qa-0.58.0.md`。

## 5.181 alpha 0.59.0 —— 外部 Harness 显式只读记忆镜像（2026-10-28 21:33–21:50）

- 0.58.0 能读 Harness 上下文，但外部 Harness 的对话没有本地记忆镜像。
  新增默认关闭的 `harness_mirror`：把桥接 Harness 的 user/assistant 以及
  App 实际收到的 tool 事件写入 `OxMemoryStore`，source 为
  `harness-mirror`/`harness-mirror-tool`；镜像行不反注入 Harness，外部
  模式仍在 digest/recall 之前返回。切回 API 后镜像行参与普通召回。
- 稳定文本 hash（不含时间戳）使重复发送/重试/重启幂等；事实抽取仅来自
  user 文本；失败回合不镜像；写库在独立线程。记忆页统计增加“镜像 N”，
  Memory library 对话框显示“镜像对话/镜像工具”标签。
- Agent 资料页新增镜像开关（仅桥接 Harness 可见）；Chatbox
  `orionekoAgent` 导出/导入 `harness_mirror`；中文包 118→119。
- 设备：`mirror_test_590`（tgId 555000015，OpenClaw）连续两次相同文本：
  SQLite 3 行（1 user 去重 + 2 assistant），重启后仍为 3；记忆页
  `3 条 · 长期 0 · 短期 3 · 事实 0 · 镜像 3`，library 对话框显示
  “镜像对话”。切回 API + mock 后，system prompt 召回 3 条镜像
  （相关度 0.89/0.81/0.80），证明交接可用。
- 同时修复空闲监控器 dispatch 后与调度器写回 enabled=1 的竞态：投递后
  连续 12 秒重复关闭，再进入冷却。
- 边界：OpenClaw `--json` 无工具结果，OpenClaw 镜像通常只有 user/
  assistant；SQLite 明文；`should_compact=true` 真机触发仍未构造；Claude
  403、OxLinux exec 限制、第三轮 A/B/C 未启动。

**产物**：`dist/OrionekoX-0.59.0-alpha-arm64-x64.apk`（versionCode 5909，
sha256 `6e444203111d4b11a2ccf12794e242818922dbd1faa4249a8b7883965962f0fa`）；
debug `dist/OrionekoX-0.59.0-debug-arm64-x64.apk`（sha256
`664bdb43b858860fc2200704e7ca07147f003e5d8009c516a823f7fd76b5c85e`）。
详见 `docs/qa-0.59.0.md`。

## 5.182 alpha 0.60.0 —— OxUI preview：独立前端、入口与真实收发（2026-10-28 22:05–22:30）

- 用户提出前端重构级升级：在 Telegram UI 与现有美化之外增加 OxUI(preview)，
  入口放进引导页和 Ox 设置；OxUI 不使用 Telegram 的 cell/action bar，但功能
  要与 Telegram UI 同步；设计参考 Claude style skill 与社区方案，美观优先。
- 设计 token：Bone/Paper/Ink/Muted/Signal/Coral；serif 标题与助手正文、
  humanist sans 控件、mono 元数据；wordmark + Agent rail + 单列编辑式对话 +
  悬浮 composer；助手用短 chartreuse 竖线而非气泡，用户用 ink pill。刻意
  避开 cream+terracotta 的默认组合，不做统一圆角卡片墙或渐变装饰。
- `OxMode` 新增 `interface_mode`（telegram/oxui）；Ox Settings Mode 区新增
  Interface 行；引导页第 6 步新增 Interface 卡与 “Open OxUI preview” 链接。
- `OxUiPreviewActivity` 独立 Activity：读取 `OxAgentStore` 的真实 Agent/历史；
  发送走新增 `OxAgentsBridge.sendFromOxUi()`（复用 Telegram UI 同一本地路径）；
  Markdown 转 native spans；每 1.2 秒按历史签名刷新；pending 用户消息与
  “Agent 正在回复…”提示；中英文字符串包 119→120→121。
- 设备：`Studio Test`（tgId 555000016，合成历史）打开 OxUI，截图
  `docs/screenshots/qa-0.60.0-oxui-preview.png`；内置 Echo 中发送
  `OxUI test 0600`，OxUI 与 Telegram Echo 会话同时出现用户消息与回复
  （id 100067/100068），证明同步。设置页/引导页入口截图已归档；测试 Agent
  及其 chat/dialog/messages/memory 清理为 0；release 6009 启动无 FATAL/ANR。
- 边界：OxUI 仍是 preview，应用根仍为 Telegram UI；只有单聊，缺群聊、
  附件/媒体、搜索、设置、记忆页、Agent 详情、平板双栏、动态主题、
  reduced-motion 与完整无障碍；外部 Harness 回复在回合结束后落盘，OxUI
  不是 token 级流式。下一轮做第二轮视觉评审与 root 切换/导航。

**产物**：`dist/OrionekoX-0.60.0-alpha-arm64-x64.apk`（versionCode 6009，
sha256 `af6eae6d0e85306b043d108f1641e7fc402403b9f54677ebd47bc4f28181ad82`）；
debug `dist/OrionekoX-0.60.0-debug-arm64-x64.apk`（sha256
`fc3a837a7a83a9f23db0de0f4b0f27c784bd98b48b30b29be0223a1c828df76f`）。
详见 `docs/qa-0.60.0.md` 与 `docs/oxui-design.md`。

## 5.183 alpha 0.61.0 —— OxUI root 切换 + Agent 列表/搜索/详情/记忆（2026-10-28 22:40–23:05）

- OxUI 第二轮：LaunchActivity 在普通 launcher 启动且
  `interface_mode=oxui`、agent-only 时直接进入 `OxUiPreviewActivity`，
  深链/分享保持 Telegram 路径；OxUI root 顶部 Telegram UI 切回偏好并
  清任务重启，返回键在 root 模式 `moveTaskToBack`，不再露出 Telegram
  UI；Ox Settings/引导页选择 OxUI 后清任务重启进入 root。
- 新增 Agents 全屏面板：名称/备注搜索、Harness/最近消息摘要、点击进入
  对话、长按详情；详情页显示名称、Harness/模型、备注、
  `OxMemoryStore.stats()` 与最近记忆、Chat 按钮；消息长按复制。
- 修复详情页 header 被拉满并垂直居中（显式 44dp + TOP）、搜索键盘导致
  详情错位（打开详情前隐藏键盘）、列表摘要残留 Markdown、消息
  selectable 抢走长按复制等问题。
- 设备：合成 `Studio Test`（tgId 555000017，合成历史 + 一条记忆）验证
  root 启动、Telegram UI 切回、OxUI 重新进入、搜索只剩 Studio Test、
  详情记忆统计与 Chat 返回；截图 root/search/detail 已归档。release
  6109 启动无 FATAL/ANR；测试 Agent/chat/dialog/messages/memory 清零，
  `interface_mode` 恢复 telegram；代理/无障碍保持。
- 边界：只对普通 launcher 启动切换 root；搜索只搜 Agent 名称/备注；
  详情记忆只读；群聊、附件/媒体、会话搜索、设置页、Agent 编辑、平板
  双栏、动态主题、reduced-motion、完整无障碍仍未做；长按复制未做
  剪贴板自动回读；OxUI 仍不是 Telegram UI 的完整替代。

**产物**：`dist/OrionekoX-0.61.0-alpha-arm64-x64.apk`（versionCode 6109，
sha256 `62c0664029de7942a722919f0679ee16a0377b2568d2afdbb55b97c44ba509f3`）；
debug `dist/OrionekoX-0.61.0-debug-arm64-x64.apk`（sha256
`6e6cc172662318e38725dfd5c7fea12297e5ddb2f57da38d786b247999287e54`）。
详见 `docs/qa-0.61.0.md`。

## 5.184 alpha 0.62.0 —— OxUI 图片附件 + 本地群组 groundwork + 视觉 P0（2026-10-28 23:10–00:20）

- 按用户 token 策略，本轮以 2 个后台子代理并行完成特性审计与视觉评审，
  主线随后落地：composer 图片附件端到端、本地群组 listing/对话/发送
  groundwork（含本地创建群标记）、以及视觉评审 P0 修复（MUTED/LINE
  对比度、回复点呼吸动画、自动滚动仅在近底部/刚发送时、coral 失败重试）。
- 图片附件：＋按钮走 SAF `image/*`，URI 复制到 `files/oxui_media`（16MB
  上限），1:1 走 `handleOutgoingMedia`（真实 photo 消息 + generatePhotoSizes
  + 归档 + 视觉回合），群组走 `handleOutgoingGroupMedia`；OxUI 渲染
  `imagePath` 圆角图片，signature 纳入 imagePath，纯图片条目不再被跳过。
- 设备：echo 合成 Agent + 测试图 480x320，picker 选择 → 预览行 → 发送 →
  `handleOutgoingMedia count=1` → photo 消息 + echo 回复同时出现在 OxUI
  与 Telegram UI；截图 attach-preview / image-sent 已归档。
- 群组：`OxGroupStore.markLocallyCreated/isLocallyCreated` 落地并由
  `createLocalGroup` 打标；OxUI 面板 Groups 区 + 群对话 + 发送路径实现。
  **缺口**：`createLocalGroup` 目前无调用方，Telegram UI New Group 在离线
  本地账号上停在服务器请求，端到端群组链路暂无法验证（QA 文档如实标注），
  下一轮接 New Group 离线分支。
- 测试 Agent/图片/oxui_media/interface_mode 均清理恢复；release 6209
  覆盖启动无 FATAL/ANR。
- 版本规则：按 minor 从 0.61.0 → **0.62.0**（6209），patch 清零。

**产物**：`dist/OrionekoX-0.62.0-alpha-arm64-x64.apk`（versionCode 6209，
sha256 `62c546b0218171e81978dc4811dddbd5e3bc3f654ce9b908d3e0bd3ebd9bc22f`）；
debug `dist/OrionekoX-0.62.0-debug-arm64-x64.apk`（sha256
`b563e6c62d40b12c72e2b0aa859604e83299913359a6f5796563eb7f9cf3b6cf`）。
详见 `docs/qa-0.62.0.md`。

## 5.185 alpha 0.63.0 —— New Group 离线分支：本地群端到端（2026-10-29 00:20–01:10）

- 0.62.0 遗留：createLocalGroup 无调用方，New Group 流程在离线本地账号
  停在服务器请求。本轮在 `MessagesController.createChat`（CHAT_TYPE_CHAT
  且非 import）的 `syncAgents` 之后新增 agent-only 分支：selectedContacts
  映射为本地 agent（真实联系人跳过——离线无法邀请），非空则调用
  `OxAgentsBridge.createLocalGroup`（改 public；打 markLocallyCreated、
  persist chat/full、service message、refreshDialogRow），`runOnUIThread`
  发布 chatDidCreated/chatDidFailCreate；空 agent 集或异常都让创建页可
  恢复。agent-only 下绝不 fall through 到 sendRequest（ConnectionsManager
  会静默丢弃请求且不回调）。
- 后台子代理代码评审 HIGH 发现已吸收：agent-only 不 fall through、混合
  选择干净失败、通知经 runOnUIThread 发布、分支 try/catch；头像离线上传
  死锁列为下轮（本轮 QA 未选头像）。
- 设备端到端：New Group → 勾选 Studio Test → 命名 OxUI QA 630 → ✓ →
  `createLocalGroup id=1800000000`、`created_1800000000=true`、
  ChatActivity 呈现；OxUI Agents 面板 Groups 区列出该群（冷启动标题经
  getChatSync 回填）；OxUI 选中群（rail ink 群 chip）发送 GROUP-TEST-630
  → `runRound dialog=-1800000000`、用户消息 + echo 回复（id=100076/77）
  在 OxUI 与 Telegram chats 列表同步可见。
- 0.62→0.63 期间修复三处回归：renderGroupList 的 removeAllViews 清掉
  agent 行；冷启动群标题缺 getChatSync 回退；rail 群 chip/agent chip
  目标重置丢失。
- 测试 Agent/QA 群（chats blob、dialogs、messages、ox_groups 键）清理为
  0；interface_mode 恢复 telegram；release 6309 无 FATAL/ANR。
- 版本规则：按 minor 从 0.62.0 → **0.63.0**（6309），patch 清零。

**产物**：`dist/OrionekoX-0.63.0-alpha-arm64-x64.apk`（versionCode 6309，
sha256 `1160c2ed6b62df5c824fefd2aff7f1128f0bebdb4b7fe1e230d227d04d250cb4`）；
debug `dist/OrionekoX-0.63.0-debug-arm64-x64.apk`（sha256
`25a4179a82fd20f1cf41c911845beca968919a0cabe416767e6ee342c8455d28`）。
详见 `docs/qa-0.63.0.md`。

## 6. 与开发报告路线图的对应关系

- 本次完成的是 **M0 的一部分**：基座 fork 就绪、patches/ 目录化、独立签名与包名、可复现构建链路（JDK+SDK+NDK 全套本机工具链已持久化在 `~/orioneko-toolchain`）。
- 尚未开始的 M0 项：pelegram 隧道复现（libbox AAR）、PRoot 容器 PoC、MCP SDK on Android PoC、GPLv2/GPLv3 组合法务结论。
- alpha-0.0.2 候选：自有 APP_ID 注入机制、品牌图标、官方 12.x→13.0.3 基线的 rebase 演练已顺带完成（首次即基于最新 master）。
## 5.186 alpha 0.64.0 —— 群头像离线分支 + sender 归属 + 定向回复（2026-10-29 01:10–02:30）

- 0.63.0 遗留三项一次收口：① New Group 选头像走 FileUploader，agent-only
  下请求被静默丢弃、创建页转圈死锁；② 群回复 meta 只有群名；③ 群里无法
  指定某位 agent 回复。
- 头像离线分支：`ImageUpdater.processBitmap` 在生成 big/small PhotoSize 后
  agent-only 直接回调 `didUploadPhoto(null,null,0,null,big,small)` +
  cleanup（不进 FileUploader）；`GroupCreateFinalActivity` 的 Done 门槛与
  进度条按 `OxMode.isAgentOnly()` 跳过上传态；`chatDidCreated` 调
  `OxAgentsBridge.attachLocalChatAvatar`：构造 `TL_chatPhoto`
  （photo_id=|chatId|*31+13、volume_id=-photoId、local_id 97/99，与
  readParams 反序列化约定一致），把 ImageUpdater cache 文件按
  `getPathToAttach` 各变体拷贝成对应磁盘名——重启后从 MessagesStorage
  反序列化即可按 volume/local id 命中文件。
- sender 归属：`OxChatEntry` 新增 `sender`（JSON 向后兼容）、
  `OxGroupStore.appendChat` 重载、`finishTurn` 群分支写 `s.agent.id`、
  OxUI meta `resolveAgentName` 显示 “Name · time”。
- 定向回复：OxUI 助手行长按菜单 `Copy / Reply <name>` → 回复 chip →
  `OxAgentsBridge.sendLocalGroupTextTo` 强制 `startGroupTurn(forced)`（log
  `groupReply forced agentId=…`）；换群/点 chip/发送后清除。
- 设备端到端（真实 UI 流）：相册选图 → 裁剪 ✓ 返回即显示头像缩略图、
  **无死锁**；创建 `OxUI AVA 640`（id=1800000002）→
  `createLocalGroup id=1800000002 agents=1`、
  `attachLocalChatAvatar attached chatId=1800000002 photoId=55800000075`、
  群聊头部渲染橙色头像；**force-stop 重启后 chats 列表头像仍在**。sender
  （`Translator · 01:38`）与定向（TARGET-640 由 Translator 回复、其他成员
  未触发）在 QA 群 1800000001 验证。
- 坑：软键盘把 ✓ FAB 顶到 (964,1411)（原 (964,2300)）；根级
  `assembleAfatStandalone` 首次在 `:TMessagesProj_App` 的 media3 variant
  解析 1 秒失败，模块级 `:TMessagesProj_AppStandalone:assemble*` 为历轮
  成功调用（2m18s，643 tasks）；旧群历史无 sender 字段不显示归属（不迁移）。
- 清理：QA 群 1800000001/1800000002 与测试 agent oxui_grp_630 的
  chats/dialogs/messages/users/contacts/ox_groups 键全部为 0；
  `/sdcard/Pictures/oxui-640.png` 删除；interface_mode=telegram；无障碍与
  代理恢复；release 6409 启动无 FATAL。
- 版本规则：按 minor 从 0.63.0 → **0.64.0**（6409），patch 清零；中文包
  OX_ZH_PACK_VERSION 124（OxUiAttach/Groups/GroupMembers/NotSent/Copy/
  Reply/ReplyChip 等）。

**产物**：`dist/OrionekoX-0.64.0-alpha-arm64-x64.apk`（versionCode 6409，
sha256 `905a3d545d3ab91cc4aba3bf81908d01eef6364c5cba5e1035320fa4df9cb005`）；
debug `dist/OrionekoX-0.64.0-debug-arm64-x64.apk`（sha256
`703a202334becc03138e602db7cf2e817374fa022380d92afefc4dd95470ae2a`）；
构建日志 `/tmp/ox-build-0640-final.log`；QA 详见
[docs/qa-0.64.0.md](qa-0.64.0.md)。
## 5.187 alpha 0.65.0 —— 已有群改/删头像离线分支 + 强制回复修复 + quote 语义 + 视觉三轮快赢（2026-10-29 02:42–03:55）

- 0.65.0 三项首要：① 已有本地群改/删头像仍走服务器请求（0.64 只覆盖创建
  路径）；② 修复 0.64.0 定向回复假验证——`sendMessage` 在 `dispatchSend`
  之前 `clearReplyTarget()`，强制路径从未生效（单成员群使 TARGET-640 的
  假阳性漏检，logcat 无 `groupReply forced` 行）；③ 定向回复 quote 语义。
- 头像编辑：`ChatEditActivity.didUploadPhoto` 最前插 agent-only 分支 →
  `updateLocalChatAvatar`（photoId=millis*1000+chatId%1000，与创建 id 三数量
  级隔离、同毫秒跨群唯一）；不设 hasUploadedPhoto，UI 由
  updateInterfaces→setAvatar 刷新。首版分支位置是死代码（ImageUpdater 离线
  回调全空参数走外层 else），子代理评审抓出后前移修正。删除路径：上游
  useAttachMenu=true 丢弃 onDelete 回调，agent-only 有头像时拦截弹
  Set New Photo / Remove photo 对话框 → `removeLocalChatAvatar`
  （TL_chatPhotoEmpty；TL_chat 无条件序列化 photo，无需清 flag）。
- quote 语义（快照方案，子代理设计注）：OxChatEntry 增 replyToSender/
  replyToText（optString 向后兼容不迁移）；groupMessages 向最后一条 user
  消息注入 `（回复 @Name：「摘录≤160」）`；Telegram UI 路径 onGroupMessage
  抽取同一快照；OxUI chip 显前 24 字、气泡上渲染 MUTED 引用行；落盘 content
  保持纯净。
- 视觉三轮（子代理评审 12 项落地 7 项）：图文一体 ink 卡片、0.6 视口
  CENTER_CROP 高度上限、发送后滚动二次对齐、删 Preview 徽章+统一头部胶囊、
  rail 36dp 渐隐、强调条 MATCH_PARENT、副标题剥离 markdown 行首；其余 5 项
  （自定义长按菜单、用户气泡时间戳、类型尺度吸附、附件行容器、竖长截图卡片
  异常）列下轮。
- 设备端到端：改头像（photoId=1793215169213003，无死锁，编辑页即时渲染，
  force-stop 重启头像仍在）；删头像（removeLocalChatAvatar cleared，init
  回落）；TARGET-650Q → `groupReply forced agentId=echo`；落盘条目含
  reply_to_sender/reply_to_text 且气泡渲染引用行；CAP-650 图文一体卡。
- 评审 backlog：群 emoji 头像创建静默丢头像（agent-only）、用户头像族 RPC
  未守护、changeChatTitle 离线回滚。
- 本轮模拟器 qemu 自发崩溃一次（打开聊天页时），按既定流程重启（默认
  snapshot + 锁清理）并 `adb root` 恢复数据访问。
- 清理：QA 群 1800000003 与图片/oxui_media 全 0；echo chat.json 仅移除本轮
  4 条 QA 条目（保留既有历史）；interface_mode=telegram；无障碍/代理恢复；
  release 6509 无 FATAL。
- 版本规则：按 minor 从 0.64.0 → **0.65.0**（6509），patch 清零。

**产物**：`dist/OrionekoX-0.65.0-alpha-arm64-x64.apk`（versionCode 6509，
sha256 `9801360d7f50d3fa9cdcaa6222707ca67d1b3a76b704b6815ff34e73a658250f`）；
debug `dist/OrionekoX-0.65.0-debug-arm64-x64.apk`（sha256
`9be0280b77349d98930bd837ee9baa7df129ca4c0c4c333536d47c020eb4a336`）；
构建日志 `/tmp/ox-build-0650-final.log`；QA 详见
[docs/qa-0.65.0.md](qa-0.65.0.md)。
## 5.188 alpha 0.66.0 —— 视觉遗留 5 项 + OxUI 会话搜索（2026-10-29 04:05–09:40）

- 本轮起按用户 2026-10-29 指示：模型回 opendesign/deepseek-v4.1-flash、
  取消多并发子代理策略，单主线推进（automation prompt 与 memory 已同步）。
- 视觉遗留 5 项全部落地：①自定义长按菜单（menuHost + bone 卡片、SIGNAL
  Reply 圆点、锚点定位/返回键/重建自动关闭，替换三处 AlertDialog/直接
  copy）；②用户气泡时间戳（两分支右对齐 11sp mono）与长按菜单；③类型
  尺度吸附（审计 52 站点→30 处修改，全部向 11/12/14/16/17/28 吸附，
  RIPPLE/SENDING_ALPHA 常量 + sansMedium helper）；④附件行 PAPER 胶囊
  + `WxH · KB` mono 副行；⑤竖长截图卡片异常定位——设备 dump 证实
  ImageView 与 caption 左缘对齐、垂直零间隙，caption 本就在同一 ink 卡
  内，0.65 的“图文割裂”为读图误判；另做图片卡内水平居中观感修正。
- OxUI 会话搜索 v1（设计注实现）：面板搜索框同时检索 agent 名/备注/群
  标题与本地聊天内容（worker 线程扫描 OxAgentStore/OxGroupStore，每目标
  ≤3、全局 ≤12、220ms 防抖 + scanGen 丢弃过期），Messages 小节 + SIGNAL
  底色高亮命中 + mono 时间，点击打开会话；空态仅在全无命中时出现；新增
  OxUiSearchAll/OxUiSearchMessages（中文包 125）。
- 设备验证：1:1 菜单/时间戳、blue 搜索两条高亮命中、附件胶囊
  `1080x2400 · 302 KB`、图卡 bounds 对齐、群菜单 Copy/●Reply、chip 引用
  摘要、TARGET-660Q → `groupReply forced agentId=echo dialog=-1800000004`
  且气泡渲染引用行；release 6609 无 FATAL。
- 自造回归并修复：类型尺度批量替换误伤 `sansMedium()` 定义导致
  StackOverflowError（启动即崩），修复后复测；批量替换须排除定义处。
- 清理：QA 群 1800000004 全 0、Sakura chat 仅移除本轮条目、oxui_media/
  tall660.png 删除、interface_mode=telegram。
- 版本规则：按 minor 从 0.65.0 → **0.66.0**（6609），patch 清零。

**产物**：`dist/OrionekoX-0.66.0-alpha-arm64-x64.apk`（versionCode 6609，
sha256 `f4f3a1bf985fc07f7af369ab335adde20c3370c7ceb371c6198f83892bc121f0`）；
debug `dist/OrionekoX-0.66.0-debug-arm64-x64.apk`（sha256
`1bda0bd9ee635b6d2ece7b932bc86b1cd48c9f39a623050d340af9beb9008881`）；
构建日志 `/tmp/ox-build-0660-final.log`；QA 详见
[docs/qa-0.66.0.md](qa-0.66.0.md)。
## 5.189 alpha 0.67.0 —— 暗色变体 + reduced-motion/可访问性 + 会话内搜索（2026-10-29 09:40–10:15）

- 暗色调色板：颜色常量 static final → 实例字段，onCreate 按系统 night mode
  解析两套（暗色 BONE #141917 / INK #EDE6D6 / MENU_PAPER #202724 等，
  SIGNAL 共用）；语义不变（INK=气泡/正文 → 暗色为奶油泡深字），状态栏
  图标随暗色去 LIGHT_* 标志；菜单纸色字面量并入 MENU_PAPER。
- reduced-motion：animationsEnabled() 读系统 ANIMATOR_DURATION_SCALE，
  两处 overlay 淡入与首渲染 fade 关闭时直接落终态。
- 可访问性：补纯符号控件 contentDescription（取消/移除附件/附件/发送/
  header 搜索/rail chips/菜单行/图片卡= caption）。
- 会话内搜索 v1.5：header ⌕ → 搜索条（输入 + n/N + ↑/↓ + ✕），150ms
  防抖扫描当前会话 user/assistant，命中按 ts 存序、循环导航、滚动留
  80dp、动画开启时整行 900ms SIGNAL 闪烁；行以 ts 为 tag；窗口外命中
  不跳转（计数仍准）。
- 设备验证：night yes 暗色截图（炭黑底/奶油泡/信号色保留/状态栏反色）、
  night no 无回归；animator scale 0 面板秒开无 FATAL；"blue" 搜索
  1/2→↓→2/2 整行高亮闪烁；uiautomator dump 确认 a11y 描述齐全；
  release 6709 无 FATAL。
- 清理：本轮只读搜索未产生 QA 数据；interface_mode/night/animator 复原。
- 版本规则：按 minor 从 0.66.0 → **0.67.0**（6709），patch 清零。

**产物**：`dist/OrionekoX-0.67.0-alpha-arm64-x64.apk`（versionCode 6709，
sha256 `32a58b343168ad5bebaf4a3a32e80186ca1e7e2572c9bad937bd84276749c2d8`）；
debug `dist/OrionekoX-0.67.0-debug-arm64-x64.apk`（sha256
`97c3c988eb96ac278df5cc626c496d4334ad99ac9580d487eb98cb65a56d25b1`）；
构建日志 `/tmp/ox-build-0670-final.log`；QA 详见
[docs/qa-0.67.0.md](qa-0.67.0.md)。
## 5.190 alpha 0.68.0 —— OxUI 设置页 + Agent 编辑 + 应用内主题切换（2026-10-29 10:00–10:30）

- 主题偏好：OxMode 增 `oxui_theme`（system|light|dark）；resolvePalette
  优先该偏好，缺省跟随系统 night mode。
- 设置页：入口在 Agents 面板 footer “Settings”（header 曾加 ⚙ 但 360dp
  屏 4 控件导致标题换行，已移除）；THEME 三行单选（SIGNAL 圆点 +
  active 标记），选择即写偏好并 recreate 即时换肤；ABOUT 显示
  BuildConfig 版本与 local-first 说明。
- Agent 编辑：详情页 Edit 按钮 → NAME/REMARK/MODEL/PERSONA + SKILLS
  多选（OxSkillStore.list()，空列表有提示）+ Cancel/Save；Save 经
  loadAgent 写回 name/remark/model/agentsMd/skillIds 并刷新列表。
- BACK 修复：onBackPressed 增加 overlay 优先关闭（此前编辑页按 BACK
  直接退出应用）。
- 设备验证：设置面板/主题双切换（dark/light 均落盘并即时重建）、
  Agent 编辑回填→改 REMARK+勾技能→Save 落盘（remark/skill_ids 正确、
  名称人设未动）→详情回显→Cancel 不落盘→还原数据归零；release 6809
  无 FATAL。
- 清理：agent.json 已还原（remark 空/skills 空）、oxui_theme=system、
  interface_mode=telegram。
- 版本规则：按 minor 从 0.67.0 → **0.68.0**（6809），patch 清零。

**产物**：`dist/OrionekoX-0.68.0-alpha-arm64-x64.apk`（versionCode 6809，
sha256 `3b9804b2789212057e9c348e362eee3a10bf3979c7bfc0d0652907b0375cffa1`）；
debug `dist/OrionekoX-0.68.0-debug-arm64-x64.apk`（sha256
`50ef0815b4a6bdbadc42747f62a77f65398660254eb5bf9e71c0811f80a0794d`）；
构建日志 `/tmp/ox-build-0680-final.log`；QA 详见
[docs/qa-0.68.0.md](qa-0.68.0.md)。
## 5.191 alpha 0.69.0 —— 多模型设计擂台合成：皮肤系统 + 外壳层 + 终端 v3（2026-10-29 10:00–12:05）

- 多模型设计擂台：7 版 4 屏设计（claude-haiku-5.5 / gpt-6-luna ×3 方向 /
  gpt-6-sol / gpt-5.6-sol / oxui-ds），简报从零、渲染截图对比；gpt-6-pro
  账号不可用、glm-5.3-flash 网关 524×3、astra 按指示避开；结论与截图存
  `design-battle-shots/`。
- 合成落地：青墨调色板（bone/paper/深青墨）+ 陶土 clay 专用于用户气泡与
  引用（signal=动作，clay=人声）；Agent 行与搜索改单字头像 + 模型标签；
  assistant meta 单字徽 + 图片卡 IMG·WxH 角标。
- **OxUiSkin 皮肤系统**：9 套预设（前 7 套即擂台方案沉淀）+ 自定义 token
  编辑器（7 色 hex、radiusSharp/Soft/Round、字号 Compact/Normal/Large、
  Copy JSON 导出）；plate 圆角与关键字号按皮肤缩放；设置页 APPEARANCE
  列表即时换肤并落盘。
- **外壳层 DIY**：Navigation（Rail/Bottom/Floating）、Material（Solid/
  Acrylic/Mica/Glass）、Dock position（Top/Above input/Bottom）、组件开关
  Rail chips/Footer status，全部进皮肤 JSON；dock 改为流式插入（不再覆盖
  输入区），玻璃材质为半透明+信号描边。
- **终端 v3**：按 Termux 实机学习（全屏黑底白 mono/绿色提示/双行键帽/
  properties 宏与 cursor/session 快捷键）+ Termius 公开设计（下划线 tab/
  状态行/键帽分组/状态脚注）重做：会话 tab（≤4、独立 log/history）、
  LOCAL 状态行、分组快捷键（Esc/^L/Tab|↑↓|符号）、↑↓历史、Tab 补全、^L
  清屏、Aa 字号（写皮肤）、13sp/3dp 行距、荧光 ↵ 与底部状态行；
  Termius APK 环境内无法安装（Play/APK 镜像均被拦），如实记录。
- 事故与恢复：验证 dock 顶部时曾以最小 prefs 覆盖 orioneko_config 导致
  agent_only 等键丢失，立即重建恢复并验证 agent-only 首页；模拟器宿主
  X server 丢失致 qemu 退出，改 `-no-window -gpu off` 恢复。
- Termius 7.5.5（用户提供 APK）实装并使用：引导页→免登录本地模式→
  Vaults/Hosts→设置页 Terminal 组（Font & colours: Termius Dark/8/Source
  Code Pro Medium、Hotkeys、Customize keyboard、autocomplete、pinch to
  zoom、back buffer）；据此补充终端 options：主题三选（Termius Dark/
  Pure black/Solarized）、Quick keys 开关、Autocomplete 开关、捏合缩放
  0.8–1.4×（写回皮肤 JSON），Solarized 实测生效。
- 清理：皮肤/主题恢复默认（Follow system），interface_mode=telegram；
  Termux 留在模拟器供对照（非用户数据）。
- 版本规则：按 minor 从 0.68.0 → **0.69.0**（6909），patch 清零。

**产物**：`dist/OrionekoX-0.69.0-alpha-arm64-x64.apk`（versionCode 6909，
sha256 `fcb0a90b36102eafaac72e97f615c71ea116d982e1c231b4686d724c13a37b3b`）；
debug `dist/OrionekoX-0.69.0-debug-arm64-x64.apk`（sha256
`b0a29643f5801b98ff3b9ae12de07c9afc81e210991ec4fea1d6b7c1ecdabf48`）；
QA 详见 [docs/qa-0.69.0.md](qa-0.69.0.md)。
## 5.192 alpha 0.70.0 —— 组件级 DIY + 皮肤导入 + 真实模糊 dock + 终端配色（2026-10-29 12:25–13:05）

- 组件级 DIY：OxUiSkin 增 density（cozy/compact，消息行距 0.62×）、
  showTimestamps、showAccent、dockItems（bitmask）；设置页 COMPONENTS
  小节（Density 二选、两个开关、Dock items 四个显隐）；全部进皮肤 JSON。
- 皮肤导入：Customize → Import JSON…（粘贴面板默认带出当前 JSON）→
  OxUiSkin.fromJson → custom 落盘 + recreate；非法 JSON 提示。
- 真实模糊 dock：floating dock 改为覆盖层，updateDockBlur() 快照 dock
  背后像素→1/6 降采样→两遍 box blur→按材质 tint（glass 荧光 20%/mica
  骨纸 80%/acrylic 60%）→圆角 LayerDrawable；滚动 260ms 防抖更新；
  失败回退 drawable。
- Termius 风格终端配色内置 11 套（Termius Dark/Light、Solarized
  Dark/Light、Dracula、One Dark、Nord、Gruvbox Dark、Tokyo Night、
  Monokai、Pure black），每套 bg/text/dim/accent，提示符/↵/tab 下划线/
  输入框描边随 accent；选项面板可滚动 + 三色 swatch；活动 tab 文字色
  随主题（修浅色不可读）。
- 设备验证：玻璃 dock 快照模糊可见；Termius Light / Solarized Light /
  Nord 实测即时生效并持久化；release 7009 无 FATAL；组件开关与导入
  UI 已接入（逐项截图下一轮补）。
- 版本规则：按 minor 从 0.69.0 → **0.70.0**（7009），patch 清零。

**产物**：`dist/OrionekoX-0.70.0-alpha-arm64-x64.apk`（sha256
`02a220f9adf3ebd2387faa86b65e19b91cd8966feb0354633f9ae96dd39514b0`）；
debug `dist/OrionekoX-0.70.0-debug-arm64-x64.apk`（sha256
`86b9ba1f15b62ede0d9697438040bbfd62c8b2c1dd9fa939af7d732a706c5aa9`）；
QA 详见 [docs/qa-0.70.0.md](qa-0.70.0.md)。
## 5.193 alpha 0.71.0 —— 终端配色对比度重调 + PWD/PST（2026-10-29 12:50–13:05）

- 用户反馈 Nord 等预设看不清字、且 Termius 有一键输密码快捷键、钥匙
  按钮突兀。用 WCAG 对比度公式审计 11 套配色：dim 普遍不足（Nord 1.69、
  One Dark 2.32、Solarized Light 2.48），且渲染逻辑把缩进输出误当 dim。
- 重调全部 11 套：text ≥6.59、dim ≥4.06、accent ≥4.75；渲染改为仅
  `session ...` 横幅使用 dim，命令输出恢复正常色；Nord 实机复查清晰。
- 参考 Termius：热键行全为等宽文本键。移除 emoji 🔑/⧉，改为 PWD/PST
  文本键（同字号同键帽、行尾滚动可达）；选项面板新增本地 TERMINAL
  SECRET（密码输入 + Save，prefs 明文存储、不入皮肤 JSON）；PWD 一键
  填入、PST 粘贴剪贴板；未设置时 Toast 引导。
- 设备验证：Nord 可读；PWD 填入 abc123 成功；键行滚动后风格统一；release
  7109 无 FATAL。
- 版本规则：功能新增 → **0.71.0**（7109），patch 清零。

**产物**：release `ea48e84b7b289c6ac807e6e9ac6447656f954754e0f618ceaa8151b16aeca7a3`；
debug `8738b14ef56473710c0a93943f81cff3eca96b2e89e9a1c7f73461287c8ccc09`。
## 5.194 alpha 0.72.0 —— OxUI 对标：普通文件附件端到端（2026-10-29 13:10–13:40）

- 主线切到「OxUI 补全与打磨」后的首个对标项：OxUI 之前只能发图片，
  本轮补齐普通文件（document）发送与渲染。
- 桥接：`handleOutgoingMedia` 新增普通文件分支（扩展名→MIME、
  buildLocalDocument + injectDocument、prompt 占位与大小），Stream/turn
  历史/echo 条目写入 filePath/fileName；startTurn/startMediaTurn 文件重载。
- OxUI：＋ 改为 Photo/File 设计菜单；File 走 GET_CONTENT */* + 原名拷贝
  到 oxui_media（25MB、重名序号）；预览胶囊 📄+名称+大小；dispatchSend
  支持 filePath；renderConversation 渲染 PAPER 文件气泡（名称/大小/说明/
  时间戳），pending 与失败重试同步支持。
- 设备验证：Echo 1:1 选 txt → 胶囊 → 发送 → logcat TL document
  （type=9）+ Echo 回复，OxUI 文件气泡与 prompt 文本正确；force-stop
  重启后气泡仍在。release 7209 无 FATAL。
- 附带发现：Yuki 历史中的 `recallnull…` 工具名条目为旧构建产物，现行
  流式解析已过滤 null 名称片段。
- 版本规则：功能新增 → **0.72.0**（7209），patch 清零。

**产物**：release `209554bc3c30b003fc0384f502edc33d0b33c222ea25636992791fb0459f761e`；
debug `c40d434cd464a49c319c3dd01040aa4e40f41868266cc776a932b5051c768ac5`。
## 5.195 alpha 0.73.0 —— 群组文件气泡 + 消息转发 + 发送键修复（2026-10-29 13:45–14:10）

- 群组文件：handleOutgoingGroupMedia 记录首文件；onGroupMessage/
  startGroupTurn 文件重载；群用户条目写 filePath/fileName，OxUI 群历史
  复用文件气泡（PAPER 胶囊+大小+说明+时间戳）。
- 转发：助手行菜单 Copy / Reply / Forward to…（1:1 Copy / Forward），
  用户气泡加 Forward；目标面板列 Agent（单字头像）与本地群（👥），
  发送 `〔转发〕\n正文`（Agent 走 sendFromOxUi 真实回合，群走
  sendLocalGroupText 并切换）。
- 真 bug 修复：updateSendVisual 未计 pendingFilePath，纯文件无文字时
  发送键禁用（0.72 文本+文件用例掩盖）；修复并复测。
- 设备验证：群 id=1800000000 内文件发送（修复前禁用/修复后 TL doc +
  runRound + OxUI 文件气泡）；长按菜单与转发面板、Echo 收到〔转发〕
  消息并回复（id=100006/100007）。release 7309 无 FATAL。
- 版本规则：功能新增 → **0.73.0**（7309），patch 清零。

**产物**：release `508b935b2bbbe4ad6c9336751d33f87f8e0c1002cb597c28b304ae20768a3bd3`；
debug `af2dd3e67008948b6dc939c5fcd0740a904b09c21857fa79a1fb493338477e5d`。
## 5.196 alpha 0.74.0 —— 消息删除 + Agent 置顶 + 记忆架构报告（2026-10-29 14:05–14:35）

- 删除：OxAgentStore.removeChatEntry / OxGroupStore.removeEntry 按 ts+role
  删本地条目；deleteOxEntry 再按 uid+date±300s 反序列化 data 比对正文
  前 24 字符定位 TL mid，走 MessagesController.deleteMessages（agent
  dialog 分支强制 cacheOnly，不发服务器请求）；无正文时仅 8s 内按时间
  兜底。OxUI 助手/用户/图片/文件气泡均加 Delete message + 二次确认，
  菜单 accent 参数从 boolean[] 改为颜色 int[]（删除珊瑚红）。
- 置顶：Agent 详情页 Chat/Edit 旁新增 Pin/Unpin，切换 pinned 后
  saveAgent、重载并刷新 rail；listAgents 原有 pinned 优先排序。
- 研究报告：docs/memory-architecture-report.md，对照 OxMemoryStore 现状
  评估 Markdown 本体 + 关键词/向量混合检索，指出五项缺口与 0.75→0.77
  路线；明确现有 embed 为 FNV 哈希投影，不是语义向量。
- 设备验证：删除用户消息（date 比 store ts 早 19s）与助手长回复均
  mids=1、cache4.db 对应行消失、chat.json 条目移除、Telegram UI 同步
  不再显示；API Agent Pin 后 rail 置顶、agent.json pinned=true、重启
  保持，Unpin 后恢复原顺序。release 7409 无 FATAL。
- 版本规则：功能新增 → **0.74.0**（7409），patch 清零。

**产物**：release `c4686060f2cefaa6b2f736c3d7e22a7731b0d54b41c20f3999b8938122c7eaf0`；
debug `00cf1a3f1782630d453f5149638b232323fe31c2c4127a0506f7b0e8b2edc0dd`。
