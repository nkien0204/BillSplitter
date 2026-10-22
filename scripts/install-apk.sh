#!/usr/bin/env bash
# Install an APK on the USB-connected Android phone. Usage: npm run install:apk [-- path/to/file.apk]
set -euo pipefail
cd "$(dirname "$0")/.."
APK="${1:-build/chiabill.apk}"

if ! command -v adb >/dev/null; then
  echo "adb not found. Install Android platform-tools (brew install --cask android-platform-tools)." >&2
  exit 1
fi
if [ ! -f "$APK" ]; then
  echo "APK not found: $APK. Build one first (npm run build:apk:local) or pass a path." >&2
  exit 1
fi
if ! adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
  echo "No authorized device. Run 'adb devices': it must say 'device', not 'unauthorized'/empty." >&2
  adb devices >&2
  exit 1
fi

adb install -r "$APK"
echo "Installed $APK"
