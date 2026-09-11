package com.verto.app.data.sync.rfm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class RfmCalculationWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RfmWorkerEntryPoint {
        fun getRfmCalculationPort(): RfmCalculationPort
    }

    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(
            applicationContext,
            RfmWorkerEntryPoint::class.java
        ).getRfmCalculationPort().recalculate()
        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "rfm_nightly_calculation"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RfmCalculationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<RfmCalculationWorker>().build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
