package com.verto.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.verto.app.data.remote.VertoSupabase
import kotlinx.coroutines.CancellationException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class SyncWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun getSyncManager(): SyncManager
    }

    override suspend fun doWork(): Result {
        try {
            VertoSupabase.awaitAuthInitialization()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            android.util.Log.e("SyncWorker", "Auth initialization failed during sync", failure)
            val decision = SyncRetryPolicy.decide(failure, runAttemptCount)
            return if (decision.retry) Result.retry() else Result.failure()
        }
        if (!VertoSupabase.hasSession()) {
            android.util.Log.i("SyncWorker", "Skipping sync: auth session is not restored")
            return Result.success()
        }

        val scope = inputScope() ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java,
        )
        val manager = entryPoint.getSyncManager()
        return try {
            // M07: WorkManager is a V2 wake source only. No rollout/legacy branch is reachable.
            if (inputReason() == SyncRequestReason.PERIODIC) manager.ensurePeriodicIntent(scope)
            when (manager.drainOrchestration(scope)) {
                SyncDrainResult.IDLE,
                SyncDrainResult.WAITING_INBOX,
                SyncDrainResult.NEEDS_REVIEW,
                SyncDrainResult.CONTINUATION_SCHEDULED,
                SyncDrainResult.AUTH_BLOCKED,
                SyncDrainResult.STALE_SCOPE -> Result.success()
                SyncDrainResult.RECOVERY_REQUIRED -> {
                    enqueueContinuation(applicationContext, scope)
                    Result.success()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            android.util.Log.e("SyncWorker", "Sync execution failed", failure)
            classifyFailure(failure)
        }
    }

    private fun classifyFailure(failure: Throwable): Result {
        if (failure is StaleSyncWorkScopeException) return Result.success()
        val decision = SyncRetryPolicy.decide(failure, runAttemptCount)
        return when {
            decision.retry -> Result.retry()
            decision.kind in setOf(
                SyncFailureKind.AUTHENTICATION,
                SyncFailureKind.STALE_SCOPE,
                SyncFailureKind.RECOVERY_REQUIRED,
                SyncFailureKind.CONFLICT_DOMAIN,
            ) -> Result.success()
            else -> Result.failure()
        }
    }

    private fun inputScope(): SyncWorkScope? {
        val organizationId = inputData.getString(KEY_ORGANIZATION_ID).orEmpty().trim()
        val userId = inputData.getString(KEY_USER_ID).orEmpty().trim()
        val sessionEpoch = inputData.getLong(KEY_SESSION_EPOCH, 0L)
        if (organizationId.isEmpty() || userId.isEmpty() || sessionEpoch <= 0L) return null
        return SyncWorkScope(organizationId = organizationId, userId = userId, sessionEpoch = sessionEpoch)
    }

    private fun inputReason(): SyncRequestReason = runCatching {
        SyncRequestReason.valueOf(inputData.getString(KEY_REASON).orEmpty())
    }.getOrDefault(SyncRequestReason.MANUAL)

    companion object {
        private const val TAG_ALL_SYNC = "verto_sync_all"
        private const val KEY_ORGANIZATION_ID = "organization_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SESSION_EPOCH = "session_epoch"
        private const val KEY_REASON = "sync_request_reason"
        private const val PERIODIC_PREFIX = "auto_sync"
        private const val IMMEDIATE_PREFIX = "immediate_sync"
        private const val LEGACY_PERIODIC_WORK_NAME = "auto_sync"
        private const val MANUAL_WORK_NAME = "manual_sync"

        private fun constraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        internal fun periodicWorkName(scope: SyncWorkScope): String =
            "$PERIODIC_PREFIX:${scope.stableKey}"

        internal fun immediateWorkName(scope: SyncWorkScope): String =
            "$IMMEDIATE_PREFIX:${scope.stableKey}"

        internal fun delayedContinuationWorkName(scope: SyncWorkScope): String =
            "$IMMEDIATE_PREFIX:retry:${scope.stableKey}"

        private fun input(scope: SyncWorkScope, reason: SyncRequestReason) = workDataOf(
            KEY_ORGANIZATION_ID to scope.organizationId,
            KEY_USER_ID to scope.userId,
            KEY_SESSION_EPOCH to scope.sessionEpoch,
            KEY_REASON to reason.name,
        )

        private fun cancelLegacyUnscopedWork(manager: WorkManager) {
            manager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
            manager.cancelUniqueWork(MANUAL_WORK_NAME)
        }

        fun schedule(context: Context, scope: SyncWorkScope) {
            val manager = WorkManager.getInstance(context)
            cancelLegacyUnscopedWork(manager)
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setInputData(input(scope, SyncRequestReason.PERIODIC))
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_ALL_SYNC)
                .addTag("$TAG_ALL_SYNC:${scope.stableKey}")
                .build()

            manager.enqueueUniquePeriodicWork(
                periodicWorkName(scope),
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Wake only. Callers must commit durable generation through SyncManager.requestSync first. */
        fun wakeNow(context: Context, scope: SyncWorkScope, reason: SyncRequestReason = SyncRequestReason.MANUAL) {
            enqueueImmediate(context, scope, reason)
        }

        fun enqueueContinuation(context: Context, scope: SyncWorkScope, delayMillis: Long = 0L) {
            val delay = delayMillis.coerceAtLeast(0L)
            if (delay == 0L) {
                enqueueImmediate(context, scope, SyncRequestReason.CONTINUATION)
                return
            }
            val manager = WorkManager.getInstance(context)
            cancelLegacyUnscopedWork(manager)
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(input(scope, SyncRequestReason.CONTINUATION))
                .setConstraints(constraints())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_ALL_SYNC)
                .addTag("$TAG_ALL_SYNC:${scope.stableKey}")
                .build()
            // Separate chain: a future retry must never block a newer manual/immediate wake.
            manager.enqueueUniqueWork(
                delayedContinuationWorkName(scope),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** Inbox wake never REPLACEs/cancels an in-flight worker during an atomic group. */
        fun enqueueInboxContinuation(context: Context, scope: SyncWorkScope, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(input(scope, SyncRequestReason.CONTINUATION))
                .setConstraints(constraints())
                .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_ALL_SYNC)
                .addTag("$TAG_ALL_SYNC:${scope.stableKey}")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$TAG_ALL_SYNC:inbox:${scope.stableKey}", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        private fun enqueueImmediate(
            context: Context,
            scope: SyncWorkScope,
            reason: SyncRequestReason,
            initialDelayMillis: Long = 0L,
        ) {
            val manager = WorkManager.getInstance(context)
            cancelLegacyUnscopedWork(manager)
            val requestBuilder = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(input(scope, reason))
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_ALL_SYNC)
                .addTag("$TAG_ALL_SYNC:${scope.stableKey}")
            if (initialDelayMillis > 0L) {
                requestBuilder.setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            }
            val request = requestBuilder.build()

            manager.enqueueUniqueWork(
                immediateWorkName(scope),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun cancel(context: Context, scope: SyncWorkScope) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(periodicWorkName(scope))
            manager.cancelUniqueWork(immediateWorkName(scope))
            manager.cancelUniqueWork(delayedContinuationWorkName(scope))
            manager.cancelUniqueWork("$TAG_ALL_SYNC:inbox:${scope.stableKey}")
        }

        fun cancelAll(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.cancelAllWorkByTag(TAG_ALL_SYNC)
            cancelLegacyUnscopedWork(manager)
        }
    }
}
