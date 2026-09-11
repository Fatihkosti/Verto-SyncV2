package com.verto.app.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawerSession341Test {
    @Test
    fun approvedSectionOrderIsStable() {
        assertEquals(
            listOf(
                DrawerSection.SALES_CUSTOMERS,
                DrawerSection.PURCHASES_SUPPLIERS,
                DrawerSection.INVENTORY_PRICING,
                DrawerSection.FINANCE_REPORTS,
                DrawerSection.MANAGEMENT,
                DrawerSection.SETTINGS,
            ),
            DrawerSection.entries,
        )
    }

    @Test
    fun legacySectionKeysMigrateWithoutCrash() {
        assertEquals(DrawerSection.SALES_CUSTOMERS, DrawerSection.fromStorageKey("records"))
        assertEquals(DrawerSection.PURCHASES_SUPPLIERS, DrawerSection.fromStorageKey("specialized_operations"))
        assertEquals(DrawerSection.MANAGEMENT, DrawerSection.fromStorageKey("management"))
        assertEquals(DrawerSection.MANAGEMENT, DrawerSection.fromStorageKey("oversight"))
        assertEquals(DrawerSection.SETTINGS, DrawerSection.fromStorageKey("system"))
        assertNull(DrawerSection.fromStorageKey("unknown"))
    }

    @Test
    fun drawerContainsApprovedScopedDestinationsAndNoLegacyRows() {
        val ids = DrawerDestinationRegistry.destinations.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("local_suppliers" in ids)
        assertTrue("international_suppliers" in ids)
        assertTrue("local_purchase_invoices" in ids)
        assertTrue("international_purchase_invoices" in ids)
        assertTrue("benzine" in ids)
        assertFalse("notifications" in ids)
        assertFalse("management_hub" in ids)
        assertFalse("employee_invite" in ids)
        assertFalse("settings" in ids)
        assertFalse(ids.any { it.startsWith("optimal_") && it != "optimal_home" })
        assertFalse("max" in ids)
    }

    @Test
    fun scopedRouteSelectionIsExact() {
        assertEquals(
            "local_suppliers",
            DrawerDestinationRegistry.selectedDestination(
                Screen.SuppliersByScope.route,
                mapOf("supplierScope" to "local"),
            )?.id,
        )
        assertEquals(
            "international_suppliers",
            DrawerDestinationRegistry.selectedDestination(
                Screen.SuppliersByScope.route,
                mapOf("supplierScope" to "international"),
            )?.id,
        )
        assertEquals(
            "international_purchase_invoices",
            DrawerDestinationRegistry.selectedDestination(
                Screen.PurchaseInvoicesByScope.route,
                mapOf("purchaseScope" to "international"),
            )?.id,
        )
    }

    @Test
    fun deepSupplierRouteSelectsPurchasesSectionWithoutDatabaseLookup() {
        assertEquals(
            DrawerSection.PURCHASES_SUPPLIERS,
            DrawerDestinationRegistry.selectedSection(Screen.SupplierDashboard.route, emptyMap()),
        )
    }
}
