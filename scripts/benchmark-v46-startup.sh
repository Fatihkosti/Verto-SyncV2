#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
PACKAGE="${PACKAGE:-com.verto.app}"
ACTIVITY="${ACTIVITY:-com.verto.app.MainActivity}"
RUNS="${RUNS:-15}"
OUT="${OUT:-v46-startup-times.csv}"

command -v "$ADB" >/dev/null || { echo "adb غير متوفر" >&2; exit 2; }
"$ADB" get-state >/dev/null

echo "run,total_time_ms,wait_time_ms" > "$OUT"
for run in $(seq 1 "$RUNS"); do
  "$ADB" shell am force-stop "$PACKAGE"
  sleep 1
  result="$($ADB shell am start -W -n "$PACKAGE/$ACTIVITY")"
  total="$(printf '%s\n' "$result" | awk -F': ' '/TotalTime/{gsub(/\r/,"",$2);print $2}')"
  wait="$(printf '%s\n' "$result" | awk -F': ' '/WaitTime/{gsub(/\r/,"",$2);print $2}')"
  [[ "$total" =~ ^[0-9]+$ ]] || { echo "تعذر قراءة TotalTime" >&2; exit 3; }
  echo "$run,$total,${wait:-0}" >> "$OUT"
done

python3 - "$OUT" <<'PY'
import csv,statistics,sys
rows=list(csv.DictReader(open(sys.argv[1],encoding='utf-8')))
values=sorted(int(r['total_time_ms']) for r in rows)
p95=values[min(len(values)-1, int((len(values)-1)*0.95+0.999))]
print(f"V46_STARTUP_RESULT runs={len(values)} medianMs={statistics.median(values):.1f} p95Ms={p95}")
PY
