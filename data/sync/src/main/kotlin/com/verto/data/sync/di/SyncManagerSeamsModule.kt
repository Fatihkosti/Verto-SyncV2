package com.verto.data.sync.di

import com.verto.app.data.sync.DefaultSyncManagerRuntimePort
import com.verto.app.data.sync.DefaultSyncRolloutAuthority
import com.verto.app.data.sync.DefaultSyncV2EnginePort
import com.verto.app.data.sync.RoomSyncOrchestrationStore
import com.verto.app.data.sync.SyncClock
import com.verto.app.data.sync.SyncManagerRuntimePort
import com.verto.app.data.sync.SyncOrchestrationStore
import com.verto.app.data.sync.SyncRolloutAuthority
import com.verto.app.data.sync.SyncV2EnginePort
import com.verto.app.data.sync.SyncWakeScheduler
import com.verto.app.data.sync.SystemSyncClock
import com.verto.app.data.sync.WorkManagerSyncWakeScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncManagerSeamsModule {
    @Binds abstract fun bindRuntime(implementation: DefaultSyncManagerRuntimePort): SyncManagerRuntimePort
    @Binds abstract fun bindScheduler(implementation: WorkManagerSyncWakeScheduler): SyncWakeScheduler
    @Binds abstract fun bindStore(implementation: RoomSyncOrchestrationStore): SyncOrchestrationStore
    @Binds abstract fun bindClock(implementation: SystemSyncClock): SyncClock
    @Binds abstract fun bindRollout(implementation: DefaultSyncRolloutAuthority): SyncRolloutAuthority
    @Binds abstract fun bindV2Engine(implementation: DefaultSyncV2EnginePort): SyncV2EnginePort
}
