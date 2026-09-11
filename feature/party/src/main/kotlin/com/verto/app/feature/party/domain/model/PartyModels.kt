package com.verto.app.feature.party.domain.model

/** Pure Party models. They deliberately contain no Android, Room, network, or Data-layer types. */
data class PartyClient(
    val id: String,
    val name: String,
    val phone: String,
    val address: String = "",
    val workplace: String = "",
    val generalNote: String = "",
    val carType: String = "",
    val bankAccount: String = "",
    val specialty: String = "",
    val secondaryPhones: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val isDirty: Boolean = true,
    /** Party V2 role/profile projection; null when this identity has no active CUSTOMER/SUPPLIER profile. */
    val customerSegment: CustomerSegment? = null,
    val supplierScope: SupplierScope? = null,
)

enum class PartyClientStatus { RED, GREEN, GREY }

data class PartyClientSummary(
    val client: PartyClient,
    val totalDebt: Double,
    val totalPaid: Double,
    val status: PartyClientStatus,
    val competitorBalance: Double = 0.0,
    val remaining: Double = totalDebt - totalPaid
)

enum class PartyInvoiceType { GOODS }
enum class PartyInvoiceCategory { SALE, PURCHASE }
enum class PartyInvoiceStatus { CLOSED_CASH, CLOSED_CREDIT }
enum class PartyPaymentMethod { CASH, TRANSFER, CHECK }

data class PartyInvoice(
    val id: String,
    val invoiceNumber: Int,
    val clientId: String,
    val type: PartyInvoiceType,
    val category: PartyInvoiceCategory,
    val description: String,
    val totalAmount: Double,
    val createdAt: Long,
    val dueDate: Long,
    val notifyDaysBefore: String = "1,3,7",
    val notifyRepeatDays: Int = 3,
    val notificationsEnabled: Boolean = true,
    val notes: String = "",
    val isOwedToMe: Boolean = true,
    val imageUri: String = "",
    val status: PartyInvoiceStatus = PartyInvoiceStatus.CLOSED_CASH,
    val commission: Double = 0.0,
    val shipmentId: String? = null,
    val createdBy: String = "",
    val voided: Boolean = false,
    val isDirty: Boolean = true,
    /** Transaction-currency snapshot. Blank means legacy/unknown and must never be mixed with known currencies. */
    val transactionCurrencyCode: String = "",
    val transactionAmountMinor: Long = 0L,
    val commissionMinor: Long = 0L,
    /** Functional-currency recognition snapshot used for profit conversion. */
    val functionalCurrencyCode: String = "",
    val functionalAmountAtRecognitionMinor: Long = 0L,
    val legacyCurrencyKnown: Boolean = false,
)

data class PartyPayment(
    val id: String,
    val invoiceId: String,
    val clientId: String,
    val amount: Double,
    /** Exact amount settling the invoice in its transaction currency. */
    val amountMinor: Long = 0L,
    val currencyCode: String = "",
    val currencyKnown: Boolean = false,
    val paymentMethod: PartyPaymentMethod,
    val note: String = "",
    val paidAt: Long,
    val employeeId: String = "",
    val employeeName: String = "",
    val reversedPaymentId: String? = null,
    val isDirty: Boolean = true,
)

data class PartyInvoiceFinancialState(
    val remaining: Double,
    val isOverdue: Boolean,
    val isPaid: Boolean,
    val progressPercent: Float,
    val isCredit: Boolean
)

data class PartyInvoiceSummary(
    val invoice: PartyInvoice,
    val totalPaid: Double,
    val payments: List<PartyPayment> = emptyList(),
    val financial: PartyInvoiceFinancialState
) {
    val remaining: Double get() = financial.remaining
    val isOverdue: Boolean get() = financial.isOverdue
    val isPaid: Boolean get() = financial.isPaid
    val progressPercent: Float get() = financial.progressPercent
}

data class PartyInvoiceItem(
    val id: String,
    val invoiceId: String,
    val itemName: String,
    val itemCategory: String = "",
    val quantity: Int = 1,
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val description: String = "",
    val isOwedToMe: Boolean = true,
    val inventoryItemId: String = "",
    val adjustedPurchasePrice: Double = 0.0,
    val isDirty: Boolean = true,
    /** Immutable sale-economics snapshots. */
    val lineRevenueSnapshotMinor: Long = 0L,
    val lineCostSnapshotMinor: Long = 0L,
    val grossProfitSnapshotMinor: Long = 0L,
    val costSnapshotStatus: String = "LEGACY_UNKNOWN",
)
