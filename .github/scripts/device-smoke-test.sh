#!/usr/bin/env bash
# Installs the debug APK on the running emulator, launches it, checks it did not crash,
# then runs the instrumented tests (app launch + on-device SQLite database test).
set -euo pipefail

APK=app/build/outputs/apk/debug/app-debug.apk
PKG=dtsalon.management
ACTIVITY=com.dtpos.salonmanager.MainActivity

adb install -r "$APK"
echo "Installed: $(adb shell pm path "$PKG")"

adb logcat -c || true  # Android 8 emulators cannot always clear the main buffer
adb shell am start -W -n "$PKG/$ACTIVITY"
sleep 15

if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
  echo "::error::App process is not running after launch"
  adb logcat -d | tail -300
  exit 1
fi
if adb logcat -d -b crash | grep -q "$PKG"; then
  echo "::error::Crash detected after launch"
  adb logcat -d -b crash
  exit 1
fi
echo "App launched and is running (pid $(adb shell pidof "$PKG"))."

./gradlew connectedDebugAndroidTest --stacktrace
