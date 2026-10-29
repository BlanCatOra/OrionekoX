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
  send never lands (120 s). 0.66.0 shows the staged attachment as a PAPER
  capsule with a `WxH · KB` mono line and renders image+caption as one ink
  card with a 0.6-viewport height cap.
- Message surfaces (0.66): long-press opens an in-design menu (bone card,
  48dp rows, SIGNAL dot on Reply) for group assistant rows (Copy / Reply
  <name>), 1:1 assistant rows (Copy) and user bubbles (Copy); user bubbles
  carry a right-aligned mono timestamp.
- Session search (0.66): the Agents-panel query also scans local chat
  histories off-thread and lists SIGNAL-highlighted message hits under a
  "Messages" section; tapping a hit opens that conversation. 0.67.0 adds
  in-conversation search: the header magnifier opens a bar with an n/N
  counter, cyclic up/down navigation, an 80dp scroll lead and a SIGNAL row
  flash.
- Themes and motion (0.67): the palette resolves from the system night mode
  (charcoal ground, cream ink bubbles, unchanged chartreuse signal, status
  bar icons inverted) and all code animations honour the system animator
  scale. Symbol-only controls carry content descriptions.
- Settings and agent editing (0.68): an in-app settings sheet (Agents footer
  → Settings) offers theme follow/light/dark with immediate recreate, plus a
  version/about block; the agent detail gains an Edit action that opens a
  form for name, remark, model, persona and multi-select SKILL.md
  association, saved back through OxAgentStore. BACK now closes overlays
  (menu, sheets) before leaving the conversation.
- Skins and shell (0.69): nine named skins (the seven directions from the
  model design battle plus reading/ink-blue variants) each carrying eight
  color tokens, radius and type scales; a Customize editor edits raw hex
  tokens, radius and type scale and can copy the skin JSON. The shell layer
  makes navigation and chrome first-class settings: rail / bottom bar /
  floating dock, solid / acrylic / mica / glass surfaces, dock position
  (top / above input / bottom) and rail/footer visibility. Clay is the human
  voice (user bubbles, quotes); chartreuse stays the single action signal.
- Terminal (0.69): a dedicated dark surface with underline session tabs,
  a LOCAL status line, grouped quick keys, command history, Tab completion,
  an Aa size cycle and honest local commands; aesthetics follow Termux's
  real-device terminal and Termius's session chrome. 0.71.0 audited every
  palette against WCAG contrast (text >= 6.6, dim >= 4.0, accent >= 4.75)
  and stopped dimming indented command output, which fixed the "unreadable
  Nord" class of bugs. Credentials follow Termius's uniform key row: PWD
  and PST are text keys like the rest, backed by a local-only terminal
  secret that never enters the exported skin JSON.
- File attachments (0.72): the composer ＋ opens a Photo / File menu and
  files travel through the same local pipeline as photos — a 📄 capsule
  preview, a real TL document message, a paper file bubble with name/size/
  caption in OxUI, and a size/mime placeholder in the agent prompt.
- Component DIY and materials (0.70): the skin now also carries component
  toggles (message density, timestamps, assistant accent rule, dock item
  visibility) and can be imported as JSON, not just exported. Floating docks
  overlay content and are given a real blurred surface (snapshot, 1/6
  downscale, two box-blur passes, material tint, rounded outline, refreshed
  on scroll). The terminal offers eleven built-in palettes — Termius
  Dark/Light, Solarized Dark/Light, Dracula, One Dark, Nord, Gruvbox Dark,
  Tokyo Night, Monokai, pure black — where the accent colour drives the
  prompt, session underline, send key and input border.

## Not done yet / next

- Root switch only applies to plain launcher starts; deep links/shares keep the
  Telegram path.
- Avatar editing moved offline in 0.65; chat search exists at panel level
  but there is no in-conversation search bar or hit navigation yet.
  Video/GIF/file attachments, settings, agent editing and full memory pages
  are still Telegram-UI-only (or absent) in OxUI.
- Targeted replies have no quote semantics yet (the chip forces the member, but
  the quoted message is not embedded in the turn context).
- The preview is phone-first; no tablet two-pane layout yet.
- Dynamic theme variants, reduced-motion handling and accessibility sweep are
  still to do.
- Next round: the remaining agent-editor fields (avatar, provider, sampling,
  tool policy) and richer settings (backup/import, bridge), then tablet
  layout and a full TalkBack pass.
