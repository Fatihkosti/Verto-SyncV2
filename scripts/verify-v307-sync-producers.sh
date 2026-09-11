#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 tools/test_sync_producer_verification_v307.py >/tmp/verto-v307-fixtures.json
python3 tools/verify_sync_producers_v307.py \
  --allow-known-exceptions docs/sync/VERTO_SYNC_307_KNOWN_EXCEPTIONS.json \
  --json-out /tmp/verto-v307-run1.json >/dev/null
python3 tools/verify_sync_producers_v307.py \
  --allow-known-exceptions docs/sync/VERTO_SYNC_307_KNOWN_EXCEPTIONS.json \
  --json-out /tmp/verto-v307-run2.json >/dev/null
python3 - <<'PY'
import json, pathlib, sys
p1=json.loads(pathlib.Path('/tmp/verto-v307-run1.json').read_text())
p2=json.loads(pathlib.Path('/tmp/verto-v307-run2.json').read_text())
if p1['staticVerifierNormalizedHash'] != p2['staticVerifierNormalizedHash']:
    print('FAIL_NON_DETERMINISTIC_VERIFIER', file=sys.stderr)
    raise SystemExit(1)
if p1['staticGateStatus'] != 'PASS_WITH_DOCUMENTED_EXCEPTIONS':
    print('FAIL_STATIC_GATE', p1['staticGateStatus'], file=sys.stderr)
    raise SystemExit(1)
if len(p1.get('documentedExceptions', [])) != 9:
    print('FAIL_EXCEPTION_COUNT', len(p1.get('documentedExceptions', [])), file=sys.stderr)
    raise SystemExit(1)
if p1['staticFixtureStats']['total'] < 70 or p1['staticFixtureStats']['failed'] != 0:
    print('FAIL_FIXTURE_GATE', p1['staticFixtureStats'], file=sys.stderr)
    raise SystemExit(1)
pathlib.Path('VERTO_SYNC_PRODUCER_VERIFICATION_v307.json').write_text(
    json.dumps(p1, ensure_ascii=False, sort_keys=True, indent=2)+'\n', encoding='utf-8'
)
print(p1['staticVerifierNormalizedHash'])
PY
