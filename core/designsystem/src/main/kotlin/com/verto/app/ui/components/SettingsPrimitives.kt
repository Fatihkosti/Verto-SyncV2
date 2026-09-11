package com.verto.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSettingsTokens
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke

/** Shared settings primitives owned exclusively by :core:designsystem. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(VertoSettingsTokens.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BgCard)
            .border(VertoStroke.thin, BorderColor, shape),
        content = content,
    )
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = TextMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(
            start = VertoSettingsTokens.sectionStartPadding,
            top = VertoSettingsTokens.sectionTopPadding,
            bottom = VertoSettingsTokens.sectionBottomPadding,
        ),
    )
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = BorderColor,
        modifier = Modifier.padding(start = VertoSettingsTokens.dividerStartInset),
    )
}

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    iconTint: Color = AccentPrimary,
    titleColor: Color = TextPrimary,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = VertoSize.minTouchTarget)
            .padding(
                horizontal = VertoSpacing.md,
                vertical = VertoSettingsTokens.rowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(VertoSize.iconMedium),
        )
        Spacer(Modifier.width(VertoSettingsTokens.rowIconGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(VertoSize.iconSmall),
        )
    }
}

@Composable
fun DialogTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        leadingIcon = leadingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        colors = dialogFieldColors(),
    )
}

@Composable
fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentPrimary,
    unfocusedBorderColor = BorderColor,
    focusedContainerColor = BgDeep,
    unfocusedContainerColor = BgDeep,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = AccentPrimary,
    unfocusedLabelColor = TextMuted,
)
