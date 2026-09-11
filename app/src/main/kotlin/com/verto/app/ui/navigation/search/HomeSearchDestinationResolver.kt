package com.verto.app.ui.navigation.search

import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import com.verto.app.ui.navigation.Screen
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds

/** Converts dashboard-safe destinations into concrete app-shell routes. */
class HomeSearchDestinationResolver internal constructor(
    private val catalog: AppSearchCatalog,
) {
    constructor() : this(AppSearchCatalog.default)

    fun resolve(destination: HomeDestination): String? =
        catalog.resolve(destination) ?: when (destination.id) {
            HomeDestinationIds.PARTY_DETAILS -> destination.partyDetailsRoute()
            HomeDestinationIds.PARTY_PAYMENT -> destination.partyPaymentRoute()
            HomeDestinationIds.INVOICE_DETAILS,
            HomeDestinationIds.INVOICE_EDIT ->
                destination.argument("invoiceId")?.let { Screen.Invoice.create(it) }

            HomeDestinationIds.INVOICE_PAYMENT -> {
                val invoiceId = destination.argument("invoiceId")
                val partyId = destination.argument("partyId")
                if (invoiceId != null && partyId != null) Screen.AddPayment.create(invoiceId, partyId) else null
            }

            // No read-only item detail route exists yet. Opening Inventory preserves view-only access.
            HomeDestinationIds.INVENTORY_ITEM_DETAILS -> Screen.Inventory.route
            HomeDestinationIds.INVENTORY_ITEM_EDIT ->
                destination.argument("itemId")?.let { Screen.EditInventoryItem.create(it) }

            HomeDestinationIds.SHIPMENT_DETAILS,
            HomeDestinationIds.SHIPMENT_STAGE_DOCUMENTS ->
                destination.argument("shipmentId")?.let { Screen.ShipmentDetail.create(it) }

            HomeDestinationIds.PRICE_LIST -> "price_list"

            HomeDestinationIds.OPTIMAL_MAINTENANCE_DETAILS ->
                destination.argument("recordId")?.let(OptimalNavigation::maintenanceDetailsRoute)

            // Reversal remains an inline invoice operation; primary payment cards open the invoice.
            HomeDestinationIds.PAYMENT_REVERSE ->
                destination.argument("invoiceId")?.let { Screen.Invoice.create(it) }

            else -> null
        }

    private fun HomeDestination.partyDetailsRoute(): String? {
        val partyId = argument("partyId") ?: return null
        return if (argument("isSupplier").toBoolean()) {
            Screen.SupplierDashboard.create(partyId)
        } else {
            Screen.ClientDashboard.create(partyId)
        }
    }

    private fun HomeDestination.partyPaymentRoute(): String? {
        val partyId = argument("partyId") ?: return null
        return if (argument("isSupplier").toBoolean()) {
            "supplier_payment/$partyId"
        } else {
            "client_payment/$partyId"
        }
    }

    private fun HomeDestination.argument(name: String): String? =
        arguments[name]?.takeIf(String::isNotBlank)
}
