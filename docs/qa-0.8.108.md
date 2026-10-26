# QA — OxLinux onboarding layout (0.8.108, 2026-10-26 19:22–19:45)

Build: `io.github.blancatora.orionekox` versionName 0.8.108 (versionCode 1239).

## Bug reproduced

- 设置 → Ox 设置 → OxLinux (opened at step 6/6) showed step 5 (“权限配置”) peeking
  on the left while step 6 started at x≈398; the title was clipped and the bottom
  action still read “登录 Telegram” even though the account was logged in.
- ViewPager diagnostics: `current=5 scroll=5400 width=1080`, every page had
  `translationX=345` despite the transformer’s apparent 26dp parallax. The whole page
  was shifted one third of a screen to the right.

## Fix

- Removed the horizontal translation from `setPageTransformer` (alpha-only now) and
  clear `translationX` on all child pages when aligning the start page.
- `setStartPage` now waits for layout, settles `setCurrentItem(..., false)`, forces
  `scrollTo(target * width, 0)`, and re-settles after 120ms.
- Last-step action text uses “开始使用 OrionekoX” when
  `UserConfig.isClientActivated()`, instead of “登录 Telegram”; unauthenticated
  login mode still shows “登录 Telegram”.

## Verified

- Reopened OxLinux at step 6: title “配置你的 OxLinux 容器环境” is fully visible and
  centered; step 5 no longer peeks; cards span the full width.
- Bottom action reads “开始使用 OrionekoX” on the logged-in account.
- No FATAL/ANR in logcat.
- The container toggle/config values were only read; no user data changed.

## Not touched

- Container provisioning/OxLinux runtime itself (still blocked by W^X).
