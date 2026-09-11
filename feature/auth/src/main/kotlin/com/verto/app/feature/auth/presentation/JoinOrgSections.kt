package com.verto.app.feature.auth.presentation

import androidx.compose.ui.res.stringResource

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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


@Composable
internal fun JoinOrgBrandHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_2d2bb6a420ab),
            color = AccentPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_dfd73d41e0a4),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun InviteCodeStep(
    inviteCode: String,
    onInviteCodeChange: (String) -> Unit,
    inviteCodeError: String?,
    isLoading: Boolean,
    onVerify: () -> Unit,
    onNext: () -> Unit,
) {
    VertoFormSectionHeader(
        title = androidx.compose.ui.res.stringResource(R.string.ds_a3a9588b092f),
        description = stringResource(R.string.join_invite_description),
    )

    VertoTextField(
        value = inviteCode,
        onValueChange = onInviteCodeChange,
        label = androidx.compose.ui.res.stringResource(R.string.ds_a3a9588b092f),
        placeholder = androidx.compose.ui.res.stringResource(R.string.ds_f639c9e070f0),
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { onNext() }),
        errorText = inviteCodeError,
    )

    VertoPrimaryButton(
        text = stringResource(R.string.join_verify_code),
        onClick = onVerify,
        enabled = inviteCode.isNotBlank() && !isLoading,
        isLoading = isLoading,
    )

    Text(
        text = androidx.compose.ui.res.stringResource(R.string.ds_590e792edd47),
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun EmployeeDetailsStep(
    inviteDetails: AuthInviteDetails,
    memberPhone: String,
    onMemberPhoneChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordConfirmation: String,
    onPasswordConfirmationChange: (String) -> Unit,
    phoneError: String?,
    emailError: String?,
    passwordError: String?,
    passwordConfirmationError: String?,
    serverError: String?,
    isLoading: Boolean,
    onPhoneFocusChanged: (Boolean) -> Unit,
    onEmailFocusChanged: (Boolean) -> Unit,
    onPasswordFocusChanged: (Boolean) -> Unit,
    onPasswordConfirmationFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    VertoFormSectionHeader(
        title = stringResource(R.string.ds_942623106f7d),
        description = stringResource(R.string.join_details_description),
    )

    VertoInlineStatus(
        message = stringResource(R.string.ds_8a203f25f052),
        tone = VertoStatusTone.Success,
    )

    VertoTextField(
        value = memberPhone,
        onValueChange = onMemberPhoneChange,
        label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number),
        placeholder = androidx.compose.ui.res.stringResource(R.string.ds_d7820fac7314),
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        errorText = phoneError,
        modifier = Modifier.onFocusChanged { focusState ->
            onPhoneFocusChanged(focusState.isFocused)
        },
    )

    VertoTextField(
        value = email,
        onValueChange = onEmailChange,
        label = androidx.compose.ui.res.stringResource(R.string.ds_0915ef8ea533),
        placeholder = androidx.compose.ui.res.stringResource(R.string.ds_0ad388a336c0),
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        errorText = emailError,
        modifier = Modifier.onFocusChanged { focusState ->
            onEmailFocusChanged(focusState.isFocused)
        },
    )

    VertoPasswordField(
        value = password,
        onValueChange = onPasswordChange,
        label = androidx.compose.ui.res.stringResource(R.string.ds_7ba22cf7e99d),
        placeholder = androidx.compose.ui.res.stringResource(R.string.ds_fe369a164b86),
        isRequired = true,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        errorText = passwordError,
        modifier = Modifier.onFocusChanged { focusState ->
            onPasswordFocusChanged(focusState.isFocused)
        },
    )

    VertoPasswordField(
        value = passwordConfirmation,
        onValueChange = onPasswordConfirmationChange,
        label = androidx.compose.ui.res.stringResource(R.string.ds_2fe5d407d642),
        placeholder = androidx.compose.ui.res.stringResource(R.string.ds_a017f9996e82),
        isRequired = true,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        errorText = passwordConfirmationError,
        modifier = Modifier.onFocusChanged { focusState ->
            onPasswordConfirmationFocusChanged(focusState.isFocused)
        },
    )

    serverError?.let { message ->
        VertoInlineStatus(message = message, tone = VertoStatusTone.Error)
    }

    VertoPrimaryButton(
        text = stringResource(R.string.join_submit),
        onClick = onSubmit,
        enabled = !isLoading,
        isLoading = isLoading,
    )

    TextButton(
        onClick = onBack,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VertoSize.minTouchTarget),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_dd3fd999d25b),
            color = AccentPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ExistingAccountLink(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VertoSize.minTouchTarget),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = TextSecondary)) {
                    append(stringResource(R.string.auth_have_account_prompt))
                }
                withStyle(SpanStyle(color = AccentPrimary, fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.auth_sign_in_action))
                }
            },
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}
