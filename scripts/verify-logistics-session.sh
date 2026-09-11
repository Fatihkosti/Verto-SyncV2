#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  verify-logistics-session.sh --previous <dir> --current <dir> --allowlist <file> \
    [--expected-room <n>] [--expected-modules <n>] [--allow-listed-deletions] [--expect-legacy-removed]

Compares a previous source tree with the current tree and fails closed on:
- changed/added files outside the session allowlist
- any unlisted deleted file (or any deletion unless --allow-listed-deletions)
- settings/root build-logic/dependency graph changes
- module-count drift
- Room-version or migration-chain drift/gaps
- destructive Room fallback
- legacy shipment table contract drift (retained by default; removed with --expect-legacy-removed)
- forbidden Android/Room/Supabase/data implementation imports in shipment domain/application
USAGE
}

PREVIOUS=""
CURRENT=""
ALLOWLIST=""
EXPECTED_ROOM=""
EXPECTED_MODULES="31"
ALLOW_LISTED_DELETIONS="0"
EXPECT_LEGACY_REMOVED="0"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --previous) PREVIOUS="$2"; shift 2 ;;
    --current) CURRENT="$2"; shift 2 ;;
    --allowlist) ALLOWLIST="$2"; shift 2 ;;
    --expected-room) EXPECTED_ROOM="$2"; shift 2 ;;
    --expected-modules) EXPECTED_MODULES="$2"; shift 2 ;;
    --allow-listed-deletions) ALLOW_LISTED_DELETIONS="1"; shift ;;
    --expect-legacy-removed) EXPECT_LEGACY_REMOVED="1"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "ERROR unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$PREVIOUS" && -d "$PREVIOUS" ]] || { echo "ERROR --previous directory is required" >&2; exit 2; }
[[ -n "$CURRENT" && -d "$CURRENT" ]] || { echo "ERROR --current directory is required" >&2; exit 2; }
[[ -n "$ALLOWLIST" ]] || { echo "ERROR --allowlist is required" >&2; exit 2; }

PREVIOUS="$(cd "$PREVIOUS" && pwd)"
CURRENT="$(cd "$CURRENT" && pwd)"
if [[ "$ALLOWLIST" = /* ]]; then
  ALLOWLIST_PATH="$ALLOWLIST"
else
  ALLOWLIST_PATH="$CURRENT/$ALLOWLIST"
fi
[[ -f "$ALLOWLIST_PATH" ]] || { echo "ERROR allowlist not found: $ALLOWLIST_PATH" >&2; exit 2; }

python3 - "$PREVIOUS" "$CURRENT" "$ALLOWLIST_PATH" "$EXPECTED_ROOM" "$EXPECTED_MODULES" "$ALLOW_LISTED_DELETIONS" "$EXPECT_LEGACY_REMOVED" <<'PY'
from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

previous = Path(sys.argv[1]).resolve()
current = Path(sys.argv[2]).resolve()
allowlist_path = Path(sys.argv[3]).resolve()
expected_room_arg = sys.argv[4].strip()
expected_modules = int(sys.argv[5])
allow_listed_deletions = sys.argv[6] == "1"
expect_legacy_removed = sys.argv[7] == "1"

failures: list[str] = []
notes: list[str] = []


def rel_files(root: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for path in sorted((p for p in root.rglob('*') if p.is_file()), key=lambda p: p.as_posix()):
        rel = path.relative_to(root).as_posix()
        h = hashlib.sha256()
        with path.open('rb') as fh:
            for chunk in iter(lambda: fh.read(1024 * 1024), b''):
                h.update(chunk)
        out[rel] = h.hexdigest()
    return out


def read(path: str) -> str:
    p = current / path
    if not p.is_file():
        failures.append(f"missing required file: {path}")
        return ''
    return p.read_text(encoding='utf-8', errors='replace')

allow = {
    line.strip().lstrip('./')
    for line in allowlist_path.read_text(encoding='utf-8').splitlines()
    if line.strip() and not line.lstrip().startswith('#')
}
prev_hash = rel_files(previous)
curr_hash = rel_files(current)

deleted = sorted(set(prev_hash) - set(curr_hash))
added = sorted(set(curr_hash) - set(prev_hash))
modified = sorted(p for p in set(prev_hash) & set(curr_hash) if prev_hash[p] != curr_hash[p])
changed = sorted(set(added) | set(modified))
all_delta = sorted(set(changed) | set(deleted))
outside = sorted(p for p in all_delta if p not in allow)

if deleted and not allow_listed_deletions:
    failures.append("deleted files: " + ", ".join(deleted))
if outside:
    failures.append("changed/added outside allowlist: " + ", ".join(outside))

# Explicit architecture/dependency graph protection, independent from allowlist contents.
protected_dependency_paths = {
    'settings.gradle.kts',
    'build.gradle.kts',
    'gradle/libs.versions.toml',
}
protected_changed = [
    p for p in changed
    if p in protected_dependency_paths or p.startswith('build-logic/')
]
if protected_changed:
    failures.append("protected dependency graph changed: " + ", ".join(protected_changed))

settings = read('settings.gradle.kts')
modules = re.findall(r'^\s*include\(\s*"(:[^"]+)"\s*\)\s*$', settings, flags=re.MULTILINE)
if len(modules) != expected_modules:
    failures.append(f"module count {len(modules)} != {expected_modules}")
if len(set(modules)) != len(modules):
    failures.append("duplicate module include detected")

catalog = read('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
version_match = re.search(r'const\s+val\s+ROOM_SCHEMA_VERSION\s*:\s*Int\s*=\s*(\d+)', catalog)
if not version_match:
    failures.append("ROOM_SCHEMA_VERSION not found")
    room_version = None
else:
    room_version = int(version_match.group(1))
    if expected_room_arg and room_version != int(expected_room_arg):
        failures.append(f"Room schema {room_version} != expected {expected_room_arg}")

pairs = [(int(a), int(b)) for a, b in re.findall(r'MIGRATION_(\d+)_(\d+)', catalog)]
if room_version is not None:
    expected_pairs = [(i, i + 1) for i in range(1, room_version)]
    if pairs != expected_pairs:
        failures.append(
            "Room migration catalog is not an exact consecutive 1..ROOM_SCHEMA_VERSION chain"
        )
    schema = current / f'app/schemas/com.verto.app.data.local.AppDatabase/{room_version}.json'
    if not schema.is_file():
        failures.append(f"latest Room schema JSON missing: {schema.relative_to(current).as_posix()}")

app_db = read('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt')
if 'fallbackToDestructiveMigration' in app_db:
    failures.append("fallbackToDestructiveMigration is present")
if 'version = ROOM_SCHEMA_VERSION' not in app_db:
    failures.append("AppDatabase does not use ROOM_SCHEMA_VERSION")

entities = read('data/database/src/main/kotlin/com/verto/app/data/local/entity/ShipmentEntities.kt')
legacy_tables = ('shipments', 'shipment_stops', 'shipment_documents', 'shipment_costs', 'shipment_receipts')
for table in legacy_tables:
    declared = bool(re.search(r'tableName\s*=\s*"' + re.escape(table) + r'"', entities))
    if expect_legacy_removed and declared:
        failures.append(f"legacy shipment table declaration still present: {table}")
    if not expect_legacy_removed and not declared:
        failures.append(f"legacy shipment table declaration missing: {table}")

if expect_legacy_removed:
    forbidden_runtime = re.compile(
        r"\b(?:ShipmentDao|ShipmentRepository|ShipmentEntity|ShipmentStopEntity|ShipmentDocumentEntity|"
        r"ShipmentCostEntity|ShipmentReceiptEntity|ShipmentStatus|ShipmentStopType|ShipmentSyncRuntimePort|"
        r"ShipmentPresentationReadPort|AndroidShipmentDocumentOpenAdapter)\b"
    )
    runtime_roots = [current / "app", current / "core", current / "data", current / "feature"]
    for runtime_root in runtime_roots:
        if not runtime_root.is_dir():
            continue
        for path in runtime_root.rglob("*.kt"):
            rel = path.relative_to(current).as_posix()
            if "/AppDatabaseMigrations" in rel:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            if forbidden_runtime.search(text):
                failures.append(f"legacy shipment runtime reference remains: {rel}")
            if "shipment_detail/" in text:
                failures.append(f"legacy shipment navigation route remains: {rel}")

for area in ('domain', 'application'):
    root = current / 'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment' / area
    if not root.is_dir():
        failures.append(f"shipment {area} directory missing")
        continue
    for path in sorted(root.rglob('*.kt')):
        text = path.read_text(encoding='utf-8', errors='replace')
        rel = path.relative_to(current).as_posix()
        forbidden_imports = (
            r'^\s*import\s+android\.',
            r'^\s*import\s+androidx\.room\.',
            r'^\s*import\s+io\.github\.jan\.supabase\.',
            r'^\s*import\s+com\.verto\.app\.data\.',
            r'^\s*import\s+.*\.(?:[A-Za-z0-9_]*Dao|[A-Za-z0-9_]*Entity|[A-Za-z0-9_]*Dto|[A-Za-z0-9_]*Repository)\b',
        )
        for pattern in forbidden_imports:
            if re.search(pattern, text, flags=re.MULTILINE):
                failures.append(f"forbidden shipment {area} import in {rel}: {pattern}")

print("LOGISTICS_SESSION_VERIFICATION")
print(f"previous={previous}")
print(f"current={current}")
print(f"allowlist={allowlist_path}")
print(f"added={len(added)}")
print(f"modified={len(modified)}")
print(f"deleted={len(deleted)}")
print(f"outside_allowlist={len(outside)}")
print(f"modules={len(modules)}")
print(f"room={room_version if room_version is not None else 'UNKNOWN'}")
print("changed_files=")
for path in changed:
    print(f"  {path}")
if failures:
    print("RESULT=FAIL")
    for item in sorted(set(failures)):
        print(f"FAIL: {item}")
    sys.exit(1)
print("RESULT=PASS")
PY
