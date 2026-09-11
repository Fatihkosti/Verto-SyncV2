package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F254: local recoverable invoice editor drafts. */
val MIGRATION_70_71 = object : Migration(70, 71) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createInvoiceDraftHeader(db)
        createInvoiceDraftLines(db)
        createInvoiceDraftMaintenanceImages(db)
    }
}

private fun createInvoiceDraftHeader(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `invoice_editor_drafts` (
            `draft_key` TEXT NOT NULL,
            `organization_id` TEXT NOT NULL,
            `existing_invoice_id` TEXT,
            `route_client_id` TEXT NOT NULL,
            `is_international` INTEGER NOT NULL,
            `is_sale` INTEGER NOT NULL,
            `payment_mode` TEXT NOT NULL,
            `selected_client_id` TEXT NOT NULL,
            `selected_date_millis` INTEGER,
            `due_days` TEXT NOT NULL,
            `notes` TEXT NOT NULL,
            `paid_amount` TEXT NOT NULL,
            `transaction_currency_code` TEXT NOT NULL,
            `exchange_rate` TEXT NOT NULL,
            `write_id` TEXT NOT NULL,
            `draft_item_name` TEXT NOT NULL,
            `draft_item_quantity` TEXT NOT NULL,
            `draft_item_sell_price` TEXT NOT NULL,
            `draft_item_buy_price` TEXT NOT NULL,
            `draft_inventory_item_id` TEXT NOT NULL,
            `maintenance_enabled` INTEGER NOT NULL,
            `maintenance_expanded` INTEGER NOT NULL,
            `maintenance_owner_organization_id` TEXT NOT NULL,
            `maintenance_owner_client_id` TEXT NOT NULL,
            `maintenance_record_id` TEXT NOT NULL,
            `maintenance_created_at` INTEGER NOT NULL,
            `maintenance_vehicle_query` TEXT NOT NULL,
            `maintenance_vehicle_org_id` TEXT,
            `maintenance_vehicle_client_id` TEXT,
            `maintenance_vehicle_remote_id` TEXT,
            `maintenance_vehicle_name` TEXT,
            `maintenance_vehicle_type` TEXT,
            `maintenance_vehicle_plate` TEXT,
            `maintenance_vehicle_updated_at` INTEGER,
            `maintenance_plate_number` TEXT NOT NULL,
            `maintenance_driver_or_delegate` TEXT NOT NULL,
            `maintenance_notes` TEXT NOT NULL,
            `updated_at` INTEGER NOT NULL,
            PRIMARY KEY(`draft_key`)
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_editor_drafts_org_updated` ON `invoice_editor_drafts` (`organization_id`, `updated_at`)")
}

private fun createInvoiceDraftLines(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `invoice_editor_draft_lines` (
            `id` TEXT NOT NULL,
            `draft_key` TEXT NOT NULL,
            `sort_order` INTEGER NOT NULL,
            `name` TEXT NOT NULL,
            `quantity` TEXT NOT NULL,
            `sell_price` TEXT NOT NULL,
            `buy_price` TEXT NOT NULL,
            `item_category` TEXT NOT NULL,
            `inventory_item_id` TEXT NOT NULL,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`draft_key`) REFERENCES `invoice_editor_drafts`(`draft_key`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_editor_draft_lines_draft_key` ON `invoice_editor_draft_lines` (`draft_key`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_invoice_editor_draft_lines_order` ON `invoice_editor_draft_lines` (`draft_key`, `sort_order`)")
}

private fun createInvoiceDraftMaintenanceImages(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `invoice_editor_draft_maintenance_images` (
            `image_id` TEXT NOT NULL,
            `draft_key` TEXT NOT NULL,
            `local_uri` TEXT NOT NULL,
            `mime_type` TEXT NOT NULL,
            `byte_size` INTEGER NOT NULL,
            `sort_order` INTEGER NOT NULL,
            PRIMARY KEY(`image_id`),
            FOREIGN KEY(`draft_key`) REFERENCES `invoice_editor_drafts`(`draft_key`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_editor_draft_maintenance_images_draft_key` ON `invoice_editor_draft_maintenance_images` (`draft_key`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_invoice_editor_draft_images_order` ON `invoice_editor_draft_maintenance_images` (`draft_key`, `sort_order`)")
}
