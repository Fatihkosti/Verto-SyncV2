#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/release/app-release.apk}"
AAB="${2:-$ROOT/app/build/outputs/bundle/release/app-release.aab}"
MAPPING="$ROOT/app/build/outputs/mapping/release/mapping.txt"

[[ -s "$APK" ]] || { echo "FAIL: missing app-release.apk: $APK" >&2; exit 1; }
[[ -s "$AAB" ]] || { echo "FAIL: missing app-release.aab: $AAB" >&2; exit 1; }
[[ -s "$MAPPING" ]] || { echo "FAIL: missing R8 mapping.txt: $MAPPING" >&2; exit 1; }

APKSIGNER="${APKSIGNER:-}"
if [[ -z "$APKSIGNER" && -n "${ANDROID_HOME:-}" ]]; then
  APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -1 || true)"
fi
[[ -x "$APKSIGNER" ]] || { echo "FAIL: set APKSIGNER or ANDROID_HOME for apksigner verify" >&2; exit 1; }

"$APKSIGNER" verify --verbose --print-certs "$APK"
jarsigner -verify -strict -certs "$AAB" >/dev/null
unzip -tq "$APK" >/dev/null
unzip -tq "$AAB" >/dev/null

printf 'PASS: APK signed and readable: %s bytes\n' "$(stat -c%s "$APK")"
printf 'PASS: AAB signed and readable: %s bytes\n' "$(stat -c%s "$AAB")"
printf 'PASS: R8 mapping generated: %s bytes\n' "$(stat -c%s "$MAPPING")"
