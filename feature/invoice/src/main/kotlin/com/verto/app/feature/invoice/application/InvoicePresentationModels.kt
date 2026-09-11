package com.verto.app.feature.invoice.application

import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import java.util.UUID

enum class ClientType(val label: String) {
    INDIVIDUAL("عميل"), COMPANY("شركة"), INSTITUTION("مؤسسة"), WORKSHOP_OWNER("صاحب ورشة"),
    MARKETER("مسوق"), TRADER("تاجر"), DISTRIBUTOR("موزع"), COMPETITOR("منافس"),
    SUPPLIER("مورد"), GLOBAL_SUPPLIER("مورد عالمي"), WHOLESALE_TRADER("تاجر جملة"),
    CAR_OWNER("صاحب سيارة"), MECHANIC("ميكانيكي"), SHOP_OWNER("صاحب محل"), OTHER("أخرى")
}

data class ClientItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val address: String = "",
    val workplace: String = "",
    val generalNote: String = "",
    val customerSegment: String? = null,
    val carType: String = "",
    val bankAccount: String = "",
    val specialty: String = "",
    val secondaryPhones: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val isDirty: Boolean = true,
)

enum class InvoiceType(val label: String) { GOODS("فاتورة") }
enum class InvoiceCategory(val label: String) { SALE("بيع"), PURCHASE("مشتريات") }
enum class InvoiceStatus { CLOSED_CASH, CLOSED_CREDIT }

data class InvoiceViewData(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: Int,
    val clientId: String,
    val type: InvoiceType = InvoiceType.GOODS,
    val category: InvoiceCategory = InvoiceCategory.SALE,
    val description: String,
    val totalAmount: Double,
    val transactionCurrencyCode: String = "",
    val functionalCurrencyCode: String = "",
    val transactionAmountMinor: Long = 0L,
    val invoiceExchangeRateSnapshot: String = "",
    val exchangeRateDirection: String = "FUNCTIONAL_PER_TRANSACTION",
    val exchangeRateTimestamp: Long = 0L,
    val exchangeRateSource: String = "",
    val functionalAmountAtRecognitionMinor: Long = 0L,
    val legacyCurrencyStatus: String = "REVIEW_REQUIRED",
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
    val commission: Double = 0.0,
    val commissionBeneficiaryClientId: String? = null,
    val commissionSource: String = "NONE",
    val shipmentId: String? = null,
    val purchaseScope: PurchaseScope = PurchaseScope.LOCAL,
    val createdBy: String = "",
    val voided: Boolean = false,
    val isDirty: Boolean = true,
)

data class InvoiceLineView(
    val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val itemName: String,
    val itemCategory: String = "",
    val quantity: Int = 1,
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val unitSellPrice: Double = sellPrice,
    val unitSellPriceMinor: Long = 0L,
    val unitCostAtSale: Double = 0.0,
    val unitCostAtSaleMinor: Long = 0L,
    val lineRevenueSnapshot: Double = totalPrice,
    val lineRevenueSnapshotMinor: Long = 0L,
    val lineCostSnapshot: Double = 0.0,
    val lineCostSnapshotMinor: Long = 0L,
    val grossProfitSnapshot: Double = lineRevenueSnapshot - lineCostSnapshot,
    val grossProfitSnapshotMinor: Long = 0L,
    val costSnapshotStatus: String = "LEGACY_UNKNOWN",
    val description: String = "",
    val isOwedToMe: Boolean = true,
    val inventoryItemId: String = "",
    val adjustedPurchasePrice: Double = 0.0,
    val isDirty: Boolean = true,
)

enum class InvoicePaymentMethod { CASH, TRANSFER, CHECK }
data class PaymentItem(
    val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val clientId: String,
    val amount: Double,
    val paymentCurrencyCode: String = "",
    val paymentExchangeRate: String = "",
    val functionalCashAmountMinor: Long = 0L,
    val historicalFunctionalAmountMinor: Long = 0L,
    val realizedFxDifferenceMinor: Long = 0L,
    val legacyCurrencyStatus: String = "REVIEW_REQUIRED",
    val paymentMethod: InvoicePaymentMethod = InvoicePaymentMethod.CASH,
    val note: String = "",
    val paidAt: Long = System.currentTimeMillis(),
    val employeeId: String = "",
    val employeeName: String = "",
    val reversedPaymentId: String? = null,
    val isDirty: Boolean = true,
)

data class InvoiceDueInstallmentView(
    val sequence: Int,
    val amount: Double,
    val currencyCode: String,
    val dueDate: Long,
)

data class InvoiceFinancialViewData(
    val remaining: Double,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val isPaid: Boolean,
    val progressPercent: Float,
)

data class InvoiceSummary(
    val invoice: InvoiceViewData,
    val totalPaid: Double,
    val payments: List<PaymentItem> = emptyList(),
    val dueInstallments: List<InvoiceDueInstallmentView> = emptyList(),
    val financial: InvoiceFinancialViewData,
) {
    val remaining: Double get() = financial.remaining
    val isOverdue: Boolean get() = financial.isOverdue
    val isPaid: Boolean get() = financial.isPaid
    val progressPercent: Float get() = financial.progressPercent
}

enum class InvoiceCommunicationKind { INVOICE_OPENED, REMINDER_OPENED, THANK_YOU_OPENED }

data class InvoiceCommunicationEvent(
    val id: String,
    val kind: InvoiceCommunicationKind,
    val createdAt: Long,
    val employeeName: String = "",
)

data class InvoicePaymentSummary(
    val invoice: InvoiceViewData,
    val totalPaid: Double,
    val financial: InvoiceFinancialViewData,
)

data class InvoiceOrgSettings(
    val shopName: String = "",
    val shopPhone: String = "",
    val city: String = "",
    val address: String = "",
    val currency: String = "",
    val invoiceFooter: String = "",
    val taxNumber: String = "",
    val logoUrl: String = "",
    val signatureUrl: String = "",
)

data class InvoicePrintSettings(
    val template: InvoiceTemplate = InvoiceTemplate.CLASSIC,
    val font: InvoiceFont = InvoiceFont.CAIRO,
    val fontSize: Int = 14,
)

sealed interface InvoicePaymentReversalResult {
    data object Success : InvoicePaymentReversalResult
    data class Error(val presentation: UserErrorPresentation) : InvoicePaymentReversalResult
}

data class InvoicePdfRequest(
    val client: ClientItem,
    val summary: InvoiceSummary,
    val orgSettings: InvoiceOrgSettings,
    val employeeName: String,
    val employeePhone: String,
    val items: List<InvoiceLineView>,
    val printSettings: InvoicePrintSettings,
)
