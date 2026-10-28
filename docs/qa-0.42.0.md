# QA — alpha 0.42.0（RikkaHub 多附件与音视频导入）

- 版本：`0.42.0`，versionCode **4209**（`0.41.0` 为 4109）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off` 软件渲染）
- 时间：2026-10-28 11:09–11:20（CST）
- 目标：RikkaHub 单条消息里的多张图片、多个文件以及 video/audio part
  都应导入，并在原生聊天按顺序显示；重复导入保持幂等。
- 版本规则：新增“多附件拆分”和“视频/音频附件导入”两项可见能力，按
  minor 从 0.41.0 → **0.42.0**，versionCode 4209（420×10+9）。

## 1. 新增能力

- 解析每条消息的全部媒体 part（单条最多 20 个）：
  - `image` → 图片媒体；
  - `document` / `video` / `audio` → 文件媒体，名称优先取 `fileName`，
    否则取 `url` 的文件名（视频/音频不再显示为 `rikka_upload_*` 前缀名）。
- 第一条媒体与消息正文组成主气泡；其余媒体按 part 顺序拆成独立气泡。
  图片沿用原生照片气泡，视频/音频按文件气泡显示（MP4/MP3 MIME）。
- 纯媒体消息允许空正文；如果整条消息只有图片且没有文字，首条图片使用
  `[图片]` 作为上下文占位。
- 去重键加入图片/文件 basename，同一条消息的多个附件不会互相覆盖；
  多附件共享该消息节点的时间戳，避免后续节点整体错位。
- 结果提示新增图片计数：例如“已导入历史消息 6 条 / 导入图片 2 张 /
  导入附件 3 个”。
- 预览提示更新为“多附件会按顺序拆成多条气泡；视频/音频按文件气泡导入”。

## 2. 设备验证 — PASS

准备：

- 2,016 B 样例 `/sdcard/Download/rikka-multi-042.zip`：
  1 个助手、1 个会话、2 个消息节点。
- 用户节点 parts 依次为：文字“多附件测试”、red.png（300 B）、
  blue.png（300 B）、report.txt（24 B）、clip.mp4（24 B）、
  clip.mp3（10 B）；助手节点回复“收到多附件”。

导入：

- 预览显示“助手 1 个 / 历史：会话 1 个 · 消息节点 2 条”。
- 结果：“已导入历史消息 6 条”“导入图片 2 张”“导入附件 3 个”。
- `chat.json` 共 6 条，顺序为：
  1. 文字 + red.png
  2. blue.png（空正文）
  3. report.txt
  4. clip.mp4
  5. clip.mp3
  6. 助手“收到多附件”
- 五个媒体条目共享 `ts=1793800000000`；文件名分别为 `report.txt`、
  `clip.mp4`、`clip.mp3`，没有 `rikka_upload_` 前缀残留。
- 原生聊天（tgId `555000085`）显示两张照片气泡、三个文件气泡
  （24 B · TXT、24 B · MP4、10 B · MP3）和助手回复，首图正文为
  “多附件测试”。
- 截图：`docs/screenshots/qa-0.42.0-multi-attachments.jpg`。

幂等与清理：

- 再次导入同一 zip：结果只有“新建助手 0，更新 1”，没有历史/图片/
  附件 note；`chat.json` 仍 6 条，原生 `messages_v2` 仍 6 行，无重复气泡。
- 删除测试 Agent、5 个 `rikka_upload_*` 文件和两个样例 zip（042/041）
  后重启，对应 `dialogs` / `messages_v2` 均为 0 行。
- 本包无 FATAL/ANR；已重新启用 `org.telegram.ox.OxAccessibilityService`。

## 3. 未覆盖 / 已知边界

- 单条消息最多导入 20 个媒体 part；超出部分跳过。
- 视频/音频目前是原生文件气泡，不显示缩略图/内嵌播放器，也不解析时长。
- 仅匹配备份 `upload/` 中的文件；远程 URL、data URL 不下载。
- 每个文件仍受 zip 读取的 4 MB / 总量 40 MB 上限约束。
- 多附件条目按现有 Chatbox 导出逻辑应会作为独立 image/raw-attachment
  资源导出，但本轮未做导出回环复验。
- 未对真实 RikkaHub 完整数据库、超大附件和 >20 附件消息做兼容性/性能验证。

## 4. 产物

- `dist/OrionekoX-0.42.0-alpha-arm64-x64.apk`（versionCode 4209），sha256
  `c6ebf364723a5f7dbfdecbcdec9d0232cafc296dcdbef9ab5b3f00433a9eb37e`
- `dist/OrionekoX-0.42.0-debug-arm64-x64.apk`，sha256
  `0a1307066d15ff3146abce5189393a5728c546430f181db92719ef49a9d87fbf`
