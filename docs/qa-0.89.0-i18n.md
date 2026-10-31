# 0.89.0 中文包验收（UI-17 + 原地升级）

验收时间：2026-11-01 00:38–00:45（Asia/Shanghai）
设备：`emulator-5554`，AVD `orioneko_test`
包：`io.github.blancatora.orionekox.debug`，`versionCode=8909`，`versionName=0.89.0-debug`
构建：`main` @ `14afc2ab`（工作树干净，仅 `.workbuddy/` 未跟踪）

---

## 结论

| 项 | 结果 |
|---|---|
| 语言包原地升级（127） | ✅ 通过 |
| 设备包与仓库包一致 | ✅ sha256 完全相同 |
| 切换语言后界面生效 | ✅ 通过 |
| UI-17 pilot（6 条） | ✅ 生效 |
| UI-17 batch 2（26 条） | ✅ 生效 |
| OxUI 面板剩余英文 | ⚠️ 仍有 24 项硬编码，见下 |

---

## 1. 原地升级

设备上 `files/ox_zh-cn.xml` 是 0.88 时期留下的旧文件，0.89.0 安装后**没有删除、没有清缓存**，由 `ensureBundledZhPack()` 按版本号覆盖。

| 项 | 值 |
|---|---|
| 设备文件 | `files/ox_zh-cn.xml`，963,026 字节，mtime `2026-10-31 22:40` |
| 设备 sha256 | `70a8004869bf80a4b074df894724bf964b7bdd9e669b8851658a3d23f9d51e69` |
| 仓库 sha256 | `70a8004869bf80a4b074df894724bf964b7bdd9e669b8851658a3d23f9d51e69` |
| 版本标记 | `shared_prefs/langconfig.xml` → `ox_zh_version=127` |
| 源码常量 | `LocaleController.OX_ZH_PACK_VERSION = 127` |

三者一致，覆盖逻辑按预期执行。

**升级前状态**：界面英文（`October 30`、`Message`、`3 members, 2 online`），`mainconfig*.xml` 无 `language` 键。
**升级后状态**：见下。

---

## 2. 切换语言

路径：`Settings → Language → 简体中文`（列表项显示 `简体中文 / Chinese (Simplified)`，说明内置包被 `LocaleController` 正确识别并注册为 `zh_cn`）。

切换后 `mainconfig*.xml` 写入 `language=local_zh_cn`。

### 切换后实测文本

设置页：

```
账户 / 号码、用户名、简介
Ox 设置 / 模式、本地会员、智能体与工具
聊天设置 / 墙纸、夜幕、动画
隐私和安全 / 最后看到、设备、密码
数据与存储 / 媒体下载设置
聊天文件夹 / 将聊天整理为文件夹
节省电源 / 减少低电荷使用量
语言 / 简体中文
代理与 VPN / 隧道、分流与全局 VPN
底栏：聊天 / 联系人 / 设置 / 我的 / 终端
```

语言页：`语言`、`翻译按钮会在您点按某条消息时出现。`

Ox 设置页：`模式 / Ox 模式（仅智能体）`、`界面 / Telegram UI 或 OxUI 预览`、`本地会员 / 在本地解锁会员功能，无需订阅，也不会请求服务器。`、`引导与授权 / 重新运行引导`、`开源协议 / GPL-2.0`、`智能体 / 创建智能体并编辑资料`、`外观 / 强调色与 Kelivo / RikkaHub 主题导入`、`MCP 服务 / 从 Operit / Operit2 导入`

---

## 3. UI-17 词条实际生效

### pilot（`612113a2`，6 条）

| key | 实测显示 | 位置 |
|---|---|---|
| `OxUiTagline` | 本地智能体工作室 | OxUI 顶栏副标题 |
| `OxUiCustomizeSkin` | 自定义皮肤 | 设置面板 |
| `OxUiTerminalOptions` | 终端选项 | 终端 |
| `OxUiEditMessage` | 编辑消息 | 消息菜单 |
| `OxUiForwardTo` | 转发到 | 消息菜单 |
| `OxUiEditAgent` | 编辑智能体 | Agent 编辑 |

### batch 2（`1deb9e56`，26 条）

设置面板实测文本：

```
设置 / ×
主题
  跟随系统 / 生效 / 浅色 / 深色
外观
  青墨 · 骨纸荧光 / 暖纸 · 陶土橙 / 鼠尾草 · 静室 / 夜色 · 荧光
  工作台 · 朱红 / 奶油 · 深青 / 方格纸 · 蓝 / 阅读 · 高对比 / 墨蓝 · 夜航
  自定义… / 颜色 · 圆角 · 字体
外壳
  Navigation / Rail / 生效 / Bottom bar / Above input / 生效 / Bottom of screen
组件
  Density / Cozy / Compact / 生效 / Timestamps / on / Assistant accent rule / on
Dock 项
  Agents / shown / Search / shown / Terminal / shown / Settings / shown
关于
  OrionekoX alpha 0.89.0 · OxUI preview
  本地优先：聊天、记忆与设置都保留在本机。
```

`主题`、`外观`、`外壳`、`组件`、`关于`、`Dock 项`、`生效`、`自定义…`、`颜色 · 圆角 · 字体`、`本地优先：…` 全部为 batch 2 新增词条，**界面实际生效**。

---

## 4. 剩余英文（batch 2 未覆盖）

设置面板里仍是英文的 24 项，全部是**硬编码字面量**，不是翻译缺失：

```
Navigation / Rail / Bottom bar / Above input / Bottom of screen
Material / Solid / Acrylic / Mica / Glass
Rail chips / Footer status
Density / Cozy / Compact
Timestamps / Assistant accent rule
Agents / Search / Terminal / Settings
shown / on
```

这些是技术选项名（shell 类型、材质、密度、dock 项名），属于 P2-8「OxUI 面板硬编码英文」的剩余部分。`strings.xml` 里没有对应 key，无法通过翻译解决，需要先抽 key。

---

## 5. 一个架构事实（影响后续验收方法）

`OxUiPreviewActivity` 引用 57 个 `R.string.Ox*`，其中：

- **57 个都在 `assets/oxlang/zh-cn.xml`**（运行时语言包）
- **57 个都不在 `res/values-zh-rCN/strings.xml`**（编译期资源）

`values-zh-rCN/strings.xml` 的 450 条里，Ox* 前缀 420 条全部是 `OxAgent*` / `OxOb*` / `OxWorkflow*` / `OxTool*` / `OxMcp*` / `OxWorkspace*` / `OxTerminal*` / `OxNetwork*` / `OxSettings*` 等，**`OxUi*` 一条都没有**。

因此：**OxUI 面板的中文只来自 `assets/oxlang/zh-cn.xml`，不走 `values-zh-rCN`。** 改 `values-zh-rCN` 对 OxUI 面板无效，这是后续做 UI-17 剩余批次时必须知道的。

---

## 6. 未覆盖

- 受影响的三个变体（`App` / `AppHuawei` / `AppHockeyApp`）的 debug manifest 与默认语言应用标签**未验**，只验了 `App`（`assembleAfatDebug`）
- 语言切换后的**重启保持**未单独验（切换后未重启应用）
- 系统语言跟随（`跟随系统` 主题项）未验
- OxUI 面板 24 项硬编码英文未修（属于 P2-8，不在本轮范围）

---

## 证据文件

- `/tmp/ox-verify/zh-settings-panel.png` —— 设置面板中文截图
- `/tmp/ox-verify/device-ox-zh.xml` —— 从设备拉取的语言包（sha256 与仓库一致）
