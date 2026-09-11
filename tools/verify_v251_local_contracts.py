#!/usr/bin/env python3
"""F251 in-chat verifier: source guards + local SQLite contract simulation only.

This is intentionally not a substitute for Room MigrationTestHelper, instrumented tests,
or a Gradle build. Those remain laptop/device gate items.
"""
from __future__ import annotations

import sqlite3
import tempfile
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

ROOT = Path(__file__).resolve().parents[1]


def require(path: str, needle: str) -> None:
    text = (ROOT / path).read_text(encoding="utf-8")
    if needle not in text:
        raise AssertionError(f"missing source contract: {path}: {needle}")


def forbid(path: str, needle: str) -> None:
    text = (ROOT / path).read_text(encoding="utf-8")
    if needle in text:
        raise AssertionError(f"forbidden source contract: {path}: {needle}")


def source_contracts() -> None:
    require("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/RecordPaymentCoordinator.kt", "store.getTotalPaidMinor(command.invoiceId)")
    require("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/RecordPaymentCoordinator.kt", "persistIntegration(organizationId, payment, command.paidAt)")
    forbid("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/RecordPaymentCoordinator.kt", "if (remote.isEnabled())")
    forbid("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/ReversePaymentCoordinator.kt", "if (remote.isEnabled())")
    require("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/BulkPaymentCoordinator.kt", "var remainingMinor = amount.amountMinor")
    forbid("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/BulkPaymentCoordinator.kt", "remaining -=")
    require("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/command/BulkPaymentAllocator.kt", "requestId: String")
    require("feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientPaymentViewModel.kt", "PENDING_BULK_PAYMENT_REQUEST_ID")
    require("feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierPaymentScreen.kt", "PENDING_BULK_PAYMENT_REQUEST_ID")
    require("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/payment/AddPaymentScreen.kt", "PENDING_PAYMENT_REQUEST_ID")
    require("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorViewModel.kt", "active_invoice_write_id")
    require("data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt", "SUM(amount_minor)")


def sqlite_contracts() -> None:
    with tempfile.NamedTemporaryFile(suffix=".db") as f:
        db = sqlite3.connect(f.name, timeout=5, isolation_level=None)
        db.executescript(
            """
            PRAGMA journal_mode=WAL;
            CREATE TABLE inventory(id TEXT PRIMARY KEY, quantity INTEGER NOT NULL);
            INSERT INTO inventory VALUES('last', 1);
            CREATE TABLE write_guard(
              id TEXT PRIMARY KEY,
              organization_id TEXT NOT NULL,
              operation_type TEXT NOT NULL,
              write_id TEXT NOT NULL,
              UNIQUE(organization_id, operation_type, write_id)
            );
            CREATE TABLE outbox(
              event_id TEXT PRIMARY KEY,
              organization_id TEXT NOT NULL,
              operation_type TEXT NOT NULL,
              write_id TEXT NOT NULL,
              UNIQUE(organization_id, operation_type, write_id)
            );
            """
        )

        # Mid-transaction failure must roll back all effects.
        db.execute("BEGIN")
        db.execute("INSERT INTO write_guard VALUES('rollback','org','POST','rollback')")
        db.execute("ROLLBACK")
        assert db.execute("SELECT COUNT(*) FROM write_guard WHERE id='rollback'").fetchone()[0] == 0

        # Duplicate request identity must not create a second financial write.
        db.execute("INSERT INTO write_guard VALUES('g1','org','POST','same-write')")
        try:
            db.execute("INSERT INTO write_guard VALUES('g2','org','POST','same-write')")
            raise AssertionError("duplicate write id unexpectedly inserted")
        except sqlite3.IntegrityError:
            pass

        # At-least-once local outbox identity remains one row on retry.
        db.execute("INSERT INTO outbox VALUES('e1','org','PAYMENT','payment-write')")
        try:
            db.execute("INSERT INTO outbox VALUES('e2','org','PAYMENT','payment-write')")
            raise AssertionError("duplicate outbox identity unexpectedly inserted")
        except sqlite3.IntegrityError:
            pass
        db.close()

        # Two contenders for the final item: conditional SQL allows exactly one success.
        def sell(_: int) -> int:
            conn = sqlite3.connect(f.name, timeout=5, isolation_level=None)
            conn.execute("PRAGMA busy_timeout=5000")
            cur = conn.execute("UPDATE inventory SET quantity=quantity-1 WHERE id='last' AND quantity>=1")
            affected = cur.rowcount
            conn.close()
            return affected

        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(sell, (1, 2)))
        assert sorted(results) == [0, 1], results
        check = sqlite3.connect(f.name)
        assert check.execute("SELECT quantity FROM inventory WHERE id='last'").fetchone()[0] == 0
        check.close()


def main() -> None:
    source_contracts()
    sqlite_contracts()
    print("V251_LOCAL_CONTRACTS_PASS")
    print("scope=static-source+sqlite-model; room/instrumented/gradle-build=NOT_RUN")


if __name__ == "__main__":
    main()
