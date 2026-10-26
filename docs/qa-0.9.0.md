# QA — Agent chat appearance, custom vs Telegram native (0.9.0, 2026-10-26 22:10–23:30)

Build: `io.github.blancatora.orionekox` versionName 0.9.0 (versionCode 1329).

## Versioning

- This is a larger feature iteration, so the **minor** version moved from 0.8 to 0.9
  (patch reset to 0). Policy added to `AGENTS.md` / `docs/RELEASING.md`: patch = fixes
  and QA, minor = feature iteration/compat layer, major = milestone. Only bumping the
  last digit is no longer acceptable.

## Custom vs native

- `Ox 设置 → 外观 → Agent 会话外观` (`OxChatAppearanceActivity`) uses
  UniversalRecyclerView/SettingCell:
  - mode switch: Telegram native / Custom;
  - background color cycle + background image picker/clear;
  - incoming/outgoing bubble color cycles;
  - bubble opacity (100/86/75/63/47%), corner radius (6–22 dp), message font scale
    (0.9–1.2×);
  - JSON import, save named preset, preset list (tap apply / long-press delete),
    one-click reset to Telegram native.
- `OxChatTheme` stores the settings and applies them only while a local-agent chat is
  open; `ChatActivity.onPause` restores Telegram colors/background/font size.
  `MessageDrawable.setOverrideRoundRadius` powers the radius override.

## Verified on device

- Switched to Custom, then set: background `#101114`, incoming `#E8F5E9`, outgoing
  `#EFFDDE`, opacity 86%, radius 10 dp, font 1.1×; saved preset `DarkSea`.
- Opened the `**Bold** Card` agent chat: background is dark, incoming bubble green,
  outgoing bubble pale yellow-green, both semi-transparent with visibly rounded
  corners; message text is larger. Screenshots `/tmp/ca11.png`, `/tmp/ca12.png`.
- The custom state was then cleared externally to give B a native baseline. The UI
  reset button is implemented but the post-custom UI reset path is left for B to
  exercise; JSON import/preset apply are also handed to B.

## Gaps (tracked in docs/beautification-parity.md)

- Bubble border, frosted/blur, gradients, separate UI/code fonts and font family are
  not implemented yet.
- Custom appearance is runtime-scoped while an agent chat is open; per-agent persisted
  themes are not done.
- Appearance JSON import has no preview/conflict rollback yet (unlike MCP import).
- Telegram native reset is verified for the mode page; full UI reset after a custom
  session is on B's checklist.
