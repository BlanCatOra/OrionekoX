# Media bubble follow-up — 0.8.83 / 0.8.84 (2026-10-26 11:04–11:14)

## Changes tried

- 0.8.83: `buildLocalDocument` now sets `document.localPath = path`, so
  `FileLoader.getPathToAttach` can return the already-written file instead of
  treating the document as a remote download.
- 0.8.84: additionally marks local documents with
  `document.id = Integer.MIN_VALUE` and `document.dc_id = Integer.MIN_VALUE`,
  matching Telegram's local-only document convention.

## Result

- A new photo was sent to Yuki on each build. The live bubble immediately after
  sending still shows the X placeholder and “已下载 0 KB / 2.7 KB”.
- After an app restart, the same bubbles render as a normal document card
  (“ox_photo.png, 2.7 KB PNG”). So the file bytes and the bubble layout are fine;
  the remaining state is a live upload/download-progress flag on the intercepted
  message, which is never cleared because the message does not go through the
  normal upload pipeline.
- Delivery to the agent remains correct: each send produced a new
  `inbox/<timestamp>_ox_photo.png` and Yuki replied after `list_dir`.

## Next step

Rather than document metadata, clear the progress state after
`persistAndShow` (for example set the document's upload progress / send state
explicitly, or call the file-loader progress completion path) so the live bubble
does not say “0 KB / 2.7 KB” until the next app start.

## Data notes

- The 11:01, 11:08 and 11:13 photo bubbles and Yuki's replies are local test
  artifacts in the Yuki Waifu chat. No real contact was messaged.
- No FATAL/ANR was produced by OrionekoX in this pass.
