package com.verto.app.feature.auth.integration

import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.sync.SyncManager
import com.verto.app.feature.auth.application.AuthSessionCoordinator
import com.verto.app.feature.auth.data.AuthErrorTranslator
import com.verto.app.feature.auth.data.AuthOperation
import com.verto.app.feature.auth.domain.repository.SessionAuthorizationStatus
import com.verto.app.feature.auth.domain.repository.SplashSessionGateway
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SplashSessionGatewayAdapter @Inject constructor(
    private val authRepository: AuthRepository,
    private val roleProvider: RoleProvider,
    private val permissionProvider: PermissionProvider,
    private val syncManager: SyncManager,
    private val sessionCoordinator: AuthSessionCoordinator,
) : SplashSessionGateway {
    override suspend fun awaitInitialization(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) {
            VertoSupabase.awaitAuthInitialization()
        }
    }

    override fun hasSession(): Boolean = VertoSupabase.hasSession()

    override suspend fun hasPendingPasswordRecovery(): Boolean =
        authRepository.isPasswordRecoveryPending()

    override suspend fun fetchSessionAuthorizationStatus(): Result<SessionAuthorizationStatus> =
        runCatching {
            val raw = VertoSupabase.client.postgrest
                .rpc("verto_auth_session_status")
                .data
                .trim()
                .trim('"')
            SessionAuthorizationStatus.entries.firstOrNull { it.name == raw }
                ?: throw IllegalStateException("UNKNOWN_SESSION_AUTHORIZATION_STATUS")
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                Result.failure(
                    AuthErrorTranslator.translate(it, AuthOperation.SESSION_STATUS)
                )
            },
        )

    /** Offline admission remains disabled until a bounded cryptographically trusted policy exists. */
    override fun hasTrustedOfflineSession(): Boolean = false

    override suspend fun refreshAccess() {
        roleProvider.refresh()
        permissionProvider.refresh()
        val profile = authRepository.fetchActiveProfile().getOrThrow()
            ?: throw BusinessRuleFailureException(AuthErrorCodes.PROFILE_MISSING)
        sessionCoordinator.restoreAuthentication(profile.organizationId)
    }

    override suspend fun wipeAndSignOut() {
        runCatching { authRepository.clearPasswordRecoveryState() }
        runCatching { roleProvider.clear() }
        runCatching { permissionProvider.clear() }
        runCatching { syncManager.clearLocalData() }
        runCatching { VertoSupabase.signOut() }
    }
}
