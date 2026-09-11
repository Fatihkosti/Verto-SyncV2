package com.verto.app.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.VertoAuthScaffold
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoPasswordField
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.components.VertoUserError
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.auth.R

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToJoin: () -> Unit,
    onNavigateToPasswordReset: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }
    var emailWasFocused by remember { mutableStateOf(false) }
    var passwordWasFocused by remember { mutableStateOf(false) }

    val validation = LoginValidationPolicy.validate(email, password)
    val visibleEmailError = if (emailTouched || submitAttempted) validation.emailError else null
    val visiblePasswordError = if (passwordTouched || submitAttempted) validation.passwordError else null

    val emailErrorText = when (visibleEmailError) {
        LoginInputError.Required -> stringResource(R.string.login_email_required)
        LoginInputError.InvalidEmail -> stringResource(R.string.login_email_invalid)
        null -> null
    }
    val passwordErrorText = when (visiblePasswordError) {
        LoginInputError.Required -> stringResource(R.string.login_password_required)
        LoginInputError.InvalidEmail -> null
        null -> null
    }

    val submitLogin = {
        submitAttempted = true
        if (validation.isValid && !uiState.isLoading) {
            focusManager.clearFocus()
            viewModel.login(email.trim(), password)
        }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.consumeSuccess()
            onLoginSuccess()
        }
    }

    VertoAuthScaffold(verticalArrangement = Arrangement.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.login_brand_name),
                color = AccentPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.login_brand_tagline),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        VertoFormCard(modifier = Modifier.padding(top = VertoSpacing.xxl)) {
            Text(
                text = stringResource(R.string.login_screen_title),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )

            VertoTextField(
                value = email,
                onValueChange = {
                    email = it
                    viewModel.clearError()
                },
                label = stringResource(R.string.login_email_label),
                isRequired = true,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                errorText = emailErrorText,
                modifier = Modifier.onFocusChanged { focusState ->
                    if (emailWasFocused && !focusState.isFocused) emailTouched = true
                    emailWasFocused = focusState.isFocused
                },
            )

            VertoPasswordField(
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearError()
                },
                label = stringResource(R.string.login_password_label),
                isRequired = true,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                errorText = passwordErrorText,
                modifier = Modifier.onFocusChanged { focusState ->
                    if (passwordWasFocused && !focusState.isFocused) passwordTouched = true
                    passwordWasFocused = focusState.isFocused
                },
            )

            uiState.error?.let { error ->
                VertoUserError(error = error)
            }

            VertoPrimaryButton(
                text = stringResource(R.string.login_submit),
                onClick = submitLogin,
                enabled = !uiState.isLoading,
                isLoading = uiState.isLoading,
            )

            LoginLinkButton(
                text = stringResource(R.string.login_forgot_password),
                onClick = { onNavigateToPasswordReset(email.trim()) },
            )
        }

        LoginAlternativeDivider(modifier = Modifier.padding(vertical = VertoSpacing.lg))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
        ) {
            VertoSecondaryButton(
                text = stringResource(R.string.login_register_organization),
                onClick = onNavigateToRegister,
            )
            VertoSecondaryButton(
                text = stringResource(R.string.login_join_organization),
                onClick = onNavigateToJoin,
            )
        }
    }

}

@Composable
private fun LoginLinkButton(
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
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LoginAlternativeDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VertoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
        Text(
            text = stringResource(R.string.login_alternative_separator),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}
