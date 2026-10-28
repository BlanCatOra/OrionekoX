# OxUI (preview) — design notes

Date: 2026-10-28. Status: preview front end implemented; it is selectable in
onboarding and Ox Settings, but Telegram UI remains the app root until parity
is complete.

## Brief

OrionekoX is a local agent studio: agents, harnesses and memory live on the
device, and the user talks to them in a quiet, recurring way. OxUI should feel
like a desk for that work, not a generic AI chat wrapper. It must not use
Telegram's cells/action bar, but it must share the same local agents, chat
history and send path.

## Research used

- The bundled Claude `frontend-design` skill (local plugin): design lead
  framing, one bold idea, type as personality, avoid template tells, critique
  with screenshots.
- Claude Academy: *Elevate Claude's design using skills* — interrogate every
  choice, select 1–2 techniques and apply them strongly, balance boldness with
  restraint.
- Community references found via GitHub search: oil-oil/oil-ui,
  superdesigndev/superdesign-skill, wilwaldon/Claude-Code-Frontend-Design-Toolkit,
  Anionex/agent-vision-toolkit. The common thread is a named visual direction,
  a compact token system, and refusing the default "AI slop" card kit.

The Claude skill explicitly calls warm cream + high-contrast serif + terracotta
a generic AI tell. The brief asks for a Claude-inspired feel, so this design
keeps editorial serif type and generous negative space but replaces the
expected terracotta with a **chartreuse signal** and a deep green-black ink.

## Token system

| Token | Value | Role |
|---|---|---|
| Bone | `#F1EBDD` | page ground, warm but not cream-white |
| Paper | `#E5DCCB` | composer, tool notes |
| Ink | `#14211C` | text, selected agent, user bubble |
| Muted | `#69766E` | meta, secondary copy |
| Signal | `#C8EF6A` | send, preview tag, assistant rule |
| Coral | `#CE6549` | reserved for errors/destructive states |

Type: `serif` for the wordmark, empty states and assistant prose; humanist
`sans-serif` for controls/body chrome; `monospace` only for small metadata and
tool output. Scale: 27 wordmark, 26 empty title, 16.2 assistant body, 15.5 user
body/composer, 10.5–13 meta. Body line spacing is generous; assistant prose is
not boxed.

Layout: a single editorial column. A horizontal agent rail sits under a quiet
header; the conversation uses a full-height chartreuse hairline for assistant
turns and ink pills for the user; a floating paper composer anchors the bottom.
The only entrance animation is the conversation fade/rise on first render.

## Why these choices

- The assistant message is a **reading surface**, not a card; the user message
  is a compact ink object. This makes the two voices structurally different
  instead of mirroring the usual left/right bubbles.
- Chartreuse is used once, as a signal (send, preview, assistant rule), not as
  a gradient wash or a decorative accent everywhere.
- The agent rail is the navigation; no sidebar chrome or numbered markers.
- Copy is plain and action-oriented ("Write something…", "Open Telegram UI"),
  with the model's Markdown converted to native spans rather than shown raw.

## What is wired now (0.64.0)

- Entry points: Ox Settings → Interface (Telegram UI / OxUI preview) and the
  onboarding final page. Both persist `interface_mode`.
- Root switch: a plain launcher start with `interface_mode=oxui` opens OxUI
  directly; deep links stay on Telegram UI. OxUI's Telegram UI button clears the
  task and switches back; back in root mode sends the task to the background.
- OxUI reads real local agent history from `OxAgentStore` and lists real agents.
- Sending calls `OxAgentsBridge.sendFromOxUi`, the same local path used by the
  Telegram UI, so the user turn and agent reply appear in both.
- Agents panel: name/remark search, Harness + recent-message subtitle, tap to
  chat, long-press for detail; detail shows harness/model, remark, memory stats
  and recent memory; message long-press copies.
- Markdown (`**bold**`, `` `code` ``, `#` headings) is rendered with native
  spans inside message bubbles.
- Groups (0.62–0.64): Telegram UI New Group with only local agents creates a
  local group offline (`createLocalGroup`), listed in OxUI's Groups panel and
  messageable from the same composer (rail group chip). 0.64.0 adds the offline
  avatar branch — ImageUpdater returns local PhotoSizes instead of FileUploader
  and `attachLocalChatAvatar` copies the crop cache into TL_chatPhoto
  volume/local-id names so the avatar survives restarts — plus per-agent
  attribution on replies (`OxChatEntry.sender` → "Name · time" meta) and
  targeted `Reply <agent>` from the long-press menu (chip + forced member turn).
- Attachments (0.62): SAF image picker → `files/oxui_media` → real photo
  message; image bubbles with a 16 MB import cap and a coral retry row when a
  send never lands (120 s).

## Not done yet / next

- Root switch only applies to plain launcher starts; deep links/shares keep the
  Telegram path.
- Editing the avatar of an existing local group, video/GIF/file attachments,
  chat search, settings, agent editing and full memory pages are still
  Telegram-UI-only (or absent) in OxUI.
- Targeted replies have no quote semantics yet (the chip forces the member, but
  the quoted message is not embedded in the turn context).
- The preview is phone-first; no tablet two-pane layout yet.
- Dynamic theme variants, reduced-motion handling and accessibility sweep are
  still to do.
- Next round: a third visual pass over groups/media polish from the subagent
  review list, chat/session search inside OxUI, then settings and agent
  editing.
