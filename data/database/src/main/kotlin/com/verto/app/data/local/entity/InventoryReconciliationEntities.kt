package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** v258 local cutover gate. A migrated database starts PENDING; a fresh v74 database has no row. */
@Entity(tableName = "inventory_reconciliation_control")
data class InventoryReconciliationControlEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "control_key") val controlKey: String,
    @ColumnInfo(name = "contract_version") val contractVersion: Int,
    val state: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A server-approved, immutable reconciliation result for one organization/item/contract. */
@Entity(
    tableName = "inventory_reconciliation_markers",
    primaryKeys = ["organization_id", "item_id", "contract_version"],
    indices = [
        Index(value = ["item_id"], name = "index_inventory_reconciliation_markers_item"),
        Index(value = ["organization_id", "state"], name = "index_inventory_reconciliation_markers_org_state"),
        Index(value = ["marker_checksum"], unique = true, name = "index_inventory_reconciliation_markers_checksum"),
    ],
)
data class InventoryReconciliationMarkerEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "contract_version") val contractVersion: Int,
    @ColumnInfo(name = "authority_kind") val authorityKind: String,
    @ColumnInfo(name = "source_device_id") val sourceDeviceId: String? = null,
    @ColumnInfo(name = "canonical_snapshot") val canonicalSnapshot: Long,
    @ColumnInfo(name = "authoritative_legacy_balance") val authoritativeLegacyBalance: Long,
    @ColumnInfo(name = "reconciliation_delta") val reconciliationDelta: Long,
    @ColumnInfo(name = "reconciliation_movement_id") val reconciliationMovementId: String? = null,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "server_sequence") val serverSequence: Long? = null,
    @ColumnInfo(name = "marker_checksum") val markerChecksum: String,
    val state: String,
    @ColumnInfo(name = "approved_at") val approvedAt: Long,
    @ColumnInfo(name = "server_accepted_at") val serverAcceptedAt: Long,
    @ColumnInfo(name = "approved_by") val approvedBy: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)

/** Cases that cannot be proven equal to the central authority are isolated instead of guessed. */
@Entity(
    tableName = "inventory_reconciliation_quarantine",
    indices = [
        Index(value = ["organization_id", "item_id", "contract_version"], unique = true, name = "index_inventory_reconciliation_quarantine_identity"),
        Index(value = ["organization_id", "resolved_at"], name = "index_inventory_reconciliation_quarantine_open"),
    ],
)
data class InventoryReconciliationQuarantineEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "contract_version") val contractVersion: Int,
    val reason: String,
    @ColumnInfo(name = "local_legacy_balance") val localLegacyBalance: Long? = null,
    @ColumnInfo(name = "authoritative_legacy_balance") val authoritativeLegacyBalance: Long? = null,
    @ColumnInfo(name = "canonical_snapshot") val canonicalSnapshot: Long? = null,
    @ColumnInfo(name = "marker_checksum") val markerChecksum: String? = null,
    @ColumnInfo(name = "detected_at") val detectedAt: Long,
    val details: String = "",
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
)

/** Transaction-scoped bypass used only by the reconciler while installing the authoritative snapshot. */
@Entity(tableName = "inventory_reconciliation_apply_context")
data class InventoryReconciliationApplyContextEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    val token: String,
)
