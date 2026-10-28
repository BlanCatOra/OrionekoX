# QA — alpha 0.65.0（已有群改/删头像离线分支 + 强制回复修复 + quote 语义 + 视觉三轮快赢）

- 版本：`0.65.0`，versionCode **6509**（`0.64.0` 为 6409）
- 设备：emulator-5554（1080×2400，Android 14/34，默认 snapshot 模式；本轮 qemu
  一次自发崩溃，已按既定流程重启并 `adb root` 恢复）
- 时间：2026-10-29 02:42–03:45（CST）
- 目标：0.65.0 首要三项——① 已有本地群改/删头像仍走服务器请求（0.64 只做了
  创建路径）；② **修复 0.64 的定向回复假验证**：`sendMessage` 在
  `dispatchSend` 之前清空 `replyAgentId`，强制定向从未真正生效（0.64 QA 的
  TARGET-640 “验证”因单成员群与普通轮换不可区分而漏检）；③ 定向回复的
  quote 语义。外加视觉三轮评审的快赢项。

## 1. 实现

1. **已有群改头像离线分支**（`ChatEditActivity` + `OxAgentsBridge`）：
   - `didUploadPhoto` 最前插入 agent-only 分支（photo/video/emoji 全空、
     userId==0、chatId!=0）→ `updateLocalChatAvatar(chatId, small, big)` 后
     `showAvatarProgress(false,true)` 并 return；不再进入会发
     `TL_channels_editPhoto` 的 `changeChatAvatar`。首版补丁曾插进外层
     if-else 链，子代理评审指出该位置对普通图片是**死代码**（ImageUpdater
     离线回调全空参数走的是外层 else），已前移修正。
   - 不设 `hasUploadedPhoto`（评审发现它会冻结 `setAvatar` 的
     updateInterfaces 刷新路径），UI 刷新完全交给
     updateInterfaces → setAvatar，设备验证 cell 文案自动变 “Set New Photo”。
   - `updateLocalChatAvatar` photoId = `millis*1000 + chatId%1000`：与创建期
     `|chatId|*31+13`（≈5.6e10）差三个数量级永不碰撞，同毫秒跨群编辑也唯一
     （评审指出的同秒碰撞已修）。
   - **删除路径**：上游 `ImageUpdater.openMenu` 在 `useAttachMenu=true`
     （ChatEditActivity 的构造）下直接走 attach sheet、丢弃 onDelete 回调，
     删除项 UI 层面不存在。agent-only 且有头像时 `openSetPhotoAlert` 拦截弹
     `Set New Photo / Remove photo` 两项对话框：前者
     `imageUpdater.openMenu(false,null,null,0)` 重开选图页，后者
     `removeLocalChatAvatar`（chat.photo 置 `TL_chatPhotoEmpty`，
     TL_chat 无条件序列化 photo 对象故无需清 flag 位）+ 本地 UI 复位。
2. **强制回复修复**（`OxUiPreviewActivity.sendMessage`）：
   `clearReplyTarget()` 移到 `dispatchSend` **之后**（原顺序先清空再派发，
   dispatchSend 读到的 `replyAgentId` 恒为 null，定向永远走普通轮换）。
3. **quote 语义**（按子代理设计注实现，快照方案）：
   - `OxChatEntry` 增 `replyToSender`/`replyToText`（JSON
     `reply_to_sender`/`reply_to_text`，optString 缺省空，向后兼容同 0.64
     sender 模式，不迁移）。
   - `OxGroupStore.appendEntry(chatId, entry, sender)` 重载承载新字段。
   - `OxAgentsBridge`：`sendLocalGroupTextTo` 5 参重载 → `startGroupTurn`
     6 参 → `groupMessages` 在最后一条 user 消息 content 注入前缀
     `（回复 @Name：「摘录≤160」）`（agent 显示名实时解析）；落盘条目
     content 保持纯净正文，quote 只存独立字段。
   - Telegram UI 对齐：`onGroupMessage` 对 replyTo 生成 agent 消息的场景
     抽 `excerpt()` 快照（≤160 字符，空白折叠；纯媒体引用跳过——本层
     TLRPC.Message 无 caption 字段）。
   - OxUI：长按菜单捕获 quote（chip 显示 `Reply → Name · 「前 24 字」`），
     气泡上方渲染 MUTED 小字 `↩ Name: 摘录`。
4. **视觉三轮快赢**（子代理评审 12 项中的 7 项，其余列下轮）：
   - P0 图片+说明一体化：`addUserImage` 改为统一 ink 卡片（图上文下、
     `plate(INK,17)` + clipToOutline），有图必有卡，caption 内嵌；解码失败
     回退文字气泡。
   - P0 图片高度上限：0.6×viewport，超出按比例缩宽 + `CENTER_CROP`。
   - P0 发送后滚动竞争：`fullScroll` 后追加 `messages.post` 二次对齐。
   - P1 头部 Preview 徽章删除（页脚已有同等信息，且与发送按钮争抢唯一
     chartreuse 信号色）；Agents 按钮统一描边胶囊样式。
   - P1 rail 渐隐：`setHorizontalFadingEdgeEnabled` + 36dp fading edge。
   - P2 assistant 强调条 `MATCH_PARENT`（长文全高 chartreuse 竖线）。
   - P2 列表副标题剥离 `- ` / `#` / `1.` 行首标记并折叠空白。
   - 评审确认的其余项（自定义长按菜单替换 AlertDialog、用户气泡时间戳、
     类型尺度吸附刻度、附件行容器）留下一轮；评审还判定 0.61 遗留的
     “exHello Sakura” 脏文本是数据 artifact（恢复/重放拼接丢分隔符），
     不在渲染层防御。

## 2. 设备验证（全部真实 UI 流）

1. **改头像**：群 OxUI POL 650（id=1800000003，无头像）→ 编辑 → Set Photo →
   相册选图 → 裁剪 ✓ → **无死锁**，logcat
   `updateLocalChatAvatar attached chatId=1800000003 photoId=1793215169213003`
   （millis+chatId 混合 id）；编辑页头像缩略图立即渲染、文案自动变
   “Set New Photo”（updateInterfaces 路径生效）；返回 profile 渲染新头像；
   **force-stop 重启后 chats 列表头像仍在**（截图 28-restart）。
2. **删头像**：编辑页 Set New Photo → agent-only 对话框 “Set New Photo /
   Remove photo” → Remove → logcat
   `removeLocalChatAvatar cleared chatId=1800000003`，头像立即回落 OX
   initials、文案回 “Set Photo or Video”。
3. **强制回复修复**：OxUI 群聊长按 echo 回复行 → Reply chip → 发送
   TARGET-650Q → logcat **`groupReply forced agentId=echo dialog=-1800000003`**
   （0.64 构建上同操作无此日志）。
4. **quote 语义**：同一条 TARGET-650Q 的落盘条目
   `"reply_to_sender":"echo","reply_to_text":"⚠ echo mode: no model backend
   configured"`（JSON 往返正确）；OxUI 气泡上方渲染
   `↩ Echo Assistant: ⚠ echo mode…` MUTED 引用行（截图 38-quote）。
5. **视觉**：头部 Preview 徽章消失、Agents/Telegram UI 双描边胶囊等高；
   rail 右缘渐隐；长回复强调条全高（Sakura 长记忆回复）；列表副标题无
   “- ” 残留；CAP-650 图文一体卡片（图+「附带说明：CAP-650」同一 ink 卡、
   高度受 0.6 视口上限约束）。

## 3. 产物

- release：`dist/OrionekoX-0.65.0-alpha-arm64-x64.apk`，sha256 见 §6
- debug：`dist/OrionekoX-0.65.0-debug-arm64-x64.apk`，sha256 见 §6
- 构建日志：`/tmp/ox-build-0650-final.log`

## 4. 边界 / 下一步

1. **0.64 假验证更正**：R2-134 的 TARGET-640 “已设备验证”不成立——当时
   强制路径已死、单成员群回复与普通轮换不可区分；本轮修复并复验，0.64
   文档不回改，以本节与 round2 更正注记为准。
2. **群 emoji 头像创建缺口**（评审发现，0.64 遗留）：New Group 选 emoji
   头像时 `inputEmojiMarkup != null` 走 `changeChatAvatar` 被静默丢弃，
   头像丢失（不挂死）；agent-only 本地不支持矢量 emoji 头像，列为后续。
3. **用户头像族 RPC**（SettingsActivity/ProfileActivity/DialogsActivity 的
   uploadProfilePhoto、PhotoViewer 主图操作）在 agent-only 下同样被丢弃或
   卡 spinner——同一类缺口，backlog 统一修。
4. **改名离线化**：`changeChatTitle`/`updateChatAbout` 无 agent 处理，
   改名重启后回滚（backlog）。
5. 视觉评审遗留 5 项：自定义长按菜单（替换系统 AlertDialog）、用户气泡
   时间戳+长按菜单、类型尺度吸附（11/12/14/16/18/22/28）、附件行容器、
   头部按钮基线细节；及本轮发现的**竖长截图卡片渲染异常**（tall 图 +
   auto-caption 的卡片/气泡错位，疑 pending 与历史重渲染交互，需复现定位）。
6. echo agent chat 中 0.61 遗留的 “exHello Sakura” 脏文本为历史数据
   artifact，未清理（不删用户数据）。

## 5. 清理

- QA 群 OxUI POL 650（1800000003）dialogs/messages/chats/chat_settings
  计数 0，`ox_groups.xml` 键移除；echo agent chat.json 移除本轮 4 条 QA
  条目（保留 0.61 及更早的既有历史，不删用户数据）；
  `/sdcard/Pictures/card650.png`、`/sdcard/oxui-650.png` 与
  `files/oxui_media/` 全部清除；`interface_mode=telegram`；无障碍重新绑定；
  未 `pm clear`。

## 6. sha256

- release：`9801360d7f50d3fa9cdcaa6222707ca67d1b3a76b704b6815ff34e73a658250f`
- debug：`9be0280b77349d98930bd837ee9baa7df129ca4c0c4c333536d47c020eb4a336`
