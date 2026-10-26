# QA — terminal TTY tabs (0.8.87, 2026-10-26 11:35–11:39)

Build: `io.github.blancatora.orionekox` versionName 0.8.87 (versionCode 1029),
emulator-5554 with `-gpu off`, Android 14. Local account only.

## Covered

| Action | Result |
| --- | --- |
| Tap TTY 2 tab | Switches immediately; header/output shows `Ox Terminal · TTY 2`; tab label `TTY 2 · QA2` retained |
| Run `echo tty2-ok` in TTY 2 | Output `tty2-ok`, prompt returns |
| Long-press TTY 2 tab | Dialog “分配智能体到这个 TTY” lists all agents with QA2 checked, plus “取消分配 / 取消” |
| Tap TTY 3, run `pwd` | Output `/storage/emulated/0/Download` |
| Tap TTY 1, run `help` | Lists `ls · cd · pwd · cat · echo · clear · TTY`, all with Chinese descriptions |
| Layout | With the IME open the composer sits at `[98,1384][821,1501]`, well above the floating main tab bar at `y=2255`; the 0.8.71 inset fix still holds |

- No OrionekoX FATAL/ANR. No files were deleted; only shell output in the three
  in-memory TTY sessions changed.
- `-gpu off` continues to keep the emulator stable for terminal and chat screens.
