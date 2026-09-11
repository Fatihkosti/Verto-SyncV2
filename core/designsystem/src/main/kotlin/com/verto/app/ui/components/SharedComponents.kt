package com.verto.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.GoldDim
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSharedPrimitiveTokens
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.core.designsystem.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.ui.graphics.Shape

/** Shared visual primitives owned by :core:designsystem. */

@Composable
fun VertoLinearValueProgress(progress: Float, modifier: Modifier = Modifier) {
    val percentage by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "prog",
    )
    val shape = RoundedCornerShape(VertoSharedPrimitiveTokens.debtProgressRadius)
    Box(
        modifier
            .fillMaxWidth()
            .height(VertoSharedPrimitiveTokens.debtProgressHeight)
            .clip(shape)
            .background(BorderColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percentage)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(GoldDim, GoldPrimary))),
        )
    }
}

@Composable
fun VertoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape? = null,
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val materialCompatibility = shape != null ||
        colors != null ||
        elevation != null ||
        border != null ||
        contentPadding != null

    if (materialCompatibility) {
        val materialShape = shape ?: CardDefaults.shape
        val materialColors = colors ?: CardDefaults.cardColors()
        val materialElevation = elevation ?: CardDefaults.cardElevation()
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = modifier,
                shape = materialShape,
                colors = materialColors,
                elevation = materialElevation,
                border = border,
                content = content,
            )
        } else {
            Card(
                modifier = modifier,
                shape = materialShape,
                colors = materialColors,
                elevation = materialElevation,
                border = border,
                content = content,
            )
        }
        return
    }

    val canonicalShape = RoundedCornerShape(VertoRadius.md)
    val sizedModifier = if (onClick != null) {
        modifier.heightIn(min = VertoSize.minTouchTarget)
    } else {
        modifier
    }
    val baseModifier = sizedModifier
        .clip(canonicalShape)
        .background(BgCard)
        .border(VertoStroke.thin, BorderColor, canonicalShape)
    if (onClick != null) {
        Column(baseModifier.clickable(onClick = onClick).padding(VertoSpacing.md), content = content)
    } else {
        Column(baseModifier.padding(VertoSpacing.md), content = content)
    }
}

@Composable
fun VertoTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = VertoSize.screenHorizontalPadding,
                vertical = VertoSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(VertoSize.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription
                        ?: stringResource(R.string.verto_navigate_back),
                    tint = AccentPrimary,
                    modifier = Modifier.size(VertoSize.iconLarge),
                )
            }
            Spacer(Modifier.width(VertoSpacing.xs))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun AmountText(
    amount: Double,
    currencyLabel: String,
    color: Color = GoldPrimary,
    large: Boolean = false,
) {
    val formatted = if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        "%.2f".format(amount)
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = formatted,
            color = color,
            style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(VertoSpacing.xxs))
        Text(
            text = currencyLabel,
            color = color.copy(alpha = VertoAlpha.secondaryContent),
            style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = VertoSharedPrimitiveTokens.amountCurrencyBaselinePadding),
        )
    }
}

@Composable
fun KpiMini(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(VertoSharedPrimitiveTokens.compactCardRadius)
    Column(
        modifier
            .clip(shape)
            .background(BgCard)
            .border(VertoStroke.thin, color.copy(alpha = VertoAlpha.faintBorder), shape)
            .padding(VertoSharedPrimitiveTokens.compactCardPadding),
    ) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(VertoSpacing.xxs))
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
fun InfoChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VertoRadius.sm)
    Row(
        modifier
            .clip(shape)
            .background(BgCard)
            .border(VertoStroke.thin, BorderColor, shape)
            .padding(VertoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(VertoSize.iconSmall))
        Column {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
