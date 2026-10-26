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

## Follow-up 0.8.85 / 0.8.86

- Root cause of the remaining state: documents sent through the “文件” picker go
  through `handleOutgoingSpecial`, which injected the picker's original document
  (no local markers) and a temporary `p.path` that is deleted after sending.
- 0.8.85 marked that document with `id/dc_id = Integer.MIN_VALUE` and
  `localPath = p.path`; the X and “0 KB / size” text disappeared, but the bubble
  still showed a download-arrow icon because the temp path no longer existed.
- 0.8.86 archives the file into the agent workspace `inbox/` first and points both
  `attachPath` and `localPath` at that durable absolute path. Freshly sent bubbles
  show a document card with “2.7 KB PNG” and no X/0 KB; the icon still renders as a
  download arrow until the app is restarted, after which it becomes the normal
  document icon. Delivery is unchanged: each send creates another
  `inbox/<timestamp>_ox_photo.png`, and Yuki lists/replies.
- Remaining polish: force a cell/media-state refresh after `persistAndShow` so the
  download-arrow icon resolves immediately instead of on next app start.

### 0.8.87 — final live-state fix

- `persistAndShow` now sets `MessageObject.attachPathExists = true` and
  `mediaExists = true` when the message's `attachPath` exists on disk.
- Verified with a fresh `ox_photo.png` send at 11:32: the bubble immediately shows
  the orange document icon and “2.7 KB PNG”, with no X, no “0 KB”, and no download
  arrow — no app restart needed. Agent delivery still works.
