#!/usr/bin/env python3
"""Verto Architecture Guard v2.

Canonical authority is the v319 machine-readable contract set under
``docs/architecture/contracts``. The legacy ``architecture-rules.json`` and v181
baseline remain historical/debug evidence only and are never selected by default.

The guard is standard-library only and fail-closed. It does not invoke Gradle,
network, Git, Android SDK, SQL, or server tooling.
"""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable

FORMAT_SCAN = "verto-architecture-scan-v2"
FORMAT_SNAPSHOT = "verto-architecture-snapshot-v1"
DEFAULT_CONTRACT = "docs/architecture/contracts/architecture-contracts.json"
DEFAULT_DEPENDENCIES = "docs/architecture/contracts/dependency-contract.json"
DEFAULT_OWNERSHIP = "docs/architecture/contracts/data-ownership.json"
DEFAULT_DEBT = "docs/architecture/contracts/technical-debt-baseline.json"
DEFAULT_RULE_BASELINE = "docs/architecture/contracts/rule-catalog-baseline-v319.json"
DEFAULT_GOVERNANCE_BASELINE = "docs/architecture/contracts/technical-debt-governance-v319.json"
DEFAULT_FORWARD_DEBT = "docs/architecture/contracts/technical-debt-governance-v321.json"
DEFAULT_COMPLEXITY_BASELINE = "docs/architecture/contracts/complexity-baseline-v322.json"
DEFAULT_EXCEPTIONS = "docs/architecture/contracts/temporary-exceptions.json"
FEATURE_MANIFEST_DIR = "docs/architecture/contracts/features"
EXCLUDED_DIRS = {".git", ".gradle", ".idea", ".kotlin", "build", "out", "__pycache__"}
EXCLUDED_SUFFIXES = {".zip", ".apk", ".aab", ".class", ".pyc"}
INCLUDE_RE = re.compile(r"include\((.*?)\)", re.DOTALL)
QUOTED_MODULE_RE = re.compile(r"[\"'](:[^\"']+)[\"']")
PROJECT_DEP_RE = re.compile(r"project\(\s*[\"'](:[^\"']+)[\"']\s*\)")
IMPORT_RE = re.compile(r"^\s*import\s+([^\s;]+)", re.MULTILINE)
PACKAGE_RE = re.compile(r"^\s*package\s+([^\s;]+)", re.MULTILINE)
ROOM_RE = re.compile(r"ROOM_SCHEMA_VERSION\s*:\s*Int\s*=\s*(\d+)")
EXTERNAL_CALL_RE = re.compile(
    r"(?m)^\s*(api|implementation|compileOnly|runtimeOnly|ksp|kapt|annotationProcessor|"
    r"debugImplementation|releaseImplementation|testImplementation|androidTestImplementation|"
    r"coreLibraryDesugaring)\s*\(([^\n]+)\)"
)
LIBS_REF_RE = re.compile(r"\blibs(?:\.[A-Za-z0-9_]+)+")
COORD_RE = re.compile(r"[\"']([^\"']+:[^\"']+:[^\"']+)[\"']")
PUBLIC_DECL_RE = re.compile(
    r"^\s*(?P<mods>(?:(?:public|protected|open|abstract|sealed|data|enum|value|annotation|expect|actual|inline|suspend|operator|infix|tailrec|external|const|lateinit|override)\s+)*)"
    r"(?P<kind>class|interface|object|fun|typealias|val|var)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*|`[^`]+`)"
)
ENTITY_RE = re.compile(r"@Entity\b[\s\S]*?\b(?:data\s+)?(?:class|object)\s+(\w+)")
DAO_RE = re.compile(r"@Dao\b[\s\S]*?\b(?:interface|abstract\s+class|class)\s+(\w+)")
REPO_DECL_RE = re.compile(r"(?m)^\s*(?:(?:public|internal|private|protected|open|abstract|sealed|data|enum|value|fun)\s+)*(?:class|interface|object)\s+(\w+)")
SEVERITY_ORDINAL = {"WARNING": 0, "RATCHET": 1, "ZERO_TOLERANCE": 2}
REQUIRED_EXCEPTION_FIELDS = {"rule_id", "identity_or_path", "owner", "reason", "created_at", "expiry", "remediation_issue", "approval"}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def rel(path: Path, root: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def is_excluded(path: Path, root: Path) -> bool:
    try: rp = path.resolve().relative_to(root.resolve())
    except ValueError: return True
    return any(part in EXCLUDED_DIRS for part in rp.parts) or path.suffix.lower() in EXCLUDED_SUFFIXES


def iter_files(root: Path) -> Iterable[Path]:
    for path in sorted(root.rglob("*")):
        if path.is_file() and not is_excluded(path, root): yield path


def production_kotlin(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*.kt") if "/src/main/" in f"/{rel(p, root)}" and not is_excluded(p, root))


def parse_modules(root: Path) -> list[str]:
    settings = root / "settings.gradle.kts"
    if not settings.exists(): raise RuntimeError("settings.gradle.kts missing")
    modules: list[str] = []
    for match in INCLUDE_RE.finditer(read_text(settings)): modules.extend(QUOTED_MODULE_RE.findall(match.group(1)))
    return list(dict.fromkeys(modules))


def module_dir(root: Path, module: str) -> Path:
    return root / module.lstrip(":").replace(":", "/")


def module_of_path(path: Path, root: Path, modules: list[str]) -> str | None:
    rp = rel(path, root)
    rows = []
    for module in modules:
        prefix = module.lstrip(":").replace(":", "/") + "/"
        if rp.startswith(prefix): rows.append((len(prefix), module))
    return max(rows)[1] if rows else None


def parse_room(root: Path) -> int:
    p = root / "data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt"
    if not p.exists(): return -1
    m = ROOM_RE.search(read_text(p))
    return int(m.group(1)) if m else -1


def parse_module_graph(root: Path, modules: list[str]) -> dict[str, list[str]]:
    graph = {m: [] for m in modules}
    for module in modules:
        build = module_dir(root, module) / "build.gradle.kts"
        if build.exists(): graph[module] = sorted(set(x for x in PROJECT_DEP_RE.findall(read_text(build)) if x in graph))
    return graph


def external_dependencies_for_build(text: str) -> list[str]:
    rows: set[str] = set()
    for conf, expr in EXTERNAL_CALL_RE.findall(text):
        if "project(" in expr or "files(" in expr or "fileTree(" in expr: continue
        tokens = set(LIBS_REF_RE.findall(expr)) | set(COORD_RE.findall(expr))
        if not tokens:
            compact = re.sub(r"\s+", "", expr)
            if compact and not compact.startswith("project("): tokens.add(compact)
        for token in tokens: rows.add(f"{conf}:{token}")
    return sorted(rows)


def parse_external_graph(root: Path, modules: list[str]) -> dict[str, list[str]]:
    result = {}
    for module in modules:
        build = module_dir(root, module) / "build.gradle.kts"
        result[module] = external_dependencies_for_build(read_text(build)) if build.exists() else []
    return result


def find_cycles(graph: dict[str, list[str]]) -> list[list[str]]:
    cycles: set[tuple[str, ...]] = set(); visiting: set[str] = set(); visited: set[str] = set(); stack: list[str] = []
    def canonical(cycle: list[str]) -> tuple[str, ...]:
        body = cycle[:-1]; rotations = [tuple(body[i:] + body[:i]) for i in range(len(body))]; best = min(rotations); return best + (best[0],)
    def dfs(node: str) -> None:
        if node in visiting:
            if node in stack: cycles.add(canonical(stack[stack.index(node):] + [node]))
            return
        if node in visited: return
        visiting.add(node); stack.append(node)
        for nxt in graph.get(node, []): dfs(nxt)
        stack.pop(); visiting.remove(node); visited.add(node)
    for node in sorted(graph): dfs(node)
    return [list(x) for x in sorted(cycles)]


def layer_from_path(path: Path, root: Path) -> str | None:
    rp = f"/{rel(path, root)}/"
    for layer in ("domain", "application", "presentation", "data"):
        if f"/{layer}/" in rp: return layer
    return None


def issue(rule_id: str, path: str, detail: str, severity: str = "ERROR", code: str | None = None) -> dict[str, str]:
    obj = {"rule_id": rule_id, "severity": severity, "path": path, "detail": detail}
    obj["identity"] = f"{rule_id}|{path}|{detail}"
    if code: obj["failure_code"] = code
    return obj


def manifest_path_for_module(root: Path, module: str) -> Path:
    suffix = module.lstrip(":").replace(":", "__")
    return root / FEATURE_MANIFEST_DIR / f"{suffix}.json"


def load_manifests(root: Path, modules: list[str]) -> tuple[dict[str, dict[str, Any]], list[str], list[str]]:
    expected = [m for m in modules if m.startswith(":feature:")]
    manifests: dict[str, dict[str, Any]] = {}
    missing = []
    for module in expected:
        p = manifest_path_for_module(root, module)
        if not p.exists(): missing.append(module); continue
        d = load_json(p); manifests[module] = d
    actual_paths = sorted((root / FEATURE_MANIFEST_DIR).glob("*.json"))
    declared_modules = {load_json(p).get("feature", {}).get("module") for p in actual_paths}
    orphan = sorted(x for x in declared_modules if x and x not in expected)
    return manifests, missing, orphan


def package_prefix_for_module(module: str) -> str:
    bits = module.split(":")[2:]
    return "com.verto.app.feature." + ".".join(bits)


def provider_for_import(imp: str, manifests: dict[str, dict[str, Any]]) -> str | None:
    candidates = []
    for module in manifests:
        prefix = package_prefix_for_module(module)
        if imp == prefix or imp.startswith(prefix + "."): candidates.append((len(prefix), module))
    return max(candidates)[1] if candidates else None


def symbol_declared_public(imp: str, manifest: dict[str, Any]) -> bool:
    entries = list(manifest.get("architecture", {}).get("public_api", [])) + list(manifest.get("architecture", {}).get("exposed_ports", []))
    for entry in entries:
        if entry == imp or imp.startswith(entry + "."): return True
        if entry.endswith(".*") and imp.startswith(entry[:-1]): return True
    return False


def public_surface(module: str, root: Path) -> list[str]:
    base = module_dir(root, module) / "src/main"
    rows: list[str] = []
    if not base.exists(): return rows
    for path in sorted(base.rglob("*.kt")):
        source = read_text(path)
        package = (PACKAGE_RE.search(source).group(1) if PACKAGE_RE.search(source) else "")
        depth = 0
        for raw in source.splitlines():
            # Ignore declarations nested in a class/object/function; lexical top-level only.
            before = depth
            if before == 0:
                m = PUBLIC_DECL_RE.match(raw)
                if m and not re.search(r"\b(private|internal)\b", m.group("mods") or ""):
                    normalized = re.sub(r"\s+", " ", raw.strip())
                    rows.append(f"{rel(path, root)}|{package}|{normalized}")
            # Approximate depth after strings/comments are stripped enough for declarations; braces in strings are rare here.
            code = re.sub(r"//.*$", "", raw)
            depth += code.count("{") - code.count("}")
            depth = max(0, depth)
    return sorted(rows)


def public_surface_hash(module: str, root: Path) -> str:
    payload = "\n".join(public_surface(module, root)).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def scan_data_ownership(root: Path) -> dict[str, Any]:
    db = root / "data/database/src/main/kotlin"
    entities, daos = set(), set()
    if db.exists():
        for p in db.rglob("*.kt"):
            text = read_text(p); entities.update(ENTITY_RE.findall(text)); daos.update(DAO_RE.findall(text))
    repositories: set[str] = set()
    for p in production_kotlin(root):
        rp = rel(p, root)
        for name in REPO_DECL_RE.findall(read_text(p)):
            if "Repository" in name or "/repository/" in f"/{rp}": repositories.add(name)
    schema = root / "app/schemas/com.verto.app.data.local.AppDatabase/81.json"
    tables: set[str] = set()
    if schema.exists():
        try: tables = {x["tableName"] for x in load_json(schema).get("database", {}).get("entities", [])}
        except Exception: tables = set()
    return {"entities": sorted(entities), "daos": sorted(daos), "repositories": sorted(repositories), "tables": sorted(tables)}


def valid_public_api_module_edge(
    src: str,
    dst: str,
    graph: dict[str, list[str]],
    manifests: dict[str, dict[str, Any]],
) -> bool:
    consumer = manifests.get(src, {})
    provider = manifests.get(dst, {})
    allowed = set(consumer.get("integration", {}).get("allowed_public_api_dependencies", []))
    if dst not in allowed or not dst.endswith(":api") or not provider:
        return False
    public_api = provider.get("architecture", {}).get("public_api", [])
    internal_packages = provider.get("architecture", {}).get("internal_packages", [])
    outgoing_feature_edges = [x for x in graph.get(dst, []) if x.startswith(":feature:") and x != dst]
    return bool(public_api) and not internal_packages and not outgoing_feature_edges


def scan_project(root: Path, contract: dict[str, Any] | None = None, debt: dict[str, Any] | None = None,
                 manifests: dict[str, dict[str, Any]] | None = None) -> dict[str, Any]:
    root = root.resolve(); contract = contract or {}; debt = debt or {}; modules = parse_modules(root); manifests = manifests or {}
    room = parse_room(root); graph = parse_module_graph(root, modules); external = parse_external_graph(root, modules); cycles = find_cycles(graph)
    violations: list[dict[str, str]] = []
    large_files: list[dict[str, Any]] = []
    app_composition: dict[str, int] = {}; app_impl: dict[str, int] = {}; app_owned: dict[str, int] = {}
    tracked = ["reports", "payment", "shipment", "invoice"]
    for feature in tracked:
        base = root / f"app/src/main/kotlin/com/verto/app/feature/{feature}"; files = sorted(base.rglob("*.kt")) if base.exists() else []
        composition = [p for p in files if any(part in {"bridge", "wiring"} for part in p.relative_to(base).parts[:-1])]
        impl = [p for p in files if p not in composition]; app_composition[feature] = len(composition); app_impl[feature] = len(impl); app_owned[feature] = len(impl)

    for path in production_kotlin(root):
        rp = rel(path, root); text = read_text(path); imports = IMPORT_RE.findall(text); module = module_of_path(path, root, modules); layer = layer_from_path(path, root)
        line_count = text.count("\n") + (0 if text.endswith("\n") or not text else 1)
        if line_count > 500: large_files.append({"path": rp, "lines": line_count})
        if module and module.startswith(":data:") and any(".feature." in i and ".presentation" in i for i in imports):
            violations.append(issue("VARCH-009", rp, "Data module imports feature presentation"))
        presentation_owned = layer == "presentation" and not (module and module.startswith(":data:"))
        if presentation_owned:
            if any(i.startswith("com.verto.app.data.local.entity") or i.startswith("com.verto.app.data.local.dao") for i in imports):
                violations.append(issue("VARCH-006", rp, "Presentation imports Room entity/DAO"))
            if any(i.startswith("com.verto.app.data.repository") or i.startswith("com.verto.app.data.remote") for i in imports):
                violations.append(issue("VARCH-007", rp, "Presentation imports repository/remote infrastructure"))
            if path.name.endswith("ViewModel.kt") and any(".application.port." in i for i in imports):
                violations.append(issue("VARCH-021", rp, "ViewModel imports low-level application port directly"))
        if layer == "domain":
            if any(i == "android" or i.startswith("android.") or i == "androidx" or i.startswith("androidx.") for i in imports):
                violations.append(issue("VARCH-001", rp, "Domain imports Android/AndroidX"))
            if any(i.startswith("com.verto.app.data.") for i in imports): violations.append(issue("VARCH-002", rp, "Domain imports data/infrastructure"))
        if layer == "application":
            if any(".presentation." in i or i.endswith(".presentation") for i in imports): violations.append(issue("VARCH-004", rp, "Application imports presentation"))
            if any(i.startswith("androidx.room.") or i.startswith("com.verto.app.data.local.") or i.startswith("com.verto.app.data.remote.") for i in imports):
                violations.append(issue("VARCH-005", rp, "Application imports Room/local/remote infrastructure"))
        if module and module.startswith(":core:") and any(i.startswith("com.verto.app.feature.") for i in imports):
            violations.append(issue("VARCH-013", rp, "Core imports feature-owned code"))

        # Cross-feature source boundary. Domain imports are the legacy VARCH-003 set; all other non-public/internal access is v24/v25.
        if module and module.startswith(":feature:"):
            for imp in imports:
                provider = provider_for_import(imp, manifests)
                if not provider or provider == module: continue
                if layer == "domain":
                    violations.append(issue("VARCH-003", rp, f"Domain imports another feature: {provider.split(':')[2]}")); break
                provider_manifest = manifests.get(provider, {})
                if ".data." in imp or imp.endswith(".data"):
                    violations.append(issue("VARCH-024", rp, f"Foreign storage implementation import: {imp}"))
                elif not symbol_declared_public(imp, provider_manifest):
                    violations.append(issue("VARCH-025", rp, f"Foreign INTERNAL implementation import: {imp}"))
                elif presentation_owned and ".presentation." in imp:
                    violations.append(issue("VARCH-008", rp, f"Presentation imports foreign presentation: {provider}"))

    # Module-level cross-feature legacy/unauthorized dependencies.
    legacy_12 = {x.get("identity") for x in debt.get("architecture", {}).get("violations", []) if x.get("rule_id") == "VARCH-012"}
    for src, deps in graph.items():
        if not src.startswith(":feature:"): continue
        m = manifests.get(src, {}); legacy_edges = set(m.get("integration", {}).get("legacy_cross_feature_debt", [])); allowed_api = set(m.get("integration", {}).get("allowed_public_api_dependencies", []))
        for dst in deps:
            if not dst.startswith(":feature:") or dst == src: continue
            edge = f"{src}->{dst}"; candidate = issue("VARCH-012", src, f"Unauthorized feature module dependency -> {dst}")
            if valid_public_api_module_edge(src, dst, graph, manifests):
                continue
            # A manifest declaration alone never turns an implementation module into public API.
            violations.append(candidate)
    for cyc in cycles: violations.append(issue("VARCH-010", "settings.gradle.kts", " -> ".join(cyc)))

    violations = sorted({v["identity"]: v for v in violations}.values(), key=lambda x: (x["rule_id"], x["path"], x["detail"]))
    rule_counts: dict[str, int] = defaultdict(int)
    for v in violations: rule_counts[v["rule_id"]] += 1
    return {
        "format": FORMAT_SCAN,
        "metrics": {
            "modules": len(modules), "room": room, "dependency_cycles": len(cycles),
            "large_production_files_over_500": len(large_files),
            "app_owned_feature_kotlin_files": app_owned, "app_composition_files": app_composition,
            "app_feature_implementation_files": app_impl,
        },
        "module_graph": graph, "external_dependency_graph": external, "cycles": cycles,
        "evidence": {"large_production_files_over_500": sorted(large_files, key=lambda x: (-x["lines"], x["path"]))},
        "rule_counts": dict(sorted(rule_counts.items())), "violations": violations,
    }


def snapshot(root: Path) -> dict[str, Any]:
    root = root.resolve(); rows = []
    for path in iter_files(root):
        data = path.read_bytes(); rows.append({"path": rel(path, root), "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)})
    return {"format": FORMAT_SNAPSHOT, "root": ".", "files": rows}


def parse_allowlist(path: Path) -> list[str]:
    return [x.strip() for x in read_text(path).splitlines() if x.strip() and not x.strip().startswith("#")]


def allowed(path: str, patterns: list[str]) -> bool:
    for p in patterns:
        if p.endswith("/**"):
            prefix = p[:-3].rstrip("/")
            if path == prefix or path.startswith(prefix + "/"): return True
        if fnmatch.fnmatchcase(path, p): return True
    return False


def compare_scope(before: dict[str, Any], after: dict[str, Any], patterns: list[str]) -> dict[str, Any]:
    b = {x["path"]: x for x in before.get("files", [])}; a = {x["path"]: x for x in after.get("files", [])}
    added = sorted(set(a)-set(b)); deleted = sorted(set(b)-set(a)); modified = sorted(p for p in set(a)&set(b) if a[p]["sha256"] != b[p]["sha256"] or a[p]["size"] != b[p]["size"])
    changed = added + modified + deleted; outside = sorted(p for p in changed if not allowed(p, patterns))
    return {"ADDED": added, "MODIFIED": modified, "DELETED": deleted, "OUTSIDE_ALLOWLIST": outside, "SCOPE_GUARD": "PASS" if not outside else "FAIL"}


def validate_rule_governance(contract: dict[str, Any], baseline: dict[str, Any]) -> list[dict[str, Any]]:
    failures = []; rules = contract.get("rules", []); ids = [x.get("id") for x in rules]
    if len(ids) != len(set(ids)): failures.append({"code": "FAIL_RULE_CATALOG_LOAD", "detail": "duplicate Rule ID"})
    expected_prefix = [f"VARCH-{i:03d}" for i in range(1, 31)]
    if ids[:30] != expected_prefix: failures.append({"code": "FAIL_RULE_CATALOG_LOAD", "detail": "VARCH-001..030 not present in stable order", "actual": ids[:30]})
    prior = {x["id"]: x for x in baseline.get("rules", [])}
    for rule in rules:
        rid = rule.get("id")
        if not rule.get("severity") or not rule.get("measurement"): failures.append({"code": "FAIL_RULE_CATALOG_LOAD", "rule_id": rid, "detail": "missing severity/measurement"})
        if rid in prior:
            p = prior[rid]
            if rule.get("prohibited_state") != p.get("prohibited_state") or rule.get("title") != p.get("title"):
                failures.append({"code": "FAIL_RULE_ID_MUTATION", "rule_id": rid})
            old_s = SEVERITY_ORDINAL.get(p.get("severity"), -1); new_s = SEVERITY_ORDINAL.get(rule.get("severity"), -1)
            if new_s < old_s: failures.append({"code": "FAIL_SEVERITY_DOWNGRADE", "rule_id": rid, "before": p.get("severity"), "after": rule.get("severity")})
    return failures


def validate_baseline_governance(debt: dict[str, Any], governance: dict[str, Any]) -> list[dict[str, Any]]:
    failures = []
    old_metrics = governance.get("kotlin_quality_metrics", {}); new_metrics = debt.get("kotlin_quality", {}).get("metrics", {})
    for metric, ceiling in old_metrics.items():
        if metric in new_metrics and isinstance(new_metrics[metric], (int, float)) and new_metrics[metric] > ceiling:
            failures.append({"code": "FAIL_BASELINE_LOOSENING", "metric": metric, "governance_max": ceiling, "current_baseline": new_metrics[metric]})
    old_ids = set(governance.get("legacy_architecture_identities", [])); new_ids = {x.get("identity") for x in debt.get("architecture", {}).get("violations", [])}
    if not new_ids.issubset(old_ids): failures.append({"code": "FAIL_BASELINE_LOOSENING", "new_legacy_identities": sorted(new_ids-old_ids)})
    return failures


def validate_exceptions(data: dict[str, Any]) -> list[dict[str, Any]]:
    failures = []
    for idx, item in enumerate(data.get("exceptions", [])):
        missing = sorted(k for k in REQUIRED_EXCEPTION_FIELDS if not str(item.get(k, "")).strip())
        if missing: failures.append({"code": "FAIL_EXCEPTION_GOVERNANCE", "rule_id": "VARCH-030", "index": idx, "missing": missing})
    return failures


def validate_dependency_contract(graph: dict[str, list[str]], external: dict[str, list[str]], dep: dict[str, Any], manifests: dict[str, dict[str, Any]]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    failures = []; module_rows = {x.get("module"): x for x in dep.get("modules", [])}; undeclared_project = []; phantom_project = []; undeclared_external = []; manifest_dep = []; manifest_external = []
    for module, deps in graph.items():
        row = module_rows.get(module)
        if row is None:
            failures.append({"code": "FAIL_UNDECLARED_DEPENDENCY", "module": module, "detail": "module missing from dependency contract"}); continue
        declared = {x.get("target") for x in row.get("direct_project_dependencies", [])}
        for target in sorted(set(deps)-declared): undeclared_project.append(f"{module}->{target}")
        for target in sorted(declared-set(deps)): phantom_project.append(f"{module}->{target}")
        ext_declared = {x.get("identity") for x in row.get("external_dependencies", [])}
        for ident in sorted(set(external.get(module, []))-ext_declared): undeclared_external.append(f"{module}|{ident}")
        for ident in sorted(ext_declared-set(external.get(module, []))): undeclared_external.append(f"PHANTOM:{module}|{ident}")
        if module.startswith(":feature:") and module in manifests:
            m = manifests[module]; mout = set(m.get("architecture", {}).get("outgoing_dependencies", [])); mext = set(m.get("architecture", {}).get("external_dependencies", []))
            for target in sorted(set(deps)-mout): manifest_dep.append(f"{module}->{target}")
            for ident in sorted(set(external.get(module, []))-mext): manifest_external.append(f"{module}|{ident}")
    if undeclared_project or phantom_project: failures.append({"code": "FAIL_UNDECLARED_DEPENDENCY", "rule_id": "VARCH-020", "undeclared": undeclared_project, "phantom": phantom_project})
    if manifest_dep: failures.append({"code": "FAIL_MANIFEST_DEPENDENCY_DRIFT", "rule_id": "VARCH-020", "edges": manifest_dep})
    if undeclared_external or manifest_external: failures.append({"code": "FAIL_UNDECLARED_EXTERNAL_DEPENDENCY", "rule_id": "VARCH-026", "dependency_contract": undeclared_external, "feature_manifest": manifest_external})
    return failures, {"undeclared_project": undeclared_project, "phantom_project": phantom_project, "undeclared_external": undeclared_external, "manifest_project": manifest_dep, "manifest_external": manifest_external}


def validate_ownership(root: Path, contract: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    observed = scan_data_ownership(root); failures = []; diffs = {}
    for key in ("entities", "tables", "daos", "repositories"):
        rows = contract.get(key, []); identities = [x.get("identity") for x in rows]; duplicates = sorted({x for x in identities if identities.count(x) > 1 and x})
        missing_owner = sorted(x.get("identity") for x in rows if not x.get("owner_module") or not x.get("owner_feature_or_infrastructure"))
        expected = set(identities); actual = set(observed[key]); missing = sorted(actual-expected); phantom = sorted(expected-actual)
        diffs[key] = {"observed": len(actual), "declared": len(expected), "missing_owner_records": missing, "phantom_owner_records": phantom, "duplicates": duplicates}
        if duplicates or missing_owner or missing or phantom:
            code = "FAIL_DATA_OWNER_DUPLICATED" if duplicates else "FAIL_DATA_OWNER_MISSING"
            failures.append({"code": code, "rule_id": "VARCH-023", "kind": key, "duplicates": duplicates, "missing_owner_fields": missing_owner, "missing": missing, "phantom": phantom})
    return failures, diffs


def verify_project(root: Path, contract: dict[str, Any], dep: dict[str, Any], ownership: dict[str, Any], debt: dict[str, Any],
                   rule_baseline: dict[str, Any], governance_baseline: dict[str, Any], exceptions: dict[str, Any],
                   complexity_baseline_path: Path | None = None) -> dict[str, Any]:
    root = root.resolve(); modules = parse_modules(root); manifests, missing_manifests, orphan_manifests = load_manifests(root, modules); failures: list[dict[str, Any]] = []
    failures.extend(validate_rule_governance(contract, rule_baseline)); failures.extend(validate_baseline_governance(debt, governance_baseline)); failures.extend(validate_exceptions(exceptions))
    if missing_manifests or orphan_manifests: failures.append({"code": "FAIL_MISSING_FEATURE_MANIFEST", "rule_id": "VARCH-019", "missing": missing_manifests, "orphan": orphan_manifests})

    scan = scan_project(root, contract, debt, manifests)
    dep_failures, dep_details = validate_dependency_contract(scan["module_graph"], scan["external_dependency_graph"], dep, manifests); failures.extend(dep_failures)
    own_failures, own_details = validate_ownership(root, ownership); failures.extend(own_failures)

    public_drift = []
    for module, m in manifests.items():
        expected = m.get("verification", {}).get("public_api_surface_hash")
        actual = public_surface_hash(module, root)
        if not expected or expected != actual: public_drift.append({"module": module, "expected": expected, "actual": actual})
    if public_drift: failures.append({"code": "FAIL_PUBLIC_API_UNDECLARED", "rule_id": "VARCH-022", "drift": public_drift})

    legacy = {x.get("identity") for x in debt.get("architecture", {}).get("violations", [])}
    current = {x.get("identity") for x in scan.get("violations", [])}
    new_ids = sorted(current-legacy); removed_ids = sorted(legacy-current)
    # Historical v318 evidence remains immutable. A forward anchor prevents repaired legacy identities from returning.
    forward_path = root / DEFAULT_FORWARD_DEBT
    reappeared_removed: list[str] = []
    if forward_path.exists():
        forward = load_json(forward_path)
        admitted = set(forward.get("admitted_legacy_architecture_identities", []))
        reappeared_removed = sorted((current & legacy) - admitted)
        if reappeared_removed:
            failures.append({"code": "FAIL_REMOVED_DEBT_REAPPEARED", "rule_id": "VARCH-015", "identities": reappeared_removed})
    # Any clean rule remains zero; exact historical identities may only disappear.
    if new_ids: failures.append({"code": "FAIL_ARCHITECTURE_GUARD", "new_violation_identities": new_ids})
    if scan["metrics"]["dependency_cycles"] != 0: failures.append({"code": "FAIL_UNDECLARED_DEPENDENCY", "rule_id": "VARCH-010", "cycles": scan["cycles"]})

    complexity_result: dict[str, Any] = {"status": "NOT_RUN"}
    if complexity_baseline_path and complexity_baseline_path.exists():
        import importlib.util
        cp = root / "tools/architecture/complexity_guard.py"
        spec = importlib.util.spec_from_file_location("verto_complexity_guard", cp); mod = importlib.util.module_from_spec(spec); assert spec and spec.loader; spec.loader.exec_module(mod)
        complexity_result = mod.verify(root, load_json(complexity_baseline_path))
        if complexity_result.get("status") != "PASS": failures.extend(complexity_result.get("failures", []))

    statuses = {
        "rule_catalog": "PASS" if not any(f.get("code") in {"FAIL_RULE_CATALOG_LOAD", "FAIL_RULE_ID_MUTATION", "FAIL_SEVERITY_DOWNGRADE"} for f in failures) else "FAIL",
        "feature_manifests": "PASS" if not missing_manifests and not orphan_manifests else "FAIL",
        "dependency_admission": "PASS" if not dep_failures else "FAIL",
        "data_ownership": "PASS" if not own_failures else "FAIL",
        "public_api": "PASS" if not public_drift else "FAIL",
        "legacy_identity_ratchet": "PASS" if not new_ids else "FAIL",
        "complexity": complexity_result.get("status", "NOT_RUN"),
    }
    return {
        "format": "verto-architecture-verification-v2", "status": "PASS" if not failures else "FAIL",
        "authority": {"architecture": DEFAULT_CONTRACT, "dependencies": DEFAULT_DEPENDENCIES, "ownership": DEFAULT_OWNERSHIP, "debt": DEFAULT_DEBT, "legacy_default_authority": False},
        "rule_catalog_count": len(contract.get("rules", [])), "feature_manifest_coverage": f"{len(manifests)}/{len([m for m in modules if m.startswith(':feature:')])}",
        "scan": scan, "legacy_debt_before": len(legacy), "legacy_debt_after": len(current & legacy), "new_architecture_violation_identities": new_ids,
        "removed_legacy_violation_identities": removed_ids, "reappeared_removed_legacy_identities": reappeared_removed, "dependency_details": dep_details, "ownership_details": own_details,
        "public_api_drift": public_drift, "complexity": complexity_result, "statuses": statuses, "failures": failures,
    }


def markdown_report(result: dict[str, Any]) -> str:
    scan = result.get("scan", result); lines = ["# Verto Architecture Guard v2", "", "```text"]
    for k in ("status", "rule_catalog_count", "feature_manifest_coverage", "legacy_debt_before", "legacy_debt_after"):
        if k in result: lines.append(f"{k} = {result[k]}")
    if "new_architecture_violation_identities" in result: lines.append(f"new_architecture_violation_identities = {len(result['new_architecture_violation_identities'])}")
    lines += ["```", "", "## Statuses", ""]
    for k, v in result.get("statuses", {}).items(): lines.append(f"- `{k}`: **{v}**")
    lines += ["", "## Rule counts", ""]
    for k, v in scan.get("rule_counts", {}).items(): lines.append(f"- `{k}`: {v}")
    if result.get("failures"):
        lines += ["", "## Failures", "", "```json", json.dumps(result["failures"], ensure_ascii=False, indent=2, sort_keys=True), "```"]
    return "\n".join(lines) + "\n"


def canonical_paths(root: Path, args: argparse.Namespace) -> dict[str, Path]:
    def p(value: str) -> Path:
        q = Path(value); return q if q.is_absolute() else root / q
    return {
        "contract": p(args.contract), "dependencies": p(args.dependencies), "ownership": p(args.ownership), "debt": p(args.debt),
        "rule_baseline": p(args.rule_baseline), "governance_baseline": p(args.governance_baseline), "exceptions": p(args.exceptions),
        "complexity": p(args.complexity_baseline),
    }


def cmd_scan(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve(); paths = canonical_paths(root, args); contract = load_json(paths["contract"]); debt = load_json(paths["debt"]); manifests, _, _ = load_manifests(root, parse_modules(root))
    result = scan_project(root, contract, debt, manifests)
    if args.output: write_json(Path(args.output), result)
    print(json.dumps(result["metrics"], ensure_ascii=False, sort_keys=True)); return 0


def cmd_verify(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve(); paths = canonical_paths(root, args)
    required = [paths[x] for x in ("contract", "dependencies", "ownership", "debt", "rule_baseline", "governance_baseline", "exceptions")]
    missing = [rel(p, root) if p.is_relative_to(root) else str(p) for p in required if not p.exists()]
    if missing:
        result = {"status": "FAIL", "failures": [{"code": "FAIL_CONTRACT_PARSE", "missing": missing}]}; code = 2
    else:
        try:
            result = verify_project(root, load_json(paths["contract"]), load_json(paths["dependencies"]), load_json(paths["ownership"]), load_json(paths["debt"]), load_json(paths["rule_baseline"]), load_json(paths["governance_baseline"]), load_json(paths["exceptions"]), paths["complexity"])
            code = 0 if result["status"] == "PASS" else 2
        except Exception as exc:
            result = {"status": "FAIL", "failures": [{"code": "FAIL_CONTRACT_PARSE", "error": f"{type(exc).__name__}: {exc}"}]}; code = 2
    if args.output: write_json(Path(args.output), result)
    if args.markdown:
        p = Path(args.markdown); p.parent.mkdir(parents=True, exist_ok=True); p.write_text(markdown_report(result), encoding="utf-8")
    print(json.dumps({"ARCHITECTURE_GUARD": result["status"], "failures": result.get("failures", [])}, ensure_ascii=False, sort_keys=True)); return code


def cmd_snapshot(args: argparse.Namespace) -> int:
    obj = snapshot(Path(args.root)); write_json(Path(args.output), obj); print(f"snapshot_files={len(obj['files'])}"); return 0


def cmd_scope(args: argparse.Namespace) -> int:
    before = load_json(Path(args.before)); after = snapshot(Path(args.root)); patterns = parse_allowlist(Path(args.allowlist)); result = compare_scope(before, after, patterns)
    if args.output: write_json(Path(args.output), result)
    print(json.dumps(result, ensure_ascii=False, sort_keys=True)); return 0 if result["SCOPE_GUARD"] == "PASS" else 2


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Verto Architecture Guard v2"); sub = p.add_subparsers(dest="command", required=True)
    def canonical(s: argparse.ArgumentParser) -> None:
        s.add_argument("--root", default="."); s.add_argument("--contract", default=DEFAULT_CONTRACT); s.add_argument("--dependencies", default=DEFAULT_DEPENDENCIES); s.add_argument("--ownership", default=DEFAULT_OWNERSHIP); s.add_argument("--debt", default=DEFAULT_DEBT)
        s.add_argument("--rule-baseline", default=DEFAULT_RULE_BASELINE); s.add_argument("--governance-baseline", default=DEFAULT_GOVERNANCE_BASELINE); s.add_argument("--complexity-baseline", default=DEFAULT_COMPLEXITY_BASELINE); s.add_argument("--exceptions", default=DEFAULT_EXCEPTIONS); s.add_argument("--output");
    s = sub.add_parser("scan"); canonical(s); s.set_defaults(func=cmd_scan)
    s = sub.add_parser("verify"); canonical(s); s.add_argument("--markdown"); s.set_defaults(func=cmd_verify)
    s = sub.add_parser("snapshot"); s.add_argument("--root", default="."); s.add_argument("--output", required=True); s.set_defaults(func=cmd_snapshot)
    s = sub.add_parser("scope"); s.add_argument("--root", default="."); s.add_argument("--before", required=True); s.add_argument("--allowlist", required=True); s.add_argument("--output"); s.set_defaults(func=cmd_scope)
    return p


def main() -> int:
    args = build_parser().parse_args(); return args.func(args)

if __name__ == "__main__":
    sys.exit(main())
