#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
a="$(mktemp)"; b="$(mktemp)"; trap 'rm -f "$a" "$b"' EXIT
python3 "$ROOT/tools/verify_sync_orchestration_v311.py" >"$a"
python3 "$ROOT/tools/verify_sync_orchestration_v311.py" >"$b"
cmp -s "$a" "$b" || { echo 'FAIL_V311_VERIFIER_NONDETERMINISTIC' >&2; exit 2; }
cat "$a"
