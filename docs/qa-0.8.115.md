# QA — SillyTavern PNG character card import (0.8.115, 2026-10-26 21:12–21:20)

Build: `io.github.blancatora.orionekox` versionName 0.8.115 (versionCode 1309).

## Feature

- The character-card importer now accepts PNG cards: it walks PNG chunks and reads the
  `tEXt` chunk named `chara` (or `ccv3`), base64-decodes it and parses the same
  SillyTavern JSON as the JSON path. Plain JSON cards still work.
- Provider inheritance from 0.8.114 is reused (prefers URL + key).

## Verified

- Generated `png-card.png` with a `chara` tEXt chunk containing a card named
  “PNG Card”, description “A card embedded in a PNG tEXt chunk.”, personality/scenario/
  first message.
- Agent list → 导入角色卡（SillyTavern JSON）→ picked the PNG.
- A new agent `PNG Card` was created; its `agent.json` contains the mapped persona and
  inherited `opendesign / deepseek-v4.1-flash / https://amr-link.open-design.ai/v1`
  with a non-empty key.
- No FATAL/ANR.

## Notes

- Only uncompressed `tEXt` chunks are read; `zTXt`/`iTXt` cards are not handled yet.
- JSON cards remain supported; PNG images are not used as the agent avatar in this pass.
- Test agents `PNG Card`, `Sailor Card`, `Sailor Card · 副本` remain as synthetic data.
