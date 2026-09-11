package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.verto.app.feature.shipment.application.model.ShipmentCardPhase
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoLoadingState
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.components.VertoStatusBanner
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun LogisticsV2Screen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BgDeep)
                .safeDrawingPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = LogisticsV2Tokens.contentMaxWidth)
                    .fillMaxSize(),
            ) {
                VertoTopBar(title = title, onBack = onBack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VertoSize.screenHorizontalPadding, vertical = VertoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
                ) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    content()
                    Spacer(Modifier.height(VertoSpacing.xl))
                }
            }
        }
    }
}

@Composable
internal fun LogisticsSection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    VertoFormCard(
        modifier = modifier,
        contentPadding = PaddingValues(VertoSpacing.md),
        contentSpacing = VertoSpacing.sm,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            if (!trailing.isNullOrBlank()) Text(trailing, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        content()
    }
}

@Composable
internal fun LogisticsMetricCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = LogisticsV2Tokens.metricMinWidth),
        color = BgCard,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(VertoStroke.thin, BorderColor),
    ) {
        Column(Modifier.padding(VertoSpacing.md), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
internal fun LogisticsShipmentCard(
    shipment: LogisticsShipmentCardUi,
    access: LogisticsV2Access,
    onOpen: () -> Unit,
    onAction: (LogisticsNextAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = BgCard,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(VertoStroke.thin, BorderColor),
    ) {
        Column(Modifier.padding(VertoSpacing.md), verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(shipment.shipmentNumber, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_c443a6158b22, shipment.sourceLocation, shipment.destinationLocation), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                LogisticsStatusChip(shipment.stateLabel, if (shipment.delayed) ErrorColor else AccentPrimary)
            }
            LogisticsShipmentCardPhaseContent(shipment)
            if (shipment.nextAction != LogisticsNextAction.NONE && actionAllowed(access, shipment.nextAction)) {
                VertoPrimaryButton(
                    text = shipment.nextAction.label,
                    onClick = { onAction(shipment.nextAction) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}


@Composable
private fun LogisticsShipmentCardPhaseContent(shipment: LogisticsShipmentCardUi) {
    when (shipment.phase) {
        ShipmentCardPhase.BEFORE_MOVEMENT -> {
            if (shipment.suppliers.isNotEmpty()) LogisticsLabeledValue("الموردون", shipment.suppliers.joinToString("، "))
            shipment.currentPackageCount?.let { LogisticsLabeledValue("الكراتين/الطرود الحالية", it.toString()) }
            shipment.currentWeightKg?.let { LogisticsLabeledValue("الوزن الحالي", "${it.toPlainString()} كجم") }
            shipment.expectedDepartureAt?.let { LogisticsLabeledValue("المغادرة المتوقعة", formatLogisticsDate(it)) }
            shipment.assigneeName?.let { LogisticsLabeledValue("المسؤول", it) }
        }
        ShipmentCardPhase.IN_MOVEMENT -> {
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_fd5861131c7e), color = TextMuted, style = MaterialTheme.typography.labelMedium)
            Text(shipment.primaryLocation ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_38413cd52f46), color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_bb767596ef7b), color = TextMuted, style = MaterialTheme.typography.labelMedium)
            Text(shipment.primaryCustodian ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_cd1659aa012d), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            shipment.currentLegLabel?.let { LogisticsLabeledValue("المرحلة الحالية", it) }
            shipment.movementLabel?.let { LogisticsLabeledValue("الوضع", it) }
            shipment.carrierName?.let { LogisticsLabeledValue("الناقل", it) }
            shipment.currentPackageCount?.let { LogisticsLabeledValue("الكراتين/الطرود", it.toString()) }
            shipment.currentWeightKg?.let { LogisticsLabeledValue("الوزن", "${it.toPlainString()} كجم") }
            shipment.expectedArrivalAt?.let { LogisticsLabeledValue("الوصول المتوقع", formatLogisticsDate(it)) }
            shipment.expectedRemainingMillis?.let { LogisticsLabeledValue("المدة المتبقية المتوقعة", logisticsDurationLabel(it)) }
            shipment.lastUpdateAt?.let { LogisticsLabeledValue("آخر تحديث", formatLogisticsDate(it)) }
            if (shipment.delayed) LogisticsStatusChip("متأخرة", ErrorColor)
            if (shipment.atCustoms) LogisticsStatusChip("عند الجمارك", AccentPrimary)
        }
        ShipmentCardPhase.CLOSED -> {
            shipment.actualArrivalAt?.let { LogisticsLabeledValue("الوصول الفعلي", formatLogisticsDate(it)) }
            LogisticsLabeledValue("المقبول / الناقص", "${shipment.acceptedQuantity} / ${shipment.missingQuantity}")
            LogisticsLabeledValue("إجمالي تكلفة اللوجستيات", shipment.totalLogisticsCost.toPlainString())
            LogisticsLabeledValue("قيمة المخزون النهائية", shipment.finalInventoryValue.toPlainString())
            if (shipment.hasRecovery) LogisticsStatusChip("يوجد استرداد لبضاعة ناقصة", SuccessColor)
        }
    }
}

@Composable
internal fun LogisticsStatusChip(text: String, accent: Color) {
    Surface(shape = CircleShape, color = accent.copy(alpha = 0.10f), border = BorderStroke(VertoStroke.thin, accent.copy(alpha = 0.28f))) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = VertoSpacing.sm, vertical = VertoSpacing.xs),
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun LogisticsLabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LogisticsV2Tokens.compactValueGap)) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
        Text(bidiIsolate(value.ifBlank { androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_7f68e27edb59) }), color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

internal fun bidiIsolate(value: String): String = "\u2068$value\u2069"

@Composable
internal fun LogisticsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    isError: Boolean = false,
    errorText: String? = null,
) {
    val focusManager = LocalFocusManager.current
    VertoTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        singleLine = singleLine,
        supportingText = supportingText,
        keyboardType = keyboardType,
        imeAction = imeAction,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { focusManager.clearFocus() },
        ),
        isError = isError,
        errorText = errorText?.takeIf { isError },
    )
}

@Composable
fun LogisticsPermissionDenied() {
    VertoStatusBanner(
        title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_1861e329f963),
        message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_642903c1008c),
        tone = VertoStatusTone.Permission,
    )
}

@Composable
fun LogisticsLoading() {
    VertoLoadingState(message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_38b7f99dc6e1))
}

@Composable
fun LogisticsError(message: String, onRetry: (() -> Unit)? = null) {
    VertoStatusBanner(
        title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e0465a89fde9),
        message = message,
        tone = VertoStatusTone.Error,
        actionLabel = if (onRetry != null) androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_retry) else null,
        onAction = onRetry,
    )
}

@Composable
internal fun LogisticsEmpty(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    VertoEmptyState(message = message, title = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_no_data), actionLabel = actionLabel, onAction = onAction)
}

internal fun formatLogisticsDate(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(epochMillis))

internal fun logisticsDurationLabel(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val days = TimeUnit.MILLISECONDS.toDays(safe)
    if (days > 0L) return "$days يوم"
    val hours = TimeUnit.MILLISECONDS.toHours(safe)
    if (hours > 0L) return "$hours ساعة"
    return "أقل من ساعة"
}
