package com.verto.app.data.local

import com.verto.app.data.local.entity.InventoryCostRevisionEntity
import com.verto.app.data.local.entity.InventoryCostRevisionKind
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryMovementKind

const val INVENTORY_LEDGER_CONTRACT_VERSION: Int = 2

/** Strict validation for rows written with the v257 canonical contract. Legacy rows stay version 1 until v258. */
fun InventoryMovementEntity.requireCanonicalInventoryContract(): InventoryMovementEntity = apply {
    require(contractVersion >= INVENTORY_LEDGER_CONTRACT_VERSION) { "canonical movement contract_version must be >= 2" }
    require(!organizationId.isNullOrBlank()) { "organizationId is required" }
    val kind = requireNotNull(movementKind) { "movementKind is required" }
    val signed = requireNotNull(signedBaseQuantity) { "signedBaseQuantity is required" }
    require(signed != 0L) { "inventory movement quantity cannot be zero; use a cost revision" }
    require(sourceType.isNotBlank()) { "sourceType is required" }
    require(sourceId.isNotBlank()) { "sourceId is required" }
    require(!commandId.isNullOrBlank()) { "commandId is required" }
    require(!idempotencyKey.isNullOrBlank()) { "idempotencyKey is required" }
    require(occurredAt != null && occurredAt >= 0L) { "occurredAt is required" }
    require(recordedAt != null && recordedAt >= 0L) { "recordedAt is required" }
    require(!createdBy.isNullOrBlank()) { "createdBy is required" }
    require(!deviceId.isNullOrBlank()) { "deviceId is required" }

    when (kind) {
        InventoryMovementKind.PURCHASE,
        InventoryMovementKind.SALES_RETURN,
        InventoryMovementKind.SHIPMENT_RECEIPT -> require(signed > 0L) { "$kind must increase stock" }

        InventoryMovementKind.SALE,
        InventoryMovementKind.PURCHASE_RETURN -> require(signed < 0L) { "$kind must decrease stock" }

        InventoryMovementKind.REVERSAL -> require(!reversesMovementId.isNullOrBlank()) {
            "REVERSAL requires reversesMovementId"
        }

        InventoryMovementKind.OPENING_BALANCE,
        InventoryMovementKind.MANUAL_ADJUSTMENT,
        InventoryMovementKind.MIGRATION_RECONCILIATION -> Unit
    }
    if (kind != InventoryMovementKind.REVERSAL) {
        require(reversesMovementId.isNullOrBlank()) { "only REVERSAL may set reversesMovementId" }
    }
}

fun InventoryCostRevisionEntity.requireCanonicalCostContract(): InventoryCostRevisionEntity = apply {
    require(contractVersion >= INVENTORY_LEDGER_CONTRACT_VERSION) { "canonical cost contract_version must be >= 2" }
    require(costRevisionId.isNotBlank()) { "costRevisionId is required" }
    require(organizationId.isNotBlank()) { "organizationId is required" }
    require(itemId.isNotBlank()) { "itemId is required" }
    require(sourceType.isNotBlank()) { "sourceType is required" }
    require(sourceId.isNotBlank()) { "sourceId is required" }
    require(commandId.isNotBlank()) { "commandId is required" }
    require(idempotencyKey.isNotBlank()) { "idempotencyKey is required" }
    require(currencyCode.isNotBlank()) { "currencyCode is required" }
    require(exchangeRateSnapshot.isNotBlank()) { "exchangeRateSnapshot is required" }
    require(directPurchaseCostMinor >= 0L) { "directPurchaseCostMinor cannot be negative" }
    require(landedCostPerBaseUnitMinor >= 0L) { "landedCostPerBaseUnitMinor cannot be negative" }
    require(approvedInventoryCostMinor >= 0L) { "approvedInventoryCostMinor cannot be negative" }
    require(recordedAt >= 0L && approvedAt >= 0L) { "cost timestamps are required" }
    require(createdBy.isNotBlank()) { "createdBy is required" }
    require(deviceId.isNotBlank()) { "deviceId is required" }

    if (revisionKind == InventoryCostRevisionKind.REVERSAL) {
        require(!reversesCostRevisionId.isNullOrBlank()) { "cost REVERSAL requires reversesCostRevisionId" }
    } else {
        require(reversesCostRevisionId.isNullOrBlank()) { "only cost REVERSAL may set reversesCostRevisionId" }
    }
}
