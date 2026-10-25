# Ox UI redesign brief

This pass applies nine public design skills (see `design-skills/`) to the three
areas that looked least native. The goal is not a new visual language: it is
Telegram-grade hierarchy, spacing, states and motion for the Ox-only screens.

## Design tokens

| Token | Value |
|---|---|
| Spacing | 4 / 8 / 12 / 16 / 20 / 24 / 32 dp (4-pt grid) |
| Radii | 12 dp (controls), 16 dp (cards), 24–26 dp (primary button) |
| Type | 28 title / 22 page title / 17 row title / 15 body / 13 secondary / 12 caption |
| Touch | ≥ 48 dp rows, ≥ 44 dp icon buttons |
| Motion | 120 ms state, 180 ms selection, 220 ms page entrance |
| Color | Telegram theme keys only; one accent (chats_actionBackground) |
| Elevation | 0–1 dp cards, 8 dp bottom action bar |

## Area 1 — onboarding (mobile visual + UX)

Problems found: too much empty white space; tiny hero; weak hierarchy; progress
drowned at the bottom; generic colored circles; tall stacked bottom bar.

Changes:
- Light gray page, white cards, 20 dp margins.
- Fixed progress header under the action bar: six segments + step text.
- Brand hero: 72–84 dp mark on a soft beige glow; other pages use a 64 dp icon tile.
- Title 28 sp, body 15 sp with 1.5 line height, centered title, readable measure.
- Options become 16 dp cards with 44 dp icon tiles, 16 sp titles, 13 sp subtitles,
  animated check/selection. Mode cards get an accent border when selected.
- Legal consent becomes its own card; permissions a single white card with 56 dp rows
  and green status pills; container settings become cards.
- Bottom bar: hint only on steps 4–6, exit left / skip step right, 52 dp primary
  button, 8 dp elevation, fixed slots.
- Progress/selection/hero entrance animations; no layout shifts.

## Area 2 — agent editor (forms + progressive disclosure)

Problems found: long undifferentiated form; advanced parameters always expanded;
weak provider call-to-action; no state feedback.

Changes:
- Hero card with avatar, name, provider icon and state.
- Provider card with a prominent “更换” action; model section with “拉取模型” CTA.
- Rounded section cards; helper text for optional fields; key status.
- Advanced parameters collapsed by default with a summary line; expanding animates
  height. Extra request body JSON is the catch-all for provider-specific options.
- Test connection shows a status dot + text; save stays in the action bar.

## Area 3 — lists, settings, sheets, controls (design system)

Problems found: plain full-width rows, no leading icons, empty MCP state is only text,
32-provider sheet has no search, inconsistent radii.

Changes:
- Section cards with 12–16 dp radii and 16 dp margins; 56 dp rows with leading
  36 dp icon tiles; 72 dp divider inset.
- Settings rows get icons; MCP empty state gets an illustration/icon + CTA.
- Provider sheet: half-screen, sticky search field, 56 dp rows, current-provider check.
- Buttons/rows keep 48 dp targets, ripple, pressed scale; one 120–220 ms motion scale.

## Non-goals

- No new fonts, no custom color palette, no decorative gradients beyond the hero glow.
- No change to the Telegram-native chat, login or dialog surfaces.
