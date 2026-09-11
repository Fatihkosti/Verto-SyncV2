#!/usr/bin/env python3
"""Move literal UI labels into the shared resource contract.

This intentionally targets only the scanner's user-facing call sites. Domain
strings in ViewModels and repositories are left for feature-owned resource
work because `stringResource` is a composable API.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import re
from pathlib import Path

MATCHERS = [
    re.compile(r'(\bText\s*\(\s*(?:text\s*=\s*)?)"((?:\\.|[^"\\])*)"'),
    re.compile(r'(\bcontentDescription\s*=\s*)"((?:\\.|[^"\\])*)"'),
    re.compile(r'(\b(?:label|placeholder|title|message|confirmLabel|supportingText|errorText|successText)\s*=\s*)"((?:\\.|[^"\\])*)"'),
]
INTERPOLATION = re.compile(r"\$\{([^{}]+)\}|\$([A-Za-z_][A-Za-z0-9_]*)")


def kotlin_unescape(value: str) -> str:
    replacements = {"\\n": "\n", "\\r": "\r", "\\t": "\t", '\\"': '"', "\\\\": "\\"}
    for source, target in replacements.items():
        value = value.replace(source, target)
    return value


def resource_value(raw: str) -> tuple[str, list[str]] | None:
    raw = kotlin_unescape(raw)
    cursor = 0
    args: list[str] = []
    chunks: list[str] = []
    for match in INTERPOLATION.finditer(raw):
        literal = raw[cursor:match.start()]
        chunks.append(literal.replace("%", "%%"))
        args.append(match.group(1) or match.group(2) or "")
        chunks.append(f"%{len(args)}$s")
        cursor = match.end()
    chunks.append(raw[cursor:].replace("%", "%%"))
    value = "".join(chunks)
    if "$" in INTERPOLATION.sub("", raw) or any(not arg for arg in args):
        return None
    return value, args


def should_scan(path: Path, root: Path) -> bool:
    rel = path.relative_to(root).as_posix()
    if "/src/main/kotlin/" not in rel or rel.startswith("core/designsystem/"):
        return False
    if not ("/presentation/" in rel or "/ui/" in rel):
        return False
    name = path.name
    if any(part in name for part in ("ViewModel", "Repository", "Mapper", "Policy", "Contract", "State")):
        return False
    text = path.read_text(errors="replace")
    return "@Composable" in text or "Text(" in text or "contentDescription" in text


def migrate(root: Path) -> tuple[dict[str, str], list[str]]:
    values: dict[str, str] = {}
    skipped: list[str] = []
    for path in sorted(root.rglob("*.kt")):
        if not should_scan(path, root):
            continue
        text = path.read_text(errors="replace")
        changed = False
        for matcher in MATCHERS:
            def replace(match: re.Match[str]) -> str:
                nonlocal changed
                raw = match.group(2)
                parsed = resource_value(raw)
                if parsed is None:
                    skipped.append(f"{path.relative_to(root)}: {raw}")
                    return match.group(0)
                value, args = parsed
                key = "ds_" + hashlib.sha1(value.encode("utf-8")).hexdigest()[:12]
                values[key] = value
                call = f"androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.{key}"
                if args:
                    call += ", " + ", ".join(args)
                call += ")"
                changed = True
                return match.group(1) + call
            text = matcher.sub(replace, text)
        if changed:
            path.write_text(text)
    return values, skipped


def update_resources(root: Path, values: dict[str, str]) -> None:
    resource_path = root / "core/designsystem/src/main/res/values/strings.xml"
    source = resource_path.read_text()
    entries = []
    for key, value in sorted(values.items()):
        if f'name="{key}"' in source:
            continue
        entries.append(f'    <string name="{key}">{html.escape(value, quote=False)}</string>')
    if entries:
        source = source.replace("</resources>", "\n" + "\n".join(entries) + "\n</resources>")
        resource_path.write_text(source)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    args = parser.parse_args()
    values, skipped = migrate(Path(args.root).resolve())
    update_resources(Path(args.root).resolve(), values)
    print(f"migrated_resources={len(values)}")
    print(f"skipped_interpolations={len(skipped)}")
    for item in skipped[:20]:
        print(f"SKIP {item}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
