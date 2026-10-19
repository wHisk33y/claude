#!/usr/bin/env bash
# Runs on the CI emulator: takes screenshots of every screen in light and dark mode (debug build)
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

# Every adb call has a time limit: a stuck emulator must not block the job until it times out.
shot() {
  sleep "$2"
  timeout 40 adb exec-out screencap -p > "$OUT/$1.png" || echo "::warning::screenshot $1 failed"
}

run_scene() {
  local theme=$1 scene=$2
  timeout 30 adb shell am force-stop $DEBUG_PKG || true
  if ! timeout 60 adb shell am start -W -n "$DEBUG_PKG/$ACT" --es debug_scene "$scene" --es debug_theme "$theme" > /dev/null; then
    echo "::warning::scene $scene did not start in time"
    timeout 120 adb wait-for-device || true
  fi
}

# The CI emulator renders in software and is slow: give every screen time for its first frames.
for theme in light dark; do
  for scene in home world shop goals games walk settings onboarding kuschelhaus album garden trips pass widget wallpaper levelup sick egg catch memory whack runner bubbles simon adventure; do
    run_scene "$theme" "$scene"
    case $scene in
      catch|whack|memory|runner|bubbles) shot "${theme}_${scene}" 12 ;;
      simon) shot "${theme}_${scene}" 14 ;;
      *) shot "${theme}_${scene}" 8 ;;
    esac
  done
done

# All creatures, clothes, eggs and shiny variants
for page in 1 2 3 4 5 6 7 8 9; do
  run_scene light "gallery$page"
  shot "gallery_$page" 9
done

# Small animals, tricks, the adventure map, the tournament and the newer games
for scene in bunny hamster guinea jungle visit2 tricks adventuremap tournament spot words shadow space jumper race; do
  run_scene light "$scene"
  case $scene in
    spot|words|shadow|space|jumper|race) shot "light_${scene}" 12 ;;
    *) shot "light_${scene}" 8 ;;
  esac
done
run_scene dark tricks
shot dark_tricks 8

# The house: overview, every room, the classic room, music and cards
for scene in house kitchenroom bathroom bedroom playroom musicroom gardenroom craftroom classic music cards; do
  run_scene light "$scene"
  shot "light_${scene}" 9
done
run_scene dark gardenroom
shot dark_gardenroom 9

# Furniture, festivals and the new celebrations
for scene in furniture furniture2 furniture3 halloween winter eventshop eggfound birthday tripback; do
  run_scene light "$scene"
  shot "light_${scene}" 8
done
run_scene dark winter
shot dark_winter 8

# Care animations and special moments (two shots each to catch different phases)
for scene in feed bath ball heal love; do
  run_scene light "$scene"
  shot "act_${scene}_a" 9
  shot "act_${scene}_b" 3
done
for scene in night sunset hatch toast memorial; do
  run_scene light "$scene"
  shot "light_${scene}" 8
done
run_scene dark night
shot dark_night 8
run_scene light evolution
shot evolution_a 8
shot evolution_b 6

for scene in forest ocean space candy; do
  run_scene light "$scene"
  shot "room_${scene}" 8
done

# Floating pet over the launcher
adb shell appops set $DEBUG_PKG SYSTEM_ALERT_WINDOW allow || true
run_scene light overlay
shot overlay_a 12
shot overlay_b 4
timeout 30 adb shell am force-stop $DEBUG_PKG || true

# Notification shade
run_scene light notify
sleep 3
timeout 30 adb shell cmd statusbar expand-notifications || true
shot notifications 2
timeout 30 adb shell cmd statusbar collapse || true

timeout 60 adb logcat -d > "$OUT/logcat_debug.txt" || true
timeout 30 adb logcat -c || true

# Release build: fresh start + random taps
timeout 180 adb install -r -g dist/Knuffi.apk
timeout 60 adb shell am start -W -n "$REL_PKG/$ACT" > /dev/null || true
shot release_start 4
timeout 900 adb shell monkey -p "$REL_PKG" --throttle 150 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 -v 2500 > "$OUT/monkey.txt" 2>&1 || true
shot release_after_monkey 2
timeout 60 adb shell am start -W -n "$REL_PKG/$ACT" > /dev/null || true
shot release_resume 3
timeout 60 adb logcat -d > "$OUT/logcat_release.txt" || true

grep -h -B2 -A40 "FATAL EXCEPTION" "$OUT"/logcat_*.txt | grep -A40 "Process: de.knuffi" > "$OUT/crashes.txt" || true
if [ -s "$OUT/crashes.txt" ]; then
  echo "::error::App crashed on the emulator"
  head -n 120 "$OUT/crashes.txt"
  exit 1
fi
echo "No crashes detected."
