package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoAdaptiveTokens
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke

internal data class InvoicePlanningCardActions(
    val onRemove: () -> Unit,
    val onPackageCountChange: (String) -> Unit,
    val onWeightChange: (String) -> Unit,
    val onReadyDateClick: () -> Unit,
)


@Composable
internal fun SelectedInvoicePlanningCard(
    source: LogisticsShipmentSource,
    canManage: Boolean,
    showValidation: Boolean,
    actions: InvoicePlanningCardActions,
) {
    val validation = source.v236Validation()
    var editMetric by rememberSaveable(source.invoiceId) { mutableStateOf<String?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BgCard,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(VertoStroke.thin, if (showValidation && !validation.isValid) ErrorColor else BorderColor),
    ) {
        Column(Modifier.padding(VertoSpacing.sm), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
            InvoiceCardHeader(
                source = source,
                canManage = canManage,
                onRemove = actions.onRemove,
                onEditCartons = { editMetric = "CARTONS" },
                onEditWeight = { editMetric = "WEIGHT" },
            )
            if (showValidation) InvoiceMetricValidation(validation)
            InvoiceReadyDateField(
                source = source,
                canManage = canManage,
                showValidation = showValidation,
                validation = validation,
                onClick = actions.onReadyDateClick,
            )
        }
    }
    editMetric?.let { metric ->
        InvoicePlanningMetricDialog(
            title = if (metric == androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_5f8c576edfcd)) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_299b4fce2cc4) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e4b4486abca0),
            initialValue = if (metric == "CARTONS") source.plannedPackageCount?.toString().orEmpty()
                else source.plannedWeightKg?.stripTrailingZeros()?.toPlainString().orEmpty(),
            decimal = metric == "WEIGHT",
            onDismiss = { editMetric = null },
            onConfirm = { value ->
                if (metric == "CARTONS") actions.onPackageCountChange(value) else actions.onWeightChange(value)
                editMetric = null
            },
        )
    }
}

@Composable
private fun InvoiceCardHeader(
    source: LogisticsShipmentSource,
    canManage: Boolean,
    onRemove: () -> Unit,
    onEditCartons: () -> Unit,
    onEditWeight: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = VertoAdaptiveTokens.shouldStackInlineContent(maxWidth, LocalDensity.current.fontScale)
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
                InvoiceSelectionTitle(source, canManage, onRemove, Modifier.fillMaxWidth())
                InvoiceMetricButton(
                    Icons.Outlined.Inventory2, "${source.plannedPackageCount ?: "—"} كرتونة", canManage, onEditCartons,
                )
                InvoiceMetricButton(
                    Icons.Outlined.Scale,
                    "${source.plannedWeightKg?.stripTrailingZeros()?.toPlainString() ?: "—"} كجم",
                    canManage,
                    onEditWeight,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xxs),
            ) {
                InvoiceSelectionTitle(source, canManage, onRemove, Modifier.weight(1f))
                InvoiceMetricButton(
                    icon = Icons.Outlined.Inventory2,
                    text = "${source.plannedPackageCount ?: "—"} كرتونة",
                    enabled = canManage,
                    onClick = onEditCartons,
                )
                InvoiceMetricButton(
                    icon = Icons.Outlined.Scale,
                    text = "${source.plannedWeightKg?.stripTrailingZeros()?.toPlainString() ?: "—"} كجم",
                    enabled = canManage,
                    onClick = onEditWeight,
                )
            }
        }
    }
}

@Composable
private fun InvoiceSelectionTitle(
    source: LogisticsShipmentSource,
    canManage: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val checkboxDescription = androidx.compose.ui.res.stringResource(
        com.verto.app.feature.shipment.R.string.shipment_ds_923281913301,
        source.invoiceNumberSnapshot,
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = true,
            onCheckedChange = { checked -> if (!checked && canManage) onRemove() },
            enabled = canManage,
            modifier = Modifier.semantics {
                contentDescription = checkboxDescription
            },
        )
        Text(
            androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a8d9bb06f339, source.invoiceNumberSnapshot),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InvoiceMetricButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(VertoSize.iconSmall))
        Spacer(Modifier.width(VertoSpacing.xxs))
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun InvoiceMetricValidation(validation: LogisticsPurchaseInvoiceValidation) {
    val errors = listOfNotNull(validation.packageCountError, validation.weightError)
    if (errors.isNotEmpty()) {
        Text(errors.joinToString(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_7bf258c9aa82)), color = ErrorColor, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InvoiceReadyDateField(
    source: LogisticsShipmentSource,
    canManage: Boolean,
    showValidation: Boolean,
    validation: LogisticsPurchaseInvoiceValidation,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
        VertoOutlinedButton(
            onClick = onClick,
            enabled = canManage,
            modifier = Modifier.fillMaxWidth().heightIn(min = VertoSize.minTouchTarget),
        ) {
            Text(
                source.expectedReadyAt?.let { androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_fb0ea839ba23, formatLogisticsDate(it)) }
                    ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_abb8310187fd),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(VertoSpacing.xs))
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(VertoSize.iconMedium))
        }
        if (showValidation && validation.readyDateError != null) {
            Text(validation.readyDateError, color = ErrorColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}
