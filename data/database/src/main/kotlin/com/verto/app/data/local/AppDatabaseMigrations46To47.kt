package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds local semantic shipment stages and private-document metadata without changing remote payloads. */
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Existing free-text stages are deliberately not inferred. Every legacy row becomes ROUTE.
        db.execSQL("ALTER TABLE shipment_stops ADD COLUMN semanticType TEXT NOT NULL DEFAULT 'ROUTE'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shipment_documents (
                id TEXT NOT NULL,
                organizationId TEXT NOT NULL,
                userId TEXT NOT NULL,
                shipmentId TEXT NOT NULL,
                stopId TEXT,
                displayName TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                privateUri TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(shipmentId) REFERENCES shipments(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(stopId) REFERENCES shipment_stops(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shipment_documents_shipmentId ON shipment_documents(shipmentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shipment_documents_stopId ON shipment_documents(stopId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shipment_documents_organizationId_userId_shipmentId ON shipment_documents(organizationId, userId, shipmentId)")
    }
}
