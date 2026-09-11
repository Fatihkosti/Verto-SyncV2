#!/usr/bin/env python3
"""Anti-regression gate for Verto's user-facing error boundary.

The gate intentionally does not ban internal diagnostic use of Throwable.message. It bans raw
exception text and text-parsing heuristics in presentation/UI production code, and prevents the
legacy ErrorHumanizer surface from expanding beyond the Session 363 baseline.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "config/quality/error_humanizer_legacy_baseline.txt"

UI_MARKERS = ("/presentation/", "/ui/")
UI_SUFFIXES = ("ViewModel.kt", "Screen.kt", "Route.kt")
RAW_MESSAGE = re.compile(
    r"\b(?:throwable|exception|error|e)\??\.message\b|"
    r"exceptionOrNull\(\)\?\.message"
)
TEXT_HEURISTIC = re.compile(
    r"\.message\??\.(?:contains|startsWith|endsWith)|"
    r"message\?\.(?:contains|startsWith|endsWith)",
    re.IGNORECASE,
)


def production_kotlin():
    roots = [ROOT / "app/src/main", ROOT / "feature", ROOT / "core"]
    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*.kt"):
            value = path.as_posix()
            if "/src/main/" in value:
                yield path


def is_ui_boundary(path: Path) -> bool:
    value = path.as_posix()
    return any(marker in value for marker in UI_MARKERS) or path.name.endswith(UI_SUFFIXES)


def load_baseline():
    result = {}
    if not BASELINE.exists():
        return result
    for line in BASELINE.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        rel, count = line.rsplit("|", 1)
        result[rel] = int(count)
    return result


violations = []
current_humanizer = {}
for path in production_kotlin():
    rel = path.relative_to(ROOT).as_posix()
    text = path.read_text(encoding="utf-8", errors="ignore")
    humanizer_count = text.count("ErrorHumanizer.humanize")
    if humanizer_count:
        current_humanizer[rel] = humanizer_count

    if not is_ui_boundary(path):
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if RAW_MESSAGE.search(line):
            violations.append(f"RAW_EXCEPTION_TEXT {rel}:{number}: {line.strip()}")
        if TEXT_HEURISTIC.search(line):
            violations.append(f"MESSAGE_TEXT_HEURISTIC {rel}:{number}: {line.strip()}")

baseline = load_baseline()
for rel, count in current_humanizer.items():
    allowed = baseline.get(rel, 0)
    if count > allowed:
        violations.append(f"LEGACY_HUMANIZER_EXPANDED {rel}: {count}>{allowed}")

if violations:
    print("USER_ERROR_BOUNDARY_GATE=FAIL")
    for violation in violations:
        print(violation)
    sys.exit(1)

print(f"USER_ERROR_BOUNDARY_GATE=PASS ui_files_scanned={sum(1 for p in production_kotlin() if is_ui_boundary(p))}")
print(f"legacy_humanizer_calls={sum(current_humanizer.values())} baseline_calls={sum(baseline.values())}")
