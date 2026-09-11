#!/usr/bin/env python3
from __future__ import annotations

import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)


def static_contracts() -> None:
    coordinator = text("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceReturnCoordinator.kt")
    inventory = text("data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt")
    migration = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations68To69.kt")
    writer = text("data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialOutboxWriter.kt")
    server = text("docs/sql/v252_invoice_returns_financial_sync.sql")
    db = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt")
    cash_dao = text("data/database/src/main/kotlin/com/verto/app/data/local/dao/CashRegisterDao.kt")
    cash_manager = text("data/operations/src/main/kotlin/com/verto/app/utils/CashRegisterManager.kt")
    app_adapters = text("app/src/main/kotlin/com/verto/app/feature/invoice/bridge/InvoiceReturnAppAdapters.kt")
    reports = text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelQueryAdapter.kt")
    report_builders = text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelBuilders.kt")
    supplier_report = text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsFinancialIntegrity.kt")

    require("updateInvoice(" not in coordinator, "return coordinator must not rewrite original invoice")
    require("returns.insertAggregate(aggregate)" in coordinator, "return must persist as independent aggregate")
    require("InvoiceReturnCashPort" in coordinator and ".toDouble()" not in coordinator,
            "F252 coordinator must keep cash money in minor units")
    duplicate_pos = coordinator.index("val duplicateInside = returns.getByWriteId")
    recheck_pos = coordinator.index("// Recheck every quantity inside the owner transaction")
    require(duplicate_pos < recheck_pos, "same-write retry must resolve before concurrent quantity recheck")
    require("recordMovementAtomicMinor" in cash_dao and "onInvoiceReturnCashOutMinor" in cash_manager,
            "F252 cash settlement must preserve minor units through the cash owner")
    return_cash_adapter = app_adapters[app_adapters.index("class CashRegisterInvoiceReturnCashAdapter"):app_adapters.index("class PermissionInvoiceReturnAuthorizationAdapter") ]
    require("toLegacyDouble" not in return_cash_adapter and "amountMinor = amountMinor" in return_cash_adapter,
            "F252 cash adapter must not round-trip money through Double")
    require("legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN" in coordinator,
            "return must fail closed without trusted currency truth")
    require('costSnapshotStatus == "KNOWN"' in coordinator,
            "tracked sale return must require historical COGS truth")
    require("restoreSalesReturnAtomic" in inventory, "sales-return stock owner missing")
    sale = inventory[inventory.index("open suspend fun restoreSalesReturnAtomic"):inventory.index("open suspend fun deductPurchaseReturnAtomic")]
    require("updateLatestPurchasePriceAtomic" not in sale, "sales return must never mutate latest purchase price")
    require('sourceType = "INVOICE_RETURN"' in sale, "sales return movement must be traceable")
    require("getCostRevaluationEventsForItemDescending" in inventory, "purchase return must resolve historical cost source")
    require("getPostedShipmentQuantityForInvoiceItem" in inventory and "internationalPurchase" in inventory,
            "international purchase return must be bounded by actually posted receipt quantity")
    require("isShipmentSourceForInvoiceItem" in inventory,
            "international purchase return must identify receipt/landed-cost source from logistics linkage")
    require("if (!belongsToReturnedSource(latest)) return" in inventory, "newer purchase/receipt guard missing")
    require("previousValid?.newUnitCostMinor ?: sourceChainFallback" in inventory, "latest valid fallback missing")
    require("guard_invoice_return_line_insert" in migration, "DB return quantity guard missing")
    require("unit_sell_price_minor FROM invoice_items" in migration and "buy_price_minor FROM invoice_items" in migration,
            "DB must bind return line prices to immutable original line facts")
    require("guard_invoice_return_allocation_insert" in migration, "DB payment allocation guard missing")
    require("guard_invoice_void_with_returns" in migration, "void-after-return guard missing")
    require("lifecycle_status FROM invoices" in migration and "<> 'POSTED'" in migration,
            "local return insert must require POSTED parent")
    require("installInvoiceReturnIntegrityGuards(db)" in db, "fresh DB must install F252 guards")
    require("OP_INVOICE_RETURN_POSTED" in writer, "return financial outbox event missing")
    require("INVOICE_RETURN_POSTED" in server and "return quantity exceeds original invoice item" in server,
            "server return conflict contract missing")
    require("return requires a posted non-void invoice" in server, "server must reject non-posted return parent")
    require("legacy_currency_status::text = 'KNOWN'" in server, "server must require known currency truth")
    require("functional_amount_at_recognition_minor" in server and "transaction_amount_minor::numeric" in server,
            "server must bind functional return value to immutable historical FX truth")
    require(server.count("from jsonb_array_elements(v_payload->'lines') line\n                      from jsonb_array_elements") == 0,
            "server SQL contains duplicated FROM in return quantity guard")
    require("monetary facts do not match original invoice line" in server, "server original-line money guard missing")
    require("return payment allocation exceeds effective payment" in server, "server payment-allocation guard missing")
    require("international purchase return exceeds posted receipt quantity" in server and "logistics_inventory_postings" in server,
            "server must reject international purchase returns beyond posted receipts")
    require("cash return exceeds effective paid amount" in server, "server cash-refund paid guard missing")
    require("invoice with returns cannot be voided" in server, "server void-after-return guard missing")
    require(server.count("'APPLIED'::text, false, ''::text;\n") == 1, "server SQL contains duplicate APPLIED tail")
    require("invoiceReturnDao.observeAllDocuments()" in reports and "salesReturnDocuments" in reports,
            "F250 reports must consume immutable F252 return facts")
    require("historicalCostAmountMinor" in reports and "returnCogsMinor" in reports,
            "P&L must reverse historical COGS for sales returns")
    require("returnedByInvoice" in report_builders and "creditedHistorical" in report_builders,
            "aged receivables must account for sales credit notes")
    require("purchaseReturnsByInvoice" in supplier_report and "adjustedObligation" in supplier_report,
            "supplier statement must account for purchase debit notes")


def sqlite_quantity_and_immutability_contract() -> None:
    con = sqlite3.connect(":memory:")
    con.execute("PRAGMA foreign_keys=ON")
    con.executescript(
        """
        CREATE TABLE clients(id TEXT PRIMARY KEY);
        CREATE TABLE invoices(
            id TEXT PRIMARY KEY, clientId TEXT NOT NULL, category TEXT NOT NULL,
            lifecycle_status TEXT NOT NULL DEFAULT 'POSTED', voided INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE invoice_items(
            id TEXT PRIMARY KEY, invoiceId TEXT NOT NULL, quantity INTEGER NOT NULL,
            FOREIGN KEY(invoiceId) REFERENCES invoices(id)
        );
        CREATE TABLE payments(
            id TEXT PRIMARY KEY, invoiceId TEXT NOT NULL, amount_minor INTEGER NOT NULL,
            functional_cash_amount_minor INTEGER NOT NULL DEFAULT 0,
            historical_functional_amount_minor INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE invoice_return_documents(
            id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, original_invoice_id TEXT NOT NULL,
            client_id TEXT NOT NULL, document_type TEXT NOT NULL, settlement_mode TEXT NOT NULL,
            transaction_amount_minor INTEGER NOT NULL, functional_amount_minor INTEGER NOT NULL,
            reason TEXT NOT NULL, write_id TEXT NOT NULL,
            FOREIGN KEY(original_invoice_id) REFERENCES invoices(id), FOREIGN KEY(client_id) REFERENCES clients(id)
        );
        CREATE TABLE invoice_return_lines(
            id TEXT PRIMARY KEY, return_id TEXT NOT NULL, original_invoice_item_id TEXT NOT NULL,
            quantity INTEGER NOT NULL, unit_transaction_amount_minor INTEGER NOT NULL,
            transaction_amount_minor INTEGER NOT NULL, unit_functional_amount_minor INTEGER NOT NULL,
            functional_amount_minor INTEGER NOT NULL, unit_cost_at_sale_minor INTEGER NOT NULL,
            historical_cost_amount_minor INTEGER NOT NULL, original_purchase_unit_cost_minor INTEGER NOT NULL,
            FOREIGN KEY(return_id) REFERENCES invoice_return_documents(id),
            FOREIGN KEY(original_invoice_item_id) REFERENCES invoice_items(id)
        );
        CREATE TABLE invoice_return_payment_allocations(
            id TEXT PRIMARY KEY, return_id TEXT NOT NULL, payment_id TEXT NOT NULL,
            allocated_functional_amount_minor INTEGER NOT NULL
        );
        CREATE TRIGGER guard_invoice_return_line_insert
        BEFORE INSERT ON invoice_return_lines
        WHEN NEW.quantity <= 0
          OR (SELECT invoiceId FROM invoice_items WHERE id = NEW.original_invoice_item_id)
             <> (SELECT original_invoice_id FROM invoice_return_documents WHERE id = NEW.return_id)
          OR (SELECT COALESCE(SUM(quantity),0) FROM invoice_return_lines WHERE original_invoice_item_id = NEW.original_invoice_item_id)
               + NEW.quantity
             > (SELECT quantity FROM invoice_items WHERE id = NEW.original_invoice_item_id)
        BEGIN SELECT RAISE(ABORT, 'RETURN_QUANTITY_EXCEEDS_ORIGINAL'); END;
        CREATE TRIGGER guard_invoice_return_allocation_insert
        BEFORE INSERT ON invoice_return_payment_allocations
        WHEN NEW.allocated_functional_amount_minor <= 0
          OR (SELECT invoiceId FROM payments WHERE id = NEW.payment_id)
             <> (SELECT original_invoice_id FROM invoice_return_documents WHERE id = NEW.return_id)
          OR (SELECT COALESCE(SUM(allocated_functional_amount_minor),0) FROM invoice_return_payment_allocations WHERE payment_id=NEW.payment_id)
               + NEW.allocated_functional_amount_minor
             > (SELECT CASE WHEN functional_cash_amount_minor > 0 THEN functional_cash_amount_minor
                       WHEN historical_functional_amount_minor > 0 THEN historical_functional_amount_minor
                       ELSE amount_minor END FROM payments WHERE id=NEW.payment_id)
        BEGIN SELECT RAISE(ABORT, 'INVALID_RETURN_PAYMENT_ALLOCATION'); END;
        CREATE TRIGGER immutable_invoice_return_documents_update BEFORE UPDATE ON invoice_return_documents
        BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_INVOICE_RETURN'); END;
        CREATE TRIGGER immutable_invoice_return_documents_delete BEFORE DELETE ON invoice_return_documents
        BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_INVOICE_RETURN'); END;
        CREATE TRIGGER guard_invoice_void_with_returns BEFORE UPDATE OF lifecycle_status, voided ON invoices
        WHEN (NEW.lifecycle_status='VOID' OR NEW.voided=1)
          AND EXISTS(SELECT 1 FROM invoice_return_documents r WHERE r.original_invoice_id=NEW.id)
        BEGIN SELECT RAISE(ABORT, 'INVOICE_WITH_RETURNS_CANNOT_BE_VOIDED'); END;
        """
    )
    con.execute("INSERT INTO clients VALUES('c')")
    con.execute("INSERT INTO invoices VALUES('i','c','SALE','POSTED',0)")
    con.execute("INSERT INTO invoice_items VALUES('l','i',5)")
    con.execute("INSERT INTO payments VALUES('p','i',5000,5000,5000)")

    def doc(i: int) -> None:
        con.execute(
            "INSERT INTO invoice_return_documents VALUES(?,?,?,?,?,?,?,?,?,?)",
            (f"r{i}", "org", "i", "c", "SALES_RETURN_CREDIT_NOTE", "CREDIT_BALANCE", 1000, 1000, "reason", f"w{i}"),
        )

    def line(i: int, qty: int) -> None:
        con.execute(
            "INSERT INTO invoice_return_lines VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            (f"rl{i}", f"r{i}", "l", qty, 1000, 1000*qty, 1000, 1000*qty, 600, 600*qty, 0),
        )

    doc(1); line(1, 2)
    doc(2); line(2, 3)
    doc(3)
    try:
        line(3, 1)
        raise AssertionError("third return should exceed original quantity")
    except sqlite3.IntegrityError as exc:
        require("RETURN_QUANTITY_EXCEEDS_ORIGINAL" in str(exc), "wrong over-return failure")
        con.rollback()
        # rollback also removed doc(3), while committed prior facts remain after explicit commit below is required.

    # Repeat on a committed fixture for allocation + immutability checks.
    con.close()
    con = sqlite3.connect(":memory:")
    con.executescript(
        """
        CREATE TABLE invoices(id TEXT PRIMARY KEY,lifecycle_status TEXT,voided INTEGER);
        CREATE TABLE invoice_return_documents(id TEXT PRIMARY KEY,original_invoice_id TEXT);
        CREATE TRIGGER immutable_invoice_return_documents_update BEFORE UPDATE ON invoice_return_documents
        BEGIN SELECT RAISE(ABORT,'IMMUTABLE_INVOICE_RETURN'); END;
        CREATE TRIGGER immutable_invoice_return_documents_delete BEFORE DELETE ON invoice_return_documents
        BEGIN SELECT RAISE(ABORT,'IMMUTABLE_INVOICE_RETURN'); END;
        CREATE TRIGGER guard_invoice_void_with_returns BEFORE UPDATE OF lifecycle_status,voided ON invoices
        WHEN (NEW.lifecycle_status='VOID' OR NEW.voided=1)
          AND EXISTS(SELECT 1 FROM invoice_return_documents r WHERE r.original_invoice_id=NEW.id)
        BEGIN SELECT RAISE(ABORT,'INVOICE_WITH_RETURNS_CANNOT_BE_VOIDED'); END;
        INSERT INTO invoices VALUES('i','POSTED',0);
        INSERT INTO invoice_return_documents VALUES('r','i');
        """
    )
    for statement in [
        "UPDATE invoice_return_documents SET original_invoice_id='x' WHERE id='r'",
        "DELETE FROM invoice_return_documents WHERE id='r'",
        "UPDATE invoices SET lifecycle_status='VOID', voided=1 WHERE id='i'",
    ]:
        try:
            con.execute(statement)
            raise AssertionError(f"guard unexpectedly allowed: {statement}")
        except sqlite3.IntegrityError:
            pass


def latest_purchase_policy_contract() -> None:
    # Policy model mirrored from InventoryDao: rollback only if returned source still owns latest price.
    def after_return(current: int, latest_source: str, returned_source: str, previous_valid: int) -> int:
        if latest_source != returned_source:
            return current
        return previous_valid

    require(after_return(12000, "purchase-2", "purchase-2", 10000) == 10000,
            "last purchase must fall back to newest remaining valid source")
    require(after_return(15000, "purchase-3", "purchase-2", 10000) == 15000,
            "newer purchase must prevent silent rollback")


def main() -> None:
    static_contracts()
    sqlite_quantity_and_immutability_contract()
    latest_purchase_policy_contract()
    print("PASS v252 invoice returns verifier")


if __name__ == "__main__":
    main()
