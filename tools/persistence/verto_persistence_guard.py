#!/usr/bin/env python3
"""Deterministic persistence architecture gates for Verto Session 327.

The guard is intentionally standard-library-only. It checks contracts and source
boundaries; it never treats a report string as evidence of a passing mutation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import tempfile
from pathlib import Path
from typing import Any

DAO_IMPORT = re.compile(r"^\s*import\s+com\.verto\.app\.data\.local\.dao\.([A-Za-z0-9_]+)", re.MULTILINE)
ENTITY_IMPORT = re.compile(r"^\s*import\s+com\.verto\.app\.data\.local\.entity\.([A-Za-z0-9_*]+)", re.MULTILINE)
REPOSITORY_IMPL = re.compile(r"\b[A-Za-z_][A-Za-z0-9_]*RepositoryImpl\b")

OWNERSHIP_CONTRACT = "docs/architecture/contracts/persistence-boundaries-v325.json"
ENFORCEMENT_CONTRACT = "docs/architecture/contracts/persistence-boundary-enforcement-v327.json"


def load(root: Path, relative: str) -> dict[str, Any]:
    return json.loads((root / relative).read_text(encoding="utf-8"))


def production_files(root: Path) -> list[Path]:
    return sorted(
        p for p in root.rglob("*.kt")
        if "/src/main/" in f"/{p.relative_to(root).as_posix()}"
        and not any(x in p.parts for x in {"build", ".gradle", ".git", "generated"})
    )


def relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def public_boundary(path: str) -> bool:
    return any(token in f"/{path}" for token in ("/domain/", "/application/", "/api/", "/ui/"))


def resolve_consumer(path: str) -> str | None:
    """Resolve semantic consumer from all v327-supported source layouts."""
    parts = path.split("/")
    for index, token in enumerate(parts):
        if token != "feature":
            continue
        if index + 1 >= len(parts):
            continue
        if parts[index + 1] == "integration":
            if index + 2 < len(parts):
                return parts[index + 2]
            return None
        return parts[index + 1]
    return None


def _ownership_maps(root: Path) -> tuple[dict[str, str], dict[str, str], set[str], dict[tuple[str, str], dict[str, str]]]:
    ownership = load(root, OWNERSHIP_CONTRACT)
    enforcement = load(root, ENFORCEMENT_CONTRACT)
    governed = set(enforcement.get("stable_entry_points", {}).get("symbols", []))

    dao_owner: dict[str, str] = {}
    entity_owner: dict[str, str] = {}
    for asset in ownership.get("assets", []):
        identity = str(asset.get("identity", ""))
        owner = str(asset.get("semantic_owner", ""))
        dao = str(asset.get("dao", ""))
        if asset.get("kind") == "DAO" and identity in governed:
            dao_owner[identity] = owner
        if asset.get("kind") == "ROOM_ENTITY" and dao in governed and identity:
            entity_owner[identity] = owner

    missing = sorted(governed - set(dao_owner))
    if missing:
        raise ValueError(f"Enforced DAO symbols have no semantic owner in {OWNERSHIP_CONTRACT}: {missing}")

    infrastructure: dict[tuple[str, str], dict[str, str]] = {}
    for row in enforcement.get("infrastructure_exceptions", []):
        rp = str(row.get("path", ""))
        for symbol in row.get("symbols", []):
            infrastructure[(rp, str(symbol))] = {
                "classification": str(row.get("classification", "explicit_infrastructure")),
                "reason": str(row.get("reason", "")),
            }
    return dao_owner, entity_owner, governed, infrastructure


def _finding(code: str, path: str, symbol: str, **extra: str) -> dict[str, str]:
    finding = {"code": code, "path": path, "symbol": symbol}
    finding.update({key: value for key, value in extra.items() if value is not None})
    return finding


def scan_boundary(root: Path) -> dict[str, Any]:
    dao_owner, entity_owner, governed, infrastructure = _ownership_maps(root)

    owner_local: list[dict[str, str]] = []
    foreign_dao: list[dict[str, str]] = []
    foreign_entity: list[dict[str, str]] = []
    unclassified: list[dict[str, str]] = []
    infrastructure_access: list[dict[str, str]] = []

    # Legacy public-contract protections remain in force in addition to v327 semantic ownership.
    application_dao: list[dict[str, str]] = []
    application_entity: list[dict[str, str]] = []
    public_leaks: list[dict[str, str]] = []
    repository_bypasses: list[dict[str, str]] = []

    for path in production_files(root):
        rp = relative(path, root)
        text = path.read_text(encoding="utf-8", errors="replace")
        imported_daos = sorted(set(DAO_IMPORT.findall(text)))
        imported_entities = sorted(set(ENTITY_IMPORT.findall(text)))
        consumer = resolve_consumer(rp)

        # Stable persistence entry points are zero-tolerance everywhere in production adapters.
        for dao in sorted(set(imported_daos) & governed):
            owner = dao_owner[dao]
            exception = infrastructure.get((rp, dao))
            if exception is not None:
                infrastructure_access.append(_finding(
                    "EXPLICIT_INFRASTRUCTURE_STORAGE_ACCESS", rp, dao,
                    semantic_owner=owner,
                    classification=exception["classification"],
                    reason=exception["reason"],
                ))
            elif consumer is None:
                unclassified.append(_finding(
                    "UNCLASSIFIED_PERSISTENCE_CONSUMER", rp, dao,
                    semantic_owner=owner,
                ))
            elif consumer == owner:
                owner_local.append(_finding(
                    "OWNER_LOCAL_STORAGE_ACCESS", rp, dao,
                    consumer=consumer,
                    semantic_owner=owner,
                ))
            else:
                foreign_dao.append(_finding(
                    "FOREIGN_DIRECT_DAO_ACCESS", rp, dao,
                    consumer=consumer,
                    semantic_owner=owner,
                ))

        # Room entities remain forbidden at public cross-feature contracts. This is the
        # v325 public-contract invariant plus semantic owner classification for v327.
        if public_boundary(rp):
            for symbol in imported_entities:
                if symbol == "*":
                    application_entity.append(_finding("PUBLIC_ROOM_ENTITY_IMPORT", rp, symbol))
                    continue
                owner = entity_owner.get(symbol)
                if owner is None:
                    # Preserve the legacy rule for any Room type at a public boundary.
                    application_entity.append(_finding("PUBLIC_ROOM_ENTITY_IMPORT", rp, symbol))
                    continue
                if consumer is None:
                    unclassified.append(_finding(
                        "UNCLASSIFIED_PERSISTENCE_CONSUMER", rp, symbol,
                        semantic_owner=owner,
                    ))
                elif consumer != owner:
                    foreign_entity.append(_finding(
                        "FOREIGN_ROOM_ENTITY_ACCESS", rp, symbol,
                        consumer=consumer,
                        semantic_owner=owner,
                    ))
                else:
                    application_entity.append(_finding(
                        "PUBLIC_ROOM_ENTITY_IMPORT", rp, symbol,
                        consumer=consumer,
                        semantic_owner=owner,
                    ))

            for dao in imported_daos:
                application_dao.append({"path": rp, "symbol": dao})
            if imported_daos or imported_entities:
                public_leaks.append({"path": rp, "kind": "persistence-import"})
            if REPOSITORY_IMPL.search(text):
                repository_bypasses.append({"path": rp, "kind": "RepositoryImplementation"})

    return {
        "owner_local_storage_access": owner_local,
        "foreign_direct_dao_access": foreign_dao,
        "foreign_room_entity_access": foreign_entity,
        "unclassified_persistence_access": unclassified,
        "infrastructure_storage_access": infrastructure_access,
        "application_to_dao": application_dao,
        "application_to_room_entity": application_entity,
        "public_persistence_leaks": public_leaks,
        "repository_implementation_bypasses": repository_bypasses,
        "enforced_dao_symbols": sorted(governed),
    }


def verify_boundary(root: Path) -> dict[str, Any]:
    scan = scan_boundary(root)
    failures: list[dict[str, Any]] = []
    zero_tolerance = (
        "foreign_direct_dao_access",
        "foreign_room_entity_access",
        "unclassified_persistence_access",
    )
    for key in zero_tolerance:
        if scan[key]:
            failures.append({"code": "FAIL_PERSISTENCE_BOUNDARY", "kind": key, "findings": scan[key]})
    for key in ("application_to_dao", "application_to_room_entity", "public_persistence_leaks", "repository_implementation_bypasses"):
        if scan[key]:
            failures.append({"code": "FAIL_PERSISTENCE_BOUNDARY", "kind": key, "findings": scan[key]})
    return {
        "format": "verto-persistence-boundary-gate-v327",
        "status": "PASS" if not failures else "FAIL",
        "scan": scan,
        "counts": {
            "owner_local_storage_access": len(scan["owner_local_storage_access"]),
            "foreign_direct_dao_access": len(scan["foreign_direct_dao_access"]),
            "foreign_room_entity_access": len(scan["foreign_room_entity_access"]),
            "unclassified_persistence_access": len(scan["unclassified_persistence_access"]),
            "infrastructure_storage_access": len(scan["infrastructure_storage_access"]),
        },
        "failures": failures,
    }


def verify_ownership(root: Path) -> dict[str, Any]:
    contract = load(root, "docs/architecture/contracts/persistence-boundaries-v325.json")
    assets = contract.get("assets", [])
    seen: set[str] = set()
    duplicates: list[str] = []
    missing: list[str] = []
    invalid_paths: list[str] = []
    required = {"identity", "kind", "semantic_owner", "physical_owner_module", "readers", "writers", "repository_or_port", "dao", "transaction_boundary", "public_contract"}
    for asset in assets:
        identity = asset.get("identity", "")
        if identity in seen:
            duplicates.append(identity)
        seen.add(identity)
        if not identity or required - set(asset) or not asset.get("semantic_owner") or not asset.get("physical_owner_module"):
            missing.append(identity or "<missing-identity>")
        for evidence in asset.get("evidence_paths", []):
            if evidence and not (root / evidence).exists():
                invalid_paths.append(evidence)
    failures = []
    if duplicates or missing or invalid_paths:
        failures.append({"code": "FAIL_DATA_OWNERSHIP", "duplicates": sorted(set(duplicates)), "missing": sorted(set(missing)), "invalid_paths": sorted(set(invalid_paths))})
    return {"format": "verto-data-ownership-gate-v1", "status": "PASS" if not failures else "FAIL", "asset_count": len(assets), "duplicates": duplicates, "missing": missing, "invalid_paths": invalid_paths, "failures": failures}


def verify_transaction(root: Path) -> dict[str, Any]:
    matrix = load(root, "docs/data/PERSISTENCE_TRANSACTION_MATRIX_v326.json")
    required = {"workflow", "owner_feature", "entry_contract", "reads", "writes", "transaction_owner", "atomicity_required", "idempotency", "failure_behavior", "rollback_behavior", "characterization_test", "regression_test"}
    missing = [row.get("workflow", "<missing>") for row in matrix.get("workflows", []) if required - set(row)]
    workflows = {row.get("workflow") for row in matrix.get("workflows", [])}
    expected = {"invoice-posting", "inventory-movement", "payment-posting", "logistics-state-transition", "logistics-receiving", "logistics-cost-posting", "sync-local-write", "sync-outbox", "sync-inbox", "sync-cursor", "sync-conflict", "invoice-update-with-items", "inventory-receiving", "payment-allocation"}
    absent = sorted(expected - workflows)
    failures = []
    if missing or absent:
        failures.append({"code": "FAIL_TRANSACTION_CONTRACT", "missing_fields": missing, "missing_workflows": absent})
    return {"format": "verto-transaction-contract-gate-v1", "status": "PASS" if not failures else "FAIL", "workflow_count": len(workflows), "missing_workflows": absent, "failures": failures}


def current_schema_fingerprint(root: Path) -> tuple[int, str, list[tuple[str, str]]]:
    catalog = root / "data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt"
    match = re.search(r"ROOM_SCHEMA_VERSION\s*:\s*Int\s*=\s*(\d+)", catalog.read_text(encoding="utf-8"))
    version = int(match.group(1)) if match else -1
    rows = sorted((relative(p, root), hashlib.sha256(p.read_bytes()).hexdigest()) for p in root.glob("app/schemas/com.verto.app.data.local.AppDatabase/*.json"))
    fingerprint = hashlib.sha256("\n".join(f"{p}\0{h}" for p, h in rows).encode()).hexdigest()
    return version, fingerprint, rows


def verify_schema(root: Path) -> dict[str, Any]:
    snapshot = load(root, "docs/architecture/verification/SESSION_326_INPUT_SNAPSHOT.json")
    baseline_version = int(snapshot.get("room_schema_version", -1))
    baseline_fingerprint = str(snapshot.get("schema_fingerprint", ""))
    version, fingerprint, rows = current_schema_fingerprint(root)
    destructive = [relative(p, root) for p in root.rglob("*.kt") if "fallbackToDestructiveMigration" in p.read_text(encoding="utf-8", errors="replace")]
    failures: list[dict[str, Any]] = []

    if destructive:
        failures.append({"code": "FAIL_DESTRUCTIVE_MIGRATION_FALLBACK", "paths": destructive})
    if version < baseline_version:
        failures.append({"code": "FAIL_SCHEMA_VERSION_REGRESSION", "baseline": baseline_version, "actual": version})
    elif version == baseline_version:
        if fingerprint != baseline_fingerprint:
            failures.append({"code": "FAIL_MIGRATION_SCHEMA", "room_version_expected": baseline_version, "room_version_actual": version, "schema_fingerprint_expected": baseline_fingerprint, "schema_fingerprint_actual": fingerprint})
    else:
        catalog_path = root / "data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt"
        catalog_text = catalog_path.read_text(encoding="utf-8", errors="replace")
        migration_sources = list((root / "data/database/src/main/kotlin/com/verto/app/data/local").glob("AppDatabaseMigrations*.kt"))
        migration_text = "\n".join(path.read_text(encoding="utf-8", errors="replace") for path in migration_sources)
        android_tests = list((root / "data/database/src/androidTest").rglob("*.kt")) if (root / "data/database/src/androidTest").exists() else []
        test_text = "\n".join(path.read_text(encoding="utf-8", errors="replace") for path in android_tests)
        missing_edges: list[dict[str, Any]] = []
        for from_version in range(baseline_version, version):
            to_version = from_version + 1
            symbol = f"MIGRATION_{from_version}_{to_version}"
            declaration = re.search(rf"\b{re.escape(symbol)}\b[\s\S]{{0,500}}Migration\(\s*{from_version}\s*,\s*{to_version}\s*\)", migration_text) is not None
            registered = re.search(rf"\b{re.escape(symbol)}\b", catalog_text) is not None
            tested = re.search(rf"\b{re.escape(symbol)}\b", test_text) is not None
            if not (declaration and registered and tested):
                missing_edges.append({"from": from_version, "to": to_version, "symbol": symbol, "declared": declaration, "registered": registered, "migration_test": tested})
        if missing_edges:
            failures.append({"code": "FAIL_MIGRATION_PATH_EVIDENCE", "edges": missing_edges})

        export_path = root / f"app/schemas/com.verto.app.data.local.AppDatabase/{version}.json"
        if not export_path.is_file():
            failures.append({"code": "BLOCKED_SCHEMA_EXPORT_MISSING", "room_version": version, "expected_export": relative(export_path, root), "detail": "Room compiler export is required; manual schema artifacts are not accepted."})
        else:
            try:
                exported = json.loads(export_path.read_text(encoding="utf-8"))
                exported_version = int(exported.get("database", {}).get("version", -1))
            except Exception as error:
                exported_version = -1
                failures.append({"code": "FAIL_SCHEMA_EXPORT_INVALID", "path": relative(export_path, root), "detail": str(error)})
            if exported_version != version:
                failures.append({"code": "FAIL_SCHEMA_EXPORT_VERSION", "expected": version, "actual": exported_version, "path": relative(export_path, root)})
        if fingerprint == baseline_fingerprint:
            failures.append({"code": "BLOCKED_SCHEMA_FINGERPRINT_NOT_ADVANCED", "baseline_version": baseline_version, "room_version": version, "detail": "Expected a generated schema export for the advanced Room version."})

    only_environment_blocks = bool(failures) and all(str(row.get("code", "")).startswith("BLOCKED_") for row in failures)
    status = "PASS" if not failures else ("BLOCKED_ENVIRONMENT" if only_environment_blocks else "FAIL")
    return {
        "format": "verto-migration-schema-gate-v2",
        "status": status,
        "baseline_room_version": baseline_version,
        "room_version": version,
        "schema_fingerprint": fingerprint,
        "schema_files": rows,
        "destructive_fallbacks": destructive,
        "failures": failures,
    }


def verify_ratchet(root: Path) -> dict[str, Any]:
    ratchet = load(root, "docs/quality/persistence-ratchet-v326.json")
    dao_root = root / "data/database/src/main/kotlin/com/verto/app/data/local/dao"
    current = {name: len((dao_root / name).read_text(encoding="utf-8").splitlines()) for name in ratchet.get("dao_limits", {}) if (dao_root / name).exists()}
    failures = [{"code": "FAIL_PERSISTENCE_RATCHET", "identity": name, "limit": limit, "actual": current.get(name)} for name, limit in ratchet.get("dao_limits", {}).items() if current.get(name, 0) > limit]
    return {"format": "verto-persistence-ratchet-gate-v1", "status": "PASS" if not failures else "FAIL", "current_dao_lines": current, "failures": failures}


def _copy_guard_contracts(source_root: Path, target_root: Path) -> None:
    for rel in (OWNERSHIP_CONTRACT, ENFORCEMENT_CONTRACT):
        src = source_root / rel
        dst = target_root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_bytes(src.read_bytes())


def _write_mutation(root: Path, relative_path: str, body: str) -> None:
    path = root / relative_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding="utf-8")


def _mutation_case(source_root: Path, relative_path: str, body: str) -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="verto-persistence-v327-") as temp:
        root = Path(temp)
        _copy_guard_contracts(source_root, root)
        _write_mutation(root, relative_path, body)
        return verify_boundary(root)


def self_test(root: Path) -> dict[str, Any]:
    cases: dict[str, dict[str, Any]] = {}

    a = _mutation_case(
        root,
        "feature/party/src/main/kotlin/com/verto/app/feature/party/data/Fake.kt",
        "package com.verto.app.feature.party.data\nimport com.verto.app.data.local.dao.InvoiceDao\n",
    )
    cases["mutation_foreign_feature_data_adapter"] = {
        "status": "PASS" if a["status"] == "FAIL" and any(
            x.get("code") == "FOREIGN_DIRECT_DAO_ACCESS" for x in a["scan"]["foreign_direct_dao_access"]
        ) else "FAIL"
    }

    b = _mutation_case(
        root,
        "app/src/main/kotlin/com/verto/app/feature/commission/bridge/Fake.kt",
        "package com.verto.app.feature.commission.bridge\nimport com.verto.app.data.local.dao.PaymentDao\n",
    )
    cases["mutation_foreign_app_bridge"] = {
        "status": "PASS" if b["status"] == "FAIL" and b["scan"]["foreign_direct_dao_access"] else "FAIL"
    }

    c = _mutation_case(
        root,
        "data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/Fake.kt",
        "package com.verto.app.feature.reports.bridge\nimport com.verto.app.data.local.dao.InvoiceDao\n",
    )
    cases["mutation_foreign_data_operations"] = {
        "status": "PASS" if c["status"] == "FAIL" and c["scan"]["foreign_direct_dao_access"] else "FAIL"
    }

    d = _mutation_case(
        root,
        "feature/party/src/main/kotlin/com/verto/app/feature/party/application/Fake.kt",
        "package com.verto.app.feature.party.application\nimport com.verto.app.data.local.entity.InvoiceEntity\n",
    )
    cases["mutation_foreign_room_entity"] = {
        "status": "PASS" if d["status"] == "FAIL" and any(
            x.get("code") == "FOREIGN_ROOM_ENTITY_ACCESS" for x in d["scan"]["foreign_room_entity_access"]
        ) else "FAIL"
    }

    e = _mutation_case(
        root,
        "feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/Fake.kt",
        "package com.verto.app.feature.inventory.data\nimport com.verto.app.data.local.dao.InventoryDao\n",
    )
    cases["mutation_owner_local_dao"] = {
        "status": "PASS" if e["status"] == "PASS" and len(e["scan"]["owner_local_storage_access"]) == 1 else "FAIL"
    }

    f = _mutation_case(
        root,
        "misc/src/main/kotlin/com/verto/app/misc/Fake.kt",
        "package com.verto.app.misc\nimport com.verto.app.data.local.dao.InvoiceDao\n",
    )
    cases["mutation_unclassified_consumer"] = {
        "status": "PASS" if f["status"] == "FAIL" and any(
            x.get("code") == "UNCLASSIFIED_PERSISTENCE_CONSUMER" for x in f["scan"]["unclassified_persistence_access"]
        ) else "FAIL"
    }

    # Baseline regression uses the same production scan. It is not a ratchet: target is absolute zero.
    baseline = verify_boundary(root)
    enforcement = load(root, ENFORCEMENT_CONTRACT)
    governed = set(enforcement.get("stable_entry_points", {}).get("symbols", []))
    offender_imports: list[dict[str, str]] = []
    for rp in enforcement.get("historical_offender_files", []):
        path = root / rp
        if not path.exists():
            continue
        imports = set(DAO_IMPORT.findall(path.read_text(encoding="utf-8", errors="replace"))) & governed
        consumer = resolve_consumer(rp)
        dao_owner, _, _, _ = _ownership_maps(root)
        for symbol in sorted(imports):
            if consumer != dao_owner.get(symbol):
                offender_imports.append({"path": rp, "symbol": symbol})

    status = "PASS" if all(row["status"] == "PASS" for row in cases.values()) and baseline["status"] == "PASS" and not offender_imports else "FAIL"
    return {
        "format": "verto-persistence-guard-self-test-v327",
        "status": status,
        "mutation_tests": cases,
        "production_baseline": {
            "status": baseline["status"],
            "foreign_direct_dao_access": baseline["counts"]["foreign_direct_dao_access"],
            "foreign_room_entity_access": baseline["counts"]["foreign_room_entity_access"],
            "unclassified_persistence_access": baseline["counts"]["unclassified_persistence_access"],
            "historical_offender_imports": offender_imports,
        },
    }


def verify_all(root: Path) -> dict[str, Any]:
    results = {"boundary": verify_boundary(root), "ownership": verify_ownership(root), "transaction": verify_transaction(root), "schema": verify_schema(root), "ratchet": verify_ratchet(root)}
    failures = {key: value for key, value in results.items() if value.get("status") != "PASS"}
    return {"format": "verto-persistence-gate-v1", "status": "PASS" if not failures else "FAIL", "results": results, "failures": failures}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["verify", "verify-boundary", "verify-ownership", "verify-transaction", "verify-schema", "verify-ratchet", "self-test"])
    parser.add_argument("--root", default=".")
    parser.add_argument("--output")
    args = parser.parse_args(); root = Path(args.root).resolve()
    handlers = {"verify": verify_all, "verify-boundary": verify_boundary, "verify-ownership": verify_ownership, "verify-transaction": verify_transaction, "verify-schema": verify_schema, "verify-ratchet": verify_ratchet, "self-test": self_test}
    result = handlers[args.command](root)
    if args.output:
        out = Path(args.output); out.parent.mkdir(parents=True, exist_ok=True); out.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0 if result.get("status") == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
