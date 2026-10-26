# QA — rename updates the welcome bubble (0.8.110, 2026-10-26 20:10–20:19)

Build: `io.github.blancatora.orionekox` versionName 0.8.110 (versionCode 1259).

## Fix

- `OxAgentProfileActivity` now records the old name before saving.
- `OxAgentsBridge.onAgentRenamed` runs on the storage queue, reads the last 200
  `messages_v2` rows for the agent, deserializes them, and rewrites any assistant
  message starting with “你好，我是 …” to the new greeting (current emoji + name).
  Updated rows are written back with `putMessages` and posted as
  `replaceMessagesObjects` to refresh an open chat.
- No change to ordinary messages; only the welcome prefix is matched.

## Verified

- Renamed the existing copy “Yuki Waifu · 副本” to “RenamedCopy” in the agent editor.
- Log: `OxBridge: onAgentRenamed updated greetings did=555000019 count=1`.
- Opening the RenamedCopy chat shows:
  “你好，我是 🤖 RenamedCopy。直接发消息即可开始对话。”
  instead of the old copy name.
- No FATAL/ANR; other messages in the chat unchanged.

## Notes

- The test agent remains named `RenamedCopy`; it is a synthetic test artifact.
- Only stored welcome bubbles are rewritten; a greeting already deleted by the user
  is not recreated.
