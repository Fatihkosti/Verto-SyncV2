#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="${1:-$(dirname "$ROOT")/Verto-source.zip}"

if [[ "$OUTPUT" != /* ]]; then
  OUTPUT="$(pwd)/$OUTPUT"
fi
mkdir -p "$(dirname "$OUTPUT")"
OUTPUT_DIR="$(cd "$(dirname "$OUTPUT")" && pwd)"
OUTPUT="$OUTPUT_DIR/$(basename "$OUTPUT")"

required=(
  "settings.gradle.kts"
  "build.gradle.kts"
  "gradlew"
  "gradle/wrapper/gradle-wrapper.jar"
  "gradle/wrapper/gradle-wrapper.properties"
  "gradle/libs.versions.toml"
  "app/build.gradle.kts"
  "app/src"
  "core"
  "data"
  "feature"
  "docs/INDEX.md"
  "scripts/design-system-scan.py"
  "scripts/ci/run-quality-gate.sh"
)

missing=()
for path in "${required[@]}"; do
  [[ -e "$ROOT/$path" ]] || missing+=("$path")
done
if (( ${#missing[@]} > 0 )); then
  printf 'package-source: required source path missing: %s\n' "${missing[@]}" >&2
  exit 66
fi

# A source-of-truth name is an admission claim, not a packaging preference.
if [[ "$(basename "$OUTPUT" | tr '[:upper:]' '[:lower:]')" == *source-of-truth* ]]; then
  ADMISSION_REPORT="${SOURCE_ADMISSION_REPORT:-$ROOT/build/reports/ci/gates/source-of-truth-admission.json}"
  python3 - "$ROOT" "$ADMISSION_REPORT" <<'PY_ADMISSION'
from __future__ import annotations
import hashlib, json, sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(); report=Path(sys.argv[2])
if not report.exists():
    raise SystemExit("package-source: FAIL_SOURCE_OF_TRUTH_ADMISSION: fresh admission report missing")
try: data=json.loads(report.read_text(encoding="utf-8"))
except Exception as exc: raise SystemExit(f"package-source: FAIL_SOURCE_OF_TRUTH_ADMISSION: invalid admission report: {exc}")
if data.get("status") != "PASS":
    raise SystemExit("package-source: FAIL_SOURCE_OF_TRUTH_ADMISSION: admission status is not PASS")
required={'change-contract','architecture','kotlin-quality','documentation','design-system','design-system-diff','detekt','lint','tests','debug-build'}
if set(data.get('required_stages',[])) != required:
    raise SystemExit("package-source: FAIL_SOURCE_OF_TRUTH_ADMISSION: required stage set mismatch")
EXCLUDED_DIRS={'.git','.gradle','.idea','.kotlin','build','out','__pycache__'}
EXCLUDED_SUFFIXES={'.zip','.apk','.aab','.class','.pyc','.log','.tmp'}
rows=[]
for p in sorted(root.rglob('*')):
    if not p.is_file(): continue
    rp=p.relative_to(root)
    if any(x in EXCLUDED_DIRS for x in rp.parts) or p.suffix.lower() in EXCLUDED_SUFFIXES: continue
    b=p.read_bytes(); rows.append(f"{rp.as_posix()}\0{hashlib.sha256(b).hexdigest()}\0{len(b)}")
fp=hashlib.sha256('\n'.join(rows).encode()).hexdigest()
if fp != data.get('tree_fingerprint'):
    raise SystemExit(f"package-source: FAIL_STALE_ADMISSION_REPORT: expected {data.get('tree_fingerprint')} actual {fp}")
print(f"package-source: source-of-truth admission PASS tree_fingerprint={fp}")
PY_ADMISSION
fi

python3 - "$ROOT" "$OUTPUT" <<'PY'
from __future__ import annotations

import os
import stat
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1]).resolve()
output = Path(sys.argv[2]).resolve()

EXCLUDED_DIR_NAMES = {
    ".git",
    ".gradle",
    ".kotlin",
    ".idea",
    ".vscode",
    ".cache",
    ".inspection",
    ".inspections",
    ".screenshots",
    ".captures",
    "__pycache__",
    "build",
}
EXCLUDED_FILE_NAMES = {
    "local.properties",
    "google-services.json",
    ".env",
    ".DS_Store",
    "Thumbs.db",
}
EXCLUDED_SUFFIXES = {
    ".zip",
    ".apk",
    ".aab",
    ".apks",
    ".log",
    ".hprof",
    ".tmp",
    ".swp",
    ".pyc",
    ".pyo",
    ".jks",
    ".keystore",
    ".p12",
    ".pfx",
    ".pem",
    ".key",
}


def excluded(rel: Path) -> bool:
    if any(part in EXCLUDED_DIR_NAMES for part in rel.parts[:-1]):
        return True
    name = rel.name
    if name in EXCLUDED_FILE_NAMES:
        return True
    if name.startswith(".env."):
        return True
    if name.endswith("~") or rel.suffix.lower() in EXCLUDED_SUFFIXES:
        return True
    if name.startswith("Verto-v") and name.endswith("-execution-report.md"):
        return True
    if name.startswith("CHANGED_FILES") and name.endswith(".txt"):
        return True
    if name.endswith("-build-report.md"):
        return True
    return False

files: list[tuple[Path, Path]] = []
for path in root.rglob("*"):
    if not path.is_file() or path.is_symlink():
        continue
    rel = path.relative_to(root)
    if excluded(rel):
        continue
    if path.resolve() == output:
        continue
    files.append((rel, path))

files.sort(key=lambda item: item[0].as_posix())
if not files:
    raise SystemExit("package-source: no source files selected")

# Fixed timestamps + sorted entries make identical source trees byte-for-byte reproducible.
fixed_time = (2000, 1, 1, 0, 0, 0)
tmp = output.with_suffix(output.suffix + ".tmp")
if tmp.exists():
    tmp.unlink()

with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for rel, path in files:
        data = path.read_bytes()
        info = zipfile.ZipInfo(rel.as_posix(), fixed_time)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.create_system = 3
        mode = stat.S_IMODE(path.stat().st_mode)
        info.external_attr = (stat.S_IFREG | mode) << 16
        archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

os.replace(tmp, output)
print(f"package-source: wrote {output} ({len(files)} files)")
PY
