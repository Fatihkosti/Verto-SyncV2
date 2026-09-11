#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="${DOCUMENTATION_GATE_REPORT_DIR:-$ROOT/build/reports/documentation}"
mkdir -p "$REPORT_DIR"

python3 "$ROOT/scripts/documentation/documentation_gate.py" \
  --json-out "$REPORT_DIR/documentation-gate.json"
