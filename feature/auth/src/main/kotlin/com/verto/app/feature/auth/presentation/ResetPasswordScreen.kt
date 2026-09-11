package com.verto.app.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.VertoAuthScaffold
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoPasswordField
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.components.resolveVertoError
import com.verto.feature.auth.R

@Composable
fun ResetPasswordScreen(
    email: String,
    isOtpVerified: Boolean,
    onPasswordReset: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    var confirmationTouched by rememberSaveable { mutableStateOf(false) }
    var passwordWasFocused by rememberSaveable { mutableStateOf(false) }
    var confirmationWasFocused by rememberSaveable { mutableStateOf(false) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }

    val passwordValidationError = when {
        password.isBlank() -> stringResource(R.string.recovery_new_password_required)
        !PasswordPolicy.isAcceptable(password) ->
            stringResource(R.string.register_password_too_short)
        else -> null
    }
    val confirmationValidationError = when {
        confirmation.isBlank() -> stringResource(R.string.register_password_confirmation_required)
        confirmation != password -> stringResource(R.string.register_password_mismatch)
        else -> null
    }
    val visiblePasswordError = if (passwordTouched || submitAttempted) {
        passwordValidationError
    } else {
        null
    }
    val visibleConfirmationError = if (confirmationTouched || submitAttempted) {
        confirmationValidationError
    } else {
        null
    }
    val serverError = uiState.savePasswordError?.resolveVertoError()?.message
    val passwordError = visiblePasswordError ?: serverError

    val submitPassword = {
        submitAttempted = true
        if (
            isOtpVerified &&
            passwordValidationError == null &&
            confirmationValidationError == null &&
            !uiState.isSavingPassword
        ) {
            focusManager.clearFocus()
            viewModel.setNewPassword(password, confirmation)
        }
    }

    LaunchedEffect(uiState.passwordResetCompleted) {
        if (uiState.passwordResetCompleted) {
            viewModel.consumePasswordResetCompleted()
            onPasswordReset()
        }
    }

    VertoAuthScaffold {
        RecoveryBrandHeader()

        RecoveryProgressIndicator(
            currentStage = RecoveryStage.Password,
            modifier = Modifier.padding(top = VertoSpacing.xl),
        )

        VertoFormCard(modifier = Modifier.padding(top = VertoSpacing.lg)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
            ) {
                RecoveryIllustration(icon = Icons.Filled.Lock)
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.ds_b9ceb1e346d4),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.ds_f9c5ce822685),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (email.isNotBlank()) {
                    Text(
                        text = email,
                        color = AccentPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                VertoPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        viewModel.clearSavePasswordError()
                    },
                    label = androidx.compose.ui.res.stringResource(R.string.ds_a29c7e9659e5),
                    isRequired = true,
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    supportingText = stringResource(R.string.register_password_help),
                    errorText = passwordError,
                    enabled = isOtpVerified && !uiState.isSavingPassword,
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (passwordWasFocused && !focusState.isFocused) passwordTouched = true
                        passwordWasFocused = focusState.isFocused
                    },
                )

                VertoPasswordField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                        viewModel.clearSavePasswordError()
                    },
                    label = androidx.compose.ui.res.stringResource(R.string.ds_2fe5d407d642),
                    isRequired = true,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submitPassword() }),
                    errorText = visibleConfirmationError,
                    enabled = isOtpVerified && !uiState.isSavingPassword,
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (confirmationWasFocused && !focusState.isFocused) {
                            confirmationTouched = true
                        }
                        confirmationWasFocused = focusState.isFocused
                    },
                )

                VertoPrimaryButton(
                    text = stringResource(R.string.recovery_save_password),
                    onClick = submitPassword,
                    enabled = isOtpVerified && !uiState.isSavingPassword,
                    isLoading = uiState.isSavingPassword,
                    modifier = Modifier.padding(top = VertoSpacing.xs),
                )
            }
        }

        if (!isOtpVerified) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_01ea21e3c4a0),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = VertoSize.minTouchTarget)
                    .padding(top = VertoSpacing.sm),
            )
        }
    }
}
