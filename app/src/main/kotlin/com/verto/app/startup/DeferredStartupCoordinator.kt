package com.verto.app.startup

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.verto.app.core.concurrency.AppCoroutineScope
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.sync.RealtimeManager
import com.verto.app.data.workers.RfmCalculationWorker
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncCoordinator
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScope
import com.verto.app.notifications.FcmTokenStore
import com.verto.app.utils.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * ينفذ الأعمال غير اللازمة للرسم الأول بعد ظهور أول إطار.
 *
 * الملكية واضحة: الأعمال طويلة العمر تعمل على AppCoroutineScope المملوك لعمر العملية.
 */
@Singleton
class DeferredStartupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val realtimeManager: RealtimeManager,
    private val roleProvider: RoleProvider,
    private val scheduleOptimalSync: OptimalSyncCoordinator,
) {
    private val appScope: AppCoroutineScope = context.applicationContext as AppCoroutineScope
    private val gate = StartupOnceGate()
    private val tokenSyncInFlight = AtomicBoolean(false)

    /** يعود true فقط للاستدعاء الأول في عمر العملية. */
    fun start(): Boolean {
        if (!gate.tryStart()) return false
        appScope.launch { runDeferredStartup() }
        return true
    }

    private suspend fun runDeferredStartup() {
        // الجدولة idempotent عبر unique work؛ لا ينبغي أن تمنع مهمة فاشلة بقية المهام.
        runCatching { RfmCalculationWorker.schedule(context) }.reportFailure("RFM scheduling")
        runCatching { preferencesManager.migrateFromSharedPreferences() }.reportFailure("preferences migration")

        if (!authRepository.isLoggedIn()) return
        val profile = authRepository.getMyProfile() ?: return
        val scope = OptimalSyncScope(
            organizationId = profile.organizationId,
            userId = profile.id,
        )
        preferencesManager.setLastOrgId(scope.organizationId)
        runCatching { scheduleOptimalSync.periodic(scope) }.reportFailure("Optimal periodic sync scheduling")
        runCatching { scheduleOptimalSync.immediate(scope) }.reportFailure("Optimal immediate sync scheduling")
        runCatching { roleProvider.refreshAsync() }.reportFailure("role refresh scheduling")
        runCatching { startRealtimeForCurrentOrganization(scope.organizationId) }.reportFailure("realtime startup")
        runCatching { syncFcmToken() }.reportFailure("FCM token sync scheduling")
    }

    private suspend fun startRealtimeForCurrentOrganization(organizationId: String) {
        if (organizationId.isBlank()) return
        realtimeManager.start(organizationId)
    }

    private fun Result<*>.reportFailure(operation: String) {
        exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
            Log.w(TAG, "Deferred startup task failed: $operation", failure)
        }
    }

    private companion object { const val TAG = "DeferredStartup" }

    private fun syncFcmToken() {
        if (!tokenSyncInFlight.compareAndSet(false, true)) return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                tokenSyncInFlight.set(false)
                return@addOnCompleteListener
            }
            val token = task.result
            if (token.isNullOrBlank()) {
                tokenSyncInFlight.set(false)
                return@addOnCompleteListener
            }
            val pending = FcmTokenStore.pending(context) ?: token
            val lastUploaded = FcmTokenStore.lastUploaded(context)
            val current = if (pending != lastUploaded) pending else token
            if (current == lastUploaded) {
                tokenSyncInFlight.set(false)
                return@addOnCompleteListener
            }
            appScope.launch {
                authRepository.syncPushToken(current)
                    .onSuccess {
                        FcmTokenStore.setLastUploaded(context, current)
                        FcmTokenStore.setPending(context, null)
                    }
                tokenSyncInFlight.set(false)
            }
        }
    }
}
