package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F252: immutable invoice-return / credit-note / debit-note aggregates. */
val MIGRATION_68_69 = object : Migration(68, 69) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_return_documents (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                original_invoice_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                document_type TEXT NOT NULL,
                settlement_mode TEXT NOT NULL,
                transaction_currency_code TEXT NOT NULL,
                functional_currency_code TEXT NOT NULL,
                transaction_amount_minor INTEGER NOT NULL,
                functional_amount_minor INTEGER NOT NULL,
                reason TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                recorded_at INTEGER NOT NULL,
                created_by TEXT NOT NULL,
                created_by_name TEXT NOT NULL,
                write_id TEXT NOT NULL,
                source_version INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(original_invoice_id) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(client_id) REFERENCES clients(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_returns_original_invoice ON invoice_return_documents(original_invoice_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_returns_client ON invoice_return_documents(client_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_returns_write_identity ON invoice_return_documents(organization_id, write_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_returns_timeline ON invoice_return_documents(organization_id, occurred_at, id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_return_lines (
                id TEXT NOT NULL PRIMARY KEY,
                return_id TEXT NOT NULL,
                original_invoice_item_id TEXT NOT NULL,
                inventory_item_id TEXT NOT NULL,
                item_name_snapshot TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                unit_transaction_amount_minor INTEGER NOT NULL,
                transaction_amount_minor INTEGER NOT NULL,
                unit_functional_amount_minor INTEGER NOT NULL,
                functional_amount_minor INTEGER NOT NULL,
                unit_cost_at_sale_minor INTEGER NOT NULL,
                historical_cost_amount_minor INTEGER NOT NULL,
                original_purchase_unit_cost_minor INTEGER NOT NULL,
                FOREIGN KEY(return_id) REFERENCES invoice_return_documents(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(original_invoice_item_id) REFERENCES invoice_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_return_lines_return ON invoice_return_lines(return_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_return_lines_original_item ON invoice_return_lines(original_invoice_item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_return_lines_inventory_item ON invoice_return_lines(inventory_item_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_return_lines_identity ON invoice_return_lines(return_id, original_invoice_item_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_return_payment_allocations (
                id TEXT NOT NULL PRIMARY KEY,
                return_id TEXT NOT NULL,
                payment_id TEXT NOT NULL,
                allocated_functional_amount_minor INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(return_id) REFERENCES invoice_return_documents(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(payment_id) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_return_allocations_return ON invoice_return_payment_allocations(return_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_return_allocations_payment ON invoice_return_payment_allocations(payment_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_return_allocations_identity ON invoice_return_payment_allocations(return_id, payment_id)")

        installInvoiceReturnIntegrityGuards(db)

    }
}

/** Also installed on open so fresh databases created directly at v69 receive the same guards. */
fun installInvoiceReturnIntegrityGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_invoice_return_document_insert
        BEFORE INSERT ON invoice_return_documents
        WHEN NEW.transaction_amount_minor <= 0
          OR NEW.functional_amount_minor <= 0
          OR length(trim(NEW.organization_id)) = 0
          OR length(trim(NEW.write_id)) = 0
          OR length(trim(NEW.reason)) = 0
          OR NEW.document_type NOT IN ('SALES_RETURN_CREDIT_NOTE','PURCHASE_RETURN_DEBIT_NOTE')
          OR NEW.settlement_mode NOT IN ('CREDIT_BALANCE','CASH_REFUND')
          OR (SELECT lifecycle_status FROM invoices WHERE id = NEW.original_invoice_id) <> 'POSTED'
          OR (SELECT voided FROM invoices WHERE id = NEW.original_invoice_id) = 1
          OR (SELECT legacy_currency_status FROM invoices WHERE id = NEW.original_invoice_id) <> 'KNOWN'
          OR length(trim(NEW.transaction_currency_code)) = 0
          OR length(trim(NEW.functional_currency_code)) = 0
          OR NEW.transaction_currency_code <> (SELECT transaction_currency_code FROM invoices WHERE id = NEW.original_invoice_id)
          OR NEW.functional_currency_code <> (SELECT functional_currency_code FROM invoices WHERE id = NEW.original_invoice_id)
          OR NEW.client_id <> (SELECT clientId FROM invoices WHERE id = NEW.original_invoice_id)
          OR NEW.document_type <> CASE (SELECT category FROM invoices WHERE id = NEW.original_invoice_id)
              WHEN 'SALE' THEN 'SALES_RETURN_CREDIT_NOTE'
              WHEN 'PURCHASE' THEN 'PURCHASE_RETURN_DEBIT_NOTE'
              ELSE ''
          END
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_INVOICE_RETURN_DOCUMENT');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_invoice_return_line_insert
        BEFORE INSERT ON invoice_return_lines
        WHEN NEW.quantity <= 0
          OR NEW.unit_transaction_amount_minor < 0
          OR NEW.transaction_amount_minor <= 0
          OR NEW.unit_functional_amount_minor < 0
          OR NEW.functional_amount_minor <= 0
          OR NEW.unit_cost_at_sale_minor < 0
          OR NEW.historical_cost_amount_minor < 0
          OR NEW.original_purchase_unit_cost_minor < 0
          OR NEW.transaction_amount_minor <> NEW.unit_transaction_amount_minor * NEW.quantity
          OR NEW.inventory_item_id <> COALESCE((SELECT inventoryItemId FROM invoice_items WHERE id = NEW.original_invoice_item_id), '')
          OR (SELECT invoiceId FROM invoice_items WHERE id = NEW.original_invoice_item_id)
             <> (SELECT original_invoice_id FROM invoice_return_documents WHERE id = NEW.return_id)
          OR (
              (SELECT document_type FROM invoice_return_documents WHERE id = NEW.return_id) = 'SALES_RETURN_CREDIT_NOTE'
              AND (
                  NEW.unit_transaction_amount_minor <> (SELECT unit_sell_price_minor FROM invoice_items WHERE id = NEW.original_invoice_item_id)
                  OR NEW.unit_cost_at_sale_minor <> (SELECT unit_cost_at_sale_minor FROM invoice_items WHERE id = NEW.original_invoice_item_id)
                  OR NEW.historical_cost_amount_minor <> NEW.unit_cost_at_sale_minor * NEW.quantity
                  OR NEW.original_purchase_unit_cost_minor <> 0
                  OR (NEW.inventory_item_id <> '' AND (SELECT cost_snapshot_status FROM invoice_items WHERE id = NEW.original_invoice_item_id) <> 'KNOWN')
              )
          )
          OR (
              (SELECT document_type FROM invoice_return_documents WHERE id = NEW.return_id) = 'PURCHASE_RETURN_DEBIT_NOTE'
              AND (
                  NEW.unit_transaction_amount_minor <> (SELECT buy_price_minor FROM invoice_items WHERE id = NEW.original_invoice_item_id)
                  OR NEW.unit_cost_at_sale_minor <> 0
                  OR NEW.historical_cost_amount_minor <> 0
                  OR NEW.original_purchase_unit_cost_minor <> (SELECT buy_price_minor FROM invoice_items WHERE id = NEW.original_invoice_item_id)
              )
          )
          OR (SELECT COALESCE(SUM(quantity),0) FROM invoice_return_lines WHERE original_invoice_item_id = NEW.original_invoice_item_id)
               + NEW.quantity
             > (SELECT quantity FROM invoice_items WHERE id = NEW.original_invoice_item_id)
        BEGIN
            SELECT RAISE(ABORT, 'RETURN_QUANTITY_EXCEEDS_ORIGINAL');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_invoice_return_allocation_insert
        BEFORE INSERT ON invoice_return_payment_allocations
        WHEN NEW.allocated_functional_amount_minor <= 0
          OR (SELECT invoiceId FROM payments WHERE id = NEW.payment_id)
             <> (SELECT original_invoice_id FROM invoice_return_documents WHERE id = NEW.return_id)
          OR (SELECT COALESCE(SUM(allocated_functional_amount_minor), 0)
                FROM invoice_return_payment_allocations WHERE payment_id = NEW.payment_id)
               + NEW.allocated_functional_amount_minor
             > (SELECT CASE
                    WHEN functional_cash_amount_minor > 0 THEN functional_cash_amount_minor
                    WHEN historical_functional_amount_minor > 0 THEN historical_functional_amount_minor
                    ELSE amount_minor
                END FROM payments WHERE id = NEW.payment_id)
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_RETURN_PAYMENT_ALLOCATION');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_invoice_void_with_returns
        BEFORE UPDATE OF lifecycle_status, voided ON invoices
        WHEN (NEW.lifecycle_status = 'VOID' OR NEW.voided = 1)
          AND EXISTS (SELECT 1 FROM invoice_return_documents r WHERE r.original_invoice_id = NEW.id)
        BEGIN
            SELECT RAISE(ABORT, 'INVOICE_WITH_RETURNS_CANNOT_BE_VOIDED');
        END
        """.trimIndent()
    )

    // Posted return documents are append-only financial facts.
    for (table in listOf("invoice_return_documents", "invoice_return_lines", "invoice_return_payment_allocations")) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS immutable_${table}_update BEFORE UPDATE ON $table " +
                "BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_INVOICE_RETURN'); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS immutable_${table}_delete BEFORE DELETE ON $table " +
                "BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_INVOICE_RETURN'); END"
        )
    }
}
