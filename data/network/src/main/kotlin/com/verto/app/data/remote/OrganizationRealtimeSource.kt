package com.verto.app.data.remote

import com.verto.app.data.sync.SyncRealtimeHint
import kotlinx.coroutines.flow.Flow

/**
 * Network-owned organization Realtime boundary.
 *
 * Realtime is hint-only: implementations may never mutate Room or advance a sync cursor. A
 * subscription id is an ownership handle; closing one id must not affect channels owned by a
 * newer subscription.
 */
interface OrganizationRealtimeSource {
    val withdrawalRequestsChanged: Flow<Unit>

    fun observeOrganization(
        orgId: String,
        subscriptionId: String,
    ): Flow<SyncRealtimeHint>

    suspend fun stop(subscriptionId: String)
}
