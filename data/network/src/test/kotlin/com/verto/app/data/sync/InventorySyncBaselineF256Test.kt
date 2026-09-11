package com.verto.app.data.sync

import com.verto.app.data.remote.dto.InventoryItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

/** v256 characterization of the mutable inventory snapshot sync contract. */
class InventorySyncBaselineF256Test {
    @Test
    fun inventory_wire_contract_currently_contains_quantity_snapshot() {
        val dto = InventoryItemDto(id = "item", organizationId = "org", name = "Item", quantity = 17)
        assertEquals(17, dto.quantity)
    }

    @Test
    fun dirty_local_snapshot_wins_even_when_remote_timestamp_is_newer() {
        assertEquals(
            SyncConflictResolution.KEEP_LOCAL,
            SyncConflictPolicy.resolve(localDirty = true, localUpdatedAt = 100L, remoteUpdatedAt = 200L),
        )
    }

    @Test
    fun clean_snapshot_uses_updated_at_last_writer_semantics() {
        assertEquals(
            SyncConflictResolution.APPLY_REMOTE,
            SyncConflictPolicy.resolve(localDirty = false, localUpdatedAt = 100L, remoteUpdatedAt = 200L),
        )
        assertEquals(
            SyncConflictResolution.KEEP_LOCAL,
            SyncConflictPolicy.resolve(localDirty = false, localUpdatedAt = 200L, remoteUpdatedAt = 100L),
        )
    }
}
