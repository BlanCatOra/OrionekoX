# QA — local group image vision (0.8.109, 2026-10-26 19:50–20:06)

Build: `io.github.blancatora.orionekox` versionName 0.8.109 (versionCode 1249).

## What changed

- `handleOutgoingGroupMedia` now keeps the first image path and passes it into the
  group turn. `onGroupMessage`/`startGroupTurn`/`groupMessages` gained an optional
  image path; the user message is sent as `visionContent(text, imagePath)` (the same
  OpenAI-compatible `image_url` payload used by single-agent photos).
- `prepareSendingDocuments` now routes local-group file sends through
  `handleOutgoingGroupMedia` too, so a JPEG chosen via “文件” is persisted as a local
  photo instead of falling into the server pipeline (red exclamation).

## Verified

- Group `TestUser、Yuki Waifu 和 Yuki Waifu · 副本` (3 members).
- Pushed a test image with “GROUP VISION 42” on a dark blue background and a yellow
  border. Sent it through 文件 → group-vision.jpg.
- Log: `persistAndShow media type=1 mediaClass=TL_messageMediaPhoto photoNonNull=true`.
- Bubble: real thumbnail, double checks, no red exclamation.
- Yuki replied: “这张我看清楚了：深蓝底上框着一个黄色矩形边框，中间一行白色小字写着
  GROUP VISION 42 ✨ 是某种测验图或暗号吗？” — the model did receive and read the
  group image.
- The older failed document bubble from the pre-fix build still shows its historical
  red mark; new sends are clean.

## Notes

- Only the first image in a multi-image group send is attached to the vision prompt.
- Emulator crashed several times while opening Chromium/WebView/gallery surfaces; it
  was restarted with `-gpu off` and the test continued. No app FATAL/ANR was observed.
