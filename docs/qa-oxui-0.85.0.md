# QA — 0.85.0（OxUI 9.1：视频 / GIF 附件）

> AI disclosure: 本文档与对应代码由 AI 编码代理（WorkBuddy）起草，作者 blancatora。
> 日期：2026-10-30 · 分支：`orioneko/0.80-memory-stage-c` · 基线：`0f3b387`（0.84 终端 ANSI-16）

## 背景

OxUI 此前只有两类附件：图片（Photo）与文件（File）。但 `OxAgentsBridge.handleOutgoingMedia`
**早已支持视频与 GIF**（按扩展名/ `info.isVideo` 走 `injectDocument`，并统计 videos/gifs）。
也就是说链路是通的，缺的是：

1. OxUI 没有「选视频」的入口（用户只能用 File 选，于是被当普通文件渲染成 📄 chip）；
2. 没有视频形态的气泡渲染（海报帧 + 播放入口 + 时长/分辨率）。

9.1 补的就是这两件事。

## 变更范围

| 子项 | 内容 | 状态 |
|---|---|---|
| 9.1 视频附件入口 | composer ＋ 菜单改为 `Photo / Video / File`；`pickVideo()` 用 `ACTION_GET_CONTENT` + `video/*`，走 `importPickedFile()` 导入 `oxui_media`（保留原文件名与扩展名） | 完成 |
| 9.1 视频气泡 | 新增 `addUserVideo()`：用 `MediaMetadataRetriever` 取首帧作海报、叠 ▶、`VIDEO/GIF · WxH · M:SS · 大小` 元信息、说明文字、长按 React/Delete | 完成 |
| 9.1 渲染分流 | `renderConversation` 与 pending 气泡按扩展名 `mp4/mov/mkv/webm/3gp/gif` 分流到 `addUserVideo()`，其余仍是文件 chip | 完成 |
| 9.1 打开播放 | `openVideo()` 经应用 FileProvider（`${applicationId}.provider`）以 `ACTION_VIEW` 交给系统播放器/相册 | 完成 |

## 行为边界

- **GIF 不循环播放**：`.gif` 会渲染成带 GIF 标签的视频气泡，点击交给系统相册/播放器；气泡内不做逐帧动画（需要 Telegram 的 `AnimatedFileDrawable`，留作后续）。
- 视频**不参与视觉理解**：本地模型通道只吃图片，视频只作为本地附件留存与播放。
- 视频与图片互斥（沿用原有 `pendingImagePath`/`pendingFilePath` 互斥逻辑），同一条消息只带一种媒体。
- 导入沿用 `importPickedFile` 的 25MB 上限。

## 构建

- `taskset -c 0-7 ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug` → BUILD SUCCESSFUL。

## 诚实缺口

1. **模拟器实测待补**：需先 `adb push` 一个测试视频到 `/sdcard/Movies`，再经系统文件选择器选中——选择器是外部 UI，脚本自动化不稳。本轮至少可验证「＋ 菜单出现 Video」与「已存在的视频附件渲染为视频气泡」。
2. GIF 气泡内动画未实现（见「行为边界」）。
