package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Party V2 tenant isolation.
 * Customer/supplier profiles are role-scoped data, therefore the local key must include organization_id.
 * Existing profiles are copied once for every matching Party role; orphaned legacy profiles remain under
 * an empty organization so they are never exposed by organization-scoped reads and can be repaired by sync.
 */
val MIGRATION_92_93 = object : Migration(92, 93) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `customer_profiles_v3` (
                `organization_id` TEXT NOT NULL,
                `party_id` TEXT NOT NULL,
                `segment` TEXT NOT NULL,
                `age_years` INTEGER,
                `purchase_contact_name` TEXT NOT NULL DEFAULT '',
                `business_activity` TEXT NOT NULL DEFAULT '',
                `workplace_name` TEXT NOT NULL DEFAULT '',
                `shop_name` TEXT NOT NULL DEFAULT '',
                `workshop_name` TEXT NOT NULL DEFAULT '',
                `vehicle_models` TEXT NOT NULL DEFAULT '',
                `workshop_worker_count` INTEGER,
                `updated_at` INTEGER NOT NULL,
                `sync_revision` INTEGER NOT NULL DEFAULT 0,
                `dirty` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`organization_id`, `party_id`),
                FOREIGN KEY(`party_id`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `customer_profiles_v3`
            SELECT pr.`organization_id`, cp.`party_id`, cp.`segment`, cp.`age_years`, cp.`purchase_contact_name`,
                   cp.`business_activity`, cp.`workplace_name`, cp.`shop_name`, cp.`workshop_name`, cp.`vehicle_models`,
                   cp.`workshop_worker_count`, cp.`updated_at`, cp.`sync_revision`, cp.`dirty`
            FROM `customer_profiles` cp
            JOIN `party_roles` pr ON pr.`party_id`=cp.`party_id` AND pr.`role`='CUSTOMER'
            WHERE pr.`deleted_at` IS NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `customer_profiles_v3`
            SELECT '', cp.`party_id`, cp.`segment`, cp.`age_years`, cp.`purchase_contact_name`,
                   cp.`business_activity`, cp.`workplace_name`, cp.`shop_name`, cp.`workshop_name`, cp.`vehicle_models`,
                   cp.`workshop_worker_count`, cp.`updated_at`, cp.`sync_revision`, cp.`dirty`
            FROM `customer_profiles` cp
            WHERE NOT EXISTS (
                SELECT 1 FROM `party_roles` pr
                WHERE pr.`party_id`=cp.`party_id` AND pr.`role`='CUSTOMER' AND pr.`deleted_at` IS NULL
            )
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `customer_profiles`")
        db.execSQL("ALTER TABLE `customer_profiles_v3` RENAME TO `customer_profiles`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_profiles_party_id` ON `customer_profiles` (`party_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_profiles_org_segment` ON `customer_profiles` (`organization_id`,`segment`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supplier_profiles_v3` (
                `organization_id` TEXT NOT NULL,
                `party_id` TEXT NOT NULL,
                `scope` TEXT NOT NULL,
                `country` TEXT NOT NULL DEFAULT '',
                `currency_code` TEXT NOT NULL DEFAULT '',
                `specialty` TEXT NOT NULL DEFAULT '',
                `updated_at` INTEGER NOT NULL,
                `sync_revision` INTEGER NOT NULL DEFAULT 0,
                `dirty` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`organization_id`, `party_id`),
                FOREIGN KEY(`party_id`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `supplier_profiles_v3`
            SELECT pr.`organization_id`, sp.`party_id`, sp.`scope`, sp.`country`, sp.`currency_code`, sp.`specialty`,
                   sp.`updated_at`, sp.`sync_revision`, sp.`dirty`
            FROM `supplier_profiles` sp
            JOIN `party_roles` pr ON pr.`party_id`=sp.`party_id` AND pr.`role`='SUPPLIER'
            WHERE pr.`deleted_at` IS NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `supplier_profiles_v3`
            SELECT '', sp.`party_id`, sp.`scope`, sp.`country`, sp.`currency_code`, sp.`specialty`,
                   sp.`updated_at`, sp.`sync_revision`, sp.`dirty`
            FROM `supplier_profiles` sp
            WHERE NOT EXISTS (
                SELECT 1 FROM `party_roles` pr
                WHERE pr.`party_id`=sp.`party_id` AND pr.`role`='SUPPLIER' AND pr.`deleted_at` IS NULL
            )
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `supplier_profiles`")
        db.execSQL("ALTER TABLE `supplier_profiles_v3` RENAME TO `supplier_profiles`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_profiles_party_id` ON `supplier_profiles` (`party_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_profiles_org_scope` ON `supplier_profiles` (`organization_id`,`scope`)")
    }
}
