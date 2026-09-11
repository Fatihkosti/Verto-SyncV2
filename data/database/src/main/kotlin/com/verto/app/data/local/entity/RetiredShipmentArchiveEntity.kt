package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-only safety archive created when v233 retires the unscoped shipment model.
 * It is not part of Logistics V2 runtime and is never synchronized remotely.
 * [restoreSql] preserves each legacy row losslessly enough for controlled/manual recovery.
 */
@Entity(
    tableName = "retired_shipment_archive",
    indices = [Index("source_table"), Index("shipment_id")],
)
data class RetiredShipmentArchiveEntity(
    @PrimaryKey @ColumnInfo(name = "archive_id") val archiveId: String,
    @ColumnInfo(name = "source_table") val sourceTable: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "source_schema_version") val sourceSchemaVersion: Int,
    @ColumnInfo(name = "restore_sql") val restoreSql: String,
)
