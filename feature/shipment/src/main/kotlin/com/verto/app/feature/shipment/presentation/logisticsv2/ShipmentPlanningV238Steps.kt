package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedButton

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlanDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDurationUnit
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoInlineStatus
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoSize

internal data class LogisticsV238CustomsDocumentActions(
    val onStage: (sourceUri: String, displayName: String, mimeType: String, target: PlanningDocumentTarget) -> Unit,
    val onRetry: (draftId: String) -> Unit,
    val onRemove: (draftId: String) -> Unit,
    val onOpen: (draftId: String) -> Unit,
    val onOpenSaved: (LogisticsCustomsPlanDocument) -> Unit,
    val onDeleteSaved: (LogisticsCustomsPlanDocument) -> Unit,
)

private fun com.verto.app.feature.shipment.domain.model.LogisticsMilestone.v238StationLabel(): String =
    city.trim().ifBlank {
        location.toLogisticsPlanningPlace().city.trim().ifBlank { placeName.trim().ifBlank { location.trim() } }
    }

/** v238 §5.6 — customs is a planning event after a real station, never a route milestone. */
@Composable
internal fun ShipmentV238CustomsStep(
    workspace: LogisticsRouteWorkspaceSnapshot,
    canManage: Boolean,
    validationAttempt: Int,
    suggestions: List<String>,
    savedDocuments: List<LogisticsCustomsPlanDocument>,
    documentActions: LogisticsV238CustomsDocumentActions,
    onWorkspaceChange: ((LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot) -> Unit,
) {
    val validation = workspace.v238CustomsValidation()
    val stations = workspace.milestones
        .filter { it.type != LogisticsMilestoneType.CUSTOMS && it.type != LogisticsMilestoneType.DESTINATION }
        .sortedBy { it.order }
    val context = LocalContext.current
    val customsPending = workspace.pendingDocuments.filter { it.milestoneId == V238_CUSTOMS_DOCUMENT_TARGET }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            documentActions.onStage(
                it.toString(),
                context.displayName(it),
                context.contentResolver.getType(it) ?: "application/octet-stream",
                PlanningDocumentTarget(milestoneId = V238_CUSTOMS_DOCUMENT_TARGET),
            )
        }
    }
    var durationText by rememberSaveable(workspace.shipmentId, workspace.customsExpectedDurationMinutes) {
        mutableStateOf(workspace.v238CustomsDurationValue()?.toString().orEmpty())
    }
    var durationUnit by rememberSaveable(workspace.shipmentId) {
        mutableStateOf(workspace.v238CustomsDurationUnit())
    }

    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.md)) {
        VertoFormCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(LogisticsV2Tokens.customsHeaderIconSize))
                Spacer(Modifier.width(VertoSpacing.xs))
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_fef51c6aa0cc), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            V238SuggestionField(
                value = workspace.customsCheckpointName,
                onValueChange = { value -> onWorkspaceChange { it.copy(customsCheckpointName = value) } },
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_f95389f389af),
                placeholder = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_dec33e573a83),
                suggestions = suggestions.filter { it.isNotBlank() && !it.equals(workspace.customsCheckpointName, true) },
                enabled = canManage,
                isError = validationAttempt > 0 && validation.checkpointError != null,
                errorText = validation.checkpointError,
            )
            if (suggestions.isNotEmpty()) {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5af808e995c1), color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            V238Selector(
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_95dc80ac3ca3),
                value = stations.firstOrNull { it.id == workspace.customsAfterStationId }?.v238StationLabel().orEmpty(),
                values = stations.map { it.v238StationLabel() },
                enabled = canManage,
                onSelect = { selected ->
                    stations.firstOrNull { it.v238StationLabel() == selected }?.let { station ->
                        onWorkspaceChange { it.copy(customsAfterStationId = station.id) }
                    }
                },
            )
            if (validationAttempt > 0 && validation.afterStationError != null) {
                Text(validation.afterStationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm)) {
                Box(Modifier.weight(1f)) {
                    VertoTextField(
                        value = durationText,
                        onValueChange = { raw ->
                            durationText = raw.filter(Char::isDigit)
                            onWorkspaceChange { it.withV238CustomsDuration(durationText.toIntOrNull(), durationUnit) }
                        },
                        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_951f2657209c),
                        keyboardType = KeyboardType.Number,
                        enabled = canManage,
                        isRequired = true,
                        isError = validationAttempt > 0 && validation.durationError != null,
                        errorText = validation.durationError.takeIf { validationAttempt > 0 },
                    )
                }
                Box(Modifier.weight(1f)) {
                    V238Selector(
                        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_445014919981),
                        value = if (durationUnit == LogisticsDurationUnit.DAYS) "أيام" else "ساعات",
                        values = listOf("أيام", "ساعات"),
                        enabled = canManage,
                        onSelect = { selected ->
                            durationUnit = if (selected == "أيام") LogisticsDurationUnit.DAYS else LogisticsDurationUnit.HOURS
                            onWorkspaceChange { it.withV238CustomsDuration(durationText.toIntOrNull(), durationUnit) }
                        },
                    )
                }
            }
            VertoInlineStatus(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_7fbf36801303), VertoStatusTone.Info)

            VertoOutlinedButton(
                onClick = {
                    picker.launch(arrayOf(
                        "image/*", "application/pdf", "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/markdown", "video/mp4",
                    ))
                },
                enabled = canManage,
                modifier = Modifier.fillMaxWidth().heightIn(min = LogisticsV2Tokens.documentUploadMinHeight),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPrimary),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(VertoSpacing.xs))
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a3879c146f2f))
            }

            savedDocuments.forEach { document ->
                CustomsSavedDocumentRow(
                    document = document,
                    onOpen = { documentActions.onOpenSaved(document) },
                    onDelete = { documentActions.onDeleteSaved(document) },
                    canDelete = canManage,
                )
            }
            customsPending.forEach { pending ->
                when {
                    pending.isStaging -> {
                        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_133fd7c79b46, pending.displayName), color = TextMuted)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    pending.errorMessage != null -> {
                        VertoInlineStatus(pending.errorMessage, VertoStatusTone.Error)
                        Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                            TextButton(onClick = { documentActions.onRetry(pending.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_retry)) }
                            TextButton(onClick = { documentActions.onRemove(pending.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
                        }
                    }
                    pending.isReady -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(pending.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { documentActions.onOpen(pending.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open)) }
                            TextButton(onClick = { documentActions.onRemove(pending.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
                        }
                        Text(v238FileSizeLabel(pending.sizeBytes), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (validationAttempt > 0 && validation.documentError != null) {
                VertoInlineStatus(validation.documentError, VertoStatusTone.Error)
            }
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_7ef1c7c2c011), color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CustomsSavedDocumentRow(
    document: LogisticsCustomsPlanDocument,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(document.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onOpen) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open)) }
        TextButton(onClick = onDelete, enabled = canDelete) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
    }
}

/** v238 §5.7 — review omits country identity and inserts customs as an event between stations. */
@Composable
internal fun ShipmentV238Review(
    shipmentNumber: String,
    draft: LogisticsPlanningDraft,
    workspace: LogisticsRouteWorkspaceSnapshot,
    invoiceOptions: List<LogisticsPurchaseInvoiceOptionUi>,
    issues: List<LogisticsV238ReviewIssue>,
    onEdit: (LogisticsV238ReviewTarget) -> Unit,
) {
    val ordered = workspace.milestones.filter { it.type != LogisticsMilestoneType.CUSTOMS }.sortedBy { it.order }
    val supplierCount = draft.sources.map { it.supplierId }.distinct().size
    val invoiceCount = draft.sources.map { it.invoiceId }.distinct().size
    val tripLabel = if (workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED) {
        "مختلطة"
    } else {
        val mode = when (workspace.unifiedTransportMode) {
            LogisticsLegTransportMode.ROAD -> "بري"
            LogisticsLegTransportMode.SEA -> "بحري"
            LogisticsLegTransportMode.AIR -> "جوي"
            LogisticsLegTransportMode.UNSPECIFIED -> "غير محدد"
        }
        "ثابتة • $mode"
    }
    val afterStation = ordered.firstOrNull { it.id == workspace.customsAfterStationId }?.v238StationLabel().orEmpty()
    val customsDuration = v238DurationLabel(workspace.customsExpectedDurationMinutes)

    VertoFormCard {
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_354353e3f91c), style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
        ReviewRow("الشحنة", "#$shipmentNumber") { onEdit(LogisticsV238ReviewTarget.BASICS) }
        Divider(color = BorderColor)
        ReviewRow("الموردون والفواتير", "$supplierCount مورد • $invoiceCount فاتورة") { onEdit(LogisticsV238ReviewTarget.PURCHASE) }
        Divider(color = BorderColor)
        ReviewRow("نوع الرحلة", tripLabel) { onEdit(LogisticsV238ReviewTarget.TRIP_TYPE) }
        Divider(color = BorderColor)
        ReviewRow("المسار", ordered.joinToString(" ← ") { it.v238StationLabel() }) { onEdit(LogisticsV238ReviewTarget.ROUTE) }
        Divider(color = BorderColor)
        ReviewRow("الجمارك", "بعد $afterStation • $customsDuration") { onEdit(LogisticsV238ReviewTarget.CUSTOMS) }

        Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
            ordered.forEachIndexed { index, station ->
                TimelineStationRow(
                    title = station.v238StationLabel(),
                    subtitle = when (station.type) {
                        LogisticsMilestoneType.ORIGIN -> "نقطة البداية"
                        LogisticsMilestoneType.DESTINATION -> "نقطة الوصول"
                        else -> "محطة وسيطة"
                    },
                    terminal = index == ordered.lastIndex,
                )
                if (station.id == workspace.customsAfterStationId) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = VertoSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(LogisticsV2Tokens.reviewCustomsMarkerSize).background(AccentPrimary, CircleShape))
                        Spacer(Modifier.width(VertoSpacing.xs))
                        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a94b33228bad, workspace.customsCheckpointName, customsDuration), color = AccentPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                workspace.legs.firstOrNull { it.fromMilestoneId == station.id }?.let { leg ->
                    val mode = when (leg.mode) {
                        LogisticsLegTransportMode.ROAD -> "بري"
                        LogisticsLegTransportMode.SEA -> "بحري"
                        LogisticsLegTransportMode.AIR -> "جوي"
                        LogisticsLegTransportMode.UNSPECIFIED -> "غير محدد"
                    }
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_88511a0322c3, v238DurationLabel(leg.expectedTransitMinutes ?: leg.expectedTransitDays?.times(24 * 60)), mode),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = VertoSpacing.lg),
                    )
                }
            }
        }
        VertoInlineStatus(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_02c61ef39207), VertoStatusTone.Success)
    }

    if (issues.isNotEmpty()) {
        VertoFormCard {
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d4885d543550), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            issues.distinctBy { it.target to it.message }.forEach { issue ->
                VertoOutlinedButton(onClick = { onEdit(issue.target) }, modifier = Modifier.fillMaxWidth()) {
                    Text(issue.message, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit))
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, modifier = Modifier.weight(0.38f))
        Text(value, color = TextPrimary, modifier = Modifier.weight(0.52f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onEdit, modifier = Modifier.weight(0.10f)) {
            Icon(Icons.Outlined.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_c2b61c89d4e4, label))
        }
    }
}

@Composable
private fun TimelineStationRow(title: String, subtitle: String, terminal: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(if (terminal) LogisticsV2Tokens.reviewTerminalDotSize else LogisticsV2Tokens.reviewStationDotSize).background(AccentPrimary, CircleShape))
        }
        Spacer(Modifier.width(VertoSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun V238SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    suggestions: List<String>,
    enabled: Boolean,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var menu by remember(value, suggestions) { mutableStateOf(false) }
    val filtered = suggestions.filter { it.contains(value.trim(), ignoreCase = true) }.take(5)
    Box {
        VertoTextField(
            value = value,
            onValueChange = { onValueChange(it); menu = it.isNotBlank() },
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            isRequired = true,
            isError = isError,
            errorText = errorText.takeIf { isError },
        )
        DropdownMenu(expanded = enabled && menu && filtered.isNotEmpty(), onDismissRequest = { menu = false }) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(text = { Text(suggestion) }, onClick = { onValueChange(suggestion); menu = false })
            }
        }
    }
}

@Composable
private fun V238Selector(
    label: String,
    value: String,
    values: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        VertoOutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = VertoSize.minTouchTarget),
        ) { Text(value.ifBlank { androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_92c0910ef0f4) }, modifier = Modifier.weight(1f)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.distinct().forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

private fun v238FileSizeLabel(size: Long?): String {
    val value = size ?: return ""
    return when {
        value >= 1024L * 1024L -> "%.1f MB".format(value.toDouble() / (1024.0 * 1024.0))
        value >= 1024L -> "%.1f KB".format(value.toDouble() / 1024.0)
        else -> "$value B"
    }
}

internal fun v238DurationLabel(minutes: Int?): String {
    val value = minutes ?: return "غير محدد"
    return if (value % (24 * 60) == 0) {
        val days = value / (24 * 60)
        if (days == 1) "يوم واحد" else "$days أيام"
    } else {
        val hours = value / 60
        if (hours == 1) "ساعة واحدة" else "$hours ساعات"
    }
}
