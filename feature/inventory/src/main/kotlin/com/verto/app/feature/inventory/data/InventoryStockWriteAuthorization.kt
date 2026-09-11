package com.verto.app.feature.inventory.data

import com.verto.app.data.remote.PermissionProvider
import javax.inject.Inject
import javax.inject.Singleton

interface InventoryStockWriteAuthorization {
    suspend fun canAdjust(): Boolean
}

class PermissionInventoryStockWriteAuthorization @Inject constructor(
    private val permissionProvider: PermissionProvider,
) : InventoryStockWriteAuthorization {
    override suspend fun canAdjust(): Boolean = permissionProvider.canNow { it.inventoryEdit }
}

/** v267 emergency kill switch. Disabling preserves queued/history data and requires roll-forward. */
@Singleton
class InventoryWriteGate @Inject constructor() {
    @Volatile private var enabled: Boolean = true
    fun isEnabled(): Boolean = enabled
    fun setEnabled(value: Boolean) { enabled = value }
}
