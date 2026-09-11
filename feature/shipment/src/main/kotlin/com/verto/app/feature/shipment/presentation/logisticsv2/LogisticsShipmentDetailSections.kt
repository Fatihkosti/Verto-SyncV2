package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.verto.app.feature.shipment.R
import com.verto.app.feature.shipment.application.model.ShipmentTimelineItemKind
import com.verto.app.feature.shipment.application.model.ShipmentTimelineReadItem
import com.verto.app.feature.shipment.application.model.ShipmentTimelineState
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsActualTimeTarget
import com.verto.app.feature.shipment.domain.model.LogisticsPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalAction
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import java.util.concurrent.TimeUnit

@Composable
internal fun LogisticsDocumentRow(
    document: LogisticsDocument,
    canDelete: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
        if (document.mimeType.startsWith("image/")) {
            AsyncImage(
                model = document.privateUri,
                contentDescription = document.displayName,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                Text(document.displayName)
            }
            if (canDelete) {
                TextButton(onClick = onDelete, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
            }
        }
        Text(
            if (document.costId != null) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_f077baf57b99) else scopeLabel(document.sourceId, document.milestoneId, document.legId),
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun CostVarianceRows(
    detail: LogisticsShipmentDetailUi,
    viewModel: LogisticsV2ViewModel = hiltViewModel(),
) {
    ClosedWithShortageBadge(detail, viewModel); val costs = detail.aggregate.costs
    val shipmentId = detail.aggregate.shipment.id
    val canManage = viewModel.currentAccess().canManage &&
        detail.aggregate.shipment.state != LogisticsShipmentState.CANCELLED
    val busy = viewModel.operationState.value.isWorking
    val context = LocalContext.current
    var proofCostId by remember { mutableStateOf<String?>(null) }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val costId = proofCostId
        proofCostId = null
        if (uri != null && costId != null) {
            viewModel.saveCostProof(
                shipmentId = shipmentId,
                costId = costId,
                sourceUri = uri.toString(),
                displayName = context.displayName(uri),
                mimeType = context.contentResolver.getType(uri) ?: "image/*",
            )
        }
    }

    var editingCostId by remember { mutableStateOf<String?>(null) }
    val scopeKeys = costs.map { costScopeKey(it.sourceId, it.milestoneId, it.legId) }.distinct()
    scopeKeys.forEach { key ->
        val scoped = costs.filter { costScopeKey(it.sourceId, it.milestoneId, it.legId) == key }
        val estimated = scoped.filter { it.status == LogisticsCostStatus.ESTIMATED }.sumOf { it.baseCurrencyAmount }
        val actual = scoped.filter { it.status == LogisticsCostStatus.ACTUAL }.sumOf { it.baseCurrencyAmount }
        LogisticsLabeledValue(scopeName(detail, key), "متوقع: $estimated • فعلي: $actual • الفرق: ${actual.subtract(estimated)}")
        scoped.filter { it.status == LogisticsCostStatus.ACTUAL }.forEach { cost ->
            LogisticsPaidCostRow(
                cost = cost,
                canManage = canManage,
                busy = busy,
                onPay = { viewModel.confirmCostPayment(shipmentId, it) },
                onEdit = { editingCostId = it },
                onProof = { proofCostId = it; proofPicker.launch("image/*") },
            )
        }
    }

    LateCostControls(detail, shipmentId, canManage, busy, viewModel)
    PaidCostEditor(costs, editingCostId, { editingCostId = it }, shipmentId, viewModel)

}

@Composable
private fun LateCostControls(
    detail: LogisticsShipmentDetailUi, shipmentId: String, canManage: Boolean, busy: Boolean, viewModel: LogisticsV2ViewModel,
) {
    var showLateCost by remember { mutableStateOf(false) }
    if (detail.aggregate.shipment.state == LogisticsShipmentState.CLOSED && canManage) {
        VertoSecondaryButton(
            text = stringResource(R.string.logistics_v232_late_cost_add), onClick = { showLateCost = true },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showLateCost) {
        ActualCostDialog(
            detail = detail, title = stringResource(R.string.logistics_v232_late_cost_title), allowScope = false,
            onDismiss = { showLateCost = false },
            onConfirm = { draft -> showLateCost = false; viewModel.addLateCost(shipmentId, draft) },
        )
    }
}

@Composable
private fun PaidCostEditor(
    costs: List<com.verto.app.feature.shipment.domain.model.LogisticsCost>, editingCostId: String?,
    onEditingCostId: (String?) -> Unit, shipmentId: String, viewModel: LogisticsV2ViewModel,
) {
    val editingCost = costs.firstOrNull { it.id == editingCostId } ?: return
    PaidCostAdjustmentDialog(
        cost = editingCost,
        onDismiss = { onEditingCostId(null) },
        onConfirm = { amount, currency, exchangeRate, description, note ->
            viewModel.adjustPaidCost(
                shipmentId, editingCost.id, LogisticsPaidCostEditDraft(amount, currency, exchangeRate, description, note),
            )
            onEditingCostId(null)
        },
    )
}

@Composable
private fun ClosedWithShortageBadge(detail: LogisticsShipmentDetailUi, viewModel: LogisticsV2ViewModel) {
    val remainingMissing = detail.aggregate.shortages.sumOf { it.quantity.remainingMissingQuantity }
    var showRecovery by remember(detail.aggregate.shipment.id) { mutableStateOf(false) }
    var settlementType by remember(detail.aggregate.shipment.id) { mutableStateOf<com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType?>(null) }
    if (detail.aggregate.shipment.state == LogisticsShipmentState.CLOSED && remainingMissing > 0) {
        LogisticsStatusChip("مغلقة مع نقص", MaterialTheme.colorScheme.error)
        LogisticsLabeledValue(stringResource(R.string.logistics_v232_shortage_quantity), remainingMissing.toString())
        if (viewModel.currentAccess().canManage) {
            VertoButton(
                onClick = { showRecovery = true },
                enabled = !viewModel.operationState.value.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.logistics_v232_shortage_found)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                VertoSecondaryButton(
                    text = stringResource(R.string.logistics_v232_shortage_compensate),
                    onClick = { settlementType = com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType.COMPENSATED },
                    enabled = !viewModel.operationState.value.isWorking,
                    modifier = Modifier.weight(1f),
                )
                VertoSecondaryButton(
                    text = stringResource(R.string.logistics_v232_shortage_final_loss),
                    onClick = { settlementType = com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType.FINAL_LOSS },
                    enabled = !viewModel.operationState.value.isWorking,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (showRecovery) {
        MissingGoodsRecoveryDialog(
            detail = detail,
            onDismiss = { showRecovery = false },
            onConfirm = { draft ->
                viewModel.recoverMissingGoods(detail.aggregate.shipment.id, draft)
                showRecovery = false
            },
        )
    }
    settlementType?.let { type ->
        ShortageSettlementDialog(
            detail = detail,
            type = type,
            onDismiss = { settlementType = null },
            onConfirm = { draft ->
                settlementType = null
                viewModel.settleShortage(detail.aggregate.shipment.id, draft)
            },
        )
    }
}

@Composable
private fun LogisticsPaidCostRow(
    cost: LogisticsCost,
    canManage: Boolean,
    busy: Boolean,
    onPay: (String) -> Unit,
    onEdit: (String) -> Unit,
    onProof: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = VertoSpacing.xxs), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
        Text(cost.description.ifBlank { cost.type.name }, color = TextPrimary, fontWeight = FontWeight.Medium)
        Text(
            androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_7e9a9265a782, cost.amount.toPlainString(), cost.currency, cost.exchangeRateSnapshot.toPlainString(), cost.baseCurrencyAmount.toPlainString(), cost.paymentState.name),
            color = TextSecondary, style = MaterialTheme.typography.bodySmall,
        )
        cost.cashPostedBaseAmount?.let { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_219025144d39, it.toPlainString()), color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        if (!canManage) return@Column
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
            when (cost.paymentState) {
                LogisticsCostPaymentState.UNPAID, LogisticsCostPaymentState.POSTING -> VertoOutlinedButton(
                    onClick = { onPay(cost.id) }, enabled = !busy,
                ) { Text(if (cost.paymentState == LogisticsCostPaymentState.POSTING) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_1a6e22fc0cdd_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_1a6e22fc0cdd)) }
                LogisticsCostPaymentState.PAID, LogisticsCostPaymentState.ADJUSTING -> VertoOutlinedButton(
                    onClick = { onEdit(cost.id) }, enabled = !busy,
                ) { Text(if (cost.paymentState == LogisticsCostPaymentState.ADJUSTING) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_b62b9c6862c3_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_b62b9c6862c3)) }
                LogisticsCostPaymentState.REVERSED -> Unit
            }
            if (cost.paymentState in setOf(LogisticsCostPaymentState.PAID, LogisticsCostPaymentState.ADJUSTING)) {
                VertoOutlinedButton(onClick = { onProof(cost.id) }, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_68382ae08fc5)) }
            }
        }
    }
}

internal fun costScopeKey(sourceId: String?, milestoneId: String?, legId: String?): String = when {
    sourceId != null -> "source:$sourceId"
    milestoneId != null -> "milestone:$milestoneId"
    legId != null -> "leg:$legId"
    else -> "shipment"
}

internal fun scopeName(detail: LogisticsShipmentDetailUi, key: String): String = when {
    key == "shipment" -> "الشحنة"
    key.startsWith("source:") -> detail.aggregate.sources.firstOrNull { it.id == key.substringAfter(':') }
        ?.let { "فاتورة #${it.invoiceNumberSnapshot}" } ?: "فاتورة"
    key.startsWith("milestone:") -> detail.aggregate.milestones.firstOrNull { it.id == key.substringAfter(':') }
        ?.let { "محطة ${it.location}" } ?: "محطة"
    key.startsWith("leg:") -> detail.aggregate.legs.firstOrNull { it.id == key.substringAfter(':') }
        ?.let { "مرحلة ${it.sequence + 1}" } ?: "مرحلة"
    else -> key
}

internal fun scopeLabel(sourceId: String?, milestoneId: String?, legId: String?): String = when {
    sourceId != null -> "مرتبط بفاتورة"
    milestoneId != null -> "مرتبط بمحطة"
    legId != null -> "مرتبط بمرحلة نقل"
    else -> "مستوى الشحنة"
}


@Composable
internal fun LogisticsJourneyTimelineRow(
    item: ShipmentTimelineReadItem,
    viewModel: LogisticsV2ViewModel = hiltViewModel(),
) {
    val accent = when (item.state) {
        ShipmentTimelineState.COMPLETED -> SuccessColor
        ShipmentTimelineState.ACTIVE -> AccentPrimary
        ShipmentTimelineState.PLANNED -> BorderColor
        ShipmentTimelineState.SUPERSEDED, ShipmentTimelineState.CANCELLED -> TextMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = VertoSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(LogisticsV2Tokens.timelineDot),
            shape = CircleShape,
            color = accent,
            border = BorderStroke(VertoStroke.thin, BorderColor),
        ) {}
        LogisticsJourneyTimelineContent(item, accent, viewModel)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LogisticsJourneyTimelineContent(
    item: ShipmentTimelineReadItem,
    accent: androidx.compose.ui.graphics.Color,
    viewModel: LogisticsV2ViewModel,
) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LogisticsV2Tokens.compactValueGap)) {
        Text(
            item.title,
            color = if (item.state == ShipmentTimelineState.SUPERSEDED) TextMuted else TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textDecoration = if (item.state == ShipmentTimelineState.SUPERSEDED) TextDecoration.LineThrough else null,
        )
        val stateLabel = when (item.state) {
            ShipmentTimelineState.COMPLETED -> "مكتمل ✓"
            ShipmentTimelineState.ACTIVE -> "الحالي ●"
            ShipmentTimelineState.PLANNED -> "مخطط ○"
            ShipmentTimelineState.SUPERSEDED -> "تم تغييره / مستبدل"
            ShipmentTimelineState.CANCELLED -> "ملغى"
        }
        val kindLabel = if (item.kind == ShipmentTimelineItemKind.LEG) "مرحلة نقل" else "محطة"
        val unplanned = if (item.planKind == LogisticsPlanKind.UNPLANNED) " • غير مخططة" else ""
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_94264ea5b813, kindLabel, stateLabel, unplanned), color = accent, style = MaterialTheme.typography.labelMedium)
        Text(item.detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        val timing = item.occurredAt?.let { "فعلي: ${formatLogisticsDate(it)}" }
            ?: item.plannedAt?.let { "مخطط: ${formatLogisticsDate(it)}" } ?: "لا يوجد وقت مسجل"
        Text(timing, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        LogisticsTimelineRepackAction(item, viewModel)
    }
}

@Composable
private fun LogisticsTimelineRepackAction(item: ShipmentTimelineReadItem, viewModel: LogisticsV2ViewModel) {
    if (item.kind != ShipmentTimelineItemKind.MILESTONE) return
    val detail = (viewModel.detailState.value as? LogisticsDetailUiState.Content)?.detail
    val shipmentId = detail?.takeIf {
        it.currentMilestone?.id == item.id &&
            it.aggregate.shipment.state !in setOf(LogisticsShipmentState.CLOSED, LogisticsShipmentState.CANCELLED) &&
            viewModel.currentAccess().canManage
    }?.aggregate?.shipment?.id ?: return
    VertoOutlinedButton(
        onClick = { viewModel.requestCargoRepack(shipmentId) },
        enabled = !viewModel.operationState.value.isWorking,
    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_2c23e6e852fc)) }
}

internal fun actionAllowed(access: LogisticsV2Access, action: LogisticsNextAction): Boolean = when (action) {
    LogisticsNextAction.START_RECEIVING,
    LogisticsNextAction.RECEIVE_ANOTHER_BATCH -> access.canConfirm
    LogisticsNextAction.NONE -> false
    else -> access.canManage
}

internal fun statusEtaLeg(detail: LogisticsShipmentDetailUi): LogisticsShipmentLeg? {
    val activeId = detail.operationalStatus?.activeLegId
    return detail.aggregate.legs.firstOrNull { it.id == activeId }
        ?: detail.aggregate.legs.filter { it.status == LogisticsLegStatus.PLANNED }.minByOrNull { it.sequence }
}

internal fun delayLabel(delayMillis: Long): String {
    if (delayMillis <= 0L) return "في الموعد"
    val days = TimeUnit.MILLISECONDS.toDays(delayMillis)
    if (days > 0L) return "متأخرة $days يوم"
    val hours = TimeUnit.MILLISECONDS.toHours(delayMillis).coerceAtLeast(1L)
    return "متأخرة $hours ساعة"
}

@Composable
internal fun UnplannedStationDialog(
    activeLeg: LogisticsShipmentLeg,
    countryContext: LogisticsMilestone?,
    carriers: List<LogisticsPartner>,
    onDismiss: () -> Unit,
    onConfirm: (LogisticsUnplannedStationDraft) -> Unit,
) {
    val countryCode = countryContext?.countryCode.orEmpty()
    val countryName = countryContext?.countryNameSnapshot.orEmpty()
    var city by remember { mutableStateOf("") }
    var placeName by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var stationType by remember { mutableStateOf(LogisticsMilestoneType.TRANSIT) }
    var carrierId by remember(activeLeg.id) { mutableStateOf(activeLeg.carrierPartnerId) }
    var mode by remember(activeLeg.id) { mutableStateOf(activeLeg.mode) }
    val draft = LogisticsUnplannedStationDraft(
        LogisticsUnplannedLocationDraft(countryCode.trim().uppercase(), countryName.trim(), city.trim(), placeName.trim()),
        stationType, reason, carrierId, mode,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_80770297e283)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    listOf(LogisticsMilestoneType.TRANSIT, LogisticsMilestoneType.CUSTOMS).forEach { type ->
                        FilterChip(stationType == type, { stationType = type }, label = { Text(if (type == LogisticsMilestoneType.CUSTOMS) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_075ded8e5c71_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_075ded8e5c71)) })
                    }
                }
                LogisticsTextField(city, { city = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.logistics_v230_city))
                LogisticsTextField(placeName, { placeName = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_cdee73211c04))
                LogisticsTextField(reason, { reason = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_5f4487c3e837), singleLine = false)
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_2e48595d179a), fontWeight = FontWeight.Bold)
                LogisticsCarrierPicker(
                    carriers = carriers,
                    selectedPartnerId = carrierId,
                    onSelect = { carrier -> carrierId = carrier.id },
                )
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_109984e3f8cf), fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    LogisticsLegTransportMode.entries.filterNot { it == LogisticsLegTransportMode.UNSPECIFIED }.forEach { option -> FilterChip(mode == option, { mode = option }, label = { Text(option.operationalLabel()) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = draft.isValid) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}

@Composable
internal fun ActualTimeCorrectionDialog(
    milestones: List<LogisticsMilestone>,
    onDismiss: () -> Unit,
    onConfirm: (LogisticsActualTimeCorrectionDraft) -> Unit,
) {
    val candidates = milestones.filter { it.arrivedAt != null || it.departedAt != null }.sortedByDescending { it.order }
    val initial = candidates.firstOrNull() ?: run { onDismiss(); return }
    var milestoneId by remember { mutableStateOf(initial.id) }
    val selected = candidates.first { it.id == milestoneId }
    val defaultTarget = if (selected.departedAt != null) LogisticsActualTimeTarget.MILESTONE_DEPARTURE else LogisticsActualTimeTarget.MILESTONE_ARRIVAL
    var target by remember(milestoneId) { mutableStateOf(defaultTarget) }
    val current = when (target) {
        LogisticsActualTimeTarget.MILESTONE_ARRIVAL -> selected.arrivedAt
        LogisticsActualTimeTarget.MILESTONE_DEPARTURE -> selected.departedAt
    } ?: 0L
    var correctedAt by remember(milestoneId, target) { mutableStateOf(current) }
    var reason by remember { mutableStateOf("") }
    val draft = LogisticsActualTimeCorrectionDraft(milestoneId, target, correctedAt, reason)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_394d02919c4d)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    candidates.forEach { milestone ->
                        FilterChip(milestoneId == milestone.id, { milestoneId = milestone.id }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_ca3bd3fc29ed, milestone.order + 1, milestone.placeName.ifBlank { milestone.location })) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    if (selected.arrivedAt != null) FilterChip(target == LogisticsActualTimeTarget.MILESTONE_ARRIVAL, { target = LogisticsActualTimeTarget.MILESTONE_ARRIVAL }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_91d61fffe1d0)) })
                    if (selected.departedAt != null) FilterChip(target == LogisticsActualTimeTarget.MILESTONE_DEPARTURE, { target = LogisticsActualTimeTarget.MILESTONE_DEPARTURE }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_df38fc90f7f4)) })
                }
                OperationalLegDateField(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_c7991b610401), correctedAt) { value ->
                    if (value != null) correctedAt = value + Math.floorMod(correctedAt, 86_400_000L)
                }
                LogisticsTextField(reason, { reason = it }, androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_0382f116d927), singleLine = false)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = draft.isValid) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0a6889cb3f9b)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) } },
    )
}

internal fun Context.dial(phone: String) {
    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
}

internal fun Context.openLogisticsWhatsApp(phone: String, message: String) {
    val normalized = phone.filter(Char::isDigit)
    if (normalized.isBlank()) return
    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$normalized&text=${Uri.encode(message)}")
    startActivity(Intent(Intent.ACTION_VIEW, uri))
}

internal fun LogisticsShipmentLeg.forOperationalMode(mode: LogisticsLegTransportMode): LogisticsShipmentLeg = when (mode) {
    LogisticsLegTransportMode.ROAD -> copy(
        mode = mode,
        seaContainerNumber = null,
        seaBillOfLading = null,
        seaVesselReference = null,
        airWaybillNumber = null,
        airFlightReference = null,
    )
    LogisticsLegTransportMode.SEA -> copy(
        mode = mode,
        roadVehicleNumber = null,
        roadDriverName = null,
        roadDriverPhone = null,
        airWaybillNumber = null,
        airFlightReference = null,
    )
    LogisticsLegTransportMode.AIR -> copy(
        mode = mode,
        roadVehicleNumber = null,
        roadDriverName = null,
        roadDriverPhone = null,
        seaContainerNumber = null,
        seaBillOfLading = null,
        seaVesselReference = null,
    )
    LogisticsLegTransportMode.UNSPECIFIED -> copy(
        mode = mode,
        roadVehicleNumber = null,
        roadDriverName = null,
        roadDriverPhone = null,
        seaContainerNumber = null,
        seaBillOfLading = null,
        seaVesselReference = null,
        airWaybillNumber = null,
        airFlightReference = null,
    )
}

internal fun LogisticsLegTransportMode.operationalLabel(): String = when (this) {
    LogisticsLegTransportMode.ROAD -> "بري"
    LogisticsLegTransportMode.SEA -> "بحري"
    LogisticsLegTransportMode.AIR -> "جوي"
    LogisticsLegTransportMode.UNSPECIFIED -> "غير محدد"
}

internal fun String.nullIfBlankDetail(): String? = trim().takeIf(String::isNotBlank)

internal fun Context.displayName(uri: Uri): String {
    val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return it.getString(index)
        }
    }
    return uri.lastPathSegment ?: "document"
}
