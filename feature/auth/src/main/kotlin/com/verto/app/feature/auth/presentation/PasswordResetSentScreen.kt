package com.verto.app.feature.auth.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.VertoAuthScaffold
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoInlineStatus
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.components.resolveVertoError
import com.verto.feature.auth.R
import kotlinx.coroutines.delay

private const val RESEND_COOLDOWN_MS = 60_000L

@Composable
fun PasswordResetSentScreen(
    email: String = "",
    onBackToLogin: () -> Unit,
    onOtpVerified: (email: String, isVerified: Boolean) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var enteredEmail by rememberSaveable { mutableStateOf(email) }
    var otpCode by remember { mutableStateOf("") }
    var currentStageName by rememberSaveable { mutableStateOf(RecoveryStage.Email.name) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var emailWasFocused by rememberSaveable { mutableStateOf(false) }
    var sendAttempted by rememberSaveable { mutableStateOf(false) }
    var verifyAttempted by rememberSaveable { mutableStateOf(false) }
    var cooldownEndsAt by rememberSaveable { mutableStateOf(0L) }
    var secondsLeft by rememberSaveable { mutableStateOf(0) }

    val currentStage = RecoveryStage.valueOf(currentStageName)
    val emailValidation = LoginValidationPolicy.validateEmail(enteredEmail)
    val emailError = if (emailTouched || sendAttempted) {
        when (emailValidation) {
            LoginInputError.Required -> stringResource(R.string.login_email_required)
            LoginInputError.InvalidEmail -> stringResource(R.string.login_email_invalid)
            null -> null
        }
    } else {
        null
    }

    val submitEmail = {
        sendAttempted = true
        if (emailValidation == null && !uiState.isSendingOtp) {
            focusManager.clearFocus()
            viewModel.sendOtp(enteredEmail.trim())
        }
    }

    val submitOtp = {
        verifyAttempted = true
        if (otpCode.length == RECOVERY_OTP_LENGTH && !uiState.isVerifyingOtp) {
            focusManager.clearFocus()
            viewModel.verifyOtp(enteredEmail.trim(), otpCode)
        }
    }

    val submitCompletedOtp: (String) -> Unit = { completedCode ->
        verifyAttempted = true
        if (completedCode.length == RECOVERY_OTP_LENGTH && !uiState.isVerifyingOtp) {
            focusManager.clearFocus()
            viewModel.verifyOtp(enteredEmail.trim(), completedCode)
        }
    }

    LaunchedEffect(uiState.otpSentEmail) {
        uiState.otpSentEmail?.let { sentEmail ->
            enteredEmail = sentEmail
            otpCode = ""
            verifyAttempted = false
            currentStageName = RecoveryStage.Verification.name
            cooldownEndsAt = System.currentTimeMillis() + RESEND_COOLDOWN_MS
            viewModel.consumeOtpSent()
        }
    }

    LaunchedEffect(uiState.otpVerifiedEmail) {
        uiState.otpVerifiedEmail?.let { verifiedEmail ->
            viewModel.consumeOtpVerified()
            onOtpVerified(verifiedEmail, true)
        }
    }

    fun fillOtpFromClipboard() {
        if (currentStage != RecoveryStage.Verification || otpCode.length == RECOVERY_OTP_LENGTH) return
        val clipboardOtp = extractRecoveryOtpFromClipboard(clipboardManager.getText()?.text)
        if (clipboardOtp != null) {
            otpCode = clipboardOtp
            verifyAttempted = false
            viewModel.clearVerifyOtpError()
        }
    }

    DisposableEffect(lifecycleOwner, currentStage, otpCode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) fillOtpFromClipboard()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentStage, cooldownEndsAt) {
        if (currentStage != RecoveryStage.Verification || cooldownEndsAt == 0L) return@LaunchedEffect
        while (true) {
            val remaining = ((cooldownEndsAt - System.currentTimeMillis()) / 1_000L)
                .toInt()
                .coerceAtLeast(0)
            secondsLeft = remaining
            if (remaining == 0) break
            delay(1_000L)
        }
    }

    VertoAuthScaffold {
        RecoveryBrandHeader()

        RecoveryProgressIndicator(
            currentStage = currentStage,
            modifier = Modifier.padding(top = VertoSpacing.xl),
        )

        VertoFormCard(modifier = Modifier.padding(top = VertoSpacing.lg)) {
            AnimatedContent(
                targetState = currentStage,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    recoveryContentTransition(
                        forward = targetState.ordinal > initialState.ordinal,
                    )
                },
                label = androidx.compose.ui.res.stringResource(R.string.ds_081f6a3d6af2),
            ) { stage ->
                when (stage) {
                    RecoveryStage.Email -> EmailStage(
                        email = enteredEmail,
                        onEmailChange = {
                            enteredEmail = it
                            viewModel.clearSendOtpError()
                        },
                        emailError = emailError ?: uiState.sendOtpError?.resolveVertoError()?.message,
                        emailWasFocused = emailWasFocused,
                        onFocusChanged = { focused ->
                            if (emailWasFocused && !focused) emailTouched = true
                            emailWasFocused = focused
                        },
                        isLoading = uiState.isSendingOtp,
                        onSubmit = submitEmail,
                    )

                    RecoveryStage.Verification -> VerificationStage(
                        email = enteredEmail,
                        code = otpCode,
                        onCodeChange = {
                            otpCode = it
                            viewModel.clearVerifyOtpError()
                        },
                        onCodeComplete = submitCompletedOtp,
                        error = if (verifyAttempted || uiState.verifyOtpError != null) {
                            uiState.verifyOtpError?.resolveVertoError()?.message
                        } else {
                            null
                        },
                        isLoading = uiState.isVerifyingOtp,
                        secondsLeft = secondsLeft,
                        canResend = secondsLeft == 0 && !uiState.isSendingOtp,
                        isResending = uiState.isSendingOtp,
                        onResend = {
                            if (secondsLeft == 0) viewModel.sendOtp(enteredEmail.trim())
                        },
                        onSubmit = submitOtp,
                        onChangeEmail = {
                            currentStageName = RecoveryStage.Email.name
                            otpCode = ""
                            cooldownEndsAt = 0L
                            secondsLeft = 0
                            viewModel.clearVerifyOtpError()
                            viewModel.clearSendOtpError()
                        },
                    )

                    RecoveryStage.Password -> Unit
                }
            }
        }

        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VertoSize.minTouchTarget)
                .padding(top = VertoSpacing.sm),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_487251153f6f),
                color = AccentPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun EmailStage(
    email: String,
    onEmailChange: (String) -> Unit,
    emailError: String?,
    emailWasFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
    ) {
        RecoveryIllustration(icon = Icons.Filled.Mail)
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_0ad388a336c0),
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_35b818de43fe),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        VertoTextField(
            value = email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.login_email_label),
            isRequired = true,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            errorText = emailError,
            modifier = Modifier.onFocusChanged { onFocusChanged(it.isFocused) },
        )
        VertoInlineStatus(
            message = androidx.compose.ui.res.stringResource(R.string.ds_537603b98a0b),
            tone = VertoStatusTone.Info,
        )
        VertoPrimaryButton(
            text = stringResource(R.string.recovery_send_code),
            onClick = onSubmit,
            enabled = !isLoading,
            isLoading = isLoading,
        )
    }
}

@Composable
private fun VerificationStage(
    email: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onCodeComplete: (String) -> Unit,
    error: String?,
    isLoading: Boolean,
    secondsLeft: Int,
    canResend: Boolean,
    isResending: Boolean,
    onResend: () -> Unit,
    onSubmit: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
    ) {
        RecoveryIllustration(icon = Icons.Filled.Shield)
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_46d3ed267d40),
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_f0655fc4fcf9),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = email,
            color = AccentPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        OtpCodeInput(
            code = code,
            onCodeChange = onCodeChange,
            onComplete = onCodeComplete,
        )
        error?.let { message ->
            VertoInlineStatus(message = message, tone = VertoStatusTone.Error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (secondsLeft > 0) {
                    stringResource(R.string.recovery_resend_countdown, secondsLeft)
                } else {
                    stringResource(R.string.recovery_code_not_received)
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onResend,
                enabled = canResend,
            ) {
                Text(
                    text = if (isResending) stringResource(R.string.recovery_sending_code) else stringResource(R.string.recovery_resend_code),
                    color = if (canResend) AccentPrimary else TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        VertoPrimaryButton(
            text = stringResource(R.string.recovery_verify_code),
            onClick = onSubmit,
            enabled = code.length == RECOVERY_OTP_LENGTH && !isLoading,
            isLoading = isLoading,
        )
        TextButton(onClick = onChangeEmail) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_7eb09c4ae92b),
                color = AccentPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun recoveryContentTransition(forward: Boolean): ContentTransform {
    return if (forward) {
        (slideInHorizontally { it / 5 } + fadeIn()) togetherWith
            (slideOutHorizontally { -it / 5 } + fadeOut())
    } else {
        (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith
            (slideOutHorizontally { it / 5 } + fadeOut())
    }
}
