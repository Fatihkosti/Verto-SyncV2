package com.verto.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.DisabledColor
import com.verto.app.ui.theme.DisabledContainer
import com.verto.app.ui.theme.InfoColor
import com.verto.app.ui.theme.InfoContainer
import com.verto.app.ui.theme.OfflineColor
import com.verto.app.ui.theme.OfflineContainer
import com.verto.app.ui.theme.PermissionColor
import com.verto.app.ui.theme.PermissionContainer
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.ErrorContainer
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.SuccessContainer
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.WaitingColor
import com.verto.app.ui.theme.WaitingContainer
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.WarningContainer
import com.verto.app.ui.theme.VertoComponentSize
import com.verto.core.designsystem.R

/**
 * Unified field used by Verto forms.
 *
 * Existing call sites keep their API while auth screens can opt into keyboard actions,
 * visibility controls, semantic descriptions, and inline field states.
 */
@Composable
fun VertoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isRequired: Boolean = false,
    isOptional: Boolean = false,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
    errorText: String? = null,
    successText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    accessibilityDescription: String? = null,
) {
    val hasError = isError || errorText != null
    val message = errorText ?: successText ?: supportingText
    val messageColor = when {
        hasError -> ErrorColor
        successText != null -> SuccessColor
        else -> TextMuted
    }
    val fieldStateDescription = errorText ?: successText
    val fieldSemantics = Modifier.semantics {
        accessibilityDescription?.let { contentDescription = it }
        fieldStateDescription?.let { stateDescription = it }
    }

    Column(modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) TextSecondary else DisabledColor,
                fontWeight = FontWeight.SemiBold,
            )
            when {
                isRequired -> Text(
                    text = stringResource(R.string.verto_field_required),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentPrimary,
                )

                isOptional -> Text(
                    text = stringResource(R.string.verto_field_optional),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
        Spacer(Modifier.height(VertoSpacing.xs))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder.takeIf { it.isNotBlank() }?.let {
                { Text(it, color = TextMuted, style = MaterialTheme.typography.bodyMedium) }
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon ?: trailingText?.let { text ->
                {
                    Text(
                        text = text,
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = VertoSpacing.sm),
                    )
                }
            },
            singleLine = singleLine,
            isError = hasError,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (successText != null) SuccessColor else AccentPrimary,
                unfocusedBorderColor = if (successText != null) SuccessColor else BorderColor,
                focusedContainerColor = BgCard,
                unfocusedContainerColor = BgCard,
                disabledContainerColor = DisabledContainer,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = DisabledColor,
                cursorColor = AccentPrimary,
                errorBorderColor = ErrorColor,
                errorCursorColor = ErrorColor,
            ),
            shape = RoundedCornerShape(VertoRadius.sm),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VertoComponentSize.formFieldMinHeight)
                .then(fieldSemantics),
        )
        if (message != null) {
            Spacer(Modifier.height(VertoSpacing.xxs))
            Text(
                text = message,
                color = messageColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics {
                    if (hasError) liveRegion = LiveRegionMode.Polite
                },
            )
        }
    }
}

@Composable
fun VertoPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isRequired: Boolean = false,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions(),
    supportingText: String? = null,
    errorText: String? = null,
    successText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    accessibilityDescription: String? = null,
    showPasswordDescription: String = stringResource(R.string.verto_password_show),
    hidePasswordDescription: String = stringResource(R.string.verto_password_hide),
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val visibilityDescription = if (passwordVisible) hidePasswordDescription else showPasswordDescription

    VertoTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        isRequired = isRequired,
        isError = isError,
        trailingIcon = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled && !readOnly,
                modifier = Modifier.size(VertoSize.minTouchTarget),
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = visibilityDescription,
                    tint = if (enabled) TextSecondary else DisabledColor,
                    modifier = Modifier.size(VertoSize.iconLarge),
                )
            }
        },
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        supportingText = supportingText,
        errorText = errorText,
        successText = successText,
        enabled = enabled,
        readOnly = readOnly,
        accessibilityDescription = accessibilityDescription,
    )
}

@Composable
fun VertoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingDescription: String = stringResource(R.string.verto_loading),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VertoComponentSize.buttonHeight)
            .semantics {
                if (isLoading) stateDescription = loadingDescription
            },
        shape = RoundedCornerShape(VertoRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentPrimary,
            disabledContainerColor = DisabledContainer,
            disabledContentColor = DisabledColor,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                color = TextOnAccent,
                modifier = Modifier.alpha(if (isLoading) 0f else 1f),
            )
            if (isLoading) {
                CircularProgressIndicator(
                    color = TextOnAccent,
                    modifier = Modifier.size(VertoSize.iconLarge),
                    strokeWidth = VertoStroke.progress,
                )
            }
        }
    }
}

@Composable
fun VertoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = VertoComponentSize.buttonHeight),
        shape = RoundedCornerShape(VertoRadius.md),
        border = BorderStroke(VertoStroke.thin, BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextSecondary,
            disabledContentColor = DisabledColor,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VertoInlineStatus(
    message: String,
    tone: VertoStatusTone,
    modifier: Modifier = Modifier,
) {
    val accent = tone.formAccent()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            },
        color = tone.formContainer(),
        shape = RoundedCornerShape(VertoRadius.sm),
        border = BorderStroke(VertoStroke.thin, accent.copy(alpha = VertoAlpha.border)),
    ) {
        Row(
            modifier = Modifier.padding(VertoSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tone.formIcon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(VertoComponentSize.formStatusIcon),
            )
            Text(
                text = message,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun VertoFormSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (description != null) {
            Text(
                text = description,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun VertoStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val safeTotal = totalSteps.coerceAtLeast(1)
    val safeCurrent = currentStep.coerceIn(1, safeTotal)
    val resolvedLabel = label ?: stringResource(R.string.verto_step_count, safeCurrent, safeTotal)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = resolvedLabel },
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
    ) {
        Text(
            text = resolvedLabel,
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(safeTotal) { index ->
                val active = index + 1 == safeCurrent
                Surface(
                    modifier = Modifier
                        .width(if (active) VertoComponentSize.stepIndicatorActiveWidth else VertoComponentSize.stepIndicatorDot)
                        .height(VertoComponentSize.stepIndicatorDot),
                    color = if (active) AccentPrimary else BorderColor,
                    shape = CircleShape,
                ) {}
            }
        }
    }
}

@Composable
fun VertoFormCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(VertoSpacing.xl),
    contentSpacing: Dp = VertoSpacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BgCard,
        shape = RoundedCornerShape(VertoRadius.lg),
        border = BorderStroke(VertoStroke.thin, BorderColor),
        tonalElevation = VertoElevation.card,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}

@Composable
private fun VertoStatusTone.formAccent(): Color = when (this) {
    VertoStatusTone.Info -> InfoColor
    VertoStatusTone.Success -> SuccessColor
    VertoStatusTone.Warning -> WarningColor
    VertoStatusTone.Error -> ErrorColor
    VertoStatusTone.Waiting -> WaitingColor
    VertoStatusTone.Offline -> OfflineColor
    VertoStatusTone.Permission -> PermissionColor
    VertoStatusTone.Disabled -> DisabledColor
}

@Composable
private fun VertoStatusTone.formContainer(): Color = when (this) {
    VertoStatusTone.Info -> InfoContainer
    VertoStatusTone.Success -> SuccessContainer
    VertoStatusTone.Warning -> WarningContainer
    VertoStatusTone.Error -> ErrorContainer
    VertoStatusTone.Waiting -> WaitingContainer
    VertoStatusTone.Offline -> OfflineContainer
    VertoStatusTone.Permission -> PermissionContainer
    VertoStatusTone.Disabled -> DisabledContainer
}

private fun VertoStatusTone.formIcon(): ImageVector = when (this) {
    VertoStatusTone.Info -> Icons.Filled.Info
    VertoStatusTone.Success -> Icons.Filled.CheckCircle
    VertoStatusTone.Warning -> Icons.Filled.Warning
    VertoStatusTone.Error -> Icons.Filled.Error
    VertoStatusTone.Waiting -> Icons.Filled.HourglassTop
    VertoStatusTone.Offline -> Icons.Filled.CloudOff
    VertoStatusTone.Permission -> Icons.Filled.Lock
    VertoStatusTone.Disabled -> Icons.Filled.Lock
}
