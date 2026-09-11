package com.verto.app.feature.auth.integration

import android.content.Context
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.PushTokenRepository
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.sync.RealtimeManager
import com.verto.app.data.sync.SyncManager
import com.verto.app.data.sync.SyncRequestReason
import com.verto.app.feature.auth.application.AuthSessionCoordinator
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncCoordinator
import com.verto.app.notifications.FcmTokenUploader
import com.verto.app.utils.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAuthSessionCoordinator @Inject constructor(
    private val syncManager: SyncManager,
    private val realtimeManager: RealtimeManager,
    private val pushTokens: PushTokenRepository,
    private val roleProvider: RoleProvider,
    private val permissionProvider: PermissionProvider,
    private val scheduleOptimalSync: OptimalSyncCoordinator,
    @ApplicationContext private val appContext: Context,
) : AuthSessionCoordinator {

    override suspend fun completeAuthentication(organizationId: String) {
        bestEffort("cancel_previous_sync") { scheduleOptimalSync.cancelAll() }
        bestEffort("stop_realtime") { realtimeManager.stop() }

        // Critical boundary: the authenticated tenant session must be prepared before HOME opens.
        syncManager.prepareSessionForOrg(organizationId)

        // Secondary services must never turn a successful login into a login failure.
        bestEffort("schedule_periodic_sync") { scheduleOptimalSync.periodicForCurrentSession() }
        bestEffort("request_startup_sync") { syncManager.request(SyncRequestReason.STARTUP).getOrThrow(); Unit }
        bestEffort("upload_fcm_token") { FcmTokenUploader.trigger(appContext, pushTokens) }
        bestEffort("refresh_role") { roleProvider.refreshAsync() }
        bestEffort("refresh_permissions") { permissionProvider.refreshAsync() }
    }

    override suspend fun restoreAuthentication(organizationId: String) {
        bestEffort("restore_cancel_previous_sync") { scheduleOptimalSync.cancelAll() }
        bestEffort("restore_stop_realtime") { realtimeManager.stop() }

        syncManager.prepareSessionForOrg(organizationId)

        bestEffort("restore_schedule_periodic_sync") { scheduleOptimalSync.periodicForCurrentSession() }
        bestEffort("restore_request_startup_sync") { syncManager.request(SyncRequestReason.STARTUP).getOrThrow(); Unit }
    }

    private suspend fun bestEffort(stage: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            CrashReporter.log("auth_session_secondary_failure stage=$stage type=${failure.javaClass.simpleName}")
            CrashReporter.recordException(failure)
        }
    }
}
