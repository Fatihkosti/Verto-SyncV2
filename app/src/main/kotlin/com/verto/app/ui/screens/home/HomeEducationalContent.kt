package com.verto.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.verto.app.R
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.InfoColor
import com.verto.app.ui.theme.InfoContainer
import com.verto.app.ui.theme.TajawalFontFamily
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.feature.dashboard.api.EducationalContent

/**
 * Home keeps the card concise: summary first, then the full topic on demand.
 * The detail view starts with the topic title and full content without repeating the summary.
 */
@Composable
internal fun HomeEducationalContentCard(
    content: EducationalContent,
    onReadMore: () -> Unit,
) {
    val readMoreDescription = stringResource(R.string.home_education_read_more_description)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeDesignTokens.pendingCardHeight)
            .semantics { contentDescription = content.summary },
        color = BgCard,
        shape = RoundedCornerShape(VertoRadius.lg),
        border = BorderStroke(VertoStroke.thin, InfoColor.copy(alpha = VertoAlpha.border)),
        tonalElevation = VertoElevation.card,
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(VertoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(VertoSize.iconContainerLarge)
                    .clip(RoundedCornerShape(VertoRadius.sm))
                    .background(InfoContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = InfoColor,
                    modifier = Modifier.size(VertoSize.iconLarge),
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = content.summary,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TajawalFontFamily),
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(
                    onClick = onReadMore,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = VertoSize.minTouchTarget)
                        .semantics { contentDescription = readMoreDescription },
                ) {
                    Text(
                        text = stringResource(R.string.home_education_read_more),
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EducationalContentDialog(
    content: EducationalContent,
    onDismiss: () -> Unit,
) {
    val closeDescription = stringResource(R.string.home_education_close_description)
    HomeDialogSurface(
        onDismissRequest = onDismiss,
        fillHeight = true,
        accessibilityLabel = stringResource(R.string.home_education_dialog_description),
    ) {
        Column(modifier = Modifier.padding(VertoSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = content.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = TajawalFontFamily),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Spacer(Modifier.width(VertoSpacing.xs))
                VertoIconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(VertoSize.minTouchTarget)
                        .semantics { contentDescription = closeDescription },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = TextMuted)
                }
            }

            Spacer(Modifier.height(VertoSpacing.md))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = HomeDesignTokens.dialogScrollableMinHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = content.fullContent,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = TajawalFontFamily),
                )
                Spacer(Modifier.height(VertoSpacing.xl))
            }

            VertoPrimaryButton(
                text = stringResource(R.string.home_close),
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = closeDescription },
            )
        }
    }
}
