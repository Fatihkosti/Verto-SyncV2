package com.verto.app.feature.auth.presentation

import com.verto.feature.auth.R

import com.verto.app.ui.components.VertoOutlinedTextField

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoComponentSize

// ── حقل نص موحد لكل شاشات المصادقة ──────────────────────────
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    VertoOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VertoRadius.sm),
        isError = isError,
        enabled = enabled,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPrimary,
            unfocusedBorderColor = BorderColor,
            errorBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentPrimary,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() },
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        singleLine = true,
    )
}

// ── عنوان قسم ────────────────────────────────────────────────
@Composable
fun AuthSectionLabel(text: String) {
    Text(
        text = text,
        color = AccentPrimary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End,
    )
}

enum class RecoveryStage(val labelRes: Int, val icon: ImageVector) {
    Email(R.string.recovery_stage_email, Icons.Outlined.Email),
    Verification(R.string.recovery_stage_verification, Icons.Filled.Shield),
    Password(R.string.recovery_stage_password, Icons.Filled.Lock),
}

/** مؤشّر موحّد لمراحل استعادة كلمة المرور. */
@Composable
fun RecoveryProgressIndicator(
    currentStage: RecoveryStage,
    modifier: Modifier = Modifier,
) {
    val stages = RecoveryStage.entries

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stages.forEachIndexed { index, stage ->
                RecoveryStageIndicator(
                    stage = stage,
                    currentStage = currentStage,
                    modifier = Modifier.weight(1f),
                )
                if (index < stages.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(VertoStroke.progress)
                            .background(
                                if (stage.ordinal < currentStage.ordinal) {
                                    AccentPrimary
                                } else {
                                    BorderColor.copy(alpha = VertoAlpha.border)
                                },
                            ),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            stages.forEach { stage ->
                Text(
                    text = androidx.compose.ui.res.stringResource(stage.labelRes),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = when {
                        stage.ordinal <= currentStage.ordinal -> AccentPrimary
                        else -> TextSecondary
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (stage == currentStage) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RecoveryStageIndicator(
    stage: RecoveryStage,
    currentStage: RecoveryStage,
    modifier: Modifier = Modifier,
) {
    val completed = stage.ordinal < currentStage.ordinal
    val current = stage == currentStage
    val active = completed || current

    Surface(
        modifier = modifier.size(VertoSize.iconContainerLarge),
        shape = CircleShape,
        color = if (active) AccentPrimary else BgCard,
        border = BorderStroke(
            VertoStroke.thin,
            if (active) AccentPrimary else BorderColor.copy(alpha = VertoAlpha.border),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (completed) Icons.Filled.Check else stage.icon,
                contentDescription = null,
                tint = if (active) TextOnAccent else TextMuted,
                modifier = Modifier.size(VertoSize.iconMedium),
            )
        }
    }
}

@Composable
fun RecoveryIllustration(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(VertoSize.iconContainerLarge),
        shape = CircleShape,
        color = AccentPrimary.copy(alpha = VertoAlpha.subtle),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(VertoSize.iconHero),
            )
        }
    }
}

@Composable
fun RecoveryBrandHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_2d2bb6a420ab),
            color = AccentPrimary,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_d5036f51ad16),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** خانات OTP بطول إعداد Supabase، وتدعم لصق الرمز كاملًا في أي خانة. */
@Composable
fun OtpCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember { List(RECOVERY_OTP_LENGTH) { FocusRequester() } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(RECOVERY_OTP_LENGTH) { index ->
            var isFocused by remember { mutableStateOf(false) }
            val digit = code.getOrNull(index)?.toString().orEmpty()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(VertoComponentSize.formFieldMinHeight)
                    .clip(RoundedCornerShape(VertoRadius.sm))
                    .background(BgCard)
                    .border(
                        width = if (isFocused) VertoStroke.progress else VertoStroke.thin,
                        color = if (isFocused) AccentPrimary else BorderColor,
                        shape = RoundedCornerShape(VertoRadius.sm),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = digit,
                    onValueChange = { incoming ->
                        val digits = incoming.filter(Char::isDigit)
                        if (digits.isEmpty()) {
                            val next = if (index < code.length) {
                                code.removeRange(index, index + 1)
                            } else {
                                code
                            }
                            onCodeChange(next)
                            if (index > 0) focusRequesters[index - 1].requestFocus()
                        } else {
                            val next = if (digits.length > 1) {
                                // Pasting/autofill may deliver the whole OTP to any focused cell.
                                digits.take(RECOVERY_OTP_LENGTH)
                            } else {
                                (code.take(index) + digits + code.drop(index + 1))
                                    .filter(Char::isDigit)
                                    .take(RECOVERY_OTP_LENGTH)
                            }
                            onCodeChange(next)
                            if (next.length == RECOVERY_OTP_LENGTH) {
                                onComplete(next)
                            } else {
                                val nextIndex = if (digits.length > 1) {
                                    next.length.coerceAtMost(RECOVERY_OTP_LENGTH - 1)
                                } else {
                                    (index + 1).coerceAtMost(RECOVERY_OTP_LENGTH - 1)
                                }
                                focusRequesters[nextIndex].requestFocus()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequesters[index])
                        .onFocusChanged { isFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (
                                event.key == Key.Backspace &&
                                event.type == KeyEventType.KeyDown &&
                                digit.isEmpty() &&
                                index > 0
                            ) {
                                focusRequesters[index - 1].requestFocus()
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    ),
                    cursorBrush = SolidColor(AccentPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == RECOVERY_OTP_LENGTH - 1) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusRequesters[(index + 1).coerceAtMost(RECOVERY_OTP_LENGTH - 1)].requestFocus() },
                        onDone = { if (code.length == RECOVERY_OTP_LENGTH) onComplete(code) },
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}
