package com.verto.app.di.optimal

import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.push.SyncV2SpecializedPushBridge
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OptimalSyncBridgeModule {
    @Binds
    @Singleton
    abstract fun bindOptimalSyncScheduler(
        implementation: WorkManagerOptimalSyncScheduler,
    ): OptimalSyncScheduler

    @Binds
    @IntoSet
    abstract fun bindOptimalOutboxSyncParticipant(
        implementation: OptimalOutboxSyncParticipantAdapter,
    ): SyncParticipant

    @Binds
    @IntoSet
    abstract fun bindOptimalV2PushBridge(
        implementation: OptimalOutboxSyncParticipantAdapter,
    ): SyncV2SpecializedPushBridge
}
