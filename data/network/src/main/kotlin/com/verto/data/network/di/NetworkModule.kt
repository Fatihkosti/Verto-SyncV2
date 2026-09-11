package com.verto.data.network.di

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.remote.OrganizationRealtimeSource
import com.verto.app.data.remote.SupabaseOrganizationRealtimeSource
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.data.sync.SupabaseUnifiedSyncPullRemote
import com.verto.app.data.sync.SupabaseUnifiedSyncBootstrapRemote
import com.verto.app.data.sync.SupabaseUnifiedSyncPushRemote
import com.verto.app.data.sync.UnifiedSyncPullRemote
import com.verto.app.data.sync.UnifiedSyncBootstrapRemote
import com.verto.app.data.sync.UnifiedSyncPushRemote
import com.verto.app.utils.PreferencesManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun bindOrganizationRealtimeSource(
        implementation: SupabaseOrganizationRealtimeSource
    ): OrganizationRealtimeSource

    @Binds
    abstract fun bindUnifiedSyncPullRemote(
        implementation: SupabaseUnifiedSyncPullRemote
    ): UnifiedSyncPullRemote

    @Binds
    abstract fun bindUnifiedSyncBootstrapRemote(
        implementation: SupabaseUnifiedSyncBootstrapRemote
    ): UnifiedSyncBootstrapRemote

    /** Session 309 inactive data-plane binding; no Worker/SyncManager wiring is introduced. */
    @Binds
    abstract fun bindUnifiedSyncPushRemote(
        implementation: SupabaseUnifiedSyncPushRemote
    ): UnifiedSyncPushRemote

    companion object {
        @Provides
        @Singleton
        fun provideSyncRuntime(
            db: AppDatabase,
            preferencesManager: PreferencesManager
        ): SyncRuntime = SyncRuntime(db, preferencesManager)
    }
}
