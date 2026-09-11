package com.verto.app.data.sync.pull

import androidx.room.InvalidationTracker
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.SyncWakeScheduler
import com.verto.app.data.sync.SyncWorkScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** The database generation is authority; Room invalidation is only a post-commit scheduling hint. */
@Singleton
class DurableInboxWakeObserver @Inject constructor(
    private val database: AppDatabase,
    private val scheduler: SyncWakeScheduler,
) {
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signals = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var bound: SyncWorkScope? = null
    @Volatile private var draining = false
    @Volatile private var lastScheduled: String? = null
    private val observer = object : InvalidationTracker.Observer("sync_inbox_apply_request") {
        override fun onInvalidated(tables: Set<String>) { signals.trySend(Unit) }
    }

    init {
        database.invalidationTracker.addObserver(observer)
        worker.launch {
            for (signal in signals) {
                // Coalesce a transaction burst. No request is consumed by this observer.
                delay(25L)
                try { schedulePersistedRequest() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    // No deletion/ACK on failure. Rebind, periodic sync or the next invalidation
                    // reads this durable obligation again (including after process restart).
                }
            }
        }
    }

    fun bind(scope: SyncWorkScope) {
        bound = scope
        draining = true
        lastScheduled = null
    }

    fun finishDrain(scope: SyncWorkScope) {
        if (bound != scope) return
        draining = false
        signals.trySend(Unit)
    }

    fun unbind() { bound = null; draining = false; lastScheduled = null }

    private suspend fun schedulePersistedRequest() {
        val scope = bound ?: return
        if (draining) return
        val dao = database.unifiedSyncDao()
        val pending = dao.pendingInboxWakes(scope.organizationId).filter { request ->
            val cursor = dao.getCursor(request.scopeId)
            request.requestedGeneration > request.drainedGeneration &&
                cursor?.state == "ACTIVE" && cursor.organizationId == scope.organizationId &&
                cursor.syncPrincipalId == scope.userId
        }.minByOrNull { checkNotNull(it.nextWakeAt) } ?: return
        if (draining || bound != scope) return
        val stamp = "${scope.stableKey}:${pending.scopeId}:${pending.requestedGeneration}:${pending.nextWakeAt}"
        if (lastScheduled == stamp) return
        val delayMillis = (checkNotNull(pending.nextWakeAt) - System.currentTimeMillis()).coerceAtLeast(0L)
        scheduler.enqueueInboxContinuation(scope, delayMillis)
        lastScheduled = stamp
    }
}
