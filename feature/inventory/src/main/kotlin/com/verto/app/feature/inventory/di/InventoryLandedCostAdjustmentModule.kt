package com.verto.app.feature.inventory.di

import com.verto.app.feature.inventory.data.RoomInventoryLandedCostAdjustmentAdapter
import com.verto.app.feature.inventory.domain.port.InventoryLandedCostAdjustmentPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryLandedCostAdjustmentModule {
    @Binds
    abstract fun bindInventoryLandedCostAdjustmentPort(
        adapter: RoomInventoryLandedCostAdjustmentAdapter,
    ): InventoryLandedCostAdjustmentPort
}
