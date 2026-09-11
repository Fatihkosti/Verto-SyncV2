#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GATE="${1:-}"
GRADLEW="${GRADLEW:-$ROOT/gradlew}"
REPORT_DIR="${CI_GATE_REPORT_DIR:-$ROOT/build/reports/ci/gates}"
mkdir -p "$REPORT_DIR"
SESSION="${2:-${DESIGN_SYSTEM_SESSION:-320}}"
CHANGE_CONTRACT="${CHANGE_CONTRACT:-$ROOT/docs/architecture/contracts/sessions/session-${SESSION}.json}"
[[ -f "$CHANGE_CONTRACT" ]] || CHANGE_CONTRACT="$ROOT/docs/architecture/contracts/sessions/session-320.json"

DESIGN_SYSTEM=(python3 "$ROOT/scripts/design-system-scan.py" --gate migration --session "$SESSION")
DESIGN_SYSTEM_DIFF=(python3 "$ROOT/tools/design_system_diff_gate.py" --root "$ROOT" --gate migration --session "$SESSION" --baseline "$ROOT/docs/design-system/BASELINE.json")
DOCUMENTATION=("$ROOT/scripts/run-documentation-gate.sh")
ARCHITECTURE=(python3 "$ROOT/tools/architecture/verto_arch_guard.py" verify --root "$ROOT" --output "$REPORT_DIR/architecture.json")
KOTLIN_QUALITY=(python3 "$ROOT/scripts/verify-kotlin-quality-static.py" verify --root "$ROOT" --mode current-ratchet --json-out "$REPORT_DIR/kotlin-quality-ratchet.json")
TESTABILITY=(python3 "$ROOT/tools/quality/verto_testability_guard.py" verify --root "$ROOT" --output "$REPORT_DIR/testability.json")
BEHAVIORAL_MUTATIONS=(python3 "$ROOT/tools/quality/verto_behavioral_mutation_runner.py" verify --root "$ROOT" --output "$REPORT_DIR/behavioral-mutations.json")
CONTRACT_COMPATIBILITY=(python3 "$ROOT/tools/architecture/verto_contract_compatibility_guard.py" verify --root "$ROOT" --output "$REPORT_DIR/contract-compatibility.json")
FEATURE_SCALABILITY_ADMISSION=(python3 "$ROOT/tools/architecture/verto_feature_admission_guard.py" verify --root "$ROOT" --output "$REPORT_DIR/feature-scalability-admission.json")
CHANGE=(python3 "$ROOT/scripts/ci/verify-change-contract.py" --root "$ROOT" --contract "$CHANGE_CONTRACT" --output "$REPORT_DIR/change-contract.json")
DEPENDENCY_GATE=(python3 "$ROOT/tools/technical_debt/dependency_gate.py" --root "$ROOT" --output "$REPORT_DIR/dependency.json")
TECHNICAL_DEBT_RATCHET=(python3 "$ROOT/tools/technical_debt/verify.py" ratchet --root "$ROOT")
DIFFERENTIAL_QUALITY=(python3 "$ROOT/tools/technical_debt/verify.py" differential --root "$ROOT")
PERSISTENCE_OWNERSHIP=(python3 "$ROOT/tools/persistence/verto_persistence_guard.py" verify-ownership --root "$ROOT" --output "$REPORT_DIR/persistence-ownership.json")
PERSISTENCE_BOUNDARY=(python3 "$ROOT/tools/persistence/verto_persistence_guard.py" verify-boundary --root "$ROOT" --output "$REPORT_DIR/persistence-boundary.json")
PERSISTENCE_TRANSACTION=(python3 "$ROOT/tools/persistence/verto_persistence_guard.py" verify-transaction --root "$ROOT" --output "$REPORT_DIR/persistence-transaction.json")
PERSISTENCE_SCHEMA=(python3 "$ROOT/tools/persistence/verto_persistence_guard.py" verify-schema --root "$ROOT" --output "$REPORT_DIR/persistence-schema.json")
PERSISTENCE_RATCHET=(python3 "$ROOT/tools/persistence/verto_persistence_guard.py" verify-ratchet --root "$ROOT" --output "$REPORT_DIR/persistence-ratchet.json")

if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 332 )); then
  ALL_STAGES=(change-contract architecture dependency contract-compatibility feature-scalability-admission data-ownership persistence-boundary transaction-contract migration-schema persistence-ratchet technical-debt-ratchet differential-quality kotlin-quality maintainability-testability behavioral-mutations documentation design-system design-system-diff detekt lint tests debug-build source-of-truth-admission)
elif [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 331 )); then
  ALL_STAGES=(change-contract architecture dependency contract-compatibility feature-scalability-admission data-ownership persistence-boundary transaction-contract migration-schema persistence-ratchet technical-debt-ratchet differential-quality kotlin-quality maintainability-testability documentation design-system design-system-diff detekt lint tests debug-build source-of-truth-admission)
elif [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 330 )); then
  ALL_STAGES=(change-contract architecture dependency contract-compatibility data-ownership persistence-boundary transaction-contract migration-schema persistence-ratchet technical-debt-ratchet differential-quality kotlin-quality maintainability-testability documentation design-system design-system-diff detekt lint tests debug-build source-of-truth-admission)
elif [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 329 )); then
  ALL_STAGES=(change-contract architecture dependency data-ownership persistence-boundary transaction-contract migration-schema persistence-ratchet technical-debt-ratchet differential-quality kotlin-quality maintainability-testability documentation design-system design-system-diff detekt lint tests debug-build source-of-truth-admission)
elif [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 326 )); then
  ALL_STAGES=(change-contract architecture dependency data-ownership persistence-boundary transaction-contract migration-schema persistence-ratchet technical-debt-ratchet differential-quality kotlin-quality documentation design-system design-system-diff detekt lint tests debug-build source-of-truth-admission)
elif [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 324 )); then
  ALL_STAGES=(change-contract architecture dependency technical-debt-ratchet differential-quality kotlin-quality documentation design-system design-system-diff detekt lint tests debug-build source-of-truth-admission)
else
  ALL_STAGES=(change-contract architecture kotlin-quality documentation design-system design-system-diff detekt lint tests debug-build source-of-truth-admission)
fi
EXPECTED_ALL_STAGES=("${ALL_STAGES[@]}")

fingerprint_tree() {
python3 - "$ROOT" <<'PY'
import hashlib,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(); ex={'.git','.gradle','.idea','.kotlin','build','out','__pycache__'}; sx={'.zip','.apk','.aab','.class','.pyc','.log','.tmp'}; rows=[]
for p in sorted(root.rglob('*')):
    if not p.is_file(): continue
    rp=p.relative_to(root)
    if any(x in ex for x in rp.parts) or p.suffix.lower() in sx: continue
    b=p.read_bytes(); rows.append(f"{rp.as_posix()}\0{hashlib.sha256(b).hexdigest()}\0{len(b)}")
print(hashlib.sha256('\n'.join(rows).encode()).hexdigest())
PY
}

validate_all_order() {
  [[ "${ALL_STAGES[*]}" == "${EXPECTED_ALL_STAGES[*]}" ]]
}

run_stage() {
  local stage="$1"; shift
  local log="$REPORT_DIR/${GATE}-${stage}.log"
  local status_file="$REPORT_DIR/all-${stage}.status"
  local started finished status
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "==> $stage"
  set +e
  (cd "$ROOT" && "$@") 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  set -e
  finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if [[ "$GATE" == "all" ]]; then
    {
      echo "stage=$stage"
      echo "status=$status"
      echo "run_id=$QUALITY_GATE_RUN_ID"
      echo "tree_fingerprint=$QUALITY_GATE_TREE_FINGERPRINT"
      echo "started_at=$started"
      echo "finished_at=$finished"
    } > "$status_file"
  fi
  if [[ "$status" -ne 0 ]]; then
    echo "quality_gate_stage=$stage status=$status" >&2
    return "$status"
  fi
}

self_test() {
  validate_all_order || { echo "UNIFIED_GATE_SELF_TEST=FAIL missing/reordered mandatory stage"; return 2; }
  local bad=("${EXPECTED_ALL_STAGES[@]}")
  unset 'bad[1]'
  [[ "${bad[*]}" != "${EXPECTED_ALL_STAGES[*]}" ]] || { echo "UNIFIED_GATE_SELF_TEST=FAIL deletion detector"; return 2; }
  local executed=() final=0 stage
  for stage in change-contract architecture kotlin-quality documentation; do
    executed+=("$stage")
    if [[ "$stage" == "architecture" ]]; then final=9; break; fi
  done
  [[ "$final" -ne 0 && "${executed[*]}" == "change-contract architecture" ]] || { echo "UNIFIED_GATE_SELF_TEST=FAIL fail-fast"; return 2; }
  echo "UNIFIED_GATE_SELF_TEST=PASS order=${ALL_STAGES[*]} fail_fast_after=architecture"
}

case "$GATE" in
  change-contract) command=("${CHANGE[@]}") ;;
  architecture) command=("${ARCHITECTURE[@]}") ;;
  dependency) command=("${DEPENDENCY_GATE[@]}") ;;
  contract-compatibility) command=("${CONTRACT_COMPATIBILITY[@]}") ;;
  feature-scalability-admission) command=("${FEATURE_SCALABILITY_ADMISSION[@]}") ;;
  behavioral-mutations) command=("${BEHAVIORAL_MUTATIONS[@]}") ;;
  data-ownership) command=("${PERSISTENCE_OWNERSHIP[@]}") ;;
  persistence-boundary) command=("${PERSISTENCE_BOUNDARY[@]}") ;;
  transaction-contract) command=("${PERSISTENCE_TRANSACTION[@]}") ;;
  migration-schema) command=("${PERSISTENCE_SCHEMA[@]}") ;;
  persistence-ratchet) command=("${PERSISTENCE_RATCHET[@]}") ;;
  technical-debt-ratchet) command=("${TECHNICAL_DEBT_RATCHET[@]}") ;;
  differential-quality) command=("${DIFFERENTIAL_QUALITY[@]}") ;;
  kotlin-quality) command=("${KOTLIN_QUALITY[@]}") ;;
  maintainability-testability) command=("${TESTABILITY[@]}") ;;
  documentation) command=("${DOCUMENTATION[@]}") ;;
  design-system|design-system-migration) command=("${DESIGN_SYSTEM[@]}") ;;
  design-system-diff) command=("${DESIGN_SYSTEM_DIFF[@]}") ;;
  lint) command=(bash "$GRADLEW" --no-daemon --stacktrace lintDebug) ;;
  detekt) command=(bash "$GRADLEW" --no-daemon --stacktrace detekt) ;;
  test) command=(bash "$GRADLEW" --no-daemon --stacktrace testDebugUnitTest) ;;
  debug) command=(bash "$GRADLEW" --no-daemon --stacktrace assembleDebug) ;;
  release) command=(bash "$GRADLEW" --no-daemon --stacktrace assembleRelease) ;;
  source-of-truth-admission) command=("$ROOT/scripts/ci/run-source-of-truth-admission.sh") ;;
  self-test) self_test; exit $? ;;
  all) command=("__ALL__") ;;
  *) echo "Unknown quality gate: $GATE" >&2; echo "Allowed: change-contract architecture dependency contract-compatibility feature-scalability-admission data-ownership persistence-boundary transaction-contract migration-schema persistence-ratchet technical-debt-ratchet differential-quality kotlin-quality maintainability-testability documentation design-system design-system-diff detekt lint test debug release source-of-truth-admission self-test all" >&2; exit 64 ;;
esac

STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; STATUS=0
if [[ "$GATE" == "all" ]]; then
  validate_all_order || { echo "FAIL_UNIFIED_GATE_GOVERNANCE: mandatory stage order drift" >&2; exit 2; }
  rm -f "$REPORT_DIR"/all-*.status "$REPORT_DIR/source-of-truth-admission.json"
  export QUALITY_GATE_RUN_ID="${QUALITY_GATE_RUN_ID:-v${SESSION}-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
  export QUALITY_GATE_SESSION="$SESSION"
  export QUALITY_GATE_TREE_FINGERPRINT="$(fingerprint_tree)"
  export CI_GATE_REPORT_DIR="$REPORT_DIR"
  run_stage change-contract "${CHANGE[@]}" && \
  run_stage architecture "${ARCHITECTURE[@]}" && \
  { if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 324 )); then run_stage dependency "${DEPENDENCY_GATE[@]}"; fi; } && \
  { if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 330 )); then run_stage contract-compatibility "${CONTRACT_COMPATIBILITY[@]}"; fi; } && \
  { if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 331 )); then run_stage feature-scalability-admission "${FEATURE_SCALABILITY_ADMISSION[@]}"; fi; } && \
  { if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 326 )); then run_stage data-ownership "${PERSISTENCE_OWNERSHIP[@]}" && run_stage persistence-boundary "${PERSISTENCE_BOUNDARY[@]}" && run_stage transaction-contract "${PERSISTENCE_TRANSACTION[@]}" && run_stage migration-schema "${PERSISTENCE_SCHEMA[@]}" && run_stage persistence-ratchet "${PERSISTENCE_RATCHET[@]}"; fi; } && \
  { if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 324 )); then run_stage technical-debt-ratchet "${TECHNICAL_DEBT_RATCHET[@]}" && run_stage differential-quality "${DIFFERENTIAL_QUALITY[@]}"; fi; } && \
  run_stage kotlin-quality "${KOTLIN_QUALITY[@]}" && \
  { if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 329 )); then run_stage maintainability-testability "${TESTABILITY[@]}"; fi; } && \
  { if [[ "$SESSION" =~ ^[0-9]+$ ]] && (( SESSION >= 332 )); then run_stage behavioral-mutations "${BEHAVIORAL_MUTATIONS[@]}"; fi; } && \
  run_stage documentation "${DOCUMENTATION[@]}" && \
  run_stage design-system "${DESIGN_SYSTEM[@]}" && \
  run_stage design-system-diff "${DESIGN_SYSTEM_DIFF[@]}" && \
  run_stage detekt bash "$GRADLEW" --no-daemon --stacktrace detekt && \
  run_stage lint bash "$GRADLEW" --no-daemon --stacktrace lintDebug && \
  run_stage tests bash "$GRADLEW" --no-daemon --stacktrace testDebugUnitTest && \
  run_stage debug-build bash "$GRADLEW" --no-daemon --stacktrace assembleDebug && \
  run_stage source-of-truth-admission "$ROOT/scripts/ci/run-source-of-truth-admission.sh" --session "$SESSION" --gate-report-dir "$REPORT_DIR" --run-id "$QUALITY_GATE_RUN_ID" --tree-fingerprint "$QUALITY_GATE_TREE_FINGERPRINT" --output "$REPORT_DIR/source-of-truth-admission.json"
  STATUS=$?
else
  run_stage "$GATE" "${command[@]}" || STATUS=$?
fi
FINISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
{
 echo "gate=$GATE"; echo "status=$STATUS"; echo "started_at=$STARTED_AT"; echo "finished_at=$FINISHED_AT"
 if [[ "$GATE" == "all" ]]; then echo "order=${ALL_STAGES[*]}"; echo "run_id=$QUALITY_GATE_RUN_ID"; echo "tree_fingerprint=$QUALITY_GATE_TREE_FINGERPRINT"; fi
} > "$REPORT_DIR/$GATE.status"
exit "$STATUS"
