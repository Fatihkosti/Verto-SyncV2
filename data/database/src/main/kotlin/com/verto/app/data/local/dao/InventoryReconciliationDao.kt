package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.AuthoritativeInventoryReconciliationMarker
import com.verto.app.data.local.INVENTORY_LEDGER_CONTRACT_VERSION
import com.verto.app.data.local.INVENTORY_RECONCILIATION_CONTROL_KEY
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryMovementKind
import com.verto.app.data.local.entity.InventoryReconciliationApplyContextEntity
import com.verto.app.data.local.entity.InventoryReconciliationControlEntity
import com.verto.app.data.local.entity.InventoryReconciliationMarkerEntity
import com.verto.app.data.local.entity.InventoryReconciliationQuarantineEntity
import com.verto.app.data.local.entity.MovementType
import com.verto.app.data.local.requireCanonicalInventoryContract
import java.util.UUID

@Dao
abstract class InventoryReconciliationDao {
    @Query("SELECT * FROM inventory_reconciliation_control WHERE control_key = :controlKey LIMIT 1")
    protected abstract suspend fun getControlRaw(controlKey: String): InventoryReconciliationControlEntity?

    suspend fun getControl(): InventoryReconciliationControlEntity? =
        getControlRaw(INVENTORY_RECONCILIATION_CONTROL_KEY)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertControl(entity: InventoryReconciliationControlEntity)

    suspend fun setControlState(state: String, contractVersion: Int, now: Long = System.currentTimeMillis()) {
        upsertControl(
            InventoryReconciliationControlEntity(
                controlKey = INVENTORY_RECONCILIATION_CONTROL_KEY,
                contractVersion = contractVersion,
                state = state,
                updatedAt = now,
            )
        )
    }

    @Query(
        """
        SELECT COUNT(*)
        FROM inventory_items item
        LEFT JOIN inventory_reconciliation_markers marker
          ON marker.item_id = item.id
         AND marker.organization_id = :organizationId
         AND marker.contract_version = :contractVersion
         AND marker.state = 'COMPLETE'
        WHERE marker.item_id IS NULL
        """
    )
    abstract suspend fun countPendingItems(organizationId: String, contractVersion: Int): Int

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN movement_kind = 'MIGRATION_RECONCILIATION' THEN 0
                WHEN signed_base_quantity IS NOT NULL THEN signed_base_quantity
                ELSE CAST(quantityAfter AS INTEGER) - CAST(quantityBefore AS INTEGER)
            END
        ), 0)
        FROM inventory_movements
        WHERE itemId = :itemId
        """
    )
    abstract suspend fun legacyLedgerBalance(itemId: String): Long

    @Query("SELECT quantity FROM inventory_items WHERE id = :itemId LIMIT 1")
    protected abstract suspend fun snapshotQuantity(itemId: String): Int?

    @Query(
        """
        SELECT * FROM inventory_reconciliation_markers
        WHERE organization_id = :organizationId
          AND item_id = :itemId
          AND contract_version = :contractVersion
        LIMIT 1
        """
    )
    abstract suspend fun getMarker(
        organizationId: String,
        itemId: String,
        contractVersion: Int,
    ): InventoryReconciliationMarkerEntity?

    @Query(
        """
        SELECT * FROM inventory_reconciliation_quarantine
        WHERE organization_id = :organizationId
          AND item_id = :itemId
          AND contract_version = :contractVersion
          AND resolved_at IS NULL
        LIMIT 1
        """
    )
    abstract suspend fun getOpenQuarantine(
        organizationId: String,
        itemId: String,
        contractVersion: Int,
    ): InventoryReconciliationQuarantineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertMarker(entity: InventoryReconciliationMarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertQuarantine(entity: InventoryReconciliationQuarantineEntity)

    @Query(
        """
        UPDATE inventory_reconciliation_quarantine
        SET resolved_at = :resolvedAt
        WHERE organization_id = :organizationId
          AND item_id = :itemId
          AND contract_version = :contractVersion
          AND resolved_at IS NULL
        """
    )
    protected abstract suspend fun resolveQuarantine(
        organizationId: String,
        itemId: String,
        contractVersion: Int,
        resolvedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun setApplyContext(entity: InventoryReconciliationApplyContextEntity)

    @Query("DELETE FROM inventory_reconciliation_apply_context WHERE item_id = :itemId AND token = :token")
    protected abstract suspend fun clearApplyContext(itemId: String, token: String)

    @Query(
        """
        UPDATE inventory_items
        SET quantity = :canonicalSnapshot,
            updatedAt = CASE WHEN updatedAt < :approvedAt THEN :approvedAt ELSE updatedAt END,
            isDirty = 0
        WHERE id = :itemId
        """
    )
    protected abstract suspend fun installCanonicalSnapshot(
        itemId: String,
        canonicalSnapshot: Int,
        approvedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCanonicalMovementRaw(entity: InventoryMovementEntity): Long

    @Query("SELECT * FROM inventory_movements WHERE id = :movementId LIMIT 1")
    protected abstract suspend fun movementById(movementId: String): InventoryMovementEntity?

    @Transaction
    open suspend fun applyAuthoritativeMarker(
        marker: AuthoritativeInventoryReconciliationMarker,
        now: Long = System.currentTimeMillis(),
    ): InventoryReconciliationApplyResult {
        val validated = runCatching { marker.requireValid() }.getOrElse { failure ->
            quarantine(marker, "INVALID_AUTHORITY_MARKER", null, failure.message.orEmpty(), now)
            return InventoryReconciliationApplyResult.QUARANTINED
        }

        val existingMarker = getMarker(
            validated.organizationId,
            validated.itemId,
            validated.contractVersion,
        )
        if (existingMarker != null) {
            if (existingMarker.markerChecksum.equals(validated.markerChecksum, ignoreCase = true) && existingMarker.state == "COMPLETE") {
                return InventoryReconciliationApplyResult.ALREADY_COMPLETE
            }
            quarantine(validated, "AUTHORITY_MARKER_CHANGED", null, "immutable marker checksum changed", now)
            return InventoryReconciliationApplyResult.QUARANTINED
        }

        val currentSnapshot = snapshotQuantity(validated.itemId)
        if (currentSnapshot == null) {
            quarantine(validated, "ITEM_MISSING_LOCALLY", null, "authoritative item is absent from Room", now)
            return InventoryReconciliationApplyResult.QUARANTINED
        }

        val localLegacy = legacyLedgerBalance(validated.itemId)
        if (localLegacy != validated.authoritativeLegacyBalance) {
            quarantine(
                validated,
                "LEGACY_LEDGER_MISMATCH",
                localLegacy,
                "local legacy ledger does not match the locked central authority",
                now,
            )
            return InventoryReconciliationApplyResult.QUARANTINED
        }

        val bypassToken = UUID.randomUUID().toString()
        setApplyContext(InventoryReconciliationApplyContextEntity(validated.itemId, bypassToken))
        try {
            check(
                installCanonicalSnapshot(
                    itemId = validated.itemId,
                    canonicalSnapshot = validated.canonicalSnapshot.toInt(),
                    approvedAt = validated.approvedAt,
                ) == 1
            ) { "failed to install authoritative inventory snapshot" }

            if (validated.reconciliationDelta != 0L) {
                val movement = InventoryMovementEntity(
                    id = requireNotNull(validated.reconciliationMovementId),
                    itemId = validated.itemId,
                    movementType = MovementType.ADJUST,
                    quantity = kotlin.math.abs(validated.reconciliationDelta).toInt(),
                    quantityBefore = validated.authoritativeLegacyBalance.toInt(),
                    quantityAfter = validated.canonicalSnapshot.toInt(),
                    note = "migration reconciliation v${validated.contractVersion}",
                    sourceType = "MIGRATION_RECONCILIATION",
                    sourceId = "${validated.organizationId}:${validated.itemId}",
                    sourceVersion = validated.contractVersion,
                    writeId = validated.idempotencyKey,
                    organizationId = validated.organizationId,
                    movementKind = InventoryMovementKind.MIGRATION_RECONCILIATION,
                    signedBaseQuantity = validated.reconciliationDelta,
                    commandId = validated.idempotencyKey,
                    idempotencyKey = validated.idempotencyKey,
                    postingGroupId = "inventory-reconcile:${validated.contractVersion}:${validated.organizationId}",
                    occurredAt = validated.approvedAt,
                    recordedAt = validated.approvedAt,
                    serverAcceptedAt = validated.serverAcceptedAt,
                    serverSequence = validated.serverSequence,
                    createdBy = validated.approvedBy,
                    deviceId = "server-reconciliation",
                    contractVersion = INVENTORY_LEDGER_CONTRACT_VERSION,
                    createdAt = validated.approvedAt,
                ).requireCanonicalInventoryContract()
                val inserted = insertCanonicalMovementRaw(movement)
                if (inserted == -1L) {
                    val existing = movementById(movement.id)
                    check(existing?.idempotencyKey == movement.idempotencyKey && existing?.signedBaseQuantity == movement.signedBaseQuantity) {
                        "reconciliation movement identity collision"
                    }
                }
            }

            val finalLedger = Math.addExact(localLegacy, validated.reconciliationDelta)
            check(finalLedger == validated.canonicalSnapshot) { "reconciliation invariant failed" }
            check(snapshotQuantity(validated.itemId)?.toLong() == validated.canonicalSnapshot) {
                "authoritative snapshot was not installed"
            }

            upsertMarker(
                InventoryReconciliationMarkerEntity(
                    organizationId = validated.organizationId,
                    itemId = validated.itemId,
                    contractVersion = validated.contractVersion,
                    authorityKind = validated.authorityKind,
                    sourceDeviceId = validated.sourceDeviceId,
                    canonicalSnapshot = validated.canonicalSnapshot,
                    authoritativeLegacyBalance = validated.authoritativeLegacyBalance,
                    reconciliationDelta = validated.reconciliationDelta,
                    reconciliationMovementId = validated.reconciliationMovementId,
                    idempotencyKey = validated.idempotencyKey,
                    serverSequence = validated.serverSequence,
                    markerChecksum = validated.markerChecksum.lowercase(),
                    state = "COMPLETE",
                    approvedAt = validated.approvedAt,
                    serverAcceptedAt = validated.serverAcceptedAt,
                    approvedBy = validated.approvedBy,
                    completedAt = now,
                )
            )
            resolveQuarantine(
                validated.organizationId,
                validated.itemId,
                validated.contractVersion,
                now,
            )
        } finally {
            clearApplyContext(validated.itemId, bypassToken)
        }
        return InventoryReconciliationApplyResult.COMPLETED
    }

    @Transaction
    open suspend fun recordServerQuarantine(
        organizationId: String,
        itemId: String,
        contractVersion: Int,
        reason: String,
        canonicalSnapshot: Long?,
        authoritativeLegacyBalance: Long?,
        now: Long = System.currentTimeMillis(),
    ) {
        upsertQuarantine(
            InventoryReconciliationQuarantineEntity(
                id = "inventory-reconcile-quarantine:$contractVersion:$organizationId:$itemId",
                organizationId = organizationId,
                itemId = itemId,
                contractVersion = contractVersion,
                reason = "SERVER_${reason.ifBlank { "UNEXPLAINED" }}",
                authoritativeLegacyBalance = authoritativeLegacyBalance,
                canonicalSnapshot = canonicalSnapshot,
                detectedAt = now,
                details = "server rejected automatic reconciliation",
            )
        )
    }

    private suspend fun quarantine(
        marker: AuthoritativeInventoryReconciliationMarker,
        reason: String,
        localLegacyBalance: Long?,
        details: String,
        now: Long,
    ) {
        upsertQuarantine(
            InventoryReconciliationQuarantineEntity(
                id = "inventory-reconcile-quarantine:${marker.contractVersion}:${marker.organizationId}:${marker.itemId}",
                organizationId = marker.organizationId,
                itemId = marker.itemId,
                contractVersion = marker.contractVersion,
                reason = reason,
                localLegacyBalance = localLegacyBalance,
                authoritativeLegacyBalance = marker.authoritativeLegacyBalance,
                canonicalSnapshot = marker.canonicalSnapshot,
                markerChecksum = marker.markerChecksum,
                detectedAt = now,
                details = details.take(500),
            )
        )
    }
}

enum class InventoryReconciliationApplyResult {
    COMPLETED,
    ALREADY_COMPLETE,
    QUARANTINED,
}
