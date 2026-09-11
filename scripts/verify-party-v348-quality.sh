#!/usr/bin/env bash
set -euo pipefail

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
quality_json="$tmpdir/quality.json"
python3 scripts/verify-kotlin-quality-static.py scan --json-out "$quality_json" >/dev/null
python3 - "$quality_json" <<'PY'
import json, sys
m=json.load(open(sys.argv[1]))["metrics"]
baseline={
  "architecture_violation_count":0,
  "broad_catches":38,
  "dependency_cycles":0,
  "excessive_parameter_lists":668,
  "exposed_mutable_state":2,
  "global_scope":0,
  "large_files_over_500":25,
  "lateinit_var":4,
  "long_functions":480,
  "manual_coroutine_scopes":1,
  "not_null_assertions":7,
}
regressions={k:(m[k],v) for k,v in baseline.items() if m[k] > v}
if regressions:
    raise SystemExit(f"QUALITY_RATCHET_REGRESSION={regressions}")
print("V348_QUALITY_RATCHET=PASS")
PY
