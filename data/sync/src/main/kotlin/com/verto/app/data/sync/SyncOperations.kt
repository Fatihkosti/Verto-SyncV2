package com.verto.app.data.sync

import com.verto.app.data.remote.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

interface SyncOperations {
    val lastReport: StateFlow<PersistedSyncReport?>
    val isSyncInProgress: StateFlow<Boolean>
    suspend fun restoreLastReport(): PersistedSyncReport?
    suspend fun isProfileReady(): Boolean
    suspend fun requestSync(): Result<SyncRequestReceipt>
    suspend fun startRealtimeForCurrentProfile()
    fun stopRealtime()
}

@Singleton
class DefaultSyncOperations @Inject constructor(
    private val syncManager: SyncManager,
    private val realtimeManager: RealtimeManager,
    private val authRepository: AuthRepository
) : SyncOperations {
    override val lastReport: StateFlow<PersistedSyncReport?> = syncManager.persistedReport
    override val isSyncInProgress: StateFlow<Boolean> = syncManager.syncInProgress
    override suspend fun restoreLastReport(): PersistedSyncReport? =
        syncManager.restoreLastReportForCurrentSession()
    override suspend fun isProfileReady(): Boolean = syncManager.isProfileReady()
    override suspend fun requestSync(): Result<SyncRequestReceipt> = syncManager.request(SyncRequestReason.MANUAL)
    override suspend fun startRealtimeForCurrentProfile() {
        val orgId = authRepository.getMyProfile()?.organizationId ?: return
        realtimeManager.start(orgId)
    }
    override fun stopRealtime() = realtimeManager.stop()
}
