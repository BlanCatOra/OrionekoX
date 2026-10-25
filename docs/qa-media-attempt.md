# QA — media send attempt (2026-10-26 06:58–07:05)

Environment: emulator-5554, release 0.8.58 (739), Yuki Waifu 1:1 chat.
Goal: send `Download/ox_photo.png` to a local agent and verify the native photo
bubble, workspace archive and vision request.

## What was tried

1. Paperclip → photo grid. The first tile opened the camera preview instead of a
   photo; backed out without taking or sending anything.
2. Paperclip → 文件 → 内部存储器 → Download → `ox_photo.png`.
   The picker navigation worked and the file was visible, but tapping the file made
   the emulator lose `adb` before the preview/send screen appeared.

## Result

- No media message was sent. After rebooting, `messages_v2` for Yuki's dialog shows
  the last message still at 06:11 (the previous tool test); there is no new media
  row, no new `oxagents/<id>/media` file and no `inbox` copy.
- No crash log or app FATAL was available because the whole emulator process
  disappeared; this matches the earlier rendering/stability failures when opening
  chats or handling media on this AVD.
- The media send path therefore remains **unverified**; it needs a stable emulator
  or a real device. This was not a code change.

## Next step

Retry on a stable device: send a photo with a caption, check the native bubble, the
`files/oxworkspace/<agent>/inbox` archive and the model's vision response. For a
document, verify `read_file` sees the archived copy.
