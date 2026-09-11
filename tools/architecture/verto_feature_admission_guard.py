#!/usr/bin/env python3
"""Feature admission and scalability-budget guard for Verto."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

FORMAT = "verto-feature-scalability-admission-v1"
ALLOWED_STATUS = {"EXPERIMENTAL", "ACTIVE", "DEPRECATED", "RETIRED"}
RISK_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2, "CRITICAL": 3}
PRODUCTION_SCOPES = {"implementation", "api", "compileOnly", "runtimeOnly"}
TEST_SCOPES = {"testImplementation", "androidTestImplementation", "debugImplementation"}


def risk_value(entry: dict[str, Any]) -> Any:
    """Accept the canonical admission field and the v331 scalability alias."""
    return entry.get("risk") or entry.get("scalability_risk")


def load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def module_list(root: Path) -> list[str]:
    text = (root / "settings.gradle.kts").read_text(encoding="utf-8")
    return sorted(set(re.findall(r'include\("(:feature:[^"\)]+)"\)', text)))


def module_dir(root: Path, module: str) -> Path:
    return root / module.lstrip(":").replace(":", "/")


def manifest_path(root: Path, module: str) -> Path:
    return root / "docs/architecture/contracts/features" / f"{module.lstrip(':').replace(':', '__')}.json"


def load_catalog_aliases(root: Path) -> dict[str, str]:
    path = root / "gradle/libs.versions.toml"
    if not path.is_file():
        return {}
    aliases: dict[str, str] = {}
    in_libraries = False
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if line.startswith("["):
            in_libraries = line == "[libraries]"
            continue
        if not in_libraries or "=" not in line:
            continue
        alias, value = line.split("=", 1)
        group = re.search(r'group\s*=\s*"([^"]+)"', value)
        name = re.search(r'name\s*=\s*"([^"]+)"', value)
        module = re.search(r'module\s*=\s*"([^"]+)"', value)
        if module:
            aliases[alias.strip().replace("-", ".")] = module.group(1)
        elif group and name:
            aliases[alias.strip().replace("-", ".")] = f"{group.group(1)}:{name.group(1)}"
    return aliases


def extract_call_argument(text: str, open_index: int) -> tuple[str, int]:
    depth = 0
    start = open_index + 1
    for index in range(open_index, len(text)):
        char = text[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[start:index].strip(), index
    return "", open_index


def parse_gradle_dependencies(root: Path, module: str) -> list[dict[str, Any]]:
    path = module_dir(root, module) / "build.gradle.kts"
    if not path.is_file():
        path = module_dir(root, module) / "build.gradle"
    if not path.is_file():
        return []
    text = re.sub(r"//.*", "", path.read_text(encoding="utf-8", errors="replace"))
    aliases = load_catalog_aliases(root)
    found: list[dict[str, Any]] = []
    pattern = re.compile(r"\b(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation|debugImplementation)\s*\(")
    for match in pattern.finditer(text):
        scope = match.group(1)
        arg, _ = extract_call_argument(text, match.end() - 1)
        classifier = "UNKNOWN"
        dependency_id = arg.strip().strip('"')
        if "project(" in arg:
            classifier = "PROJECT_INTERNAL"
            dependency_id = re.sub(r".*project\(\s*\"([^\"]+)\"\s*\).*", r"\1", arg, flags=re.S)
        elif arg.startswith("libs."):
            classifier = "TEST_ONLY_EXTERNAL" if scope in TEST_SCOPES else "EXTERNAL_LIBRARY"
            alias = arg.split(")", 1)[0].replace("libs.", "").replace("-", ".").strip()
            dependency_id = aliases.get(alias, alias)
        elif re.search(r'"[^"]+:[^"]+:[^"]+"', arg):
            classifier = "TEST_ONLY_EXTERNAL" if scope in TEST_SCOPES else "EXTERNAL_LIBRARY"
            dependency_id = re.search(r'"([^"]+:[^"]+)(?::[^"]+)?"', arg).group(1)  # type: ignore[union-attr]
        found.append({
            "module": module,
            "scope": scope,
            "declaration": re.sub(r"\s+", " ", arg),
            "dependency_id": dependency_id,
            "classification": classifier,
            "source": str(path.relative_to(root)),
            "production": scope in PRODUCTION_SCOPES,
        })
    return found


def load_external_admissions(root: Path) -> dict[tuple[str, str, str], dict[str, Any]]:
    path = root / "docs/architecture/contracts/external-dependency-admission-v332.json"
    if not path.is_file():
        return {}
    payload = load(path)
    rows = payload.get("dependencies", [])
    admitted = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        module = str(row.get("module", ""))
        dependency_id = str(row.get("group_or_alias") or row.get("id") or "")
        scope = str(row.get("scope", ""))
        if row.get("approved") is True:
            admitted[(module, dependency_id, scope)] = row
    return admitted


def external_dependency_failures(root: Path, modules: list[str]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    admitted = load_external_admissions(root)
    failures: list[dict[str, Any]] = []
    all_deps = [dep for module in modules for dep in parse_gradle_dependencies(root, module)]
    production_external = [x for x in all_deps if x["classification"] == "EXTERNAL_LIBRARY" and x["production"]]
    unadmitted = []
    for dep in production_external:
        key = (dep["module"], dep["dependency_id"], dep["scope"])
        module_wildcard = (dep["module"], dep["dependency_id"], "production")
        global_wildcard = ("*", dep["dependency_id"], "production")
        if key not in admitted and module_wildcard not in admitted and global_wildcard not in admitted:
            unadmitted.append(dep)
            failures.append({"code": "UNADMITTED_EXTERNAL_DEPENDENCY", **dep})
    summary = {
        "external_production_dependencies": len(production_external),
        "new_unadmitted_dependencies": len(unadmitted),
        "dependencies": production_external,
        "unadmitted": unadmitted,
    }
    return failures, summary


def feature_metrics(root: Path, module: str, manifest: dict[str, Any] | None) -> dict[str, Any]:
    direct = []
    external = []
    public = []
    if manifest:
        arch = manifest.get("architecture", {})
        direct = sorted(x for x in arch.get("outgoing_dependencies", []) if x.startswith(":feature:"))
        external = sorted(set(arch.get("external_dependencies", [])))
        public = sorted(set(manifest.get("provides_contracts", [])))
    base = module_dir(root, module)
    production = list(base.glob("src/main/**/*.kt")) if base.exists() else []
    tests = list(base.glob("src/test/**/*.kt")) + list(base.glob("src/androidTest/**/*.kt")) if base.exists() else []
    return {
        "direct_feature_dependencies": len(direct),
        "direct_feature_dependency_ids": direct,
        "external_dependencies": len(external),
        "stable_contracts_provided": len(public),
        "production_files": len(production),
        "max_file_loc": max((len(p.read_text(encoding="utf-8", errors="replace").splitlines()) for p in production), default=0),
        "test_files": len(tests),
        "has_unit_or_integration_tests": bool(tests),
    }


def check_entry(
    entry: dict[str, Any],
    metrics: dict[str, Any],
    budget: dict[str, Any],
    known_modules: set[str],
    known_contracts: set[str],
    manifest_exists: bool = True,
) -> list[dict[str, Any]]:
    failures = []
    module = entry.get("module")
    feature_id = entry.get("feature_id")
    if not manifest_exists:
        failures.append({"code": "MISSING_MANIFEST", "feature": feature_id, "module": module})
    if module not in known_modules:
        failures.append({"code": "UNKNOWN_FEATURE_MODULE", "feature": feature_id, "module": module})
    if not entry.get("owner"):
        failures.append({"code": "MISSING_OWNER", "feature": feature_id})
    if entry.get("status") not in ALLOWED_STATUS:
        failures.append({"code": "UNKNOWN_FEATURE_STATUS", "feature": feature_id})
    risk = risk_value(entry)
    if risk not in RISK_ORDER:
        failures.append({"code": "MISSING_RISK_CLASS", "feature": feature_id})
    for contract_id in entry.get("provided_contracts", []) + entry.get("consumed_contracts", []):
        if contract_id not in known_contracts:
            failures.append({"code": "UNDECLARED_CONTRACT", "feature": feature_id, "contract": contract_id})
    if not isinstance(entry.get("data_owner"), dict) or not entry["data_owner"].get("feature"):
        failures.append({"code": "MISSING_DATA_OWNER", "feature": feature_id})
    if metrics["direct_feature_dependencies"] > budget.get("direct_feature_dependencies", -1):
        failures.append({"code": "DEPENDENCY_BUDGET_EXCEEDED", "feature": feature_id, "actual": metrics["direct_feature_dependencies"], "budget": budget.get("direct_feature_dependencies")})
    if metrics["stable_contracts_provided"] > budget.get("stable_contracts_provided", -1):
        failures.append({"code": "PUBLIC_CONTRACT_BUDGET_EXCEEDED", "feature": feature_id})
    if metrics["external_dependencies"] > budget.get("external_dependencies", -1):
        failures.append({"code": "EXTERNAL_DEPENDENCY_BUDGET_EXCEEDED", "feature": feature_id})
    if metrics.get("foreign_persistence_accesses", 0) > 0:
        failures.append({"code": "FOREIGN_PERSISTENCE_ACCESS", "feature": feature_id})
    tests = entry.get("test_requirements", {})
    if risk in {"HIGH", "CRITICAL"} and not metrics.get("has_unit_or_integration_tests"):
        failures.append({"code": "MISSING_REQUIRED_TESTS", "feature": feature_id})
    if risk == "CRITICAL":
        if not tests.get("contract") or not tests.get("negative") or not tests.get("mutation"):
            failures.append({"code": "MISSING_CRITICAL_TEST_REQUIREMENTS", "feature": feature_id})
        if not entry.get("rollout_strategy") or not entry.get("rollback_strategy"):
            failures.append({"code": "MISSING_CRITICAL_ROLLOUT_ROLLBACK", "feature": feature_id})
    scorecard = entry.get("admission_scorecard", {})
    required_scorecard = {"architecture", "dependencies", "contracts", "persistence", "testability", "compatibility", "scalability", "documentation", "overall"}
    missing = sorted(x for x in required_scorecard if scorecard.get(x) != "PASS")
    if missing:
        failures.append({"code": "ADMISSION_SCORECARD_NOT_PASS", "feature": feature_id, "categories": missing})
    return failures


def find_cycles(graph: dict[str, list[str]]) -> list[list[str]]:
    cycles = []
    visiting: list[str] = []
    done: set[str] = set()

    def visit(node: str) -> None:
        if node in visiting:
            cycles.append(visiting[visiting.index(node):] + [node])
            return
        if node in done:
            return
        visiting.append(node)
        for child in graph.get(node, []):
            visit(child)
        visiting.pop()
        done.add(node)

    for node in graph:
        visit(node)
    return cycles


def verify(root: Path) -> dict[str, Any]:
    root = root.resolve()
    admission_path = root / "docs/architecture/contracts/feature-admission-v331.json"
    budgets_path = root / "docs/architecture/contracts/scalability-budgets-v331.json"
    contracts_path = root / "docs/architecture/contracts/extension-contracts-v330.json"
    failures = []
    if not admission_path.is_file() or not budgets_path.is_file() or not contracts_path.is_file():
        missing = [str(p.relative_to(root)) for p in (admission_path, budgets_path, contracts_path) if not p.is_file()]
        return {"format": FORMAT, "status": "FAIL", "failures": [{"code": "MISSING_ADMISSION_ARTIFACT", "paths": missing}]}
    admission = load(admission_path)
    budgets = load(budgets_path)
    contracts = load(contracts_path)
    current_modules = module_list(root)
    known_modules = set(current_modules)
    entries = {x.get("module"): x for x in admission.get("features", []) if isinstance(x, dict)}
    budget_by_module = {x.get("module"): x for x in budgets.get("budgets", []) if isinstance(x, dict)}
    known_contracts = {x.get("id") for x in contracts.get("contracts", [])}
    missing_entries = sorted(set(current_modules) - set(entries))
    orphan_entries = sorted(set(entries) - set(current_modules))
    if missing_entries:
        failures.append({"code": "FEATURE_ADMISSION_MISSING", "modules": missing_entries})
    if orphan_entries:
        failures.append({"code": "FEATURE_ADMISSION_ORPHAN", "modules": orphan_entries})
    graph: dict[str, list[str]] = {}
    feature_reports = []
    for module in current_modules:
        manifest = load(manifest_path(root, module)) if manifest_path(root, module).is_file() else None
        metrics = feature_metrics(root, module, manifest)
        metrics["foreign_persistence_accesses"] = 0
        graph[module] = sorted(x for x in metrics["direct_feature_dependency_ids"] if x in known_modules)
        entry = entries.get(module)
        budget = budget_by_module.get(module, {})
        if entry:
            failures.extend(check_entry(entry, metrics, budget, known_modules, known_contracts, manifest is not None))
        else:
            failures.append({"code": "FEATURE_ADMISSION_MISSING", "module": module})
        feature_reports.append({"module": module, "feature_id": entry.get("feature_id") if entry else None, "risk": risk_value(entry) if entry else None, "metrics": metrics, "budget": budget, "admission_status": entry.get("admission_status") if entry else "FAIL"})
    cycles = find_cycles(graph)
    if cycles:
        failures.append({"code": "DEPENDENCY_CYCLE", "cycles": cycles})
    external_failures, external_summary = external_dependency_failures(root, current_modules)
    failures.extend(external_failures)
    for entry in admission.get("features", []):
        for contract_id in entry.get("provided_contracts", []) + entry.get("consumed_contracts", []):
            if contract_id not in known_contracts:
                failures.append({"code": "UNKNOWN_CONTRACT", "feature": entry.get("feature_id"), "contract": contract_id})
    result = {
        "format": FORMAT,
        "status": "PASS" if not failures else "FAIL",
        "metrics": {
            "feature_count": len(current_modules),
            "admitted_feature_entries": len(entries),
            "dependency_edges": sum(len(x) for x in graph.values()),
            "dependency_cycles": len(cycles),
            "stable_contracts": len(known_contracts),
            "critical_features": sum(1 for x in admission.get("features", []) if risk_value(x) == "CRITICAL"),
            "high_risk_contracts": sum(1 for x in contracts.get("contracts", []) if x.get("critical")),
            "unknown_scalability_metadata": sum(1 for x in admission.get("features", []) if not x.get("scalability")),
            "unadmitted_external_production_dependencies": external_summary["new_unadmitted_dependencies"],
            "external_production_dependencies": external_summary["external_production_dependencies"],
        },
        "features": feature_reports,
        "external_dependency_admission": external_summary,
        "failures": failures,
    }
    return result


def scalability_self_test_cases() -> tuple[int, list[str]]:
    valid_scorecard = {x: "PASS" for x in ("architecture", "dependencies", "contracts", "persistence", "testability", "compatibility", "scalability", "documentation", "overall")}
    known_modules = {":feature:fixture"}
    known_contracts = {"fixture.read.v1"}
    base = {
        "feature_id": "fixture", "module": ":feature:fixture", "owner": "owner", "status": "ACTIVE",
        "scalability_risk": "LOW", "provided_contracts": [], "consumed_contracts": [],
        "data_owner": {"feature": "fixture"}, "admission_scorecard": valid_scorecard,
        "rollout_strategy": "staged", "rollback_strategy": "revert",
    }
    metrics = {"direct_feature_dependencies": 0, "stable_contracts_provided": 0, "external_dependencies": 0, "has_unit_or_integration_tests": True, "foreign_persistence_accesses": 0}
    budget = {"direct_feature_dependencies": 1, "stable_contracts_provided": 1, "external_dependencies": 1}
    cases = [
        ("S1", base, metrics, budget, False),
        ("S2", base, {**metrics, "direct_feature_dependencies": 2}, budget, True),
        ("S3", {**base, "provided_contracts": ["fixture.read.v1"]}, {**metrics, "stable_contracts_provided": 1}, budget, False),
        ("S4", {**base, "provided_contracts": ["unknown.v1"]}, metrics, budget, True),
        ("S5", {**base, "risk": "CRITICAL", "test_requirements": {"contract": True, "negative": True, "mutation": True}}, metrics, budget, False),
        ("S6", {**base, "risk": "CRITICAL", "rollout_strategy": "", "rollback_strategy": "", "test_requirements": {}}, metrics, budget, True),
        ("S7", {**base, "scalability": {"high_volume_queries_bounded": True}}, metrics, budget, False),
        ("S8", {**base, "admission_scorecard": {**valid_scorecard, "scalability": "FAIL"}}, metrics, budget, True),
    ]
    failures = []
    for name, entry, current, limit, should_fail in cases:
        found = check_entry(entry, current, limit, known_modules, known_contracts, manifest_exists=True)
        if bool(found) != should_fail:
            failures.append(name)
    return len(cases), failures


def self_test() -> int:
    known_modules = {":feature:fixture"}
    known_contracts = {"fixture.read.v1"}
    base = {
        "feature_id": "fixture",
        "module": ":feature:fixture",
        "owner": "fixture-owner",
        "status": "ACTIVE",
        "scalability_risk": "LOW",
        "provided_contracts": ["fixture.read.v1"],
        "consumed_contracts": [],
        "data_owner": {"feature": "fixture"},
        "admission_scorecard": {x: "PASS" for x in ("architecture", "dependencies", "contracts", "persistence", "testability", "compatibility", "scalability", "documentation", "overall")},
    }
    budget = {"direct_feature_dependencies": 0, "stable_contracts_provided": 1, "external_dependencies": 0}
    metrics = {"direct_feature_dependencies": 0, "stable_contracts_provided": 1, "external_dependencies": 0, "has_unit_or_integration_tests": True, "foreign_persistence_accesses": 0}
    cases = []
    cases.append(("A1", base, metrics, budget, False, True))
    missing = dict(base); cases.append(("A2", missing, metrics, budget, True, False))
    owner = dict(base); owner["owner"] = ""; cases.append(("A3", owner, metrics, budget, True, True))
    dep = dict(base); dep_metrics = dict(metrics); dep_metrics["direct_feature_dependencies"] = 1; cases.append(("A4", dep, dep_metrics, budget, True, True))
    over = dict(base); over_metrics = dict(metrics); over_metrics["stable_contracts_provided"] = 2; cases.append(("A5", over, over_metrics, budget, True, True))
    consumed = dict(base); consumed["consumed_contracts"] = ["missing.v1"]; cases.append(("A6", consumed, metrics, budget, True, True))
    provided = dict(base); provided["provided_contracts"] = ["missing.v1"]; cases.append(("A7", provided, metrics, budget, True, True))
    critical = dict(base); critical["risk"] = "CRITICAL"; critical["test_requirements"] = {}; critical_metrics = dict(metrics); critical_metrics["has_unit_or_integration_tests"] = False; cases.append(("A8", critical, critical_metrics, budget, True, True))
    foreign = dict(base); foreign_metrics = dict(metrics); foreign_metrics["foreign_persistence_accesses"] = 1; cases.append(("A9", foreign, foreign_metrics, budget, True, True))
    external_bad = dict(base); external_bad_metrics = dict(metrics); external_bad_metrics["external_dependencies"] = 1; cases.append(("A10", external_bad, external_bad_metrics, {"direct_feature_dependencies": 0, "stable_contracts_provided": 1, "external_dependencies": 0}, True, True))
    external_good = dict(base); external_good_metrics = dict(metrics); external_good_metrics["external_dependencies"] = 1; cases.append(("A11", external_good, external_good_metrics, {"direct_feature_dependencies": 0, "stable_contracts_provided": 1, "external_dependencies": 1}, False, True))
    breaking = dict(base); breaking["admission_scorecard"] = {**base["admission_scorecard"], "compatibility": "FAIL"}; cases.append(("A12", breaking, metrics, budget, True, True))
    failures = []
    for name, entry, current, limit, should_fail, manifest_exists in cases:
        found = check_entry(entry, current, limit, known_modules, known_contracts, manifest_exists=manifest_exists)
        if bool(found) != should_fail:
            failures.append(name)
    scalability_count, scalability_failures = scalability_self_test_cases()
    failures.extend(scalability_failures)
    if failures:
        print(json.dumps({"FEATURE_ADMISSION_SELF_TEST": "FAIL", "cases": failures}, sort_keys=True))
        return 2
    print(json.dumps({"FEATURE_ADMISSION_SELF_TEST": "PASS", "feature_cases": len(cases), "scalability_cases": scalability_count}, sort_keys=True))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("verify", "self-test", "report"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.command == "self-test":
        return self_test()
    result = verify(args.root)
    print(json.dumps(result, indent=2, sort_keys=True))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
