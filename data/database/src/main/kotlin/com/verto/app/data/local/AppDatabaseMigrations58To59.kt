package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val V232_SCHEMA_STATEMENTS = listOf(
    """
    CREATE TABLE IF NOT EXISTS `logistics_shortage_settlements` (
        `organization_id` TEXT NOT NULL, `id` TEXT NOT NULL, `shipment_id` TEXT NOT NULL,
        `shortage_id` TEXT NOT NULL, `type` TEXT NOT NULL, `quantity` INTEGER NOT NULL,
        `compensation_amount` TEXT, `currency` TEXT, `exchange_rate_snapshot` TEXT,
        `base_currency_amount` TEXT, `occurred_at` INTEGER NOT NULL, `employee_id` TEXT,
        `employee_name_snapshot` TEXT, `note` TEXT NOT NULL, `request_id` TEXT NOT NULL,
        PRIMARY KEY(`organization_id`, `id`),
        FOREIGN KEY(`organization_id`, `shipment_id`) REFERENCES `logistics_shipments`(`organization_id`, `id`) ON UPDATE CASCADE ON DELETE CASCADE,
        FOREIGN KEY(`organization_id`, `shortage_id`) REFERENCES `logistics_shortages`(`organization_id`, `id`) ON UPDATE CASCADE ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS `index_logistics_shortage_settlements_organization_id_shipment_id` ON `logistics_shortage_settlements` (`organization_id`, `shipment_id`)",
    "CREATE INDEX IF NOT EXISTS `index_logistics_shortage_settlements_organization_id_shortage_id` ON `logistics_shortage_settlements` (`organization_id`, `shortage_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_logistics_shortage_settlements_organization_id_shipment_id_request_id` ON `logistics_shortage_settlements` (`organization_id`, `shipment_id`, `request_id`)",
    """
    CREATE TABLE IF NOT EXISTS `logistics_late_cost_adjustments` (
        `organization_id` TEXT NOT NULL, `id` TEXT NOT NULL, `shipment_id` TEXT NOT NULL,
        `cost_id` TEXT NOT NULL, `recorded_at` INTEGER NOT NULL, `employee_id` TEXT,
        `employee_name_snapshot` TEXT, `request_id` TEXT NOT NULL,
        PRIMARY KEY(`organization_id`, `id`),
        FOREIGN KEY(`organization_id`, `shipment_id`) REFERENCES `logistics_shipments`(`organization_id`, `id`) ON UPDATE CASCADE ON DELETE CASCADE,
        FOREIGN KEY(`organization_id`, `cost_id`) REFERENCES `logistics_costs`(`organization_id`, `id`) ON UPDATE CASCADE ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS `index_logistics_late_cost_adjustments_organization_id_shipment_id` ON `logistics_late_cost_adjustments` (`organization_id`, `shipment_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_logistics_late_cost_adjustments_organization_id_cost_id` ON `logistics_late_cost_adjustments` (`organization_id`, `cost_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_logistics_late_cost_adjustments_organization_id_shipment_id_request_id` ON `logistics_late_cost_adjustments` (`organization_id`, `shipment_id`, `request_id`)",
    """
    CREATE TABLE IF NOT EXISTS `logistics_late_cost_allocations` (
        `organization_id` TEXT NOT NULL, `id` TEXT NOT NULL, `adjustment_id` TEXT NOT NULL,
        `shipment_id` TEXT NOT NULL, `shipment_line_id` TEXT NOT NULL, `amount` TEXT NOT NULL,
        PRIMARY KEY(`organization_id`, `id`),
        FOREIGN KEY(`organization_id`, `adjustment_id`) REFERENCES `logistics_late_cost_adjustments`(`organization_id`, `id`) ON UPDATE CASCADE ON DELETE CASCADE,
        FOREIGN KEY(`organization_id`, `shipment_id`) REFERENCES `logistics_shipments`(`organization_id`, `id`) ON UPDATE CASCADE ON DELETE CASCADE,
        FOREIGN KEY(`organization_id`, `shipment_line_id`) REFERENCES `logistics_shipment_lines`(`organization_id`, `id`) ON UPDATE CASCADE ON DELETE RESTRICT
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS `index_logistics_late_cost_allocations_organization_id_adjustment_id` ON `logistics_late_cost_allocations` (`organization_id`, `adjustment_id`)",
    "CREATE INDEX IF NOT EXISTS `index_logistics_late_cost_allocations_organization_id_shipment_id` ON `logistics_late_cost_allocations` (`organization_id`, `shipment_id`)",
    "CREATE INDEX IF NOT EXISTS `index_logistics_late_cost_allocations_organization_id_shipment_line_id` ON `logistics_late_cost_allocations` (`organization_id`, `shipment_line_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_logistics_late_cost_allocations_organization_id_adjustment_id_shipment_line_id` ON `logistics_late_cost_allocations` (`organization_id`, `adjustment_id`, `shipment_line_id`)",
)

/** v232 final receiving settlements: shortage outcomes and post-close cost adjustments. */
val MIGRATION_58_59 = object : Migration(58, 59) {
    override fun migrate(db: SupportSQLiteDatabase) {
        V232_SCHEMA_STATEMENTS.forEach(db::execSQL)
    }
}
