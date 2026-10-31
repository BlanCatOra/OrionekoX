# QA — local group reply + media send (0.8.82, 2026-10-26 10:54–11:02)

Build: `io.github.blancatora.orionekox` versionName 0.8.82 (versionCode 979),
emulator-5554, Android 14. Only local agents / the existing local group were used.
No real Telegram contact was messaged and no user data was cleared.

## Key environment finding

- The repeated whole-emulator crashes while opening a chat, the archive folder or
  the chat search box were **not** app crashes. Restarting the AVD with
  `-gpu off` (guest software rendering) instead of `-gpu swiftshader_indirect`
  keeps the device online through chat open, the attachment picker and the media
  preview. No FATAL/ANR from OrionekoX was present in any of the crashed runs.
- Use `-gpu off` for future interactive QA on this host; SwiftShader remains usable
  for simple screens but dies on chat/media rendering.

## Local group reply

- Opened “TestUser 和 🤖 Yuki Waifu”, sent
  “Version check: reply with one word, do not use tools.”
- Bubble shows `于 10:57 AM 发送, 已查看`; Yuki replied once
  (“Ready～…”), and `OxBridge: onRound dialog=-1800000001 tools=0` confirms no tool
  call. Group history and local delivery still work on 0.8.82.

## Media send (first successful attempt)

- In Yuki Waifu 1:1, attachment → 文件 → 内部存储器 → Download → `ox_photo.png`
  (2.7 KB) → preview sheet → send with one attachment.
- Bubble status: `于 11:01 AM 发送, 已查看`.
- `OxBridge` log: `persistAndShow media type=9 mediaClass=TL_messageMediaDocument
  photoNonNull=false sizes=-1`.
- Yuki then called `list_dir({"path":"inbox"})`, which returned
  `1792983667815_ox_photo.png  2745B`, and replied that the file is saved in
  `inbox/`. So the local agent receives and can inspect media.
- Cosmetic limitation: the chat bubble shows an X placeholder and
  “0 KB / 2.7 KB” because the injected local document has no downloadable
  thumbnail/file location. The bytes are delivered, but the bubble thumbnail is
  not rendered. A future fix can copy the file into the attachment cache and/or
  attach a local photo size.

## Data notes

- One text message was added to the existing local group, and one photo message to
  Yuki Waifu's local chat; both are local-only test artifacts.
- No contacts, agents, workflows, MCP servers or workspace files were changed.
