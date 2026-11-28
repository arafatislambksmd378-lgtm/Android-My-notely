#!/usr/bin/env bash
# The exact build-job bytes are installed after tests, not an independently signed rebuild.
set -euo pipefail
adb shell getprop ro.kernel.qemu | tr -d '\r' | grep -qx 1
for apk in $(find packaged-apks -name '*.apk' | sort); do
  [[ "$apk" == *unsigned* ]] && continue
  if [[ "$apk" == *app-debug.apk ]]; then package=com.notely.app.debug; else package=com.notely.app; fi
  # Disposable CI emulator only; recovery tests already finished.
  adb uninstall "$package" >/dev/null 2>&1 || true
  adb logcat -c
  adb install -t "$apk"
  for permission in android.permission.CAMERA android.permission.POST_NOTIFICATIONS; do
    adb shell pm grant "$package" "$permission" || true
  done
  result=$(adb shell am start -W -n "$package/com.notely.app.MainActivity")
  echo "$result"
  grep -q 'Status: ok' <<< "$result"
  sleep 5
  adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q "$package"
  if adb logcat -d -b crash | grep -q 'FATAL EXCEPTION'; then
    adb logcat -d -b crash; exit 1
  fi
  echo "::notice title=Exact APK install::$package installed, launched and remained foreground; no immediate fatal crash."
done
