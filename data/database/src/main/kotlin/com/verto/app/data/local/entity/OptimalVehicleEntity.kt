package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Tenant-scoped cache of vehicles supplied by an Optimal company already linked to Verto.
 *
 * The remote identifier is meaningful only inside the organization and linked client. The
 * composite key deliberately allows the same remote vehicle id to exist in another tenant.
 */
@Entity(
    tableName = "optimal_vehicles",
    primaryKeys = ["organization_id", "client_id", "remote_vehicle_id"],
    foreignKeys = [
        ForeignKey(
            entity = OptimalCompanyLinkEntity::class,
            parentColumns = ["organization_id", "client_id"],
            childColumns = ["organization_id", "client_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["organization_id", "client_id"],
            name = "index_optimal_vehicles_org_client",
        ),
        Index(
            value = ["organization_id", "client_id", "name"],
            name = "index_optimal_vehicles_org_client_name",
        ),
        Index(
            value = ["organization_id", "client_id", "vehicle_type"],
            name = "index_optimal_vehicles_org_client_type",
        ),
        Index(
            value = ["organization_id", "client_id", "plate_number"],
            name = "index_optimal_vehicles_org_client_plate",
        ),
    ],
)
data class OptimalVehicleEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "remote_vehicle_id") val remoteVehicleId: String,
    val name: String,
    @ColumnInfo(name = "vehicle_type") val vehicleType: String,
    @ColumnInfo(name = "plate_number") val plateNumber: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
