# QA — local group info, add-member picker, message menu (0.8.100, 2026-10-26 13:00–13:05)

Build: `io.github.blancatora.orionekox` versionName 0.8.100 (versionCode 1159),
emulator-5554 with `-gpu off`. Local test group only; no real contact was messaged
and no agent was added or removed.

## Group info (after 0.8.100)

- Title “BlanCat 和 🤖 Yuki Waifu”, subtitle “2 位成员, 2 人在线”.
- Creator row now renders “BlanCat · 在线 · 创建者”; member row “🤖 Yuki Waifu ·
  本地智能体”.
- Actions: 消息 / 静音 / 语音聊天 / 离开; “添加成员” row present.

## Add-member picker

- Tapping 添加成员 opens the native picker with “通过链接邀请他人加入群组” and the
  full local-agent list (API Agent, QA2, Sakura Waifu, Yuki Waifu, Echo Assistant,
  Summarizer, Translator).
- Yuki Waifu is shown with a checked, disabled switch because she is already a
  participant — the ignore-list works.
- No agent was selected or confirmed; back was used to leave the picker.

## Message context menu

- Long-press on a group message shows 回复 / 复制 / 转发 / 置顶 / 举报 / 删除.
- The menu was dismissed with back; 举报 and 删除 were not exercised on local data,
  since they may issue server requests.

## State

- Device stayed online under `-gpu off`; no OrionekoX FATAL/ANR; no group membership,
  messages or settings were changed during this pass.
