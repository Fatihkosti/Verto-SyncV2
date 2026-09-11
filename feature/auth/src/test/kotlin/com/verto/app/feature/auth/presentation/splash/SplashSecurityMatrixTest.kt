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

class SplashSecurityMatrixTest {
    @Test fun `every non authorized server status is denied and wiped`() = runTest {
        val denied = SessionAuthorizationStatus.entries.filterNot {
            it == SessionAuthorizationStatus.AUTHORIZED
        }
        denied.forEach { status ->
            val gateway = Gateway(status = Result.success(status))
            assertEquals(status.name, SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
            assertTrue(status.name, gateway.wiped)
        }
    }

    @Test fun `authorized status with failed refresh and no trusted offline is denied`() = runTest {
        val gateway = Gateway(
            status = Result.success(SessionAuthorizationStatus.AUTHORIZED),
            refreshFails = true,
            trustedOffline = false,
        )
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
        assertFalse(gateway.wiped)
    }

    @Test fun `authorized status with failed refresh may use explicit trusted offline`() = runTest {
        val gateway = Gateway(
            status = Result.success(SessionAuthorizationStatus.AUTHORIZED),
            refreshFails = true,
            trustedOffline = true,
        )
        assertEquals(SplashViewModel.Decision.HOME, SplashViewModel(gateway).decide())
    }

    @Test fun `timeout is denied unless trusted offline session exists`() = runTest {
        val failure = Result.failure<SessionAuthorizationStatus>(
            ClassifiedFailureException(AppFailure.Timeout())
        )
        assertEquals(
            SplashViewModel.Decision.LOGIN,
            SplashViewModel(Gateway(status = failure, trustedOffline = false)).decide(),
        )
        assertEquals(
            SplashViewModel.Decision.HOME,
            SplashViewModel(Gateway(status = failure, trustedOffline = true)).decide(),
        )
    }

    @Test fun `unexpected initialization exception fails closed`() = runTest {
        val gateway = Gateway(initializationFails = true)
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
    }

    @Test fun `pending recovery session is wiped before server admission call`() = runTest {
        val gateway = Gateway(recoveryPending = true)
        assertEquals(SplashViewModel.Decision.LOGIN, SplashViewModel(gateway).decide())
        assertTrue(gateway.wiped)
        assertEquals(0, gateway.statusCalls)
    }

    private class Gateway(
        private val hasSessionValue: Boolean = true,
        private val status: Result<SessionAuthorizationStatus> =
            Result.success(SessionAuthorizationStatus.AUTHORIZED),
        private val trustedOffline: Boolean = false,
        private val recoveryPending: Boolean = false,
        private val refreshFails: Boolean = false,
        private val initializationFails: Boolean = false,
    ) : SplashSessionGateway {
        var wiped = false
        var statusCalls = 0

        override suspend fun awaitInitialization(timeoutMillis: Long) {
            if (initializationFails) error("init failure")
        }

        override fun hasSession(): Boolean = hasSessionValue
        override suspend fun hasPendingPasswordRecovery(): Boolean = recoveryPending
        override suspend fun fetchSessionAuthorizationStatus(): Result<SessionAuthorizationStatus> {
            statusCalls += 1
            return status
        }
        override fun hasTrustedOfflineSession(): Boolean = trustedOffline
        override suspend fun refreshAccess() {
            if (refreshFails) error("refresh failure")
        }
        override suspend fun wipeAndSignOut() { wiped = true }
    }
}
