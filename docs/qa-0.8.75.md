# QA — alpha 0.8.75 (2026-10-26 09:58–10:03)

Build: `io.github.blancatora.orionekox` versionName 0.8.75 (versionCode 909),
emulator-5554, Android 14. Chinese UI, local synthetic account. No real Telegram
contacts were messaged and no user data was cleared.

## Covered

| Area | Action | Result |
| --- | --- | --- |
| Chat list | Long-press Yuki Waifu → action mode → overflow | Only 归档 / 标记为未读 / 清空历史记录; “屏蔽用户” is gone for local agents (0.8.74 fix confirmed) |
| Contacts | Open Contacts tab, inspect agent rows | 7 local agents, no real contacts added or removed |
| New contact sheet | Tap the add-contact FAB, read copy, close without saving | “同步联系人到手机” shown (0.8.75 copy fix); no contact created |
| Local group | Open “TestUser 和 🤖 Yuki Waifu”, send “Group check: reply with the word kiwi, do not use tools.” | Message shows 已查看, Yuki replies without tool calls (`onRound tools=0`) and recalls the group codeword “pineapple” from history |
| Rebrand runtime | Sample zh pack on device | `ChatAir` count 0; “清除 OrionekoX 缓存”, “同步联系人到手机”, Telegram Desktop QR copy present |

## Emulator limitation (not an app crash)

- Tapping the chat-list search field and typing causes the whole qemu process to
  disappear (`adb devices` empty, no app FATAL, no ANR). Reproduced twice, both
  times at the same step. The AVD was restarted and booted normally; app data
  survived.
- Workaround used for this pass: open the target chat by row coordinates instead of
  search. This is consistent with the previously documented file-picker/media
  crash pattern on this emulator image (SwiftShader), not with an OrionekoX
  exception.

## Data notes

- One test message was added to the existing local group “TestUser 和 🤖 Yuki Waifu”
  (1800000001). It contains no personal data and can be deleted by the user.
- No agents, workflows, MCP servers, workspace files or contacts were created,
  modified or deleted during this pass.
