# QA — alpha 0.35.0（RikkaHub 图像头像导入）

- 版本：`0.35.0`，versionCode **3509**（`0.34.0` 为 3409）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 08:00–08:18（CST）
- 目标：读取 RikkaHub 备份 settings.json 中的 `Avatar.Image`，把
  `upload/` 内的头像写入 Agent 目录并显示；同时兼容 FQN 形式的
  `Avatar.Emoji`。
- 版本规则：新增“RikkaHub 图像头像导入”这一可见能力，按 minor 从 0.34.0 →
  **0.35.0**，versionCode 3509（350×10+9）。

## 1. 新增能力

- `saveRikkaAvatar`：解析助手 `avatar` 对象中的 `url`，支持
  `file:` / `content:` 或类型名含 `image`；取 URL 的 basename，与备份
  `upload/<name>` 匹配后写入 `files/oxagents/<id>/avatar.<png|jpg|webp|gif>`。
- 只使用备份内文件，不下载网络 URL；无匹配资源时保留 emoji/默认头像。
- 重复导入时若 Agent 已有头像则保留，不覆盖自定义头像。
- `avatarEmoji` 从精确匹配 `type=emoji` 改为包含 `emoji`，兼容 RikkaHub
  默认序列化的 `me.rerere.rikkahub.data.model.Avatar.Emoji`。
- 预览增加“将导入 N 个助手图像头像”，总览文案补充“助手图像头像”；
  结果 note“导入头像 N 个”。

## 2. 设备验证 — PASS

样例 zip：`settings.json` + `upload/rikka-av.png`（96×96，RA）。
助手：
1. `Rikka Avatar`，`avatar` 为
   `{"type":"me.rerere.rikkahub.data.model.Avatar.Image","url":"file:///data/user/0/me.rerere.rikkahub/files/upload/rikka-av.png"}`；
2. `Rikka Emoji`，`avatar` 为
   `{"type":"me.rerere.rikkahub.data.model.Avatar.Emoji","content":"🦊"}`。

- 预览：助手 2、Provider 0、MCP 0、技能 0；note“将导入 1 个助手图像头像”。
- 导入：新建助手 2、note“导入头像 1 个”；`Rikka Avatar` 目录生成
  `avatar.png` 2,519 B，`avatar_path` 指向该文件；`Rikka Emoji` 的
  `emoji` 为 🦊。
- 原生界面：打开 `Rikka Avatar` 会话，标题左侧显示绿色 RA 头像，
  欢迎语与在线状态正常。
- 重复导入：新建 0、更新 2，无头像 note；`avatar.png` sha256 保持
  `3bbb3ca9…`，未重复写文件。
- 清理：删除 2 个测试 Agent 与样例；重启后 dialogs/messages 对
  555000072/555000073 均为 0 行；无本包 FATAL/ANR。
- 截图：`docs/screenshots/qa-0.35.0-preview.jpg`、
  `qa-0.35.0-result.jpg`、`qa-0.35.0-chat.jpg`。

## 3. 未覆盖 / 已知边界

- 只支持 `Avatar.Image` 且文件位于备份 `upload/`；网络 URL、content URI
  无本地文件时跳过，不联网下载。
- 仅取 URL basename 匹配；重名上传文件可能匹配到错误资源（备份内文件名
  通常唯一）。
- 不导入 RikkaHub 的聊天图片/其他附件（已有单独能力）、工作区、字体等。
- 真实 RikkaHub 导出包尚未复测；样例按 `settings.json` 结构构造。

## 4. 产物

- `dist/OrionekoX-0.35.0-alpha-arm64-x64.apk`（versionCode 3509），sha256
  `7c1aa6d7d77e224263c4fc57b0785c743b283b2ac1f2316e8060fac781817313`
- `dist/OrionekoX-0.35.0-debug-arm64-x64.apk`，sha256
  `1c3b982d25905c1dd309334e0c25ad62333a6995fa2410982b886e97ffbccf49`
