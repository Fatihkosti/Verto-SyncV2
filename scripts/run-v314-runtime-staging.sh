#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/VERTO_SYNC_RUNTIME_EVIDENCE_v314.json"
# Final runtime requires an environment-specific runner capable of Android/Room + PostgreSQL + two clients + RLS.
# Absence is NOT a PASS and does not mutate the static evidence artifact.
required=(V314_STAGING_RUNTIME_RUNNER V314_POSTGRES_URL V314_TEST_ORG_A V314_TEST_ORG_B)
missing=()
for key in "${required[@]}"; do [[ -n "${!key:-}" ]] || missing+=("$key"); done
if ((${#missing[@]})); then
  printf 'NOT_RUN_ENVIRONMENT_UNAVAILABLE missing=%s\n' "$(IFS=,; echo "${missing[*]}")" >&2
  exit 3
fi
runner="$V314_STAGING_RUNTIME_RUNNER"
[[ -x "$runner" ]] || { echo 'BLOCKED_RUNTIME_FAILURE: runner is not executable' >&2; exit 4; }
"$runner" --project-root "$ROOT" --evidence-out "$OUT"
python3 - "$OUT" <<'PY'
import json,sys
p=sys.argv[1]; d=json.load(open(p))
pairs=[('room8081RuntimeExecuted','room8081RuntimePassed'),('postgres313Executed','postgres313Passed'),('bootstrapRuntimeExecuted','bootstrapRuntimePassed'),('cursorExpiryRuntimeExecuted','cursorExpiryRuntimePassed'),('pendingMutationRecoveryExecuted','pendingMutationRecoveryPassed'),('processDeathRuntimeExecuted','processDeathRuntimePassed'),('twoDeviceRuntimeExecuted','twoDeviceRuntimePassed'),('rlsAdversarialExecuted','rlsAdversarialPassed'),('realtimeParityExecuted','realtimeParityPassed')]
for e,passed in pairs:
    if not d.get(e,False) and d.get(passed,False): raise SystemExit('FAIL_RUNTIME_EVIDENCE_FABRICATION')
if not all(d.get(p,False) for _,p in pairs): raise SystemExit('BLOCKED_RUNTIME_FAILURE')
print('RUNTIME_EVIDENCE_SCHEMA_PASS')
PY
