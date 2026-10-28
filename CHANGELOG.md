# Changelog

> AI disclosure: entries after 2026-10-24 were produced by the AI coding agent
> **blancatora** (DeepSeek Harness, model `deepseek-v4.1-flash`). See [NOTICE](NOTICE).
> This project is an independent GPL-2.0 fork of DrKLO/Telegram.

## 0.43.0 — 2026-10-28

- Kelivo/Chatbox backups now import every image and file in a message, in
  `contentParts` order, instead of only the first image and first file. The old
  behaviour that discarded files whenever an image was present is gone.
- The first media shares the message text; remaining media become extra
  entries with empty captions. Images are stored under `media/img_<n>`, files
  under `media/<n>_<name>` using the part `fileName`. Existing per-conversation
  caps (40 images / 20 files) still apply.
- The preview explains that multi/mixed attachments are split and reports the
  resulting image/file counts.
- Verified with a 2,392 B backup (text + two PNGs, one data-URL encoded +
  `notes.txt` + `data.json`): five messages, two images and two files. The
  native chat showed a green photo with the text caption, an orange photo,
  `notes.txt` (19 B · TXT), `data.json` (29 B · JSON), and the assistant reply.
  A repeat import replaced the history and left five entries/five rows;
  cleanup returned dialogs/messages to zero with no app FATAL/ANR.
- Limits: 40 images/20 files per conversation and 64 resource files; split
  entries share the source timestamp; `info` parts are ignored; remote URLs are
  not downloaded; no export roundtrip was re-tested.

## 0.42.0 — 2026-10-28

- RikkaHub messages can now import every media part (up to 20): images become
  native photo bubbles, while `document`, `video` and `audio` parts become file
  bubbles. The first media shares the message text; the remaining attachments
  are emitted as extra bubbles in order. Video/audio names fall back to the URL
  basename instead of the internal `rikka_upload_` prefix.
- Deduplication now includes the image/file basename, so multiple attachments
  in one message no longer collapse into a single entry. Extra bubbles share
  the node timestamp, keeping later messages ordered and remaining compatible
  with 0.41.0 single-file history.
- The result reports the image count and an attachment count covering files,
  video and audio.
- Verified with a 2,016 B backup (text + two PNGs + `report.txt` + mp4 + mp3):
  six messages, two photo bubbles and three file bubbles (`report.txt` 24 B ·
  TXT, `clip.mp4` 24 B · MP4, `clip.mp3` 10 B · MP3), followed by the assistant
  reply. A repeat import left six entries and six native rows; cleanup returned
  dialogs/messages to zero with no app FATAL/ANR.
- Limits: max 20 media per message; video/audio are generic file bubbles with
  no thumbnail, inline player or duration; only `upload/` matches are
  supported; no export roundtrip was re-tested this round.

## 0.41.0 — 2026-10-28

- RikkaHub backups now import non-image `document` parts as local file
  messages. The importer matches the part URL against `upload/`, copies the
  file into app-private storage, stores `file_path`/`file_name`, and allows
  pure-file messages with an empty caption; the result reports the attachment
  count.
- Imported RikkaHub conversation history is now replayed into the native chat
  after import. Previously `chat.json` received the messages but the native
  dialog could still show only the greeting.
- Verified with a 1,064 B backup (one conversation, two messages, 37 B
  `report.txt`): the native chat rendered a `report.txt` document bubble
  (37 B · TXT, caption “看附件”) and the assistant reply. A repeat import
  added no duplicate rows; cleanup returned dialogs/messages to zero with no
  app FATAL/ANR.
- Limits: only the first document per message; images/documents only;
  video/audio are skipped; only `upload/` matches are supported; imported
  files are not yet part of Chatbox export.

## 0.40.0 — 2026-10-28

- Chatbox export now prefers the active conversation background over the agent
  background when an agent has chat history, so the exported session matches the
  wallpaper the user actually sees. Chatless copilots still export the agent
  background; resource format and caps are unchanged.
- Verified with an agent whose agent background was BG38 but whose conversation
  override was BG40: the native chat rendered BG40, and the exported background
  resource decoded to the BG40 SHA-256 (not BG38). Deleting the original
  agent/theme/dialog and re-importing restored BG40 and the wallpaper.
- The conversation specificity is flattened to agent scope on re-import; only
  image backgrounds are exported (not global or video).

## 0.39.0 — 2026-10-28

- Chatbox exports now include the agent-scope chat background. Agents with chat
  get a session-scoped `background` resource and `session.background`; chatless
  copilots get a global background resource and `copilot.background`.
- Background assets use the same data-URL payload, SHA-256/size checks and
  2 MB/total caps as other resources; headers and keys are still excluded.
- Verified with a BG38 agent: the 15,580 B export contained one session and one
  background resource with the correct storage key. After deleting the original
  agent/theme and re-importing, background.png (14,884 B) and the agent theme
  were restored, and the native chat showed the BG38 wallpaper again.

## 0.38.0 — 2026-10-28

- Kelivo/Chatbox chat backgrounds are now imported: `kind=background` resources
  and session/copilot `background`/`backgroundImage` references are copied into
  the agent directory and applied as an agent-scope image theme after sync.
- A different background already configured on the agent is preserved; the
  result notes how many backgrounds were applied and that the rest kept their
  custom background.
- Preview and result report background counts.
- Verified with a 300×500 BG38 PNG: import wrote background.png (14,884 B) plus
  the agent theme JSON, and the native chat rendered the image wallpaper. After
  changing the agent background to custom-bg.png, a repeat import applied zero
  backgrounds and left the custom path untouched.

## 0.37.1 — 2026-10-28

- Finished the Chinese copy for the newer export flows: the Kelivo/Chatbox
  conversation and export previews, MCP JSON export, and skill-library export
  now have Chinese strings (27 new entries), and the empty-backup copy mentions
  conversations.
- Bumped the Chinese pack from 98 to 99 so installed devices reload it.
- Audited every `R.string.Ox*` reference in the Ox UI/import code against the
  pack: zero missing entries.
- Verified on device: Ox settings, skill library and MCP export rows are
  Chinese, and the Kelivo preview reads “对话 1 段（2 条消息）/项目：Multi B”
  instead of `Conversations:` / `Items:`. No functional changes.

## 0.37.0 — 2026-10-28

- The MCP page now offers “Export MCP JSON”: SAF creates `orioneko-mcp.json`
  with an `mcpServers` map of HTTP servers (`type=streamableHttp`, `url`,
  `isActive`).
- Headers (including Authorization/API keys), OAuth, SSE/stdio entries and tool
  allowlists are never written, so tokens cannot leak through this export.
  Duplicate names get suffixes; an empty list does not create a file.
- Verified with a test server carrying `Authorization: Bearer QA_SECRET_0370`:
  the 390 B export contained four HTTP servers without any headers field and
  zero occurrences of the secret, while the internal store kept its header.
  Deleting and re-importing the export restored the URL with empty headers and
  left the other three servers untouched.

## 0.36.0 — 2026-10-28

- Added “Export selected agents” in Ox settings: a native scrollable checkbox
  list of non-built-in agents, then SAF creates `orioneko-selected-backup.zip`.
- Only the checked ids are passed to the exporter, so no other agent, session or
  resource is written. An empty selection shows a hint; cancelling SAF clears
  the pending selection.
- Verified with two test agents (Multi B with 2 messages; Single Export with 4
  messages, avatar, image and notes.txt): the 6,697 B export contained exactly
  2 sessions, 0 copilots and 3 resources, and re-importing restored both
  conversations with their image and attachment.

## 0.35.0 — 2026-10-28

- RikkaHub `Avatar.Image` avatars are now imported: the URL basename is matched
  against the backup's `upload/` folder and the image is copied into the agent
  directory. Network URLs are never fetched, and repeat imports keep an existing
  avatar.
- `Avatar.Emoji` is recognized by type-name substring, so RikkaHub's FQN
  serialization keeps its emoji.
- Preview reports image-avatar counts and the overview mentions assistant image
  avatars; the result notes how many avatars were imported.
- Verified with two assistants (upload/RA image and FQN 🦊 emoji): import
  created both, stored a 2,519 B avatar.png, showed the RA header and kept 🦊;
  a repeat import updated both with the avatar hash unchanged.

## 0.34.0 — 2026-10-28

- The skill library can export all visible skills as an SAF-created zip with
  `skills/<id>/SKILL.md` entries, importable by Kelivo, Chatbox, RikkaHub or
  this app. Duplicate ids get `_2`, `_3` suffixes; only SKILL.md bodies are
  written, without scripts, attachments or keys.
- Fixed the import side so the `skills/` container is not part of the skill id;
  re-importing an export now restores `<id>` instead of creating a duplicate
  `skills_<id>`.
- Verified with a 150 B QASkill0340: the export was 2,353 B / 7 SKILL.md files,
  and a 272 B filtered subset re-imported to the exact
  `files/oxskills/QASkill0340/SKILL.md` with no duplicate id.

## 0.33.0 — 2026-10-28

- The non-built-in agent long-press menu now offers “Export Chatbox backup”.
  A confirmation explains that the selected agent's chat, avatar, images and
  attachments are written as a Chatbox v2 zip, without API keys or provider URLs.
- `OxKelivoExporter` accepts an agent-id filter, so a single agent can be
  exported; chat history is written as a session and chatless agents as a
  copilot, with no other agents included.
- SAF creates `<agent name>-chatbox-backup.zip` and the result toast reports the
  message and file counts.
- Verified with a single-agent export of 6,190 B (1 session, 0 copilots,
  3 resources, matching checksums, no `ox1:`/`sk-`/`api_key`/`test-key`).
  Re-importing it restored the prompt, 0.5/0.7/80 sampling, avatar, image,
  notes.txt and four messages, rendered in the native chat.

## 0.32.0 — 2026-10-28

- Added “Export Kelivo / Chatbox backup” in Ox settings: writes all non-built-in
  agents, chats, avatars, images and attachments to a Chatbox v2 zip through the
  system save picker.
- Each agent is written once: agents with chat history become a Chatbox session,
  chatless agents become copilots, so importing the export back does not create
  duplicate agents.
- Resources use data-URL payloads with SHA-256/size descriptors, global/session
  scopes and original storage keys; the manifest carries matching stats.
- API keys, provider URLs, Authorization headers, workspaces, memory, skills,
  MCP and themes are never exported.
- Verified with a 483,866 B full export whose manifest/checksums/stats passed and
  contained no `ox1:`/`sk-`/`test-key`; a filtered 8,306 B subset round-tripped
  back into one assistant and one 4-message conversation with prompt, sampling,
  avatar, image and file intact.

## 0.31.0 — 2026-10-28

- Non-image attachments from Kelivo/Chatbox backups are now imported. Chatbox
  v2 `parsed-attachment`/`raw-attachment` resources are matched to message file
  parts by storage key, raw storage key or URL.
- Matched files are copied into the agent's `media/` directory; chat entries now
  carry `file_path`/`file_name`, and the surrounding text becomes the caption
  while a pure attachment may have an empty caption.
- Attachments replay as native document bubbles (name, size, MIME, local path)
  through `buildLocalDocument`; the model context gets a `[文件] name` note and
  file contents are never executed.
- Caps: 20 attachments per conversation, 2 MB each, 24 MB total. Preview and
  result report attachment counts.
- Verified with a 4-message session containing a raw 35 B text file and a 38 B
  data-URL JSON: the native chat showed notes.txt with its caption and data.json
  as a pure document bubble; a repeat import kept both hashes and 4 message rows.

## 0.30.0 — 2026-10-28

- Chat images from Kelivo/Chatbox backups are now imported. Chatbox v2
  `resources[]` entries of kind `image` (and image attachments) are matched to
  message image parts by storage key or URL.
- Matched images are copied into the agent's `media/` directory and recorded on
  the chat entry; surrounding text becomes the photo caption and a pure-image
  message is allowed to have an empty caption.
- Imported images replay as native photo bubbles through
  `generatePhotoSizes`, with a text fallback when a photo cannot be prepared.
  One image is attached per message and extra images keep a `[图片]` placeholder.
- Caps: 40 images per conversation, 2 MB per image, 24 MB total, PNG/JPEG/GIF/
  WebP only. Preview and result report image counts.
- Verified with a 4-message session containing one raw PNG and one data-URL PNG:
  both landed as files, the native chat showed PIC1 with its caption and PIC2 as
  a pure photo bubble, and a repeat import kept both hashes and 4 message rows.

## 0.29.0 — 2026-10-28

- Kelivo/Chatbox backups now import assistant and conversation avatars. Chatbox
  v2 `manifest.json.resources[]` entries with `kind=avatar` or
  `kind=copilot-image` are mapped through their original storage keys, from raw
  image bytes or `data-url-base64` text.
- A matched image is written to `files/oxagents/<agentId>/avatar.<ext>`, so the
  imported agent shows it in the native chat header and profile. Repeat imports
  keep an existing avatar instead of overwriting a user-set one, and remote
  URLs are never fetched.
- Resources are capped at 64 files, 2 MB each and 24 MB total; only PNG, JPEG,
  GIF and WebP magic is accepted. Preview and result report avatar counts.
- Verified with one assistant and one session using two resources: both avatar
  files landed, the native headers showed the S1/AI images, and a repeat import
  left both hashes unchanged. Test agents and the sample were removed.

## 0.28.0 — 2026-10-28

- Kelivo/Chatbox backups now import conversation history. Chatbox v2 zips are
  read through `manifest.json` and `sessions/*/session.json`; older zips and
  direct JSON exports (`chat-sessions-list` / `session:<id>`) are also accepted.
- Every thread becomes a local agent with a stable id. The first system message
  becomes its prompt, session provider/model/sampling settings are inherited,
  and user/assistant/tool/system turns keep their original timestamps.
- Imported history is replayed into the native message DB, so the stock chat
  screen shows the backup bubbles instead of a greeting. Repeat imports replace
  history without duplicating messages.
- Covers 100 conversations, 500 messages per conversation and 20,000 characters
  per message. Images/files are placeholders, and tool turns are flattened into
  assistant bubbles. Verified with a 3-conversation zip and a legacy JSON sample,
  including repeat-import row counts, then all test objects were removed.

## 0.27.2 — 2026-10-28

- Kelivo/Chatbox backup import now reads HTTP MCP servers from
  `mcp_servers_v1` (also `mcpServers`) into the local MCP list, including name,
  URL, headers and enabled state. Entries are keyed by URL, so repeat imports
  do not duplicate.
- SSE, stdio/command and inmemory entries are reported as skipped and never
  executed. Preview and result show imported/skipped MCP counts, and the
  overview copy now says assistants, personas, skills and HTTP MCP are imported.
- Verified with one HTTP and one SSE entry: only the HTTP server landed with its
  header, a repeat import added nothing, and the test objects were removed.

## 0.27.1 — 2026-10-28

- Kelivo/Chatbox backup import now reads `skills/**/SKILL.md` from the zip into
  the private skill library (up to 50 skills, 512 KB each); same-name skills are
  updated, and preview/result report the skill counts.
- Corrected the preview copy that incorrectly said skills were excluded.
- Verified with a schema sample: one assistant and one 96 B SKILL.md imported,
  then a repeat import updated the assistant and kept exactly one skill folder.

## 0.27.0 — 2026-10-28

- Named presets can now be previewed before applying: long-press opens a native
  menu with 预览 / 重命名 / 删除. Preview reads the stored preset JSON and renders
  its background, bubbles, border, font and line height without writing the
  active scope.
- The preview renderer was refactored to accept any appearance snapshot, shared by
  the current-theme preview and preset preview.
- Verified on device: previewing DarkSea showed its black/green look while the
  current image-background theme and prefs file remained unchanged.

## 0.26.2 — 2026-10-28

- Fixed Operit appearance import blur mapping: `use_background_blur` and
  `background_blur_radius` now drive the 1–3 image blur levels (≥20 strong,
  ≥12 medium, otherwise light) instead of only switching bubbles to frosted.
- Frosted bubbles now follow only the liquid/water glass flags.
- Verified with radius 24: preview reported “模糊强”, the agent JSON stored
  `backgroundBlur=3`, and the preview wallpaper was visibly smoothed.

## 0.26.1 — 2026-10-28

- Kelivo/Chatbox assistant import now reads `provider_configs_v1` from the backup
  and wires each assistant through its `chatModelProvider`: base URL, API key and
  the first model are taken from the referenced provider config, with the
  assistant's `chatModelId` overriding the model name. Missing configs fall back
  to a local provider.
- Preview/result report how many provider configs were used, without showing keys.
- Verified with a schema sample: the referenced provider's URL/model landed in the
  imported agent, an unbound copilot fell back locally, and a repeat import
  updated both existing agents. OAuth, multi-key, proxy and provider-specific
  headers are not imported.

## 0.26.0 — 2026-10-28

- Added Kelivo/Chatbox backup import for assistants: pick a zip or JSON and
  preview the assistant count/names before writing.
- Zips are read through `copilots.json` and the `assistants_v1` value in
  `settings.json` (JSON string, array or id→object map); direct JSON arrays and
  objects are also accepted.
- Assistants map name, system prompt/prompt, temperature, topP, max tokens,
  custom headers and a model-name override. Provider wiring is inherited from
  the first existing agent with a base URL and key (otherwise offline). Repeat
  imports update `kelivo_<id>` agents while preserving tg_id and avatars.
- Conversations, provider keys/configs, skills, MCP, workspaces, avatars and
  preset/regex data are not imported. Verified with a schema sample: two
  assistants created, then a repeat import updated both.

## 0.25.2 — 2026-10-28

- Named presets can now be renamed: long-press opens a native rename/delete menu.
  Rename pre-fills the current name, applies the same file-name sanitization as
  save, updates the JSON name and deletes the old file. An existing target name is
  rejected without overwriting it.
- Verified on device: RenameQA became RenameQA2 with only the new file left;
  renaming to an existing BundleQA was rejected; cancel changed nothing.

## 0.25.1 — 2026-10-28

- Persona recommendations now open a native confirmation dialog naming the
  suggested theme; cancelling leaves the appearance untouched.
- Before applying, the current scope snapshot is backed up under
  `files/ox_recommend/<scope>.json`; an “撤销上次推荐” row restores it and removes
  the backup. Backups are isolated per scope and the first backup is preserved.
- Verified on an agent: recommended 赛博霓虹, then undo restored the original image
  background, opacity, dim and AMOLED state. One test override was removed.
- Fixed a build-only AAPT2 failure caused by an unescaped apostrophe in the English
  confirmation copy.

## 0.25.0 — 2026-10-28

- RikkaHub history import now also collects `upload/` images (up to 40 files,
  4 MB each) and matches them to image parts by file name. Matches are copied to
  app-private storage and stored in the history entry's `imagePath`; image-only
  messages use a `[图片]` placeholder.
- Existing history assembly sends a user entry's `imagePath` as inline base64
  vision content, so imported images remain usable for later multimodal turns.
- Verified with a schema sample: a one-conversation/three-node backup imported a
  70 B PNG as the third entry, and a repeat import kept exactly three entries.
  Remote/data URLs and non-image files are skipped.

## 0.24.1 — 2026-10-28

- Filled 12 missing Chinese strings found by auditing `R.string` references in the
  Ox sources: Operit import (title/explanation/running/done/permission), local
  premium (title/description) and terminal (title/clear/restart/hint).
- Bumped the Chinese pack to 94 so installed devices reload the translations.
  No functional code changed; the settings page and terminal now render in
  Chinese without English fallbacks.

## 0.24.0 — 2026-10-28

- RikkaHub backup import now reads `rikka_hub.db` and imports the selected text
  branch of each conversation into the matching local agent chat.
- For every message node the importer takes `messages[select_index]`, keeps only
  user/assistant text parts, and appends with deterministic timestamps;
  re-imports are deduplicated. Limits: 200 conversations, 500 nodes each, 5000
  messages, 100k characters per message.
- The preview reports conversation/message-node counts and the result reports how
  many messages were imported.
- Verified with a schema sample: two conversations/four nodes imported the
  selected `Branch B selected` (not the unselected branch) and stayed at four
  entries after a repeat import. Images, files, tool parts and attachments are
  not imported.

## 0.23.0 — 2026-10-28

- Added a one-tap AMOLED pure-black toggle in the appearance editor. It paints
  the current scope's wallpaper and system bars pure black while keeping bubbles,
  text, border, font and line height; turning it off restores the stored
  wallpaper.
- The `amoled` flag persists through global/agent/chat scopes, presets and theme
  packages, and the import parser accepts boolean or 0/1 aliases.
- Device QA: with an image background, enabling showed pure black in the editor
  preview and a local group; disabling restored the image and cleared the flag.

## 0.22.0 — 2026-10-28

- Added persona-based appearance recommendation on agent appearance pages:
  local keyword scoring over the agent name and AGENTS.md selects one of the six
  built-in themes, with 深海 as the deterministic fallback. No model or network
  request is made.
- Applying a recommendation replaces the agent's current appearance snapshot and
  shows the chosen theme name; the agent remains scoped to that override until
  cleared.
- Device QA: `barB-nokey` (深夜/酒吧 persona) recommended 赛博霓虹 and rendered
  the expected gradient, purple outgoing bubble, cyan text and 2 dp pink border.

## 0.21.2 — 2026-10-28

- RikkaHub provider `customHeaders` and assistant `customHeaders` are merged by
  header name (assistant overrides provider) into the agent headers; provider
  headers apply when one of its models is referenced by an assistant.
- The first assistant text part in `presetMessages` is imported as the agent's
  opening message (`firstMes`, capped at 4000 characters).
- Fixed a merge bug where pairs were round-tripped through JSON arrays and
  provider headers disappeared; merging now collects from the source arrays.
  Verified both headers and the opener survive a repeat import.

## 0.21.1 — 2026-10-28

- RikkaHub backup import now resolves assistant `enabledSkills` against the
  imported/local skill library by skill name or id and writes the matches to the
  agent's `skillIds`; an empty list preserves the existing selection.
- Assistant `customHeaders` are mapped into the agent's request-header array;
  empty arrays also leave existing values untouched.
- Verified with a schema sample: `Rikka QA` with skill `Rikka Test Skill` and
  header `X-Rikka-Assistant: 1` imported as `skill_ids=["TestSkill"]` and
  `headers=[["X-Rikka-Assistant","1"]]`; repeat import updated the same agents
  and kept both mappings.

## 0.21.0 — 2026-10-28

- Added a live appearance preview in the custom appearance editor: a native
  dialog renders the active scope's wallpaper (including image blur and dim),
  incoming/outgoing sample bubbles, border, text color, chat font, font scale
  and line height.
- The preview reads the current snapshot on every open, so global, per-agent and
  per-chat scopes each show their own values; changing blur and reopening the
  preview reflects the new wallpaper treatment.
- It is a lightweight sample rather than real message cells: media/tool bubbles,
  replies, avatars and real wrapping are not shown, and frosted bubbles remain an
  alpha simulation.

## 0.20.0 — 2026-10-28

- RikkaHub backup import now also reads `skills/<name>/SKILL.md` from the same
  zip (up to 50 skills of 512 KB each) and writes them into the app-private
  skill library through the new `OxSkillStore.importSkill` entry point.
- Skill ids come from the folder relative to `skills/`; repeat imports update
  the same skill instead of creating copies and never delete other local skills.
- The preview and result dialogs report skill counts and existing skills; a
  `settings.json`-only import still works with zero skills.
- Verified with a schema sample: preview “2 assistants · 1 provider · 1 MCP ·
  1 skill”; import created the skill with frontmatter intact; repeat import kept
  exactly one skill directory. Test objects and the sample file were removed.

## 0.19.0 — 2026-10-28

- Added RikkaHub backup import: pick a backup zip or `settings.json` and preview
  the assistants, referenced providers and streamable-HTTP MCP servers before
  writing.
- Assistants map their system prompt, model wiring, emoji avatar, temperature,
  topP, max tokens and reasoning level. Repeat imports update agents with ids
  `rikka_<assistant UUID>` while preserving `tg_id` and custom avatars; MCP
  servers are deduplicated by URL.
- The chat database, attachments, skills, OAuth tokens and SSE MCP servers are
  intentionally skipped. Preview and result dialogs never show API keys.
- A schema-derived sample verified preview, create, idempotent update, headers
  and rejection of a non-RikkaHub file; the test agents and MCP entry were
  removed afterwards. A real RikkaHub export is still needed for a full pass.

## 0.18.0 — 2026-10-27

- Added portable preset bundles (`.oxthemes`): a zip with `bundle.json` and the
  background/font assets referenced by every named preset. Export all presets,
  preview the count/names/duplicate names before importing, and restore assets
  into app-private storage with rewritten paths.
- Imports skip existing preset names instead of overwriting, validate the whole
  bundle before writing (100 presets, 300 entries, 8 MB per asset, 40 MB total),
  and remove files created by a failed import.
- Device QA: colour-only presets re-imported idempotently (0/2); a preset with a
  16 KB wallpaper was exported, deleted, restored (1/2) and applied; an invalid
  file was rejected without touching presets or assets.

## 0.17.1 — 2026-10-27

- Fixed per-chat appearance being skipped on the first open after a cold start:
  the chat activity now re-applies the theme once the message list exists.
- Local groups now honour per-conversation appearance, including background
  blur; the group editor and scoped reset were verified on a local test group.
- Clearing a chat/agent override now reports “已清除覆盖，现跟随上层外观”
  instead of the global “reset to native” message.
- QA note: `-gpu off` repeatedly crashed the emulator host renderer when a chat
  opened; group verification used `-gpu guest`. This is an emulator limitation,
  not an app FATAL/ANR, and a real-device/GPU pass is still pending.

## 0.17.0 — 2026-10-27

- True image-background blur with three levels (轻/中/强), implemented with a
  cached stack blur at 3×/5×/8× downscale. The value travels through global
  preferences, per-agent/per-chat JSON, presets and `.oxtheme`; imports accept
  `backgroundBlur`, `background_blur`, `blur`, `blurSigma` and `blur_sigma`.
- Device measurement on an agent chat: the visible wallpaper strip's grayscale
  standard deviation fell from 29.54 (off) to 0.49–0.54 (strong), and edge
  energy from 2.1 to 0.03.

## 0.16.0 — 2026-10-27

- Per-conversation appearance: a new chat menu icon (local agent chats and local
  groups) opens the appearance editor scoped to that conversation. Overrides are
  stored in `files/ox_chat_dialogs/dialog_<id>.json` and read as
  chat → agent → global; scoped pages inherit the parent look and offer a
  “clear override and follow parent” action.
- Verified on an agent chat: the override rendered immediately, another agent
  kept the global theme, the override survived a force-stop/relaunch, and
  clearing it restored the global look. The local-group entry is wired but not
  device-verified because opening a local group repeatedly crashed qemu through
  Chromium on the `-gpu off` emulator.

## 0.15.0 — 2026-10-27

- Configurable message line height for agent chats: a new row below font size
  cycles 1.0–1.5×, and the value persists in global/per-agent snapshots, named
  presets, JSON exports and `.oxtheme` packages. Imports (including 0.8–1.8
  values) recognise `lineHeight`, `line_height`, `lineSpacing` and
  `line_spacing`.
- `MessageObject.TextLayoutBlocks` applies the multiplier only to the currently
  themed agent dialog, covering text, quote and code blocks; other Telegram
  chats stay at 1.0. Verified at 1.4× on a long Markdown message, and the
  exported theme's `theme.json` contained `lineHeight=1.4`.

## 0.14.1 — 2026-10-27

- Theme every agent-chat bubble: the appearance snapshot now overrides all eight
  bubble drawables (incoming/outgoing, selected, media and media-selected) and
  restores them when the chat closes. Previously only the text bubbles were
  covered, so agent tool-call hints kept the native white media bubble and their
  white custom text became invisible. Verified on existing history without
  sending new messages.

## 0.14.0 — 2026-10-27

- Import Operit / Operit2 appearance backups: unwraps the nested `values`
  object and maps AI/user bubble and text colors, font scale, background
  opacity, theme mode, glass flags and the primary color (border). The same
  preview → confirm → write path is used, so invalid or unrelated JSON leaves
  the current theme untouched. Operit detection requires Operit-specific keys,
  so Kelivo/RikkaHub files are not misread as Operit.
- Verified on device with an Operit preference file: preview and applied rows
  matched, and an agent chat rendered the mapped purple bubbles, white text and
  pink 1dp border over the retained wallpaper.

## 0.13.0 — 2026-10-27

- “Auto palette from background”: samples the wallpaper (or averages the
  gradient/solid colors), then generates a readable incoming/outgoing bubble
  pair, text color, border and dimming. Verified with a dark-blue wallpaper on
  R2C Card while keeping the wallpaper.

## 0.12.0 — 2026-10-27

- Six one-tap built-in themes: AMOLED “Midnight Ink”, Paper, Deep Sea,
  Sakura, Forest and Cyberpunk; each writes a coherent snapshot to the current
  global or per-agent scope.
- Fixed custom text colors: `ThemeDelegate` now overrides
  `chat_messageTextIn/Out` while a custom theme is active, so cyberpunk's cyan
  text and other overrides are no longer reset to the Telegram theme color.

## 0.11.0 — 2026-10-27

- Appearance export now produces a portable `.oxtheme` package containing
  `theme.json`, the chat background and imported chat/code fonts. Import
  detects JSON or the package, previews the included assets, restores them to
  app-private storage and rewrites the snapshot paths.
- Verified on device with an imported TTF and image background: export → reset
  font → re-import → font and background restored.

## 0.10.0 — 2026-10-27

- Per-agent chat appearances with global fallback and “apply to all agents”.
- Solid/gradient/image backgrounds, independent background opacity and dimming,
  system bar colors and a wallpaper-layer override.
- Solid/frosted bubbles, bubble borders, text color and a WCAG contrast hint.
- Chat/code font families with TTF import; JSON import preview before applying;
  theme export to a file; shortcut to Telegram's native chat theme.
- Agent chats retry an empty first history pass (0.9.1) and file-picker images
  become photo bubbles with vision.

## 0.9.1 — 2026-10-27

- Agent chats no longer reopen blank: stale scroll positions are ignored, an empty
  first history pass gets one bounded retry, and the stock “Delete this chat”
  overlay is suppressed while local history loads.
- File-picker images now become photo bubbles with thumbnails and reach vision;
  outbound tool-call bubbles show a short summary instead of raw JSON.
- Theme JSON import accepts snake_case keys (`in_bubble`, `out_bubble`,
  `corner_radius`, `font_size`, …) and Kelivo-style aliases.
- Assistant text double-escaped newlines are decoded before persistence;
  SillyTavern `first_mes` becomes the greeting; skills overwrite on re-import and
  can be deleted; local agents match substring searches.

## 0.9.0 — 2026-10-26

- New Agent chat appearance page: Telegram-native or custom mode, background
  color/image, incoming/outgoing bubble colors, opacity, radius, message font
  scale, named presets, JSON import and one-click native reset.
- Second-round independent test reports A/B/C and the consolidated issue tracker.

## 0.8.103 — 2026-10-26

- Local groups: removing an agent member is fully local. Long-press a member →
  从群组中移除; member count and online count refresh immediately, without a
  `deleteChatUser` request.
- Fixed local-group profile subtitle ordering so “2 位成员, 2 人在线” is shown in place.
- Public repository documentation, AI disclosure, bilingual README, release and
  sanitization checklist, Kelivo/RikkaHub/Operit compatibility analysis.

## 0.8.102 — 2026-10-26

- Local groups: adding an agent after creation is fully local, with no
  `addChatUser` request.
- Fixed `chatInfoDidLoad` fourth-argument NPE in `TopicsFragment` and a duplicate
  member row caused by `ProfileActivity`’s optimistic append.

## 0.8.100 — 2026-10-26

- Local-group creator row renders the real self user (BlanCat · 在线 · 创建者).
- Agent profile actions trimmed to 消息/静音; bot-only share/report/block entries hidden.

## 0.8.95–0.8.99 — 2026-10-26

- Local-group profiles rebuild and persist `ChatFull`, so members and the
  add-member row survive restart.
- Diagnosed the blank creator row and the media bubble X/0KB regressions.

## 0.8.87 — 2026-10-26

- Media bubbles render correctly after local document injection
  (`attachPathExists`/`mediaExists`), files archived to the agent inbox.

## 0.8.55–0.8.56 — 2026-10-25

- Local-only group text is persisted locally with double checks and per-group
  conversation history; group replies use the group log.

## 0.0.1–0.0.6 — 2026-10-24/25

- Fork rebrand, standalone package and signing, dual-ABI sing-box/VPN, local agent
  system, tool calling with per-tool permissions, terminal, MCP, skills, workspace,
  workflows and Chinese localization.

Full per-round evidence lives in
[docs/OrionekoX-alpha-0.0.1-构建报告.md](docs/OrionekoX-alpha-0.0.1-构建报告.md)
and `docs/qa-*.md`.
