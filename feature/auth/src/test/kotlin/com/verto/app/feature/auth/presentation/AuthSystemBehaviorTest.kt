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
class AuthSystemBehaviorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `blank login fails locally without contacting gateway`() = runTest(dispatcher) {
        val gateway = RecordingGateway()
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.login("", "")

        assertEquals(0, gateway.loginCalls)
        assertEquals(UserErrorMessageKey.AUTH_FORM_INCOMPLETE, vm.uiState.value.error?.messageKey)
    }

    @Test fun `successful login admits organization only after gateway success`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply {
            loginResult = Result.success(AuthenticatedUser("user-1", "org-1", "User", "sales"))
        }
        val coordinator = RecordingCoordinator()
        val vm = AuthViewModel(gateway, coordinator)

        vm.login("user@example.com", "password")
        advanceUntilIdle()

        assertEquals(1, gateway.loginCalls)
        assertEquals("org-1", coordinator.completedOrg)
        assertTrue(vm.uiState.value.isSuccess)
    }

    @Test fun `coordinator failure prevents successful admission`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply {
            loginResult = Result.success(AuthenticatedUser("user-1", "org-1", "User", "sales"))
        }
        val coordinator = RecordingCoordinator(fail = true)
        val vm = AuthViewModel(gateway, coordinator)

        vm.login("user@example.com", "password")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSuccess)
        assertEquals(UserErrorMessageKey.UNEXPECTED, vm.uiState.value.error?.messageKey)
    }

    @Test fun `successful registration provisions then admits organization`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply { registerResult = Result.success("org-new") }
        val coordinator = RecordingCoordinator()
        val vm = AuthViewModel(gateway, coordinator)

        vm.register(
            email = "owner@example.com",
            password = "abc123",
            ownerName = "Owner",
            orgName = "Organization",
        )
        advanceUntilIdle()

        assertEquals(1, gateway.registerCalls)
        assertEquals("org-new", coordinator.completedOrg)
        assertTrue(vm.uiState.value.isSuccess)
    }

    @Test fun `registration gateway failure never admits organization`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply {
            registerResult = Result.failure(
                BusinessRuleFailureException(AuthErrorCodes.PROVISIONING_INCOMPLETE)
            )
        }
        val coordinator = RecordingCoordinator()
        val vm = AuthViewModel(gateway, coordinator)

        vm.register(
            email = "owner@example.com",
            password = "abc123",
            ownerName = "Owner",
            orgName = "Organization",
        )
        advanceUntilIdle()

        assertEquals(1, gateway.registerCalls)
        assertNull(coordinator.completedOrg)
        assertFalse(vm.uiState.value.isSuccess)
    }

    @Test fun `join organization is blocked until invite has been verified`() = runTest(dispatcher) {
        val gateway = RecordingGateway()
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.joinOrg("CODE", "user@example.com", "password")

        assertEquals(0, gateway.joinCalls)
        assertEquals(UserErrorMessageKey.AUTH_VERIFY_INVITE_FIRST, vm.uiState.value.error?.messageKey)
    }

    @Test fun `verified invite can join and admit organization`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply {
            inviteResult = Result.success(AuthInviteDetails("CODE", "Org", "sales", ""))
            joinResult = Result.success("org-2")
        }
        val coordinator = RecordingCoordinator()
        val vm = AuthViewModel(gateway, coordinator)

        vm.loadInviteDetails("CODE")
        advanceUntilIdle()
        vm.joinOrg("CODE", "user@example.com", "password")
        advanceUntilIdle()

        assertEquals(1, gateway.inviteCalls)
        assertEquals(1, gateway.joinCalls)
        assertEquals("org-2", coordinator.completedOrg)
        assertTrue(vm.uiState.value.isSuccess)
    }

    @Test fun `blank recovery email never calls Supabase gateway`() = runTest(dispatcher) {
        val gateway = RecordingGateway()
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.sendOtp(" ")

        assertEquals(0, gateway.resetCalls)
        assertEquals(UserErrorMessageKey.AUTH_RECOVERY_EMAIL_REQUIRED, vm.uiState.value.sendOtpError?.messageKey)
    }

    @Test fun `successful recovery request exposes target email`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply { resetResult = Result.success(Unit) }
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.sendOtp("user@example.com")
        advanceUntilIdle()

        assertEquals(1, gateway.resetCalls)
        assertEquals("user@example.com", vm.uiState.value.otpSentEmail)
        assertFalse(vm.uiState.value.isSendingOtp)
    }

    @Test fun `six digit legacy recovery otp is rejected without server call`() = runTest(dispatcher) {
        val gateway = RecordingGateway()
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.verifyOtp("user@example.com", "123456")

        assertEquals(0, gateway.verifyCalls)
        assertEquals(UserErrorMessageKey.AUTH_OTP_FORMAT, vm.uiState.value.verifyOtpError?.messageKey)
    }

    @Test fun `eight digit recovery otp is sent unchanged to gateway`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply { verifyResult = Result.success(Unit) }
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.verifyOtp("user@example.com", "12345678")
        advanceUntilIdle()

        assertEquals(1, gateway.verifyCalls)
        assertEquals("12345678", gateway.lastOtp)
        assertEquals("user@example.com", vm.uiState.value.otpVerifiedEmail)
    }

    @Test fun `otp containing non digits is rejected without normalization`() = runTest(dispatcher) {
        val gateway = RecordingGateway()
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.verifyOtp("user@example.com", "1234-678")

        assertEquals(0, gateway.verifyCalls)
        assertEquals(UserErrorMessageKey.AUTH_OTP_FORMAT, vm.uiState.value.verifyOtpError?.messageKey)
    }

    @Test fun `invalid server otp does not advance password recovery`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply {
            verifyResult = Result.failure(BusinessRuleFailureException(AuthErrorCodes.INVALID_OTP))
        }
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.verifyOtp("user@example.com", "12345678")
        advanceUntilIdle()

        assertNull(vm.uiState.value.otpVerifiedEmail)
        assertEquals(UserErrorMessageKey.AUTH_INVALID_OTP, vm.uiState.value.verifyOtpError?.messageKey)
    }

    @Test fun `short new password is rejected without update call`() = runTest(dispatcher) {
        val gateway = RecordingGateway()
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.setNewPassword("short", "short")

        assertEquals(0, gateway.setPasswordCalls)
        assertEquals(UserErrorMessageKey.AUTH_PASSWORD_TOO_SHORT, vm.uiState.value.savePasswordError?.messageKey)
    }

    @Test fun `password mismatch is rejected without update call`() = runTest(dispatcher) {
        val gateway = RecordingGateway()
        val vm = AuthViewModel(gateway, RecordingCoordinator())
        val valid = "abc123"

        vm.setNewPassword(valid, "$valid!")

        assertEquals(0, gateway.setPasswordCalls)
        assertEquals(UserErrorMessageKey.AUTH_PASSWORD_MISMATCH, vm.uiState.value.savePasswordError?.messageKey)
    }

    @Test fun `verified recovery session can complete password update`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply { setPasswordResult = Result.success(Unit) }
        val vm = AuthViewModel(gateway, RecordingCoordinator())
        val valid = "abc123"

        vm.setNewPassword(valid, valid)
        advanceUntilIdle()

        assertEquals(1, gateway.setPasswordCalls)
        assertEquals(valid, gateway.lastNewPassword)
        assertTrue(vm.uiState.value.passwordResetCompleted)
    }

    @Test fun `missing recovery session remains fail closed`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply {
            setPasswordResult = Result.failure(
                BusinessRuleFailureException(AuthErrorCodes.RECOVERY_SESSION_REQUIRED)
            )
        }
        val vm = AuthViewModel(gateway, RecordingCoordinator())
        val valid = "abc123"

        vm.setNewPassword(valid, valid)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.passwordResetCompleted)
        assertEquals(
            UserErrorMessageKey.AUTH_RECOVERY_SESSION_REQUIRED,
            vm.uiState.value.savePasswordError?.messageKey,
        )
    }

    @Test fun `network failure remains distinguishable from credential failure`() = runTest(dispatcher) {
        val gateway = RecordingGateway().apply {
            loginResult = Result.failure(ClassifiedFailureException(AppFailure.NetworkUnavailable()))
        }
        val vm = AuthViewModel(gateway, RecordingCoordinator())

        vm.login("user@example.com", "password")
        advanceUntilIdle()

        assertEquals(UserErrorMessageKey.NETWORK_UNAVAILABLE, vm.uiState.value.error?.messageKey)
    }

    private class RecordingCoordinator(
        private val fail: Boolean = false,
    ) : AuthSessionCoordinator {
        var completedOrg: String? = null
        override suspend fun completeAuthentication(organizationId: String) {
            if (fail) error("coordinator failure")
            completedOrg = organizationId
        }
        override suspend fun restoreAuthentication(organizationId: String) = Unit
    }

    private class RecordingGateway : AuthGateway {
        var loginCalls = 0
        var registerCalls = 0
        var joinCalls = 0
        var inviteCalls = 0
        var resetCalls = 0
        var verifyCalls = 0
        var setPasswordCalls = 0
        var lastOtp: String? = null
        var lastNewPassword: String? = null

        var loginResult: Result<AuthenticatedUser> = Result.failure(
            ClassifiedFailureException(AppFailure.Unknown("AUTH_TEST_UNSET"))
        )
        var registerResult: Result<String> = Result.success("org-register")
        var inviteResult: Result<AuthInviteDetails> = Result.failure(
            ClassifiedFailureException(AppFailure.Unknown("AUTH_TEST_UNSET"))
        )
        var joinResult: Result<String> = Result.failure(
            ClassifiedFailureException(AppFailure.Unknown("AUTH_TEST_UNSET"))
        )
        var resetResult: Result<Unit> = Result.success(Unit)
        var verifyResult: Result<Unit> = Result.failure(
            BusinessRuleFailureException(AuthErrorCodes.INVALID_OTP)
        )
        var setPasswordResult: Result<Unit> = Result.failure(
            BusinessRuleFailureException(AuthErrorCodes.RECOVERY_SESSION_REQUIRED)
        )

        override suspend fun login(email: String, password: String): Result<AuthenticatedUser> {
            loginCalls += 1
            return loginResult
        }

        override suspend fun registerWithOrganization(
            email: String,
            password: String,
            ownerName: String,
            organizationName: String,
            ownerPhone: String,
            organizationAddress: String,
        ): Result<String> {
            registerCalls += 1
            return registerResult
        }

        override suspend fun joinOrganization(
            inviteCode: String,
            email: String,
            password: String,
            memberPhone: String,
        ): Result<String> {
            joinCalls += 1
            return joinResult
        }

        override suspend fun getInviteDetails(inviteCode: String): Result<AuthInviteDetails> {
            inviteCalls += 1
            return inviteResult
        }

        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            resetCalls += 1
            return resetResult
        }

        override suspend fun verifyPasswordResetOtp(email: String, otp: String): Result<Unit> {
            verifyCalls += 1
            lastOtp = otp
            return verifyResult
        }

        override suspend fun setNewPasswordAfterVerifiedOtp(newPassword: String): Result<Unit> {
            setPasswordCalls += 1
            lastNewPassword = newPassword
            return setPasswordResult
        }
    }
}
