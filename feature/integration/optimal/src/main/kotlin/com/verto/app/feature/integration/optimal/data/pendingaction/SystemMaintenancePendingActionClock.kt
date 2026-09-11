package com.verto.app.feature.integration.optimal.data.pendingaction

import com.verto.app.feature.integration.optimal.application.pendingaction.MaintenancePendingActionClock
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class SystemMaintenancePendingActionClock @Inject constructor() : MaintenancePendingActionClock {
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
