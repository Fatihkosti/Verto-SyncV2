package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F255: covering indexes for aging, period analytics, PPV and supplier-payment timing. */
val MIGRATION_71_72 = object : Migration(71, 72) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_analytics_aging` ON `invoices` (`category`, `status`, `lifecycle_status`, `dueDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_analytics_period` ON `invoices` (`category`, `lifecycle_status`, `createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_items_inventory_item` ON `invoice_items` (`inventoryItemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_allocations_invoice_created` ON `payment_allocations` (`invoice_id`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_matches_org_matched_at` ON `purchase_invoice_matches` (`organization_id`, `matched_at`)")
    }
}
