package com.verto.app.ui.screens.home

import com.verto.app.data.model.PermissionDeniedException
import com.verto.app.feature.expenses.bridge.enforceExpenseCreatePermission
import com.verto.app.feature.expenses.application.quickaction.ExpensesQuickActionProvider
import com.verto.app.feature.inventory.application.quickaction.InventoryQuickActionProvider
import com.verto.app.feature.invoice.application.quickaction.InvoiceQuickActionProvider
import com.verto.app.feature.party.application.quickaction.PartyQuickActionProvider
import com.verto.app.ui.navigation.QuickActionDestinationResolver
import com.verto.app.ui.navigation.Screen
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.QuickActionDestinationIds
import com.verto.feature.dashboard.api.QuickActionIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQuickActionHardening338Test {
    @Test fun `canonical lookup rejects stale unknown and revoked actions`() {
        val canonical = QuickAction(
            id = QuickActionIds.SALES_INVOICE,
            label = "فاتورة بيع",
            destination = HomeDestination(QuickActionDestinationIds.INVOICE_EDITOR, mapOf("type" to "sale")),
            requiredPermission = HomePermissionKeys.SALES_CREATE,
        )
        val allowed = HomePermissionContext("org", "user", setOf(HomePermissionKeys.SALES_CREATE))
        val denied = HomePermissionContext("org", "user", emptySet())
        assertSame(canonical, resolveCurrentAuthorizedQuickAction(canonical.id, listOf(canonical), allowed))
        assertNull(resolveCurrentAuthorizedQuickAction("unknown", listOf(canonical), allowed))
        assertNull(resolveCurrentAuthorizedQuickAction(canonical.id, emptyList(), allowed))
        assertNull(resolveCurrentAuthorizedQuickAction(canonical.id, listOf(canonical), denied))
        assertNull(resolveCurrentAuthorizedQuickAction(canonical.id, listOf(canonical), null))
    }

    @Test fun `forged UI destination cannot replace canonical destination`() {
        val canonical = QuickAction(
            id = QuickActionIds.SALES_INVOICE,
            label = "canonical",
            destination = HomeDestination(QuickActionDestinationIds.INVOICE_EDITOR, mapOf("type" to "sale")),
        )
        val forged = canonical.copy(destination = HomeDestination(QuickActionDestinationIds.ADD_CLIENT))
        val resolved = resolveCurrentAuthorizedQuickAction(
            actionId = forged.id,
            actions = listOf(canonical),
            context = HomePermissionContext("org", "user", emptySet()),
        )
        assertEquals(canonical.destination, resolved?.destination)
    }

    @Test fun `busy acquisition prevents double submit and preserves other action state`() {
        val state = MutableStateFlow<Set<String>>(emptySet())
        assertTrue(tryAcquireQuickActionBusy(state, QuickActionIds.RECORD_EXPENSE))
        assertFalse(tryAcquireQuickActionBusy(state, QuickActionIds.RECORD_EXPENSE))
        assertTrue(tryAcquireQuickActionBusy(state, QuickActionIds.QUICK_STOCK_COUNT))
        assertEquals(setOf(QuickActionIds.RECORD_EXPENSE, QuickActionIds.QUICK_STOCK_COUNT), state.value)
        releaseQuickActionBusy(state, QuickActionIds.RECORD_EXPENSE)
        assertEquals(setOf(QuickActionIds.QUICK_STOCK_COUNT), state.value)
        releaseQuickActionBusy(state, QuickActionIds.QUICK_STOCK_COUNT)
        assertTrue(state.value.isEmpty())
    }

    @Test fun `quick action effect channel buffers delayed collector and preserves order`() = runBlocking {
        val channel = createQuickActionEffectChannel()
        channel.send(HomeQuickActionEffect.ShowExpenseDialog)
        channel.send(HomeQuickActionEffect.ExpenseSaved)
        assertEquals(HomeQuickActionEffect.ShowExpenseDialog, channel.receive())
        assertEquals(HomeQuickActionEffect.ExpenseSaved, channel.receive())
        channel.close()
        Unit
    }

    @Test fun `resolver fails closed for malformed invoice and payment arguments`() {
        val resolver = QuickActionDestinationResolver()
        assertEquals(Screen.NewInvoice.route, resolver.resolve(HomeDestination(QuickActionDestinationIds.INVOICE_EDITOR, mapOf("type" to "sale"))))
        assertEquals(Screen.NewPurchase.route, resolver.resolve(HomeDestination(QuickActionDestinationIds.INVOICE_EDITOR, mapOf("type" to "purchase"))))
        assertNull(resolver.resolve(HomeDestination(QuickActionDestinationIds.INVOICE_EDITOR)))
        assertNull(resolver.resolve(HomeDestination(QuickActionDestinationIds.INVOICE_EDITOR, mapOf("type" to ""))))
        assertNull(resolver.resolve(HomeDestination(QuickActionDestinationIds.INVOICE_EDITOR, mapOf("type" to "unknown"))))
        assertEquals("clients_list", resolver.resolve(HomeDestination(QuickActionDestinationIds.PAYMENT_ENTRY, mapOf("partyType" to "client"))))
        assertEquals("suppliers_list", resolver.resolve(HomeDestination(QuickActionDestinationIds.PAYMENT_ENTRY, mapOf("partyType" to "supplier"))))
        assertNull(resolver.resolve(HomeDestination(QuickActionDestinationIds.PAYMENT_ENTRY)))
        assertNull(resolver.resolve(HomeDestination(QuickActionDestinationIds.PAYMENT_ENTRY, mapOf("partyType" to ""))))
        assertNull(resolver.resolve(HomeDestination(QuickActionDestinationIds.PAYMENT_ENTRY, mapOf("partyType" to "unknown"))))
        assertNull(resolver.resolve(HomeDestination("unknown")))
    }

    @Test fun `expense authorization fails closed audits denial and preserves cancellation`() = runBlocking {
        var audited = 0
        assertThrows(PermissionDeniedException::class.java) {
            runBlocking {
                enforceExpenseCreatePermission(isAllowed = { false }, auditDenied = { audited++ })
            }
        }
        assertEquals(1, audited)

        var grantedAudit = 0
        enforceExpenseCreatePermission(isAllowed = { true }, auditDenied = { grantedAudit++ })
        assertEquals(0, grantedAudit)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                enforceExpenseCreatePermission(
                    isAllowed = { false },
                    auditDenied = { throw CancellationException("audit-cancel") },
                )
            }
        }
        Unit
    }

    @Test fun `providers expose the fixed session 358 home action set in deterministic order`() = runBlocking {
        val invoice = InvoiceQuickActionProvider().observeQuickActions(
            HomePermissionContext("org", "user", setOf(HomePermissionKeys.SALES_CREATE, HomePermissionKeys.PURCHASES_CREATE)),
        ).first()
        assertEquals(
            listOf(QuickActionIds.SALES_INVOICE, QuickActionIds.PURCHASE, QuickActionIds.INTERNATIONAL_PURCHASE),
            invoice.map { it.id },
        )
        assertEquals(listOf("فاتورة بيع", "فاتورة شراء", "فاتورة شراء دولية"), invoice.map { it.label })

        val inventory = InventoryQuickActionProvider().observeQuickActions(
            HomePermissionContext("org", "user", setOf(HomePermissionKeys.INVENTORY_EDIT, HomePermissionKeys.INVENTORY_VIEW)),
        ).first()
        assertEquals(listOf(QuickActionIds.ADD_INVENTORY_ITEM, QuickActionIds.PRICE_LIST), inventory.map { it.id })

        val expenses = ExpensesQuickActionProvider().observeQuickActions(
            HomePermissionContext("org", "user", setOf(HomePermissionKeys.EXPENSES_CREATE)),
        ).first()
        assertEquals(QuickActionIds.RECORD_EXPENSE, expenses.single().id)

        val party = PartyQuickActionProvider().observeQuickActions(
            HomePermissionContext("org", "user", setOf(HomePermissionKeys.CLIENTS_EDIT)),
        ).first()
        assertEquals(listOf(QuickActionIds.ADD_CLIENT, QuickActionIds.ADD_SUPPLIER), party.map { it.id })
    }
    @Test fun `purchase shortcut stays visible but execution still requires purchase permission`() = runBlocking {
        val deniedContext = HomePermissionContext("org", "user", emptySet())
        val actions = InvoiceQuickActionProvider().observeQuickActions(deniedContext).first()

        assertEquals(listOf(QuickActionIds.PURCHASE), actions.map { it.id })
        assertNull(resolveCurrentAuthorizedQuickAction(QuickActionIds.PURCHASE, actions, deniedContext))
    }

}
