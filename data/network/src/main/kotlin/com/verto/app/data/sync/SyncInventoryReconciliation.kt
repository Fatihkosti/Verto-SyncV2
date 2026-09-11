package com.verto.app.data.sync

import com.verto.app.data.local.AuthoritativeInventoryReconciliationMarker
import com.verto.app.data.local.INVENTORY_RECONCILIATION_BATCH_SIZE
import com.verto.app.data.local.INVENTORY_RECONCILIATION_CONTRACT_VERSION
import com.verto.app.data.remote.dto.InventoryReconciliationBatchRequest
import com.verto.app.data.remote.dto.InventoryReconciliationMarkerDto
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

/**
     * v258 pre-push cutover. The server owns authority selection and the one approved reconciliation
     * movement per item. Room only installs markers whose legacy balance and checksum it can prove.
     */
    suspend fun SyncRuntime.reconcileLegacyInventoryIfRequired(orgId: String) {
        val reconciliationDao = db.inventoryReconciliationDao()
        val control = reconciliationDao.getControl() ?: return // Fresh schema 74: no legacy cutover needed.
        if (control.state == "COMPLETE") return
        require(control.contractVersion == INVENTORY_RECONCILIATION_CONTRACT_VERSION) {
            "unsupported inventory reconciliation contract"
        }
        require(orgId.isNotBlank()) { "inventory reconciliation requires organization" }

        reconciliationDao.setControlState("RUNNING", INVENTORY_RECONCILIATION_CONTRACT_VERSION)

        // Hydrate the central legacy set before asking the server to approve deltas. Quantity itself
        // is preserved until the marker is verified and installed inside the reconciliation transaction.
        pullInventoryItems(
            orgId = orgId,
            pendingDeletions = userPrefs.getPendingInventoryDeletions(),
            forceFull = true,
            preserveQuantity = true,
        )
        pullInventoryMovements(orgId = orgId, forceFull = true)

        var afterItemId = ""
        var pages = 0
        var boundedDrainComplete = false
        while (pages < 1_000) {
            pages += 1
            val page = supabase.postgrest.rpc(
                "inventory_prepare_reconciliation_batch_v2",
                InventoryReconciliationBatchRequest(
                    contractVersion = INVENTORY_RECONCILIATION_CONTRACT_VERSION,
                    afterItemId = afterItemId,
                    limit = INVENTORY_RECONCILIATION_BATCH_SIZE,
                ),
            ).decodeList<InventoryReconciliationMarkerDto>()

            if (page.isEmpty()) {
                boundedDrainComplete = true
                break
            }
            require(page.all { it.organizationId == orgId }) { "reconciliation returned foreign organization data" }
            val ordered = page.sortedBy { it.itemId }
            require(ordered == page && ordered.last().itemId > afterItemId) { "reconciliation page did not advance" }

            ordered.forEach { dto ->
                when (dto.status) {
                    "COMPLETE" -> {
                        val authoritative = AuthoritativeInventoryReconciliationMarker(
                            organizationId = dto.organizationId,
                            itemId = dto.itemId,
                            contractVersion = dto.contractVersion,
                            authorityKind = requireNotNull(dto.authorityKind),
                            sourceDeviceId = dto.sourceDeviceId,
                            canonicalSnapshot = requireNotNull(dto.canonicalSnapshot),
                            authoritativeLegacyBalance = requireNotNull(dto.legacyLedgerBalance),
                            reconciliationDelta = requireNotNull(dto.reconciliationDelta),
                            reconciliationMovementId = dto.reconciliationMovementId,
                            idempotencyKey = requireNotNull(dto.idempotencyKey),
                            serverSequence = dto.serverSequence,
                            markerChecksum = requireNotNull(dto.markerChecksum),
                            approvedAt = requireNotNull(dto.approvedAt),
                            serverAcceptedAt = requireNotNull(dto.serverAcceptedAt),
                            approvedBy = requireNotNull(dto.approvedBy),
                        )
                        reconciliationDao.applyAuthoritativeMarker(authoritative)
                    }
                    "QUARANTINED" -> reconciliationDao.recordServerQuarantine(
                        organizationId = dto.organizationId,
                        itemId = dto.itemId,
                        contractVersion = dto.contractVersion,
                        reason = dto.quarantineReason.orEmpty(),
                        canonicalSnapshot = dto.canonicalSnapshot,
                        authoritativeLegacyBalance = dto.legacyLedgerBalance,
                    )
                    else -> error("unsupported reconciliation status: ${dto.status}")
                }
            }

            afterItemId = ordered.last().itemId
            if (page.size < INVENTORY_RECONCILIATION_BATCH_SIZE) {
                boundedDrainComplete = true
                break
            }
        }
        check(boundedDrainComplete) { "inventory reconciliation exceeded bounded page limit" }

        val pending = reconciliationDao.countPendingItems(orgId, INVENTORY_RECONCILIATION_CONTRACT_VERSION)
        if (pending == 0) {
            reconciliationDao.setControlState("COMPLETE", INVENTORY_RECONCILIATION_CONTRACT_VERSION)
            return
        }
        reconciliationDao.setControlState("NEEDS_REVIEW", INVENTORY_RECONCILIATION_CONTRACT_VERSION)
        error("inventory reconciliation incomplete: $pending item(s) require authoritative review")
    }
