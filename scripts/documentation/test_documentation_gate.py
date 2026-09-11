#!/usr/bin/env python3
"""Negative mutation proof for the v318 documentation drift gate."""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = ROOT / "scripts/documentation/documentation_gate.py"
CHECK_RE = re.compile(r"^(D[1-9]_[A-Z_]+)\s+(PASS|FAIL)\b")


def run_gate(root: Path):
    cp = subprocess.run(
        [sys.executable, str(VALIDATOR), "--root", str(root)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    statuses = {}
    for line in cp.stdout.splitlines():
        m = CHECK_RE.match(line)
        if m:
            statuses[m.group(1)] = m.group(2)
    return cp.returncode, statuses, cp.stdout


def append_map_row(path: Path, row: str):
    text = path.read_text(encoding="utf-8")
    # Keep the row in the canonical table, before the first blank line after table rows.
    marker = "\n\n"
    table_start = text.index("| Responsibility |")
    end = text.find(marker, table_start)
    if end < 0:
        raise RuntimeError("canonical table terminator not found")
    text = text[:end] + "\n" + row + text[end:]
    path.write_text(text, encoding="utf-8")


def add_index_link(path: Path, label: str, target: str):
    text = path.read_text(encoding="utf-8")
    marker = "## Quality\n\n"
    if marker not in text:
        raise RuntimeError("Quality section not found")
    text = text.replace(marker, marker + f"- [{label}]({target})\n", 1)
    path.write_text(text, encoding="utf-8")


def fixture_doc(path: Path, title: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "---\nstatus: canonical\nscope: system\nowner: \"documentation-governance\"\nlast_verified_against: v318\n---\n"
        f"# {title}\n",
        encoding="utf-8",
    )


def mutate_n1(root: Path):
    candidates = sorted(root.glob("**/src/main/**/*.kt"))
    target = next(p for p in candidates if ".rpc(" in p.read_text(encoding="utf-8", errors="ignore"))
    with target.open("a", encoding="utf-8") as f:
        f.write('\n// v318 mutation fixture only\n.rpc("v318_undocumented_rpc_fixture")\n')


def mutate_n2(root: Path):
    p = root / "settings.gradle.kts"
    p.write_text(p.read_text(encoding="utf-8") + '\ninclude(":feature:v318_fixture")\n', encoding="utf-8")


def mutate_n3(root: Path):
    p = root / "docs/quality/documentation-policy.md"
    p.write_text(p.read_text(encoding="utf-8") + "\n[broken fixture](../v318-does-not-exist.md)\n", encoding="utf-8")


def mutate_n4(root: Path):
    p = root / "docs/CANONICAL_DOCUMENT_MAP.md"
    text = p.read_text(encoding="utf-8")
    lines = text.splitlines()
    first = next(line for line in lines if line.startswith("| Repository documentation entry |"))
    idx = lines.index(first)
    lines.insert(idx + 1, first)
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")


def mutate_n5(root: Path):
    p = root / "docs/quality/documentation-policy.md"
    text = p.read_text(encoding="utf-8")
    text, count = re.subn(r'^owner:.*\n', '', text, count=1, flags=re.M)
    if count != 1:
        raise RuntimeError("owner metadata not found")
    p.write_text(text, encoding="utf-8")


def mutate_n6(root: Path):
    add_index_link(root / "docs/INDEX.md", "Deprecated fixture", "design-system/DESIGN-SYSTEM-CONTRACT.md")


def mutate_n7(root: Path):
    rel = "docs/quality/LATEST-policy.md"
    fixture_doc(root / rel, "Naming Fixture")
    append_map_row(
        root / "docs/CANONICAL_DOCUMENT_MAP.md",
        f"| v318 naming mutation fixture | `{rel}` | mutation fixture | system | documentation-governance | v318 | none | none |",
    )
    add_index_link(root / "docs/INDEX.md", "Naming Fixture", "quality/LATEST-policy.md")


def mutate_n8(root: Path):
    rel = "docs/quality/index-orphan-fixture.md"
    fixture_doc(root / rel, "Index Orphan Fixture")
    append_map_row(
        root / "docs/CANONICAL_DOCUMENT_MAP.md",
        f"| v318 INDEX orphan mutation fixture | `{rel}` | mutation fixture | system | documentation-governance | v318 | none | none |",
    )


def mutate_n9(root: Path):
    p = root / "docs/CANONICAL_DOCUMENT_MAP.md"
    text = p.read_text(encoding="utf-8")
    lines = text.splitlines()
    kept = [line for line in lines if "`docs/features/authentication.md`" not in line]
    if len(kept) == len(lines):
        raise RuntimeError("authentication Canonical mapping not found")
    p.write_text("\n".join(kept) + "\n", encoding="utf-8")


CASES = [
    ("N1_UNDOCUMENTED_RPC", "D5_RPC_DRIFT", mutate_n1),
    ("N2_UNDOCUMENTED_MODULE", "D6_MODULE_MAP_DRIFT", mutate_n2),
    ("N3_BROKEN_INTERNAL_LINK", "D3_INTERNAL_LINKS", mutate_n3),
    ("N4_DUPLICATE_CANONICAL_RESPONSIBILITY", "D1_CANONICAL_REGISTRY", mutate_n4),
    ("N5_MISSING_METADATA", "D2_METADATA", mutate_n5),
    ("N6_DEPRECATED_ACTIVE_REFERENCE", "D4_DEPRECATED_REFERENCES", mutate_n6),
    ("N7_CANONICAL_NAMING", "D8_CANONICAL_NAMING", mutate_n7),
    ("N8_INDEX_ORPHAN", "D9_INDEX_INTEGRITY", mutate_n8),
    ("N9_CRITICAL_FEATURE_COVERAGE", "D7_FEATURE_COVERAGE", mutate_n9),
]


def copy_repository(dst: Path):
    def ignore(_dir, names):
        return [n for n in names if n in {"build", ".gradle", ".git"}]
    shutil.copytree(ROOT, dst, ignore=ignore)


def main() -> int:
    results = []
    with tempfile.TemporaryDirectory(prefix="verto-doc-gate-v318-") as td:
        base = Path(td) / "fixture"
        copy_repository(base)
        rc, statuses, out = run_gate(base)
        if rc != 0:
            print("BASELINE_FIXTURE FAIL")
            print(out)
            return 2

        for name, expected_check, mutator in CASES:
            case_root = Path(td) / name
            shutil.copytree(base, case_root)
            mutator(case_root)
            rc, statuses, out = run_gate(case_root)
            passed = rc != 0 and statuses.get(expected_check) == "FAIL"
            results.append({
                "case": name,
                "expected_check": expected_check,
                "gate_exit": rc,
                "observed": statuses.get(expected_check, "MISSING"),
                "result": "PASS" if passed else "FAIL",
            })
            print(f"{name} {'PASS' if passed else 'FAIL'} expected={expected_check} observed={statuses.get(expected_check, 'MISSING')} gate_exit={rc}")
            if not passed:
                print(out)

    passed_count = sum(r["result"] == "PASS" for r in results)
    report = ROOT / "build/reports/documentation/mutation-tests.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({"schema": 1, "passed": passed_count, "total": len(results), "cases": results}, indent=2) + "\n", encoding="utf-8")
    print(f"NEGATIVE_MUTATION_TESTS {passed_count}/{len(results)}")
    return 0 if passed_count == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
