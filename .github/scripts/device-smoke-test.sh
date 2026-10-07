#!/usr/bin/env bash
# On the running emulator: installs the release APK that CI publishes (R8-optimized, the one
# salons install), launches it and checks it keeps running without a crash; then does the same
# with the debug APK and runs the instrumented tests (app launch + on-device database test).
set -euo pipefail

PKG=dtsalon.management
ACTIVITY=com.dtpos.salonmanager.MainActivity
RELEASE_APK=$(ls release-apk/*.apk 2>/dev/null | head -n 1 || true)
DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk

launch_and_check() {
  local apk=$1 label=$2
  adb uninstall "$PKG" >/dev/null 2>&1 || true
  adb install "$apk"
  echo "Installed $label: $(adb shell pm path "$PKG") version $(adb shell dumpsys package "$PKG" | grep -m1 versionName | tr -d ' ')"
  adb logcat -c || true  # Android 8 emulators cannot always clear the main buffer
  adb logcat -b crash -c || true
  adb shell am start -W -n "$PKG/$ACTIVITY"
  sleep 15
  if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
    echo "::error::$label: app process is not running after launch"
    adb logcat -d | tail -300
    exit 1
  fi
  if adb logcat -d -b crash | grep -q "$PKG"; then
    echo "::error::$label: crash detected after launch"
    adb logcat -d -b crash
    exit 1
  fi
  # Leave and come back (process kept), then a cold start again.
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  adb shell am start -W -n "$PKG/$ACTIVITY"
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/$ACTIVITY" | grep -E "TotalTime|WaitTime" || true
  sleep 8
  if ! adb shell pidof "$PKG" >/dev/null 2>&1 || adb logcat -d -b crash | grep -q "$PKG"; then
    echo "::error::$label: crash after resume / restart"
    adb logcat -d -b crash
    exit 1
  fi
  echo "$label launched, resumed and restarted without a crash (pid $(adb shell pidof "$PKG"))."
}

if [ -n "$RELEASE_APK" ]; then
  launch_and_check "$RELEASE_APK" "Release APK"
else
  echo "::warning::No release APK downloaded; only the debug APK is checked."
fi
launch_and_check "$DEBUG_APK" "Debug APK"

./gradlew connectedDebugAndroidTest --stacktrace
