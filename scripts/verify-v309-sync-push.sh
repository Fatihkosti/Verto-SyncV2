#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 tools/test_sync_push_verification_v309.py >/tmp/verto-v309-model.json
python3 tools/test_sync_pull_verification_v308.py >/tmp/verto-v308-regression.json

set +e
h1="$(python3 tools/verify_sync_push_v309.py)"; rc1=$?
set -e
if [[ $rc1 -eq 2 || $rc1 -eq 3 ]]; then exit "$rc1"; fi
if [[ $rc1 -ne 0 ]]; then exit 1; fi

set +e
h2="$(python3 tools/verify_sync_push_v309.py)"; rc2=$?
set -e
if [[ $rc2 -eq 2 || $rc2 -eq 3 ]]; then exit "$rc2"; fi
if [[ $rc2 -ne 0 ]]; then exit 1; fi

if [[ "$h1" != "$h2" ]]; then
  echo "FAIL_REQUEST_HASH_NONDETERMINISTIC: verifier hashes differ" >&2
  exit 1
fi
printf 'PASS_STATIC %s\n' "$h1"
