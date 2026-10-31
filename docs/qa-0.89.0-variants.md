# 0.89.0 变体与语言持久性补充验收

验收时间：2026-11-01 01:25–01:32（Asia/Shanghai）
设备：`emulator-5554`，AVD `orioneko_test`
包：`io.github.blancatora.orionekox.debug`，`versionCode=8909`
语言包：`ox_zh_version=129`

---

## 1. 语言切换重启保持 —— 通过

| 步骤 | `mainconfig.xml` 的 `language` | 界面 |
|---|---|---|
| 切换后 | `local_zh_cn` | 全中文 |
| `am force-stop` + 重启 | `local_zh_cn` | 全中文 |

重启后 `本地智能体工作室`、`智能体`、`聊天/联系人/设置/我的/终端` 全部保持。

---

## 2. 系统语言跟随 —— 不适用（不是缺陷）

Telegram 的语言设置里**没有「跟随系统」选项**，只有具体语言列表（English / Deutsch / Español / 简体中文 / …）。这是上游 Telegram 的设计，不是 OrionekoX 的缺口。

因此「系统语言跟随」这条验收项**在当前实现下不适用**，从待验清单移除。

---

## 3. 变体构建 —— 一个既有问题，不是本次引入

### 现状

| 变体 | `google-services` 插件 | 能否构建 |
|---|---|---|
| `TMessagesProj_AppStandalone` | 条件启用（`if (file("google-services.json").exists())`） | ✅ 能 |
| `TMessagesProj_App` | 硬启用（`apply plugin: 'com.google.gms.google-services'`） | ❌ 失败 |
| `TMessagesProj_AppHuawei` | 硬启用 | ❌ 失败 |
| `TMessagesProj_AppHockeyApp` | 硬启用 | ❌ 失败 |

### 失败原因

```
Execution failed for task ':TMessagesProj_App:processAfatDebugGoogleServices'.
> No matching client found for package name 'io.github.blancatora.orionekox.beta'
```

`TMessagesProj/google-services.json` 里的包名只有上游的三个：

```
org.telegram.messenger
org.telegram.messenger.beta
org.telegram.messenger.web
```

我们的 `io.github.blancatora.orionekox` 一个都不在里面。

### 这是既有问题

`google-services.json` 的提交历史只有 `9054dc3c update to 13.0.3 (7183)`，是上游 Telegram 的提交。**这个文件从来没被我们改过，包名不匹配是 fork 后一直存在的问题。**

不是本次改动引入的。

### 影响

- 日常开发与验收用 `AppStandalone`（`applicationIdSuffix '.debug'`），不受影响
- 要出 `App` / `AppHuawei` / `AppHockeyApp` 变体时才会撞上

### 修法（未做，记录在此）

两条路：

1. **给 `google-services.json` 加上我们的包名**——需要 Firebase 项目配置，且这个文件是上游公开测试凭据，改它会让上游合并冲突
2. **把三个变体的 `google-services` 也改成条件启用**（跟 `AppStandalone` 一样）——最小改动，但会失去 Firebase 推送能力

小狗倾向第 2 条：Ox 模式下本来就不依赖 Firebase 推送，条件启用更符合「本地优先」的定位。但这需要单独一轮验证，不在本次范围。

---

## 4. 结论

| 项 | 结果 |
|---|---|
| 语言切换重启保持 | ✅ 通过 |
| 系统语言跟随 | — 不适用（上游无此选项） |
| AppStandalone 构建 | ✅ 通过（本次验收用的就是它） |
| App / AppHuawei / AppHockeyApp | ❌ 既有问题，非本次引入，已记录修法 |
