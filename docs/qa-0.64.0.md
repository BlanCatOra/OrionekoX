# QA — alpha 0.64.0（群头像离线分支 + 群回复 sender 归属与定向回复）

- 版本：`0.64.0`，versionCode **6409**（`0.63.0` 为 6309）
- 设备：emulator-5554（1080×2400，Android 14/34，默认 snapshot 模式）
- 时间：2026-10-29 01:10–02:20（CST）
- 目标：0.63.0 遗留三项——① New Group 选头像会走 FileUploader，离线
  agent-only 下上传请求被丢弃（0.62 代码评审 HIGH 发现），本轮做头像
  离线分支；② 群回复只有群名 meta、没有发言人归属（`OxChatEntry` 无
  sender）；③ 无法在群里定向某个 agent 回复。
- 版本规则：三项均为用户可见新能力，按 minor 从 0.63.0 → **0.64.0**
  （6409），patch 清零。

## 1. 实现

1. **群头像离线分支**（`ImageUpdater.processBitmap` + 
   `GroupCreateFinalActivity` + `OxAgentsBridge.attachLocalChatAvatar`）：
   - `ImageUpdater.processBitmap` 生成 bigPhoto/smallPhoto 后，agent-only
     分支直接 `didUploadPhoto(null,null,0,null,big,small,false,markup)` +
     `cleanup()`，不再进入 FileUploader（离线下其请求会被
     ConnectionsManager 丢弃且无回调，表现为转圈死锁）。
   - `GroupCreateFinalActivity` 的 Done 门槛改为
     `isUploadingImage() && !agentOnly`；`showAvatarProgress(!agentOnly)`；
     `chatDidCreated` 在 agent-only 且无 inputPhoto、有本地 avatar 时调用
     `OxAgentsBridge.attachLocalChatAvatar(chatId, avatar, avatarBig)`。
   - `attachLocalChatAvatar` 构造 `TL_chatPhoto`：photo_id = |chatId|*31+13、
     volume_id = -photoId、local_id 97/99（与 `TL_chatPhoto.readParams`
     反序列化约定一致），把 ImageUpdater 的 cache 文件
     （`getPathToAttach` 各变体）`Files.copy` 成对应磁盘名，set 到
     chat.photo 后 putChat 持久化 + `updateInterfaces(UPDATE_MASK_AVATAR)`。
     这样重启后从 MessagesStorage 反序列化出的 TL_chatPhoto 能按
     volume/local id 直接命中已拷贝的文件——头像可跨进程存活。
2. **sender 归属**：`OxChatEntry` 新增 `sender`（JSON `"sender"`，向后
   兼容缺失即视为旧数据）；`OxGroupStore.appendChat` 加 sender 重载；
   `OxAgentsBridge.finishTurn` 群分支写入 `s.agent.id`；OxUI 气泡 meta
   通过 `resolveAgentName` 显示发言 agent 名（如 “Translator · 01:38”）。
3. **定向回复**：`OxAgentsBridge.sendLocalGroupTextTo(dialogId, agentId,
   text)` → `sendLocalGroupText(dialogId, text, replyTo, agentId)`，带
   agentId 时强制 `startGroupTurn(forced)`（log
   `groupReply forced agentId=...`）；OxUI 助手行长按弹
   `Copy / Reply <name>`（android.app.AlertDialog），选择后 composer 上方
   出现回复 chip，发送走定向路径；换群/点 chip/发信后清除。

## 2. 设备验证（全部真实 UI 流）

1. **头像离线分支（本轮新增链路）**：Telegram UI → FAB → New Group →
   勾选 agent → 点头像 → 相册选 `oxui-640.png` → 裁剪页 ✓ → 返回
   New Group，**头像缩略图已设置、无转圈死锁**（截图
   `/tmp/ox-0640-avatar-set.png`）；清空预填名、输入 `OxUI AVA 640` →
   ✓ FAB。logcat：
   `createLocalGroup id=1800000002 agents=1`、
   `attachLocalChatAvatar attached chatId=1800000002 photoId=55800000075`、
   `persistAndShow shown did=-1800000002 id=100086 out=true`；群聊页头部
   直接渲染橙色头像 + “OxUI AVA 640 · 2 members”（截图
   `/tmp/ox-0640-ava-group2.png`）。
2. **重启持久化**：`am force-stop` + 重新启动 → chats 列表顶部
   `OxUI AVA 640` 仍渲染橙色头像（截图 `/tmp/ox-0640-restart.png`），
   证明 TL_chatPhoto volume/local_id 轮转命名命中拷贝文件。
3. **sender 归属**：群 `OxUI QA 640`（id=1800000001，成员 translator）
   发送 `PING2-640`，回复气泡 meta 显示 `Translator · 01:38`（此前只有
   群名）。注意：升级前写入的历史条目无 sender 字段，不显示归属、也无
   长按菜单（见边界 4.1）。
4. **定向回复**：长按 Translator 的回复行 → 菜单 `Copy / Reply
   Translator` → composer 上方出现 chip → 发送 `TARGET-640` → logcat
   `groupReply forced agentId=translator dialog=-1800000001`，回复由
   Translator 生成（其他成员未被触发）。
5. **UI 坐标坑**：软键盘弹出会把 ✓ FAB 从 (964,2300) 顶到 (964,1411)；
   首次按旧坐标点到了键盘。每次 tap 前重新截图/dump 确认边界。

## 3. 产物

- release：`dist/OrionekoX-0.64.0-alpha-arm64-x64.apk`，sha256 见 §6
- debug：`dist/OrionekoX-0.64.0-debug-arm64-x64.apk`，sha256 见 §6
- 构建日志：`/tmp/ox-build-0640-final.log`

## 4. 边界 / 下一步

1. **旧群历史无 sender**：0.64 前写入的 OxChatEntry 无 sender 字段，
   不显示归属、长按菜单不出现；新消息起生效。不做数据迁移。
2. **头像只在创建路径**：改群头像（ChatActivity 菜单 → 编辑头像）仍走
   服务器路径，agent-only 下未做离线分支——下轮可复用
   attachLocalChatAvatar 的文件轮转方案。
3. `groupReply` 定向只跳过成员轮换，未把“回复哪条消息”写进上下文；
   带引用语义的定向（quote）留给后续轮次。
4. 视觉评审 12 项 polish 清单（图片+说明一体化、图片高度上限、Preview
   徽章去按钮化、rail 渐隐、类型尺度归一等）继续排后续轮次。

## 5. 清理

- QA 群 `OxUI QA 640`（1800000001）与 `OxUI AVA 640`（1800000002）：
  chats/dialogs/messages/chat_settings/users/contacts 行删除后计数 0；
  `ox_groups.xml` 的 chat_/created_/log_ 键已移除。
- 测试 agent `oxui_grp_630` 目录及全部会话数据已删除；重启后 chats
  列表仅剩既有 5 个 agent，无 FATAL（截图 `/tmp/ox-0640-cleaned.png`）。
- `/sdcard/Pictures/oxui-640.png` 已删除；`interface_mode=telegram`；
  force-stop 后无障碍已重新绑定；未 `pm clear`，未向真实联系人发消息。

## 6. sha256

- release：`905a3d545d3ab91cc4aba3bf81908d01eef6364c5cba5e1035320fa4df9cb005`
- debug：`703a202334becc03138e602db7cf2e817374fa022380d92afefc4dd95470ae2a`

## 7. 构建备注

- 首次用根级 `assembleAfatStandalone assembleAfatDebug`（带 `-q`）在 1 秒内
  失败：`:TMessagesProj_App:mergeAfatStandaloneNativeLibs` 解析 media3
  “No matching variant of media-lib-exoplayer”。改用历轮成功的模块级调用
  `:TMessagesProj_AppStandalone:assembleAfatStandalone
  :TMessagesProj_AppStandalone:assembleAfatDebug` 后 2m18s 成功（643
  tasks，与 0.63 final 相同规模）。根级失败疑似 daemon/配置态问题，未深
  究；统一以模块级为准。debug APK 源码与 01:56 的 QA 构建一致，Gradle
  判 UP-TO-DATE 未重打包，时间戳早属正常。
