package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v233 retires the unscoped shipment model.
 *
 * Legacy rows cannot be safely projected into organization-scoped Logistics V2 because the
 * legacy parent row has no organization id. Preserve every row as executable restore SQL before
 * removing the runtime tables; the archive is local-only and is not part of Logistics V2 sync.
 */
val MIGRATION_59_60 = object : Migration(59, 60) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `retired_shipment_archive` (
                `archive_id` TEXT NOT NULL,
                `source_table` TEXT NOT NULL,
                `shipment_id` TEXT NOT NULL,
                `source_schema_version` INTEGER NOT NULL,
                `restore_sql` TEXT NOT NULL,
                PRIMARY KEY(`archive_id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_retired_shipment_archive_source_table` ON `retired_shipment_archive` (`source_table`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_retired_shipment_archive_shipment_id` ON `retired_shipment_archive` (`shipment_id`)")

        archiveLegacyShipments(db)
        archiveLegacyStops(db)
        archiveLegacyDocuments(db)
        archiveLegacyCosts(db)
        archiveLegacyReceipts(db)

        db.execSQL("DROP TABLE IF EXISTS shipment_documents")
        db.execSQL("DROP TABLE IF EXISTS shipment_receipts")
        db.execSQL("DROP TABLE IF EXISTS shipment_costs")
        db.execSQL("DROP TABLE IF EXISTS shipment_stops")
        db.execSQL("DROP TABLE IF EXISTS shipments")
    }

    private fun archiveLegacyShipments(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR REPLACE INTO retired_shipment_archive
                (archive_id, source_table, shipment_id, source_schema_version, restore_sql)
            SELECT
                'shipments:' || id, 'shipments', id, 59,
                'INSERT INTO shipments(id,shipmentNumber,origin,destination,title,status,createdAt,expectedArrivalDate,actualArrivalDate,notes) VALUES(' ||
                quote(id) || ',' || quote(shipmentNumber) || ',' || quote(origin) || ',' || quote(destination) || ',' || quote(title) || ',' ||
                quote(status) || ',' || quote(createdAt) || ',' || quote(expectedArrivalDate) || ',' || quote(actualArrivalDate) || ',' || quote(notes) || ');'
            FROM shipments
            """.trimIndent(),
        )
    }

    private fun archiveLegacyStops(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR REPLACE INTO retired_shipment_archive
                (archive_id, source_table, shipment_id, source_schema_version, restore_sql)
            SELECT
                'shipment_stops:' || id, 'shipment_stops', shipmentId, 59,
                'INSERT INTO shipment_stops(id,shipmentId,`order`,locationName,arrivedAt,legCost,isDestination,semanticType) VALUES(' ||
                quote(id) || ',' || quote(shipmentId) || ',' || quote(`order`) || ',' || quote(locationName) || ',' || quote(arrivedAt) || ',' ||
                quote(legCost) || ',' || quote(isDestination) || ',' || quote(semanticType) || ');'
            FROM shipment_stops
            """.trimIndent(),
        )
    }

    private fun archiveLegacyDocuments(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR REPLACE INTO retired_shipment_archive
                (archive_id, source_table, shipment_id, source_schema_version, restore_sql)
            SELECT
                'shipment_documents:' || id, 'shipment_documents', shipmentId, 59,
                'INSERT INTO shipment_documents(id,organizationId,userId,shipmentId,stopId,displayName,mimeType,sizeBytes,privateUri,sha256,createdAt) VALUES(' ||
                quote(id) || ',' || quote(organizationId) || ',' || quote(userId) || ',' || quote(shipmentId) || ',' || quote(stopId) || ',' ||
                quote(displayName) || ',' || quote(mimeType) || ',' || quote(sizeBytes) || ',' || quote(privateUri) || ',' || quote(sha256) || ',' || quote(createdAt) || ');'
            FROM shipment_documents
            """.trimIndent(),
        )
    }

    private fun archiveLegacyCosts(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR REPLACE INTO retired_shipment_archive
                (archive_id, source_table, shipment_id, source_schema_version, restore_sql)
            SELECT
                'shipment_costs:' || id, 'shipment_costs', shipmentId, 59,
                'INSERT INTO shipment_costs(id,shipmentId,type,amount,description) VALUES(' ||
                quote(id) || ',' || quote(shipmentId) || ',' || quote(type) || ',' || quote(amount) || ',' || quote(description) || ');'
            FROM shipment_costs
            """.trimIndent(),
        )
    }

    private fun archiveLegacyReceipts(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR REPLACE INTO retired_shipment_archive
                (archive_id, source_table, shipment_id, source_schema_version, restore_sql)
            SELECT
                'shipment_receipts:' || id, 'shipment_receipts', shipmentId, 59,
                'INSERT INTO shipment_receipts(id,shipmentId,isComplete,damagedItems,missingItems,receivedAt,receivedBy) VALUES(' ||
                quote(id) || ',' || quote(shipmentId) || ',' || quote(isComplete) || ',' || quote(damagedItems) || ',' || quote(missingItems) || ',' ||
                quote(receivedAt) || ',' || quote(receivedBy) || ');'
            FROM shipment_receipts
            """.trimIndent(),
        )
    }
}
