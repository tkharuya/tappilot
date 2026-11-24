#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_dir="${TAPPILOT_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
avd_name="${TAPPILOT_AVD:-TapPilot35}"
qa_dir="$project_dir/build/qa"
apk="${TAPPILOT_APK:-$project_dir/build/outputs/TapPilot-1.0.0.apk}"
mkdir -p "$qa_dir"
adb_bin="$sdk_dir/platform-tools/adb"
port="${TAPPILOT_EMULATOR_PORT:-5580}"
[[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -ge 5554 ] && [ "$port" -le 5680 ] && [ $((port % 2)) -eq 0 ] || { echo 'Choose an even emulator port from 5554 through 5680.'; exit 1; }
serial="emulator-$port"
target_adb(){ "$adb_bin" -s "$serial" "$@"; }
"$adb_bin" start-server
if "$adb_bin" devices | rg -q "^${serial}[[:space:]]"; then echo 'The selected emulator port is already in use. Choose TAPPILOT_EMULATOR_PORT.'; exit 1; fi
cleanup(){ if [ -n "${emulator_pid:-}" ]; then kill "$emulator_pid" 2>/dev/null || true; wait "$emulator_pid" 2>/dev/null || true; fi; }
"$sdk_dir/emulator/emulator" -avd "$avd_name" -port "$port" -no-window -no-audio -no-boot-anim -no-snapshot -show-kernel -accel "${TAPPILOT_ACCEL:-auto}" -gpu swiftshader -feature -Vulkan -memory 2560 -cores 2 -no-metrics -camera-back none -camera-front none > "$qa_dir/emulator.log" 2>&1 &
emulator_pid=$!
trap cleanup EXIT
echo 'Waiting for test Android device...'
for attempt in $(seq 1 60); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then tail -50 "$qa_dir/emulator.log"; exit 1; fi
    booted="$(timeout 3s "$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$booted" = 1 ]; then break; fi
    if [ "$attempt" -eq 60 ]; then echo 'Android boot timed out'; tail -35 "$qa_dir/emulator.log"; exit 1; fi
    if [ $((attempt % 15)) -eq 0 ]; then echo "Waiting for Android ($attempt checks)"; fi
    sleep 2
done
echo 'Android boot complete'
target_adb shell settings put global window_animation_scale 0
target_adb shell settings put global transition_animation_scale 0
target_adb shell settings put global animator_duration_scale 0
target_adb shell input keyevent 82
target_adb install -r "$apk"
target_adb install -r "$project_dir/build/device-tests/TapPilot-tests.apk"
target_adb shell pm grant jp.tappilot.app android.permission.POST_NOTIFICATIONS
target_adb shell settings put secure enabled_accessibility_services jp.tappilot.app/jp.tappilot.app.TapService
target_adb shell settings put secure accessibility_enabled 1
target_adb shell am instrument -w -r jp.tappilot.test/jp.tappilot.app.DeviceSmokeTest | tee "$qa_dir/device-results.txt"
target_adb pull /sdcard/Android/data/jp.tappilot.app/files/. "$qa_dir/" || true
target_adb logcat -d -b crash > "$qa_dir/crash-log.txt"
if ! rg -q 'OK: .*Android smoke tests passed' "$qa_dir/device-results.txt"; then exit 1; fi
