package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds tenant-scoped Optimal maintenance records and image metadata. */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_maintenance_records (
                organization_id TEXT NOT NULL,
                record_id TEXT NOT NULL,
                invoice_id TEXT NOT NULL,
                vehicle_client_id TEXT,
                vehicle_id TEXT,
                vehicle_name_snapshot TEXT NOT NULL,
                vehicle_type_snapshot TEXT NOT NULL,
                plate_number_snapshot TEXT NOT NULL,
                driver_or_delegate TEXT NOT NULL,
                notes TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, record_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_maintenance_records_org_invoice
            ON optimal_maintenance_records(organization_id, invoice_id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_maintenance_records_org_vehicle
            ON optimal_maintenance_records(organization_id, vehicle_client_id, vehicle_id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_optimal_maintenance_records_org_created_at
            ON optimal_maintenance_records(organization_id, created_at)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS optimal_maintenance_images (
                organization_id TEXT NOT NULL,
                image_id TEXT NOT NULL,
                record_id TEXT NOT NULL,
                local_uri TEXT NOT NULL,
                storage_path TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                byte_size INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                sync_status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(organization_id, image_id),
                FOREIGN KEY(organization_id, record_id)
                    REFERENCES optimal_maintenance_records(organization_id, record_id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_maintenance_images_org_record_order
            ON optimal_maintenance_images(organization_id, record_id, sort_order)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_optimal_maintenance_images_org_storage_path
            ON optimal_maintenance_images(organization_id, storage_path)
            """.trimIndent(),
        )
        installOptimalMaintenanceIntegrityGuards(db)
    }
}

/** Installed after migrations and on every open so fresh databases receive the same guards. */
fun installOptimalMaintenanceIntegrityGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS optimal_maintenance_vehicle_guard_insert
        BEFORE INSERT ON optimal_maintenance_records
        WHEN
            (NEW.vehicle_id IS NULL AND NEW.vehicle_client_id IS NOT NULL)
            OR (NEW.vehicle_id IS NOT NULL AND NEW.vehicle_client_id IS NULL)
            OR (
                NEW.vehicle_id IS NOT NULL
                AND NOT EXISTS (
                    SELECT 1 FROM optimal_vehicles
                    WHERE organization_id = NEW.organization_id
                      AND client_id = NEW.vehicle_client_id
                      AND remote_vehicle_id = NEW.vehicle_id
                )
            )
        BEGIN
            SELECT RAISE(ABORT, 'invalid tenant-scoped maintenance vehicle reference');
        END
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS optimal_maintenance_vehicle_guard_update
        BEFORE UPDATE OF organization_id, vehicle_client_id, vehicle_id ON optimal_maintenance_records
        WHEN
            (NEW.vehicle_id IS NULL AND NEW.vehicle_client_id IS NOT NULL)
            OR (NEW.vehicle_id IS NOT NULL AND NEW.vehicle_client_id IS NULL)
            OR (
                NEW.vehicle_id IS NOT NULL
                AND NOT EXISTS (
                    SELECT 1 FROM optimal_vehicles
                    WHERE organization_id = NEW.organization_id
                      AND client_id = NEW.vehicle_client_id
                      AND remote_vehicle_id = NEW.vehicle_id
                )
            )
        BEGIN
            SELECT RAISE(ABORT, 'invalid tenant-scoped maintenance vehicle reference');
        END
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS optimal_maintenance_image_path_guard_insert
        BEFORE INSERT ON optimal_maintenance_images
        WHEN NEW.storage_path != (
            'organizations/' || NEW.organization_id || '/optimal/maintenance/' ||
            NEW.record_id || '/images/' || NEW.image_id
        )
        BEGIN
            SELECT RAISE(ABORT, 'invalid tenant-scoped maintenance image path');
        END
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS optimal_maintenance_image_path_guard_update
        BEFORE UPDATE OF organization_id, record_id, image_id, storage_path ON optimal_maintenance_images
        WHEN NEW.storage_path != (
            'organizations/' || NEW.organization_id || '/optimal/maintenance/' ||
            NEW.record_id || '/images/' || NEW.image_id
        )
        BEGIN
            SELECT RAISE(ABORT, 'invalid tenant-scoped maintenance image path');
        END
        """.trimIndent(),
    )
}
