package com.verto.app.feature.shipment.bridge

import com.verto.app.data.sync.SyncLogisticsV2
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.feature.shipment.data.sync.LogisticsV2SyncRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

class LogisticsV2SyncAdapter(
    private val sync: SyncLogisticsV2,
) : LogisticsV2SyncRuntime {
    private companion object {
        // Independent composition-root kill switch. v211 must never activate remote V2.
        const val REMOTE_LOGISTICS_V2_ACTIVATION_APPROVED = false
    }
    override fun isRemoteEnabled(organizationId: String): Boolean {
        if (organizationId.isBlank()) return false
        // SQL 008/009 are package-only in v211. Live server/RLS verification is still required.
        return REMOTE_LOGISTICS_V2_ACTIVATION_APPROVED && sync.isRemoteEnabled(organizationId)
    }

    override suspend fun push(organizationId: String) {
        sync.push(organizationId)
    }

    override suspend fun pull(organizationId: String) {
        sync.pull(organizationId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LogisticsV2SyncBridgeModule {
    @Provides
    @Singleton
    fun provideSyncLogisticsV2(runtime: SyncRuntime): SyncLogisticsV2 =
        SyncLogisticsV2(runtime)

    @Provides
    @Singleton
    fun provideLogisticsV2SyncRuntime(sync: SyncLogisticsV2): LogisticsV2SyncRuntime =
        LogisticsV2SyncAdapter(sync)
}
