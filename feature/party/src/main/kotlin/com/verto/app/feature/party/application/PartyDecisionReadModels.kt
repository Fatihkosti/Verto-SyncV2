package com.verto.app.feature.party.application

/** Stable cross-feature read contract. Internal decision engines stay private to feature:party. */
enum class PartyCreditGate { ALLOW_CREDIT, CASH_ONLY, REQUIRES_APPROVAL }

data class PartyCustomerAccountReadModel(
    val outstandingByCurrencyMinor: Map<String, Long> = emptyMap(),
    val overdueByCurrencyMinor: Map<String, Long> = emptyMap(),
)

data class PartyCustomerDecisionReadModel(
    val gate: PartyCreditGate,
    val dataComplete: Boolean,
    val reasons: List<String>,
    val currentlyOverdueInvoiceCount: Int,
    val maxCurrentDaysOverdue: Int,
    val account: PartyCustomerAccountReadModel = PartyCustomerAccountReadModel(),
)

data class PartySupplierRecommendationReadModel(
    val inventoryItemId: String,
    val bestSupplierId: String?,
    val bestSupplierScoreBps: Int?,
    val bestSupplierOrderCount: Int?,
    val reasons: List<String>,
)
