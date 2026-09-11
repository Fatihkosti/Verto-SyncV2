package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class InventoryUnitDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    val name: String = "",
    @SerialName("quantity_per_unit") val quantityPerUnit: Double = 0.0,
    @SerialName("unit_type") val unitType: String = "COUNT"
)

// ── تصنيف الصنف (SYNC-014.a) ──────────────────────────────────
@Serializable
data class ItemCategoryDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("item_id") val itemId: String = "",
    val category: String = ""
)

// ── قطعة المخزون ──────────────────────────────────────────────
@Serializable
data class InventoryItemDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("part_number") val partNumber: String = "",
    val name: String = "",
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("linked_unit_item_id") val linkedUnitItemId: String? = null,
    @SerialName("is_unit_item") val isUnitItem: Boolean = false,
    @SerialName("quantity_per_unit") val quantityPerUnit: Double = 0.0,
    @SerialName("is_service") val isService: Boolean = false,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("buy_price") val buyPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("sell_price") val sellPrice: BigDecimal = BigDecimal.ZERO,
    val quantity: Int = 0,
    @SerialName("min_quantity") val minQuantity: Int = 5,
    val location: String = "",
    val note: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("archived_by") val archivedBy: String? = null,
)

/** v263 metadata-only upsert; quantity is exclusively projected by inventory_apply_commands_v2. */
@Serializable
data class InventoryItemMetadataDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("part_number") val partNumber: String,
    val name: String,
    @SerialName("unit_id") val unitId: String?,
    @SerialName("linked_unit_item_id") val linkedUnitItemId: String?,
    @SerialName("is_unit_item") val isUnitItem: Boolean,
    @SerialName("quantity_per_unit") val quantityPerUnit: Double,
    @SerialName("is_service") val isService: Boolean,
    @Serializable(with = BigDecimalSerializer::class) @SerialName("buy_price") val buyPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) @SerialName("sell_price") val sellPrice: BigDecimal,
    @SerialName("min_quantity") val minQuantity: Int,
    val location: String,
    val note: String,
    @SerialName("created_at") val createdAt: String?,
    @SerialName("updated_at") val updatedAt: String?,
)

// ── حركة المخزون ──────────────────────────────────────────────
@Serializable
data class InventoryMovementDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("item_id") val itemId: String = "",
    @SerialName("invoice_id") val invoiceId: String = "",
    @SerialName("client_id") val clientId: String = "",
    @SerialName("movement_type") val movementType: String = "ADJUST",
    val quantity: Int = 0,
    @SerialName("quantity_before") val quantityBefore: Int = 0,
    @SerialName("quantity_after") val quantityAfter: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("unit_price") val unitPrice: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_line_id") val sourceLineId: String? = null,
    @SerialName("command_id") val commandId: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
    @SerialName("posting_group_id") val postingGroupId: String? = null,
    @SerialName("reverses_movement_id") val reversesMovementId: String? = null,
    @SerialName("conversion_factor_snapshot") val conversionFactorSnapshot: String? = null,
    @SerialName("movement_kind") val movementKind: String? = null,
    @SerialName("signed_base_quantity") val signedBaseQuantity: Long? = null,
    @SerialName("occurred_at") val occurredAt: Long? = null,
    @SerialName("recorded_at") val recordedAt: Long? = null,
    @SerialName("server_accepted_at") val serverAcceptedAt: String? = null,
    @SerialName("server_sequence") val serverSequence: Long? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class InventoryCommandBatchRequest(
    @SerialName("p_commands") val commands: List<InventoryCommandDto>,
)

@Serializable
data class InventoryCommandDto(
    @SerialName("client_outbox_id") val clientOutboxId: String,
    @SerialName("movement_id") val movementId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("invoice_id") val invoiceId: String,
    @SerialName("client_id") val clientId: String?,
    @SerialName("movement_kind") val movementKind: String,
    @SerialName("signed_base_quantity") val signedBaseQuantity: Long,
    @SerialName("unit_price_minor") val unitPriceMinor: Long,
    val note: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_line_id") val sourceLineId: String?,
    @SerialName("command_id") val commandId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("posting_group_id") val postingGroupId: String?,
    @SerialName("reverses_movement_id") val reversesMovementId: String?,
    @SerialName("conversion_factor_snapshot") val conversionFactorSnapshot: String,
    @SerialName("occurred_at") val occurredAt: Long,
    @SerialName("device_id") val deviceId: String,
    @SerialName("contract_version") val contractVersion: Int,
)

@Serializable
data class InventoryCommandAckDto(
    @SerialName("client_outbox_id") val clientOutboxId: String,
    @SerialName("movement_id") val movementId: String,
    val status: String,
    @SerialName("server_sequence") val serverSequence: Long = 0L,
    @SerialName("projected_quantity") val projectedQuantity: Long = 0L,
    @SerialName("conflict_type") val conflictType: String? = null,
)

@Serializable
data class ArchiveInventoryItemRequest(@SerialName("p_item_id") val itemId: String)

@Serializable
data class InventoryMovementPullRequest(
    @SerialName("p_after_sequence") val afterSequence: Long,
    @SerialName("p_limit") val limit: Int,
)

@Serializable
data class InventoryCostBatchRequest(@SerialName("p_revisions") val revisions: List<InventoryCostCommandDto>)

@Serializable
data class InventoryCostCommandDto(
    @SerialName("client_outbox_id") val clientOutboxId: String,
    @SerialName("cost_revision_id") val costRevisionId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_line_id") val sourceLineId: String?,
    @SerialName("revision_kind") val revisionKind: String,
    @SerialName("direct_purchase_cost_minor") val directPurchaseCostMinor: Long,
    @SerialName("landed_cost_per_base_unit_minor") val landedCostPerBaseUnitMinor: Long,
    @SerialName("approved_inventory_cost_minor") val approvedInventoryCostMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("exchange_rate_snapshot") val exchangeRateSnapshot: String,
    @SerialName("allocation_basis") val allocationBasis: String,
    @SerialName("allocation_residual_minor") val allocationResidualMinor: Long,
    @SerialName("is_provisional") val isProvisional: Boolean,
    @SerialName("reverses_cost_revision_id") val reversesCostRevisionId: String?,
    @SerialName("command_id") val commandId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("approved_at") val approvedAt: Long,
    @SerialName("device_id") val deviceId: String,
    @SerialName("contract_version") val contractVersion: Int,
)

@Serializable
data class InventoryCostAckDto(
    @SerialName("client_outbox_id") val clientOutboxId: String,
    @SerialName("cost_revision_id") val costRevisionId: String,
    val status: String,
    @SerialName("cost_sequence") val costSequence: Long = 0L,
)

@Serializable
data class InventoryCostRevisionDto(
    @SerialName("cost_revision_id") val costRevisionId: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_line_id") val sourceLineId: String? = null,
    @SerialName("revision_kind") val revisionKind: String,
    @SerialName("direct_purchase_cost_minor") val directPurchaseCostMinor: Long,
    @SerialName("landed_cost_per_base_unit_minor") val landedCostPerBaseUnitMinor: Long,
    @SerialName("approved_inventory_cost_minor") val approvedInventoryCostMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("exchange_rate_snapshot") val exchangeRateSnapshot: String,
    @SerialName("allocation_basis") val allocationBasis: String = "",
    @SerialName("allocation_residual_minor") val allocationResidualMinor: Long = 0L,
    @SerialName("is_provisional") val isProvisional: Boolean = false,
    @SerialName("reverses_cost_revision_id") val reversesCostRevisionId: String? = null,
    @SerialName("command_id") val commandId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("cost_sequence") val costSequence: Long,
    @SerialName("approved_at") val approvedAt: Long,
    @SerialName("recorded_at") val recordedAt: Long,
    @SerialName("created_by") val createdBy: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("contract_version") val contractVersion: Int,
)

@Serializable
data class InventoryReconciliationBatchRequest(
    @SerialName("p_contract_version") val contractVersion: Int,
    @SerialName("p_after_item_id") val afterItemId: String,
    @SerialName("p_limit") val limit: Int,
)

@Serializable
data class InventoryReconciliationMarkerDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("contract_version") val contractVersion: Int,
    val status: String,
    @SerialName("canonical_snapshot") val canonicalSnapshot: Long? = null,
    @SerialName("legacy_ledger_balance") val legacyLedgerBalance: Long? = null,
    @SerialName("reconciliation_delta") val reconciliationDelta: Long? = null,
    @SerialName("marker_checksum") val markerChecksum: String? = null,
    @SerialName("reconciliation_movement_id") val reconciliationMovementId: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
    @SerialName("server_sequence") val serverSequence: Long? = null,
    @SerialName("approved_at") val approvedAt: Long? = null,
    @SerialName("server_accepted_at") val serverAcceptedAt: Long? = null,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("authority_kind") val authorityKind: String? = null,
    @SerialName("source_device_id") val sourceDeviceId: String? = null,
    @SerialName("quarantine_reason") val quarantineReason: String? = null,
)

// ── صلاحيات الموظف ────────────────────────────────────────────
