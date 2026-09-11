package com.verto.app.feature.shipment.bridge

import com.verto.app.feature.shipment.application.port.LogisticsPlanningReferencePort
import com.verto.app.feature.shipment.application.port.LogisticsUnifiedReadPort
import com.verto.app.feature.shipment.domain.port.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShipmentAppBridgeModule {
    @Provides @Singleton fun provideLogisticsPlanningReferencePort(adapter: AppLogisticsPlanningReferenceAdapter): LogisticsPlanningReferencePort = adapter
    @Provides @Singleton fun provideLogisticsUnifiedReadPort(adapter: LogisticsUnifiedReadModelAdapter): LogisticsUnifiedReadPort = adapter
    @Provides @Singleton fun provideLogisticsShipmentStorePort(adapter: RoomLogisticsV2ShipmentStoreAdapter): LogisticsShipmentStorePort = adapter
    @Provides @Singleton fun provideLogisticsPurchaseInvoiceQueryPort(adapter: RepositoryLogisticsPurchaseInvoiceQueryAdapter): LogisticsPurchaseInvoiceQueryPort = adapter
    @Provides @Singleton fun provideLogisticsInventoryIdentityPort(adapter: RoomLogisticsInventoryIdentityAdapter): LogisticsInventoryIdentityPort = adapter
    @Provides @Singleton fun provideLogisticsShipmentNumberPort(adapter: LogisticsShipmentNumberAdapter): LogisticsShipmentNumberPort = adapter
    @Provides @Singleton fun provideLogisticsAssigneeDirectoryPort(adapter: ExistingLogisticsAssigneeDirectoryAdapter): AssigneeDirectoryPort = adapter
    @Provides @Singleton fun provideLogisticsIdentityPort(adapter: UuidLogisticsIdentityAdapter): LogisticsIdentityPort = adapter
    @Provides @Singleton fun provideLogisticsClockPort(adapter: SystemLogisticsClockAdapter): LogisticsClockPort = adapter
    @Provides @Singleton fun provideLogisticsDocumentStoragePort(adapter: AppPrivateLogisticsDocumentStorageAdapter): LogisticsDocumentStoragePort = adapter
    @Provides @Singleton fun provideLogisticsCashPostingPort(adapter: LogisticsCashPostingAdapter): LogisticsCashPostingPort = adapter
    @Provides @Singleton fun provideLogisticsInventoryPostingPort(adapter: InventoryLogisticsV2PostingAdapter): LogisticsInventoryPostingPort = adapter
    @Provides @Singleton fun provideLogisticsReceivingTransactionPort(adapter: RoomLogisticsReceivingTransactionAdapter): LogisticsReceivingTransactionPort = adapter
    @Provides @Singleton fun provideLogisticsInventoryCostPort(adapter: InventoryLogisticsV2CostAdapter): LogisticsInventoryCostPort = adapter
    @Provides @Singleton fun provideLogisticsPermanentDeletePort(adapter: RoomLogisticsPermanentDeleteAdapter): LogisticsPermanentDeletePort = adapter
}
