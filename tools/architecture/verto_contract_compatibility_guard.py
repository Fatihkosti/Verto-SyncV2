#!/usr/bin/env python3
"""Compatibility guard for Verto's explicit cross-boundary contracts."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import tempfile
from pathlib import Path
from typing import Any

FORMAT = "verto-contract-compatibility-v1"
REGISTRY = "docs/architecture/contracts/extension-contracts-v330.json"
SNAPSHOT = "docs/architecture/contracts/contract-api-snapshot-v332.json"
TEST_REGISTRY = "docs/architecture/contracts/contract-test-registry-v333.json"
COMPATIBLE = {"BACKWARD_COMPATIBLE", "EXACT_VERSION", "INTERNAL_ONLY"}
TYPES = {"INTERNAL_PRIVATE", "FEATURE_PUBLIC", "CROSS_FEATURE_STABLE", "INTEGRATION_STABLE", "DEPRECATED"}


def load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def modules(root: Path) -> set[str]:
    text = (root / "settings.gradle.kts").read_text(encoding="utf-8")
    return set(re.findall(r'include\("(:[^"\)]+)"\)', text)) | {":app"}


def source_for(root: Path, entry: dict[str, Any]) -> list[Path]:
    evidence = entry.get("evidence")
    if isinstance(evidence, str) and (root / evidence).is_file():
        return [root / evidence]
    return []


def symbol_simple(symbol: str) -> str:
    return symbol.rsplit(".", 1)[-1]


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//.*", "", text)


def find_matching(text: str, start: int, opener: str, closer: str) -> int:
    depth = 0
    in_string: str | None = None
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == in_string:
                in_string = None
            continue
        if char in {'"', "'"}:
            in_string = char
        elif char == opener:
            depth += 1
        elif char == closer:
            depth -= 1
            if depth == 0:
                return index
    return -1


def split_top_level(value: str, separator: str = ",") -> list[str]:
    parts: list[str] = []
    start = 0
    depth = 0
    for index, char in enumerate(value):
        if char in "([{<":
            depth += 1
        elif char in ")]}>":
            depth = max(0, depth - 1)
        elif char == separator and depth == 0:
            item = value[start:index].strip()
            if item:
                parts.append(item)
            start = index + 1
    item = value[start:].strip()
    if item:
        parts.append(item)
    return parts


def normalize_type(value: str) -> str:
    value = value.strip().rstrip(",")
    value = re.sub(r"\s+", " ", value)
    value = re.sub(r"\s*([<>,?:()])\s*", r"\1", value)
    return value


def parse_parameters(params: str) -> list[dict[str, Any]]:
    parsed: list[dict[str, Any]] = []
    for raw in split_top_level(params):
        raw = re.sub(r"^(?:vararg|crossinline|noinline)\s+", "", raw.strip())
        raw = re.sub(r"^(?:val|var)\s+", "", raw)
        if ":" not in raw:
            continue
        name, rest = raw.split(":", 1)
        default = "=" in rest
        type_part = split_top_level(rest, "=")[0] if default else rest
        parsed.append({
            "name": name.strip(),
            "type": normalize_type(type_part),
            "nullable": "?" in normalize_type(type_part),
            "has_default": default,
        })
    return parsed


def parse_functions(body: str) -> list[dict[str, Any]]:
    functions: list[dict[str, Any]] = []
    pattern = re.compile(r"\b(?P<suspend>suspend\s+)?fun\s+(?P<name>\w+)\s*\(", re.M)
    for match in pattern.finditer(body):
        open_index = body.find("(", match.end() - 1)
        close_index = find_matching(body, open_index, "(", ")")
        if close_index < 0:
            continue
        tail = body[close_index + 1:]
        return_match = re.match(r"\s*:\s*([^\n={]+(?:<[^>\n]+>)?\??)", tail)
        return_type = normalize_type(return_match.group(1)) if return_match else "Unit"
        params = parse_parameters(body[open_index + 1:close_index])
        functions.append({
            "name": match.group("name"),
            "parameters": params,
            "parameter_count": len(params),
            "return_type": return_type,
            "suspend": bool(match.group("suspend")),
            "signature": normalized_function_signature(match.group("name"), params, return_type, bool(match.group("suspend"))),
        })
    return sorted(functions, key=lambda x: (x["name"], x["signature"]))


def normalized_function_signature(name: str, params: list[dict[str, Any]], return_type: str, suspend: bool) -> str:
    prefix = "suspend " if suspend else ""
    encoded = ",".join(f"{p['name']}:{p['type']}:{'default' if p['has_default'] else 'required'}" for p in params)
    return f"{prefix}fun {name}({encoded}):{return_type}"


def parse_data_class_fields(body: str, simple: str) -> list[dict[str, Any]]:
    match = re.search(rf"\bdata\s+class\s+{re.escape(simple)}\s*\(", body)
    if not match:
        return []
    open_index = body.find("(", match.end() - 1)
    close_index = find_matching(body, open_index, "(", ")")
    if close_index < 0:
        return []
    fields = []
    for param in parse_parameters(body[open_index + 1:close_index]):
        fields.append({
            "name": param["name"],
            "type": param["type"],
            "nullable": param["nullable"],
            "has_default": param["has_default"],
            "required": not param["nullable"] and not param["has_default"],
        })
    return sorted(fields, key=lambda x: x["name"])


def api_shape(root: Path, entry: dict[str, Any]) -> dict[str, Any]:
    symbol = entry["symbol"]
    simple = symbol_simple(symbol)
    paths = source_for(root, entry)
    if not paths:
        return {"symbol": symbol, "found": False, "methods": [], "fields": []}
    text = strip_comments(paths[0].read_text(encoding="utf-8", errors="replace"))
    declaration = re.search(rf"\b(interface|class|data class|sealed interface|sealed class)\s+{re.escape(simple)}\b", text)
    if not declaration:
        return {"symbol": symbol, "found": False, "methods": [], "fields": []}
    start = declaration.start()
    brace = text.find("{", start)
    if brace >= 0:
        end = find_matching(text, brace, "{", "}")
        body = text[start:] if end < 0 else text[start:end + 1]
    else:
        body = text[start:text.find("\n\n", start) if text.find("\n\n", start) > 0 else len(text)]
    functions = parse_functions(body)
    fields = parse_data_class_fields(body, simple)
    return {
        "symbol": symbol,
        "found": True,
        "declaration_kind": declaration.group(1),
        "source": str(paths[0].relative_to(root)),
        "functions": functions,
        "methods": [x["signature"] for x in functions],
        "fields": fields,
        "field_names": [x["name"] for x in fields],
        "source_sha256": hashlib.sha256(paths[0].read_bytes()).hexdigest(),
    }


def current_snapshot(root: Path, registry: dict[str, Any]) -> dict[str, Any]:
    contracts = registry.get("contracts", [])
    return {
        "format": "verto-contract-api-snapshot-v1",
        "session": 330,
        "contracts": {
            entry["id"]: {
                "id": entry["id"],
                "version": entry["version"],
                "status": entry["status"],
                "contract_type": entry["contract_type"],
                "owner_module": entry["owner_module"],
                "compatibility": entry["compatibility"],
                **api_shape(root, entry),
            }
            for entry in contracts
        },
    }


def diff_contracts(before: dict[str, Any], after: dict[str, Any]) -> list[dict[str, Any]]:
    changes = []
    old = before.get("contracts", {})
    new = after.get("contracts", {})
    for contract_id in sorted(set(old) | set(new)):
        if contract_id not in old:
            changes.append({"id": contract_id, "classification": "NEW_VERSION"})
            continue
        if contract_id not in new:
            changes.append({"id": contract_id, "classification": "BREAKING", "reason": "active contract removed"})
            continue
        a, b = old[contract_id], new[contract_id]
        if a.get("version") != b.get("version"):
            changes.append({"id": contract_id, "classification": "NEW_VERSION"})
            continue
        old_methods, new_methods = set(a.get("methods", [])), set(b.get("methods", []))
        old_fields = {x["name"]: x for x in a.get("fields", []) if isinstance(x, dict)}
        new_fields = {x["name"]: x for x in b.get("fields", []) if isinstance(x, dict)}
        if old_methods != new_methods:
            changes.append({"id": contract_id, "classification": "BREAKING", "reason": "method surface changed", "removed": sorted(old_methods - new_methods), "added": sorted(new_methods - old_methods)})
        elif set(old_fields) - set(new_fields):
            changes.append({"id": contract_id, "classification": "BREAKING", "reason": "required DTO field removed", "removed": sorted(set(old_fields) - set(new_fields))})
        elif any(old_fields[name].get("type") != new_fields.get(name, {}).get("type") for name in old_fields if name in new_fields):
            changes.append({"id": contract_id, "classification": "BREAKING", "reason": "DTO field type changed"})
        elif any(new_fields[name].get("required") and name not in old_fields for name in new_fields):
            changes.append({"id": contract_id, "classification": "BREAKING", "reason": "required DTO field added"})
        elif set(new_fields) - set(old_fields):
            changes.append({"id": contract_id, "classification": "COMPATIBLE", "reason": "DTO field added"})
        else:
            changes.append({"id": contract_id, "classification": "UNCHANGED"})
    return changes


def load_contract_test_registry(root: Path, registry: dict[str, Any]) -> list[dict[str, Any]]:
    path = root / TEST_REGISTRY
    if path.is_file():
        payload = load(path)
        return [x for x in payload.get("tests", []) if isinstance(x, dict)]
    rows = []
    for entry in registry.get("contracts", []):
        if isinstance(entry.get("contract_test"), str):
            rows.append({
                "contract_id": entry["id"],
                "contract_symbol": symbol_simple(entry["symbol"]),
                "test_file": entry["contract_test"],
                "test_symbol": Path(entry["contract_test"]).stem,
                "coverage_mode": "DIRECT_CONTRACT",
                "status": "ACTIVE",
            })
    return rows


SEMANTIC_ASSERTION_FORBIDDEN = {"exists", "loaded", "referenced", "present", "symbol", "symbol_present", "reflection"}
ASSERTION_RE = re.compile(r"\b(?:assert(?:Equals|True|False|Null|NotNull|Throws|Fails)|check|require)\s*\(")


def production_implementation_linked(root: Path, implementation_symbol: str, contract_symbol: str) -> bool:
    class_re = re.compile(rf"\bclass\s+{re.escape(implementation_symbol)}\b")
    contract_re = re.compile(rf"[: ,]\s*{re.escape(contract_symbol)}\b")
    for path in root.rglob("*.kt"):
        rel = path.relative_to(root).as_posix()
        if "/src/main/" not in rel:
            continue
        text = strip_comments(path.read_text(encoding="utf-8", errors="replace"))
        if class_re.search(text) and contract_re.search(text):
            return True
    return False


def semantic_test_failures_for_row(
    root: Path,
    entry: dict[str, Any],
    row: dict[str, Any],
) -> list[dict[str, Any]]:
    failures: list[dict[str, Any]] = []
    cid = entry.get("id")
    path = root / str(row.get("test_file", ""))
    if not path.is_file():
        return [{"code": "MISSING_CONTRACT_TEST_EVIDENCE", "id": cid, "path": row.get("test_file")}]
    text = strip_comments(path.read_text(encoding="utf-8", errors="replace"))
    test_symbol = row.get("test_symbol")
    contract_symbol = str(row.get("contract_symbol") or symbol_simple(str(entry.get("symbol", ""))))
    implementation_symbol = row.get("implementation_symbol")
    assertions = row.get("semantic_assertions")

    if not isinstance(test_symbol, str) or not test_symbol or not re.search(rf"\b{re.escape(test_symbol)}\b", text):
        failures.append({"code": "MISSING_TEST_SYMBOL", "id": cid, "test_symbol": test_symbol})
    if not isinstance(implementation_symbol, str) or not implementation_symbol:
        failures.append({"code": "MISSING_IMPLEMENTATION_LINKAGE", "id": cid})
    elif not production_implementation_linked(root, implementation_symbol, contract_symbol):
        failures.append({"code": "INVALID_IMPLEMENTATION_LINKAGE", "id": cid, "implementation_symbol": implementation_symbol, "contract_symbol": contract_symbol})

    if not isinstance(assertions, list) or not assertions or any(not isinstance(x, str) or not x.strip() for x in assertions):
        failures.append({"code": "MISSING_SEMANTIC_ASSERTIONS", "id": cid})
    else:
        vague = sorted({str(x).strip().lower() for x in assertions} & SEMANTIC_ASSERTION_FORBIDDEN)
        if vague:
            failures.append({"code": "VAGUE_SEMANTIC_ASSERTIONS", "id": cid, "values": vague})

    typed_contract = bool(re.search(rf"(?::|<)\s*{re.escape(contract_symbol)}\b", text))
    shape = api_shape(root, entry)
    method_names = [str(x.get("name")) for x in shape.get("functions", []) if isinstance(x, dict) and x.get("name")]
    invoked = sorted({name for name in method_names if re.search(rf"\.\s*{re.escape(name)}\s*\(", text)})
    has_assertion = bool(ASSERTION_RE.search(text))
    reflection_only_markers = bool(re.search(rf"{re.escape(contract_symbol)}\s*::\s*class|\.java\.simpleName", text))

    if not typed_contract:
        failures.append({"code": "CONTRACT_NOT_EXECUTED_AS_TYPE", "id": cid, "contract_symbol": contract_symbol})
    if not invoked:
        failures.append({"code": "NO_CONTRACT_BEHAVIOR_EXECUTION", "id": cid, "contract_symbol": contract_symbol})
    if not has_assertion:
        failures.append({"code": "MISSING_SEMANTIC_BEHAVIOR_ASSERTION", "id": cid})
    if reflection_only_markers and not invoked:
        failures.append({"code": "SYMBOL_ONLY_CONTRACT_TEST", "id": cid})
    if row.get("coverage_mode") not in {"DIRECT_CONTRACT", "PRODUCTION_ADAPTER_CONFORMANCE"}:
        failures.append({"code": "UNKNOWN_CONTRACT_TEST_COVERAGE_MODE", "id": cid})
    return failures


def validate_contract_tests(root: Path, registry: dict[str, Any]) -> list[dict[str, Any]]:
    failures: list[dict[str, Any]] = []
    contracts = {x.get("id"): x for x in registry.get("contracts", []) if isinstance(x, dict)}
    test_rows = load_contract_test_registry(root, registry)
    rows_by_contract = {x.get("contract_id"): x for x in test_rows if x.get("status") == "ACTIVE"}
    for entry in registry.get("contracts", []):
        if not entry.get("critical"):
            continue
        row = rows_by_contract.get(entry.get("id"))
        if not row:
            failures.append({"code": "CRITICAL_CONTRACT_MISSING_VALID_TEST", "id": entry.get("id")})
            continue
        failures.extend(semantic_test_failures_for_row(root, entry, row))
    for row in test_rows:
        if row.get("contract_id") not in contracts:
            failures.append({"code": "CONTRACT_TEST_UNKNOWN_CONTRACT", "id": row.get("contract_id")})
    return failures


def validate_registry(root: Path, registry: dict[str, Any]) -> list[dict[str, Any]]:
    failures = []
    seen: set[str] = set()
    available = modules(root)
    for entry in registry.get("contracts", []):
        cid = entry.get("id")
        if cid in seen:
            failures.append({"code": "DUPLICATE_CONTRACT_ID", "id": cid})
        seen.add(cid)
        if not isinstance(cid, str) or not re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)*\.v[0-9]+", cid):
            failures.append({"code": "INVALID_CONTRACT_ID", "id": cid})
        if entry.get("contract_type") not in TYPES:
            failures.append({"code": "UNKNOWN_CONTRACT_TYPE", "id": cid})
        if entry.get("compatibility") not in COMPATIBLE:
            failures.append({"code": "UNKNOWN_COMPATIBILITY", "id": cid})
        owner_module = entry.get("owner_module")
        if owner_module not in available:
            failures.append({"code": "UNKNOWN_PROVIDER", "id": cid, "module": owner_module})
        if not entry.get("version"):
            failures.append({"code": "UNVERSIONED_CONTRACT", "id": cid})
        if not source_for(root, entry):
            failures.append({"code": "MISSING_PROVIDER_EVIDENCE", "id": cid, "path": entry.get("evidence")})
        if entry.get("status") == "DEPRECATED" and not entry.get("replacement"):
            failures.append({"code": "DEPRECATED_CONTRACT_WITHOUT_REPLACEMENT", "id": cid})
        for consumer in entry.get("consumers", []):
            if consumer not in available:
                failures.append({"code": "UNKNOWN_CONSUMER", "id": cid, "module": consumer})
        if entry.get("critical") and not isinstance(entry.get("contract_test"), str):
            failures.append({"code": "MISSING_CRITICAL_CONTRACT_TEST", "id": cid})
        if isinstance(entry.get("contract_test"), str) and not (root / entry["contract_test"]).is_file():
            failures.append({"code": "MISSING_CONTRACT_TEST_EVIDENCE", "id": cid, "path": entry["contract_test"]})
    return failures


def validate_declarations(root: Path, registry: dict[str, Any]) -> list[dict[str, Any]]:
    """Validate provider/consumer declarations using the same production path."""
    failures = []
    for entry in registry.get("contracts", []):
        cid = entry.get("id")
        owner_manifest = root / str(entry.get("owner_manifest", ""))
        if not owner_manifest.is_file():
            failures.append({"code": "PROVIDER_UNDECLARED", "id": cid, "module": entry.get("owner_module")})
        else:
            manifest = load(owner_manifest)
            if cid not in manifest.get("provides_contracts", []):
                failures.append({"code": "PROVIDER_UNDECLARED", "id": cid, "module": entry.get("owner_module")})
        for consumer in entry.get("consumers", []):
            if consumer == ":app":
                continue
            suffix = consumer.lstrip(":").replace(":", "__")
            path = root / "docs/architecture/contracts/features" / f"{suffix}.json"
            if not path.is_file() or cid not in load(path).get("consumes_contracts", []):
                failures.append({"code": "CONSUMER_UNDECLARED", "id": cid, "module": consumer})
    return failures


def validate_declarations_from_metadata(entry: dict[str, Any], owner_contracts: list[str], consumer_contracts: list[str]) -> list[dict[str, Any]]:
    failures = []
    cid = entry.get("id")
    if cid not in owner_contracts:
        failures.append({"code": "PROVIDER_UNDECLARED", "id": cid})
    if entry.get("consumers") and cid not in consumer_contracts:
        failures.append({"code": "CONSUMER_UNDECLARED", "id": cid})
    if entry.get("contract_type") == "INTERNAL_ONLY" and entry.get("consumers"):
        failures.append({"code": "INTERNAL_ONLY_FOREIGN_CONSUMER", "id": cid})
    return failures


def verify(root: Path) -> dict[str, Any]:
    root = root.resolve()
    failures = []
    registry_path = root / REGISTRY
    snapshot_path = root / SNAPSHOT
    if not registry_path.is_file():
        failures.append({"code": "MISSING_REGISTRY", "path": REGISTRY})
        registry = {"contracts": []}
    else:
        registry = load(registry_path)
    failures.extend(validate_registry(root, registry))
    failures.extend(validate_declarations(root, registry))
    failures.extend(validate_contract_tests(root, registry))
    current = current_snapshot(root, registry)
    if not snapshot_path.is_file():
        failures.append({"code": "MISSING_API_SNAPSHOT", "path": SNAPSHOT})
        changes = []
    else:
        baseline = load(snapshot_path)
        changes = diff_contracts(baseline, current)
        failures.extend({"code": "BREAKING_ACTIVE_CONTRACT", **change} for change in changes if change["classification"] == "BREAKING")
    critical = [x for x in registry.get("contracts", []) if x.get("critical")]
    usage = []
    for entry in registry.get("contracts", []):
        simple = symbol_simple(entry["symbol"])
        consumer_files = []
        for consumer in entry.get("consumers", []):
            prefix = root / ("app/src" if consumer == ":app" else consumer.lstrip(":").replace(":", "/") + "/src")
            if prefix.exists() and any(simple in p.read_text(encoding="utf-8", errors="replace") for p in prefix.rglob("*.kt")):
                consumer_files.append(consumer)
        usage.append({"id": entry["id"], "owner": entry["owner_module"], "symbol": entry["symbol"], "version": entry["version"], "consumers": consumer_files, "declared_consumers": entry.get("consumers", [])})
        for consumer in entry.get("consumers", []):
            if consumer not in consumer_files:
                failures.append({"code": "UNKNOWN_STABLE_CONSUMER", "id": entry["id"], "module": consumer})
    result = {
        "format": FORMAT,
        "status": "PASS" if not failures else "FAIL",
        "metrics": {
            "contracts_discovered": len(registry.get("contracts", [])),
            "unversioned_cross_feature_contracts": sum(1 for x in registry.get("contracts", []) if x.get("contract_type") in {"CROSS_FEATURE_STABLE", "INTEGRATION_STABLE"} and not x.get("version")),
            "unknown_consumers": sum(1 for x in failures if x.get("code") in {"UNKNOWN_CONSUMER", "UNKNOWN_STABLE_CONSUMER"}),
            "unknown_providers": sum(1 for x in failures if x.get("code") == "UNKNOWN_PROVIDER"),
            "breaking_active_contracts": sum(1 for x in failures if x.get("code") == "BREAKING_ACTIVE_CONTRACT"),
            "invalid_contract_test_evidence": sum(1 for x in failures if x.get("code") in {
                "INVALID_CONTRACT_TEST_EVIDENCE", "MISSING_TEST_SYMBOL", "CRITICAL_CONTRACT_MISSING_VALID_TEST",
                "MISSING_IMPLEMENTATION_LINKAGE", "INVALID_IMPLEMENTATION_LINKAGE", "MISSING_SEMANTIC_ASSERTIONS",
                "VAGUE_SEMANTIC_ASSERTIONS", "CONTRACT_NOT_EXECUTED_AS_TYPE", "NO_CONTRACT_BEHAVIOR_EXECUTION",
                "MISSING_SEMANTIC_BEHAVIOR_ASSERTION", "SYMBOL_ONLY_CONTRACT_TEST"
            }),
            "critical_contracts": len(critical),
            "critical_contracts_with_semantic_conformance": len(critical) - len({x.get("id") for x in failures if x.get("id") in {c.get("id") for c in critical} and x.get("code") in {
                "MISSING_TEST_SYMBOL", "CRITICAL_CONTRACT_MISSING_VALID_TEST", "MISSING_IMPLEMENTATION_LINKAGE",
                "INVALID_IMPLEMENTATION_LINKAGE", "MISSING_SEMANTIC_ASSERTIONS", "VAGUE_SEMANTIC_ASSERTIONS",
                "CONTRACT_NOT_EXECUTED_AS_TYPE", "NO_CONTRACT_BEHAVIOR_EXECUTION", "MISSING_SEMANTIC_BEHAVIOR_ASSERTION",
                "SYMBOL_ONLY_CONTRACT_TEST", "MISSING_CONTRACT_TEST_EVIDENCE"
            }}),
            "symbol_only_contract_tests_counted": 0,
        },
        "changes": changes,
        "usage": usage,
        "failures": failures,
        "current_snapshot": current,
    }
    return result


def self_test() -> int:
    base = {"contracts": {"x.v1": {"id": "x.v1", "version": "1", "methods": ["fun load(clientId:String:required,from:Long?:required):Report"], "fields": [{"name": "id", "type": "String", "required": True}]}}}
    cases = [
        ("P1", {"contracts": {"x.v1": {"id": "x.v1", "version": "1", "methods": ["fun load(clientId:Int:required,from:Long?:required):Report"], "fields": [{"name": "id", "type": "String", "required": True}]}}}, True),
        ("P2", {"contracts": {"x.v1": {"id": "x.v1", "version": "1", "methods": ["fun load(clientId:String:required,from:Long:required):Report"], "fields": [{"name": "id", "type": "String", "required": True}]}}}, True),
        ("P3", {"contracts": {"x.v1": {"id": "x.v1", "version": "1", "methods": ["fun load(clientId:String:required,from:Long?:required):OtherReport"], "fields": [{"name": "id", "type": "String", "required": True}]}}}, True),
        ("P4", {"contracts": {"x.v1": {"id": "x.v1", "version": "1", "methods": ["fun load(clientId:String:required,from:Long?:required):Report"], "fields": []}}}, True),
        ("P5", {"contracts": {"x.v1": {"id": "x.v1", "version": "1", "methods": ["fun load(clientId:String:required,from:Long?:required):Report"], "fields": [{"name": "id", "type": "String", "required": True}, {"name": "note", "type": "String?", "required": False}]}}}, False),
        ("P6", {"contracts": {"x.v1": base["contracts"]["x.v1"]}}, False),
        ("C7", {"contracts": {"x.v1": base["contracts"]["x.v1"]}}, False),
        ("C8", {"contracts": {"x.v1": base["contracts"]["x.v1"], "x.v2": {"id": "x.v2", "version": "2", "methods": ["fun load(clientId:String:required,from:Long?:required):Report"], "fields": []}}}, False),
    ]
    undetected = []
    for name, after, should_break in cases:
        changed = diff_contracts(base, after)
        breaking = any(x["classification"] == "BREAKING" for x in changed)
        if name == "C7":
            entry = {"id": "x.v1", "owner_manifest": "owner.json", "owner_module": ":feature:owner", "consumers": [":feature:consumer"], "contract_type": "CROSS_FEATURE_STABLE"}
            findings = validate_declarations_from_metadata(entry, ["x.v1"], [])
            if not findings:
                undetected.append(name)
        elif breaking != should_break:
            undetected.append(name)
    # Session 333 false-evidence regression: a symbol/reflection-only test must never count.
    with tempfile.TemporaryDirectory(prefix="verto-contract-evidence-selftest-") as td:
        fixture_root = Path(td)
        fake_test = fixture_root / "SymbolOnlyContractTest.kt"
        fake_test.write_text(
            "class SymbolOnlyContractTest { fun evidence() { assertNotNull(PartyDirectoryGateway::class.java.simpleName) } }\n",
            encoding="utf-8",
        )
        fake_entry = {"id": "party.lookup.v1", "symbol": "x.PartyDirectoryGateway", "evidence": "missing"}
        fake_row = {
            "contract_id": "party.lookup.v1", "contract_symbol": "PartyDirectoryGateway",
            "implementation_symbol": "ClientRepository", "test_file": "SymbolOnlyContractTest.kt",
            "test_symbol": "SymbolOnlyContractTest", "semantic_assertions": ["exists"],
            "coverage_mode": "DIRECT_CONTRACT", "status": "ACTIVE",
        }
        evidence_findings = semantic_test_failures_for_row(fixture_root, fake_entry, fake_row)
        evidence_codes = {x.get("code") for x in evidence_findings}
        required_codes = {"NO_CONTRACT_BEHAVIOR_EXECUTION", "SYMBOL_ONLY_CONTRACT_TEST", "VAGUE_SEMANTIC_ASSERTIONS"}
        if not required_codes.issubset(evidence_codes):
            undetected.append("E9_SYMBOL_ONLY_EVIDENCE")
    if undetected:
        print(json.dumps({"CONTRACT_COMPATIBILITY_SELF_TEST": "FAIL", "undetected": undetected}, sort_keys=True))
        return 2
    print(json.dumps({
        "CONTRACT_COMPATIBILITY_SELF_TEST": "PASS",
        "compatibility_mutations_detected": 8,
        "false_evidence_regression": "PASS",
        "self_tests": 9,
    }, sort_keys=True))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("verify", "self-test", "report", "snapshot"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.command == "self-test":
        return self_test()
    if args.command == "snapshot":
        root = args.root.resolve()
        registry = load(root / REGISTRY)
        result = current_snapshot(root, registry)
        output = args.output or (root / SNAPSHOT)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(json.dumps({"CONTRACT_API_SNAPSHOT": "WRITTEN", "path": str(output)}, sort_keys=True))
        return 0
    result = verify(args.root)
    print(json.dumps(result, indent=2, sort_keys=True))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
