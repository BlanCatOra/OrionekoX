# AGENTS.md

本文件给 AI 编码代理阅读。人类请看 [README.md](README.md)。

## 项目是什么

OrionekoX 是 [DrKLO/Telegram](https://github.com/DrKLO/Telegram) 13.0.3 的 GPL-2.0
fork，包名 `io.github.blancatora.orionekox`。新增代码集中在
`TMessagesProj/src/main/java/org/telegram/ox/`，界面改动尽量复用 Telegram 原生组件。

## 构建与安装

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug
adb install -r TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/app.apk
```

签名密钥 `TMessagesProj/config/orioneko.keystore` 不随仓库分发。调试包名为
`io.github.blancatora.orionekox.debug`。

## 代码地图

- `org/telegram/ox/OxAgentsBridge.java`：本地智能体/群组/媒体桥接，所有服务器分流都从这里进入。
- `org/telegram/ox/OxAgentStore.java`、`OxGroupStore.java`：SharedPreferences 持久化。
- `org/telegram/ox/` 其余文件：Provider 配置、模型调用、工具、记忆、MCP、技能、终端、工作流。
- `org/telegram/ui/`：Agent 编辑器、Ox 设置、MCP 商城、技能库、终端等界面。
- `TMessagesProj/src/main/assets/oxlang/zh-cn.xml`：中文包；改文案后同步 bump
  `LocaleController` 中的 `OX_ZH_PACK_VERSION`。

## 约定

- 自写界面复用 `UniversalRecyclerView` / `SettingsActivity.SettingCell` / 原生
  `ActionBar` / `AlertDialog`，不要新造一套设置列表。
- 本地智能体/群组的服务器请求必须在 `MessagesController` 入口提前分流；返回 true
  表示已完全本地处理。
- 修改群成员、消息或设置时，先确认不会写入真实 Telegram 会话。
- 图标语义化且不重复；底部 Tab 文案对齐；点击音只响一次。
- 中文文案要自然，避免机翻腔；占位符数量必须与英文原文一致。
- 新增文件/目录时同步更新 `.gitignore`，构建产物放 `dist/`，补丁放 `patches/`。

## 测试与 QA

- 交互 QA 使用 emulator-5554，模拟器以 `-gpu off` 启动；force-stop/重装后重新启用
  `OxAccessibilityService`。
- 只与本地智能体或仅含本地成员的群聊天；**不要给真实 Telegram 联系人发消息**。
- 不 `pm clear`、不删除用户数据、不暴露 API Key/手机号。
- 每轮记录写入 `docs/qa-*.md`，构建/版本记录写入
  `docs/OrionekoX-alpha-0.0.1-构建报告.md`。
- 构建失败必须记录真实错误并修复，不能把失败写成成功。

## 提交与公开内容

- commit 作者固定为 `blancatora <blancatora@agent.qq.com>`。
- 每个 commit 末尾加：
  `Generated-by: blancatora (DeepSeek Harness / deepseek-v4.1-flash)` 与
  `AI-Disclosure: 本提交由 AI 编码代理生成；可能含未经人工复核的改动。`
- README/issue/PR 等公开内容必须带 agent + harness + model + 联系方式与免责声明。
- 不要把 keystore、`google-services.json`、私有邮箱、内网 IP、API Key 提交到公开仓库。
- 公开推送前先跑脱敏扫描；未经用户明确授权，不 push、不建 release、不向他人仓库发 issue/PR。
