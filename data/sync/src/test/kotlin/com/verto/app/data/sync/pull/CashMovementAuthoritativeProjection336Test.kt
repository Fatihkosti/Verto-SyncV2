package com.verto.app.data.sync.pull

import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.CashRegisterMovementEntity
import com.verto.app.data.sync.push.dependencyBlockReason336
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashMovementAuthoritativeProjection336Test {
    private fun row(
        id: String = "m1",
        type: CashMovementType = CashMovementType.EXPENSE,
        amount: Double = -20.0,
        amountMinor: Long = -2_000L,
        before: Double = 100.0,
        beforeMinor: Long = 10_000L,
        after: Double = 80.0,
        afterMinor: Long = 8_000L,
        referenceId: String = "E1",
        note: String = "expense",
        sourceType: String = "EXPENSE",
        sourceId: String = "E1",
        sourceVersion: Int = 1,
        writeId: String = "w1",
        createdAt: Long = 1234L,
    ) = CashRegisterMovementEntity(
        id = id,
        movementType = type,
        amount = amount,
        amountMinor = amountMinor,
        balanceBefore = before,
        balanceBeforeMinor = beforeMinor,
        balanceAfter = after,
        balanceAfterMinor = afterMinor,
        referenceId = referenceId,
        note = note,
        sourceType = sourceType,
        sourceId = sourceId,
        sourceVersion = sourceVersion,
        writeId = writeId,
        createdAt = createdAt,
    )

    @Test fun `same intent ignores authoritative balance minors`() {
        assertTrue(sameCashMovementIntent336(row(), row(beforeMinor = 7_000L, afterMinor = 5_000L)))
    }

    @Test fun `same intent ignores balance double projections`() {
        assertTrue(sameCashMovementIntent336(row(), row(before = 70.0000001, after = 50.0000001)))
    }

    @Test fun `same intent ignores raw amount double projection`() {
        assertTrue(sameCashMovementIntent336(row(), row(amount = -20.000000000000004)))
    }

    @Test fun `amount minor mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(amountMinor = -3_000L)))
    }

    @Test fun `movement type mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(type = CashMovementType.MANUAL_ADD)))
    }

    @Test fun `reference mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(referenceId = "E2")))
    }

    @Test fun `note mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(note = "other")))
    }

    @Test fun `source type mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(sourceType = "INVOICE")))
    }

    @Test fun `source id mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(sourceId = "E2")))
    }

    @Test fun `source version mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(sourceVersion = 2)))
    }

    @Test fun `write id mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(writeId = "w2")))
    }

    @Test fun `created at mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(createdAt = 1235L)))
    }

    @Test fun `id mismatch conflicts`() {
        assertFalse(sameCashMovementIntent336(row(), row(id = "m2")))
    }

    @Test fun `canonical projection derives amount from minor units`() {
        assertEquals(-20.0, canonicalAuthoritativeCashMovement336(row(amount = -20.0000000004)).amount, 0.0)
    }

    @Test fun `canonical projection derives causal balances from minor units`() {
        val canonical = canonicalAuthoritativeCashMovement336(row(before = 999.0, beforeMinor = 7_000L, after = 999.0, afterMinor = 5_000L))
        assertEquals(70.0, canonical.balanceBefore, 0.0)
        assertEquals(50.0, canonical.balanceAfter, 0.0)
    }

    @Test fun `projection difference is detected for server reorder`() {
        val local = row()
        val remote = canonicalAuthoritativeCashMovement336(row(beforeMinor = 7_000L, afterMinor = 5_000L))
        assertTrue(cashProjectionDiffers336(local, remote))
    }

    @Test fun `equal canonical projection is no op`() {
        val local = canonicalAuthoritativeCashMovement336(row())
        val remote = canonicalAuthoritativeCashMovement336(row())
        assertFalse(cashProjectionDiffers336(local, remote))
    }

    @Test fun `A then B server reorder reconciles B without semantic conflict`() {
        val bLocal = row(id = "b", writeId = "wb", amountMinor = -2_000L, amount = -20.0, beforeMinor = 10_000L, afterMinor = 8_000L)
        val bServer = canonicalAuthoritativeCashMovement336(
            bLocal.copy(balanceBeforeMinor = 7_000L, balanceAfterMinor = 5_000L)
        )
        assertTrue(sameCashMovementIntent336(bLocal, bServer))
        assertEquals(7_000L, bServer.balanceBeforeMinor)
        assertEquals(5_000L, bServer.balanceAfterMinor)
    }

    @Test fun `B then A server reorder reconciles A without semantic conflict`() {
        val aLocal = row(id = "a", writeId = "wa", amountMinor = -3_000L, amount = -30.0, beforeMinor = 10_000L, afterMinor = 7_000L)
        val aServer = canonicalAuthoritativeCashMovement336(
            aLocal.copy(balanceBeforeMinor = 8_000L, balanceAfterMinor = 5_000L)
        )
        assertTrue(sameCashMovementIntent336(aLocal, aServer))
        assertEquals(8_000L, aServer.balanceBeforeMinor)
        assertEquals(5_000L, aServer.balanceAfterMinor)
    }

    @Test fun `three device server causal projections converge to one balance`() {
        val a = row(id = "a", writeId = "wa", amountMinor = -1_000L, amount = -10.0, beforeMinor = 10_000L, afterMinor = 9_000L)
        val b = row(id = "b", writeId = "wb", amountMinor = -2_000L, amount = -20.0, beforeMinor = 10_000L, afterMinor = 8_000L)
        val c = row(id = "c", writeId = "wc", type = CashMovementType.MANUAL_ADD, amountMinor = 500L, amount = 5.0, beforeMinor = 10_000L, afterMinor = 10_500L)
        val server = listOf(
            canonicalAuthoritativeCashMovement336(a.copy(balanceBeforeMinor = 10_000L, balanceAfterMinor = 9_000L)),
            canonicalAuthoritativeCashMovement336(b.copy(balanceBeforeMinor = 9_000L, balanceAfterMinor = 7_000L)),
            canonicalAuthoritativeCashMovement336(c.copy(balanceBeforeMinor = 7_000L, balanceAfterMinor = 7_500L)),
        )
        assertEquals(7_500L, server.last().balanceAfterMinor)
        assertTrue(sameCashMovementIntent336(a, server[0]))
        assertTrue(sameCashMovementIntent336(b, server[1]))
        assertTrue(sameCashMovementIntent336(c, server[2]))
    }

    @Test fun `semantic corruption remains fail closed after projection canonicalization`() {
        val local = row()
        val corrupted = canonicalAuthoritativeCashMovement336(row(amountMinor = -3_000L))
        assertFalse(sameCashMovementIntent336(local, corrupted))
    }

    @Test fun `dependency missing blocks`() {
        assertEquals("DEPENDENCY_MISSING", dependencyBlockReason336(null))
    }

    @Test fun `dependency pending blocks`() {
        assertEquals("DEPENDENCY_NOT_ACKNOWLEDGED", dependencyBlockReason336("PENDING"))
    }

    @Test fun `dependency leased blocks`() {
        assertEquals("DEPENDENCY_NOT_ACKNOWLEDGED", dependencyBlockReason336("LEASED"))
    }

    @Test fun `dependency retry blocks`() {
        assertEquals("DEPENDENCY_NOT_ACKNOWLEDGED", dependencyBlockReason336("RETRY"))
    }

    @Test fun `dependency acknowledged is eligible`() {
        assertNull(dependencyBlockReason336("ACKNOWLEDGED"))
    }

    @Test fun `dependency rejected fails closed`() {
        assertEquals("DEPENDENCY_FAILED", dependencyBlockReason336("REJECTED"))
    }

    @Test fun `dependency review fails closed`() {
        assertEquals("DEPENDENCY_FAILED", dependencyBlockReason336("REQUIRES_REVIEW"))
    }

    @Test fun `unknown dependency state fails closed`() {
        assertEquals("DEPENDENCY_FAILED", dependencyBlockReason336("UNKNOWN"))
    }
}
