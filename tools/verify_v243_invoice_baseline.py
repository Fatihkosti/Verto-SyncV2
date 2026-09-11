#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(rel):
    p = ROOT / rel
    if not p.exists():
        errors.append(f"missing:{rel}")
        return ""
    return p.read_text(encoding="utf-8")

def require(rel, needle, label):
    text = read(rel)
    if needle not in text:
        errors.append(f"{label}:{rel}:{needle}")

# Current architecture anchors discovered in F243.
require("data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt", "ROOM_SCHEMA_VERSION: Int = 61", "room-schema")
require("data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/RoomDatabaseTransactionRunner.kt", "database.withTransaction", "room-owner-transaction")
require("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt", "transactionPort.inTransaction", "invoice-owner-transaction")
require("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/RecordPaymentCoordinator.kt", "transaction.inTransaction", "payment-owner-transaction")
require("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceSaveValidator.kt", "toIntOrNull() ?: 1", "quantity-fallback-baseline")
require("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceInventoryWriter.kt", "byName", "name-match-baseline")
require("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoicePostCommitEffects.kt", "auditLogger.logInsert", "postcommit-audit-baseline")
require("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/sync/InvoiceSyncParticipant.kt", "pushInvoices", "table-sync-baseline")
require("feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/OptimalInvoiceIntegrationOutboxAdapter.kt", "OptimalOutboxEntity", "optimal-outbox-baseline")
require("core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt", "isFinancialMutationsEnabled: Boolean = false", "financial-rpc-default")

entities = read("data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt")
for field in ("val totalAmount: Double", "val buyPrice: Double", "val sellPrice: Double", "val amount: Double", "val balance: Double"):
    if field not in entities:
        errors.append(f"financial-double-baseline:{field}")

# Mandatory F243 docs.
for rel in [
    "docs/invoice/invoice-flow-map.md",
    "docs/invoice/invoice-invariants.md",
    "docs/invoice/invoice-schema-sync-baseline.md",
    "docs/invoice/invoice-v238-merge-impact.md",
    "docs/invoice/invoice-files-allowlist-244-251.md",
    "verification-invoice-F243.md",
]:
    text = read(rel)
    if len(text.strip()) < 500:
        errors.append(f"doc-too-small:{rel}")

# Lineage guard: versions 239-242 must remain documented as logistics-scoped and Room 61 unchanged.
for version in (239, 240, 241, 242):
    text = read(f"Verto-v{version}-report.md")
    if "Room" not in text or "61" not in text:
        errors.append(f"lineage-room-missing:v{version}")

if errors:
    print("V243_INVOICE_BASELINE_FAIL")
    for e in errors:
        print(" -", e)
    sys.exit(1)

print("V243_INVOICE_BASELINE_PASS")
