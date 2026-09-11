package com.verto.app.feature.reports.bridge

import com.verto.app.feature.reports.application.port.ReportsLogisticsSource
import com.verto.app.feature.shipment.bridge.ShipmentReportsLogisticsSourceAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReportsLogisticsBridgeModule {
    @Binds
    abstract fun bindReportsLogisticsSource(
        adapter: ShipmentReportsLogisticsSourceAdapter,
    ): ReportsLogisticsSource
}
