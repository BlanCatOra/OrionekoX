# QA — media send attempt (2026-10-26 06:58–07:45)

Environment: emulator-5554, release 0.8.58 (739), Yuki Waifu 1:1 chat.
Goal: send `Download/ox_photo.png` to a local agent and verify the native photo
bubble, workspace archive and vision request.

## What was tried

1. Paperclip → photo grid. The first tile opened the camera preview instead of a
   photo; backed out without taking or sending anything.
2. Paperclip → 文件 → 内部存储器 → Download → `ox_photo.png`.
   The picker navigation worked and the file was visible, but tapping the file made
   the emulator lose `adb` before the preview/send screen appeared.
3. Repeated with a plain text file (`ox_file.txt`, 45 B) to rule out image decoding:
   attach → file → internal storage → Download → tap the file. The emulator went
   offline (`adb` reported `offline`) at the same point, before any preview or
   caption screen.

## Result

- No media message was sent in any attempt. After each reboot, `messages_v2` for
  Yuki's dialog still ends at 06:11 (the previous tool test); there are no new
  `oxagents/<id>/media` files and no `inbox` copies.
- The crash is reproducible when Telegram moves from the system/document picker to
  the attachment preview, for both an image and a text document, so it is not image
  decoding in the app. No app FATAL is recorded because the whole emulator process
  disappears (SwiftShader/Vulkan path on this AVD).
- The media send path therefore remains **unverified**; it needs a stable emulator
  or a real device. This was not a code change.

## Next step

Retry on a real device or a different emulator image/GPU: send a photo with a
caption, check the native bubble, the `files/oxworkspace/<agent>/inbox` archive and
the model's vision response. For a document, verify `read_file` sees the archived
copy. Do not treat the media path as verified until that run succeeds.
