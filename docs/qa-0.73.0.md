# QA — alpha 0.73.0（群组文件气泡 + 消息转发 + 发送键启用修复）

- 版本：`0.73.0`，versionCode **7309**（`0.72.0` 为 7209）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口）
- 时间：2026-10-29 13:45–14:10（CST）
- 目标（OxUI 对标主线）：① 群组文件消息在 OxUI 显示为文件气泡
  （0.72 遗留文本占位）；② 消息操作“转发到其他 Agent/群”。

## 1. 实现

1. **群组文件条目落盘**：`handleOutgoingGroupMedia` 记录
   firstFilePath/firstFileName；`onGroupMessage`/`startGroupTurn` 增加文件
   重载；群用户条目写入 `filePath/fileName`，OxUI 群历史复用同一
   `renderConversation` 文件气泡（PAPER 胶囊 + 名称 + mono 大小 + 说明 +
   时间戳）。
2. **转发（Forward to…）**：助手行菜单变为
   `Copy / Reply <name>(群) / Forward to…`（1:1 为 Copy / Forward to…），
   用户气泡菜单加 Forward to…；目标选择面板列出全部 Agent（单字头像）与
   本地群（👥），选择后以 `〔转发〕\n<正文>` 发送：
   - Agent：`OxAgentsBridge.sendFromOxUi`（真实回合，TL 同步）
   - 群：`sendLocalGroupText(-chatId, …)` 并切到该群
3. **真 bug 修复**：`updateSendVisual` 的 ready 判定只含 pendingImagePath，
   纯文件（无文字）时发送键保持禁用——0.72 的文本+文件用例掩盖了它；
   本轮加入 pendingFilePath 修复并复测。

## 2. 设备验证（debug 7309）

1. **群组文件**：Telegram UI 新建本地群（id=1800000000，Echo）→ OxUI
   Groups 打开 → ＋ → File → `oxui-730.txt` → 无文字直接发送：
   - 修复前：发送键灰色禁用（bug 复现）；
   - 修复后：logcat `runRound dialog=-1800000000` +
     `persistAndShow media type=9 TL_messageMediaDocument`；
     OxUI 群内渲染 `📄 1_oxui-730.txt · 1 KB` + `用户发送了文件：…` +
     Echo 回复（截图 08）。
2. **转发**：长按 Echo 回复 → 菜单 `Copy / Reply Echo Assistant /
   Forward to…`（截图 10）→ Forward to… → 目标面板（5 agent + 1 群，
   截图 11）→ 选 Echo → logcat 用户消息 id=100006 / 回复 id=100007；
   echo chat.json 末两条为
   `〔转发〕\n⚠ echo mode: …` 与其回声回复。
3. release 7309 安装启动无本包 FATAL。

## 3. 产物

- release：`dist/OrionekoX-0.73.0-alpha-arm64-x64.apk`（versionCode 7309）
  sha256 `508b935b2bbbe4ad6c9336751d33f87f8e0c1002cb597c28b304ae20768a3bd3`
- debug：`dist/OrionekoX-0.73.0-debug-arm64-x64.apk`
  sha256 `af2dd3e67008948b6dc939c5fcd0740a904b09c21857fa79a1fb493338477e5d`
- 构建日志 `/tmp/ox-build-0730-final.log`；截图 `/tmp/oxui-730/`。

## 4. 边界 / 下一步

1. 转发仅转发文本（图片/文件条目不转发媒体；文件气泡长按暂无菜单）；
   转发前缀固定「〔转发〕」，未记录来源。
2. 转发到群会切换视图；转发到 agent 保持当前视图（仅有 Toast）。
3. 群文件重名会带序号前缀（1_oxui-730.txt）——oxui_media 目录内重名
   策略，非 bug。
4. 下一步：消息删除（需条目↔TL 消息 id 映射）、编辑、表情反应；
   会话内搜索命中导航补验；视频/GIF 附件 UI；会话列表管理。
