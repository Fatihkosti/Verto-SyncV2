package com.verto.app.feature.inventory.bridge

import com.verto.app.feature.inventory.domain.port.InventoryOrganizationSettingsPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryAppBridgeModule {
    @Binds
    @Singleton
    abstract fun bindInventoryOrganizationSettings(
        adapter: AppInventoryOrganizationSettingsAdapter
    ): InventoryOrganizationSettingsPort
}
