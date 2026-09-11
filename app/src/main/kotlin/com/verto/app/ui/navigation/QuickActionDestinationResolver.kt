package com.verto.app.ui.navigation

import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.QuickActionDestinationIds

/** Maps dashboard-safe destinations to concrete app-shell routes. */
internal class QuickActionDestinationResolver {
    fun resolve(destination: HomeDestination): String? = when (destination.id) {
        QuickActionDestinationIds.INVOICE_EDITOR -> when (destination.arguments[INVOICE_TYPE]) {
            SALE_TYPE -> Screen.NewInvoice.route
            PURCHASE_TYPE -> Screen.NewPurchase.route
            else -> null
        }
        QuickActionDestinationIds.PAYMENT_ENTRY -> when (destination.arguments[PARTY_TYPE]) {
            CLIENT_TYPE -> CLIENTS_ROUTE
            SUPPLIER_TYPE -> SUPPLIERS_ROUTE
            else -> null
        }
        QuickActionDestinationIds.ADD_CLIENT -> Screen.AddClient.route
        QuickActionDestinationIds.ADD_SUPPLIER -> Screen.AddSupplier.route
        QuickActionDestinationIds.ADD_INVENTORY_ITEM -> Screen.AddInventoryItem.route
        QuickActionDestinationIds.PRICE_LIST -> Screen.PriceList.route
        else -> null
    }

    private companion object {
        const val INVOICE_TYPE = "type"
        const val SALE_TYPE = "sale"
        const val PURCHASE_TYPE = "purchase"
        const val PARTY_TYPE = "partyType"
        const val CLIENT_TYPE = "client"
        const val SUPPLIER_TYPE = "supplier"
        const val CLIENTS_ROUTE = "clients_list"
        const val SUPPLIERS_ROUTE = "suppliers_list"
    }
}
