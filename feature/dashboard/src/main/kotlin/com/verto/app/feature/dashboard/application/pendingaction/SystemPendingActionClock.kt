package com.verto.app.feature.dashboard.application.pendingaction

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Minute-resolution Home clock used for snooze expiry without network or provider mutation. */
@Singleton
class SystemPendingActionClock @Inject constructor() {
    fun nowEpochMillis(): Long = System.currentTimeMillis()

    fun observeNowEpochMillis(): Flow<Long> = flow {
        while (true) {
            emit(nowEpochMillis())
            delay(TICK_MS)
        }
    }

    companion object {
        const val TICK_MS: Long = 60_000L
    }
}
