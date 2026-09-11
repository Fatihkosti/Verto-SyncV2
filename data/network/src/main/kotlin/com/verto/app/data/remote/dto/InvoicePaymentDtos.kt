package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class InvoiceDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("client_id") val clientId: String = "",
    @SerialName("supplier_invoice_ref") val supplierInvoiceReference: String? = null,
    @SerialName("supplier_invoice_ref_normalized") val supplierInvoiceReferenceNormalized: String? = null,
    @SerialName("invoice_number") val invoiceNumber: Int = 0,
    val type: String = "GOODS",
    val category: String = "SALE",           // SALE | PURCHASE — الحقل المفقود
    val description: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerialName("transaction_currency_code") val transactionCurrencyCode: String = "",
    @SerialName("functional_currency_code") val functionalCurrencyCode: String = "",
    @SerialName("transaction_amount_minor") val transactionAmountMinor: Long = 0L,
    @SerialName("invoice_exchange_rate_snapshot") val invoiceExchangeRateSnapshot: String = "",
    @SerialName("exchange_rate_direction") val exchangeRateDirection: String = "FUNCTIONAL_PER_TRANSACTION",
    @SerialName("exchange_rate_timestamp") val exchangeRateTimestamp: Long = 0L,
    @SerialName("exchange_rate_source") val exchangeRateSource: String = "",
    @SerialName("functional_amount_at_recognition_minor") val functionalAmountAtRecognitionMinor: Long = 0L,
    @SerialName("legacy_currency_status") val legacyCurrencyStatus: String = "REVIEW_REQUIRED",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("buy_price") val buyPrice: BigDecimal = BigDecimal.ZERO,
    val quantity: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("unit_price") val unitPrice: BigDecimal = BigDecimal.ZERO,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("notify_days_before") val notifyDaysBefore: String = "1,3,7",
    @SerialName("notify_repeat_days") val notifyRepeatDays: Int = 3,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = true,
    val notes: String = "",
    @SerialName("is_owed_to_me") val isOwedToMe: Boolean = true,
    @SerialName("image_url") val imageUrl: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val discount: BigDecimal = BigDecimal.ZERO,
    @SerialName("discount_minor") val discountMinor: Long = 0L,
    @Serializable(with = BigDecimalSerializer::class)
    val commission: BigDecimal = BigDecimal.ZERO,
    @SerialName("commission_beneficiary_client_id") val commissionBeneficiaryClientId: String? = null,
    @SerialName("commission_source") val commissionSource: String = "NONE",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val status: String = "",
    // SYNC-017: ربط الفاتورة بشحنة — كان محلياً فقط
    @SerialName("shipment_id") val shipmentId: String? = null,
    @SerialName("purchase_order_id") val purchaseOrderId: String? = null,
    @SerialName("purchase_scope") val purchaseScope: String = "LOCAL",
    @SerialName("lifecycle_status") val lifecycleStatus: String = "POSTED",
    @SerialName("lifecycle_version") val lifecycleVersion: Int = 1,
    @SerialName("posted_at") val postedAt: Long = 0L,
    @SerialName("voided_at") val voidedAt: Long = 0L,
    @SerialName("void_reason") val voidReason: String = "",
    @SerialName("void_write_id") val voidWriteId: String = "",
    // Compatibility flag retained through F249.
    val voided: Boolean = false
)

// ── بند الفاتورة ──────────────────────────────────────────────
@Serializable
data class InvoiceItemDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("invoice_id") val invoiceId: String = "",
    @SerialName("item_type") val itemType: String = "GOODS",
    @SerialName("item_name") val itemName: String = "",
    @SerialName("item_category") val itemCategory: String = "",  // FIX-06
    @SerialName("item_sku_snapshot") val itemSkuSnapshot: String = "",
    @SerialName("unit_snapshot") val unitSnapshot: String = "",
    val quantity: Int = 1,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("buy_price") val buyPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("sell_price") val sellPrice: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_price") val totalPrice: BigDecimal = BigDecimal.ZERO,
    val description: String = "",
    @SerialName("is_owed_to_me") val isOwedToMe: Boolean = true,
    @SerialName("inventory_item_id") val inventoryItemId: String = "",
    // SYNC-017: سعر الشراء المعدَّل (تخصيص تكلفة) — كان محلياً فقط
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("adjusted_purchase_price") val adjustedPurchasePrice: BigDecimal = BigDecimal.ZERO,
    // F247: immutable realized sale economics. Minor-unit fields are the accounting truth.
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("unit_sell_price") val unitSellPrice: BigDecimal = BigDecimal.ZERO,
    @SerialName("unit_sell_price_minor") val unitSellPriceMinor: Long = 0L,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("unit_cost_at_sale") val unitCostAtSale: BigDecimal = BigDecimal.ZERO,
    @SerialName("unit_cost_at_sale_minor") val unitCostAtSaleMinor: Long = 0L,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("line_revenue_snapshot") val lineRevenueSnapshot: BigDecimal = BigDecimal.ZERO,
    @SerialName("line_revenue_snapshot_minor") val lineRevenueSnapshotMinor: Long = 0L,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("line_cost_snapshot") val lineCostSnapshot: BigDecimal = BigDecimal.ZERO,
    @SerialName("line_cost_snapshot_minor") val lineCostSnapshotMinor: Long = 0L,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("gross_profit_snapshot") val grossProfitSnapshot: BigDecimal = BigDecimal.ZERO,
    @SerialName("gross_profit_snapshot_minor") val grossProfitSnapshotMinor: Long = 0L,
    @SerialName("cost_snapshot_status") val costSnapshotStatus: String = "LEGACY_UNKNOWN"
)

// ── الدفعة ────────────────────────────────────────────────────
@Serializable
data class PaymentDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("invoice_id") val invoiceId: String = "",
    @SerialName("client_id") val clientId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    @SerialName("payment_currency_code") val paymentCurrencyCode: String = "",
    @SerialName("supplier_amount_minor") val supplierAmountMinor: Long = 0L,
    @SerialName("payment_exchange_rate") val paymentExchangeRate: String = "",
    @SerialName("payment_exchange_rate_direction") val paymentExchangeRateDirection: String = "FUNCTIONAL_PER_TRANSACTION",
    @SerialName("payment_exchange_rate_timestamp") val paymentExchangeRateTimestamp: Long = 0L,
    @SerialName("payment_exchange_rate_source") val paymentExchangeRateSource: String = "",
    @SerialName("functional_cash_amount_minor") val functionalCashAmountMinor: Long = 0L,
    @SerialName("historical_functional_amount_minor") val historicalFunctionalAmountMinor: Long = 0L,
    @SerialName("realized_fx_difference_minor") val realizedFxDifferenceMinor: Long = 0L,
    @SerialName("legacy_currency_status") val legacyCurrencyStatus: String = "REVIEW_REQUIRED",
    @SerialName("payment_method") val paymentMethod: String = "CASH",
    val note: String = "",
    @SerialName("paid_at") val paidAt: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    // SYNC-011: انتشار تعديل المبلغ بين الأجهزة (يُدار سيرفرياً عبر trg_payments_updated_at)
    @SerialName("updated_at") val updatedAt: String? = null,
    // SYNC-017: الموظف الذي سجّل السداد
    @SerialName("employee_id") val employeeId: String = "",
    @SerialName("employee_name") val employeeName: String = "",
    // Session 9: ربط الدفعة العكسية بأصلها — يمكّن حارس «معكوسة سلفاً» عبر الأجهزة
    @SerialName("reversed_payment_id") val reversedPaymentId: String? = null
)

@Serializable
data class PaymentAllocationDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("payment_id") val paymentId: String = "",
    @SerialName("invoice_id") val invoiceId: String = "",
    @SerialName("allocated_transaction_amount_minor") val allocatedTransactionAmountMinor: Long = 0L,
    @SerialName("historical_functional_amount_minor") val historicalFunctionalAmountMinor: Long = 0L,
    @SerialName("realized_fx_difference_minor") val realizedFxDifferenceMinor: Long = 0L,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("write_id") val writeId: String = "",
)

@Serializable
data class RealizedFxEventDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("payment_id") val paymentId: String = "",
    @SerialName("invoice_id") val invoiceId: String = "",
    @SerialName("functional_currency_code") val functionalCurrencyCode: String = "",
    @SerialName("historical_functional_amount_minor") val historicalFunctionalAmountMinor: Long = 0L,
    @SerialName("functional_cash_amount_minor") val functionalCashAmountMinor: Long = 0L,
    @SerialName("difference_minor") val differenceMinor: Long = 0L,
    val result: String = "NONE",
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("write_id") val writeId: String = "",
)

// ── الرصيد المقدَّم (client_credits) — Session 9 ───────────────
@Serializable
data class ClientCreditDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("client_id") val clientId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    @SerialName("source_payment_id") val sourcePaymentId: String? = null,
    @SerialName("employee_id") val employeeId: String = "",
    @SerialName("employee_name") val employeeName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ── المصروف ───────────────────────────────────────────────────
