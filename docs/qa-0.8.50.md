# QA 0.8.50 — focused pass (2026-10-26 05:50–05:53)

Environment: emulator-5554, release 0.8.50 (versionCode 659), Chinese UI,
real OpenDesign model used earlier for the long-press E2E.

## Checks

| Area | Action | Result |
| --- | --- | --- |
| Ox settings → 工具与权限 | Scrolled to the `ui_*` tools | `ui_long_press` and `ui_long_press_text` are present with Chinese descriptions and default `询问` (Ask) |
| 终端设置 | Selected `Ox · 炭黑底绿字` + `18 sp`, then restored | Radio state updates immediately; restored to `Ubuntu · 茄紫底` + `16 sp` |
| 工作流 | Opened after deleting the test entries | Empty state shown; `ox_workflows` is `[]`; no `RUN_WORKFLOW` alarms |
| Logs | Scanned the last ~900 lines | No FATAL or ANR entries |
| 长按工具 | Earlier E2E (same build) | Coordinate long-press triggered multi-select; text long-press opened the agent menu |

## Known issues

- Chat-list `DialogCell` rows are custom-drawn and expose no readable text to the
  accessibility service, so `ui_tap_text` / `ui_long_press_text` cannot target them.
  Use `ui_dump` plus `ui_long_press` coordinates for that screen; settings and agent
  list rows work with the text variants.
- An Ask permission dialog can swallow a gesture dispatched in the same frame it
  closes; set the tool to Auto or let the screen settle before acting.

## Artifacts

- Version 0.8.50 (659), commit `bff0c54`, report section 5.51.
- No code changes in this QA pass; the terminal settings were restored to their
  previous values and the test workflows were removed.
