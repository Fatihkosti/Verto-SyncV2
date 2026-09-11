#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/tools/test_sync_recovery_verification_v313.py" >/tmp/v313-fixtures.json
a="$(python3 "$ROOT/tools/verify_sync_recovery_v313.py")"
b="$(python3 "$ROOT/tools/verify_sync_recovery_v313.py")"
[[ "$a" == "$b" ]] || { echo FAIL_V313_VERIFIER_NONDETERMINISTIC >&2; exit 2; }
printf '%s\n' "$b"
