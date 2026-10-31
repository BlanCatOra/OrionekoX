# P2-8 收口：OxUI 设置面板硬编码英文清理

验收时间：2026-11-01 00:50–00:58（Asia/Shanghai）
设备：`emulator-5554`，AVD `orioneko_test`
包：`io.github.blancatora.orionekox.debug`，`versionCode=8909`，`versionName=0.89.0-debug`
APK：`TMessagesProj_AppStandalone/build/outputs/apk/afat/debug/app.apk`
APK sha256：`ca70959348250263bbac0f3868ee9f37413651d6a5a98e8656a9b4506c1fdc5f`
安装：`lastUpdateTime=2026-11-01 00:56:05`

---

## 结论

| 项 | 结果 |
|---|---|
| 24 项硬编码英文 | ✅ 全部清掉，改走 `R.string` |
| 新增词条 | 26 条（英文 + 中文各一份） |
| 语言包版本 | 127 → **128** |
| 界面实测 | ✅ 设置面板全中文 |
| **顺带修掉一个真 bug** | `dockPosition` 选项点了不生效 |

---

## 1. 改了什么

### 硬编码 → 资源

`OxUiPreviewActivity` 的设置面板里，`shellChoice` / `shellToggle` / `dockItemToggle` 三个辅助方法原本接收 `String label` 并直接 `setText(label)`。改成接收 `int labelRes`，内部走 `LocaleController.getString()`。

| 原硬编码 | 新 key | 中文 |
|---|---|---|
| `Navigation` | `OxUiNavStyle` | 导航 |
| `Rail` | `OxUiNavRail` | 侧栏 |
| `Bottom bar` | `OxUiNavBottom` | 底栏 |
| `Floating dock` | `OxUiNavFloating` | 悬浮坞 |
| `Dock position` | `OxUiDockPosition` | 坞位置 |
| `Top (under header)` | `OxUiDockTop` | 顶部（标题下） |
| `Above input` | `OxUiDockAboveInput` | 输入框上方 |
| `Bottom of screen` | `OxUiDockBottom` | 屏幕底部 |
| `Material` | `OxUiMaterial` | 材质 |
| `Solid` | `OxUiMatSolid` | 实色 |
| `Acrylic` | `OxUiMatAcrylic` | 亚克力 |
| `Mica` | `OxUiMatMica` | 云母 |
| `Glass` | `OxUiMatGlass` | 玻璃 |
| `Rail chips` | `OxUiRailChips` | 侧栏标签 |
| `Footer status` | `OxUiFooterStatus` | 页脚状态 |
| `Density` | `OxUiDensity` | 密度 |
| `Cozy` | `OxUiDensityCozy` | 宽松 |
| `Compact` | `OxUiDensityCompact` | 紧凑 |
| `Timestamps` | `OxUiTimestamps` | 时间戳 |
| `Assistant accent rule` | `OxUiAccentRule` | 助手强调线 |
| `on` / `off` | `OxUiOn` / `OxUiOff` | 开 / 关 |
| `shown` / `hidden` | `OxUiShown` / `OxUiHidden` | 显示 / 隐藏 |
| `Dock item <label>` | `OxUiDockItemDesc` | Dock 项 %1$s |
| `Agents`（dock 项） | `OxUiAgents`（改中文值） | 智能体 |
| `Settings`（dock 项） | `OxUiSettings` | 设置 |

### 顺带修的 bug

原代码用 label 字符串反推字段名：

```java
final String fieldName = "Navigation".equals(label) ? "navStyle"
        : ("Density".equals(label) ? "density" : "navMaterial"); ```

`Dock position` 落到 else 分支 → 写成 `navMaterial`。**点「屏幕底部」实际改的是材质，坞位置永远不变。**

改成 `shellChoice(int labelRes, String field, ...)`，字段名显式传入。这同时解掉了「翻译 label 会断掉字段映射」的耦合——不改这个，翻译反而会让面板彻底失灵。

---

## 2. 实测证据

### 语言包升级

```
shared_prefs/langconfig.xml → ox_zh_version = 128
源码 LocaleController.OX_ZH_PACK_VERSION = 128
```

### 设置面板实测文本（uiautomator dump）

上半：

```
设置 / ×
主题
  跟随系统 / 生效 / 浅色 / 深色
外观
  青墨 · 骨纸荧光 / 暖纸 · 陶土橙 / 鼠尾草 · 静室 / 夜色 · 荧光
  工作台 · 朱红 / 奶油 · 深青 / 方格纸 · 蓝 / 阅读 · 高对比 / 墨蓝 · 夜航
  自定义… / 颜色 · 圆角 · 字体
外壳
  导航 / 侧栏 / 生效 / 底栏
```

中段：

```
屏幕底部
材质 / 实色 / 生效 / 亚克力 / 云母 / 玻璃
侧栏标签 / 开
页脚状态 / 开
组件
密度 / 宽松 / 紧凑 / 生效
时间戳 / 开
助手强调线 / 开
```

下段：

```
Dock 项
  智能体 / 显示
  搜索 Agent 和消息 / 显示
  终端 / 显示
  设置 / 显示
关于
  OrionekoX alpha 0.89.0 · OxUI preview
  本地优先：聊天、记忆与设置都保留在本机。
```

**上一轮验收时这 24 项全是英文，现在全部中文。**

### dockPosition bug 修复验证

| 操作 | `orioneko_config.xml` 结果 |
|---|---|
| 初始 | `dockPosition: aboveInput`，`navMaterial: solid` |
| 点「屏幕底部」 | `dockPosition: bottom`，`navMaterial: solid`（**未被误改**） |
| 点「输入框上方」 | `dockPosition: aboveInput` |

修复前点「屏幕底部」会写成 `navMaterial: bottom`（非法值），`dockPosition` 不动。现在双向都正确。

---

## 3. 静态核对

`OxUiPreviewActivity` 引用 83 个 `R.string.Ox*`：

- 缺 `values/strings.xml`：**0**
- 缺 `assets/oxlang/zh-cn.xml`：**0**
- oxlang 里值不含中文的：**1**（`OxUiTelegram = Telegram UI`，专有名词，保留英文正确）

---

## 4. 改动文件

```
TMessagesProj/src/main/java/org/telegram/ui/OxUiPreviewActivity.java   +42 −36
TMessagesProj/src/main/res/values/strings.xml                          +26
TMessagesProj/src/main/assets/oxlang/zh-cn.xml                         +27 −1
TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java  +1 −1
```

4 文件，+96 / −38，在「15 文件 / 800 行」限制内。

---

## 5. 未覆盖

- `AppHuawei` / `AppHockeyApp` 变体未构建验证（`TMessagesProj_App` 因 `google-services.json` 缺 `io.github.blancatora.orionekox.beta` 包名而失败，属于既有问题，不是本次改动引入）
- 其余约 38 处硬编码（图标字形、动态拼接、emoji 徽标）不在本次范围
- 英文界面回归未单独验（只验了中文）

---

## 证据文件

- `/tmp/ox-verify/p2-8-panel-zh.png` —— 设置面板中文截图
