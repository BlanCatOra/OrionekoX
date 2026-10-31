# QA — bar-rice P1 fixes (0.8.104, 2026-10-26 18:25–18:48)

Build: `io.github.blancatora.orionekox` versionName 0.8.104 (versionCode 1199),
emulator-5554 with `-gpu off`. Local agents/groups only; no real contact messaged.

## Fixes verified

### Fresh agent + immediate group creation (A/C P1)

- Duplicated Yuki through long-press → 复制; log assigned a fresh
  `tgId 555000019` to the copy.
- Created a group immediately with Yuki + the copy: log
  `handleCreateChat users=2 agents=2 real=0`, `createLocalGroup id=1800000005 agents=2`;
  profile showed “3 位成员, 3 人在线” with TestUser + Yuki + copy. Previously C was
  silently dropped because `createChat` skipped users missing from the users cache;
  `createChat` now calls `syncAgents(false)` before building `req.users`.

### Group @ routing (B P1)

- In the new group, `@agent_19 reply with OK` produced “OK ✨” from
  “Yuki Waifu · 副本”, not Yuki.
- In the earlier group, `@agent_17 reply with the word B` produced “B” from
  酒吧炒饭B. `onGroupMessage` now resolves `@agent_<id>` before display-name matching.

### Local group photo (B P1)

- Group attach → gallery → send persisted `TL_messageMediaPhoto`
  (`persistAndShow media type=1 ... photoNonNull=true`) with
  `did=-1800000005 out=true`; two local replies followed and no red exclamation
  appeared. Previously the negative dialog id fell through to the server pipeline.
- Limitation: the group agent cannot read the image content; it replied “图片我这边
  没能加载出内容”. Group vision is not part of this fix.

### Chat history / StartBot (A/B/C P1)

- Reopened 酒吧炒饭C from the chat list twice: existing history rendered immediately,
  normal input field, no “开始Bot” overlay. `refreshDialogRow` now reads the newest
  `mid` with `ORDER BY date DESC, mid DESC LIMIT 1` instead of an undefined `mid`
  from a `MAX(date)` aggregate; `ChatActivity` no longer shows the bot start button
  for local agents.

### Settings bottom row (B P2)

- Scrolled 设置 to the end: 代理与 VPN and the version footer are fully above the
  floating tab bar. Added 64dp bottom padding to the settings list.

## Implemented but not fully re-tested

- `.oxagent` import now clears `tg_id`, generates a new id, appends “副本” on a name
  collision, and calls `syncAgents(false)`; not re-imported in this pass.
- Markdown: `**bold**`, `` `code` `` and `## heading` are converted to native
  Telegram entities before injection. The test model did not emit markers on request,
  so runtime rendering still needs a deterministic fixture.
- Pack 65: `AccDescrDocumentType` is now “%s 文件”; translation info string changed to
  “翻译按钮会在您点按某条消息时出现。”; export filename keeps Chinese characters and
  only replaces illegal path characters.

## Still open

- Group agent cannot see images sent to the group.
- Copy/rename greeting still uses the old agent name.
- OxLinux onboarding step 6 layout (C P1) untouched.
- MCP `mcpServers` JSON / skills / theme import (C P2, Kelivo/RikkaHub compatibility)
  not implemented yet.
- Test objects from A/B/C remain on the device for later cleanup.

## Evidence

- Logs: `createLocalGroup id=1800000005 agents=2`, `@agent_19` reply, group photo
  `did=-1800000005`, `persistAndShow media type=1`.
- Screenshots: `/tmp/h5.png` (3-member group), `/tmp/h6.png` (@ copy reply),
  `/tmp/i5.png` (settings footer above tab bar).
