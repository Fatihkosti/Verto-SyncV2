package com.verto.app.feature.auth.presentation

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.error.ClassifiedFailureException
import com.verto.app.core.error.UserErrorMessageKey
import com.verto.app.feature.auth.application.AuthSessionCoordinator
import com.verto.app.feature.auth.domain.model.AuthInviteDetails
import com.verto.app.feature.auth.domain.model.AuthenticatedUser
import com.verto.app.feature.auth.domain.repository.AuthGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `login success completes session`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply {
            loginResult = Result.success(AuthenticatedUser("u", "org", "User", "sales"))
        }
        val coordinator = FakeCoordinator()
        val vm = AuthViewModel(gateway, coordinator)
        vm.login("u@example.com", "password")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSuccess)
        assertEquals("org", coordinator.completedOrg)
    }

    @Test fun `invalid credentials use canonical auth message`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply {
            loginResult = Result.failure(business(AuthErrorCodes.INVALID_CREDENTIALS))
        }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.login("u@example.com", "bad-password")
        advanceUntilIdle()
        assertEquals(UserErrorMessageKey.AUTH_INVALID_CREDENTIALS, vm.uiState.value.error?.messageKey)
        assertFalse(vm.uiState.value.isSuccess)
    }

    @Test fun `duplicate login tap is ignored while request is in flight`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply {
            loginResult = Result.failure(business(AuthErrorCodes.INVALID_CREDENTIALS))
        }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.login("u@example.com", "bad-password")
        vm.login("u@example.com", "bad-password")
        advanceUntilIdle()
        assertEquals(1, gateway.loginCalls)
    }

    @Test fun `duplicate recovery send tap is ignored while request is in flight`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway()
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.sendOtp("u@example.com")
        vm.sendOtp("u@example.com")
        advanceUntilIdle()
        assertEquals(1, gateway.passwordResetRequests)
    }

    @Test fun `network login failure stays network`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply {
            loginResult = Result.failure(
                ClassifiedFailureException(AppFailure.NetworkUnavailable())
            )
        }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.login("u@example.com", "password")
        advanceUntilIdle()
        assertEquals(UserErrorMessageKey.NETWORK_UNAVAILABLE, vm.uiState.value.error?.messageKey)
        assertFalse(vm.uiState.value.isSuccess)
    }

    @Test fun `thrown gateway exception is fail closed`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply { throwOnLogin = true }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.login("u@example.com", "password")
        advanceUntilIdle()
        assertEquals(UserErrorMessageKey.UNEXPECTED, vm.uiState.value.error?.messageKey)
        assertFalse(vm.uiState.value.isSuccess)
    }

    @Test fun `eight digit otp rejected by server is invalid otp not format error`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply {
            verifyOtpResult = Result.failure(business(AuthErrorCodes.INVALID_OTP))
        }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.verifyOtp("u@example.com", "12345678")
        advanceUntilIdle()
        assertNull(vm.uiState.value.otpVerifiedEmail)
        assertEquals(UserErrorMessageKey.AUTH_INVALID_OTP, vm.uiState.value.verifyOtpError?.messageKey)
    }

    @Test fun `malformed otp uses format error`() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeAuthGateway(), FakeCoordinator())
        vm.verifyOtp("u@example.com", "1234567")
        assertEquals(UserErrorMessageKey.AUTH_OTP_FORMAT, vm.uiState.value.verifyOtpError?.messageKey)
    }

    @Test fun `server verified otp advances recovery`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply { verifyOtpResult = Result.success(Unit) }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.verifyOtp("u@example.com", "12345678")
        advanceUntilIdle()
        assertEquals("u@example.com", vm.uiState.value.otpVerifiedEmail)
    }

    @Test fun `expired otp remains expired otp`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply {
            verifyOtpResult = Result.failure(business(AuthErrorCodes.EXPIRED_OTP))
        }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.verifyOtp("u@example.com", "12345678")
        advanceUntilIdle()
        assertEquals(UserErrorMessageKey.AUTH_EXPIRED_OTP, vm.uiState.value.verifyOtpError?.messageKey)
    }

    @Test fun `password reset without recovery state is rejected`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply {
            setPasswordResult = Result.failure(business(AuthErrorCodes.RECOVERY_SESSION_REQUIRED))
        }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.setNewPassword("abc123", "abc123")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.passwordResetCompleted)
        assertEquals(
            UserErrorMessageKey.AUTH_RECOVERY_SESSION_REQUIRED,
            vm.uiState.value.savePasswordError?.messageKey,
        )
    }

    @Test fun `password reset after verified recovery can complete`() = runTest(dispatcher) {
        val gateway = FakeAuthGateway().apply { setPasswordResult = Result.success(Unit) }
        val vm = AuthViewModel(gateway, FakeCoordinator())
        vm.setNewPassword("abc123", "abc123")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.passwordResetCompleted)
    }

    private class FakeCoordinator : AuthSessionCoordinator {
        var completedOrg: String? = null
        override suspend fun completeAuthentication(organizationId: String) { completedOrg = organizationId }
        override suspend fun restoreAuthentication(organizationId: String) = Unit
    }

    private class FakeAuthGateway : AuthGateway {
        var loginCalls = 0
        var passwordResetRequests = 0
        var loginResult: Result<AuthenticatedUser> = Result.failure(
            ClassifiedFailureException(AppFailure.Unknown("AUTH_TEST_UNKNOWN"))
        )
        var verifyOtpResult: Result<Unit> = Result.failure(business(AuthErrorCodes.INVALID_OTP))
        var setPasswordResult: Result<Unit> = Result.failure(business(AuthErrorCodes.RECOVERY_SESSION_REQUIRED))
        var throwOnLogin = false

        override suspend fun login(email: String, password: String): Result<AuthenticatedUser> {
            loginCalls += 1
            if (throwOnLogin) error("boom")
            return loginResult
        }

        override suspend fun registerWithOrganization(
            email: String,
            password: String,
            ownerName: String,
            organizationName: String,
            ownerPhone: String,
            organizationAddress: String,
        ) = Result.success("org")

        override suspend fun joinOrganization(
            inviteCode: String,
            email: String,
            password: String,
            memberPhone: String,
        ) = Result.success("org")

        override suspend fun getInviteDetails(inviteCode: String) =
            Result.success(AuthInviteDetails(inviteCode, "", "", ""))

        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            passwordResetRequests += 1
            return Result.success(Unit)
        }
        override suspend fun verifyPasswordResetOtp(email: String, otp: String) = verifyOtpResult
        override suspend fun setNewPasswordAfterVerifiedOtp(newPassword: String) = setPasswordResult
    }

    companion object {
        private fun business(code: String) = BusinessRuleFailureException(code)
    }
}
