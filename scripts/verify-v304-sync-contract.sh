#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
export VERTO_V303_SOURCE_ZIP="${VERTO_V303_SOURCE_ZIP:-}"
status=0
run_child() {
  local kind="$1"; shift
  local out rc
  out="$(mktemp)"
  "$@" >"$out" 2>&1; rc=$?
  cat "$out"
  if [[ $rc -ne 0 ]]; then
    if [[ "$kind" == "gradle" ]] && grep -Eq 'UnknownHostException|services\.gradle\.org|Could not install Gradle|Could not resolve|No cached version' "$out"; then
      rc=2
    fi
    if [[ $rc -eq 2 ]]; then status=2; elif [[ $status -eq 0 ]]; then status=1; fi
  fi
  rm -f "$out"
}
run_child static python3 tools/verify_sync_contract_v304.py --identity-only
run_child static python3 tools/verify_sync_contract_v304.py --contract
run_child static python3 tools/verify_sync_contract_v304.py --coverage
run_child static python3 tools/test_sync_contract_verification_v304.py
run_child gradle ./gradlew :data:network:testDebugUnitTest --tests 'com.verto.app.data.sync.UnifiedSyncContractV304Test' --offline --no-daemon
run_child gradle ./gradlew :data:network:testDebugUnitTest --tests 'com.verto.app.data.sync.UnifiedSyncAggregateRegistryV304Test' --offline --no-daemon
run_child static python3 tools/verify_sync_contract_v304.py --integrity-after
exit "$status"
