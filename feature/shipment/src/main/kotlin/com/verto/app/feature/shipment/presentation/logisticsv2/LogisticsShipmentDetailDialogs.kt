package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.verto.app.feature.shipment.R
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsActualTimeTarget
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalAction
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsRepackProof
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSpacing
import java.util.concurrent.TimeUnit
@Composable
internal fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            VertoOutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EtaPickerDialog(
    current: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = current ?: System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.selectedDateMillis) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_refresh)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    ) { DatePicker(state = state) }
}
@Composable
internal fun FutureLegDialog(
    initial: LogisticsShipmentLeg,
    carriers: List<com.verto.app.feature.shipment.domain.model.LogisticsPartner>,
    onDismiss: () -> Unit,
    onConfirm: (LogisticsShipmentLeg, String) -> Unit,
) {
    var leg by remember(initial.id) { mutableStateOf(initial) }
    var reason by remember(initial.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_8ee60f07fb55)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                ) {
                    LogisticsLegTransportMode.entries.filterNot { it == LogisticsLegTransportMode.UNSPECIFIED }.forEach { mode ->
                        FilterChip(
                            selected = leg.mode == mode,
                            onClick = { leg = leg.forOperationalMode(mode) },
                            label = { Text(mode.operationalLabel()) },
                        )
                    }
                }
                LogisticsCarrierPicker(
                    carriers = carriers,
                    selectedPartnerId = leg.carrierPartnerId,
                    onSelect = { carrier -> leg = leg.copy(carrierPartnerId = carrier.id) },
                )
                OperationalLegDateField(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_3dcb67169929), leg.plannedDepartureAt) {
                    leg = leg.copy(plannedDepartureAt = it)
                }
                OperationalLegDateField(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_dc74787d3f03), leg.plannedArrivalAt) {
                    leg = leg.copy(plannedArrivalAt = it)
                }
                when (leg.mode) {
                    LogisticsLegTransportMode.ROAD -> {
                        LogisticsTextField(leg.roadVehicleNumber.orEmpty(), { leg = leg.copy(roadVehicleNumber = it.nullIfBlankDetail()) }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_0e5d2d8b3d72))
                        LogisticsTextField(leg.roadDriverName.orEmpty(), { leg = leg.copy(roadDriverName = it.nullIfBlankDetail()) }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_5c241b9d45d0))
                        LogisticsTextField(
                            value = leg.roadDriverPhone.orEmpty(),
                            onValueChange = { value -> leg = leg.copy(roadDriverPhone = value.filter(Char::isDigit).nullIfBlankDetail()) },
                            label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_8e7ef1e3998d),
                            keyboardType = KeyboardType.Phone,
                        )
                    }
                    LogisticsLegTransportMode.SEA -> {
                        LogisticsTextField(leg.seaContainerNumber.orEmpty(), { leg = leg.copy(seaContainerNumber = it.nullIfBlankDetail()) }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_2fd73883bd2b))
                        LogisticsTextField(leg.seaBillOfLading.orEmpty(), { leg = leg.copy(seaBillOfLading = it.nullIfBlankDetail()) }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_4d37988cbaa4))
                        LogisticsTextField(leg.seaVesselReference.orEmpty(), { leg = leg.copy(seaVesselReference = it.nullIfBlankDetail()) }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_ba1f43e1448d))
                    }
                    LogisticsLegTransportMode.AIR -> {
                        LogisticsTextField(leg.airWaybillNumber.orEmpty(), { leg = leg.copy(airWaybillNumber = it.nullIfBlankDetail()) }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_469961810e3c))
                        LogisticsTextField(leg.airFlightReference.orEmpty(), { leg = leg.copy(airFlightReference = it.nullIfBlankDetail()) }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_df16f58349f5))
                    }
                    LogisticsLegTransportMode.UNSPECIFIED -> Unit
                }
                LogisticsTextField(leg.note, { leg = leg.copy(note = it) }, androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_note), singleLine = false)
                LogisticsTextField(reason, { reason = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_08f1a20cffc0), singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(leg, reason.trim()) }, enabled = reason.isNotBlank() && leg != initial) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationalLegDateField(label: String, value: Long?, onChange: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
        Box(modifier = Modifier.weight(1f)) {
            VertoOutlinedTextField(
                value = value?.let(::formatLogisticsDate).orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(label) },
                placeholder = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_b2c702e73c91)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.matchParentSize().clickable { open = true })
        }
        if (value != null) TextButton(onClick = { onChange(null) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_clear)) }
    }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = value ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChange(picker.selectedDateMillis); open = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
internal fun ActualCostDialog(
    detail: LogisticsShipmentDetailUi,
    title: String = "إضافة تكلفة فعلية",
    allowScope: Boolean = true,
    initial: LogisticsCostDraft = LogisticsCostDraft(),
    onDismiss: () -> Unit,
    onConfirm: (LogisticsCostDraft) -> Unit,
) {
    val initialScope = when {
        initial.sourceId != null -> "source:${initial.sourceId}"
        initial.milestoneId != null -> "milestone:${initial.milestoneId}"
        initial.legId != null -> "leg:${initial.legId}"
        else -> "shipment"
    }
    var type by remember(initial) { mutableStateOf(initial.type) }
    var amount by remember(initial) { mutableStateOf(initial.amount) }
    var currency by remember(initial) { mutableStateOf(initial.currency) }
    var exchangeRate by remember(initial) { mutableStateOf(initial.exchangeRate) }
    var description by remember(initial) { mutableStateOf(initial.note) }
    var scope by remember(initial) { mutableStateOf(initialScope) }
    val selectedMilestone = scope.takeIf { it.startsWith("milestone:") }
        ?.substringAfter(':')
        ?.let { id -> detail.aggregate.milestones.firstOrNull { it.id == id } }
    val requiresCustomsDescription = type in setOf(LogisticsCostType.CUSTOMS_DUTY, LogisticsCostType.CLEARANCE) ||
        selectedMilestone?.type == LogisticsMilestoneType.CUSTOMS
    val draft = LogisticsCostDraft(
        type = type,
        amount = amount,
        currency = currency,
        exchangeRate = exchangeRate,
        status = LogisticsCostStatus.ACTUAL,
        servicePartnerId = initial.servicePartnerId,
        sourceId = scope.takeIf { it.startsWith("source:") }?.substringAfter(':'),
        milestoneId = scope.takeIf { it.startsWith("milestone:") }?.substringAfter(':'),
        legId = scope.takeIf { it.startsWith("leg:") }?.substringAfter(':'),
        note = description.trim(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    LogisticsCostType.entries.forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option.name) })
                    }
                }
                if (allowScope) ActualCostScopeChips(detail, scope) { scope = it }
                LogisticsTextField(amount, { amount = it }, androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_amount))
                LogisticsTextField(currency, { currency = it.uppercase() }, stringResource(R.string.logistics_v232_currency))
                LogisticsTextField(exchangeRate, { exchangeRate = it }, stringResource(R.string.logistics_v232_exchange_rate))
                LogisticsTextField(
                    description,
                    { description = it },
                    if (requiresCustomsDescription) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_d2922d052404_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_d2922d052404),
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled = draft.isValid && (!requiresCustomsDescription || description.isNotBlank()),
            ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}
@Composable
private fun ActualCostScopeChips(detail: LogisticsShipmentDetailUi, scope: String, onScope: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
        FilterChip(selected = scope == "shipment", onClick = { onScope("shipment") }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_068b74354fab)) })
        detail.aggregate.sources.forEach { source ->
            val key = "source:${source.id}"
            FilterChip(selected = scope == key, onClick = { onScope(key) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_adebc50ade66, source.invoiceNumberSnapshot)) })
        }
        detail.aggregate.milestones.forEach { milestone ->
            val key = "milestone:${milestone.id}"
            FilterChip(selected = scope == key, onClick = { onScope(key) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_7c288ea25807, milestone.order + 1)) })
        }
        detail.aggregate.legs.forEach { leg ->
            val key = "leg:${leg.id}"
            FilterChip(selected = scope == key, onClick = { onScope(key) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_386141eaafe7, leg.sequence + 1)) })
        }
    }
}
@Composable
internal fun MissingGoodsRecoveryDialog(detail: LogisticsShipmentDetailUi, onDismiss: () -> Unit, onConfirm: (LogisticsRecoveryDraft) -> Unit) {
    val shortages = detail.aggregate.shortages.filter { it.quantity.remainingMissingQuantity > 0 }
    var quantities by remember(detail.aggregate.shipment.id) { mutableStateOf(shortages.associate { it.identity.id to "" }) }
    var noteOrLocation by remember { mutableStateOf("") }
    var findingCost by remember { mutableStateOf(LogisticsRecoveryCostDraft(LogisticsCostType.OTHER, description = "تكلفة العثور / الاسترداد")) }
    var transportCost by remember { mutableStateOf(LogisticsRecoveryCostDraft(LogisticsCostType.LOCAL_TRANSPORT, description = "نقل البضاعة المسترجعة")) }
    var otherCost by remember { mutableStateOf(LogisticsRecoveryCostDraft(LogisticsCostType.OTHER, description = "تكلفة استرجاع أخرى")) }
    val draft = LogisticsRecoveryDraft(shortages.map { LogisticsRecoveryLineDraft(it.identity.id, quantities[it.identity.id].orEmpty()) }, noteOrLocation, listOf(findingCost, transportCost, otherCost))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_63bc0aba83c3)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_b7fb114667a6), fontWeight = FontWeight.Bold)
            shortages.forEach { shortage ->
                val line = detail.aggregate.lines.firstOrNull { it.id == shortage.identity.shipmentLineId }
                VertoOutlinedTextField(quantities[shortage.identity.id].orEmpty(), { value -> quantities = quantities + (shortage.identity.id to value.filter(Char::isDigit)) },
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_64a8adadb431_2, line?.itemNameSnapshot ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_64a8adadb431), shortage.quantity.remainingMissingQuantity)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            LogisticsTextField(noteOrLocation, { noteOrLocation = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_14869537231a), singleLine = false)
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_36fd37a8839a), fontWeight = FontWeight.Bold)
            RecoveryCostEditor(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_5b7ffdd86ab1_2), findingCost) { findingCost = it }; RecoveryCostEditor(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_5b7ffdd86ab1), transportCost) { transportCost = it }; RecoveryCostEditor(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.logistics_contact_other), otherCost) { otherCost = it }
        }
    }, confirmButton = { TextButton({ onConfirm(draft) }, enabled = draft.isValid) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_b4bc83adff68)) } }, dismissButton = { TextButton(onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } })
}
@Composable
private fun RecoveryCostEditor(label: String, draft: LogisticsRecoveryCostDraft, onChange: (LogisticsRecoveryCostDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        VertoOutlinedTextField(draft.amount, { value -> onChange(draft.copy(amount = value.filter { it.isDigit() || it == '.' })) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (draft.amount.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                VertoOutlinedTextField(draft.currency, { onChange(draft.copy(currency = it.uppercase())) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_currency)) }, singleLine = true, modifier = Modifier.weight(1f))
                VertoOutlinedTextField(draft.exchangeRate, { value -> onChange(draft.copy(exchangeRate = value.filter { it.isDigit() || it == '.' })) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_fd829449a723)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            }
            if (label == androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.logistics_contact_other)) LogisticsTextField(draft.description, { onChange(draft.copy(description = it)) }, "وصف التكلفة *")
            FilterChip(draft.confirmPaid, { onChange(draft.copy(confirmPaid = !draft.confirmPaid)) }, label = { Text(if (draft.confirmPaid) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_44c40fe79792_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_44c40fe79792)) })
        }
    }
}
@Composable
internal fun CustodyHandoffConfirmationDialog(
    initial: LogisticsCustodyHandoffDraft,
    onDismiss: () -> Unit,
    onRepack: () -> Unit,
    onConfirm: (LogisticsCustodyHandoffDraft) -> Unit,
) {
    var draft by remember(initial.shipmentId, initial.sourceId, initial.currentPackageCount) { mutableStateOf(initial) }
    var showDifferences by remember(initial.shipmentId, initial.sourceId) { mutableStateOf(initial.isDiscrepant) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_8032a57fef6c)) },
        text = { CustodyReceiptFields(draft, showDifferences, { showDifferences = !showDifferences }, onRepack) { draft = it } },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = draft.isValid) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_78dfddd410bc)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}

@Composable
internal fun CustomsPickupDialog(
    initial: LogisticsCustomsPickupDraft,
    onDismiss: () -> Unit,
    onRepack: () -> Unit,
    onConfirm: (LogisticsCustomsPickupDraft) -> Unit,
) {
    var draft by remember(initial.shipmentId, initial.milestoneId) { mutableStateOf(initial) }
    var showDifferences by remember(initial.shipmentId, initial.milestoneId) { mutableStateOf(initial.handoff.isDiscrepant) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logistics_v231_customs_pickup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                LogisticsCarrierPicker(
                    carriers = draft.brokers, selectedPartnerId = draft.selectedBrokerId,
                    onSelect = { draft = draft.copy(selectedBrokerId = it.id) },
                    allowedRoles = setOf(LogisticsPartnerRole.CUSTOMS_BROKER),
                    label = stringResource(R.string.logistics_v231_customs_broker),
                )
                CustodyReceiptFields(
                    draft.handoff, showDifferences, { showDifferences = !showDifferences }, onRepack,
                ) { handoff -> draft = draft.copy(handoff = handoff) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = draft.isValid) {
                Text(stringResource(R.string.logistics_v231_confirm_customs_pickup))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.logistics_v230_back)) }
        },
    )
}

@Composable
private fun CustodyReceiptFields(
    draft: LogisticsCustodyHandoffDraft,
    showDifferences: Boolean,
    onToggleDifferences: () -> Unit,
    onRepack: () -> Unit,
    onChange: (LogisticsCustodyHandoffDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_f854023162ce, draft.currentPackageCount), fontWeight = FontWeight.Bold)
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_38890dc3d4cc), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onToggleDifferences) {
            Text(if (showDifferences) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_dc4a2fa77ad7_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_dc4a2fa77ad7))
        }
        if (showDifferences) {
            VertoOutlinedTextField(draft.handoverPackageCount, { value -> onChange(draft.copy(handoverPackageCount = value.filter(Char::isDigit))) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a995059cc2f2)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            VertoOutlinedTextField(draft.receivedPackageCount, { value -> onChange(draft.copy(receivedPackageCount = value.filter(Char::isDigit))) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_63cb9f260029)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            VertoOutlinedTextField(draft.receivedWeightKg, { value -> onChange(draft.copy(receivedWeightKg = value.filter { it.isDigit() || it == '.' })) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_b7fc3eb05891)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            PackageConditionFields(draft.condition) { condition -> onChange(draft.copy(condition = condition)) }
            if (draft.isDiscrepant) {
                VertoOutlinedTextField(draft.discrepancyNote, { onChange(draft.copy(discrepancyNote = it)) }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_80aec57db0a7)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            VertoOutlinedButton(onClick = onRepack, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_eafaff1b0154)) }
        }
    }
}

@Composable
private fun PackageConditionFields(
    condition: LogisticsPackageConditionDraft,
    onChange: (LogisticsPackageConditionDraft) -> Unit,
) {
    LogisticsTextField(
        value = condition.openedPackageCount,
        onValueChange = { value -> onChange(condition.copy(openedPackageCount = value.filter(Char::isDigit))) },
        label = stringResource(R.string.logistics_v231_opened_packages),
        keyboardType = KeyboardType.Number,
    )
    LogisticsTextField(
        value = condition.damagedPackageCount,
        onValueChange = { value -> onChange(condition.copy(damagedPackageCount = value.filter(Char::isDigit))) },
        label = stringResource(R.string.logistics_v231_damaged_packages),
        keyboardType = KeyboardType.Number,
    )
}

@Composable
internal fun CargoRepackDialog(
    initial: LogisticsCargoRepackDraft,
    onDismiss: () -> Unit,
    onConfirm: (LogisticsCargoRepackDraft) -> Unit,
) {
    var draft by remember(initial.shipmentId, initial.previousPackageCount) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_2c23e6e852fc)) },
        text = { CargoRepackFields(draft) { draft = it } },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = draft.isValid) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_706120a63dfd)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}
@Composable
private fun CargoRepackFields(
    draft: LogisticsCargoRepackDraft,
    onChange: (LogisticsCargoRepackDraft) -> Unit,
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            onChange(draft.copy(proof = LogisticsRepackProof(
                sourceUri = it.toString(), displayName = context.displayName(it),
                mimeType = context.contentResolver.getType(it) ?: "image/*",
            )))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5e2be18983e9, draft.previousPackageCount), fontWeight = FontWeight.Bold)
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_6d492abc2abf_2, draft.previousWeightKg?.toPlainString() ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_6d492abc2abf)))
        VertoOutlinedTextField(
            value = draft.newPackageCount,
            onValueChange = { value -> onChange(draft.copy(newCargo = draft.newCargo.copy(packageCount = value.filter(Char::isDigit)))) },
            label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_110df7f7d999)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        VertoOutlinedTextField(
            value = draft.newWeightKg,
            onValueChange = { value -> onChange(draft.copy(newCargo = draft.newCargo.copy(weightKg = value.filter { it.isDigit() || it == '.' }))) },
            label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4e86737bd0c2)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        CargoRepackReasonChips(draft, onChange)
        VertoOutlinedTextField(
            value = draft.note, onValueChange = { onChange(draft.copy(note = it)) },
            label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_79e5243b31d8)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
        VertoOutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(draft.proof?.displayName?.let { androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_d473acba9867_2, it) } ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_d473acba9867))
        }
    }
}
@Composable
private fun CargoRepackReasonChips(
    draft: LogisticsCargoRepackDraft,
    onChange: (LogisticsCargoRepackDraft) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
    ) {
        com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason.entries
            .filter { it != com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason.CORRECTION }
            .forEach { reason ->
                FilterChip(
                    selected = draft.reason == reason,
                    onClick = { onChange(draft.copy(reason = reason)) },
                    label = { Text(reason.name) },
                )
            }
    }
}
@Composable
internal fun PaidCostAdjustmentDialog(
    cost: LogisticsCost,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, currency: String, exchangeRate: String, description: String, note: String) -> Unit,
) {
    var amount by remember(cost.id, cost.amount) { mutableStateOf(cost.amount.toPlainString()) }
    var currency by remember(cost.id, cost.currency) { mutableStateOf(cost.currency) }
    var exchangeRate by remember(cost.id, cost.exchangeRateSnapshot) { mutableStateOf(cost.exchangeRateSnapshot.toPlainString()) }
    var description by remember(cost.id, cost.description) { mutableStateOf(cost.description) }
    var note by remember(cost.id, cost.note) { mutableStateOf(cost.note) }
    val valid = amount.toBigDecimalOrNull()?.signum() == 1 &&
        exchangeRate.toBigDecimalOrNull()?.signum() == 1 &&
        currency.isNotBlank() && description.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_11610ccbba9a)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_88f8434a0116_2, cost.cashPostedBaseAmount?.toPlainString() ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_88f8434a0116)),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                LogisticsTextField(amount, { amount = it }, androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_amount))
                LogisticsTextField(currency, { currency = it.uppercase() }, stringResource(R.string.logistics_v232_currency))
                LogisticsTextField(exchangeRate, { exchangeRate = it }, stringResource(R.string.logistics_v232_exchange_rate))
                LogisticsTextField(description, { description = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_415aa4e2c3b4), singleLine = false)
                LogisticsTextField(note, { note = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_5681b5571444), singleLine = false)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amount, currency.trim(), exchangeRate, description.trim(), note.trim()) },
                enabled = valid,
            ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_769b801ced6c)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}
@Composable
internal fun FinalReceivingQuestionDialog(
    onDismiss: () -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_ae4c8f56a1b8)) },
        text = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_370f5d2c2b04)) },
        confirmButton = {
            VertoButton(onClick = onYes) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d045bef8e536)) }
        },
        dismissButton = {
            VertoOutlinedButton(onClick = onNo) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5c528d9fa396)) }
        },
    )
}

@Composable
internal fun PermanentDeleteShipmentDialog(
    shipmentNumber: String,
    isDraft: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (confirmationShipmentNumber: String?, reason: String) -> Unit,
) {
    var confirmation by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val valid = isDraft || (confirmation.trim() == shipmentNumber && reason.trim().isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isDraft) R.string.logistics_v232_delete_draft else R.string.logistics_v232_delete_exception_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                if (isDraft) {
                    Text(stringResource(R.string.logistics_v232_delete_draft_message))
                } else {
                    Text(stringResource(R.string.logistics_v232_delete_executed_message))
                    LogisticsTextField(reason, { reason = it }, stringResource(R.string.logistics_v232_delete_reason), singleLine = false)
                    LogisticsTextField(confirmation, { confirmation = it }, stringResource(R.string.logistics_v232_delete_confirm_number, shipmentNumber))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (isDraft) null else confirmation.trim(), reason.trim()) }, enabled = valid) {
                Text(stringResource(R.string.logistics_v232_delete_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.logistics_v230_back)) } },
    )
}

@Composable
internal fun ShortageSettlementDialog(
    detail: LogisticsShipmentDetailUi,
    type: com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType,
    onDismiss: () -> Unit,
    onConfirm: (LogisticsShortageSettlementDraft) -> Unit,
) {
    val shortages = detail.aggregate.shortages.filter { it.quantity.remainingMissingQuantity > 0 }
    var selectedId by remember(detail.aggregate.shipment.id, type) { mutableStateOf(shortages.firstOrNull()?.identity?.id.orEmpty()) }
    val selected = shortages.firstOrNull { it.identity.id == selectedId }
    var quantity by remember(selectedId, type) { mutableStateOf("") }
    var amount by remember(type) { mutableStateOf("") }
    var currency by remember(type) { mutableStateOf("SDG") }
    var exchangeRate by remember(type) { mutableStateOf("1") }
    var note by remember(type) { mutableStateOf("") }
    val draft = LogisticsShortageSettlementDraft(
        shortageId = selectedId,
        type = type,
        quantity = quantity.toIntOrNull() ?: 0,
        compensationAmount = amount,
        currency = currency,
        exchangeRate = exchangeRate,
        note = note,
    )
    val max = selected?.quantity?.remainingMissingQuantity ?: 0
    val valid = draft.isValid && draft.quantity <= max
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (type == com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType.COMPENSATED) R.string.logistics_v232_compensation_title else R.string.logistics_v232_final_loss_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                shortages.forEach { shortage ->
                    val line = detail.aggregate.lines.firstOrNull { it.id == shortage.identity.shipmentLineId }
                    FilterChip(
                        selected = selectedId == shortage.identity.id,
                        onClick = { selectedId = shortage.identity.id },
                        label = { Text(stringResource(R.string.logistics_v232_shortage_item_remaining, line?.itemNameSnapshot ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_7ceda9df539d), shortage.quantity.remainingMissingQuantity)) },
                    )
                }
                LogisticsTextField(quantity, { quantity = it.filter(Char::isDigit) }, stringResource(R.string.logistics_v232_quantity_required))
                if (type == com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType.COMPENSATED) {
                    LogisticsTextField(amount, { amount = it }, stringResource(R.string.logistics_v232_compensation_amount))
                    LogisticsTextField(currency, { currency = it.uppercase() }, stringResource(R.string.logistics_v232_currency))
                    LogisticsTextField(exchangeRate, { exchangeRate = it }, stringResource(R.string.logistics_v232_exchange_rate))
                }
                LogisticsTextField(note, { note = it }, stringResource(R.string.logistics_v232_note), singleLine = false)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = valid) { Text(stringResource(R.string.logistics_v232_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.logistics_v230_back)) } },
    )
}
