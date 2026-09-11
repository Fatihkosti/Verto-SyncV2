package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.verto.app.utils.SearchTextNormalizer
import com.verto.app.money.Money
import kotlinx.serialization.Serializable
import java.util.UUID

// ── نوع حركة المخزون ─────────────────────────────────
@Serializable
enum class MovementType(val label: String) {
    IN("وارد"),        // شراء من مورد
    OUT("صادر"),       // بيع لعميل
    ADJUST("تعديل"),   // تعديل يدوي
    RETURN("مرتجع")    // مرتجع
}

/** v257 canonical ledger meaning. Legacy [MovementType] stays until the write-path cutover. */
@Serializable
enum class InventoryMovementKind {
    OPENING_BALANCE,
    PURCHASE,
    SALE,
    SALES_RETURN,
    PURCHASE_RETURN,
    MANUAL_ADJUSTMENT,
    SHIPMENT_RECEIPT,
    REVERSAL,
    MIGRATION_RECONCILIATION,
}

/** Canonical, quantity-independent inventory cost history introduced by v257. */
@Serializable
enum class InventoryCostRevisionKind {
    LOCAL_PURCHASE_APPROVED,
    LANDED_COST_PROVISIONAL,
    LANDED_COST_APPROVED,
    PURCHASE_CANCELLATION,
    COST_CORRECTION,
    REVERSAL,
    MIGRATION_BASELINE,
}

// ── نوع الوحدة ───────────────────────────────────────
@Serializable
enum class UnitType(val label: String) {
    COUNT("عدد"),   // كرتونة / دستة / علبة...
    LENGTH("طول")   // لفة / متر...
}

// ─────────────────────────────────────────────────────
// INVENTORY UNIT — وحدة القياس (كرتونة, دستة, لفة...)
// ─────────────────────────────────────────────────────
@Serializable
@Entity(tableName = "inventory_units")
data class InventoryUnitEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,                         // اسم الوحدة مثل "كرتونة"
    val quantityPerUnit: Double,              // compatibility projection for existing UI/remote contracts
    val unitType: UnitType = UnitType.COUNT,
    @ColumnInfo(name = "quantity_per_unit_base", defaultValue = "0")
    val quantityPerUnitBase: Long = quantityPerUnit.toLong(),
)

// ─────────────────────────────────────────────────────
// ITEM CATEGORY — جدول الوصل (many-to-many) بين الأصناف والتصنيفات
// ─────────────────────────────────────────────────────
@Serializable
@Entity(
    tableName = "item_categories",
    foreignKeys = [ForeignKey(
        entity = InventoryItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class ItemCategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val category: String   // اسم التصنيف نصياً (مستخدم-defined)
)

// ─────────────────────────────────────────────────────
// INVENTORY ITEM — الصنف
// ─────────────────────────────────────────────────────
@Serializable
@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["nameSearch"], name = "index_inventory_items_name_search"),
        Index(value = ["partNumberSearch"], name = "index_inventory_items_part_number_search"),
        Index(value = ["barcodeSearch"], name = "index_inventory_items_barcode_search")
    ]
)
data class InventoryItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // ── بيانات الصنف ───────────────────────────────
    val partNumber: String = "",          // رقم القطعة
    val name: String,                     // اسم الصنف
    val barcode: String = "",            // Barcode محلي اختياري؛ لا يدخل Remote DTO
    @ColumnInfo(defaultValue = "''")
    val nameSearch: String = SearchTextNormalizer.text(name),
    @ColumnInfo(defaultValue = "''")
    val partNumberSearch: String = SearchTextNormalizer.identifier(partNumber),
    @ColumnInfo(defaultValue = "''")
    val barcodeSearch: String = SearchTextNormalizer.identifier(barcode),

    // ── نظام الوحدات ───────────────────────────────
    val unitId: String? = null,           // ID الوحدة المرتبطة (من inventory_units)
    val linkedUnitItemId: String? = null, // ID الصنف المولَّد تلقائياً لهذه الوحدة
    val isUnitItem: Boolean = false,      // true = صنف وحدة مولَّد تلقائياً
    val quantityPerUnit: Double = 0.0,   // عدد القطع داخل الوحدة (للبنود isUnitItem فقط)

    // ── صنف خدمي ───────────────────────────────────
    val isService: Boolean = false,       // true = صنف خدمي (لا يؤثر على الأرباح)

    // ── أسعار وكميات ──────────────────────────────
    val buyPrice: Double = 0.0,
    @ColumnInfo(name = "buy_price_minor", defaultValue = "0")
    val buyPriceMinor: Long = Money.fromLegacyDouble(buyPrice).amountMinor,
    val sellPrice: Double = 0.0,
    @ColumnInfo(name = "sell_price_minor", defaultValue = "0")
    val sellPriceMinor: Long = Money.fromLegacyDouble(sellPrice).amountMinor,
    val quantity: Int = 0,
    val minQuantity: Int = 5,

    // ── معلومات إضافية ────────────────────────────
    val location: String = "",
    val note: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,   // SYNC-012
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null,
    @ColumnInfo(name = "archived_by")
    val archivedBy: String? = null,
)

// ─────────────────────────────────────────────────────
// INVENTORY MOVEMENT — سجل كل حركة دخول وخروج
// ─────────────────────────────────────────────────────
// ✅ الإصلاح — الثغرة #4:
// أُضيف ForeignKey يربط كل حركة بقطعتها في inventory_items.
// v261: inventory history survives item archival; physical deletion is blocked by NO_ACTION.
// أُضيف Index على invoiceId لتسريع استعلامات reverseInvoiceMovements.
@Serializable
@Entity(
    tableName = "inventory_movements",
    foreignKeys = [ForeignKey(
        entity = InventoryItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.NO_ACTION
    )],
    indices = [
        Index("itemId"),
        Index("invoiceId"),
        Index(value = ["source_id"], name = "index_inventory_movements_source_id"),
        Index(
            value = ["organization_id", "idempotency_key"],
            unique = true,
            name = "index_inventory_movements_org_idempotency",
        ),
        Index(
            value = ["organization_id", "reverses_movement_id"],
            unique = true,
            name = "index_inventory_movements_org_reversal",
        ),
        Index(
            value = ["organization_id", "itemId", "server_sequence"],
            name = "index_inventory_movements_org_item_sequence",
        ),
        Index(
            value = ["organization_id", "itemId", "movement_kind", "occurred_at"],
            name = "index_inventory_movements_org_item_kind_occurred",
        ),
        Index(
            value = ["organization_id", "source_type", "source_id", "source_line_id"],
            name = "index_inventory_movements_org_source",
        ),
    ]
)
data class InventoryMovementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val itemId: String,                   // رابط بـ InventoryItemEntity (ForeignKey)
    val invoiceId: String = "",           // رابط بالفاتورة (اختياري)
    val clientId: String = "",            // العميل أو المورد

    val movementType: MovementType,
    val quantity: Int,                    // الكمية المتحركة (دايماً موجبة)
    val quantityBefore: Int,              // الكمية قبل الحركة
    val quantityAfter: Int,               // الكمية بعد الحركة

    val unitPrice: Double = 0.0,
    @ColumnInfo(name = "unit_price_minor", defaultValue = "0")
    val unitPriceMinor: Long = Money.fromLegacyDouble(unitPrice).amountMinor,
    val note: String = "",
    val shipmentId: String = "",
    @ColumnInfo(name = "source_type", defaultValue = "''")
    val sourceType: String = "",
    @ColumnInfo(name = "source_id", defaultValue = "''")
    val sourceId: String = "",
    @ColumnInfo(name = "source_version", defaultValue = "1")
    val sourceVersion: Int = 1,
    @ColumnInfo(name = "write_id", defaultValue = "''")
    val writeId: String = "",

    // v257 canonical ledger identity. Null means a legacy row awaiting v258 reconciliation/backfill.
    @ColumnInfo(name = "organization_id")
    val organizationId: String? = null,
    @ColumnInfo(name = "movement_kind")
    val movementKind: InventoryMovementKind? = null,
    @ColumnInfo(name = "signed_base_quantity")
    val signedBaseQuantity: Long? = null,
    @ColumnInfo(name = "source_line_id")
    val sourceLineId: String? = null,
    @ColumnInfo(name = "command_id")
    val commandId: String? = null,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String? = null,
    @ColumnInfo(name = "posting_group_id")
    val postingGroupId: String? = null,
    @ColumnInfo(name = "reverses_movement_id")
    val reversesMovementId: String? = null,
    @ColumnInfo(name = "conversion_factor_snapshot")
    val conversionFactorSnapshot: String? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long? = null,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long? = null,
    @ColumnInfo(name = "server_accepted_at")
    val serverAcceptedAt: Long? = null,
    @ColumnInfo(name = "server_sequence")
    val serverSequence: Long? = null,
    @ColumnInfo(name = "created_by")
    val createdBy: String? = null,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "contract_version", defaultValue = "1")
    val contractVersion: Int = 2,

    val createdAt: Long = System.currentTimeMillis()
)

/**
 * v257 canonical cost ledger. It is additive and intentionally does not replace the older
 * revaluation/landed-cost event tables yet; v258 owns reconciliation/backfill.
 */
@Serializable
@Entity(
    tableName = "inventory_cost_revisions",
    foreignKeys = [ForeignKey(
        entity = InventoryItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["item_id"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [
        Index("item_id"),
        Index(
            value = ["organization_id", "idempotency_key"],
            unique = true,
            name = "index_inventory_cost_revisions_org_idempotency",
        ),
        Index(
            value = ["organization_id", "reverses_cost_revision_id"],
            unique = true,
            name = "index_inventory_cost_revisions_org_reversal",
        ),
        Index(
            value = ["organization_id", "item_id", "cost_sequence"],
            name = "index_inventory_cost_revisions_org_item_sequence",
        ),
        Index(
            value = ["organization_id", "source_type", "source_id", "source_line_id"],
            name = "index_inventory_cost_revisions_org_source",
        ),
    ],
)
data class InventoryCostRevisionEntity(
    @PrimaryKey @ColumnInfo(name = "cost_revision_id") val costRevisionId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_line_id") val sourceLineId: String? = null,
    @ColumnInfo(name = "revision_kind") val revisionKind: InventoryCostRevisionKind,
    @ColumnInfo(name = "direct_purchase_cost_minor") val directPurchaseCostMinor: Long,
    @ColumnInfo(name = "landed_cost_per_base_unit_minor") val landedCostPerBaseUnitMinor: Long,
    @ColumnInfo(name = "approved_inventory_cost_minor") val approvedInventoryCostMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "exchange_rate_snapshot") val exchangeRateSnapshot: String,
    @ColumnInfo(name = "allocation_basis") val allocationBasis: String = "",
    @ColumnInfo(name = "allocation_residual_minor") val allocationResidualMinor: Long = 0L,
    @ColumnInfo(name = "is_provisional") val isProvisional: Boolean = false,
    @ColumnInfo(name = "reverses_cost_revision_id") val reversesCostRevisionId: String? = null,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "cost_sequence") val costSequence: Long? = null,
    @ColumnInfo(name = "approved_at") val approvedAt: Long,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "contract_version") val contractVersion: Int = 2,
)


fun InventoryItemEntity.withSearchKeys(): InventoryItemEntity = copy(
    nameSearch = SearchTextNormalizer.text(name),
    partNumberSearch = SearchTextNormalizer.identifier(partNumber),
    barcodeSearch = SearchTextNormalizer.identifier(barcode)
)


// ─────────────────────────────────────────────────────
// F247 — immutable inventory costing events
// ─────────────────────────────────────────────────────
@Serializable
@Entity(
    tableName = "inventory_cost_revaluation_events",
    foreignKeys = [ForeignKey(
        entity = InventoryItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["item_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("item_id"),
        Index("source_id"),
        Index(
            value = ["source_type", "source_id", "write_id", "item_id", "new_unit_cost_minor"],
            unique = true,
            name = "index_inventory_cost_revaluation_identity",
        ),
    ],
)
data class InventoryCostRevaluationEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "quantity_before") val quantityBefore: Int,
    @ColumnInfo(name = "old_unit_cost_minor") val oldUnitCostMinor: Long,
    @ColumnInfo(name = "new_unit_cost_minor") val newUnitCostMinor: Long,
    @ColumnInfo(name = "revaluation_difference_minor") val revaluationDifferenceMinor: Long,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_version", defaultValue = "1") val sourceVersion: Int = 1,
    @ColumnInfo(name = "actor_id", defaultValue = "''") val actorId: String = "",
    @ColumnInfo(name = "actor_name", defaultValue = "''") val actorName: String = "",
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "write_id") val writeId: String,
)

@Serializable
@Entity(
    tableName = "landed_cost_adjustment_events",
    foreignKeys = [ForeignKey(
        entity = InventoryItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["item_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("item_id"),
        Index("shipment_id"),
        Index(value = ["posting_id", "new_posting_unit_cost_minor"], unique = true, name = "index_landed_cost_adjustment_identity"),
    ],
)
data class LandedCostAdjustmentEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "posting_id") val postingId: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "quantity_at_adjustment") val quantityAtAdjustment: Int,
    @ColumnInfo(name = "previous_posting_unit_cost_minor") val previousPostingUnitCostMinor: Long,
    @ColumnInfo(name = "new_posting_unit_cost_minor") val newPostingUnitCostMinor: Long,
    @ColumnInfo(name = "previous_inventory_unit_cost_minor") val previousInventoryUnitCostMinor: Long,
    @ColumnInfo(name = "new_inventory_unit_cost_minor") val newInventoryUnitCostMinor: Long,
    @ColumnInfo(name = "revaluation_difference_minor") val revaluationDifferenceMinor: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "write_id") val writeId: String,
)
