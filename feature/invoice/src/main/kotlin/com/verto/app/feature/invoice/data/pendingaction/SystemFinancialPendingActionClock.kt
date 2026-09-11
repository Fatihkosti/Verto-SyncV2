package com.verto.app.feature.invoice.data.pendingaction

import com.verto.app.feature.invoice.application.pendingaction.FinancialPendingActionClock
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class SystemFinancialPendingActionClock @Inject constructor() : FinancialPendingActionClock {
    override fun observeNowEpochMillis(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TICK_MS: Long = 15L * 60L * 1_000L
    }
}
