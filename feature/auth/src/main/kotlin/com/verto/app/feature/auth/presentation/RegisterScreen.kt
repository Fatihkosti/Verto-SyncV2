package com.verto.app.feature.auth.presentation

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.VertoAuthScaffold
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoFormSectionHeader
import com.verto.app.ui.components.VertoInlineStatus
import com.verto.app.ui.components.VertoPasswordField
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.components.VertoStepIndicator
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.components.resolveVertoError
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.auth.R

private const val ORGANIZATION_STEP = 1
private const val ACCOUNT_STEP = 2
private const val REGISTER_STEPS = 2

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var currentStep by rememberSaveable { mutableStateOf(ORGANIZATION_STEP) }
    var organizationName by rememberSaveable { mutableStateOf("") }
    var organizationAddress by rememberSaveable { mutableStateOf("") }
    var ownerName by rememberSaveable { mutableStateOf("") }
    var ownerPhone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var stepOneSubmitted by rememberSaveable { mutableStateOf(false) }
    var stepTwoSubmitted by rememberSaveable { mutableStateOf(false) }

    val organizationNameFocus = remember { FocusRequester() }
    val ownerNameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }

    val validation = RegisterValidationPolicy.validate(
        organizationName = organizationName,
        ownerName = ownerName,
        phone = ownerPhone,
        email = email,
        password = password,
        passwordConfirmation = passwordConfirmation,
    )

    val organizationNameError = if (stepOneSubmitted) {
        validation.organizationNameError.toOrganizationErrorText()
    } else {
        null
    }
    val ownerNameError = if (stepTwoSubmitted) validation.ownerNameError.toOwnerErrorText() else null
    val phoneError = if (stepTwoSubmitted) validation.phoneError.toPhoneErrorText() else null
    val emailError = if (stepTwoSubmitted) validation.emailError.toEmailErrorText() else null
    val passwordError = if (stepTwoSubmitted) validation.passwordError.toPasswordErrorText() else null
    val confirmationError = if (stepTwoSubmitted) {
        validation.confirmationError.toConfirmationErrorText()
    } else {
        null
    }

    val proceedToAccount = {
        stepOneSubmitted = true
        if (validation.isStepOneValid) {
            focusManager.clearFocus()
            viewModel.clearError()
            currentStep = ACCOUNT_STEP
        } else {
            organizationNameFocus.requestFocus()
        }
    }

    val submitRegistration = {
        stepTwoSubmitted = true
        if (validation.isStepTwoValid && !uiState.isLoading) {
            focusManager.clearFocus()
            viewModel.register(
                email.trim(),
                password,
                ownerName.trim(),
                organizationName.trim(),
                ownerPhone.trim(),
                organizationAddress.trim(),
            )
        } else if (!uiState.isLoading) {
            when {
                validation.ownerNameError != null -> ownerNameFocus.requestFocus()
                validation.phoneError != null -> phoneFocus.requestFocus()
                validation.emailError != null -> emailFocus.requestFocus()
                validation.passwordError != null -> passwordFocus.requestFocus()
                validation.confirmationError != null -> confirmationFocus.requestFocus()
            }
        }
    }

    val returnToOrganizationStep = {
        if (!uiState.isLoading) {
            focusManager.clearFocus()
            viewModel.clearError()
            currentStep = ORGANIZATION_STEP
        }
    }

    BackHandler(enabled = currentStep == ACCOUNT_STEP) {
        returnToOrganizationStep()
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.consumeSuccess()
            onRegisterSuccess()
        }
    }

    LaunchedEffect(currentStep) {
        if (currentStep == ACCOUNT_STEP) ownerNameFocus.requestFocus()
    }

    VertoAuthScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.register_brand_name),
                color = AccentPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.register_brand_tagline),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        VertoStepIndicator(
            currentStep = currentStep,
            totalSteps = REGISTER_STEPS,
            modifier = Modifier.padding(top = VertoSpacing.xl),
        )

        VertoFormCard(modifier = Modifier.padding(top = VertoSpacing.md)) {
            if (currentStep == ORGANIZATION_STEP) {
                OrganizationStep(
                    organizationName = organizationName,
                    onOrganizationNameChange = {
                        organizationName = it
                        viewModel.clearError()
                    },
                    organizationAddress = organizationAddress,
                    onOrganizationAddressChange = {
                        organizationAddress = it
                        viewModel.clearError()
                    },
                    organizationNameError = organizationNameError,
                    organizationNameFocus = organizationNameFocus,
                    onNext = proceedToAccount,
                    onNavigateToLogin = onNavigateToLogin,
                )
            } else {
                AccountStep(
                    ownerName = ownerName,
                    onOwnerNameChange = {
                        ownerName = it
                        viewModel.clearError()
                    },
                    ownerPhone = ownerPhone,
                    onOwnerPhoneChange = {
                        ownerPhone = it
                        viewModel.clearError()
                    },
                    email = email,
                    onEmailChange = {
                        email = it
                        viewModel.clearError()
                    },
                    password = password,
                    onPasswordChange = {
                        password = it
                        viewModel.clearError()
                    },
                    passwordConfirmation = passwordConfirmation,
                    onPasswordConfirmationChange = {
                        passwordConfirmation = it
                        viewModel.clearError()
                    },
                    ownerNameError = ownerNameError,
                    phoneError = phoneError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmationError = confirmationError,
                    serverError = uiState.error?.resolveVertoError()?.message,
                    isLoading = uiState.isLoading,
                    ownerNameFocus = ownerNameFocus,
                    phoneFocus = phoneFocus,
                    emailFocus = emailFocus,
                    passwordFocus = passwordFocus,
                    confirmationFocus = confirmationFocus,
                    onSubmit = submitRegistration,
                    onBack = returnToOrganizationStep,
                )
            }
        }
    }
}

@Composable
private fun OrganizationStep(
    organizationName: String,
    onOrganizationNameChange: (String) -> Unit,
    organizationAddress: String,
    onOrganizationAddressChange: (String) -> Unit,
    organizationNameError: String?,
    organizationNameFocus: FocusRequester,
    onNext: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    VertoFormSectionHeader(
        title = stringResource(R.string.register_organization_section_title),
        description = stringResource(R.string.register_organization_section_description),
    )

    VertoTextField(
        value = organizationName,
        onValueChange = onOrganizationNameChange,
        label = stringResource(R.string.register_organization_name_label),
        isRequired = true,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        errorText = organizationNameError,
        modifier = Modifier.focusRequester(organizationNameFocus),
    )

    VertoTextField(
        value = organizationAddress,
        onValueChange = onOrganizationAddressChange,
        label = stringResource(R.string.register_organization_address_label),
        isOptional = true,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { onNext() }),
    )

    VertoPrimaryButton(
        text = stringResource(R.string.register_next),
        onClick = onNext,
    )

    RegisterLinkButton(
        text = stringResource(R.string.register_existing_account),
        onClick = onNavigateToLogin,
    )
}

@Composable
private fun AccountStep(
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    ownerPhone: String,
    onOwnerPhoneChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordConfirmation: String,
    onPasswordConfirmationChange: (String) -> Unit,
    ownerNameError: String?,
    phoneError: String?,
    emailError: String?,
    passwordError: String?,
    confirmationError: String?,
    serverError: String?,
    isLoading: Boolean,
    ownerNameFocus: FocusRequester,
    phoneFocus: FocusRequester,
    emailFocus: FocusRequester,
    passwordFocus: FocusRequester,
    confirmationFocus: FocusRequester,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    VertoFormSectionHeader(
        title = stringResource(R.string.register_account_section_title),
        description = stringResource(R.string.register_account_section_description),
    )

    VertoTextField(
        value = ownerName,
        onValueChange = onOwnerNameChange,
        label = stringResource(R.string.register_owner_name_label),
        isRequired = true,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        errorText = ownerNameError,
        modifier = Modifier.focusRequester(ownerNameFocus),
    )

    VertoTextField(
        value = ownerPhone,
        onValueChange = onOwnerPhoneChange,
        label = stringResource(R.string.register_owner_phone_label),
        isOptional = true,
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        errorText = phoneError,
        modifier = Modifier.focusRequester(phoneFocus),
    )

    VertoTextField(
        value = email,
        onValueChange = onEmailChange,
        label = stringResource(R.string.register_email_label),
        isRequired = true,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        errorText = emailError,
        modifier = Modifier.focusRequester(emailFocus),
    )

    VertoPasswordField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.register_password_label),
        isRequired = true,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        supportingText = stringResource(R.string.register_password_help),
        errorText = passwordError,
        modifier = Modifier.focusRequester(passwordFocus),
    )

    VertoPasswordField(
        value = passwordConfirmation,
        onValueChange = onPasswordConfirmationChange,
        label = stringResource(R.string.register_password_confirmation_label),
        isRequired = true,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        errorText = confirmationError,
        modifier = Modifier.focusRequester(confirmationFocus),
    )

    serverError?.let { message ->
        VertoInlineStatus(message = message, tone = VertoStatusTone.Error)
    }

    VertoPrimaryButton(
        text = stringResource(R.string.register_submit),
        onClick = onSubmit,
        enabled = !isLoading,
        isLoading = isLoading,
    )

    VertoSecondaryButton(
        text = stringResource(R.string.register_back),
        onClick = onBack,
        enabled = !isLoading,
    )
}

@Composable
private fun RegisterLinkButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VertoSize.minTouchTarget),
    ) {
        Text(
            text = text,
            color = AccentPrimary,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RegisterInputError?.toOrganizationErrorText(): String? = when (this) {
    RegisterInputError.Required -> stringResource(R.string.register_organization_name_required)
    else -> null
}

@Composable
private fun RegisterInputError?.toOwnerErrorText(): String? = when (this) {
    RegisterInputError.Required -> stringResource(R.string.register_owner_name_required)
    else -> null
}

@Composable
private fun RegisterInputError?.toPhoneErrorText(): String? = when (this) {
    RegisterInputError.InvalidPhone -> stringResource(R.string.register_phone_invalid)
    else -> null
}

@Composable
private fun RegisterInputError?.toEmailErrorText(): String? = when (this) {
    RegisterInputError.Required -> stringResource(R.string.register_email_required)
    RegisterInputError.InvalidEmail -> stringResource(R.string.register_email_invalid)
    else -> null
}

@Composable
private fun RegisterInputError?.toPasswordErrorText(): String? = when (this) {
    RegisterInputError.Required -> stringResource(R.string.register_password_required)
    RegisterInputError.PasswordTooShort -> stringResource(R.string.register_password_too_short)
    else -> null
}

@Composable
private fun RegisterInputError?.toConfirmationErrorText(): String? = when (this) {
    RegisterInputError.Required -> stringResource(R.string.register_password_confirmation_required)
    RegisterInputError.PasswordMismatch -> stringResource(R.string.register_password_mismatch)
    else -> null
}
