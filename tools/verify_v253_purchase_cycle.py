#!/usr/bin/env python3
from __future__ import annotations

import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)


def static_contracts() -> None:
    catalog = text("data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt")
    migration = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations69To70.kt")
    entities = text("data/database/src/main/kotlin/com/verto/app/data/local/entity/PurchaseCycleEntities.kt")
    coordinator = text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt")
    store = text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/RoomPurchaseCycleStore.kt")
    invoice_writer = text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt")
    inventory_writer = text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceInventoryWriter.kt")
    pay_guard = text("app/src/main/kotlin/com/verto/app/feature/payment/bridge/RoomPurchaseReceiptPaymentGuardAdapter.kt")
    invoice_payment = text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoicePaymentWriter.kt")
    sync = text("data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt")
    participant = text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/sync/InvoiceSyncParticipant.kt")
    server = text("docs/sql/v253_purchase_cycle.sql")
    invoice_migration = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations62To63.kt")
    inventory_dao = text("data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt")

    import re
    version_match = re.search(r"ROOM_SCHEMA_VERSION:\s*Int\s*=\s*(\d+)", catalog)
    require(version_match is not None and int(version_match.group(1)) >= 70 and "MIGRATION_69_70" in catalog,
            "Room schema >=70/migration 69->70 missing")
    for table in (
        "purchase_orders", "purchase_order_lines", "goods_receipts", "goods_receipt_lines",
        "purchase_cycle_attachments", "purchase_invoice_matches", "purchase_invoice_match_lines",
        "purchase_invoice_receipt_allocations", "purchase_payment_overrides", "purchase_order_shipment_sources",
    ):
        require(table in migration and table in server, f"purchase-cycle table missing: {table}")
    require("PurchaseInvoiceReceiptAllocationEntity" in entities,
            "GRN-to-invoice allocation entity missing")
    require("index_invoice_supplier_external_ref" in invoice_migration or "supplier_invoice_ref_normalized" in invoice_migration,
            "local supplier external reference uniqueness migration missing")
    require("uq_invoices_org_supplier_external_ref" in server,
            "server supplier external reference unique index missing")

    require('sourceType = "GOODS_RECEIPT"' in coordinator,
            "LOCAL GRN must own purchase inventory recognition")
    require("order.purchaseScope == PurchaseScope.LOCAL" in coordinator,
            "GRN inventory posting must be LOCAL-only")
    require("PurchaseScope.INTERNATIONAL || !command.purchaseOrderId.isNullOrBlank()" in inventory_writer,
            "linked/international supplier invoice must not post inventory")
    require("LinkPurchaseOrderToShipment" in text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/purchase/LinkPurchaseOrderToShipmentUseCase.kt"),
            "international PO-to-shipment source use case missing")
    require("PurchaseScope.INTERNATIONAL" in text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/purchase/LinkPurchaseOrderToShipmentUseCase.kt"),
            "PO shipment source must be international-only")

    require("acceptedBefore" in coordinator and "orderedQuantity" in coordinator,
            "GRN accepted quantity bound missing")
    require("getResolvedInventoryItemId(line.id)" in coordinator,
            "three-way match must resolve inventory identity established by an earlier GRN")
    require("PARTIALLY_RECEIVED" in coordinator and "PurchaseOrderStatus.CLOSED" in coordinator,
            "partial receiving/close lifecycle missing")
    require("سبب إغلاق أمر الشراء مطلوب" in coordinator,
            "partial close reason requirement missing")
    require("quantityVariance" in coordinator and "priceVariance" in coordinator and "WITHIN_TOLERANCE" in coordinator,
            "three-way-match variance/tolerance model missing")
    require("OVERRIDDEN" in coordinator and "varianceReason" in coordinator and "approvedBy" in coordinator,
            "documented variance override missing")
    require("canOverridePurchaseVariance" in invoice_writer,
            "invoice variance override authorization missing")

    require("allocateAcceptedReceipts" in store and "getReceiptLinesWithAvailableQuantity" in store,
            "FIFO GRN allocation missing")
    require("getCurrentReceivedPayableMinor" in pay_guard,
            "payment guard must compute dynamic received payable")
    require("PAYMENT_EXCEEDS_RECEIVED_QUANTITY" in migration,
            "SQLite payment DB backstop missing")
    require("purchase_payment_overrides" in migration and "reason" in migration and "approved_by" in migration,
            "documented payment override storage missing")
    require("invoice-create-payment|" in invoice_payment and "invoice-create-payment|" in invoice_writer,
            "initial payment/override deterministic identity mismatch")

    require("NOT EXISTS (SELECT 1 FROM purchase_orders WHERE id = NEW.purchase_order_id)" in migration,
            "SQLite invoice->PO missing-parent guard missing")
    require("guard_purchase_order_update" in migration and "PURCHASE_ORDER_STATUS_REGRESSION" in server,
            "PO monotonic lifecycle guard missing")
    require("guard_purchase_cycle_attachment_insert" in migration and "verto_guard_purchase_attachment_v253" in server,
            "purchase attachment owner guard missing")
    require("INVALID_PURCHASE_ORDER_SHIPMENT_SOURCE" in migration and "verto_guard_purchase_order_shipment_source_v253" in server,
            "international PO shipment-source guard missing")

    require("isGoodsReceiptSourceForInvoiceItem" in inventory_dao and "isGoodsReceiptCostSourceValid" in inventory_dao,
            "F252 purchase-return cost-source compatibility missing")
    require("purchase_invoice_receipt_allocations" in inventory_dao,
            "purchase returns must trace GRN-backed invoice quantity")

    require('"verto_purchase_cycle_push_pre_v253"' in sync and '"verto_purchase_cycle_push_post_v253"' in sync,
            "purchase-cycle sync RPCs missing")
    require('SyncOperation(SyncStage.PUSH, 12' in participant and 'SyncOperation(SyncStage.PUSH, 35' in participant,
            "purchase sync push ordering missing")
    require('SyncOperation(SyncStage.PULL, 12' in participant and 'SyncOperation(SyncStage.PULL, 75' in participant,
            "purchase sync pull ordering missing")
    require("security definer" in server.lower() and "purchase_cycle_tenant_mismatch" in server,
            "server tenant-bound sync missing")
    require("THREE_WAY_MATCH_OVERRIDE_PERMISSION_DENIED" in server and "purchases_edit" in server and "inventory_price" in server,
            "server variance override permission guard missing")
    require("PURCHASE_PAYMENT_OVERRIDE_PERMISSION_DENIED" in server and "suppliers_add_payment" in server,
            "server unreceived-payment override permission guard missing")
    require("for update" in server.lower() and "PURCHASE_RECEIPT_ALLOCATION_EXCEEDS_AVAILABLE" in server,
            "server concurrency guard for GRN allocation missing")


def exact_migration_sql_syntax() -> None:
    """Execute every exact triple-quoted F253 Room DDL/trigger block against SQLite."""
    source = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations69To70.kt")
    blocks = re.findall(r'"""(.*?)"""', source, re.S)
    require(len(blocks) >= 20, "unexpected F253 migration block count")
    con = sqlite3.connect(":memory:")
    con.execute("PRAGMA foreign_keys=ON")
    con.executescript(
        """
        CREATE TABLE clients(id TEXT PRIMARY KEY);
        CREATE TABLE invoices(
          id TEXT PRIMARY KEY, clientId TEXT NOT NULL, category TEXT NOT NULL,
          organization_id TEXT NOT NULL, purchase_scope TEXT NOT NULL, lifecycle_status TEXT DEFAULT 'POSTED'
        );
        CREATE TABLE invoice_items(
          id TEXT PRIMARY KEY, invoiceId TEXT NOT NULL, inventory_item_id TEXT,
          FOREIGN KEY(invoiceId) REFERENCES invoices(id)
        );
        CREATE TABLE payments(
          id TEXT PRIMARY KEY, invoiceId TEXT NOT NULL, supplier_amount_minor INTEGER NOT NULL DEFAULT 0,
          reversedPaymentId TEXT, write_id TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE logistics_shipments(
          organization_id TEXT NOT NULL, id TEXT NOT NULL, PRIMARY KEY(organization_id,id)
        );
        """
    )
    con.execute("ALTER TABLE invoices ADD COLUMN purchase_order_id TEXT")
    con.execute("CREATE INDEX index_invoices_purchase_order_id ON invoices(purchase_order_id)")
    for block in blocks:
        sql = "\n".join(
            line[12:] if line.startswith("            ") else line
            for line in block.strip("\n").splitlines()
        ).strip()
        con.executescript(sql)
    con.close()


def sqlite_model() -> None:
    con = sqlite3.connect(":memory:")
    con.execute("PRAGMA foreign_keys=ON")
    con.executescript(
        """
        CREATE TABLE purchase_orders(
          id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, supplier_id TEXT NOT NULL,
          purchase_scope TEXT NOT NULL, status TEXT NOT NULL, closed_at INTEGER, close_reason TEXT
        );
        CREATE TABLE purchase_order_lines(
          id TEXT PRIMARY KEY, purchase_order_id TEXT NOT NULL, ordered_quantity INTEGER NOT NULL,
          unit_price_minor INTEGER NOT NULL, FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id)
        );
        CREATE TABLE goods_receipts(
          id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, purchase_order_id TEXT NOT NULL,
          FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id)
        );
        CREATE TABLE goods_receipt_lines(
          id TEXT PRIMARY KEY, goods_receipt_id TEXT NOT NULL, purchase_order_line_id TEXT NOT NULL,
          received_quantity INTEGER NOT NULL, accepted_quantity INTEGER NOT NULL, rejected_quantity INTEGER NOT NULL,
          FOREIGN KEY(goods_receipt_id) REFERENCES goods_receipts(id),
          FOREIGN KEY(purchase_order_line_id) REFERENCES purchase_order_lines(id)
        );
        CREATE TABLE invoices(
          id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, supplier_id TEXT NOT NULL,
          supplier_ref_norm TEXT, purchase_order_id TEXT
        );
        CREATE UNIQUE INDEX uq_supplier_ref ON invoices(organization_id,supplier_id,supplier_ref_norm)
          WHERE supplier_ref_norm IS NOT NULL AND supplier_ref_norm<>'';
        CREATE TABLE purchase_invoice_matches(
          id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, invoice_id TEXT NOT NULL,
          purchase_order_id TEXT NOT NULL, status TEXT NOT NULL, variance_reason TEXT, approved_by TEXT
        );
        CREATE TABLE purchase_invoice_match_lines(
          id TEXT PRIMARY KEY, match_id TEXT NOT NULL, purchase_order_line_id TEXT NOT NULL,
          invoiced_quantity INTEGER NOT NULL, invoice_unit_price_minor INTEGER NOT NULL,
          FOREIGN KEY(match_id) REFERENCES purchase_invoice_matches(id),
          FOREIGN KEY(purchase_order_line_id) REFERENCES purchase_order_lines(id)
        );
        CREATE TABLE purchase_invoice_receipt_allocations(
          id TEXT PRIMARY KEY, match_line_id TEXT NOT NULL, goods_receipt_line_id TEXT NOT NULL,
          allocated_quantity INTEGER NOT NULL,
          FOREIGN KEY(match_line_id) REFERENCES purchase_invoice_match_lines(id),
          FOREIGN KEY(goods_receipt_line_id) REFERENCES goods_receipt_lines(id)
        );
        CREATE TABLE purchase_payment_overrides(
          id TEXT PRIMARY KEY, invoice_id TEXT NOT NULL, payment_request_id TEXT NOT NULL,
          reason TEXT NOT NULL, approved_by TEXT NOT NULL
        );
        CREATE TABLE payments(
          id TEXT PRIMARY KEY, invoice_id TEXT NOT NULL, supplier_amount_minor INTEGER NOT NULL,
          reversed_payment_id TEXT
        );

        CREATE TRIGGER grn_guard BEFORE INSERT ON goods_receipt_lines
        WHEN NEW.received_quantity<=0 OR NEW.accepted_quantity<0 OR NEW.rejected_quantity<0
          OR NEW.accepted_quantity+NEW.rejected_quantity<>NEW.received_quantity
          OR (SELECT COALESCE(SUM(accepted_quantity),0) FROM goods_receipt_lines WHERE purchase_order_line_id=NEW.purchase_order_line_id)
             + NEW.accepted_quantity > (SELECT ordered_quantity FROM purchase_order_lines WHERE id=NEW.purchase_order_line_id)
        BEGIN SELECT RAISE(ABORT,'GRN_EXCEEDS_ORDER'); END;

        CREATE TRIGGER match_override_guard BEFORE INSERT ON purchase_invoice_matches
        WHEN NEW.status='OVERRIDDEN' AND (length(trim(coalesce(NEW.variance_reason,'')))=0 OR length(trim(coalesce(NEW.approved_by,'')))=0)
        BEGIN SELECT RAISE(ABORT,'MATCH_OVERRIDE_REQUIRES_AUDIT'); END;

        CREATE TRIGGER allocation_guard BEFORE INSERT ON purchase_invoice_receipt_allocations
        WHEN NEW.allocated_quantity<=0
          OR (SELECT purchase_order_line_id FROM purchase_invoice_match_lines WHERE id=NEW.match_line_id)
             <> (SELECT purchase_order_line_id FROM goods_receipt_lines WHERE id=NEW.goods_receipt_line_id)
          OR (SELECT COALESCE(SUM(allocated_quantity),0) FROM purchase_invoice_receipt_allocations WHERE match_line_id=NEW.match_line_id)
             + NEW.allocated_quantity > (SELECT invoiced_quantity FROM purchase_invoice_match_lines WHERE id=NEW.match_line_id)
          OR (SELECT COALESCE(SUM(allocated_quantity),0) FROM purchase_invoice_receipt_allocations WHERE goods_receipt_line_id=NEW.goods_receipt_line_id)
             + NEW.allocated_quantity > (SELECT accepted_quantity FROM goods_receipt_lines WHERE id=NEW.goods_receipt_line_id)
        BEGIN SELECT RAISE(ABORT,'ALLOCATION_EXCEEDS_AVAILABLE'); END;

        CREATE TRIGGER payment_guard BEFORE INSERT ON payments
        WHEN NEW.reversed_payment_id IS NULL AND NEW.supplier_amount_minor>0
          AND (SELECT purchase_order_id FROM invoices WHERE id=NEW.invoice_id) IS NOT NULL
          AND (SELECT COALESCE(SUM(supplier_amount_minor),0) FROM payments p
                 WHERE p.invoice_id=NEW.invoice_id AND p.reversed_payment_id IS NULL
                 AND NOT EXISTS(SELECT 1 FROM payments r WHERE r.reversed_payment_id=p.id))
              + NEW.supplier_amount_minor
              > COALESCE((SELECT SUM(a.allocated_quantity*ml.invoice_unit_price_minor)
                    FROM purchase_invoice_receipt_allocations a
                    JOIN purchase_invoice_match_lines ml ON ml.id=a.match_line_id
                    JOIN purchase_invoice_matches m ON m.id=ml.match_id
                    WHERE m.invoice_id=NEW.invoice_id),0)
          AND NOT EXISTS(SELECT 1 FROM purchase_payment_overrides o
                         WHERE o.invoice_id=NEW.invoice_id AND o.payment_request_id=NEW.id)
        BEGIN SELECT RAISE(ABORT,'PAYMENT_EXCEEDS_RECEIVED'); END;
        """
    )

    # Supplier invoice reference uniqueness: same org+supplier rejects duplicate; another supplier is allowed.
    con.execute("INSERT INTO invoices VALUES('legacy','o','s','abc',NULL)")
    try:
        con.execute("INSERT INTO invoices VALUES('dup','o','s','abc',NULL)")
        raise AssertionError("duplicate supplier reference was accepted")
    except sqlite3.IntegrityError:
        pass
    con.execute("INSERT INTO invoices VALUES('other-supplier','o','s2','abc',NULL)")

    con.execute("INSERT INTO purchase_orders VALUES('po','o','s','LOCAL','OPEN',NULL,NULL)")
    con.execute("INSERT INTO purchase_order_lines VALUES('pol','po',10,100)")
    con.execute("INSERT INTO goods_receipts VALUES('gr1','o','po')")
    con.execute("INSERT INTO goods_receipt_lines VALUES('grl1','gr1','pol',4,4,0)")
    accepted = con.execute("SELECT SUM(accepted_quantity) FROM goods_receipt_lines WHERE purchase_order_line_id='pol'").fetchone()[0]
    require(accepted == 4, "partial GRN fixture failed")
    try:
        con.execute("INSERT INTO goods_receipts VALUES('gr-bad','o','po')")
        con.execute("INSERT INTO goods_receipt_lines VALUES('grl-bad','gr-bad','pol',7,7,0)")
        raise AssertionError("over-receipt was accepted")
    except sqlite3.IntegrityError:
        con.rollback()
        # Rebuild committed business fixture after rollback.
        con.execute("INSERT OR IGNORE INTO purchase_orders VALUES('po','o','s','LOCAL','OPEN',NULL,NULL)")
        con.execute("INSERT OR IGNORE INTO purchase_order_lines VALUES('pol','po',10,100)")
        con.execute("INSERT OR IGNORE INTO goods_receipts VALUES('gr1','o','po')")
        con.execute("INSERT OR IGNORE INTO goods_receipt_lines VALUES('grl1','gr1','pol',4,4,0)")

    con.execute("INSERT OR REPLACE INTO invoices VALUES('inv','o','s','ref-1','po')")
    try:
        con.execute("INSERT INTO purchase_invoice_matches VALUES('m-bad','o','inv','po','OVERRIDDEN','',NULL)")
        raise AssertionError("unaudited variance override was accepted")
    except sqlite3.IntegrityError:
        pass
    con.execute("INSERT INTO purchase_invoice_matches VALUES('m','o','inv','po','OVERRIDDEN','price/qty variance','u')")
    con.execute("INSERT INTO purchase_invoice_match_lines VALUES('ml','m','pol',10,120)")
    con.execute("INSERT INTO purchase_invoice_receipt_allocations VALUES('a1','ml','grl1',4)")
    payable = con.execute("SELECT SUM(a.allocated_quantity*ml.invoice_unit_price_minor) FROM purchase_invoice_receipt_allocations a JOIN purchase_invoice_match_lines ml ON ml.id=a.match_line_id").fetchone()[0]
    require(payable == 480, "partial received payable must use invoice price")

    try:
        con.execute("INSERT INTO payments VALUES('p-too-much','inv',600,NULL)")
        raise AssertionError("payment beyond received quantity was accepted")
    except sqlite3.IntegrityError:
        pass
    con.execute("INSERT INTO payments VALUES('p1','inv',480,NULL)")

    # Later GRN dynamically unlocks the already-invoiced remainder without rewriting the historical match.
    con.execute("INSERT INTO goods_receipts VALUES('gr2','o','po')")
    con.execute("INSERT INTO goods_receipt_lines VALUES('grl2','gr2','pol',6,6,0)")
    con.execute("INSERT INTO purchase_invoice_receipt_allocations VALUES('a2','ml','grl2',6)")
    payable2 = con.execute("SELECT SUM(a.allocated_quantity*ml.invoice_unit_price_minor) FROM purchase_invoice_receipt_allocations a JOIN purchase_invoice_match_lines ml ON ml.id=a.match_line_id").fetchone()[0]
    require(payable2 == 1200, "later GRN must unlock remaining payable")
    con.execute("INSERT INTO payments VALUES('p2','inv',720,NULL)")

    # Allocation cannot over-consume accepted GRN or invoiced quantity.
    try:
        con.execute("INSERT INTO purchase_invoice_receipt_allocations VALUES('a3','ml','grl2',1)")
        raise AssertionError("over-allocation was accepted")
    except sqlite3.IntegrityError:
        pass

    # Explicit documented override can authorize a new payment above current received payable.
    con.execute("INSERT INTO purchase_payment_overrides VALUES('ov','inv','p-override','urgent approved exception','u')")
    con.execute("INSERT INTO payments VALUES('p-override','inv',1,NULL)")

    con.close()


def main() -> None:
    static_contracts()
    exact_migration_sql_syntax()
    sqlite_model()
    print("PASS v253 purchase cycle verifier")


if __name__ == "__main__":
    main()
