package com.verto.app.feature.shipment.data.pendingaction

import com.verto.app.feature.shipment.application.pendingaction.ShipmentOperationalPendingActionClock
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class SystemShipmentOperationalPendingActionClock @Inject constructor() : ShipmentOperationalPendingActionClock {
    override fun observeNowEpochMillis(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TICK_MS: Long = 60_000L
    }
}
