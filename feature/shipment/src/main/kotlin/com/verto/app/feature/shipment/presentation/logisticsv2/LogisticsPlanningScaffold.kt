package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.verto.app.feature.shipment.R
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke

enum class LogisticsPlanningStep { BASICS, PURCHASE, ROUTE, CUSTOMS, REVIEW }

private val LogisticsPlanningStep.detailIndex: Int?
    get() = when (this) {
        LogisticsPlanningStep.BASICS -> null
        LogisticsPlanningStep.PURCHASE -> 1
        LogisticsPlanningStep.ROUTE -> 3
        LogisticsPlanningStep.CUSTOMS -> 5
        LogisticsPlanningStep.REVIEW -> 6
    }

@Composable
internal fun LogisticsPlanningScaffold(
    shipmentNumber: String,
    step: LogisticsPlanningStep,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextLabel: String = stringResource(R.string.logistics_v230_next),
    backEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    detailsIndexOverride: Int? = null,
    contentScrollState: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedScrollState = contentScrollState ?: rememberScrollState()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize().background(BgDeep),
            containerColor = BgDeep,
            topBar = {
                Surface(color = BgCard) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        VertoTopBar(
                            title = stringResource(R.string.logistics_v230_planning_title),
                            onBack = onBack,
                        )
                    }
                }
            },
            bottomBar = {
                Surface(color = BgCard) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = VertoSize.screenHorizontalPadding, vertical = VertoSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VertoSecondaryButton(
                            text = stringResource(R.string.logistics_v230_back),
                            onClick = onBack,
                            enabled = backEnabled,
                            modifier = Modifier.weight(1f),
                        )
                        VertoPrimaryButton(
                            text = nextLabel,
                            onClick = onNext,
                            enabled = nextEnabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = LogisticsV2Tokens.contentMaxWidth)
                        .fillMaxWidth()
                        .verticalScroll(resolvedScrollState)
                        .padding(horizontal = VertoSize.screenHorizontalPadding, vertical = VertoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
                ) {
                    LogisticsPlanningProgress(
                        step = step,
                        detailsIndexOverride = detailsIndexOverride,
                        modifier = Modifier.semantics {
                            contentDescription = buildString {
                                append("تخطيط الشحنة ")
                                append(shipmentNumber)
                                append(". ")
                                append(if (step == LogisticsPlanningStep.BASICS) "تعريف الشحنة 1 من 2" else "تفاصيل الشحنة ${detailsIndexOverride ?: step.detailIndex} من 6")
                            }
                        },
                    )
                    content()
                    // Safe scroll tail so IME and fixed actions never cover the last field.
                    Box(Modifier.fillMaxWidth().height(VertoSpacing.xl))
                }
            }
        }
    }
}

@Composable
private fun LogisticsPlanningProgress(
    step: LogisticsPlanningStep,
    detailsIndexOverride: Int? = null,
    modifier: Modifier = Modifier,
) {
    val detailsActive = step != LogisticsPlanningStep.BASICS
    val detailsIndex = detailsIndexOverride ?: step.detailIndex
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlanningProgressNode(number = 1, active = !detailsActive, completed = detailsActive)
            Box(
                Modifier
                    .weight(1f)
                    .height(VertoStroke.progress)
                    .background(if (detailsActive) AccentPrimary else BorderColor),
            )
            PlanningProgressNode(number = 2, active = detailsActive, completed = false)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            ProgressLabel(
                title = stringResource(R.string.logistics_v230_definition),
                counter = if (!detailsActive) stringResource(R.string.logistics_v235_definition_progress) else null,
                active = !detailsActive,
                modifier = Modifier.weight(1f),
            )
            ProgressLabel(
                title = stringResource(R.string.logistics_v235_details_title),
                counter = detailsIndex?.let { stringResource(R.string.logistics_v235_details_progress, it) },
                active = detailsActive,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlanningProgressNode(number: Int, active: Boolean, completed: Boolean) {
    val filled = active || completed
    Box(
        modifier = Modifier
            .size(LogisticsV2Tokens.planningProgressNodeSize)
            .background(if (filled) AccentPrimary else BgCard, CircleShape)
            .border(VertoStroke.thin, if (filled) AccentPrimary else BorderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (completed) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(VertoSize.iconMedium))
        } else {
            Text(
                text = number.toString(),
                color = if (filled) Color.White else TextMuted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProgressLabel(
    title: String,
    counter: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
        Text(
            text = title,
            color = if (active) AccentPrimary else TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
        counter?.let {
            Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
