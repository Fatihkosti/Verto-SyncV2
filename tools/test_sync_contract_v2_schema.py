#!/usr/bin/env python3
"""Deterministic B06 contract/schema coverage gate; uses only the Python stdlib."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = json.loads((ROOT / "SYNC_CONTRACT_V2.schema.json").read_text(encoding="utf-8"))
KOTLIN = (ROOT / "data/network/src/main/kotlin/com/verto/app/data/sync/FinancialSyncContractV2.kt").read_text(encoding="utf-8")

EXPECTED_DEFS = {
    "financialAggregateSnapshotV2", "inventoryMovementDtoV2", "inventoryCostRevisionDtoV2",
    "clientCreditDtoV2", "expenseDtoV2", "cashReconciliationDtoV2", "cashDenominationDtoV2",
    "cashMovementDtoV2", "costAllocationDtoV2", "purchaseRequestV2", "syncBatchRequestV2",
    "syncReceiptV2", "syncMutationEnvelopeV2",
}
FULL_FINANCIAL_LISTS = {
    "items", "dueInstallments", "payments", "paymentAllocations", "realizedFxEvents",
    "returnDocuments", "returnLines", "returnPaymentAllocations", "explicitTombstones",
    "effectReferences",
}
PURCHASE_LISTS = {
    "purchaseOrders", "purchaseOrderLines", "goodsReceipts", "goodsReceiptLines", "attachments",
    "matches", "matchLines", "allocations", "paymentOverrides",
}

defs = SCHEMA.get("$defs", {})
assert EXPECTED_DEFS <= defs.keys(), f"missing definitions: {EXPECTED_DEFS - defs.keys()}"
assert FULL_FINANCIAL_LISTS <= set(defs["financialAggregateSnapshotV2"]["required"])
assert PURCHASE_LISTS == set(defs["purchaseRequestV2"]["required"])
assert all(
    not (value.get("type") == "object" and not value.get("properties") and "$ref" not in value)
    for value in defs.values()
), "top-level schema definition contains a placeholder object"
schema_text = json.dumps(SCHEMA, ensure_ascii=False, separators=(",", ":"))
assert "privateUri" not in schema_text and "imageUri" not in schema_text
assert "allocatedAmountMinor" not in json.dumps(defs["costAllocationDtoV2"])
assert {"allocatedAmount", "perUnitCost"} <= set(defs["costAllocationDtoV2"]["required"])

for field in FULL_FINANCIAL_LISTS | {"businessContentHash", "financialStreamVersion"}:
    assert f"val {field}:" in KOTLIN, f"Kotlin DTO misses {field}"
for class_name in (
    "InventoryMovementDtoV2", "InventoryCostRevisionDtoV2", "ClientCreditDtoV2",
    "PurchaseRequestDtoV2", "SyncBatchRequestDtoV2", "SyncReceiptDtoV2",
):
    assert f"class {class_name}" in KOTLIN, f"Kotlin DTO misses {class_name}"

print(f"B06_SCHEMA_GATE=PASS defs={len(defs)} fullFinancialLists={len(FULL_FINANCIAL_LISTS)}")
