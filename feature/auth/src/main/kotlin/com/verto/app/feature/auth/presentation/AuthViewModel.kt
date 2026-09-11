package com.verto.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.ErrorPresentationContext
import com.verto.app.core.error.ErrorPresentationPolicy
import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.feature.auth.application.AuthSessionCoordinator
import com.verto.app.feature.auth.domain.model.AuthInviteDetails
import com.verto.app.feature.auth.domain.repository.AuthGateway
import com.verto.app.utils.CrashReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: UserErrorPresentation? = null,
    val inviteDetails: AuthInviteDetails? = null,
    val isSendingOtp: Boolean = false,
    val sendOtpError: UserErrorPresentation? = null,
    val otpSentEmail: String? = null,
    val isVerifyingOtp: Boolean = false,
    val verifyOtpError: UserErrorPresentation? = null,
    val otpVerifiedEmail: String? = null,
    val isSavingPassword: Boolean = false,
    val savePasswordError: UserErrorPresentation? = null,
    val passwordResetCompleted: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authGateway: AuthGateway,
    private val sessionCoordinator: AuthSessionCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = businessError(AuthErrorCodes.FORM_INCOMPLETE)) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            safeGatewayCall { authGateway.login(email, password) }
                .onSuccess { user -> completeAuthentication(user.organizationId) }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isLoading = false, error = present(failure))
                    }
                }
        }
    }

    fun register(
        email: String,
        password: String,
        ownerName: String,
        orgName: String,
        ownerPhone: String = "",
        orgAddress: String = "",
    ) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            safeGatewayCall {
                authGateway.registerWithOrganization(
                    email = email,
                    password = password,
                    ownerName = ownerName,
                    organizationName = orgName,
                    ownerPhone = ownerPhone,
                    organizationAddress = orgAddress,
                )
            }.onSuccess { organizationId ->
                completeAuthentication(organizationId)
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(isLoading = false, error = present(failure))
                }
            }
        }
    }

    fun joinOrg(
        inviteCode: String,
        email: String,
        password: String,
        memberPhone: String = "",
    ) {
        if (_uiState.value.isLoading) return
        if (inviteCode.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = businessError(AuthErrorCodes.FORM_INCOMPLETE)) }
            return
        }
        if (_uiState.value.inviteDetails == null) {
            _uiState.update { it.copy(error = businessError(AuthErrorCodes.VERIFY_INVITE_FIRST)) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            safeGatewayCall {
                authGateway.joinOrganization(inviteCode, email, password, memberPhone)
            }.onSuccess { organizationId ->
                completeAuthentication(organizationId)
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(isLoading = false, error = present(failure))
                }
            }
        }
    }

    fun loadInviteDetails(inviteCode: String) {
        if (_uiState.value.isLoading) return
        if (inviteCode.isBlank()) {
            _uiState.update { it.copy(inviteDetails = null, error = null) }
            return
        }
        _uiState.update { it.copy(isLoading = true, inviteDetails = null, error = null) }
        viewModelScope.launch {
            safeGatewayCall { authGateway.getInviteDetails(inviteCode) }
                .onSuccess { details ->
                    _uiState.update { it.copy(isLoading = false, inviteDetails = details) }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            inviteDetails = null,
                            error = present(failure),
                        )
                    }
                }
        }
    }

    fun sendOtp(email: String) {
        if (_uiState.value.isSendingOtp) return
        if (email.isBlank()) {
            _uiState.update {
                it.copy(
                    sendOtpError = businessError(
                        AuthErrorCodes.RECOVERY_EMAIL_REQUIRED,
                        target = "email",
                        context = ErrorPresentationContext.FIELD,
                    )
                )
            }
            return
        }
        _uiState.update {
            it.copy(isSendingOtp = true, sendOtpError = null, otpSentEmail = null)
        }
        viewModelScope.launch {
            safeGatewayCall { authGateway.requestPasswordReset(email) }
                .onSuccess {
                    _uiState.update { it.copy(isSendingOtp = false, otpSentEmail = email) }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            isSendingOtp = false,
                            sendOtpError = present(failure, ErrorPresentationContext.FIELD),
                        )
                    }
                }
        }
    }

    fun verifyOtp(email: String, otp: String) {
        if (_uiState.value.isVerifyingOtp) return
        val normalizedOtp = otp.filter(Char::isDigit)
        if (normalizedOtp.length != RECOVERY_OTP_LENGTH || normalizedOtp != otp) {
            _uiState.update {
                it.copy(
                    verifyOtpError = businessError(
                        AuthErrorCodes.OTP_FORMAT,
                        target = "otp",
                        context = ErrorPresentationContext.FIELD,
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isVerifyingOtp = true,
                verifyOtpError = null,
                otpVerifiedEmail = null,
            )
        }
        viewModelScope.launch {
            safeGatewayCall {
                authGateway.verifyPasswordResetOtp(email, normalizedOtp)
            }.onSuccess {
                _uiState.update {
                    it.copy(isVerifyingOtp = false, otpVerifiedEmail = email)
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(
                        isVerifyingOtp = false,
                        verifyOtpError = present(failure, ErrorPresentationContext.FIELD),
                    )
                }
            }
        }
    }

    fun setNewPassword(newPassword: String, confirmPassword: String) {
        if (_uiState.value.isSavingPassword) return
        if (!PasswordPolicy.isAcceptable(newPassword)) {
            _uiState.update {
                it.copy(
                    savePasswordError = businessError(
                        AuthErrorCodes.PASSWORD_TOO_SHORT,
                        target = "password",
                        context = ErrorPresentationContext.FIELD,
                    )
                )
            }
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.update {
                it.copy(
                    savePasswordError = businessError(
                        AuthErrorCodes.PASSWORD_MISMATCH,
                        target = "password_confirmation",
                        context = ErrorPresentationContext.FIELD,
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSavingPassword = true,
                savePasswordError = null,
                passwordResetCompleted = false,
            )
        }
        viewModelScope.launch {
            safeGatewayCall {
                authGateway.setNewPasswordAfterVerifiedOtp(newPassword)
            }.onSuccess {
                _uiState.update {
                    it.copy(isSavingPassword = false, passwordResetCompleted = true)
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(
                        isSavingPassword = false,
                        savePasswordError = present(failure, ErrorPresentationContext.FIELD),
                    )
                }
            }
        }
    }

    private suspend fun completeAuthentication(organizationId: String) {
        runCatching { sessionCoordinator.completeAuthentication(organizationId) }
            .onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
                // Diagnostics must never prevent the fail-closed UI transition (local JVM tests
                // and devices without an initialized Crashlytics runtime are both valid cases).
                runCatching { CrashReporter.recordException(failure) }
                _uiState.update {
                    it.copy(isLoading = false, error = present(failure))
                }
            }
    }

    private suspend fun <T> safeGatewayCall(block: suspend () -> Result<T>): Result<T> =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        }

    private fun present(
        failure: Throwable,
        context: ErrorPresentationContext = ErrorPresentationContext.FORM,
    ): UserErrorPresentation = ErrorPresentationPolicy.from(failure, context)

    private fun businessError(
        code: String,
        target: String? = null,
        context: ErrorPresentationContext = ErrorPresentationContext.FORM,
    ): UserErrorPresentation = ErrorPresentationPolicy.from(
        AppFailure.BusinessRule(code = code, target = target),
        context,
    )

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSendOtpError() = _uiState.update { it.copy(sendOtpError = null) }
    fun clearVerifyOtpError() = _uiState.update { it.copy(verifyOtpError = null) }
    fun clearSavePasswordError() = _uiState.update { it.copy(savePasswordError = null) }
    fun clearInviteDetails() = _uiState.update { it.copy(inviteDetails = null) }
    fun consumeSuccess() = _uiState.update { it.copy(isSuccess = false) }
    fun consumeOtpSent() = _uiState.update { it.copy(otpSentEmail = null) }
    fun consumeOtpVerified() = _uiState.update { it.copy(otpVerifiedEmail = null) }
    fun consumePasswordResetCompleted() =
        _uiState.update {
            it.copy(passwordResetCompleted = false, savePasswordError = null)
        }

}
