# QA — tool-policy reset and Markdown runtime attempt (0.8.113, 2026-10-26 20:45–20:58)

Build: `io.github.blancatora.orionekox` versionName 0.8.113 (versionCode 1289).

## Tool policy reset

- Tool rows keep tap-to-cycle Auto/Ask/Deny, but long-press now removes the per-agent
  override and restores the tool default. A footer hint explains both gestures.
- Verified in 工具与权限: `get_time` was “自动” (left over from the previous test),
  long-pressed the row → it became “默认 · 自动”; toast “已恢复默认策略”.
- Chinese pack 71 adds the hint/reset strings.

## Markdown runtime attempt (not a pass)

- Wanted a deterministic `**bold**` assistant bubble. Tried three prompts asking the
  model to emit Markdown, including typing literal `**bold**` with `KEYCODE_STAR`.
- The composer’s input layer consumed the asterisks before sending (the sent user
  bubble showed plain “bold”), and the model replied without markers (“bold”,
  “bond and code”). Therefore the assistant-side `applyMarkdown` path still lacks a
  runtime fixture; it remains code-reviewed but not visually verified.
- This is recorded as a test limitation, not claimed as passing.

## State

- No FATAL/ANR. The only state change is `get_time` being reset to default, which is
  the intended test result; other policy rows were untouched.
