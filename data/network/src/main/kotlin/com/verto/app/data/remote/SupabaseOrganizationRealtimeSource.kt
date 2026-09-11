package com.verto.app.data.remote

import android.util.Log
import com.verto.app.data.sync.SyncRealtimeHint
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Singleton
class SupabaseOrganizationRealtimeSource @Inject constructor() : OrganizationRealtimeSource {
    private val supabase by lazy { VertoSupabase.client }
    private val _withdrawalRequestsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val withdrawalRequestsChanged: Flow<Unit> = _withdrawalRequestsChanged.asSharedFlow()

    /**
     * v312 listens only to the minimal append-only hint projection created by the v312 migration.
     * No business JSON, financial amount, mutation payload, token, or authoritative snapshot is
     * present on this surface. Realtime remains optional; periodic/manual unified pull converges.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOrganization(
        orgId: String,
        subscriptionId: String,
    ): Flow<SyncRealtimeHint> = channelFlow {
        require(orgId.isNotBlank()) { "organization id is required for realtime" }
        require(subscriptionId.isNotBlank()) { "subscription id is required for realtime" }

        runCatching { supabase.realtime.connect() }
            .onFailure { Log.w(TAG, "Realtime connect failed; durable sync remains authoritative", it) }

        val channelName = "verto_${subscriptionId}_sync_hints"
        val channel = supabase.channel(channelName)
        registerOwnedChannel(subscriptionId, channelName) {
            runCatching { supabase.realtime.removeChannel(channel) }
                .onFailure { Log.w(TAG, "Failed to remove owned channel=$channelName", it) }
        }

        try {
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = HINT_TABLE
                filter("organization_id", FilterOperator.EQ, orgId)
            }.onEach { action ->
                val record = action.record
                val revision = record["revision"]?.jsonPrimitive?.longOrNull
                val aggregateType = record["aggregate_type"]?.jsonPrimitive?.contentOrNull
                val aggregateId = record["aggregate_id"]?.jsonPrimitive?.contentOrNull
                trySend(
                    SyncRealtimeHint(
                        organizationId = orgId,
                        aggregateType = aggregateType,
                        aggregateId = aggregateId,
                        serverRevision = revision,
                    )
                )
            }.launchIn(this)

            channel.subscribe()
            Log.d(TAG, "Listening minimal sync hint surface; subscription=$subscriptionId")

            // Subscribe/re-subscribe does not imply replay. This forces one bounded durable catch-up.
            trySend(SyncRealtimeHint(organizationId = orgId))
            awaitCancellation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w(TAG, "Optional Realtime hint surface unavailable; durable sync remains available", failure)
        } finally {
            removeOwnedChannel(subscriptionId, channelName)
        }
    }

    override suspend fun stop(subscriptionId: String) {
        if (subscriptionId.isBlank()) return
        val removers = synchronized(ownedChannelRemovers) {
            ownedChannelRemovers.remove(subscriptionId)?.values?.toList().orEmpty()
        }
        removers.forEach { remover -> remover() }
    }

    private val ownedChannelRemovers =
        ConcurrentHashMap<String, MutableMap<String, suspend () -> Unit>>()

    private fun registerOwnedChannel(
        subscriptionId: String,
        name: String,
        remover: suspend () -> Unit,
    ) {
        synchronized(ownedChannelRemovers) {
            val owned = ownedChannelRemovers.getOrPut(subscriptionId) { linkedMapOf() }
            owned[name] = remover
        }
    }

    private suspend fun removeOwnedChannel(subscriptionId: String, name: String) {
        val remover = synchronized(ownedChannelRemovers) {
            val owned = ownedChannelRemovers[subscriptionId] ?: return@synchronized null
            val removed = owned.remove(name)
            if (owned.isEmpty()) ownedChannelRemovers.remove(subscriptionId)
            removed
        }
        remover?.invoke()
    }

    private companion object {
        const val TAG = "OrganizationRealtime"
        const val HINT_TABLE = "verto_sync_realtime_hints"
    }
}
