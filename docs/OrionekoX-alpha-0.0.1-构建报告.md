# OrionekoX alpha 0.0.1 构建报告

> 构建日期：2026-10-24 · 基座：DrKLO/Telegram @ `0aae959`（官方 13.0.3 / 7183）· 结果：**BUILD SUCCESSFUL（12m47s，341 tasks）**

## 1. 交付物

| 文件 | 说明 |
|---|---|
| `OrionekoX/dist/OrionekoX-0.0.1-alpha-arm64.apk` | 38MB · arm64-v8a 单 ABI · R8 压缩 + 资源收缩 |
| `OrionekoX/dist/OrionekoX-0.0.1-alpha-arm64.apk.sha256` | `c6c7ca2a649b6e534c450ff5bf78cf4f7d70904b6dca529237fc47d38e9f1c9c` |
| `OrionekoX/patches/0001-*.patch`、`0002-*.patch` | 对上游的全部改动（目录化补丁，M0 纪律的第一次实践） |
| `OrionekoX/TMessagesProj/config/orioneko.keystore` | 签名密钥（密码 `CHANGE_ME`，alias `orioneko`） |

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
| `RELEASE_KEY_*` → `CHANGE_ME/orioneko` | `gradle.properties` | 指向自有签名密钥 |
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

## 6. 与开发报告路线图的对应关系

- 本次完成的是 **M0 的一部分**：基座 fork 就绪、patches/ 目录化、独立签名与包名、可复现构建链路（JDK+SDK+NDK 全套本机工具链已持久化在 `~/orioneko-toolchain`）。
- 尚未开始的 M0 项：pelegram 隧道复现（libbox AAR）、PRoot 容器 PoC、MCP SDK on Android PoC、GPLv2/GPLv3 组合法务结论。
- alpha-0.0.2 候选：自有 APP_ID 注入机制、品牌图标、官方 12.x→13.0.3 基线的 rebase 演练已顺带完成（首次即基于最新 master）。
