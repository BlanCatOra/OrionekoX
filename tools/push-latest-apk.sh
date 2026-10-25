#!/usr/bin/env bash
# Install the newest release/debug APKs, copy them to the emulator's Download
# folder, and pin their icons on the emulator HOME SCREEN (outside the drawer).
# No host-desktop copies: the user wants the VM home screen.
set -euo pipefail
cd "$(dirname "$0")/.."

release=$(ls -t dist/OrionekoX-*-alpha-arm64-x64.apk 2>/dev/null | head -1 || true)
debug=$(ls -t dist/OrionekoX-*-debug-arm64-x64.apk 2>/dev/null | head -1 || true)
[ -n "$release" ] || { echo "no release APK in dist/"; exit 1; }

adb install -r "$release" >/dev/null && echo "installed release: $release"
adb push "$release" /sdcard/Download/OrionekoX-latest.apk >/dev/null
if [ -n "$debug" ]; then
  adb install -r "$debug" >/dev/null && echo "installed debug: $debug"
  adb push "$debug" /sdcard/Download/OrionekoX-debug-latest.apk >/dev/null
fi

# Pin icons to the launcher workspace (container -100 = home, not drawer).
adb root >/dev/null 2>&1 || true
db=/data/data/com.google.android.apps.nexuslauncher/databases/launcher_4_by_5.db
rel_intent='#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=io.github.blancatora.orionekox/org.telegram.ui.LaunchActivity;end'
dbg_intent='#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=io.github.blancatora.orionekox.debug/org.telegram.ui.LaunchActivity;end'
now=$(date +%s%3N)
adb shell "am force-stop com.google.android.apps.nexuslauncher" >/dev/null 2>&1 || true
adb shell "sqlite3 $db \"DELETE FROM favorites WHERE container=-100 AND title LIKE 'OrionekoX%';\""
adb shell "sqlite3 $db \"INSERT INTO favorites (title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,appWidgetId,icon,appWidgetProvider,modified,restored,profileId,rank,options,appWidgetSource) VALUES('OrionekoX','$rel_intent',-100,0,0,4,1,1,0,-1,NULL,NULL,$now,0,0,0,0,-1);\""
if [ -n "$debug" ]; then
  adb shell "sqlite3 $db \"INSERT INTO favorites (title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,appWidgetId,icon,appWidgetProvider,modified,restored,profileId,rank,options,appWidgetSource) VALUES('OrionekoX Debug','$dbg_intent',-100,0,0,3,1,1,0,-1,NULL,NULL,$now,0,0,0,0,-1);\""
fi
pid=$(adb shell pidof com.google.android.apps.nexuslauncher 2>/dev/null | tr -d '\r' || true)
if [ -n "$pid" ]; then adb shell "kill -9 $pid" || true; fi
sleep 2
adb shell input keyevent 3
echo "pinned OrionekoX icons on the emulator home screen"

# Optional housekeeping: remove the old host-desktop APK copies.
rm -f "$HOME/Desktop/OrionekoX-latest.apk" "$HOME/Desktop/OrionekoX-debug-latest.apk"
