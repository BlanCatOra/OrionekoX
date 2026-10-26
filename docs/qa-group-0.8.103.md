# QA — local group member removal (0.8.103, 2026-10-26 13:36–13:53)

Build: `io.github.blancatora.orionekox` versionName 0.8.103 (versionCode 1189),
emulator-5554 with `-gpu off`. Only the local test group was used; no real contact
was messaged and no server member request was issued.

## Implementation

- `OxAgentsBridge.removeLocalGroupMember(chatId, user)` returns false for self or a
  non-local group, so leaving the group still uses the normal Telegram path. For an
  agent member it removes the agent from `OxGroupStore`, rebuilds the full through
  `ensureLocalGroupFull`, and posts `chatInfoDidLoad`.
- `MessagesController.deleteParticipantFromChat(chatId, user, chat, forceDelete,
  revoke)` now early-returns for local groups after the bridge call.
- `ProfileActivity` refreshes `updateProfileData(true)` after `updateListAnimated`
  for local groups. Without it the member count changed but the subtitle kept the
  old online count; ordering matters because `updateListAnimated` recalculates
  `onlineCount` first.

## Removal pass

- Starting state: “2 位成员, 2 人在线”, rows BlanCat (创建者) and Yuki Waifu.
- Added Echo Assistant through 添加成员 → confirmed (“3 位成员, 3 人在线”, one Echo row).
- Long-pressed the Echo row: 发送消息 / 添加成员标记 / 设为管理员 / 修改权限 /
  从群组中移除. Tapped 从群组中移除.
- Result without leaving the screen: “2 位成员, 2 人在线”, BlanCat + Yuki only.
- `OxBridge: removeLocalGroupMember chat=1800000001 user=555000002 count=2`, no
  `deleteChatUser` request in logcat. `OxGroupStore` no longer lists Echo.

## Notes

- The local branch does not emit Telegram service messages for add/remove; the member
  list and count update directly. Self-leave and deleting the whole local group remain
  on the normal Telegram paths and are not covered by this pass.
- Test group is back to Yuki-only; no messages were sent and no settings changed.
