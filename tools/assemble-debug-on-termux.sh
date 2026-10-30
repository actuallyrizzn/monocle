#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME
{
  echo "sdk.dir=$ANDROID_HOME"
  if [[ -f "$HOME/.ssh/monocle-moya.pass" ]]; then
    set -a
    # shellcheck disable=SC1090
    . "$HOME/.ssh/monocle-moya.pass"
    set +a
    printf 'presence.url=%s\n' "${MONOCLE_PRESENCE_URL:-http://127.0.0.1:8890}"
    printf 'presence.apiKey=%s\n' "${MONOCLE_PRESENCE_API_KEY:-}"
    echo "presence: key loaded from monocle-moya.pass" >&2
  else
    echo "presence: no monocle-moya.pass; heartbeat will fail-closed" >&2
  fi
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
  echo "Do not copy to Downloads. Serve with:"
  echo "  python3 \$HOME/.otto-portable/tools/termux-single-file-serve/serve.py \"$NAMED\" --bind 0.0.0.0 --port 8775"
  ls -lh "$APK" "$NAMED"
fi
