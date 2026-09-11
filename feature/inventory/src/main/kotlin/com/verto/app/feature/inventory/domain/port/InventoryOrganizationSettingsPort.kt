package com.verto.app.feature.inventory.domain.port

import kotlinx.coroutines.flow.Flow

data class InventoryShopSettings(
    val shopName: String = "",
    val shopPhone: String = "",
    val address: String = ""
)

interface InventoryOrganizationSettingsPort {
    val settings: Flow<InventoryShopSettings>
}
