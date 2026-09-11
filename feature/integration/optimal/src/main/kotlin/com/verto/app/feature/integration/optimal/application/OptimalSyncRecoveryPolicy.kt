package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.error.ErrorClassifier
import javax.inject.Inject
import kotlin.math.min

interface OptimalSyncRecoveryPolicy {
    val leaseDurationMillis: Long

    fun nextAttemptAt(
        attemptCount: Int,
        failedAt: Long,
    ): Long

    fun sanitizeFailure(throwable: Throwable): String
}

class DefaultOptimalSyncRecoveryPolicy @Inject constructor() : OptimalSyncRecoveryPolicy {
    override val leaseDurationMillis: Long = 2 * 60 * 1_000L

    override fun nextAttemptAt(attemptCount: Int, failedAt: Long): Long {
        val exponent = (attemptCount - 1).coerceIn(0, 8)
        val delay = min(MAX_RETRY_DELAY_MILLIS, BASE_RETRY_DELAY_MILLIS shl exponent)
        return failedAt + delay
    }

    override fun sanitizeFailure(throwable: Throwable): String =
        ErrorClassifier.classify(throwable).diagnosticCode

    private companion object {
        const val BASE_RETRY_DELAY_MILLIS = 30_000L
        const val MAX_RETRY_DELAY_MILLIS = 6 * 60 * 60 * 1_000L
    }
}
