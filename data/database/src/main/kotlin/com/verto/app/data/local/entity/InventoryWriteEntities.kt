package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** v259: durable idempotency claim for one local stock command. */
@Entity(
    tableName = "inventory_write_guards",
    indices = [
        Index(value = ["organization_id", "command_id"], unique = true, name = "index_inventory_write_guard_command"),
        Index(value = ["organization_id", "idempotency_key"], unique = true, name = "index_inventory_write_guard_idempotency"),
    ],
)
class InventoryWriteGuardEntity {
    @PrimaryKey var id: String = ""
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    @ColumnInfo(name = "command_id") var commandId: String = ""
    @ColumnInfo(name = "idempotency_key") var idempotencyKey: String = ""
    @ColumnInfo(name = "operation") var operation: String = ""
    @ColumnInfo(name = "actor_id") var actorId: String = ""
    @ColumnInfo(name = "actor_name") var actorName: String = ""
    @ColumnInfo(name = "created_at") var createdAt: Long = 0L
}

/** v259: transactional outbox row emitted by the same Room transaction as stock + movement. */
@Entity(
    tableName = "inventory_stock_outbox",
    indices = [
        Index(value = ["organization_id", "movement_id"], unique = true, name = "index_inventory_stock_outbox_movement"),
        Index(value = ["organization_id", "command_id"], name = "index_inventory_stock_outbox_command"),
        Index(value = ["organization_id", "sync_state", "created_at"], name = "index_inventory_stock_outbox_delivery"),
    ],
)
class InventoryStockOutboxEntity {
    @PrimaryKey var id: String = ""
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    @ColumnInfo(name = "command_id") var commandId: String = ""
    @ColumnInfo(name = "idempotency_key") var idempotencyKey: String = ""
    @ColumnInfo(name = "movement_id") var movementId: String = ""
    @ColumnInfo(name = "item_id") var itemId: String = ""
    @ColumnInfo(name = "operation") var operation: String = ""
    @ColumnInfo(name = "signed_base_quantity") var signedBaseQuantity: Long = 0L
    @ColumnInfo(name = "sync_state", defaultValue = "'PENDING'") var syncState: String = "PENDING"
    @ColumnInfo(name = "attempt_count", defaultValue = "0") var attemptCount: Int = 0
    @ColumnInfo(name = "next_attempt_at", defaultValue = "0") var nextAttemptAt: Long = 0L
    @ColumnInfo(name = "last_error") var lastError: String? = null
    @ColumnInfo(name = "ack_sequence") var ackSequence: Long? = null
    @ColumnInfo(name = "acknowledged_at") var acknowledgedAt: Long? = null
    @ColumnInfo(name = "created_at") var createdAt: Long = 0L
}

/** v263: durable, deduplicated operational conflict created after convergent server ordering. */
@Entity(
    tableName = "inventory_sync_conflicts",
    indices = [
        Index(value = ["organization_id", "status", "detected_at"], name = "index_inventory_sync_conflicts_status"),
        Index(value = ["organization_id", "conflict_key"], unique = true, name = "index_inventory_sync_conflicts_key"),
    ],
)
data class InventorySyncConflictEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "conflict_key") val conflictKey: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "conflict_type") val conflictType: String,
    @ColumnInfo(name = "server_sequence") val serverSequence: Long,
    @ColumnInfo(name = "projected_quantity") val projectedQuantity: Long,
    val status: String = "OPEN",
    val details: String = "",
    @ColumnInfo(name = "detected_at") val detectedAt: Long,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
)

@Entity(tableName = "inventory_sync_cursors")
data class InventorySyncCursorEntity(
    @PrimaryKey @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "last_server_sequence") val lastServerSequence: Long,
    @ColumnInfo(name = "last_cost_sequence") val lastCostSequence: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "inventory_cost_outbox",
    indices = [
        Index(value = ["organization_id", "cost_revision_id"], unique = true, name = "index_inventory_cost_outbox_revision"),
        Index(value = ["organization_id", "sync_state", "next_attempt_at", "created_at"], name = "index_inventory_cost_outbox_delivery"),
    ],
)
data class InventoryCostOutboxEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "cost_revision_id") val costRevisionId: String,
    @ColumnInfo(name = "sync_state") val syncState: String = "PENDING",
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long = 0L,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "ack_sequence") val ackSequence: Long? = null,
    @ColumnInfo(name = "acknowledged_at") val acknowledgedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
