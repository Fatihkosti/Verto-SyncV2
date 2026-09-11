#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPORT_DIR="${CI_GATE_REPORT_DIR:-$ROOT/build/reports/ci/gates}"
RUN_ID="${QUALITY_GATE_RUN_ID:-}"
EXPECTED_FP="${QUALITY_GATE_TREE_FINGERPRINT:-}"
OUTPUT="${SOURCE_ADMISSION_REPORT:-$REPORT_DIR/source-of-truth-admission.json}"
SESSION="${QUALITY_GATE_SESSION:-320}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gate-report-dir) REPORT_DIR="$2"; shift 2 ;;
    --run-id) RUN_ID="$2"; shift 2 ;;
    --tree-fingerprint) EXPECTED_FP="$2"; shift 2 ;;
    --session) SESSION="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done

python3 - "$ROOT" "$REPORT_DIR" "$RUN_ID" "$EXPECTED_FP" "$OUTPUT" "$SESSION" <<'PY'
from __future__ import annotations
import hashlib, json, sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(); report_dir=Path(sys.argv[2]).resolve(); run_id=sys.argv[3]; expected=sys.argv[4]; output=Path(sys.argv[5]); session=sys.argv[6]
required=['change-contract','architecture']
if session.isdigit() and int(session) >= 324: required += ['dependency','technical-debt-ratchet','differential-quality']
if session.isdigit() and int(session) >= 330: required += ['contract-compatibility']
if session.isdigit() and int(session) >= 331: required += ['feature-scalability-admission']
if session.isdigit() and int(session) >= 326: required += ['data-ownership','persistence-boundary','transaction-contract','migration-schema','persistence-ratchet']
if session.isdigit() and int(session) >= 329: required += ['maintainability-testability']
if session.isdigit() and int(session) >= 332: required += ['behavioral-mutations']
required += ['kotlin-quality','documentation','design-system','design-system-diff','detekt','lint','tests','debug-build']
EXCLUDED_DIRS={'.git','.gradle','.idea','.kotlin','build','out','__pycache__'}
EXCLUDED_SUFFIXES={'.zip','.apk','.aab','.class','.pyc','.log','.tmp'}
def tree_fp():
    rows=[]
    for p in sorted(root.rglob('*')):
        if not p.is_file(): continue
        rp=p.relative_to(root)
        if any(x in EXCLUDED_DIRS for x in rp.parts) or p.suffix.lower() in EXCLUDED_SUFFIXES: continue
        b=p.read_bytes(); rows.append(f"{rp.as_posix()}\0{hashlib.sha256(b).hexdigest()}\0{len(b)}")
    return hashlib.sha256('\n'.join(rows).encode()).hexdigest()
def parse_status(path:Path):
    d={}
    if not path.exists(): return d
    for line in path.read_text(errors='replace').splitlines():
        if '=' in line:
            k,v=line.split('=',1); d[k]=v
    return d
current=tree_fp(); stages={}; failures=[]
if not run_id: failures.append({'code':'FAIL_SOURCE_OF_TRUTH_ADMISSION','detail':'missing run id'})
if not expected: failures.append({'code':'FAIL_STALE_ADMISSION_REPORT','detail':'missing expected tree fingerprint'})
if expected and current!=expected: failures.append({'code':'FAIL_STALE_ADMISSION_REPORT','expected':expected,'actual':current})
for stage in required:
    d=parse_status(report_dir/f'all-{stage}.status'); stages[stage]=d
    if not d:
        failures.append({'code':'FAIL_SOURCE_OF_TRUTH_ADMISSION','stage':stage,'status':'NOT RUN'}); continue
    if d.get('run_id')!=run_id: failures.append({'code':'FAIL_STALE_ADMISSION_REPORT','stage':stage,'expected_run_id':run_id,'actual_run_id':d.get('run_id')})
    if d.get('tree_fingerprint')!=expected: failures.append({'code':'FAIL_STALE_ADMISSION_REPORT','stage':stage,'expected_tree_fingerprint':expected,'actual_tree_fingerprint':d.get('tree_fingerprint')})
    if d.get('status')!='0': failures.append({'code':'FAIL_SOURCE_OF_TRUTH_ADMISSION','stage':stage,'status':d.get('status','NOT RUN')})
payload={
 'format':'verto-source-of-truth-admission-v1','status':'PASS' if not failures else 'FAIL','run_id':run_id,
 'tree_fingerprint':current,'required_stages':required,'stages':stages,'failures':failures,
}
output.parent.mkdir(parents=True,exist_ok=True); output.write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n')
print(json.dumps({'SOURCE_OF_TRUTH_ADMISSION':payload['status'],'tree_fingerprint':current,'failures':failures},sort_keys=True))
raise SystemExit(0 if not failures else 2)
PY
