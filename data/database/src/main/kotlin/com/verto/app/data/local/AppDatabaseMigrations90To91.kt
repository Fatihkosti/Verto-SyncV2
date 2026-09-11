package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v376 price-list cutover:
 * - draft rows are no longer persisted;
 * - templates retain inventory references only;
 * - legacy price_list_header / price_list_items are physically removed.
 */
val MIGRATION_90_91 = object : Migration(90, 91) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `price_list_templates` (
                `id` TEXT NOT NULL,
                `organization_id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `is_favorite` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_list_templates_organization_id` ON `price_list_templates` (`organization_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_list_templates_organization_id_name` ON `price_list_templates` (`organization_id`, `name`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `price_list_template_items` (
                `template_id` TEXT NOT NULL,
                `inventory_item_id` TEXT NOT NULL,
                `sort_order` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`template_id`, `inventory_item_id`),
                FOREIGN KEY(`template_id`) REFERENCES `price_list_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`inventory_item_id`) REFERENCES `inventory_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_list_template_items_template_id` ON `price_list_template_items` (`template_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_list_template_items_inventory_item_id` ON `price_list_template_items` (`inventory_item_id`)")

        // Preserve useful selections without copying stale prices/names into the new model.
        db.execSQL(
            """
            INSERT OR IGNORE INTO `price_list_templates`
                (`id`, `organization_id`, `name`, `is_favorite`, `created_at`, `updated_at`)
            SELECT
                '00000000-0000-0000-0000-00000000a901',
                COALESCE((SELECT `organization_id` FROM `organization_settings_local` LIMIT 1), 'legacy'),
                'كشف سابق', 0,
                CAST(strftime('%s','now') AS INTEGER) * 1000,
                CAST(strftime('%s','now') AS INTEGER) * 1000
            WHERE EXISTS (SELECT 1 FROM `price_list_items` LIMIT 1)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `price_list_template_items` (`template_id`, `inventory_item_id`, `sort_order`)
            SELECT '00000000-0000-0000-0000-00000000a901', i.`id`, p.`sortOrder`
            FROM `price_list_items` p
            JOIN `inventory_items` i
              ON ((p.`partNumber` <> '' AND i.`partNumber` = p.`partNumber`)
                  OR (p.`partNumber` = '' AND i.`name` = p.`name`))
            WHERE 1 = (
                SELECT COUNT(*) FROM `inventory_items` m
                WHERE (p.`partNumber` <> '' AND m.`partNumber` = p.`partNumber`)
                   OR (p.`partNumber` = '' AND m.`name` = p.`name`)
            )
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS `price_list_items`")
        db.execSQL("DROP TABLE IF EXISTS `price_list_header`")
    }
}
