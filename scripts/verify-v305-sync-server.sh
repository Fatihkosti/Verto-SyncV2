#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 tools/test_sync_server_verification_v305.py > /tmp/verto-v305-model-fixtures.json
python3 tools/verify_sync_server_v305.py > /tmp/verto-v305-static-verification.json
if command -v psql >/dev/null 2>&1 && command -v postgres >/dev/null 2>&1 && command -v initdb >/dev/null 2>&1; then
  echo "PostgreSQL runtime detected. Execute the migration and adversarial database suite against an isolated Supabase-compatible baseline before claiming Full PASS." >&2
  exit 3
fi
echo "SERVER_STATIC_COMPLETE / DATABASE_EXECUTION_BLOCKED"
