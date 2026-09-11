package com.verto.app.feature.auth.presentation.splash

import androidx.lifecycle.ViewModel
import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.feature.auth.domain.repository.SessionAuthorizationStatus
import com.verto.app.feature.auth.domain.repository.SplashSessionGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionGateway: SplashSessionGateway,
) : ViewModel() {
    enum class Decision { HOME, LOGIN }

    suspend fun decide(): Decision = try {
        sessionGateway.awaitInitialization()
        if (!sessionGateway.hasSession()) {
            Decision.LOGIN
        } else if (sessionGateway.hasPendingPasswordRecovery()) {
            // Recovery sessions are never admitted as application sessions.
            sessionGateway.wipeAndSignOut()
            Decision.LOGIN
        } else {
            sessionGateway.fetchSessionAuthorizationStatus().fold(
                onSuccess = { status -> decideAuthorizedStatus(status) },
                onFailure = { failure -> decideFailure(failure) },
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        Decision.LOGIN
    }

    private suspend fun decideAuthorizedStatus(status: SessionAuthorizationStatus): Decision =
        when (status) {
            SessionAuthorizationStatus.AUTHORIZED -> {
                val refreshed = runCatching { sessionGateway.refreshAccess() }.isSuccess
                when {
                    refreshed -> Decision.HOME
                    sessionGateway.hasTrustedOfflineSession() -> Decision.HOME
                    else -> Decision.LOGIN
                }
            }

            SessionAuthorizationStatus.PROFILE_INACTIVE,
            SessionAuthorizationStatus.PROFILE_MISSING,
            SessionAuthorizationStatus.ACCOUNT_BLOCKED,
            SessionAuthorizationStatus.MEMBERSHIP_INVALID,
            SessionAuthorizationStatus.PROVISIONING_INCOMPLETE -> {
                sessionGateway.wipeAndSignOut()
                Decision.LOGIN
            }
        }

    private suspend fun decideFailure(failure: Throwable): Decision {
        val classified = ErrorClassifier.classify(failure)
        val connectivityFailure = classified is AppFailure.NetworkUnavailable ||
            classified is AppFailure.ConnectionFailed ||
            classified is AppFailure.Timeout
        if (connectivityFailure && sessionGateway.hasTrustedOfflineSession()) {
            return Decision.HOME
        }
        if (!connectivityFailure) {
            sessionGateway.wipeAndSignOut()
        }
        return Decision.LOGIN
    }
}
