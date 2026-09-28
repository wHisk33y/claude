#!/usr/bin/env bash
# Runs on the CI emulator: takes screenshots of every screen in every style (debug build)
# and hammers the release build with the monkey to catch crashes.
set -u
OUT=shots
mkdir -p "$OUT"
DEBUG_PKG=de.knuffi.app.debug
REL_PKG=de.knuffi.app
ACT=de.knuffi.app.MainActivity

adb wait-for-device
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1

adb install -r -g dist/Knuffi-debug.apk
adb logcat -c

shot() {
  sleep "$2"
  adb exec-out screencap -p > "$OUT/$1.png"
}

run_scene() {
  local style=$1 scene=$2
  adb shell am force-stop $DEBUG_PKG
  adb shell am start -W -n "$DEBUG_PKG/$ACT" --es debug_scene "$scene" --es debug_style "$style" > /dev/null
}

for style in kawaii pixel minimal; do
  for scene in home shop goals games walk settings onboarding catch memory whack gallery widget levelup sick night egg memorial; do
    run_scene "$style" "$scene"
    case $scene in
      catch|whack) shot "${style}_${scene}" 6 ;;
      levelup) shot "${style}_${scene}" 2 ;;
      *) shot "${style}_${scene}" 3 ;;
    esac
  done
  run_scene "$style" evolution
  shot "${style}_evolution_a" 2
  shot "${style}_evolution_b" 3
done

for scene in forest ocean space candy; do
  run_scene kawaii "$scene"
  shot "kawaii_room_${scene}" 3
  run_scene pixel "$scene"
  shot "pixel_room_${scene}" 3
done

# Notification shade
run_scene kawaii notify
sleep 3
adb shell cmd statusbar expand-notifications
shot notifications 2
adb shell cmd statusbar collapse

adb logcat -d > "$OUT/logcat_debug.txt"
adb logcat -c

# Release build: fresh start + random taps
adb install -r -g dist/Knuffi.apk
adb shell am start -W -n "$REL_PKG/$ACT" > /dev/null
shot release_start 4
adb shell monkey -p "$REL_PKG" --throttle 150 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 -v 2500 > "$OUT/monkey.txt" 2>&1
shot release_after_monkey 2
adb shell am start -W -n "$REL_PKG/$ACT" > /dev/null
shot release_resume 3
adb logcat -d > "$OUT/logcat_release.txt"

grep -h -B2 -A40 "FATAL EXCEPTION" "$OUT"/logcat_*.txt | grep -A40 "Process: de.knuffi" > "$OUT/crashes.txt" || true
if [ -s "$OUT/crashes.txt" ]; then
  echo "::error::App crashed on the emulator"
  head -n 120 "$OUT/crashes.txt"
  exit 1
fi
echo "No crashes detected."
