# QA — alpha 0.72.0（OxUI 对标：普通文件附件端到端）

- 版本：`0.72.0`，versionCode **7209**（`0.71.0` 为 7109）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口）
- 时间：2026-10-29 13:10–13:40（CST）
- 目标：主线切到「OxUI 补全与打磨」后的首个对标项——Telegram UI 可发的
  普通文件（文档）消息，OxUI 之前只能发图片。

## 1. 实现

1. **桥接（1:1 媒体路径）**：`OxAgentsBridge.handleOutgoingMedia` 在
   视频/GIF/图片分支之外新增普通文件分支：按扩展名映射 MIME
   （pdf/txt/md/json/zip/apk，缺省 octet-stream），`buildLocalDocument`
   + `injectDocument` 生成真实本地文档消息；prompt 追加
   `用户发送了文件：<name>（<mime>, <size> 字节）` 与附带说明。
   `Stream` 增加 filePath/fileName，`startTurn`/`startMediaTurn` 增加
   文件重载，turn 历史条目与 echo 模式的用户条目都会写入
   `filePath/fileName`（`OxChatEntry` 早在 0.31 已有这两个字段）。
2. **OxUI 附件入口**：composer 的 ＋ 改为设计菜单 `Photo / File`
   （复用 message menu 组件）；File 走 `ACTION_GET_CONTENT "*/*"` +
   OpenableColumns 取原名，拷贝到 `files/oxui_media/<name>`（同名加
   序号前缀，25MB 上限），预览胶囊显示 📄 + 文件名 + KB/MB 大小；
   图片预览恢复 ImageView 可见性。
3. **发送与渲染**：`dispatchSend` 增加 filePath（图片/文件二选一），
   1:1 走 `handleOutgoingMedia`、群组走 `handleOutgoingGroupMedia`
   （后者本就支持文件占位）；失败重试（120s 超时）保留文件字段；
   `renderConversation` 识别 `filePath` 条目渲染文件气泡
   （PAPER 胶囊：📄 + 名称 + mono 大小，说明在下方，时间戳随皮肤开关）；
   pending 渲染同样显示文件气泡。
4. **附带发现（历史数据）**：Yuki 会话存在 `recallnullnull…` 工具名条目，
   核对当前流式解析 `OxModelHub.parseStream` 已过滤 null/空 name 片段，
   属旧构建产物，非现行 bug。

## 2. 设备验证（debug 7209，Echo 1:1）

1. ＋ → 菜单 Photo/File（截图 05）；File → 系统文件选择器 → 选中
   `/sdcard/Download/oxui-720.txt`；
2. 预览胶囊显示 `📄 oxui-720.txt · 1 KB`（截图 07）；输入说明
   `FILE-720` 后发送；
3. logcat：`handleOutgoingMedia dialog=555000002 count=1` +
   `persistAndShow media type=9 mediaClass=TL_messageMediaDocument
   photoNonNull=false` + 用户消息 id=100001 / echo 回复 id=100002；
4. OxUI 渲染文件气泡 `📄 oxui-720.txt · 1 KB`、下方 prompt 文本
   `用户发送了文件：oxui-720.txt（text/plain, 42 字节）· 附带说明：FILE-720`、
   时间戳与 echo 回复（截图 09）；
5. force-stop 重启后重新打开 Echo，滚到底部文件气泡仍在、时间戳保留
   （截图 12）。

## 3. 产物

- release：`dist/OrionekoX-0.72.0-alpha-arm64-x64.apk`（versionCode 7209）
  sha256 `209554bc3c30b003fc0384f502edc33d0b33c222ea25636992791fb0459f761e`
- debug：`dist/OrionekoX-0.72.0-debug-arm64-x64.apk`
  sha256 `c40d434cd464a49c319c3dd01040aa4e40f41868266cc776a932b5051c768ac5`
- 构建日志 `/tmp/ox-build-0720-final.log`；截图 `/tmp/oxui-720/`。

## 4. 边界 / 下一步

1. 群组文件消息：Telegram UI 与 agent prompt 已支持（0.31/0.62 路径），
   但 OxUI 群历史里显示为文本占位而非文件气泡（群条目未写 filePath），
   下轮补齐。
2. 文件不可在 OxUI 内预览/打开（仅展示名称与大小）。
3. 25MB 上限、无分片/断点；文件 prompt 仅名称+大小，不含内容。
4. 下一步按对标清单：消息操作（删除/编辑/转发/反应）、会话内搜索命中
   导航补验、视频/GIF 附件 UI、会话列表管理。
