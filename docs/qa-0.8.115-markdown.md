# QA — deterministic Markdown rendering (0.8.115, 2026-10-26 21:22–21:25)

Build: `io.github.blancatora.orionekox` versionName 0.8.115 (versionCode 1309).

## Method

- Previously the model would not emit Markdown markers and the composer strips literal
  `**` typed by the user. Instead of relying on the model, imported a SillyTavern card
  whose **name** is `**Bold** Card`. The local greeting is built from the agent name
  (`你好，我是 🤖 <name>。…`) and goes through `OxAgentsBridge.injectAgentReply` →
  `applyMarkdown`.
- Chat-list title still shows literal `**Bold** Card` (titles are not Markdown), but the
  greeting bubble text is “你好，我是 🤖 Bold Card。直接发消息即可开始对话。” with
  “Bold” rendered in bold and both asterisk pairs removed.
- Agent id `carde7d9ee`, tg_id 555000023; no FATAL/ANR.

## Result

- `applyMarkdown` is now verified at runtime for the assistant greeting path: marker
  stripping and native bold entity are both visible.
- This exercises the same code path used by assistant/group replies; no code change was
  needed.
- Test agent `**Bold** Card` remains as a synthetic artifact.
