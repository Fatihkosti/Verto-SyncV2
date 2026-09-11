package com.verto.app.data.sync

import android.util.Log
import com.verto.app.core.concurrency.AppCoroutineScope
import com.verto.app.data.remote.OrganizationRealtimeSource
import com.verto.app.data.sync.rollout.SyncRolloutPolicy
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tenant/session/lifecycle-safe Realtime accelerator for the durable v311 sync drain.
 *
 * Realtime carries hints, never truth. It cannot mutate Room, write/reconstruct a cursor, or invoke
 * legacy fullSync. Missing/duplicate/out-of-order target metadata simply coalesces into a normal
 * durable [SyncRequestReason.REALTIME] request.
 */
@Singleton
class RealtimeManager @Inject constructor(
    private val syncManager: SyncManager,
    private val source: OrganizationRealtimeSource,
    private val appScope: AppCoroutineScope,
) {
    private val lifecycleLock = Any()
    private val lifecycleGeneration = AtomicLong(0L)
    private val coalescer = RealtimeHintCoalescer()

    private var listenerJob: Job? = null
    private var flushJob: Job? = null
    private var activeSubscriptionId: String? = null
    private var activeScope: SyncWorkScope? = null

    val withdrawalRequestsChanged: Flow<Unit> = source.withdrawalRequestsChanged

    fun start(orgId: String) {
        val rollout = SyncRolloutPolicy.snapshot(orgId)
        if (!rollout.isValid || !rollout.organizationEligible || !rollout.masterV2Enabled ||
            !rollout.v2PullEnabled || !rollout.realtimeEnabled || rollout.wave.wireValue < 5
        ) {
            Log.d(TAG, "V2 Realtime accelerator disabled by rollout policy")
            return
        }
        require(orgId.isNotBlank()) { "organization id is required for realtime" }

        val generation = lifecycleGeneration.incrementAndGet()
        val oldSubscription = synchronized(lifecycleLock) {
            listenerJob?.cancel()
            flushJob?.cancel()
            listenerJob = null
            flushJob = null
            activeScope = null
            coalescer.reset()
            activeSubscriptionId.also { activeSubscriptionId = null }
        }
        oldSubscription?.let { subscription ->
            appScope.launch { source.stop(subscription) }
        }

        listenerJob = appScope.launch {
            val scope = syncManager.currentWorkScope()
            if (scope == null || scope.organizationId != orgId || !isCurrentGeneration(generation)) {
                return@launch
            }
            val subscriptionId = "rt_${generation}_${scope.stableKey}"
            synchronized(lifecycleLock) {
                if (!isCurrentGeneration(generation)) return@launch
                activeScope = scope
                activeSubscriptionId = subscriptionId
            }

            try {
                source.observeOrganization(orgId, subscriptionId).collect { wireHint ->
                    if (!acceptsHint(generation, subscriptionId, scope, wireHint)) return@collect
                    val normalized = normalizeHint(wireHint)
                    if (coalescer.offer(normalized)) {
                        scheduleFlush(generation, subscriptionId, scope)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.w(TAG, "Realtime listener stopped; durable periodic/manual sync remains available", failure)
            } finally {
                source.stop(subscriptionId)
            }
        }
    }

    fun stop() {
        lifecycleGeneration.incrementAndGet()
        val oldSubscription = synchronized(lifecycleLock) {
            listenerJob?.cancel()
            flushJob?.cancel()
            listenerJob = null
            flushJob = null
            activeScope = null
            coalescer.reset()
            activeSubscriptionId.also { activeSubscriptionId = null }
        }
        oldSubscription?.let { subscription ->
            appScope.launch { source.stop(subscription) }
        }
    }

    private suspend fun acceptsHint(
        generation: Long,
        subscriptionId: String,
        scope: SyncWorkScope,
        hint: SyncRealtimeHint,
    ): Boolean {
        if (!isCurrentLifecycle(generation, subscriptionId, scope)) return false
        if (hint.organizationId != scope.organizationId) return false
        val trusted = syncManager.currentWorkScope() ?: return false
        return trusted == scope && isCurrentLifecycle(generation, subscriptionId, scope)
    }

    private fun normalizeHint(hint: SyncRealtimeHint): SyncRealtimeHint {
        val revision = hint.serverRevision?.takeIf { it > 0L }
        val type = hint.aggregateType?.takeIf { it.isNotBlank() }
        val id = hint.aggregateId?.takeIf { it.isNotBlank() }
        val knownTarget = if (type != null && id != null) {
            runCatching { UnifiedSyncAggregateRegistry.requireById(type) }.isSuccess
        } else {
            false
        }
        return if (knownTarget) {
            hint.copy(aggregateType = type, aggregateId = id, serverRevision = revision)
        } else {
            // Partial/unknown target is intentionally degraded to organization-wide revision pull.
            hint.copy(aggregateType = null, aggregateId = null, serverRevision = revision)
        }
    }

    private fun scheduleFlush(
        generation: Long,
        subscriptionId: String,
        scope: SyncWorkScope,
    ) {
        synchronized(lifecycleLock) {
            if (!isCurrentLifecycle(generation, subscriptionId, scope)) return
            if (flushJob?.isActive == true) return
            flushJob = appScope.launch {
                delay(COALESCE_WINDOW_MILLIS)
                while (isCurrentLifecycle(generation, subscriptionId, scope)) {
                    val batch = coalescer.drain() ?: break
                    val trusted = syncManager.currentWorkScope()
                    if (trusted != scope || !isCurrentLifecycle(generation, subscriptionId, scope)) break

                    // maxServerRevision/targets are advisory diagnostics only. requestSync persists
                    // the v311 generation before waking WorkManager; PullEngine alone owns cursor/apply.
                    Log.d(
                        TAG,
                        "Coalesced Realtime hint revision=${batch.maxServerRevision} targets=${batch.targets.size} overflow=${batch.overflowed}",
                    )
                    syncManager.requestSync(scope, SyncRequestReason.REALTIME)

                    if (!coalescer.completeFlushAndShouldContinue()) break
                    delay(COALESCE_WINDOW_MILLIS)
                }
            }
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        lifecycleGeneration.get() == generation

    private fun isCurrentLifecycle(
        generation: Long,
        subscriptionId: String,
        scope: SyncWorkScope,
    ): Boolean = synchronized(lifecycleLock) {
        lifecycleGeneration.get() == generation &&
            activeSubscriptionId == subscriptionId &&
            activeScope == scope
    }

    private companion object {
        const val TAG = "RealtimeManager"
        const val COALESCE_WINDOW_MILLIS = 250L
    }
}
