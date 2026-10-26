# QA — SillyTavern JSON character card import (0.8.114, 2026-10-26 21:00–21:10)

Build: `io.github.blancatora.orionekox` versionName 0.8.114 (versionCode 1299).

## Feature

- Agent list footer now has “导入角色卡（SillyTavern JSON）” next to the `.oxagent`
  import. It accepts a JSON card (`data` wrapper or legacy flat object) and maps
  description / personality / scenario / system_prompt / post_history_instructions /
  mes_example / first_mes into the agent persona.
- The new agent gets a fresh id/tg_id, a “· 副本” suffix on a name collision, and
  inherits an existing usable provider (prefers an agent whose URL **and** key are both
  present, otherwise any agent with a URL).
- PNG character cards are not parsed in this pass; JSON only.

## Verified

- Imported `silly-card.json` (`Sailor Card`, description/personality/scenario/first
  message). The agent list showed “Sailor Card”; its `agent.json` contained the mapped
  persona and a fresh id.
- First import inherited a URL-only agent (empty key), which exposed the donor-choice
  weakness. After changing the preference to keyed providers and rebuilding, a second
  import (“Sailor Card · 副本”) inherited
  `opendesign / deepseek-v4.1-flash / https://amr-link.open-design.ai/v1` with a
  non-empty key, provider_id `opendesign`.
- No FATAL/ANR.

## Notes

- `first_mes` is embedded in the persona as “# First message”; the native greeting
  stays the standard local-agent greeting.
- The two Sailor Card test agents remain as synthetic artifacts for later cleanup.
