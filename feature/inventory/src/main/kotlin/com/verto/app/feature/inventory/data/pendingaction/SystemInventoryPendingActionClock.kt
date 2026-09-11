package com.verto.app.feature.inventory.data.pendingaction

import com.verto.app.feature.inventory.application.pendingaction.InventoryPendingActionClock
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class SystemInventoryPendingActionClock @Inject constructor() : InventoryPendingActionClock {
    override fun observeNowEpochMillis(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TICK_MS: Long = 6L * 60L * 60L * 1_000L
    }
}
