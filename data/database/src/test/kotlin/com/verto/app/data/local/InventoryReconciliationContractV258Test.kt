package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryReconciliationContractV258Test {
    @Test
    fun `schema 74 is connected from 73`() {
        val path = migrationPath(73, 74)
        assertEquals(1, path.size)
        assertEquals(73, path.single().startVersion)
        assertEquals(74, path.single().endVersion)
    }

    @Test
    fun `deterministic key is identical on every device`() {
        val a = marker(canonical = 10, legacy = 0, delta = 10, movementId = "server-m1", sequence = 7)
        val b = a.copy(sourceDeviceId = null)
        assertEquals("inventory-reconcile:2:org-1:item-1", a.deterministicIdempotencyKey())
        assertEquals(a.deterministicIdempotencyKey(), b.deterministicIdempotencyKey())
    }

    @Test
    fun `marker accepts positive negative and zero deltas only when arithmetic matches`() {
        marker(10, 0, 10, "m1", 1).validWithChecksum().requireValid()
        marker(3, 8, -5, "m2", 2).validWithChecksum().requireValid()
        marker(4, 4, 0, null, null).validWithChecksum().requireValid()
        assertThrows(IllegalArgumentException::class.java) {
            marker(10, 0, 9, "m3", 3).validWithChecksum().requireValid()
        }
    }

    @Test
    fun `checksum detects changed canonical snapshot`() {
        val valid = marker(10, 0, 10, "m1", 1).validWithChecksum()
        valid.requireValid()
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(canonicalSnapshot = 11, reconciliationDelta = 11).requireValid()
        }
    }

    @Test
    fun `owner device authority must identify locked source`() {
        val invalid = marker(10, 0, 10, "m1", 1).copy(authorityKind = "OWNER_DEVICE", sourceDeviceId = null)
            .validWithChecksum()
        assertThrows(IllegalArgumentException::class.java) { invalid.requireValid() }
    }

    private fun marker(
        canonical: Long,
        legacy: Long,
        delta: Long,
        movementId: String?,
        sequence: Long?,
    ) = AuthoritativeInventoryReconciliationMarker(
        organizationId = "org-1",
        itemId = "item-1",
        contractVersion = INVENTORY_RECONCILIATION_CONTRACT_VERSION,
        authorityKind = "CENTRAL",
        sourceDeviceId = null,
        canonicalSnapshot = canonical,
        authoritativeLegacyBalance = legacy,
        reconciliationDelta = delta,
        reconciliationMovementId = movementId,
        idempotencyKey = "inventory-reconcile:2:org-1:item-1",
        serverSequence = sequence,
        markerChecksum = "pending",
        approvedAt = 100,
        serverAcceptedAt = 101,
        approvedBy = "user-1",
    )

    private fun AuthoritativeInventoryReconciliationMarker.validWithChecksum() =
        copy(markerChecksum = calculatedChecksum())
}
