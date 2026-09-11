#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/tools/test_sync_cutover_verification_v314.py" >/tmp/verto-v314-model.json
a="$(python3 "$ROOT/tools/verify_sync_cutover_v314.py")"
b="$(python3 "$ROOT/tools/verify_sync_cutover_v314.py")"
[[ "$a" == "$b" ]] || { echo FAIL_V314_VERIFIER_NONDETERMINISTIC >&2; exit 2; }
printf '%s\n' "$b"
