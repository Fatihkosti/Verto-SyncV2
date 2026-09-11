package com.verto.app.data.repository

import com.verto.app.utils.CashReverseContext336
import com.verto.app.utils.expenseCashDependency336
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCashDependency336Test {
    @Test fun `expense create cash effect depends on expense mutation`() {
        assertEquals("expense-create-1", expenseCashDependency336("EXPENSE", "expense-create-1"))
    }

    @Test fun `expense update increase cash effect depends on expense mutation`() {
        assertEquals("expense-update-1", expenseCashDependency336("EXPENSE", "expense-update-1"))
    }

    @Test fun `expense derived source subtype also depends on expense mutation`() {
        assertEquals("expense-update-2", expenseCashDependency336("EXPENSE_UPDATE_REFUND", "expense-update-2"))
    }

    @Test fun `blank expense mutation never creates dependency`() {
        assertNull(expenseCashDependency336("EXPENSE", ""))
    }

    @Test fun `non expense cash movement has no implicit dependency`() {
        assertNull(expenseCashDependency336("INVOICE", "invoice-1"))
    }

    @Test fun `reverse context preserves explicit expense dependency`() {
        val context = CashReverseContext336("EXPENSE_VOID_REFUND", "expense-void-1")
        assertEquals("EXPENSE_VOID_REFUND", context.sourceType)
        assertEquals("expense-void-1", context.dependsOnMutationId)
    }

    @Test fun `reverse dependency refers to expense parent not cash child`() {
        val context = CashReverseContext336("EXPENSE_VOID_REFUND", "expense-void-1")
        val cashMutationId = "cash:movement-derived-from-expense-void-1"
        assertTrue(cashMutationId != context.dependsOnMutationId)
    }

    @Test fun `update decrease produces exact refund amount and source`() = runBlocking {
        var refundAmount = 0L
        var refundSource = ""
        applyExpenseAmountDelta(10_000L, 7_000L, cashOut = { error("unexpected cash out") }) { amount, source ->
            refundAmount = amount
            refundSource = source
        }
        assertEquals(3_000L, refundAmount)
        assertEquals(EXPENSE_UPDATE_REFUND_SOURCE, refundSource)
    }

    @Test fun `update increase produces exact cash out and no refund`() = runBlocking {
        var cashOut = 0L
        var refunds = 0
        applyExpenseAmountDelta(7_000L, 10_000L, cashOut = { cashOut += it }) { _, _ -> refunds++ }
        assertEquals(3_000L, cashOut)
        assertEquals(0, refunds)
    }

    @Test fun `equal update creates no cash effect`() = runBlocking {
        var cashOuts = 0
        var refunds = 0
        applyExpenseAmountDelta(7_000L, 7_000L, cashOut = { cashOuts++ }) { _, _ -> refunds++ }
        assertEquals(0, cashOuts)
        assertEquals(0, refunds)
    }

    @Test fun `void helper refunds exactly once for already voided state`() = runBlocking {
        var lifecycle = "ACTIVE"
        var refunds = 0
        suspend fun invoke(): Boolean = voidExpenseEffects(
            current = lifecycle,
            isVoided = { it == "VOID" },
            refund = { refunds++ },
            toVoided = { "VOID" },
            commitVoid = { lifecycle = it },
        )
        assertTrue(invoke())
        assertTrue(!invoke())
        assertEquals(1, refunds)
    }
}
