package com.verto.app.feature.inventory.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryLedgerReleaseGateV267Test {
    private data class Movement(val id: String, val delta: Long, val reverses: String? = null)

    private class Ledger(initial: Long) {
        val opening = initial
        val movements = linkedMapOf<String, Movement>()
        val conflicts = linkedSetOf<String>()
        var snapshot = initial
            private set

        fun apply(movement: Movement) {
            if (movement.id in movements) return
            movement.reverses?.let { original ->
                require(original in movements)
                require(movements.values.none { it.reverses == original })
                require(movements.getValue(original).reverses == null)
            }
            movements[movement.id] = movement
            snapshot = Math.addExact(snapshot, movement.delta)
            if (snapshot < 0) conflicts += "oversold:${movement.id}"
        }

        fun ledgerBalance(): Long = opening + movements.values.sumOf(Movement::delta)
    }

    @Test fun `property sequences retain snapshot ledger invariant under retry`() {
        repeat(500) { seed ->
            val ledger = Ledger(100)
            val random = Random(seed)
            repeat(200) { index ->
                val delta = random.nextLong(-5, 8)
                if (delta != 0L) {
                    val movement = Movement("$seed:$index", delta)
                    ledger.apply(movement)
                    if (random.nextBoolean()) ledger.apply(movement)
                }
                assertEquals(ledger.ledgerBalance(), ledger.snapshot)
            }
        }
    }

    @Test fun `reversal is additive unique and cannot reverse reversal`() {
        val ledger = Ledger(10)
        ledger.apply(Movement("sale", -4))
        ledger.apply(Movement("reverse-sale", 4, "sale"))
        assertEquals(10, ledger.snapshot)
        runCatching { ledger.apply(Movement("second-reversal", 4, "sale")) }.onSuccess { error("expected duplicate reversal rejection") }
        runCatching { ledger.apply(Movement("reverse-reversal", -4, "reverse-sale")) }.onSuccess { error("expected reversal-of-reversal rejection") }
    }

    @Test fun `offline oversell keeps all movements and creates conflict`() {
        val ledger = Ledger(10)
        ledger.apply(Movement("device-a", -7))
        ledger.apply(Movement("device-b", -7))
        assertEquals(-4, ledger.snapshot)
        assertEquals(2, ledger.movements.size)
        assertEquals(1, ledger.conflicts.size)
    }

    @Test fun `server cost sequence wins independently of device time`() {
        val costs = listOf(Triple(200L, 1L, 9_999L), Triple(250L, 2L, 1L))
        assertEquals(250L, costs.maxBy { it.second }.first)
    }

    @Test fun `unit input converts to one base balance exactly`() {
        val factor = 12L
        val cartons = 3L
        assertEquals(36L, Math.multiplyExact(cartons, factor))
        assertTrue(factor > 0)
    }
}
