package com.verto.app.feature.shipment.di

import com.verto.app.data.sync.SyncParticipant
import com.verto.app.feature.shipment.application.activityevent.ShipmentActivityEventProvider
import com.verto.app.feature.shipment.application.activityevent.ShipmentActivityEventSource
import com.verto.app.feature.shipment.application.pendingaction.ShipmentOperationalPendingActionClock
import com.verto.app.feature.shipment.application.pendingaction.ShipmentOperationalPendingActionProvider
import com.verto.app.feature.shipment.application.pendingaction.ShipmentOperationalPendingActionSource
import com.verto.app.feature.shipment.application.pendingaction.ShipmentReceiptIssuePendingActionProvider
import com.verto.app.feature.shipment.application.pendingaction.ShipmentReceiptIssuePendingActionSource
import com.verto.app.feature.shipment.data.activityevent.RoomShipmentActivityEventSource
import com.verto.app.feature.shipment.data.pendingaction.RoomShipmentOperationalPendingActionSource
import com.verto.app.feature.shipment.data.pendingaction.RoomShipmentReceiptIssuePendingActionSource
import com.verto.app.feature.shipment.data.pendingaction.SystemShipmentOperationalPendingActionClock
import com.verto.app.feature.shipment.data.sync.LogisticsV2SyncParticipant
import com.verto.app.feature.shipment.data.sync.LogisticsV2SyncRuntime
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.PendingActionProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShipmentModule {
    @Provides @Singleton
    fun provideShipmentReceiptIssuePendingActionSource(adapter: RoomShipmentReceiptIssuePendingActionSource): ShipmentReceiptIssuePendingActionSource = adapter

    @Provides @IntoSet @Singleton
    fun provideShipmentReceiptIssuePendingActionProvider(provider: ShipmentReceiptIssuePendingActionProvider): PendingActionProvider = provider

    @Provides @Singleton
    fun provideShipmentOperationalPendingActionSource(adapter: RoomShipmentOperationalPendingActionSource): ShipmentOperationalPendingActionSource = adapter

    @Provides @Singleton
    fun provideShipmentOperationalPendingActionClock(clock: SystemShipmentOperationalPendingActionClock): ShipmentOperationalPendingActionClock = clock

    @Provides @IntoSet @Singleton
    fun provideShipmentOperationalPendingActionProvider(provider: ShipmentOperationalPendingActionProvider): PendingActionProvider = provider

    @Provides @Singleton
    fun provideShipmentActivityEventSource(adapter: RoomShipmentActivityEventSource): ShipmentActivityEventSource = adapter

    @Provides @IntoSet @Singleton
    fun provideShipmentActivityEventProvider(provider: ShipmentActivityEventProvider): ActivityEventProvider = provider

    @Provides @IntoSet @Singleton
    fun provideLogisticsV2SyncParticipant(runtime: LogisticsV2SyncRuntime): SyncParticipant = LogisticsV2SyncParticipant(runtime)
}
