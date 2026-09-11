package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F253 — PO -> GRN -> supplier invoice -> payment control. */
val MIGRATION_69_70 = object : Migration(69, 70) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invoices ADD COLUMN purchase_order_id TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_purchase_order_id ON invoices(purchase_order_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_orders (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                order_number TEXT NOT NULL,
                supplier_id TEXT NOT NULL,
                purchase_scope TEXT NOT NULL,
                currency_code TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                created_by TEXT NOT NULL,
                created_by_name TEXT NOT NULL,
                closed_at INTEGER,
                close_reason TEXT,
                note TEXT NOT NULL,
                write_id TEXT NOT NULL,
                FOREIGN KEY(supplier_id) REFERENCES clients(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_supplier_id ON purchase_orders(supplier_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_orders_org_number ON purchase_orders(organization_id, order_number)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_orders_org_write ON purchase_orders(organization_id, write_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_org_status ON purchase_orders(organization_id, status)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_order_lines (
                id TEXT NOT NULL PRIMARY KEY,
                purchase_order_id TEXT NOT NULL,
                line_number INTEGER NOT NULL,
                inventory_item_id TEXT,
                item_name_snapshot TEXT NOT NULL,
                ordered_quantity INTEGER NOT NULL,
                unit_price_minor INTEGER NOT NULL,
                FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_purchase_order_id ON purchase_order_lines(purchase_order_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_order_lines_order_number ON purchase_order_lines(purchase_order_id, line_number)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_inventory ON purchase_order_lines(purchase_order_id, inventory_item_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goods_receipts (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                purchase_order_id TEXT NOT NULL,
                receipt_number TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                received_by TEXT NOT NULL,
                received_by_name TEXT NOT NULL,
                note TEXT NOT NULL,
                write_id TEXT NOT NULL,
                FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipts_purchase_order_id ON goods_receipts(purchase_order_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_goods_receipts_org_number ON goods_receipts(organization_id, receipt_number)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_goods_receipts_org_write ON goods_receipts(organization_id, write_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goods_receipt_lines (
                id TEXT NOT NULL PRIMARY KEY,
                goods_receipt_id TEXT NOT NULL,
                purchase_order_line_id TEXT NOT NULL,
                inventory_item_id TEXT,
                received_quantity INTEGER NOT NULL,
                accepted_quantity INTEGER NOT NULL,
                rejected_quantity INTEGER NOT NULL,
                unit_cost_minor INTEGER NOT NULL,
                FOREIGN KEY(goods_receipt_id) REFERENCES goods_receipts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(purchase_order_line_id) REFERENCES purchase_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipt_lines_goods_receipt_id ON goods_receipt_lines(goods_receipt_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipt_lines_purchase_order_line_id ON goods_receipt_lines(purchase_order_line_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_goods_receipt_line_identity ON goods_receipt_lines(goods_receipt_id, purchase_order_line_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_cycle_attachments (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                owner_type TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                uri TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                display_name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                write_id TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_cycle_attachments_owner ON purchase_cycle_attachments(organization_id, owner_type, owner_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_cycle_attachments_identity ON purchase_cycle_attachments(organization_id, write_id, uri)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_invoice_matches (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                invoice_id TEXT NOT NULL,
                purchase_order_id TEXT NOT NULL,
                status TEXT NOT NULL,
                quantity_variance_units INTEGER NOT NULL,
                price_variance_minor INTEGER NOT NULL,
                quantity_tolerance_units INTEGER NOT NULL,
                price_tolerance_minor INTEGER NOT NULL,
                invoice_amount_minor INTEGER NOT NULL,
                payable_amount_minor INTEGER NOT NULL,
                variance_reason TEXT,
                approved_by TEXT,
                approved_by_name TEXT,
                matched_at INTEGER NOT NULL,
                write_id TEXT NOT NULL,
                FOREIGN KEY(invoice_id) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_invoice_matches_invoice ON purchase_invoice_matches(invoice_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_matches_purchase_order_id ON purchase_invoice_matches(purchase_order_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_matches_org_status ON purchase_invoice_matches(organization_id, status)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_invoice_match_lines (
                id TEXT NOT NULL PRIMARY KEY,
                match_id TEXT NOT NULL,
                invoice_item_id TEXT NOT NULL,
                purchase_order_line_id TEXT NOT NULL,
                ordered_quantity INTEGER NOT NULL,
                accepted_quantity INTEGER NOT NULL,
                invoiced_quantity INTEGER NOT NULL,
                po_unit_price_minor INTEGER NOT NULL,
                invoice_unit_price_minor INTEGER NOT NULL,
                quantity_variance_units INTEGER NOT NULL,
                price_variance_minor INTEGER NOT NULL,
                payable_amount_minor INTEGER NOT NULL,
                FOREIGN KEY(match_id) REFERENCES purchase_invoice_matches(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(invoice_item_id) REFERENCES invoice_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(purchase_order_line_id) REFERENCES purchase_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_match_lines_match_id ON purchase_invoice_match_lines(match_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_match_lines_invoice_item_id ON purchase_invoice_match_lines(invoice_item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_match_lines_purchase_order_line_id ON purchase_invoice_match_lines(purchase_order_line_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_invoice_match_line_identity ON purchase_invoice_match_lines(match_id, purchase_order_line_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_invoice_receipt_allocations (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                match_line_id TEXT NOT NULL,
                goods_receipt_line_id TEXT NOT NULL,
                allocated_quantity INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                write_id TEXT NOT NULL,
                FOREIGN KEY(match_line_id) REFERENCES purchase_invoice_match_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(goods_receipt_line_id) REFERENCES goods_receipt_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_receipt_allocations_match_line_id ON purchase_invoice_receipt_allocations(match_line_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_receipt_allocations_goods_receipt_line_id ON purchase_invoice_receipt_allocations(goods_receipt_line_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_invoice_receipt_allocation_identity ON purchase_invoice_receipt_allocations(organization_id, match_line_id, goods_receipt_line_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_payment_overrides (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                invoice_id TEXT NOT NULL,
                payment_request_id TEXT NOT NULL,
                requested_amount_minor INTEGER NOT NULL,
                payable_before_override_minor INTEGER NOT NULL,
                reason TEXT NOT NULL,
                approved_by TEXT NOT NULL,
                approved_by_name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(invoice_id) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_payment_overrides_invoice_id ON purchase_payment_overrides(invoice_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_payment_override_request ON purchase_payment_overrides(organization_id, payment_request_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_order_shipment_sources (
                id TEXT NOT NULL PRIMARY KEY,
                organization_id TEXT NOT NULL,
                shipment_id TEXT NOT NULL,
                purchase_order_id TEXT NOT NULL,
                added_at INTEGER NOT NULL,
                write_id TEXT NOT NULL,
                FOREIGN KEY(purchase_order_id) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(organization_id, shipment_id) REFERENCES logistics_shipments(organization_id, id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_shipment_sources_purchase_order_id ON purchase_order_shipment_sources(purchase_order_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_order_shipment_identity ON purchase_order_shipment_sources(organization_id, shipment_id, purchase_order_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_order_shipment_write ON purchase_order_shipment_sources(organization_id, write_id)")

        installPurchaseCycleIntegrityGuards(db)
    }
}

/** Installed on open too so fresh schema-70 databases receive non-Room trigger invariants. */
fun installPurchaseCycleIntegrityGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_order_insert
        BEFORE INSERT ON purchase_orders
        WHEN length(trim(NEW.organization_id)) = 0
          OR length(trim(NEW.order_number)) = 0
          OR length(trim(NEW.supplier_id)) = 0
          OR NEW.purchase_scope NOT IN ('LOCAL','INTERNATIONAL')
          OR length(trim(NEW.currency_code)) = 0
          OR NEW.status NOT IN ('OPEN','PARTIALLY_RECEIVED','RECEIVED','CLOSED','CANCELLED')
          OR length(trim(NEW.write_id)) = 0
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_ORDER');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_order_update
        BEFORE UPDATE ON purchase_orders
        WHEN OLD.organization_id <> NEW.organization_id
          OR OLD.order_number <> NEW.order_number
          OR OLD.supplier_id <> NEW.supplier_id
          OR OLD.purchase_scope <> NEW.purchase_scope
          OR OLD.currency_code <> NEW.currency_code
          OR OLD.created_at <> NEW.created_at
          OR OLD.created_by <> NEW.created_by
          OR OLD.created_by_name <> NEW.created_by_name
          OR OLD.note <> NEW.note
          OR OLD.write_id <> NEW.write_id
          OR (OLD.status IN ('CLOSED','CANCELLED') AND NEW.status <> OLD.status)
          OR (OLD.status = 'RECEIVED' AND NEW.status IN ('OPEN','PARTIALLY_RECEIVED'))
          OR (OLD.status = 'PARTIALLY_RECEIVED' AND NEW.status = 'OPEN')
          OR (NEW.status = 'CLOSED' AND (NEW.closed_at IS NULL OR length(trim(COALESCE(NEW.close_reason,''))) = 0))
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_ORDER_UPDATE');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_order_line_insert
        BEFORE INSERT ON purchase_order_lines
        WHEN NEW.line_number <= 0
          OR NEW.ordered_quantity <= 0
          OR NEW.unit_price_minor < 0
          OR length(trim(NEW.item_name_snapshot)) = 0
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_ORDER_LINE');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_goods_receipt_insert
        BEFORE INSERT ON goods_receipts
        WHEN length(trim(NEW.organization_id)) = 0
          OR length(trim(NEW.receipt_number)) = 0
          OR length(trim(NEW.write_id)) = 0
          OR NEW.received_at <= 0
          OR (SELECT organization_id FROM purchase_orders WHERE id = NEW.purchase_order_id) <> NEW.organization_id
          OR (SELECT status FROM purchase_orders WHERE id = NEW.purchase_order_id) IN ('CLOSED','CANCELLED')
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_GOODS_RECEIPT');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_goods_receipt_line_insert
        BEFORE INSERT ON goods_receipt_lines
        WHEN NEW.received_quantity <= 0
          OR NEW.accepted_quantity < 0
          OR NEW.rejected_quantity < 0
          OR NEW.accepted_quantity + NEW.rejected_quantity <> NEW.received_quantity
          OR NEW.unit_cost_minor < 0
          OR (SELECT purchase_order_id FROM purchase_order_lines WHERE id = NEW.purchase_order_line_id)
             <> (SELECT purchase_order_id FROM goods_receipts WHERE id = NEW.goods_receipt_id)
          OR (SELECT COALESCE(SUM(accepted_quantity),0) FROM goods_receipt_lines WHERE purchase_order_line_id = NEW.purchase_order_line_id)
             + NEW.accepted_quantity
             > (SELECT ordered_quantity FROM purchase_order_lines WHERE id = NEW.purchase_order_line_id)
        BEGIN
            SELECT RAISE(ABORT, 'GOODS_RECEIPT_EXCEEDS_ORDERED_QUANTITY');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_invoice_purchase_order_insert
        BEFORE INSERT ON invoices
        WHEN NEW.purchase_order_id IS NOT NULL AND (
             NOT EXISTS (SELECT 1 FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR NEW.category <> 'PURCHASE'
          OR NEW.clientId <> (SELECT supplier_id FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR NEW.organization_id <> (SELECT organization_id FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR NEW.purchase_scope <> (SELECT purchase_scope FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR (SELECT status FROM purchase_orders WHERE id = NEW.purchase_order_id) IN ('CANCELLED')
        )
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_ORDER_INVOICE_LINK');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_invoice_purchase_order_update
        BEFORE UPDATE OF purchase_order_id, clientId, organization_id, purchase_scope, category ON invoices
        WHEN NEW.purchase_order_id IS NOT NULL AND (
             NOT EXISTS (SELECT 1 FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR NEW.category <> 'PURCHASE'
          OR NEW.clientId <> (SELECT supplier_id FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR NEW.organization_id <> (SELECT organization_id FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR NEW.purchase_scope <> (SELECT purchase_scope FROM purchase_orders WHERE id = NEW.purchase_order_id)
          OR (SELECT status FROM purchase_orders WHERE id = NEW.purchase_order_id) IN ('CANCELLED')
        )
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_ORDER_INVOICE_LINK');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_cycle_attachment_insert
        BEFORE INSERT ON purchase_cycle_attachments
        WHEN length(trim(NEW.organization_id)) = 0
          OR NEW.owner_type NOT IN ('PURCHASE_ORDER','GOODS_RECEIPT')
          OR length(trim(NEW.owner_id)) = 0
          OR length(trim(NEW.uri)) = 0
          OR length(trim(NEW.write_id)) = 0
          OR NEW.created_at <= 0
          OR (NEW.owner_type = 'PURCHASE_ORDER' AND NOT EXISTS (
              SELECT 1 FROM purchase_orders po WHERE po.id = NEW.owner_id AND po.organization_id = NEW.organization_id
          ))
          OR (NEW.owner_type = 'GOODS_RECEIPT' AND NOT EXISTS (
              SELECT 1 FROM goods_receipts gr WHERE gr.id = NEW.owner_id AND gr.organization_id = NEW.organization_id
          ))
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_CYCLE_ATTACHMENT');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_invoice_match_insert
        BEFORE INSERT ON purchase_invoice_matches
        WHEN NEW.invoice_amount_minor < 0
          OR NEW.payable_amount_minor < 0
          OR NEW.payable_amount_minor > NEW.invoice_amount_minor
          OR NEW.quantity_tolerance_units < 0
          OR NEW.price_tolerance_minor < 0
          OR NEW.status NOT IN ('MATCHED','WITHIN_TOLERANCE','OVERRIDDEN')
          OR (SELECT purchase_order_id FROM invoices WHERE id = NEW.invoice_id) <> NEW.purchase_order_id
          OR (SELECT organization_id FROM invoices WHERE id = NEW.invoice_id) <> NEW.organization_id
          OR (NEW.status = 'OVERRIDDEN' AND (length(trim(COALESCE(NEW.variance_reason,''))) = 0 OR length(trim(COALESCE(NEW.approved_by,''))) = 0))
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_THREE_WAY_MATCH');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_invoice_match_line_insert
        BEFORE INSERT ON purchase_invoice_match_lines
        WHEN NEW.ordered_quantity <= 0
          OR NEW.accepted_quantity < 0
          OR NEW.invoiced_quantity <= 0
          OR NEW.po_unit_price_minor < 0
          OR NEW.invoice_unit_price_minor < 0
          OR NEW.payable_amount_minor < 0
          OR (SELECT invoiceId FROM invoice_items WHERE id = NEW.invoice_item_id)
             <> (SELECT invoice_id FROM purchase_invoice_matches WHERE id = NEW.match_id)
          OR (SELECT purchase_order_id FROM purchase_order_lines WHERE id = NEW.purchase_order_line_id)
             <> (SELECT purchase_order_id FROM purchase_invoice_matches WHERE id = NEW.match_id)
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_THREE_WAY_MATCH_LINE');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_invoice_receipt_allocation_insert
        BEFORE INSERT ON purchase_invoice_receipt_allocations
        WHEN NEW.allocated_quantity <= 0
          OR length(trim(NEW.organization_id)) = 0
          OR length(trim(NEW.write_id)) = 0
          OR NEW.created_at <= 0
          OR (SELECT organization_id FROM purchase_invoice_matches WHERE id = (SELECT match_id FROM purchase_invoice_match_lines WHERE id = NEW.match_line_id)) <> NEW.organization_id
          OR (SELECT purchase_order_line_id FROM purchase_invoice_match_lines WHERE id = NEW.match_line_id)
             <> (SELECT purchase_order_line_id FROM goods_receipt_lines WHERE id = NEW.goods_receipt_line_id)
          OR (SELECT COALESCE(SUM(allocated_quantity),0) FROM purchase_invoice_receipt_allocations WHERE match_line_id = NEW.match_line_id)
             + NEW.allocated_quantity
             > (SELECT invoiced_quantity FROM purchase_invoice_match_lines WHERE id = NEW.match_line_id)
          OR (SELECT COALESCE(SUM(allocated_quantity),0) FROM purchase_invoice_receipt_allocations WHERE goods_receipt_line_id = NEW.goods_receipt_line_id)
             + NEW.allocated_quantity
             > (SELECT accepted_quantity FROM goods_receipt_lines WHERE id = NEW.goods_receipt_line_id)
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_RECEIPT_ALLOCATION');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_payment_override_insert
        BEFORE INSERT ON purchase_payment_overrides
        WHEN NEW.requested_amount_minor <= 0
          OR NEW.payable_before_override_minor < 0
          OR length(trim(NEW.reason)) = 0
          OR length(trim(NEW.approved_by)) = 0
          OR (SELECT organization_id FROM invoices WHERE id = NEW.invoice_id) <> NEW.organization_id
          OR (SELECT purchase_order_id FROM invoices WHERE id = NEW.invoice_id) IS NULL
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_PAYMENT_OVERRIDE');
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_payment_unreceived
        BEFORE INSERT ON payments
        WHEN NEW.reversedPaymentId IS NULL
          AND NEW.supplier_amount_minor > 0
          AND (SELECT purchase_order_id FROM invoices WHERE id = NEW.invoiceId) IS NOT NULL
          AND (
              (SELECT COALESCE(SUM(p.supplier_amount_minor),0)
                 FROM payments p
                WHERE p.invoiceId = NEW.invoiceId
                  AND p.reversedPaymentId IS NULL
                  AND p.supplier_amount_minor > 0
                  AND NOT EXISTS (SELECT 1 FROM payments r WHERE r.reversedPaymentId = p.id))
              + NEW.supplier_amount_minor
          ) > COALESCE((
              SELECT SUM(a.allocated_quantity * ml.invoice_unit_price_minor)
              FROM purchase_invoice_receipt_allocations a
              INNER JOIN purchase_invoice_match_lines ml ON ml.id = a.match_line_id
              INNER JOIN purchase_invoice_matches m ON m.id = ml.match_id
              WHERE m.invoice_id = NEW.invoiceId
          ), 0)
          AND NOT EXISTS (
              SELECT 1 FROM purchase_payment_overrides o
               WHERE o.invoice_id = NEW.invoiceId
                 AND (o.payment_request_id = NEW.id OR (length(trim(NEW.write_id)) > 0 AND o.payment_request_id = NEW.write_id))
          )
        BEGIN
            SELECT RAISE(ABORT, 'PAYMENT_EXCEEDS_RECEIVED_QUANTITY');
        END
        """.trimIndent()
    )

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS guard_purchase_order_shipment_source_insert
        BEFORE INSERT ON purchase_order_shipment_sources
        WHEN length(trim(NEW.organization_id)) = 0
          OR length(trim(NEW.shipment_id)) = 0
          OR length(trim(NEW.write_id)) = 0
          OR NEW.added_at <= 0
          OR (SELECT organization_id FROM purchase_orders WHERE id = NEW.purchase_order_id) <> NEW.organization_id
          OR (SELECT purchase_scope FROM purchase_orders WHERE id = NEW.purchase_order_id) <> 'INTERNATIONAL'
          OR (SELECT status FROM purchase_orders WHERE id = NEW.purchase_order_id) = 'CANCELLED'
        BEGIN
            SELECT RAISE(ABORT, 'INVALID_PURCHASE_ORDER_SHIPMENT_SOURCE');
        END
        """.trimIndent()
    )

    for (table in listOf("goods_receipts", "goods_receipt_lines", "purchase_invoice_matches", "purchase_invoice_match_lines", "purchase_invoice_receipt_allocations", "purchase_payment_overrides")) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS immutable_${table}_update BEFORE UPDATE ON $table " +
                "BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_PURCHASE_CYCLE_FACT'); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS immutable_${table}_delete BEFORE DELETE ON $table " +
                "BEGIN SELECT RAISE(ABORT, 'IMMUTABLE_PURCHASE_CYCLE_FACT'); END"
        )
    }
}
