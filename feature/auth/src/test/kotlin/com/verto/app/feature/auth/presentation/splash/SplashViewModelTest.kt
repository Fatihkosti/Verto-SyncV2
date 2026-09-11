package com.verto.app.feature.auth.presentation.splash

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.ClassifiedFailureException
import com.verto.app.feature.auth.domain.repository.SessionAuthorizationStatus
import com.verto.app.feature.auth.domain.repository.SplashSessionGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashViewModelTest {
    @Test fun `no session goes to login`() = runTest {
        val gateway = FakeSplashGateway(hasSession = false)
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
    }


    @Test fun `recovery session is wiped and never admitted to home`() = runTest {
        val gateway = FakeSplashGateway(passwordRecoveryPending = true)
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
        assertTrue(gateway.wiped)
        assertFalse(gateway.refreshed)
    }

    @Test fun `authorized online session goes home`() = runTest {
        val gateway = FakeSplashGateway(status = Result.success(SessionAuthorizationStatus.AUTHORIZED))
        assertEquals(SplashViewModel.Decision.HOME, SplashViewModel(gateway).decide())
        assertTrue(gateway.refreshed)
    }

    @Test fun `profile inactive is wiped and denied`() = runTest {
        val gateway = FakeSplashGateway(status = Result.success(SessionAuthorizationStatus.PROFILE_INACTIVE))
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
        assertTrue(gateway.wiped)
    }

    @Test fun `blocked account is denied`() = runTest {
        val gateway = FakeSplashGateway(status = Result.success(SessionAuthorizationStatus.ACCOUNT_BLOCKED))
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
    }

    @Test fun `network failure without trusted offline is denied without wipe`() = runTest {
        val gateway = FakeSplashGateway(
            status = Result.failure(ClassifiedFailureException(AppFailure.NetworkUnavailable()))
        )
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
        assertFalse(gateway.wiped)
    }

    @Test fun `network failure permits only explicit trusted offline`() = runTest {
        val gateway = FakeSplashGateway(
            status = Result.failure(ClassifiedFailureException(AppFailure.NetworkUnavailable())),
            trustedOffline = true,
        )
        assertEquals(SplashViewModel.Decision.HOME, SplashViewModel(gateway).decide())
    }

    @Test fun `unknown failure is wiped and denied`() = runTest {
        val gateway = FakeSplashGateway(
            status = Result.failure(ClassifiedFailureException(AppFailure.Unknown("AUTH_TEST_UNKNOWN")))
        )
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
        assertTrue(gateway.wiped)
    }

    private class FakeSplashGateway(
        private val hasSession: Boolean = true,
        private val status: Result<SessionAuthorizationStatus> =
            Result.success(SessionAuthorizationStatus.AUTHORIZED),
        private val trustedOffline: Boolean = false,
        private val passwordRecoveryPending: Boolean = false,
    ) : SplashSessionGateway {
        var refreshed = false
        var wiped = false
        override suspend fun awaitInitialization(timeoutMillis: Long) = Unit
        override fun hasSession(): Boolean = hasSession
        override suspend fun hasPendingPasswordRecovery(): Boolean = passwordRecoveryPending
        override suspend fun fetchSessionAuthorizationStatus(): Result<SessionAuthorizationStatus> = status
        override fun hasTrustedOfflineSession(): Boolean = trustedOffline
        override suspend fun refreshAccess() { refreshed = true }
        override suspend fun wipeAndSignOut() { wiped = true }
    }
}
