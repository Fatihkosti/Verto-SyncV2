package com.verto.app.data.workers

import android.content.Context
import com.verto.app.data.sync.rfm.RfmCalculationWorker as SyncRfmCalculationWorker

/**
 * App compatibility facade. data:sync owns scheduling; AppRfmCalculationAdapter
 * supplies business reads through PartyDirectoryGateway without a concrete repository dependency.
 */
object RfmCalculationWorker {
    const val WORK_NAME: String = SyncRfmCalculationWorker.WORK_NAME

    fun schedule(context: Context) = SyncRfmCalculationWorker.schedule(context)

    fun runOnce(context: Context) = SyncRfmCalculationWorker.runOnce(context)

    fun cancel(context: Context) = SyncRfmCalculationWorker.cancel(context)
}
