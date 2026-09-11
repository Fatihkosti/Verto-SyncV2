package com.verto.app.feature.inventory.bridge

import com.verto.app.data.repository.OrgSettingsRepository
import com.verto.app.feature.inventory.domain.port.InventoryOrganizationSettingsPort
import com.verto.app.feature.inventory.domain.port.InventoryShopSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInventoryOrganizationSettingsAdapter @Inject constructor(
    repository: OrgSettingsRepository
) : InventoryOrganizationSettingsPort {
    override val settings: Flow<InventoryShopSettings> = repository.orgSettings.map {
        InventoryShopSettings(shopName = it.shopName, shopPhone = it.shopPhone, address = it.address)
    }
}
