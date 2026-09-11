package com.verto.app.data.sync.expense

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ExpenseRevisionContractB12Test {
    private fun expense(amountMinor: Long = 10_000L, note: String = "old", lifecycle: String = "ACTIVE") =
        ExpenseRevisionSnapshotB12(
            id = "11111111-1111-1111-1111-111111111111",
            category = "GENERAL",
            item = "Fuel",
            amountMinor = amountMinor,
            note = note,
            date = 1_700_000_000_000L,
            lifecycleState = lifecycle,
            voidedAt = if (lifecycle == "VOID") 1_700_000_001_000L else null,
            voidReason = if (lifecycle == "VOID") "USER_DELETE" else null,
            reversalWriteId = if (lifecycle == "VOID") "write-3" else null,
        )

    @Test fun `T21 note edit has zero cash delta`() {
        assertEquals(0L, expenseCashDeltaMinorB12(expense(note = "old"), expense(note = "new")))
    }

    @Test fun `T21 amount 10000 to 15000 removes exactly 5000 cash`() {
        assertEquals(-5_000L, expenseCashDeltaMinorB12(expense(10_000L), expense(15_000L)))
    }

    @Test fun `T21 void refunds the full effective 15000`() {
        assertEquals(15_000L, expenseCashDeltaMinorB12(expense(15_000L), expense(15_000L, lifecycle = "VOID")))
    }

    @Test fun `identity remains stable while mutable content changes`() {
        val before = expense()
        val after = expense(15_000L, note = "new")
        assertEquals(before.id, after.id)
        assertNotEquals(expenseContentHashB12(before), expenseContentHashB12(after))
    }
}
