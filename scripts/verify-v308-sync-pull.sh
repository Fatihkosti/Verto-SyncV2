#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 tools/test_sync_pull_verification_v308.py >/tmp/v308-fixtures.json
h1="$(python3 tools/verify_sync_pull_v308.py)" || { rc=$?; exit "$rc"; }
h2="$(python3 tools/verify_sync_pull_v308.py)" || { rc=$?; exit "$rc"; }
if [[ "$h1" != "$h2" ]]; then
  echo "determinism mismatch: $h1 != $h2" >&2
  exit 1
fi
printf '%s\n' "$h2"
