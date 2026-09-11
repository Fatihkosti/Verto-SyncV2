package com.verto.app.feature.invoice.data.pendingaction

import com.verto.app.data.local.entity.InvoiceDueInstallmentEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class InstallmentEffectiveDue370Test {
    private val schedule = listOf(
        row(1, 10_000L, 1_000L),
        row(2, 10_000L, 2_000L),
        row(3, 10_000L, 3_000L),
    )

    @Test fun `paying an installment advances the effective due date`() {
        assertEquals(1_000L, effectiveDueDate370(300.0, 0.0, 999L, schedule))
        assertEquals(2_000L, effectiveDueDate370(300.0, 100.0, 999L, schedule))
        assertEquals(3_000L, effectiveDueDate370(300.0, 200.0, 999L, schedule))
        assertEquals(0L, effectiveDueDate370(300.0, 300.0, 999L, schedule))
    }

    @Test fun `initial payment is excluded from the installment balance`() {
        val remainingSchedule = listOf(row(1, 10_000L, 1_000L), row(2, 10_000L, 2_000L), row(3, 5_000L, 3_000L))
        assertEquals(1_000L, effectiveDueDate370(300.0, 50.0, 999L, remainingSchedule))
        assertEquals(2_000L, effectiveDueDate370(300.0, 150.0, 999L, remainingSchedule))
    }

    private fun row(sequence: Int, amountMinor: Long, dueDate: Long) = InvoiceDueInstallmentEntity(
        id = "i-$sequence", invoiceId = "invoice", sequence = sequence, amountMinor = amountMinor,
        currencyCode = "SDG", dueDate = dueDate, createdAt = 1L, writeId = "write",
    )
}
