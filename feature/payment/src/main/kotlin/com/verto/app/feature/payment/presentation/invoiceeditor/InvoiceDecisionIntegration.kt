package com.verto.app.feature.payment.presentation.invoiceeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.feature.payment.application.model.PaymentCustomerDecision
import com.verto.app.feature.payment.application.model.PaymentMode
import com.verto.app.feature.payment.application.model.PaymentSupplierRecommendation

@Composable
internal fun ObserveInvoiceDecisionContext(
    vm: InvoiceEditorViewModel,
    clientId: String,
    isSale: Boolean,
    paymentMode: PaymentMode,
    inventoryItemId: String,
) {
    LaunchedEffect(clientId, isSale, paymentMode, inventoryItemId) {
        vm.updateDecisionContext(clientId, isSale, paymentMode, inventoryItemId)
    }
}

@Composable
internal fun InvoiceCustomerDecisionSection(
    customerDecision: PaymentCustomerDecision?,
    isAdmin: Boolean,
    isSale: Boolean,
    paymentMode: PaymentMode,
) {
    if (isSale && paymentMode == PaymentMode.CREDIT) {
        customerDecision?.let { CustomerCreditDecisionBanner(it, isAdmin = isAdmin) }
    }
}

@Composable
internal fun InvoiceSupplierDecisionSection(
    supplierRecommendation: PaymentSupplierRecommendation?,
    isSale: Boolean,
    allClients: List<ClientItem>,
    selectedPartyId: String,
) {
    if (!isSale) {
        supplierRecommendation?.let { recommendation ->
            val bestName = allClients.firstOrNull { it.id == recommendation.bestSupplierId }?.name
            SupplierRecommendationBanner(recommendation, bestName, selectedPartyId)
        }
    }
}
