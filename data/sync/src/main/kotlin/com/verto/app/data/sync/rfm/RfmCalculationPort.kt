package com.verto.app.data.sync.rfm

/** App-composed RFM calculation invoked by the sync-owned WorkManager task. */
fun interface RfmCalculationPort {
    suspend fun recalculate()
}
