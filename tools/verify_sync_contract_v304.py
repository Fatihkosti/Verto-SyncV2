#!/usr/bin/env python3
"""Static verifier for Session 304 unified sync contract. Python stdlib only."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import zipfile
from pathlib import Path

SOURCE_SHA = "6fddd8d485cdbdbe7b7331f8b342739b0543ef7e4e99057f41bb4fbb099a5e6b"
SOURCE_ENTRIES = 1651
PROD_COUNT = 1153
PROD_MANIFEST = "05963e2a9817f6c2aeb803212f74f09c3783d5c6fc4beef45a009a06012aaa00"
SYNC_COUNT = 67
SYNC_MANIFEST = "410e5119396f99e988a917bcf1ca5e1f2ba3aa13656c230790a9735bbfd6c046"
ROOM_VERSION = 77
ALLOWED_PROD_ADDITIONS = {
    "data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt",
    "data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt",
}
ALLOWED_TEST_ADDITIONS = {
    "data/network/src/test/kotlin/com/verto/app/data/sync/UnifiedSyncContractV304Test.kt",
    "data/network/src/test/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistryV304Test.kt",
}
REQUIRED_TOP = {
    "schemaVersion", "contractFamily", "contractVersion", "revisionPolicy", "cursorPolicy", "scopePolicy",
    "bootstrapPolicy", "transactionPolicy", "idempotencyPolicy", "errorTypes", "conflictPolicies",
    "deletePolicies", "attachmentPolicies", "retention", "budgets", "aggregateRegistry", "reconciliation",
    "baselineReferences",
}
ERROR_TYPES = ["TRANSIENT", "AUTH", "VALIDATION", "CONFLICT", "CURSOR_EXPIRED", "CONTRACT_UNSUPPORTED"]
CONFLICT_POLICIES = [
    "OPTIMISTIC_VERSION", "APPEND_ONLY_IDEMPOTENT", "SEMANTIC_COMMAND", "SERVER_STATE_MACHINE",
    "IMMUTABLE_REVISION", "SERVER_AUTHORITATIVE", "EXPLICIT_SCOPED_LWW", "BRIDGE_EXISTING_STRONGER_CONTRACT",
]
DELETE_POLICIES = ["VERSIONED_DELETE", "TOMBSTONE", "ARCHIVE", "VOID_OR_REVERSE", "CANCEL_STATE_TRANSITION", "NO_CLIENT_DELETE", "SERVER_OWNED"]
ATTACHMENT_POLICIES = ["NONE", "METADATA_ONLY_EXTERNAL_BINARY"]
COVERAGE_HEADERS = [
    "aggregate_id", "participant_key", "current_producer", "producer_kind", "local_tables",
    "current_outbox_or_dirty_mechanism", "current_server_tables_or_rpc", "delete_path", "payload_version",
    "conflict_policy", "delete_policy", "attachment_policy", "current_cursor_kind", "migration_owner_session",
    "coverage_status", "evidence_path", "evidence_symbol", "notes",
]
PRODUCER_KINDS = {
    "UI_REPOSITORY", "DOMAIN_USE_CASE", "IMPORT", "BACKGROUND_JOB", "LEGACY_SYNC", "ADMIN_TOOL", "DEMO_TOOL",
    "SERVER_DERIVED", "READ_ONLY", "BRIDGE",
}
COVERAGE_STATUSES = {
    "VERIFIED_CURRENT", "VERIFIED_STRONGER_EXISTING_CONTRACT", "LEGACY_TIMESTAMP_PATH", "LEGACY_DIRTY_PATH",
    "LEGACY_DELETE_QUEUE", "INERT_BEHIND_GATE", "SERVER_REFERENCE_ONLY", "READ_ONLY", "LOCAL_ONLY", "DEFERRED_WITH_OWNER",
}
REQUIRED_SERVER_PATHS = {
    "purchase_orders", "purchase_order_lines", "goods_receipts", "goods_receipt_lines", "purchase_invoice_matches",
    "purchase_invoice_match_lines", "purchase_invoice_receipt_allocations", "purchase_payment_overrides", "purchase_cycle_attachments",
    "invoices", "invoice_items", "payments", "payment_allocations", "realized_fx_events", "client_credits",
    "inventory_items", "inventory_units", "categories", "item_categories", "cost_allocations",
    "inventory_apply_commands_v2", "inventory_apply_cost_revisions_v2", "inventory_pull_movements_v2",
    "inventory_pull_cost_revisions_v2", "inventory_prepare_reconciliation_batch_v2",
    "expenses", "budgets", "cash_register", "cash_register_movements", "cash_reconciliation_sessions", "cash_denominations", "commission_payments",
    "clients", "notes", "client_reminders", "verto_sync_snapshot_state", "get_my_notifications_cache", "organization_settings",
    "educational_topics", "educational_topic_targets",
}
LOGISTICS_TABLES = {
    "logistics_shipments", "logistics_shipment_sources", "logistics_shipment_lines", "logistics_milestones",
    "logistics_assignments", "logistics_events", "logistics_partners", "logistics_shipment_legs", "logistics_custody_handoffs",
    "logistics_shipment_partner_links", "logistics_transport_details", "logistics_documents", "logistics_costs", "logistics_payments",
    "logistics_route_templates", "logistics_route_template_stops", "logistics_receiving_batches", "logistics_receiving_lines",
    "logistics_inventory_postings", "logistics_cost_allocations", "logistics_shortages", "logistics_recoveries",
    "logistics_recovery_lines", "logistics_recovery_postings",
}

class PolicyFailure(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message); self.code = code

class ToolError(Exception):
    pass

def sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def sha_file(path: Path) -> str:
    return sha_bytes(path.read_bytes())

def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as e:
        raise ToolError(f"missing input: {path}") from e
    except (OSError, json.JSONDecodeError, UnicodeError) as e:
        raise ToolError(f"malformed/unreadable JSON: {path}: {e}") from e

def require(condition: bool, code: str, message: str):
    if not condition:
        raise PolicyFailure(code, message)

def manifest_hash(file_hashes: dict[str, str]) -> str:
    payload = "".join(f"{p}\0{h}\n" for p, h in sorted(file_hashes.items()))
    return sha_bytes(payload.encode("utf-8"))

def current_production_kotlin(root: Path) -> dict[str, str]:
    result = {}
    for p in root.rglob("*.kt"):
        rel = p.relative_to(root).as_posix()
        if "/src/main/kotlin/" in "/" + rel:
            result[rel] = sha_file(p)
    return result

def current_test_sources(root: Path) -> dict[str, str]:
    result = {}
    for p in root.rglob("*"):
        if not p.is_file() or p.suffix not in {".kt", ".java"}: continue
        rel = p.relative_to(root).as_posix()
        if "/src/test/" in "/" + rel or "/src/androidTest/" in "/" + rel:
            result[rel] = sha_file(p)
    return result

def current_sql(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): sha_file(p) for p in sorted(root.rglob("*.sql")) if p.is_file()}

def parse_enum(source: str, enum_name: str) -> list[str]:
    m = re.search(rf"enum\s+class\s+{re.escape(enum_name)}\s*\{{(.*?)\}}", source, re.S)
    if not m: raise PolicyFailure("FAIL_CONTRACT_RECONCILIATION", f"enum not found: {enum_name}")
    body = re.sub(r"//.*", "", m.group(1))
    return [x.strip() for x in body.split(",") if x.strip()]

def extract_record_calls(source: str) -> list[str]:
    start = source.find("val all:")
    end = source.find(").sortedBy", start)
    if start < 0 or end < 0: raise PolicyFailure("FAIL_CONTRACT_RECONCILIATION", "registry list not found")
    text = source[start:end]
    out=[]; i=0
    while True:
        j=text.find("record(", i)
        if j<0: break
        k=j+len("record("); depth=1; in_str=False; esc=False
        while k<len(text) and depth:
            ch=text[k]
            if in_str:
                if esc: esc=False
                elif ch=='\\': esc=True
                elif ch=='"': in_str=False
            else:
                if ch=='"': in_str=True
                elif ch=='(': depth+=1
                elif ch==')': depth-=1
            k+=1
        if depth: raise PolicyFailure("FAIL_CONTRACT_RECONCILIATION", "unterminated registry record")
        out.append(text[j:k]); i=k
    return out

def parse_registry_kotlin(source: str) -> list[dict]:
    records=[]
    head_re = re.compile(r'record\(\s*"([A-Z][A-Z0-9_]*)"\s*,\s*(\d+)\s*,\s*UnifiedSyncConflictPolicy\.([A-Z_]+)\s*,\s*UnifiedSyncDeletePolicy\.([A-Z_]+)', re.S)
    for call in extract_record_calls(source):
        m=head_re.search(call)
        if not m: raise PolicyFailure("FAIL_CONTRACT_RECONCILIATION", f"cannot parse registry call: {call[:100]}")
        attachment = re.search(r'attachment\s*=\s*UnifiedSyncAttachmentPolicy\.([A-Z_]+)', call)
        sensitivity = re.search(r'sensitivity\s*=\s*UnifiedSyncFinancialSensitivity\.([A-Z_]+)', call)
        records.append({
            "id": m.group(1), "payloadVersion": int(m.group(2)), "conflictPolicy": m.group(3), "deletePolicy": m.group(4),
            "attachmentPolicy": attachment.group(1) if attachment else "NONE",
            "financialSensitivity": sensitivity.group(1) if sensitivity else "NONE",
        })
    return sorted(records, key=lambda x:x["id"])

def load_required(root: Path):
    paths = {
        "baseline": root/"docs/sync/VERTO_SYNC_BASELINE_v303.json",
        "contract": root/"docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json",
        "coverage": root/"docs/sync/VERTO_SYNC_AGGREGATE_COVERAGE_v304.csv",
        "contractKt": root/"data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt",
        "registryKt": root/"data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt",
    }
    for p in paths.values():
        if not p.is_file(): raise ToolError(f"missing input: {p}")
    baseline=load_json(paths["baseline"]); contract=load_json(paths["contract"])
    try:
        with paths["coverage"].open(newline="", encoding="utf-8") as f:
            reader=csv.DictReader(f); rows=list(reader); headers=reader.fieldnames or []
    except (OSError, UnicodeError, csv.Error) as e: raise ToolError(f"malformed/unreadable CSV: {e}") from e
    try:
        ckt=paths["contractKt"].read_text(encoding="utf-8"); rkt=paths["registryKt"].read_text(encoding="utf-8")
    except (OSError, UnicodeError) as e: raise ToolError(f"unreadable Kotlin input: {e}") from e
    return paths,baseline,contract,headers,rows,ckt,rkt

def verify_identity(root: Path, baseline: dict, source_zip: Path|None):
    require(baseline.get("sourceZipSha256")==SOURCE_SHA, "BLOCKED_INPUT_DRIFT", "recorded source SHA mismatch")
    require(baseline.get("archiveEntries")==SOURCE_ENTRIES, "BLOCKED_INPUT_DRIFT", "recorded archive entry count mismatch")
    require(baseline.get("productionKotlinCount")==PROD_COUNT, "BLOCKED_EXTRACTED_SOURCE_DRIFT", "baseline production count mismatch")
    require(baseline.get("productionManifestSha256")==PROD_MANIFEST, "BLOCKED_EXTRACTED_SOURCE_DRIFT", "recorded production manifest mismatch")
    require(baseline.get("syncFocusedCount")==SYNC_COUNT, "BLOCKED_SYNC_BASELINE_DRIFT", "recorded sync-focused count mismatch")
    require(baseline.get("syncFocusedManifestSha256")==SYNC_MANIFEST, "BLOCKED_SYNC_BASELINE_DRIFT", "recorded sync-focused manifest mismatch")
    if source_zip:
        if not source_zip.is_file(): raise ToolError(f"source zip not found: {source_zip}")
        require(sha_file(source_zip)==SOURCE_SHA, "BLOCKED_INPUT_DRIFT", "source ZIP SHA mismatch")
        try:
            with zipfile.ZipFile(source_zip) as z: count=len(z.infolist())
        except (OSError, zipfile.BadZipFile) as e: raise ToolError(f"cannot inspect source ZIP: {e}") from e
        require(count==SOURCE_ENTRIES, "BLOCKED_INPUT_DRIFT", "source ZIP entry count mismatch")
    base_prod=baseline.get("productionFileHashes")
    if not isinstance(base_prod,dict) or len(base_prod)!=PROD_COUNT: raise ToolError("baseline production hash map malformed")
    require(manifest_hash(base_prod)==PROD_MANIFEST, "BLOCKED_EXTRACTED_SOURCE_DRIFT", "baseline production map fingerprint mismatch")
    current=current_production_kotlin(root)
    for rel,expected in base_prod.items():
        require(rel in current, "FAIL_SCOPE_VIOLATION", f"pre-existing production file missing: {rel}")
        require(current[rel]==expected, "FAIL_SCOPE_VIOLATION", f"pre-existing production file changed: {rel}")
    additions=set(current)-set(base_prod)
    require(additions==ALLOWED_PROD_ADDITIONS, "FAIL_SCOPE_VIOLATION", f"unexpected production additions: {sorted(additions)}")
    sync_map=baseline.get("syncFocusedFileHashes")
    if not isinstance(sync_map,dict) or len(sync_map)!=SYNC_COUNT: raise ToolError("sync-focused baseline map malformed")
    for rel,expected in sync_map.items():
        require(current.get(rel)==expected, "BLOCKED_SYNC_BASELINE_DRIFT", f"sync-focused file drift: {rel}")
    # The supplied contract pins a sync-focused aggregate fingerprint without exposing its original selection/hash recipe.
    # We therefore validate the exact pinned fingerprint field plus byte identity of all 67 recorded members.
    for rel,expected in baseline.get("criticalFileHashes",{}).items():
        p=root/rel
        require(p.is_file() and sha_file(p)==expected, "FAIL_SCOPE_VIOLATION", f"critical protected file drift: {rel}")
    mig=(root/"data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt").read_text(encoding="utf-8")
    m=re.search(r"ROOM_SCHEMA_VERSION\s*:\s*Int\s*=\s*(\d+)",mig)
    require(bool(m) and int(m.group(1))==ROOM_VERSION, "FAIL_SCOPE_VIOLATION", "Room schema version drift")
    return {"preExistingProduction":len(base_prod),"approvedProductionAdditions":sorted(additions),"syncFocusedProtected":len(sync_map),"roomVersion":ROOM_VERSION}

def verify_contract(contract: dict, ckt: str, rkt: str):
    missing=sorted(REQUIRED_TOP-set(contract))
    require(not missing,"FAIL_CONTRACT_RECONCILIATION",f"missing top-level contract fields: {missing}")
    require(contract["contractFamily"]=="verto-unified-sync" and contract["contractVersion"]==1,"FAIL_CONTRACT_RECONCILIATION","contract identity mismatch")
    require(contract["errorTypes"]==ERROR_TYPES,"FAIL_CONTRACT_RECONCILIATION","error enum ordering/value mismatch")
    require(contract["conflictPolicies"]==CONFLICT_POLICIES,"FAIL_CONTRACT_RECONCILIATION","conflict policy enum mismatch")
    require(contract["deletePolicies"]==DELETE_POLICIES,"FAIL_CONTRACT_RECONCILIATION","delete policy enum mismatch")
    require(contract["attachmentPolicies"]==ATTACHMENT_POLICIES,"FAIL_CONTRACT_RECONCILIATION","attachment policy enum mismatch")
    require(contract["revisionPolicy"].get("gapsAllowed") is True and contract["revisionPolicy"].get("gaplessRequired") is False,"FAIL_GAPLESS_REVISION_POLICY","revision gaps must be accepted")
    require(contract["revisionPolicy"].get("timeFieldsAreMetadataOnly") is True,"FAIL_TIMESTAMP_CURSOR_POLICY","time must be metadata only")
    require(contract["cursorPolicy"].get("timestampCursorForbidden") is True,"FAIL_TIMESTAMP_CURSOR_POLICY","timestamp cursor not forbidden")
    require(contract["cursorPolicy"].get("filteredPullAdvancesGlobalCursor") is False,"FAIL_TARGETED_CURSOR_POLICY","filtered pull may advance global cursor")
    require(contract["bootstrapPolicy"].get("gapFreeRequired") is True and contract["bootstrapPolicy"].get("independentPostSnapshotMaxRevisionForbidden") is True,"FAIL_BOOTSTRAP_HANDOFF_POLICY","bootstrap handoff not gap-free")
    require(contract["transactionPolicy"].get("pageSplitForbidden") is True and contract["transactionPolicy"].get("roomCommitSplitForbidden") is True,"FAIL_TRANSACTION_GROUP_POLICY","transaction group split not forbidden")
    records=contract["aggregateRegistry"]
    require(isinstance(records,list) and len(records)>=34,"FAIL_AGGREGATE_COVERAGE","registry has fewer than 34 aggregates")
    ids=[r.get("id") for r in records]
    require(ids==sorted(ids),"FAIL_CONTRACT_RECONCILIATION","aggregate registry JSON ordering is nondeterministic")
    require(len(ids)==len(set(ids)),"FAIL_CONTRACT_RECONCILIATION","duplicate aggregate id")
    require(all(isinstance(x,str) and re.fullmatch(r"[A-Z][A-Z0-9_]*",x) for x in ids),"FAIL_CONTRACT_RECONCILIATION","aggregate id format invalid")
    kotlin_family=re.search(r'UNIFIED_SYNC_CONTRACT_FAMILY:\s*String\s*=\s*"([^"]+)"',ckt)
    kotlin_version=re.search(r'UNIFIED_SYNC_CONTRACT_VERSION:\s*Int\s*=\s*(\d+)',ckt)
    require(kotlin_family and kotlin_family.group(1)==contract["contractFamily"],"FAIL_CONTRACT_RECONCILIATION","Kotlin/JSON contract family mismatch")
    require(kotlin_version and int(kotlin_version.group(1))==contract["contractVersion"],"FAIL_CONTRACT_RECONCILIATION","Kotlin/JSON contract version mismatch")
    require(parse_enum(ckt,"SyncProtocolErrorType")==ERROR_TYPES,"FAIL_CONTRACT_RECONCILIATION","Kotlin error enum mismatch")
    kr=parse_registry_kotlin(rkt)
    jr=[{k:r[k] for k in ["id","payloadVersion","conflictPolicy","deletePolicy","attachmentPolicy","financialSensitivity"]} for r in records]
    require(kr==jr,"FAIL_CONTRACT_RECONCILIATION","Kotlin/JSON registry policy mismatch")
    by={r["id"]:r for r in records}
    require(by["PAYMENT"]["conflictPolicy"]=="APPEND_ONLY_IDEMPOTENT","FAIL_CONFLICT_POLICY","PAYMENT policy weakened")
    require(by["INVENTORY_MOVEMENT"]["conflictPolicy"]=="APPEND_ONLY_IDEMPOTENT","FAIL_CONFLICT_POLICY","inventory movement policy weakened")
    require(by["INVENTORY_COST_REVISION"]["conflictPolicy"]=="IMMUTABLE_REVISION","FAIL_CONFLICT_POLICY","inventory cost policy weakened")
    require(by["SHIPMENT"]["conflictPolicy"]=="SERVER_STATE_MACHINE","FAIL_CONFLICT_POLICY","shipment state machine policy weakened")
    require(all(r["conflictPolicy"]!="EXPLICIT_SCOPED_LWW" for r in records if r.get("financialSensitivity")!="NONE"),"FAIL_CONFLICT_POLICY","financial LWW found")
    require(by["PARTY_IDENTITY"]["payloadVersion"]==2 and by["PARTY_ROLE"]["payloadVersion"]==2,"FAIL_CONTRACT_RECONCILIATION","Party v2 baseline lost")
    require(contract.get("realtimePolicy",{}).get("hintOnly") is True and contract["realtimePolicy"].get("correctnessIndependentOfRealtime") is True,"FAIL_TARGETED_CURSOR_POLICY","realtime must be hint-only")
    for name,block in [("retention",contract["retention"]),("budgets",contract["budgets"])]:
        if not isinstance(block,dict): raise ToolError(f"malformed {name} block")
        for key,value in block.items():
            if not isinstance(value,dict): raise ToolError(f"malformed {name}.{key}")
            require(bool(value.get("evidence")) and bool(value.get("rationale")), "FAIL_RETENTION_EVIDENCE" if name=="retention" else "FAIL_BUDGET_EVIDENCE", f"{name}.{key} missing evidence/rationale")
            if value.get("status")=="EVIDENCE_BLOCKED":
                require(value.get("value") is None,"FAIL_RETENTION_EVIDENCE" if name=="retention" else "FAIL_BUDGET_EVIDENCE",f"invented blocked numeric value: {name}.{key}")
    combined=ckt+"\n"+rkt
    for banned in ["lastPulledAt","sinceUpdatedAt","updatedAtCursor","deviceTimeCursor"]:
        require(banned not in combined,"FAIL_TIMESTAMP_CURSOR_POLICY",f"timestamp cursor naming introduced: {banned}")
    gapless_patterns=[r"revision\s*==\s*previous\s*\+\s*1",r"expectedRevision\s*=\s*lastRevision\s*\+\s*1"]
    require(not any(re.search(p,combined) for p in gapless_patterns),"FAIL_GAPLESS_REVISION_POLICY","gapless revision assumption introduced")
    return {"contractFamily":contract["contractFamily"],"contractVersion":contract["contractVersion"],"aggregates":len(records),"retentionStatus":contract.get("evidenceStatus",{}).get("retention"),"budgetStatus":contract.get("evidenceStatus",{}).get("budgets")}

def verify_coverage(root: Path, contract: dict, headers: list[str], rows: list[dict], baseline:dict):
    require(headers==COVERAGE_HEADERS,"FAIL_AGGREGATE_COVERAGE",f"coverage headers mismatch: {headers}")
    records={r["id"]:r for r in contract["aggregateRegistry"]}
    require(len(rows)==len(records),"FAIL_AGGREGATE_COVERAGE","coverage row count != registry count")
    ids=[r["aggregate_id"] for r in rows]
    require(len(ids)==len(set(ids)),"FAIL_AGGREGATE_COVERAGE","duplicate aggregate authority row")
    require(set(ids)==set(records),"FAIL_AGGREGATE_COVERAGE","missing/extra aggregate coverage row")
    for row in rows:
        aid=row["aggregate_id"]; rec=records[aid]
        kinds=set(filter(None,row["producer_kind"].split("|")))
        require(bool(kinds) and kinds<=PRODUCER_KINDS,"FAIL_WRITE_PATH_COVERAGE",f"unclassified producer kind for {aid}: {kinds}")
        require(row["coverage_status"] in COVERAGE_STATUSES and "UNKNOWN" not in row["coverage_status"],"FAIL_AGGREGATE_COVERAGE",f"invalid coverage status for {aid}")
        require(bool(row["current_producer"].strip()),"FAIL_WRITE_PATH_COVERAGE",f"blank producer for {aid}")
        require(bool(row["evidence_path"].strip()) and "*" not in row["evidence_path"],"FAIL_AGGREGATE_COVERAGE",f"invalid evidence path for {aid}")
        require(bool(row["evidence_symbol"].strip()) and "*" not in row["evidence_symbol"],"FAIL_AGGREGATE_COVERAGE",f"invalid evidence symbol for {aid}")
        evidence_paths=row["evidence_path"].split("|")
        require(all((root/p).is_file() for p in evidence_paths),"FAIL_AGGREGATE_COVERAGE",f"evidence path missing for {aid}: {evidence_paths}")
        require(int(row["payload_version"])==rec["payloadVersion"],"FAIL_CONTRACT_RECONCILIATION",f"payload version coverage mismatch {aid}")
        require(row["conflict_policy"]==rec["conflictPolicy"],"FAIL_CONTRACT_RECONCILIATION",f"conflict coverage mismatch {aid}")
        require(row["delete_policy"]==rec["deletePolicy"],"FAIL_CONTRACT_RECONCILIATION",f"delete coverage mismatch {aid}")
        require(row["attachment_policy"]==rec["attachmentPolicy"],"FAIL_CONTRACT_RECONCILIATION",f"attachment coverage mismatch {aid}")
        require(row["migration_owner_session"].isdigit(),"FAIL_AGGREGATE_COVERAGE",f"missing migration owner session {aid}")
    participants=set(baseline.get("participantKeys",[]))
    represented={r["participant_key"] for r in rows}
    require(participants<=represented,"FAIL_WRITE_PATH_COVERAGE",f"participant keys not represented: {sorted(participants-represented)}")
    shipment=next(r for r in rows if r["aggregate_id"]=="SHIPMENT")
    require("V303-PARTICIPANT-KEY-DRIFT-001" in shipment["notes"] and shipment["participant_key"]=="logistics-v2","FAIL_AGGREGATE_COVERAGE","shipments/logistics-v2 discrepancy missing")
    server_tokens=set()
    for row in rows:
        server_tokens.update(x for x in row["current_server_tables_or_rpc"].split("|") if x)
    missing=(REQUIRED_SERVER_PATHS|LOGISTICS_TABLES)-server_tokens
    require(not missing,"FAIL_WRITE_PATH_COVERAGE",f"required server paths uncovered: {sorted(missing)}")
    # Every explicit sync RPC literal in the current network sync runtime must appear in coverage.
    runtime_rpc=set()
    sync_dir=root/"data/network/src/main/kotlin/com/verto/app/data/sync"
    for p in sync_dir.glob("*.kt"):
        if p.name in {"UnifiedSyncContract.kt","UnifiedSyncAggregateRegistry.kt"}: continue
        txt=p.read_text(encoding="utf-8")
        runtime_rpc.update(re.findall(r'\.rpc\(\s*"([A-Za-z0-9_]+)"',txt))
    require(runtime_rpc<=server_tokens,"FAIL_WRITE_PATH_COVERAGE",f"runtime RPCs omitted from matrix: {sorted(runtime_rpc-server_tokens)}")
    return {"rows":len(rows),"uncovered":0,"participants":sorted(represented),"runtimeRpcsChecked":len(runtime_rpc)}

def verify_integrity(root:Path, baseline:dict, source_zip:Path|None):
    summary=verify_identity(root,baseline,source_zip)
    sql_now=current_sql(root); expected_sql=baseline.get("sqlFileHashes")
    if not isinstance(expected_sql,dict): raise ToolError("SQL baseline map missing")
    require(set(sql_now)==set(expected_sql),"FAIL_SERVER_MUTATION","SQL path set changed")
    for rel,h in expected_sql.items(): require(sql_now[rel]==h,"FAIL_SERVER_MUTATION",f"SQL changed: {rel}")
    for map_key,code in [("gradleFileHashes","FAIL_SCOPE_VIOLATION"),("roomExportedSchemaFileHashes","FAIL_SCOPE_VIOLATION")]:
        expected=baseline.get(map_key)
        if not isinstance(expected,dict): raise ToolError(f"{map_key} missing")
        for rel,h in expected.items():
            p=root/rel; require(p.is_file() and sha_file(p)==h,code,f"protected file drift: {rel}")
        if map_key=="roomExportedSchemaFileHashes":
            actual={p.relative_to(root).as_posix() for p in (root/"app/schemas").rglob("*") if p.is_file()}
            require(actual==set(expected),code,"exported Room schema path set changed")
    feature=root/"core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt"
    txt=feature.read_text(encoding="utf-8")
    require(re.search(r"isRealtimeSyncEnabled\s*:\s*Boolean\s*=\s*false",txt) is not None,"FAIL_SCOPE_VIOLATION","Realtime flag changed")
    require(re.search(r"isVersionedSyncEnabled\s*:\s*Boolean\s*=\s*false",txt) is not None,"FAIL_SCOPE_VIOLATION","Versioned sync flag changed")
    hist=baseline.get("historicalTestFileHashes")
    if not isinstance(hist,dict): raise ToolError("historical test baseline missing")
    curtests=current_test_sources(root)
    for rel,h in hist.items(): require(curtests.get(rel)==h,"FAIL_SCOPE_VIOLATION",f"historical test changed: {rel}")
    additions=set(curtests)-set(hist)
    require(additions==ALLOWED_TEST_ADDITIONS,"FAIL_SCOPE_VIOLATION",f"unexpected test additions: {sorted(additions)}")
    fixture_root=root/"tools/sync-contract-fixtures"
    prod_paths=set(current_production_kotlin(root))
    require(not any(str(fixture_root.relative_to(root)) in p for p in prod_paths),"FAIL_FIXTURE_SCOPE_LEAK","fixture leaked into production scope")
    summary.update({"sqlFiles":len(sql_now),"historicalTests":len(hist),"approvedTestAdditions":sorted(additions),"flags":{"realtime":False,"versioned":False}})
    return summary

def main():
    ap=argparse.ArgumentParser()
    g=ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--identity-only",action="store_true")
    g.add_argument("--contract",action="store_true")
    g.add_argument("--coverage",action="store_true")
    g.add_argument("--integrity-after",action="store_true")
    ap.add_argument("--root",default=".")
    ap.add_argument("--source-zip",default=os.environ.get("VERTO_V303_SOURCE_ZIP"))
    args=ap.parse_args(); root=Path(args.root).resolve(); source_zip=Path(args.source_zip).resolve() if args.source_zip else None
    gate="identity" if args.identity_only else "contract" if args.contract else "coverage" if args.coverage else "integrity-after"
    try:
        paths,baseline,contract,headers,rows,ckt,rkt=load_required(root)
        if args.identity_only: details=verify_identity(root,baseline,source_zip)
        elif args.contract: details=verify_contract(contract,ckt,rkt)
        elif args.coverage: details=verify_coverage(root,contract,headers,rows,baseline)
        else: details=verify_integrity(root,baseline,source_zip)
        print(json.dumps({"gate":gate,"status":"PASS","details":details},sort_keys=True,separators=(",",":")))
        return 0
    except PolicyFailure as e:
        print(json.dumps({"gate":gate,"status":"FAIL","code":e.code,"message":str(e)},sort_keys=True,separators=(",",":")))
        return 1
    except (ToolError,OSError,UnicodeError,ValueError,KeyError,TypeError) as e:
        print(json.dumps({"gate":gate,"status":"TOOL_ERROR","code":"TOOL_ERROR","message":str(e)},sort_keys=True,separators=(",",":")))
        return 2

if __name__=="__main__":
    sys.exit(main())
