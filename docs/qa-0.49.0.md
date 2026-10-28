# QA — alpha 0.49.0（Agent 导出 SillyTavern / RikkaHub 角色卡）

- 版本：`0.49.0`，versionCode **4909**（`0.48.1` 为 4819）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 15:20–15:33（CST）
- 目标：把本地 Agent 导出为 SillyTavern V2 角色卡，支持纯 JSON 与
  带头像的 PNG；补齐此前“角色卡只导入不导出”的双向兼容缺口。
- 版本规则：新增用户可见的导出格式、入口与 PNG 资产写入，属于功能迭代，
  按 minor 从 0.48.1 → **0.49.0**（4909），patch 清零。没有用 patch
  号承载新功能。

## 1. 实现

### 1.1 入口

- Agent 列表长按菜单新增“导出角色卡”，仅对非内置 Agent 显示，与
  “导出 .oxagent”“导出 Chatbox 备份”并列。
- 点击后弹出原生格式选择框：“PNG（含头像）”或“JSON（纯文本）”，
  说明两者都不包含 API Key、Provider 地址或请求头。
- 选择后走系统 `ACTION_CREATE_DOCUMENT`，默认文件名为
  `<Agent 名>.png` / `<Agent 名>.json`；写入在后台线程完成，结果用
  Toast 提示。
- 中英文案分别写入 `values/strings.xml` 与 `assets/oxlang/zh-cn.xml`；
  中文包 108 → 109。

### 1.2 JSON 格式

- 输出 `spec=chara_card_v2`、`spec_version=2.0` 的 V2 包装，`data`
  内包含 `name`、`description`、`personality`、`scenario`、
  `first_mes`、`mes_example`、`system_prompt`、
  `post_history_instructions`、`alternate_greetings`、
  `character_book`、`tags`、`creator`、`character_version`、
  `extensions`、`avatar`。
- 若 AGENTS.md 由角色卡导入生成，含六个 `## Description` /
  `## Personality` / `## Scenario` / `## System prompt` /
  `## Post-history instructions` / `## Example dialogue` 小节，则逐节
  还原到对应字段；其他 Agent 的整份 AGENTS.md 写入 `description`。
- `first_mes` 取 Agent 的开场白。字段上限与导入端一致：单字段 32 KiB、
  开场白 16 KiB，超长按字符边界截断。
- 顶层同时写入 `name` / `description` / `first_mes` 作为旧版 V1
  读取器的兼容别名。

### 1.3 PNG 格式

- 以 Agent 头像为底图，按最大边 1024 px、总像素 160 万缩放后重新编码，
  避免把大图原样塞进卡片。
- 把 V2 JSON 以 Base64 写入 PNG 的 `tEXt` 块，关键字为 `chara`，
  与 SillyTavern 的常见读取路径一致；块插在 IHDR 之后，CRC32 按
  PNG 规范计算。
- 没有可用头像时生成 512×640 的占位卡片：按 Agent 名取色、绘制 emoji
  与名称，再嵌入同一份 JSON。头像损坏或超出上限也回退占位图。
- 不写入 API Key、Provider 地址、请求头、`extra_body`、聊天、记忆、
  技能或工作区；导出内容只有姓名、人设、开场白与头像像素。

## 2. 设备验证 — PASS

测试对象（自建，未使用真实联系人或真实 Provider）：

- `QA Card 0490`：tgId 555000103，256×256 PNG 头像（1,383 B），
  AGENTS.md 含六节人设，开场白 `QA card greeting 0490`。
- `QA Card NoImg 0490`：无头像，用于验证占位卡片。

### 2.1 头像 PNG 导出

- 长按 `QA Card 0490` → 导出角色卡 → PNG，保存为
  `Download/QA Card 0490.png`，大小 2,420 B。
- PNG 结构：`IHDR`（256×256）、`tEXt`、`sRGB`、`sBIT`、`IDAT`、
  `IEND`，逐块 CRC 校验全部通过。
- `tEXt` 的 `chara` Base64 解码后是合法 V2 JSON：name、六节人设、
  开场白与源 `agent.json`/AGENTS.md 一致。
- 导出图与源头像逐像素比较无差异；对 PNG 内嵌 JSON 全文扫描
  `api_key`、`base_url`、`baseUrl`、`headers`、`Authorization`、
  `Bearer `、`sk-`、`ox1:` 均 0 命中。

### 2.2 JSON 导出

- 同一 Agent 导出 `Download/QA Card 0490.json`，大小 790 B。
- 解析后 `spec=chara_card_v2`、`spec_version=2.0`，六节人设与开场白
  逐项一致；敏感字段扫描 0 命中。

### 2.3 回导与幂等

- 通过 Agent 列表的“导入角色卡”选择导出的 PNG，得到
  `rikka_card_67bc18d8`（tgId 555000104）。
- 回读导入 Agent：name、开场白一致，AGENTS.md 仅在小节之间多了空行
  （导入器的既有格式），内容语义一致；头像与导出图逐像素一致；
  Provider 为空，按离线模式导入。
- 再导入同一 Agent 的 JSON：仍只有 1 个 `rikka_card_` 目录，更新同一
  Agent，不产生副本；已有头像保留；开场白与人设保持。
- 说明：JSON 卡没有头像资源，导入端按“已有头像保留”处理。

### 2.4 无头像占位图

- `QA Card NoImg 0490` 导出 PNG 大小 20,888 B，`IHDR` 为 512×640；
  `chara` 中的 V2 JSON 含正确的姓名、开场白与描述。
- 占位图绘制、PNG 编码与 tEXt 嵌入路径均无异常。

### 2.5 稳定性与清理

- 导出、格式选择、SAF 保存、两次回导与菜单检查全程无本包
  FATAL/ANR（`logcat` 过滤 `FATAL EXCEPTION` / `ANR in
  io.github.blancatora.orionekox` 均为 0）。
- 清理：删除 `qa_card_0490`、`qa_cardni_0490`、`rikka_card_67bc18d8`
  三个测试 Agent；`dialogs` / `messages_v2` 对 555000103/555000104
  均为 0；Download 中三个测试卡以及遗留的 `qa-prefs-0480.zip`、
  `qa-ws-0471-*.zip` 测试包已删除；未执行 `pm clear`，未删除其他
  Agent 或用户数据。
- 重装 release 4909 后恢复无障碍服务，中文包版本 109。

## 3. 未覆盖 / 已知边界

- 未与真实 SillyTavern / RikkaHub 客户端互测；本机以自导出、自回导
  验证格式与字段。
- 不导出 `alternate_greetings`、`character_book`/世界书、tags、
  正则、示例对话以外的扩展；这些字段在源 Agent 中也不存在。
- 原生 Agent 的整份 AGENTS.md 会进入 `description`，不会自动拆成六节；
  只有原本由角色卡生成的六节 AGENTS.md 才能逐节还原。
- emoji 不写入角色卡（V2 没有该字段），回导后使用默认 🤖；头像与
  开场白会保留。
- 头像统一缩放并在必要时重新编码，原始 EXIF/ICC/动画不保留；无头像
  时使用程序生成的占位图，不下载网络图片。
- 角色卡不包含聊天、长期记忆、技能、工作区、Provider 配置或 API Key；
  这些仍走 `.oxagent` 或 Chatbox 备份。

## 4. 产物

- `dist/OrionekoX-0.49.0-alpha-arm64-x64.apk`（versionCode 4909），sha256
  `94e8615fd8ec67a22ef3d85761a367d85cbd92656a8465f7ce6d4f781ef5ab98`
- `dist/OrionekoX-0.49.0-debug-arm64-x64.apk`，sha256
  `c649e810d6ba255b72082ea6c5c3db7291d134b823a442c68522dbbd1a7dad88`
- 截图：`docs/screenshots/qa-0.49.0-export-menu.png`（长按菜单）、
  `qa-0.49.0-export-format.png`（格式选择框）
- 构建日志：`/tmp/ox-build-0490.log`（`BUILD SUCCESSFUL in 4m 3s`）
