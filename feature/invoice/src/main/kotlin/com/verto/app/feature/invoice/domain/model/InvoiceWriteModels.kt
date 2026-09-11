package com.verto.app.feature.invoice.domain.model

import com.verto.app.money.ExchangeRate
import com.verto.app.money.Money
import java.util.UUID

enum class InvoiceCategory { SALE, PURCHASE }
enum class InvoiceStatus { CLOSED_CASH, CLOSED_CREDIT }
/** Financial document lifecycle. Payment terms remain in [InvoiceStatus] for backward compatibility only. */
enum class InvoiceLifecycleStatus { DRAFT, POSTED, VOID }
enum class InvoicePaymentMode { CASH, CREDIT }
enum class InvoiceVoidPaymentDisposition { REFUND_TO_CASH }
enum class PurchaseScope { LOCAL, INTERNATIONAL }
enum class LegacyCurrencyStatus { KNOWN, UNKNOWN, REVIEW_REQUIRED }
const val FUNCTIONAL_PER_TRANSACTION: String = "FUNCTIONAL_PER_TRANSACTION"

data class InvoiceItemData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: String = "",
    val sellPrice: String = "",
    val buyPrice: String = "",
    val itemCategory: String = "",
    val inventoryItemId: String = ""
)

enum class PaymentMode { CASH, CREDIT }
enum class InvoicePaymentMethod { CASH }
enum class InvoiceCashMovementType {
    SALE_CASH,
    PURCHASE_CASH,
    PAYMENT_RECEIVED,
    PAYMENT_MADE
}

data class InvoiceDraftItem(
    val name: String,
    val quantity: String,
    val sellPrice: String,
    val buyPrice: String,
    val itemCategory: String = "",
    val inventoryItemId: String = ""
)

data class InvoiceDueInstallmentDraft(
    val amount: Money,
    val dueDate: Long,
)

data class InvoiceDueInstallment(
    val id: String,
    val invoiceId: String,
    val sequence: Int,
    val amountMinor: Long,
    val currencyCode: String,
    val dueDate: Long,
    val createdAt: Long,
    val writeId: String,
)

data class SaveInvoiceCommand(
    val existingInvoiceId: String?,
    val clientId: String,
    val items: List<InvoiceDraftItem>,
    val paymentMode: InvoicePaymentMode,
    val dueDate: Long,
    /** Optional contractual schedule for the unpaid balance. Sum must equal outstanding amount. */
    val dueInstallments: List<InvoiceDueInstallmentDraft> = emptyList(),
    val notes: String,
    val isSale: Boolean = true,
    val originalCreatedAt: Long? = null,
    val originalInvoiceNumber: Int? = null,
    val initialPayment: Money = Money.zero(),
    val shipmentId: String? = null,
    val purchaseScope: PurchaseScope = PurchaseScope.LOCAL,
    /** Invoice-level discount. Line prices remain gross; invoice total is net after discount. */
    val discount: Money = Money.zero(),
    val commission: Money = Money.zero(),
    /** Commission belongs to this marketing party. Usually the buyer; otherwise the optional referrer. */
    val commissionBeneficiaryClientId: String? = null,
    /** NONE | BUYER | REFERRER. Explicit for audit/reporting. */
    val commissionSource: String = "NONE",
    val exchangeRate: ExchangeRate = ExchangeRate.one(),
    /** Currency of the supplier/customer amount. For LOCAL it is normalized to functional currency. */
    val transactionCurrencyCode: String = "",
    val functionalCurrencyCode: String = "",
    val exchangeRateTimestamp: Long = 0L,
    val exchangeRateSource: String = "USER_INPUT",
    /** Explicit tenant identity used by atomic invoice/integration persistence. */
    val organizationId: String = "",
    /** True only when the selected client currently contains the COMPANY type. */
    val companyClient: Boolean = false,
    val maintenance: CompanyMaintenanceData? = null,
    /** Supplier-issued invoice reference. It is optional and only meaningful for purchases. */
    val supplierInvoiceReference: String? = null,
    /** Optional F253 purchase order. When present, GRN owns inventory recognition. */
    val purchaseOrderId: String? = null,
    val purchaseQuantityToleranceUnits: Int = 0,
    val purchasePriceToleranceMinor: Long = 0L,
    val purchaseVarianceReason: String? = null,
    val unreceivedPaymentOverrideReason: String? = null,
    /** One identity shared by every local effect and integration event for this save attempt. */
    val writeId: String = "",
    val requestedAt: Long = 0L,
)

data class InvoiceVoidRequest(
    val invoiceId: String,
    val reason: String,
    val paymentDisposition: InvoiceVoidPaymentDisposition? = null,
    /** Stable identity from the first attempt through every retry. */
    val requestId: String = "",
    val requestedAt: Long = 0L,
)

data class InvoiceSaveResult(
    val invoiceId: String,
    val newInventoryItemsCreated: Int = 0,
    val inventoryItemsUpdated: Int = 0
)

data class InvoiceRecord(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: Int,
    val clientId: String,
    val organizationId: String = "",
    val supplierInvoiceReference: String? = null,
    val category: InvoiceCategory,
    val description: String,
    /** Legacy/display projection; calculations use totalAmountMinor. */
    val totalAmount: Double,
    val totalAmountMinor: Long = Money.fromLegacyDouble(totalAmount).amountMinor,
    val transactionCurrencyCode: String = "",
    val functionalCurrencyCode: String = "",
    val transactionAmountMinor: Long = totalAmountMinor,
    val invoiceExchangeRateSnapshot: String = "",
    val exchangeRateDirection: String = FUNCTIONAL_PER_TRANSACTION,
    val exchangeRateTimestamp: Long = 0L,
    val exchangeRateSource: String = "",
    val functionalAmountAtRecognitionMinor: Long = 0L,
    val legacyCurrencyStatus: LegacyCurrencyStatus = LegacyCurrencyStatus.REVIEW_REQUIRED,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val notes: String = "",
    val isOwedToMe: Boolean = true,
    val status: InvoiceStatus = InvoiceStatus.CLOSED_CASH,
    val discount: Double = 0.0,
    val discountMinor: Long = Money.fromLegacyDouble(discount).amountMinor,
    val commission: Double = 0.0,
    val commissionMinor: Long = Money.fromLegacyDouble(commission).amountMinor,
    val commissionBeneficiaryClientId: String? = null,
    val commissionSource: String = "NONE",
    val shipmentId: String? = null,
    val purchaseOrderId: String? = null,
    val purchaseScope: PurchaseScope = PurchaseScope.LOCAL,
    val createdBy: String = "",
    /** Lifecycle is independent from cash/credit terms and payment state. */
    val lifecycleStatus: InvoiceLifecycleStatus = InvoiceLifecycleStatus.POSTED,
    val lifecycleVersion: Int = 1,
    val postedAt: Long = createdAt,
    val voidedAt: Long = 0L,
    val voidReason: String = "",
    val voidWriteId: String = "",
    /** Compatibility projection. New code must use lifecycleStatus. */
    val voided: Boolean = lifecycleStatus == InvoiceLifecycleStatus.VOID
)

data class InvoiceLine(
    val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val itemName: String,
    val itemCategory: String = "",
    /** Immutable descriptive snapshots captured when the invoice is posted. */
    val itemSkuSnapshot: String = "",
    val unitSnapshot: String = "",
    val quantity: Int = 1,
    /** Legacy/display projections; calculations use the minor-unit fields. */
    val buyPrice: Double = 0.0,
    val buyPriceMinor: Long = Money.fromLegacyDouble(buyPrice).amountMinor,
    val sellPrice: Double = 0.0,
    val sellPriceMinor: Long = Money.fromLegacyDouble(sellPrice).amountMinor,
    val totalPrice: Double = 0.0,
    val totalPriceMinor: Long = Money.fromLegacyDouble(totalPrice).amountMinor,
    /** F247 immutable sale-economics snapshot. */
    val unitSellPrice: Double = sellPrice,
    val unitSellPriceMinor: Long = Money.fromLegacyDouble(unitSellPrice).amountMinor,
    val unitCostAtSale: Double = 0.0,
    val unitCostAtSaleMinor: Long = Money.fromLegacyDouble(unitCostAtSale).amountMinor,
    val lineRevenueSnapshot: Double = totalPrice,
    val lineRevenueSnapshotMinor: Long = Money.fromLegacyDouble(lineRevenueSnapshot).amountMinor,
    val lineCostSnapshot: Double = 0.0,
    val lineCostSnapshotMinor: Long = Money.fromLegacyDouble(lineCostSnapshot).amountMinor,
    val grossProfitSnapshot: Double = lineRevenueSnapshot - lineCostSnapshot,
    val grossProfitSnapshotMinor: Long = Money.fromLegacyDouble(grossProfitSnapshot).amountMinor,
    val costSnapshotStatus: String = "LEGACY_UNKNOWN",
    val isOwedToMe: Boolean = true,
    val inventoryItemId: String = ""
)

data class InvoicePayment(
    val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val clientId: String,
    val amount: Double,
    val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor,
    val paymentCurrencyCode: String = "",
    val supplierAmountMinor: Long = amountMinor,
    val paymentExchangeRate: String = "",
    val paymentExchangeRateDirection: String = FUNCTIONAL_PER_TRANSACTION,
    val paymentExchangeRateTimestamp: Long = 0L,
    val paymentExchangeRateSource: String = "",
    val functionalCashAmountMinor: Long = 0L,
    val historicalFunctionalAmountMinor: Long = 0L,
    val realizedFxDifferenceMinor: Long = 0L,
    val legacyCurrencyStatus: LegacyCurrencyStatus = LegacyCurrencyStatus.REVIEW_REQUIRED,
    val paymentMethod: InvoicePaymentMethod = InvoicePaymentMethod.CASH,
    val note: String = "",
    val paidAt: Long = System.currentTimeMillis(),
    val employeeId: String = "",
    val employeeName: String = "",
    val sourceType: String = "INVOICE",
    val sourceId: String = invoiceId,
    val sourceVersion: Int = 1,
    val writeId: String = "",
    /** Original payment id when this row is a financial reversal. */
    val reversedPaymentId: String? = null,
)

data class InvoicePaymentAllocation(
    val id: String = UUID.randomUUID().toString(),
    val paymentId: String,
    val invoiceId: String,
    val allocatedTransactionAmountMinor: Long,
    val historicalFunctionalAmountMinor: Long,
    val realizedFxDifferenceMinor: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceType: String = "INVOICE",
    val sourceId: String = invoiceId,
    val sourceVersion: Int = 1,
    val writeId: String = "",
)

data class RealizedFxEvent(
    val id: String = UUID.randomUUID().toString(),
    val paymentId: String,
    val invoiceId: String,
    val functionalCurrencyCode: String,
    val historicalFunctionalAmountMinor: Long,
    val functionalCashAmountMinor: Long,
    val differenceMinor: Long,
    val result: String,
    val occurredAt: Long,
    val writeId: String = "",
)

data class InvoicePurchaseStockCommand(
    val itemId: String,
    val quantity: Int,
    val invoiceId: String,
    val supplierId: String,
    val buyPriceMinor: Long,
    val sellPriceMinor: Long? = null,
    val actorId: String,
    val actorName: String,
    val occurredAt: Long,
    val writeId: String,
    val eventId: String,
    val sourceType: String = "INVOICE",
    val sourceId: String = invoiceId,
    val sourceLineId: String? = null,
    val postingGroupId: String? = null,
)

data class InvoiceSaleStockMutation(
    val itemId: String,
    val quantity: Int,
    val invoiceId: String,
    val clientId: String,
    val unitPrice: Double,
    val allowNegativeStock: Boolean,
)

data class InvoicePostingIdentity(
    val writeId: String,
    val sourceLineId: String,
    val postingGroupId: String,
)

data class InvoiceInventoryRevaluationRecord(
    val itemId: String,
    val quantityBefore: Int,
    val oldUnitCostMinor: Long,
    val newUnitCostMinor: Long,
    val revaluationDifferenceMinor: Long,
)

data class InvoiceStockItem(
    val id: String = UUID.randomUUID().toString(),
    val partNumber: String = "",
    val name: String,
    val unitId: String? = null,
    val linkedUnitItemId: String? = null,
    val isUnitItem: Boolean = false,
    val quantityPerUnit: Double = 0.0,
    val isService: Boolean = false,
    val buyPrice: Double = 0.0,
    val buyPriceMinor: Long = Money.fromLegacyDouble(buyPrice).amountMinor,
    val sellPrice: Double = 0.0,
    val sellPriceMinor: Long = Money.fromLegacyDouble(sellPrice).amountMinor,
    val quantity: Int = 0,
    val minQuantity: Int = 5,
    val location: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true
)

enum class InvoiceWriteOperation { CREATE, UPDATE, VOID }

data class InvoiceWriteIdentity(
    val organizationId: String,
    val operation: InvoiceWriteOperation,
    val writeId: String,
    val invoiceId: String,
    val sourceVersion: Int = 1,
)

data class InvoiceWriteClaim(
    val invoiceId: String,
    val claimed: Boolean,
)

class InvoiceAuthorizationException(message: String) : RuntimeException(message)
