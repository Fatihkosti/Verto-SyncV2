package com.verto.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.R
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.feature.dashboard.api.PendingActionDetails

@Composable
internal fun PendingActionDetailsDialog(
    details: PendingActionDetails,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(details.copyText) { mutableStateOf(false) }
    val dialogDescription = stringResource(R.string.home_details_dialog_description)
    val copyDescription = stringResource(R.string.home_details_copy_description)
    val closeDescription = stringResource(R.string.home_details_close_description)

    HomeDialogSurface(
        onDismissRequest = onDismiss,
        fillHeight = true,
        accessibilityLabel = dialogDescription,
    ) {
        Column(modifier = Modifier.padding(VertoSpacing.lg)) {
            Text(
                text = details.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(VertoSpacing.md))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            ) {
                details.rows.forEachIndexed { index, row ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = BgCard,
                        shape = RoundedCornerShape(VertoRadius.md),
                        border = BorderStroke(VertoStroke.thin, BorderColor),
                    ) {
                        Column(
                            modifier = Modifier.padding(VertoSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                        ) {
                            Text(
                                text = row.title,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            row.fields.forEach { field ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = field.label,
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = field.value,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    if (index != details.rows.lastIndex) {
                        HorizontalDivider(color = BorderColor)
                    }
                }
                Spacer(Modifier.height(VertoSpacing.sm))
            }

            Spacer(Modifier.height(VertoSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            ) {
                details.copyText?.let { copyText ->
                    VertoSecondaryButton(
                        text = if (copied) {
                            stringResource(R.string.home_details_copied)
                        } else {
                            stringResource(R.string.home_details_copy)
                        },
                        onClick = {
                            clipboard.setText(AnnotatedString(copyText))
                            copied = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = copyDescription },
                    )
                }
                VertoPrimaryButton(
                    text = stringResource(R.string.home_close),
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = closeDescription },
                )
            }
        }
    }
}
