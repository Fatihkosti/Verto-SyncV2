package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Party V2 customer-profile normalization.
 * Migrates the overloaded legacy client fields into single-purpose customer profile columns.
 */
val MIGRATION_91_92 = object : Migration(91, 92) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `customer_profiles_v2` (
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
                PRIMARY KEY(`party_id`),
                FOREIGN KEY(`party_id`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO `customer_profiles_v2` (
                `party_id`, `segment`, `age_years`, `purchase_contact_name`, `business_activity`,
                `workplace_name`, `shop_name`, `workshop_name`, `vehicle_models`,
                `workshop_worker_count`, `updated_at`, `sync_revision`, `dirty`
            )
            SELECT
                cp.`party_id`,
                cp.`segment`,
                CASE
                    WHEN cp.`segment`='INDIVIDUAL'
                     AND TRIM(COALESCE(c.`specialty`,'')) <> ''
                     AND TRIM(COALESCE(c.`specialty`,'')) NOT GLOB '*[^0-9]*'
                    THEN CAST(TRIM(c.`specialty`) AS INTEGER)
                    ELSE NULL
                END,
                CASE WHEN cp.`segment` IN ('COMPANY','INSTITUTION') THEN COALESCE(c.`specialty`,'') ELSE '' END,
                CASE
                    WHEN cp.`segment` IN ('COMPANY','INSTITUTION','DISTRIBUTOR') THEN COALESCE(c.`workplace`,'')
                    WHEN cp.`segment` IN ('WORKSHOP_OWNER','TRADER','COMPETITOR') THEN COALESCE(c.`specialty`,'')
                    ELSE ''
                END,
                CASE WHEN cp.`segment`='INDIVIDUAL' THEN COALESCE(c.`workplace`,'') ELSE '' END,
                CASE WHEN cp.`segment` IN ('TRADER','COMPETITOR') THEN COALESCE(c.`workplace`,'') ELSE '' END,
                CASE WHEN cp.`segment`='WORKSHOP_OWNER' THEN COALESCE(c.`workplace`,'') ELSE '' END,
                CASE
                    WHEN cp.`segment`='INDIVIDUAL' THEN
                        CASE
                            WHEN TRIM(COALESCE(cp.`vehicle_information`,''))='' THEN COALESCE(c.`secondaryPhones`,'')
                            WHEN TRIM(COALESCE(c.`secondaryPhones`,''))='' THEN cp.`vehicle_information`
                            ELSE cp.`vehicle_information` || '||' || c.`secondaryPhones`
                        END
                    WHEN cp.`segment` IN ('COMPANY','INSTITUTION') THEN REPLACE(COALESCE(cp.`vehicle_information`,''), ',', '||')
                    ELSE COALESCE(cp.`vehicle_information`,'')
                END,
                cp.`workshop_worker_count`,
                cp.`updated_at`,
                cp.`sync_revision`,
                cp.`dirty`
            FROM `customer_profiles` cp
            JOIN `clients` c ON c.`id` = cp.`party_id`
            """.trimIndent()
        )

        db.execSQL("DROP TABLE `customer_profiles`")
        db.execSQL("ALTER TABLE `customer_profiles_v2` RENAME TO `customer_profiles`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customer_profiles_party_id` ON `customer_profiles` (`party_id`)")
    }
}
