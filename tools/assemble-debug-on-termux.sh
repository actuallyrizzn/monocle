#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME
{
  echo "sdk.dir=$ANDROID_HOME"
} >"$ROOT/local.properties"
ARCH="$(uname -m)"
GRADLE_ARGS=(--no-daemon)
if [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" || "$ARCH" == armv7* ]]; then
  GRADLE_ARGS+=(-Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/34.0.0/aapt2")
fi
./gradlew "${GRADLE_ARGS[@]}" :app:testDebugUnitTest :app:assembleDebug "$@"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
NAMED="$ROOT/app/build/outputs/apk/debug/MONOCLE-BRIDGE.apk"
if [[ -f "$APK" ]]; then
  cp -f "$APK" "$NAMED"
  echo "OK: $NAMED"
  echo "Do not copy to Downloads. Serve the APK over HTTP from this directory."
  ls -lh "$APK" "$NAMED"
fi
