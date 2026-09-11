package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.port.InvoiceAuthorizationPort
import com.verto.app.feature.invoice.domain.port.PurchaseSupplierRecommendation
import com.verto.app.feature.invoice.domain.port.PurchaseSupplierRecommendationPort
import javax.inject.Inject

/** Keeps purchase-cycle authorization and advisory supplier intelligence behind one application policy. */
class PurchaseCyclePolicy @Inject constructor(
    private val authorization: InvoiceAuthorizationPort,
    private val supplierRecommendation: PurchaseSupplierRecommendationPort,
) {
    suspend fun canPostPurchase(): Boolean = authorization.canPost(InvoiceCategory.PURCHASE)

    suspend fun recommendationForItem(inventoryItemId: String): PurchaseSupplierRecommendation =
        supplierRecommendation.recommendationForItem(inventoryItemId)
}
