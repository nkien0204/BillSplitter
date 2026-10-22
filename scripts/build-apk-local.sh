#!/usr/bin/env bash
# Build an installable release APK on this machine and copy it to build/chiabill.apk.
# Needs Android Studio / the Android SDK (ANDROID_HOME) and a JDK 17.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "ANDROID_HOME is not set. Install Android Studio, or use the cloud build: npm run build:apk" >&2
  exit 1
fi

npx expo prebuild --platform android
(cd android && ./gradlew assembleRelease)

mkdir -p build
cp android/app/build/outputs/apk/release/app-release.apk build/chiabill.apk
echo "APK ready: build/chiabill.apk  (install with: npm run install:apk)"
