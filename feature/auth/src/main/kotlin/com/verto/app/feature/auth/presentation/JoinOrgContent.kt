package com.verto.app.feature.auth.presentation

import com.verto.feature.auth.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.verto.app.feature.auth.domain.model.AuthInviteDetails
import com.verto.app.ui.components.VertoAuthScaffold
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoFormSectionHeader
import com.verto.app.ui.components.VertoInlineStatus
import com.verto.app.ui.components.VertoPasswordField
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.components.VertoStepIndicator
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing

private const val INVITE_CODE_STEP = 1
private const val EMPLOYEE_DETAILS_STEP = 2
private const val JOIN_STEPS = 2

internal data class JoinOrgContentState(
    val inviteDetails: AuthInviteDetails?,
    val error: String?,
    val isLoading: Boolean,
)

internal data class JoinOrgEvents(
    val onLoadInviteDetails: (String) -> Unit,
    val onJoin: (String, String, String, String) -> Unit,
    val onClearInviteDetails: () -> Unit,
    val onClearError: () -> Unit,
    val onNavigateToLogin: () -> Unit,
)

@Composable
internal fun JoinOrgContent(
    state: JoinOrgContentState,
    events: JoinOrgEvents,
) {
    val focusManager = LocalFocusManager.current

    var currentStep by rememberSaveable { mutableStateOf(INVITE_CODE_STEP) }
    var inviteCode by remember { mutableStateOf("") }
    var memberPhone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var employeeDetailsSubmitted by rememberSaveable { mutableStateOf(false) }
    var phoneTouched by rememberSaveable { mutableStateOf(false) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    var passwordConfirmationTouched by rememberSaveable { mutableStateOf(false) }
    var phoneWasFocused by rememberSaveable { mutableStateOf(false) }
    var emailWasFocused by rememberSaveable { mutableStateOf(false) }
    var passwordWasFocused by rememberSaveable { mutableStateOf(false) }
    var passwordConfirmationWasFocused by rememberSaveable { mutableStateOf(false) }
    var verifiedInviteCode by remember { mutableStateOf<String?>(null) }

    val inviteDetails = state.inviteDetails
    val emailValidation = LoginValidationPolicy.validateEmail(email)
    val phoneValidation = RegisterValidationPolicy.validatePhone(memberPhone)

    val phoneError = if (phoneTouched || employeeDetailsSubmitted) {
        when (phoneValidation) {
            RegisterInputError.InvalidPhone -> androidx.compose.ui.res.stringResource(R.string.join_phone_invalid)
            else -> null
        }
    } else {
        null
    }
    val emailError = if (emailTouched || employeeDetailsSubmitted) {
        when (emailValidation) {
            LoginInputError.Required -> androidx.compose.ui.res.stringResource(R.string.login_email_required)
            LoginInputError.InvalidEmail -> androidx.compose.ui.res.stringResource(R.string.login_email_invalid)
            null -> null
        }
    } else {
        null
    }
    val passwordError = if (passwordTouched || employeeDetailsSubmitted) {
        when {
            password.isBlank() -> androidx.compose.ui.res.stringResource(R.string.register_password_required)
            !PasswordPolicy.isAcceptable(password) ->
                androidx.compose.ui.res.stringResource(R.string.register_password_too_short)
            else -> null
        }
    } else {
        null
    }
    val passwordConfirmationError = if (
        passwordConfirmationTouched || employeeDetailsSubmitted
    ) {
        when {
            passwordConfirmation.isBlank() -> androidx.compose.ui.res.stringResource(R.string.register_password_confirmation_required)
            passwordConfirmation != password -> androidx.compose.ui.res.stringResource(R.string.register_password_mismatch)
            else -> null
        }
    } else {
        null
    }

    val isEmployeeDetailsValid = inviteDetails != null &&
        phoneValidation == null &&
        emailValidation == null &&
        password.isNotBlank() &&
        PasswordPolicy.isAcceptable(password) &&
        passwordConfirmation.isNotBlank() &&
        password == passwordConfirmation

    LaunchedEffect(inviteDetails?.code) {
        val detailsCode = inviteDetails?.code
        if (detailsCode == null) {
            verifiedInviteCode = null
            if (currentStep == EMPLOYEE_DETAILS_STEP) {
                currentStep = INVITE_CODE_STEP
            }
        } else if (verifiedInviteCode != detailsCode) {
            verifiedInviteCode = detailsCode
            currentStep = EMPLOYEE_DETAILS_STEP
        }
    }

    val verifyInviteCode = {
        if (inviteCode.isNotBlank() && !state.isLoading) {
            focusManager.clearFocus()
            events.onLoadInviteDetails(inviteCode)
        }
    }

    val submitJoin = {
        employeeDetailsSubmitted = true
        if (isEmployeeDetailsValid && !state.isLoading) {
            focusManager.clearFocus()
            events.onJoin(inviteCode, email.trim(), password, memberPhone.trim())
        }
    }

    val returnToInviteCode = {
        if (!state.isLoading) {
            focusManager.clearFocus()
            events.onClearError()
            currentStep = INVITE_CODE_STEP
        }
    }

    BackHandler(enabled = currentStep == EMPLOYEE_DETAILS_STEP) {
        returnToInviteCode()
    }

    VertoAuthScaffold {
        JoinOrgBrandHeader()

        if (currentStep == EMPLOYEE_DETAILS_STEP && inviteDetails != null) {
            VertoStepIndicator(
                currentStep = EMPLOYEE_DETAILS_STEP,
                totalSteps = JOIN_STEPS,
                label = androidx.compose.ui.res.stringResource(R.string.ds_e6ee3d3b9eeb),
                modifier = Modifier.padding(top = VertoSpacing.xl),
            )
        }

        VertoFormCard(
            modifier = Modifier.padding(
                top = if (currentStep == EMPLOYEE_DETAILS_STEP && inviteDetails != null) {
                    VertoSpacing.md
                } else {
                    VertoSpacing.xxl
                },
            ),
        ) {
            if (currentStep == INVITE_CODE_STEP || inviteDetails == null) {
                InviteCodeStep(
                    inviteCode = inviteCode,
                    onInviteCodeChange = {
                        inviteCode = it.uppercase()
                        events.onClearInviteDetails()
                        events.onClearError()
                    },
                    inviteCodeError = state.error,
                    isLoading = state.isLoading,
                    onVerify = verifyInviteCode,
                    onNext = verifyInviteCode,
                )
            } else {
                EmployeeDetailsStep(
                    inviteDetails = inviteDetails,
                    memberPhone = memberPhone,
                    onMemberPhoneChange = {
                        memberPhone = it
                        events.onClearError()
                    },
                    email = email,
                    onEmailChange = {
                        email = it
                        events.onClearError()
                    },
                    password = password,
                    onPasswordChange = {
                        password = it
                        events.onClearError()
                    },
                    passwordConfirmation = passwordConfirmation,
                    onPasswordConfirmationChange = {
                        passwordConfirmation = it
                        events.onClearError()
                    },
                    phoneError = phoneError,
                    emailError = emailError,
                    passwordError = passwordError,
                    passwordConfirmationError = passwordConfirmationError,
                    serverError = state.error,
                    isLoading = state.isLoading,
                    onPhoneFocusChanged = { isFocused ->
                        if (phoneWasFocused && !isFocused) phoneTouched = true
                        phoneWasFocused = isFocused
                    },
                    onEmailFocusChanged = { isFocused ->
                        if (emailWasFocused && !isFocused) emailTouched = true
                        emailWasFocused = isFocused
                    },
                    onPasswordFocusChanged = { isFocused ->
                        if (passwordWasFocused && !isFocused) passwordTouched = true
                        passwordWasFocused = isFocused
                    },
                    onPasswordConfirmationFocusChanged = { isFocused ->
                        if (passwordConfirmationWasFocused && !isFocused) {
                            passwordConfirmationTouched = true
                        }
                        passwordConfirmationWasFocused = isFocused
                    },
                    onSubmit = submitJoin,
                    onBack = returnToInviteCode,
                )
            }
        }

        ExistingAccountLink(onClick = events.onNavigateToLogin)
    }
}
