#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
a="$(mktemp)"; b="$(mktemp)"; trap 'rm -f "$a" "$b"' EXIT
python3 "$ROOT/tools/verify_sync_realtime_v312.py" > "$a"
python3 "$ROOT/tools/verify_sync_realtime_v312.py" > "$b"
cmp -s "$a" "$b" || { echo FAIL_V312_VERIFIER_NONDETERMINISTIC >&2; exit 2; }
cat "$a"
