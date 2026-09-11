#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 tools/test_sync_stronger_verification_v310.py >/tmp/verto-v310-model.json
python3 tools/test_sync_push_verification_v309.py >/tmp/verto-v309-regression-v310.json
python3 tools/test_sync_pull_verification_v308.py >/tmp/verto-v308-regression-v310.json
h1="$(python3 tools/verify_sync_stronger_v310.py >/dev/null && python3 -c 'import json;print(json.load(open("VERTO_SYNC_STRONGER_VERIFICATION_v310.json"))["verifierNormalizedHash"])')"
h2="$(python3 tools/verify_sync_stronger_v310.py >/dev/null && python3 -c 'import json;print(json.load(open("VERTO_SYNC_STRONGER_VERIFICATION_v310.json"))["verifierNormalizedHash"])')"
if [[ "$h1" != "$h2" ]]; then echo "FAIL_REQUEST_HASH_NONDETERMINISTIC" >&2; exit 1; fi
printf 'PASS_STATIC %s\n' "$h1"
