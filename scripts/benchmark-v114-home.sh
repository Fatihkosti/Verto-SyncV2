#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${1:-com.verto.app}"
ACTIVITY="${2:-com.verto.app/.MainActivity}"
RUNS="${3:-5}"
SEARCH_QUERY="${4:-}" # Optional; requires a logged-in Home with local data.

command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
adb get-state 2>/dev/null | grep -q device || { echo "No adb device" >&2; exit 2; }
[[ "$RUNS" =~ ^[1-9][0-9]*$ ]] || { echo "RUNS must be positive" >&2; exit 2; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
RESULTS="$WORK/home-times.txt"

for _ in $(seq 1 "$RUNS"); do
  adb shell am force-stop "$PACKAGE" >/dev/null
  output="$(adb shell am start -W -n "$ACTIVITY" 2>&1)"
  total="$(printf '%s\n' "$output" | awk -F': ' '/^TotalTime:/{gsub(/\r/,"",$2); print $2; exit}')"
  [[ "$total" =~ ^[0-9]+$ ]] || { printf '%s\n' "$output" >&2; exit 3; }
  printf '%s\n' "$total" >> "$RESULTS"
done

python3 - "$RESULTS" <<'PY'
from pathlib import Path
import statistics, sys
values=sorted(int(x) for x in Path(sys.argv[1]).read_text().split())
idx=max(0, min(len(values)-1, int((len(values)-1)*0.95 + 0.999999)))
print(f"HOME_COLD_START runs={len(values)} median_ms={int(statistics.median(values))} p95_ms={values[idx]} raw={values}")
PY

# Optional UIAutomator-based local search timing. It measures input-to-idle on the actual device.
if [[ -n "$SEARCH_QUERY" ]]; then
  XML="$WORK/window.xml"
  dump_ui() {
    adb shell uiautomator dump /sdcard/verto-v114-window.xml >/dev/null 2>&1
    adb pull /sdcard/verto-v114-window.xml "$XML" >/dev/null 2>&1
  }
  center_for_description() {
    local description="$1"
    python3 - "$XML" "$description" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); wanted=sys.argv[2]
for node in root.iter('node'):
    if node.attrib.get('content-desc') == wanted:
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2, (y1+y2)//2); raise SystemExit
raise SystemExit(1)
PY
  }

  dump_ui
  read -r sx sy < <(center_for_description "فتح البحث الموحد")
  adb shell input tap "$sx" "$sy" >/dev/null
  sleep 0.3
  dump_ui
  read -r qx qy < <(center_for_description "حقل البحث الموحد")
  adb shell input tap "$qx" "$qy" >/dev/null

  start_ns="$(date +%s%N)"
  adb shell input text "$(printf '%s' "$SEARCH_QUERY" | sed 's/ /%s/g')" >/dev/null
  deadline=$((SECONDS + 10))
  while (( SECONDS < deadline )); do
    dump_ui
    if ! grep -q 'android.widget.ProgressBar' "$XML"; then
      if grep -Eq 'لا توجد نتائج|تعذّر إكمال البحث|اضغط لعرض التفاصيل|بطاقة ممددة' "$XML"; then
        end_ns="$(date +%s%N)"
        echo "HOME_SEARCH query=$SEARCH_QUERY elapsed_ms=$(( (end_ns-start_ns)/1000000 ))"
        exit 0
      fi
    fi
    sleep 0.1
  done
  echo "HOME_SEARCH timed_out_after_ms=10000" >&2
  exit 4
fi
