package com.verto.app.data.local.entity

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.utils.SearchTextNormalizer
import com.verto.app.money.Money

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

// ── أنواع الفواتير ───────────────────────────────────
// قيمة واحدة مقصودة — الفاتورة الخدمية أُلغيت ولم تُنفَّذ؛ الـ enum يُبقى لتوافق قاعدة البيانات


enum class PurchaseScope { LOCAL, INTERNATIONAL }

enum class LegacyCurrencyStatus { KNOWN, UNKNOWN, REVIEW_REQUIRED }

/** Stored direction is always functional-currency units for one transaction-currency unit. */
const val FUNCTIONAL_PER_TRANSACTION: String = "FUNCTIONAL_PER_TRANSACTION"

@Serializable
@Entity(
    tableName = "invoices",
    foreignKeys = [ForeignKey(
        entity = PartyIdentityEntity::class,
        parentColumns = ["id"],
        childColumns = ["clientId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("clientId"),
        Index("createdAt"),
        Index(value = ["invoiceNumberSearch"], name = "index_invoices_number_search"),
        Index(value = ["purchase_scope"], name = "index_invoices_purchase_scope"),
        Index(value = ["purchase_order_id"], name = "index_invoices_purchase_order_id"),
        Index(value = ["category", "status", "lifecycle_status", "dueDate"], name = "index_invoices_analytics_aging"),
        Index(value = ["category", "lifecycle_status", "createdAt"], name = "index_invoices_analytics_period"),
        Index(
            value = ["organization_id", "clientId", "supplier_invoice_ref_normalized"],
            unique = true,
            name = "index_invoices_org_supplier_external_ref",
        ),
        Index(value = ["commission_beneficiary_client_id"], name = "index_invoices_commission_beneficiary")
    ]
)
data class InvoiceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: Int,
    @ColumnInfo(defaultValue = "''")
    val invoiceNumberSearch: String = SearchTextNormalizer.identifier(invoiceNumber.toString()),
    val clientId: String,
    @ColumnInfo(name = "organization_id", defaultValue = "''")
    val organizationId: String = "",
    /** External supplier invoice reference; null for sales or when the supplier did not provide one. */
    @ColumnInfo(name = "supplier_invoice_ref")
    val supplierInvoiceReference: String? = null,
    /** Canonical reference used only for local duplicate prevention. */
    @ColumnInfo(name = "supplier_invoice_ref_normalized")
    val supplierInvoiceReferenceNormalized: String? = null,
    val type: InvoiceType,
    // SALE = فاتورة مبيعات | PURCHASE = فاتورة مشتريات
    // يُشتق تلقائياً من isOwedToMe عند migration
    val category: InvoiceCategory = InvoiceCategory.SALE,
    val description: String,
    val totalAmount: Double,
    @ColumnInfo(name = "total_amount_minor", defaultValue = "0")
    val totalAmountMinor: Long = Money.fromLegacyDouble(totalAmount).amountMinor,
    @ColumnInfo(name = "transaction_currency_code", defaultValue = "''")
    val transactionCurrencyCode: String = "",
    @ColumnInfo(name = "functional_currency_code", defaultValue = "''")
    val functionalCurrencyCode: String = "",
    @ColumnInfo(name = "transaction_amount_minor", defaultValue = "0")
    val transactionAmountMinor: Long = totalAmountMinor,
    @ColumnInfo(name = "invoice_exchange_rate_snapshot", defaultValue = "''")
    val invoiceExchangeRateSnapshot: String = "",
    @ColumnInfo(name = "exchange_rate_direction", defaultValue = "'FUNCTIONAL_PER_TRANSACTION'")
    val exchangeRateDirection: String = FUNCTIONAL_PER_TRANSACTION,
    @ColumnInfo(name = "exchange_rate_timestamp", defaultValue = "0")
    val exchangeRateTimestamp: Long = 0L,
    @ColumnInfo(name = "exchange_rate_source", defaultValue = "''")
    val exchangeRateSource: String = "",
    @ColumnInfo(name = "functional_amount_at_recognition_minor", defaultValue = "0")
    val functionalAmountAtRecognitionMinor: Long = 0L,
    @ColumnInfo(name = "legacy_currency_status", defaultValue = "'REVIEW_REQUIRED'")
    val legacyCurrencyStatus: LegacyCurrencyStatus = LegacyCurrencyStatus.REVIEW_REQUIRED,
    // default للفواتير الجديدة محلياً فقط — الاستقبال من Supabase يمرّر القيمة صراحةً عبر parseDate
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val notifyDaysBefore: String = "1,3,7",
    val notifyRepeatDays: Int = 3,
    val notificationsEnabled: Boolean = true,
    val notes: String = "",
    val isOwedToMe: Boolean = true,
    val imageUri: String = "",
    val status: InvoiceStatus = InvoiceStatus.CLOSED_CASH,
    val discount: Double = 0.0,
    @ColumnInfo(name = "discount_minor", defaultValue = "0")
    val discountMinor: Long = Money.fromLegacyDouble(discount).amountMinor,
    val commission: Double = 0.0,
    @ColumnInfo(name = "commission_minor", defaultValue = "0")
    val commissionMinor: Long = Money.fromLegacyDouble(commission).amountMinor,
    @ColumnInfo(name = "commission_beneficiary_client_id")
    val commissionBeneficiaryClientId: String? = null,
    @ColumnInfo(name = "commission_source", defaultValue = "'NONE'")
    val commissionSource: String = "NONE",
    val shipmentId: String? = null,
    @ColumnInfo(name = "purchase_order_id")
    val purchaseOrderId: String? = null,
    @ColumnInfo(name = "purchase_scope", defaultValue = "'LOCAL'")
    val purchaseScope: PurchaseScope = PurchaseScope.LOCAL,
    // معرّف الموظف الذي أنشأ الفاتورة (لأداء الموظف) — يُملأ عند الإنشاء ويُسحب من السيرفر.
    val createdBy: String = "",
    // F248: financial lifecycle is independent from payment terms/status.
    @ColumnInfo(name = "lifecycle_status", defaultValue = "'POSTED'")
    val lifecycleStatus: InvoiceLifecycleStatus = InvoiceLifecycleStatus.POSTED,
    @ColumnInfo(name = "lifecycle_version", defaultValue = "1")
    val lifecycleVersion: Int = 1,
    @ColumnInfo(name = "posted_at", defaultValue = "0")
    val postedAt: Long = createdAt,
    @ColumnInfo(name = "voided_at", defaultValue = "0")
    val voidedAt: Long = 0L,
    @ColumnInfo(name = "void_reason", defaultValue = "''")
    val voidReason: String = "",
    @ColumnInfo(name = "void_write_id", defaultValue = "''")
    val voidWriteId: String = "",
    // Compatibility projection retained for existing queries/sync until F249 migration of transport.
    val voided: Boolean = lifecycleStatus == InvoiceLifecycleStatus.VOID,
    val isDirty: Boolean = true   // SYNC-012
)

// ─────────────────────────────────────────────────────
// INVOICE ITEMS
// ─────────────────────────────────────────────────────
@Serializable
@Entity(
    tableName = "invoice_items",
    foreignKeys = [ForeignKey(
        entity = InvoiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("invoiceId"),
        Index(value = ["inventoryItemId"], name = "index_invoice_items_inventory_item"),
    ]
)
data class InvoiceItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val itemType: ItemType = ItemType.GOODS,
    val itemName: String,
    // تصنيف الصنف للتقارير
    val itemCategory: String = "",
    @ColumnInfo(name = "item_sku_snapshot", defaultValue = "''")
    val itemSkuSnapshot: String = "",
    @ColumnInfo(name = "unit_snapshot", defaultValue = "''")
    val unitSnapshot: String = "",
    val quantity: Int = 1,
    val buyPrice: Double = 0.0,
    @ColumnInfo(name = "buy_price_minor", defaultValue = "0")
    val buyPriceMinor: Long = Money.fromLegacyDouble(buyPrice).amountMinor,
    val sellPrice: Double = 0.0,
    @ColumnInfo(name = "sell_price_minor", defaultValue = "0")
    val sellPriceMinor: Long = Money.fromLegacyDouble(sellPrice).amountMinor,
    val totalPrice: Double = 0.0,
    @ColumnInfo(name = "total_price_minor", defaultValue = "0")
    val totalPriceMinor: Long = Money.fromLegacyDouble(totalPrice).amountMinor,
    val description: String = "",
    val isOwedToMe: Boolean = true,
    val inventoryItemId: String = "",
    val adjustedPurchasePrice: Double = 0.0,
    @ColumnInfo(name = "adjusted_purchase_price_minor", defaultValue = "0")
    val adjustedPurchasePriceMinor: Long = Money.fromLegacyDouble(adjustedPurchasePrice).amountMinor,
    // F247: immutable sale economics. Historical profit must never depend on today's inventory price.
    @ColumnInfo(name = "unit_sell_price", defaultValue = "0")
    val unitSellPrice: Double = sellPrice,
    @ColumnInfo(name = "unit_sell_price_minor", defaultValue = "0")
    val unitSellPriceMinor: Long = Money.fromLegacyDouble(unitSellPrice).amountMinor,
    @ColumnInfo(name = "unit_cost_at_sale", defaultValue = "0")
    val unitCostAtSale: Double = 0.0,
    @ColumnInfo(name = "unit_cost_at_sale_minor", defaultValue = "0")
    val unitCostAtSaleMinor: Long = Money.fromLegacyDouble(unitCostAtSale).amountMinor,
    @ColumnInfo(name = "line_revenue_snapshot", defaultValue = "0")
    val lineRevenueSnapshot: Double = totalPrice,
    @ColumnInfo(name = "line_revenue_snapshot_minor", defaultValue = "0")
    val lineRevenueSnapshotMinor: Long = Money.fromLegacyDouble(lineRevenueSnapshot).amountMinor,
    @ColumnInfo(name = "line_cost_snapshot", defaultValue = "0")
    val lineCostSnapshot: Double = 0.0,
    @ColumnInfo(name = "line_cost_snapshot_minor", defaultValue = "0")
    val lineCostSnapshotMinor: Long = Money.fromLegacyDouble(lineCostSnapshot).amountMinor,
    @ColumnInfo(name = "gross_profit_snapshot", defaultValue = "0")
    val grossProfitSnapshot: Double = lineRevenueSnapshot - lineCostSnapshot,
    @ColumnInfo(name = "gross_profit_snapshot_minor", defaultValue = "0")
    val grossProfitSnapshotMinor: Long = Money.fromLegacyDouble(grossProfitSnapshot).amountMinor,
    @ColumnInfo(name = "cost_snapshot_status", defaultValue = "'LEGACY_UNKNOWN'")
    val costSnapshotStatus: String = "LEGACY_UNKNOWN",
    val isDirty: Boolean = true   // SYNC-012
)

@Serializable
@Entity(
    tableName = "invoice_due_installments",
    foreignKeys = [ForeignKey(
        entity = InvoiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["invoice_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["invoice_id"], name = "index_invoice_due_installments_invoice"),
        Index(value = ["invoice_id", "sequence"], unique = true, name = "index_invoice_due_installments_sequence"),
        Index(value = ["due_date"], name = "index_invoice_due_installments_due_date"),
    ]
)
data class InvoiceDueInstallmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    val sequence: Int,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "due_date") val dueDate: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "write_id") val writeId: String,
)

// ─────────────────────────────────────────────────────
// PAYMENT
// ─────────────────────────────────────────────────────
@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = InvoiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId"), Index("paidAt")]
)
data class PaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val clientId: String,
    val amount: Double,
    @ColumnInfo(name = "amount_minor", defaultValue = "0")
    val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor,
    /** Amount settling the invoice, in invoice transaction currency. */
    @ColumnInfo(name = "payment_currency_code", defaultValue = "''")
    val paymentCurrencyCode: String = "",
    @ColumnInfo(name = "supplier_amount_minor", defaultValue = "0")
    val supplierAmountMinor: Long = amountMinor,
    @ColumnInfo(name = "payment_exchange_rate", defaultValue = "''")
    val paymentExchangeRate: String = "",
    @ColumnInfo(name = "payment_exchange_rate_direction", defaultValue = "'FUNCTIONAL_PER_TRANSACTION'")
    val paymentExchangeRateDirection: String = FUNCTIONAL_PER_TRANSACTION,
    @ColumnInfo(name = "payment_exchange_rate_timestamp", defaultValue = "0")
    val paymentExchangeRateTimestamp: Long = 0L,
    @ColumnInfo(name = "payment_exchange_rate_source", defaultValue = "''")
    val paymentExchangeRateSource: String = "",
    @ColumnInfo(name = "functional_cash_amount_minor", defaultValue = "0")
    val functionalCashAmountMinor: Long = 0L,
    @ColumnInfo(name = "historical_functional_amount_minor", defaultValue = "0")
    val historicalFunctionalAmountMinor: Long = 0L,
    @ColumnInfo(name = "realized_fx_difference_minor", defaultValue = "0")
    val realizedFxDifferenceMinor: Long = 0L,
    @ColumnInfo(name = "legacy_currency_status", defaultValue = "'REVIEW_REQUIRED'")
    val legacyCurrencyStatus: LegacyCurrencyStatus = LegacyCurrencyStatus.REVIEW_REQUIRED,
    val paymentMethod: PaymentMethod,
    val note: String = "",
    val paidAt: Long = System.currentTimeMillis(),
    // معرف الموظف الذي سجّل السداد
    val employeeId: String = "",
    val employeeName: String = "",
    @ColumnInfo(name = "source_type", defaultValue = "''")
    val sourceType: String = "",
    @ColumnInfo(name = "source_id", defaultValue = "''")
    val sourceId: String = "",
    @ColumnInfo(name = "source_version", defaultValue = "1")
    val sourceVersion: Int = 1,
    @ColumnInfo(name = "write_id", defaultValue = "''")
    val writeId: String = "",
    // Session 7: إن كانت هذه الحركة عكساً لدفعة سابقة، يحمل معرف الدفعة الأصلية (amount سالب).
    // الدفعات العادية = null.
    val reversedPaymentId: String? = null,
    val isDirty: Boolean = true   // SYNC-012
)

/** Allocation is separate from the payment so one payment can later settle multiple invoices safely. */
@Serializable
@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(entity = PaymentEntity::class, parentColumns = ["id"], childColumns = ["payment_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoice_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("payment_id"),
        Index("invoice_id"),
        Index(value = ["payment_id", "invoice_id"], unique = true, name = "index_payment_allocation_identity"),
        Index(value = ["invoice_id", "created_at"], name = "index_payment_allocations_invoice_created"),
    ],
)
data class PaymentAllocationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "payment_id") val paymentId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "allocated_transaction_amount_minor") val allocatedTransactionAmountMinor: Long,
    @ColumnInfo(name = "historical_functional_amount_minor") val historicalFunctionalAmountMinor: Long,
    @ColumnInfo(name = "realized_fx_difference_minor") val realizedFxDifferenceMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source_type", defaultValue = "'INVOICE'") val sourceType: String = "INVOICE",
    @ColumnInfo(name = "source_id", defaultValue = "''") val sourceId: String = "",
    @ColumnInfo(name = "source_version", defaultValue = "1") val sourceVersion: Int = 1,
    @ColumnInfo(name = "write_id", defaultValue = "''") val writeId: String = "",
)

@Serializable
@Entity(
    tableName = "realized_fx_events",
    foreignKeys = [
        ForeignKey(entity = PaymentEntity::class, parentColumns = ["id"], childColumns = ["payment_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoice_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("payment_id"), Index("invoice_id")],
)
data class RealizedFxEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "payment_id") val paymentId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "functional_currency_code") val functionalCurrencyCode: String,
    @ColumnInfo(name = "historical_functional_amount_minor") val historicalFunctionalAmountMinor: Long,
    @ColumnInfo(name = "functional_cash_amount_minor") val functionalCashAmountMinor: Long,
    @ColumnInfo(name = "difference_minor") val differenceMinor: Long,
    @ColumnInfo(name = "result") val result: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "source_type", defaultValue = "'PAYMENT'") val sourceType: String = "PAYMENT",
    @ColumnInfo(name = "source_id", defaultValue = "''") val sourceId: String = "",
    @ColumnInfo(name = "source_version", defaultValue = "1") val sourceVersion: Int = 1,
    @ColumnInfo(name = "write_id", defaultValue = "''") val writeId: String = "",
)

// ─────────────────────────────────────────────────────
// EXPENSE
// ─────────────────────────────────────────────────────
@Serializable
@Entity(tableName = "expenses", indices = [Index(value=["lifecycle_state","date"], name="index_expenses_lifecycle_date"), Index(value=["lifecycle_state","category","date"], name="index_expenses_lifecycle_category_date")])
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String,
    val item: String,
    val amount: Double,
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,   // SYNC-012 compatibility marker only after Session 307
    @ColumnInfo(name = "lifecycle_state", defaultValue = "'ACTIVE'")
    val lifecycleState: String = "ACTIVE",
    @ColumnInfo(name = "voided_at")
    val voidedAt: Long? = null,
    @ColumnInfo(name = "void_reason")
    val voidReason: String? = null,
    @ColumnInfo(name = "reversal_write_id")
    val reversalWriteId: String? = null,
    @ColumnInfo(name = "amount_minor", defaultValue = "0")
    val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor,
)

// ─────────────────────────────────────────────────────
// CASH REGISTER — الصندوق (صف واحد ثابت id="main")
// ─────────────────────────────────────────────────────
@Serializable
@Entity(tableName = "cash_register")
data class CashRegisterEntity(
    @PrimaryKey val id: String = "main",
    val balance: Double = 0.0,
    @ColumnInfo(name = "balance_minor", defaultValue = "0")
    val balanceMinor: Long = Money.fromLegacyDouble(balance).amountMinor,
    val updatedAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────
// CASH REGISTER MOVEMENTS — حركات الصندوق
// ─────────────────────────────────────────────────────
@Serializable
@Entity(tableName = "cash_register_movements", indices = [Index("createdAt"), Index(value=["movementType","referenceId"], name="index_cash_register_movements_type_reference")])
data class CashRegisterMovementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val movementType: CashMovementType,
    // موجب = دخول للصندوق | سالب = خروج من الصندوق
    val amount: Double,
    @ColumnInfo(name = "amount_minor", defaultValue = "0")
    val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor,
    val balanceBefore: Double,
    @ColumnInfo(name = "balance_before_minor", defaultValue = "0")
    val balanceBeforeMinor: Long = Money.fromLegacyDouble(balanceBefore).amountMinor,
    val balanceAfter: Double,
    @ColumnInfo(name = "balance_after_minor", defaultValue = "0")
    val balanceAfterMinor: Long = Money.fromLegacyDouble(balanceAfter).amountMinor,
    // id الفاتورة أو المصروف المرتبط (اختياري)
    val referenceId: String = "",
    val note: String = "",
    @ColumnInfo(name = "source_type", defaultValue = "''")
    val sourceType: String = "",
    @ColumnInfo(name = "source_id", defaultValue = "''")
    val sourceId: String = "",
    @ColumnInfo(name = "source_version", defaultValue = "1")
    val sourceVersion: Int = 1,
    @ColumnInfo(name = "write_id", defaultValue = "''")
    val writeId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Durable local idempotency claim. The unique tenant+operation+writeId key is inserted before
 * any invoice side effect inside the owner Room transaction.
 */
@Entity(
    tableName = "invoice_write_guard",
    indices = [
        Index(
            value = ["organization_id", "operation_type", "write_id"],
            unique = true,
            name = "index_invoice_write_guard_identity",
        ),
        Index(value = ["target_invoice_id"], name = "index_invoice_write_guard_target"),
    ],
)
data class InvoiceWriteGuardEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "write_id") val writeId: String,
    @ColumnInfo(name = "target_invoice_id") val targetInvoiceId: String,
    @ColumnInfo(name = "source_type") val sourceType: String = "INVOICE",
    @ColumnInfo(name = "source_id") val sourceId: String = targetInvoiceId,
    @ColumnInfo(name = "source_version") val sourceVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
)

// ─────────────────────────────────────────────────────
// AUDIT LOG — سجل التعديلات (24 ساعة للتراجع)
// ─────────────────────────────────────────────────────


fun InvoiceEntity.withSearchKeys(): InvoiceEntity = copy(
    invoiceNumberSearch = SearchTextNormalizer.identifier(invoiceNumber.toString())
)

// ─────────────────────────────────────────────────────
// F249 — durable financial sync envelope
// ─────────────────────────────────────────────────────
/**
 * Durable at-least-once delivery record written in the same Room transaction as the financial write.
 * Server-side idempotency turns replayed delivery into exactly-once financial effects.
 */
@Entity(
    tableName = "financial_outbox",
    indices = [
        Index(
            value = ["organization_id", "operation_type", "write_id"],
            unique = true,
            name = "index_financial_outbox_identity",
        ),
        Index(
            value = ["organization_id", "aggregate_id", "sequence"],
            unique = true,
            name = "index_financial_outbox_aggregate_sequence",
        ),
        Index(
            value = ["organization_id", "sync_state", "next_attempt_at", "created_at"],
            name = "index_financial_outbox_delivery",
        ),
    ],
)
data class FinancialOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "organization_id")
    val organizationId: String,
    @ColumnInfo(name = "write_id")
    val writeId: String,
    @ColumnInfo(name = "aggregate_id")
    val aggregateId: String,
    @ColumnInfo(name = "aggregate_version")
    val aggregateVersion: Int,
    val sequence: Long,
    @ColumnInfo(name = "operation_type")
    val operationType: String,
    @ColumnInfo(name = "payload_version", defaultValue = "1")
    val payloadVersion: Int = 1,
    @ColumnInfo(name = "schema_version", defaultValue = "1")
    val schemaVersion: Int = 1,
    val payload: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at")
    val createdAt: Long = recordedAt,
    @ColumnInfo(name = "sync_state", defaultValue = "'PENDING'")
    val syncState: String = "PENDING",
    @ColumnInfo(name = "attempt_count", defaultValue = "0")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at", defaultValue = "0")
    val nextAttemptAt: Long = 0L,
    @ColumnInfo(name = "last_error", defaultValue = "''")
    val lastError: String = "",
    @ColumnInfo(name = "server_revision")
    val serverRevision: Long? = null,
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null,
)

/**
 * Durable receipt for remote financial events. Duplicate event ids are ignored, and dependencies
 * can remain WAITING_DEPENDENCY until their parent invoice arrives through the compatibility pull.
 */
@Entity(
    tableName = "financial_inbox",
    indices = [
        Index(
            value = ["organization_id", "server_revision"],
            unique = true,
            name = "index_financial_inbox_server_revision",
        ),
        Index(
            value = ["organization_id", "aggregate_id", "apply_state"],
            name = "index_financial_inbox_aggregate_state",
        ),
    ],
)
data class FinancialInboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "organization_id")
    val organizationId: String,
    @ColumnInfo(name = "aggregate_id")
    val aggregateId: String,
    @ColumnInfo(name = "aggregate_version")
    val aggregateVersion: Int,
    val sequence: Long,
    @ColumnInfo(name = "operation_type")
    val operationType: String,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    val payload: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
    @ColumnInfo(name = "server_revision")
    val serverRevision: Long,
    @ColumnInfo(name = "apply_state")
    val applyState: String,
    @ColumnInfo(name = "apply_reason", defaultValue = "''")
    val applyReason: String = "",
    @ColumnInfo(name = "received_at")
    val receivedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "applied_at")
    val appliedAt: Long? = null,
)
