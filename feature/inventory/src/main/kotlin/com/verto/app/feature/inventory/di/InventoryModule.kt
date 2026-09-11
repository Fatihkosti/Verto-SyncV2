package com.verto.app.feature.inventory.di

import com.verto.app.data.local.AppDatabase
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.local.dao.CategoryDao
import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.data.local.dao.InventoryUnitDao
import com.verto.app.data.local.dao.ItemCategoryDao
import com.verto.app.data.repository.InventoryRepository
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.feature.inventory.application.DeleteInventoryCoordinator
import com.verto.app.feature.inventory.application.InventoryPriceCoordinator
import com.verto.app.feature.inventory.application.SaveInventoryItemCoordinator
import com.verto.app.feature.inventory.application.SaveInventoryUnitItemCoordinator
import com.verto.app.feature.inventory.application.activityevent.InventoryActivityEventProvider
import com.verto.app.feature.inventory.application.activityevent.InventoryActivityEventSource
import com.verto.app.feature.inventory.application.search.InventoryHomeSearchProvider
import com.verto.app.feature.inventory.application.pendingaction.InventoryPendingActionClock
import com.verto.app.feature.inventory.application.pendingaction.InventoryPendingActionProvider
import com.verto.app.feature.inventory.application.pendingaction.InventoryPendingActionSource
import com.verto.app.feature.inventory.application.quickaction.InventoryQuickActionProvider
import com.verto.app.feature.inventory.application.search.InventoryHomeSearchSource
import com.verto.app.feature.inventory.application.port.*
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.feature.inventory.data.*
import com.verto.app.feature.inventory.data.search.RoomInventoryHomeSearchSource
import com.verto.app.feature.inventory.data.activityevent.RoomInventoryActivityEventSource
import com.verto.app.feature.inventory.data.pendingaction.RoomInventoryPendingActionSource
import com.verto.app.feature.inventory.data.pendingaction.SystemInventoryPendingActionClock
import com.verto.app.feature.inventory.data.sync.InventorySyncParticipant
import com.verto.app.feature.inventory.domain.port.*
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.PendingActionProvider
import com.verto.feature.dashboard.api.QuickActionProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InventoryModule {
    @Provides @Singleton
    fun provideInventoryRepository(
        inventoryDao: InventoryDao,
        inventoryUnitDao: InventoryUnitDao,
        itemCategoryDao: ItemCategoryDao,
        categoryDao: CategoryDao,
        inventoryStore: InventoryStorePort,
        inventoryStock: InventoryStockPort,
        deletionQueue: InventoryDeletionQueuePort,
        saveItemCoordinator: SaveInventoryItemCoordinator,
        saveUnitItemCoordinator: SaveInventoryUnitItemCoordinator,
        priceCoordinator: InventoryPriceCoordinator,
        deleteCoordinator: DeleteInventoryCoordinator,
        database: AppDatabase,
        sessionReader: SessionReader,
        unifiedOutboxWriter: UnifiedOutboxWriter,
    ): InventoryRepository = InventoryRepository(
        inventoryDao,
        inventoryUnitDao,
        itemCategoryDao,
        categoryDao,
        inventoryStore,
        inventoryStock,
        deletionQueue,
        saveItemCoordinator,
        saveUnitItemCoordinator,
        priceCoordinator,
        deleteCoordinator,
        database,
        sessionReader,
        unifiedOutboxWriter,
    )
    @Provides @Singleton fun provideInventoryStorePort(adapter: RoomInventoryStoreAdapter): InventoryStorePort = adapter
    @Provides @Singleton fun provideInventoryStockPort(adapter: RoomInventoryStockAdapter): InventoryStockPort = adapter
    @Provides @Singleton fun provideInventoryStockWriteAuthorization(adapter: PermissionInventoryStockWriteAuthorization): InventoryStockWriteAuthorization = adapter
    @Provides @Singleton fun provideReceiveShipmentStockPort(adapter: RoomReceiveShipmentStockAdapter): ReceiveShipmentStockPort = adapter
    @Provides @Singleton fun provideApplyShipmentLandedCostPort(adapter: RoomApplyShipmentLandedCostAdapter): ApplyShipmentLandedCostPort = adapter
    @Provides @Singleton fun provideReverseShipmentReceiptsPort(adapter: RoomReverseShipmentReceiptsAdapter): ReverseShipmentReceiptsPort = adapter
    @Provides @Singleton fun provideInventoryDeletionQueuePort(adapter: PreferencesInventoryDeletionQueueAdapter): InventoryDeletionQueuePort = adapter
    @Provides @Singleton fun provideInventoryIdentityPort(adapter: UuidInventoryIdentityAdapter): InventoryIdentityPort = adapter
    @Provides @Singleton
    fun provideInventoryPresentationQuery(adapter: InventoryPresentationAdapter): InventoryPresentationQuery = adapter
    @Provides @Singleton
    fun provideInventoryPresentationCommand(adapter: InventoryPresentationAdapter): InventoryPresentationCommand = adapter

    @Provides @Singleton
    fun providePriceListPresentationQuery(adapter: PriceListPresentationAdapter): PriceListPresentationQuery = adapter

    @Provides @Singleton
    fun providePriceListPresentationCommand(adapter: PriceListPresentationAdapter): PriceListPresentationCommand = adapter

    @Provides @Singleton
    fun provideInventoryPreferencesPort(adapter: InventoryPreferencesAdapter): InventoryPreferencesPort = adapter

    @Provides @Singleton
    fun provideInventoryPermissionPort(adapter: InventoryPermissionAdapter): InventoryPermissionPort = adapter

    @Provides @Singleton
    fun provideInventoryHomeSearchSource(adapter: RoomInventoryHomeSearchSource): InventoryHomeSearchSource = adapter

    @Provides @IntoSet @Singleton
    fun provideInventoryHomeSearchProvider(provider: InventoryHomeSearchProvider): HomeSearchProvider = provider

    @Provides @IntoSet @Singleton
    fun provideInventoryQuickActionProvider(provider: InventoryQuickActionProvider): QuickActionProvider = provider

    @Provides @Singleton
    fun provideInventoryPendingActionSource(adapter: RoomInventoryPendingActionSource): InventoryPendingActionSource = adapter

    @Provides @Singleton
    fun provideInventoryPendingActionClock(clock: SystemInventoryPendingActionClock): InventoryPendingActionClock = clock

    @Provides @IntoSet @Singleton
    fun provideInventoryPendingActionProvider(provider: InventoryPendingActionProvider): PendingActionProvider = provider

    @Provides @Singleton
    fun provideInventoryPriceBatchEventPort(adapter: RoomInventoryPriceBatchEventAdapter): InventoryPriceBatchEventPort = adapter

    @Provides @Singleton
    fun provideInventoryActivityEventSource(
        adapter: RoomInventoryActivityEventSource,
    ): InventoryActivityEventSource = adapter

    @Provides @IntoSet @Singleton
    fun provideInventoryActivityEventProvider(
        provider: InventoryActivityEventProvider,
    ): ActivityEventProvider = provider

    @Provides @IntoSet @Singleton
    fun provideInventorySyncParticipant(runtime: SyncRuntime): SyncParticipant =
        InventorySyncParticipant(runtime)
}
