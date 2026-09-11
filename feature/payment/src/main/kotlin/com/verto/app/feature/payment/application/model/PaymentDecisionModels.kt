package com.verto.app.feature.payment.application.model

enum class PaymentCreditDecision { ALLOW_CREDIT, CASH_ONLY, REQUIRES_APPROVAL }

data class PaymentCustomerAccountSnapshot(
    val outstandingByCurrencyMinor: Map<String, Long> = emptyMap(),
    val overdueByCurrencyMinor: Map<String, Long> = emptyMap(),
)

data class PaymentCustomerDecision(
    val decision: PaymentCreditDecision,
    val dataComplete: Boolean,
    val reasons: List<String>,
    val currentlyOverdueInvoiceCount: Int,
    val maxCurrentDaysOverdue: Int,
    val account: PaymentCustomerAccountSnapshot = PaymentCustomerAccountSnapshot(),
)

data class PaymentSupplierRecommendation(
    val inventoryItemId: String,
    val bestSupplierId: String?,
    val bestSupplierScoreBps: Int?,
    val bestSupplierOrderCount: Int?,
    val reasons: List<String>,
)
