#!/usr/bin/env bash
set -euo pipefail
ADB="${ADB:-adb}"
PACKAGE="${PACKAGE:-com.verto.app}"
EVENTS="${EVENTS:-5000}"
THROTTLE_MS="${THROTTLE_MS:-250}"
OUT="${OUT:-v46-stability-log.txt}"

command -v "$ADB" >/dev/null || { echo "adb غير متوفر" >&2; exit 2; }
"$ADB" get-state >/dev/null
"$ADB" logcat -c
"$ADB" shell monkey -p "$PACKAGE" --pct-syskeys 0 --throttle "$THROTTLE_MS" "$EVENTS"
"$ADB" logcat -d > "$OUT"
if grep -E "ANR in $PACKAGE|FATAL EXCEPTION.*$PACKAGE|Process: $PACKAGE.*FATAL" "$OUT"; then
  echo "V46_STABILITY_RESULT failed" >&2
  exit 1
fi
echo "V46_STABILITY_RESULT passed events=$EVENTS"
