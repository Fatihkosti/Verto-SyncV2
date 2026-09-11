package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV240StationReceiptTest {
    @Test
    fun `station receipt defaults to same confirmed carton count`() {
        val draft = LogisticsCustodyHandoffDraft(
            scope = LogisticsCustodyHandoffScope("shipment"),
            currentCargo = LogisticsCargoSnapshot(packageCount = 18),
        )
        assertEquals("18", draft.handoverPackageCount)
        assertEquals("18", draft.receivedPackageCount)
        assertFalse(draft.isDiscrepant)
        assertTrue(draft.isValid)
    }

    @Test
    fun `carton discrepancy requires reason and preserves received count`() {
        val base = LogisticsCustodyHandoffDraft(
            scope = LogisticsCustodyHandoffScope("shipment"),
            currentCargo = LogisticsCargoSnapshot(packageCount = 18),
        )
        val missing = base.copy(receivedPackageCount = "17")
        assertTrue(missing.isDiscrepant)
        assertFalse(missing.isValid)
        val explained = missing.copy(discrepancyNote = "كرتونة ناقصة عند الاستلام")
        assertTrue(explained.isValid)
        assertEquals(17, explained.receivedCount)
    }
}
