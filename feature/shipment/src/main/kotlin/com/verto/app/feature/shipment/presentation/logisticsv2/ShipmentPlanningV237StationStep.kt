package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedButton

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verto.app.feature.shipment.domain.model.LogisticsDurationUnit
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoInlineStatus
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing

internal data class LogisticsV237StationDocumentActions(
    val onStage: (sourceUri: String, displayName: String, mimeType: String, target: PlanningDocumentTarget) -> Unit,
    val onRetry: (draftId: String) -> Unit,
    val onRemove: (draftId: String) -> Unit,
    val onOpen: (draftId: String) -> Unit,
)

/** v237 §5.5 — all values below are planned defaults only; no execution-side write is performed here. */
@Composable
internal fun ShipmentV237StationDetails(
    draft: LogisticsPlanningDraft,
    workspace: LogisticsRouteWorkspaceSnapshot,
    milestoneId: String,
    canManage: Boolean,
    validationAttempt: Int,
    stationNameSuggestions: List<String>,
    documentActions: LogisticsV237StationDocumentActions,
    onWorkspaceChange: ((LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot) -> Unit,
) {
    val ordered = workspace.milestones.sortedBy { it.order }
    val target = ordered.firstOrNull { it.id == milestoneId } ?: return
    val leg = workspace.v237LegForTarget(milestoneId) ?: return
    val sequenceNumber = leg.sequence + 1
    val targetCount = workspace.legs.size
    val isDestination = target.type == LogisticsMilestoneType.DESTINATION
    val validation = leg.v237Validation(target, workspace.routeTransportPlanKind)
    val context = LocalContext.current
    val pendingProof = workspace.pendingDocuments.lastOrNull { it.legId == leg.id }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            documentActions.onStage(
                it.toString(),
                context.displayName(it),
                context.contentResolver.getType(it) ?: "application/octet-stream",
                PlanningDocumentTarget(legId = leg.id),
            )
        }
    }

    var packageText by rememberSaveable(leg.id) { mutableStateOf(leg.plannedPackageCount?.toString().orEmpty()) }
    var weightText by rememberSaveable(leg.id) { mutableStateOf(leg.plannedWeightKg?.toPlainString().orEmpty()) }
    var phoneText by rememberSaveable(leg.id) { mutableStateOf(leg.plannedRepresentativePhoneSnapshot.orEmpty()) }
    var amountText by rememberSaveable(leg.id) { mutableStateOf(leg.plannedCost?.amount?.toPlainString().orEmpty()) }
    var exchangeText by rememberSaveable(leg.id) { mutableStateOf(leg.plannedCost?.exchangeRate?.takeUnless { leg.plannedCost?.currency == LogisticsCurrencyPolicy.BASE_CURRENCY }?.toPlainString().orEmpty()) }
    var durationText by rememberSaveable(leg.id) { mutableStateOf(leg.v237DurationValue()?.toString().orEmpty()) }
    var currency by rememberSaveable(leg.id) { mutableStateOf(leg.plannedCost?.currency ?: LogisticsCurrencyPolicy.BASE_CURRENCY) }
    val durationUnit = leg.v237DurationUnit()
    val currencies = remember(currency) { (listOf("SDG", "USD", "EUR", "SAR", "AED", "EGP", "CNY") + currency).distinct() }
    val carrierSuggestions = remember(draft.carriers) { draft.carriers.distinctBy { it.id } }

    fun updateLeg(transform: (LogisticsShipmentLeg) -> LogisticsShipmentLeg) {
        onWorkspaceChange { current -> current.withV237Leg(leg.id, transform) }
    }
    fun updateCost(nextAmount: String = amountText, nextCurrency: String = currency, nextExchange: String = exchangeText) {
        val cost = v237PlannedCost(nextAmount, nextCurrency, nextExchange)
        updateLeg { it.copy(plannedCost = cost) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.md)) {
        VertoFormCard {
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_2567b12394b5, sequenceNumber), style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            if (targetCount > 1) Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_507477d79706, sequenceNumber, targetCount), color = TextMuted, style = MaterialTheme.typography.bodySmall)

            if (isDestination) {
                VertoTextField(value = target.location, onValueChange = {}, label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4aec1d4bc481), readOnly = true, enabled = false)
            } else {
                V237SuggestionField(
                    value = target.location,
                    onValueChange = { name -> onWorkspaceChange { it.withV237StationName(target.id, name) } },
                    label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4aec1d4bc481),
                    placeholder = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_387cca30ac74),
                    suggestions = (stationNameSuggestions + ordered.map { it.location })
                        .filter { it.isNotBlank() && !it.equals(target.location, true) }
                        .distinct(),
                    enabled = canManage,
                    isError = validationAttempt > 0 && validation.stationNameError != null,
                    errorText = validation.stationNameError,
                )
            }

            if (workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED) {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4ed9ad8ff4ed), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    V237TransportChoice("بري", Icons.Outlined.LocalShipping, LogisticsLegTransportMode.ROAD, leg.mode, true, canManage, Modifier.weight(1f)) { updateLeg { it.copy(mode = LogisticsLegTransportMode.ROAD) } }
                    V237TransportChoice("بحري", Icons.Outlined.DirectionsBoat, LogisticsLegTransportMode.SEA, leg.mode, true, canManage, Modifier.weight(1f)) { updateLeg { it.copy(mode = LogisticsLegTransportMode.SEA) } }
                    V237TransportChoice("جوي", Icons.Outlined.Flight, LogisticsLegTransportMode.AIR, leg.mode, true, canManage, Modifier.weight(1f)) { updateLeg { it.copy(mode = LogisticsLegTransportMode.AIR) } }
                }
                if (workspace.unifiedTransportModeSelected) {
                    val previousModeLabel = when (workspace.unifiedTransportMode) {
                        LogisticsLegTransportMode.ROAD -> "بري"
                        LogisticsLegTransportMode.SEA -> "بحري"
                        LogisticsLegTransportMode.AIR -> "جوي"
                        LogisticsLegTransportMode.UNSPECIFIED -> null
                    }
                    previousModeLabel?.let {
                        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0ef2f3260962, it), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (validationAttempt > 0 && validation.transportModeError != null) Text(validation.transportModeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            V237CarrierFreeField(
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_00604056d35b),
                placeholder = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a10eed993005),
                value = leg.plannedCarrierNameSnapshot.orEmpty(),
                carriers = carrierSuggestions,
                enabled = canManage,
                onValueChange = { name -> updateLeg { it.copy(plannedCarrierPartnerId = null, plannedCarrierNameSnapshot = name) } },
                onCarrierSelected = { partner ->
                    phoneText = v237NormalizePhone(partner.representativePhone ?: partner.phone.orEmpty())
                    updateLeg {
                        it.copy(
                            plannedCarrierPartnerId = partner.id,
                            plannedCarrierNameSnapshot = partner.name,
                            plannedRepresentativeNameSnapshot = partner.representativeName,
                            plannedRepresentativePhoneSnapshot = phoneText.ifBlank { null },
                        )
                    }
                },
            )
            V237SuggestionField(
                value = leg.plannedRepresentativeNameSnapshot.orEmpty(),
                onValueChange = { value -> updateLeg { it.copy(plannedRepresentativeNameSnapshot = value.ifBlank { null }) } },
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e4641409a1fc),
                placeholder = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_f026e08b371f),
                suggestions = carrierSuggestions.mapNotNull { it.representativeName }.filter(String::isNotBlank).distinct(),
                enabled = canManage,
                onSuggestion = { chosen ->
                    val partner = carrierSuggestions.firstOrNull { it.representativeName == chosen }
                    val suggestedPhone = v237NormalizePhone(partner?.representativePhone ?: partner?.phone.orEmpty())
                    if (suggestedPhone.isNotBlank()) phoneText = suggestedPhone
                    updateLeg {
                        it.copy(
                            plannedRepresentativeNameSnapshot = chosen,
                            plannedRepresentativePhoneSnapshot = suggestedPhone.ifBlank { it.plannedRepresentativePhoneSnapshot },
                        )
                    }
                },
            )
            VertoTextField(
                value = phoneText,
                onValueChange = { raw ->
                    phoneText = raw.filter { it.isDigit() || it == '+' || it.isWhitespace() }
                    updateLeg { it.copy(plannedRepresentativePhoneSnapshot = v237NormalizePhone(phoneText).ifBlank { null }) }
                },
                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number),
                placeholder = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_ffc655bf3309),
                keyboardType = KeyboardType.Phone,
                enabled = canManage,
                isError = validationAttempt > 0 && validation.phoneError != null,
                errorText = validation.phoneError.takeIf { validationAttempt > 0 },
                isOptional = true,
            )
            VertoTextField(
                value = packageText,
                onValueChange = { raw ->
                    packageText = raw.filter(Char::isDigit)
                    updateLeg { it.copy(plannedPackageCount = packageText.toIntOrNull()) }
                },
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_299b4fce2cc4),
                keyboardType = KeyboardType.Number,
                enabled = canManage,
                isRequired = true,
                isError = validationAttempt > 0 && validation.packageCountError != null,
                errorText = validation.packageCountError.takeIf { validationAttempt > 0 },
            )
            VertoTextField(
                value = weightText,
                onValueChange = { raw ->
                    weightText = v237DecimalInput(raw)
                    updateLeg { it.copy(plannedWeightKg = weightText.toBigDecimalOrNull()) }
                },
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e4b4486abca0),
                keyboardType = KeyboardType.Decimal,
                enabled = canManage,
                isRequired = true,
                isError = validationAttempt > 0 && validation.weightError != null,
                errorText = validation.weightError.takeIf { validationAttempt > 0 },
            )
        }

        VertoFormCard {
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_1d4e3efaadfd), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            VertoTextField(
                value = amountText,
                onValueChange = { raw -> amountText = v237DecimalInput(raw); updateCost(nextAmount = amountText) },
                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_amount),
                keyboardType = KeyboardType.Decimal,
                enabled = canManage,
                isError = validationAttempt > 0 && validation.amountError != null,
                errorText = validation.amountError.takeIf { validationAttempt > 0 },
                isOptional = true,
            )
            V237Selector(
                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_currency),
                value = currency,
                values = currencies,
                enabled = canManage,
                onSelect = { selected ->
                    currency = selected
                    updateCost(nextCurrency = selected)
                },
            )
            if (currency != LogisticsCurrencyPolicy.BASE_CURRENCY) {
                VertoTextField(
                    value = exchangeText,
                    onValueChange = { raw -> exchangeText = v237DecimalInput(raw); updateCost(nextExchange = exchangeText) },
                    label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_fd829449a723),
                    keyboardType = KeyboardType.Decimal,
                    enabled = canManage,
                    isRequired = amountText.isNotBlank(),
                    isError = validationAttempt > 0 && validation.exchangeRateError != null,
                    errorText = validation.exchangeRateError.takeIf { validationAttempt > 0 },
                )
            }
            leg.plannedCost?.takeIf { it.exchangeRate.signum() > 0 }?.let {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_eea522667a22, it.baseCurrencyAmount.stripTrailingZeros().toPlainString(), LogisticsCurrencyPolicy.BASE_CURRENCY), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        VertoFormCard {
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_ab82ad3d39a2), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            VertoOutlinedButton(
                onClick = { picker.launch(arrayOf("image/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/markdown", "video/mp4")) },
                enabled = canManage && pendingProof?.isStaging != true,
                modifier = Modifier.fillMaxWidth().heightIn(min = LogisticsV2Tokens.stationProofUploadMinHeight),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPrimary),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(VertoSpacing.xs))
                Text(if (pendingProof == null) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_1f1c9321f939_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_1f1c9321f939))
            }
            when {
                pendingProof?.isStaging == true -> {
                    Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_133fd7c79b46, pendingProof.displayName), color = TextMuted)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                pendingProof?.errorMessage != null -> {
                    VertoInlineStatus(pendingProof.errorMessage, VertoStatusTone.Error)
                    Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                        TextButton(onClick = { documentActions.onRetry(pendingProof.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_retry)) }
                        TextButton(onClick = { documentActions.onRemove(pendingProof.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
                    }
                }
                pendingProof?.isReady == true -> {
                    Text(pendingProof.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                    Text(v237FileSizeLabel(pendingProof.sizeBytes), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                        TextButton(onClick = { documentActions.onOpen(pendingProof.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open)) }
                        TextButton(onClick = { documentActions.onRemove(pendingProof.draftId) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
                    }
                }
            }
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_2fbc0d175aa9), color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }

        VertoFormCard {
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_75fbcc858dc5), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            VertoTextField(
                value = durationText,
                onValueChange = { raw ->
                    durationText = raw.filter(Char::isDigit)
                    updateLeg { it.withV237Duration(durationText.toIntOrNull(), durationUnit) }
                },
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_fbd816bd4cfb),
                keyboardType = KeyboardType.Number,
                enabled = canManage,
                isRequired = true,
                supportingText = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4e8f34a69755),
                isError = validationAttempt > 0 && validation.durationError != null,
                errorText = validation.durationError.takeIf { validationAttempt > 0 },
            )
            V237Selector(
                label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_445014919981),
                value = if (durationUnit == LogisticsDurationUnit.HOURS) "ساعات" else "أيام",
                values = listOf("ساعات", "أيام"),
                enabled = canManage,
                onSelect = { selected ->
                    val next = if (selected == "أيام") LogisticsDurationUnit.DAYS else LogisticsDurationUnit.HOURS
                    updateLeg { it.withV237Duration(durationText.toIntOrNull(), next) }
                },
            )
        }
    }
}

@Composable
private fun V237SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    suggestions: List<String>,
    enabled: Boolean,
    onSuggestion: (String) -> Unit = onValueChange,
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
            isError = isError,
            errorText = errorText.takeIf { isError },
            isOptional = !isError && label != "اسم المحطة",
        )
        DropdownMenu(expanded = enabled && menu && filtered.isNotEmpty(), onDismissRequest = { menu = false }) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(text = { Text(suggestion) }, onClick = { onSuggestion(suggestion); menu = false })
            }
        }
    }
}

@Composable
private fun V237CarrierFreeField(
    label: String,
    placeholder: String,
    value: String,
    carriers: List<LogisticsPartner>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onCarrierSelected: (LogisticsPartner) -> Unit,
) {
    var menu by remember(value, carriers) { mutableStateOf(false) }
    val filtered = carriers.filter { it.name.contains(value.trim(), ignoreCase = true) }.take(5)
    Box {
        VertoTextField(
            value = value,
            onValueChange = { onValueChange(it); menu = it.isNotBlank() },
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            isOptional = true,
        )
        DropdownMenu(expanded = enabled && menu && filtered.isNotEmpty(), onDismissRequest = { menu = false }) {
            filtered.forEach { carrier ->
                DropdownMenuItem(text = { Text(carrier.name) }, onClick = { onCarrierSelected(carrier); menu = false })
            }
        }
    }
}

@Composable
private fun V237Selector(
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
        ) { Text(value, modifier = Modifier.weight(1f)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false }) }
        }
    }
}

private fun v237FileSizeLabel(size: Long?): String {
    val value = size ?: return ""
    return when {
        value >= 1024L * 1024L -> "%.1f MB".format(value.toDouble() / (1024.0 * 1024.0))
        value >= 1024L -> "%.1f KB".format(value.toDouble() / 1024.0)
        else -> "$value B"
    }
}
