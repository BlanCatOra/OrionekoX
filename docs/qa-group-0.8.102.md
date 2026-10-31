# QA — local group add-member goes local (0.8.102, 2026-10-26 13:10–13:35)

Build: `io.github.blancatora.orionekox` versionName 0.8.102 (versionCode 1179),
emulator-5554 with `-gpu off`. Only the local test group was used; no real contact
was messaged and no server member request was issued.

## Implementation

- `OxAgentsBridge.addLocalGroupMembers(chat, users, onAddUser, onComplete)` handles
  `MessagesController.addUsersToChat` for `OxGroupStore.isLocalGroup(chat.id)` and
  returns before the `messages.addChatUser` request.
- It rebuilds the local full, appends only agents that are not already participants,
  persists `OxGroupStore`, `chat.participants_count` and the full, then posts
  `chatInfoDidLoad` so an open profile refreshes.
- 0.8.102 fixes two issues found while testing 0.8.101:
  - `chatInfoDidLoad`'s fourth argument must be a `Boolean`; passing `null` crashed
    `TopicsFragment.checkGroupCallJoin((Boolean) args[3])` with an NPE. It now passes
    `false`.
  - `ProfileActivity` optimistically appends every user reported through `onAddUser`
    in its `onComplete` callback. The local branch already updated the full, so it no
    longer calls `onAddUser`; doing both produced a duplicate Echo row and “4 位成员”.

## Add-member pass

- Starting state: “2 位成员, 2 人在线”, rows TestUser (创建者) and Yuki Waifu.
- 添加成员 → picker → Echo Assistant selected (Yuki's switch is checked+disabled).
- Confirmation dialog: “添加1成员 … 向 Echo Assistant 显示最后100条消息”.
- After 添加: “3 位成员, 3 人在线”, exactly one Echo Assistant row; no duplicate.
- `OxBridge: addLocalGroupMembers chat=1800000001 count=3`, no `failed` line.
- No `addChatUser` / `AddChatUser` RPC in logcat.

## Restore

- Force-stopped, restored the pre-0.8.101 `ox_groups.xml` backup (Yuki only) with the
  original owner/mode, relaunched and reopened the group.
- Chat header and profile are back to “2 位成员, 2 人在线” with only Yuki. A profile
  fragment auto-restored by `am start` after the external prefs rollback briefly
  showed the previous row until it was closed and reopened; normal list → chat →
  profile navigation and a cold start both show two members.

## State

- Test group is Yuki-only again; no messages were sent, no settings changed, and no
  real Telegram contact was involved.
