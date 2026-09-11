#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
INPUT_ZIP="${2:?usage: verify-v306-sync-room.sh [root] <Verto-v305-server-static.zip>}"
python3 "$ROOT/tools/verify_sync_room_v306.py" --root "$ROOT" --input-zip "$INPUT_ZIP"
python3 "$ROOT/tools/test_sync_room_verification_v306.py" --root "$ROOT" --input-zip "$INPUT_ZIP"
