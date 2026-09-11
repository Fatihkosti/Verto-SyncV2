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
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.verto.app.feature.shipment.R
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDelayLevel
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalAction
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSpacing
import java.util.concurrent.TimeUnit

@Composable
internal fun ShipmentDetailContent(
    detail: LogisticsShipmentDetailUi,
    access: LogisticsV2Access,
    operationState: LogisticsOperationUiState,
    onAction: (LogisticsNextAction) -> Unit,
    onOpenPartners: () -> Unit,
    onAddDocument: () -> Unit,
    onAddCustomsDocument: (milestoneId: String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onSourceHandoff: (String) -> Unit,
    onPrepareMovement: (LogisticsMovementPreparationDraft) -> Unit,
    onStartPreparedMovement: () -> Unit,
    onOperationalArrival: () -> Unit,
    onConfirmUnload: () -> Unit,
    onStartCustoms: () -> Unit,
    onCompleteCustoms: () -> Unit,
    onOperationalHandoff: () -> Unit,
    onConfirmLoad: () -> Unit,
    onOperationalDeparture: () -> Unit,
    onUpdateEta: (String, Long?) -> Unit,
    onUpdateFutureLeg: (LogisticsShipmentLeg, String) -> Unit,
    onInsertUnplannedStation: (LogisticsUnplannedStationDraft) -> Unit,
    onCorrectActualTime: (LogisticsActualTimeCorrectionDraft) -> Unit,
    onFollowUp: (String) -> Unit,
    onCancel: (String) -> Unit,
    onAddActualCost: (LogisticsCostDraft) -> Unit,
    onPermanentDelete: (confirmationShipmentNumber: String?, reason: String) -> Unit,
) {
    val aggregate = detail.aggregate
    val shipment = aggregate.shipment
    val context = LocalContext.current
    var showFollowUp by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showEta by remember { mutableStateOf(false) }
    var editingLeg by remember { mutableStateOf<LogisticsShipmentLeg?>(null) }
    var showUnplannedStation by remember { mutableStateOf(false) }
    var showActualTimeCorrection by remember { mutableStateOf(false) }
    var showActualCost by remember { mutableStateOf(false) }
    var customsCostMilestoneId by remember { mutableStateOf<String?>(null) }
    var showPermanentDelete by remember { mutableStateOf(false) }
    val nextPlannedLeg = aggregate.legs
        .filter { it.status == LogisticsLegStatus.PLANNED && it.actualDepartureAt == null && it.supersededAt == null }
        .minByOrNull { it.sequence }
    val movementNeedsPreparation = nextPlannedLeg != null && !nextPlannedLeg.v239IsPrepared()
    val currentStationReadyForPreparation = shipment.state == LogisticsShipmentState.AT_STATION &&
        nextPlannedLeg?.fromMilestoneId?.let { fromId ->
            aggregate.milestones.singleOrNull { it.id == fromId }?.let { station ->
                station.handlingStatus == LogisticsMilestoneHandlingStatus.UNLOADED &&
                    (!LogisticsV240ExecutionPolicy.isCustomsHost(aggregate, station) || station.customsCompletedAt != null)
            }
        } == true

    LogisticsSection("الحالة التشغيلية") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LogisticsStatusChip(shipment.state.arabicLabel(), AccentPrimary)
            Text(
                detail.operationalDelay?.let { delay ->
                    when (delay.level) {
                        LogisticsDelayLevel.NORMAL -> androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_9b5bbad38da7)
                        LogisticsDelayLevel.LATE -> androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_ff24c9e9f587, delay.timing.delayDays)
                        LogisticsDelayLevel.VERY_LATE -> androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_3f6720733931, delay.timing.delayDays)
                    }
                } ?: detail.operationalStatus?.let { delayLabel(it.delayMillis) } ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_b3555351dc47),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        detail.operationalStatus?.let { status ->
            LogisticsLabeledValue("الموقع", status.currentLocation)
            LogisticsLabeledValue("عند", status.currentCustodianName)
            LogisticsLabeledValue("الهاتف", status.currentCustodianPhone ?: "—")
            LogisticsLabeledValue("التالي", status.nextMilestoneName ?: shipment.destinationLocation)
            LogisticsLabeledValue("المستلم التالي", status.nextCarrierName ?: if (shipment.state == LogisticsShipmentState.ARRIVED) "المخزن" else "—")
            LogisticsLabeledValue("ETA", status.expectedArrivalAt?.let(::formatLogisticsDate) ?: "—")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.lg)) {
            LogisticsLabeledValue("المصدر", shipment.sourceLocation, Modifier.weight(1f))
            LogisticsLabeledValue("الوجهة", shipment.destinationLocation, Modifier.weight(1f))
        }
        LogisticsLabeledValue("المسؤول", shipment.assignee?.employeeName ?: "—")
    }

    detail.operationalDelay?.takeIf { it.level == LogisticsDelayLevel.VERY_LATE }?.let { delay ->
        val contact = delay.contact
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            tonalElevation = LogisticsV2Tokens.severeDelayCardElevation,
        ) {
            Column(
                modifier = Modifier.padding(VertoSpacing.md),
                verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e5cad11bed05), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_c26ba57fd52b, delay.timing.delayDays, delay.currentStage))
                detail.operationalStatus?.let { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_46aba4246448, it.currentCustodianName)) }
                contact?.let { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_b583ce1365a6, it.displayName, it.role)) }
                delay.lastOperationalUpdateAt?.let { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_25d0fc1376ad, formatLogisticsDate(it)), style = MaterialTheme.typography.bodySmall) }
                contact?.phone?.let { phone ->
                    val whatsappMessage = androidx.compose.ui.res.stringResource(
                        com.verto.app.feature.shipment.R.string.shipment_ds_6428d80e04c5,
                        shipment.shipmentNumber,
                        delay.timing.delayDays,
                        delay.currentStage,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                        VertoOutlinedButton(onClick = { context.dial(phone) }, modifier = Modifier.weight(1f)) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_call)) }
                        VertoOutlinedButton(
                            onClick = {
                                context.openLogisticsWhatsApp(
                                    phone = phone,
                                    message = whatsappMessage,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_whatsapp)) }
                    }
                }
                if (access.canManage) {
                    VertoButton(
                        onClick = { showFollowUp = true },
                        enabled = !operationState.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d02c1412a0f8)) }
                }
            }
        }
    }

    if (
        access.canManage &&
        (shipment.state == LogisticsShipmentState.READY || currentStationReadyForPreparation) &&
        movementNeedsPreparation
    ) {
        nextPlannedLeg?.let { leg ->
            V239MovementPreparationSection(leg, detail.availableCarriers, operationState.isWorking, onPrepareMovement)
        }
    }

    if (shipment.state == LogisticsShipmentState.WAITING_DEPARTURE) {
        V239PreparedDepartureSection(detail, access, operationState.isWorking, onSourceHandoff, onStartPreparedMovement)
    } else if (detail.sourceCustody.size > 1) {
        LogisticsSection("المسؤولية الحالية") {
            detail.sourceCustody.forEach { source -> LogisticsLabeledValue("#${source.invoiceNumber}", source.holderName) }
        }
    }

    detail.operationalStatus?.let { status ->
        val actions = status.availableActions
        if (actions.isNotEmpty() && access.canManage) {
            LogisticsSection("الإجراء التالي") {
                status.currentCustodianPhone?.takeIf { LogisticsOperationalAction.CALL in actions && it.isNotBlank() }?.let { phone ->
                    VertoOutlinedButton(
                        onClick = { context.dial(phone) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_call)) }
                }
                if (LogisticsOperationalAction.FOLLOW_UP in actions) {
                    VertoOutlinedButton(onClick = { showFollowUp = true }, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d02c1412a0f8)) }
                }
                if (LogisticsOperationalAction.UPDATE_ETA in actions) {
                    VertoOutlinedButton(onClick = { showEta = true }, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_bc90229d3de5)) }
                }
                if (LogisticsOperationalAction.RECORD_ARRIVAL in actions) {
                    VertoButton(onClick = onOperationalArrival, enabled = !operationState.isWorking, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_451760f65fa3)) }
                }
                if (LogisticsOperationalAction.CONFIRM_UNLOAD in actions) {
                    VertoButton(onClick = onConfirmUnload, enabled = !operationState.isWorking, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5abb0960548f)) }
                }
                if (LogisticsOperationalAction.START_CUSTOMS in actions) {
                    VertoPrimaryButton(
                        text = stringResource(R.string.logistics_v231_start_customs),
                        onClick = onStartCustoms,
                        enabled = !operationState.isWorking,
                    )
                }
                if (LogisticsOperationalAction.COMPLETE_CUSTOMS in actions) {
                    VertoPrimaryButton(
                        text = stringResource(R.string.logistics_v231_complete_customs),
                        onClick = onCompleteCustoms,
                        enabled = !operationState.isWorking,
                    )
                }
                if (LogisticsOperationalAction.HANDOFF in actions && !movementNeedsPreparation) {
                    VertoButton(onClick = onOperationalHandoff, enabled = !operationState.isWorking, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0272ebd3fd61)) }
                }
                if (LogisticsOperationalAction.CONFIRM_LOAD in actions && !movementNeedsPreparation) {
                    VertoButton(onClick = onConfirmLoad, enabled = !operationState.isWorking, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4a551b767474)) }
                }
                if (LogisticsOperationalAction.RECORD_DEPARTURE in actions && !movementNeedsPreparation) {
                    VertoButton(onClick = onOperationalDeparture, enabled = !operationState.isWorking, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_f5ab61d3d55b)) }
                }
                if (LogisticsOperationalAction.EDIT_FUTURE_PLAN in actions) {
                    aggregate.legs.filter { it.status == LogisticsLegStatus.PLANNED }.forEach { leg ->
                        VertoOutlinedButton(onClick = { editingLeg = leg }, modifier = Modifier.fillMaxWidth()) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_9600b88abcbd, leg.sequence + 1))
                        }
                    }
                }
                if (aggregate.legs.any { it.status == LogisticsLegStatus.IN_TRANSIT }) {
                    VertoOutlinedButton(
                        onClick = { showUnplannedStation = true },
                        enabled = !operationState.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_80770297e283)) }
                }
                if (aggregate.milestones.any { it.arrivedAt != null || it.departedAt != null }) {
                    TextButton(
                        onClick = { showActualTimeCorrection = true },
                        enabled = !operationState.isWorking,
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_394d02919c4d)) }
                }
                if (LogisticsOperationalAction.CANCEL_SHIPMENT in actions) {
                    TextButton(onClick = { showCancel = true }, enabled = !operationState.isWorking) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_835dc6169e2d)) }
                }
                if (LogisticsOperationalAction.START_RECEIVING in actions && access.canConfirm) {
                    VertoButton(
                        onClick = { onAction(LogisticsNextAction.START_RECEIVING) },
                        enabled = !operationState.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_13a7778ab392)) }
                }
            }
        }
    }

    LogisticsV240ExecutionPolicy.customsHostMilestone(aggregate)?.let { customsHost ->
        val customsVisible = customsHost.arrivedAt != null || customsHost.customsStartedAt != null || customsHost.customsCompletedAt != null
        if (customsVisible) {
            val timing = LogisticsV240ExecutionPolicy.customsTiming(aggregate, System.currentTimeMillis())
            val customsCosts = aggregate.costs.filter { it.milestoneId == customsHost.id && it.type in setOf(LogisticsCostType.CUSTOMS_DUTY, LogisticsCostType.CLEARANCE) }
            val customsDocuments = aggregate.documents.filter { it.milestoneId == customsHost.id && it.type == LogisticsDocumentType.CUSTOMS_DOCUMENT }
            LogisticsSection("الجمارك") {
                LogisticsLabeledValue("النقطة", aggregate.customsPlan?.checkpointName ?: customsHost.location)
                LogisticsLabeledValue("المخلص", customsHost.customsBrokerNameSnapshot ?: "—")
                LogisticsLabeledValue("بدأت", customsHost.customsStartedAt?.let(::formatLogisticsDate) ?: "لم تبدأ")
                LogisticsLabeledValue("اكتملت", customsHost.customsCompletedAt?.let(::formatLogisticsDate) ?: "—")
                timing?.let {
                    LogisticsLabeledValue("المدة المحتسبة", logisticsDurationLabel(it.elapsedWorkingMillis))
                    LogisticsLabeledValue("تأخير الجمارك", delayLabel(it.delayMillis))
                    Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a8b128ee39c6), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (customsCosts.isNotEmpty()) {
                    customsCosts.forEach { cost ->
                        LogisticsLabeledValue(
                            if (cost.type == LogisticsCostType.CUSTOMS_DUTY) "رسوم جمركية" else "تخليص",
                            "${cost.amount.toPlainString()} ${cost.currency} • ${cost.paymentState.name}",
                        )
                    }
                }
                customsDocuments.forEach { document ->
                    VertoOutlinedButton(onClick = { onOpenDocument(document.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(document.displayName)
                    }
                }
                if (access.canManage && customsHost.customsStartedAt != null && customsHost.customsCompletedAt == null) {
                    VertoOutlinedButton(
                        onClick = { customsCostMilestoneId = customsHost.id },
                        enabled = !operationState.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_7bf98db3e882)) }
                    VertoOutlinedButton(
                        onClick = { onAddCustomsDocument(customsHost.id) },
                        enabled = !operationState.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_f5e929cf77ed)) }
                }
            }
        }
    }

    detail.closedSummary?.let { closed ->
        LogisticsClosedSummaryContent(shipment.shipmentNumber, closed)
    }

    LogisticsSection("المسار الزمني", trailing = detail.timeline.size.toString()) {
        if (detail.timeline.isEmpty()) LogisticsEmpty("لا توجد محطات أو مراحل مسجلة.")
        detail.timeline.forEach { item -> LogisticsJourneyTimelineRow(item) }
    }

    LogisticsSection("فواتير الشراء", trailing = aggregate.sources.size.toString()) {
        if (aggregate.sources.isEmpty()) LogisticsEmpty("لا توجد فواتير مصدرية.")
        aggregate.sources.forEach { source ->
            LogisticsLabeledValue("${source.invoiceNumberSnapshot} — ${source.supplierNameSnapshot}", "مرجع للقراءة فقط")
        }
    }

    LogisticsSection("الأصناف والكميات", trailing = aggregate.lines.size.toString()) {
        if (aggregate.lines.isEmpty()) LogisticsEmpty("لا توجد أصناف مخططة.")
        aggregate.lines.forEach { line ->
            LogisticsLabeledValue(line.itemNameSnapshot, "متوقع: ${line.expectedQuantity} • سعر شراء: ${line.basePurchaseUnitPrice.toPlainString()}")
        }
    }

    LogisticsSection("الجهات اللوجستية", trailing = aggregate.partners.size.toString()) {
        if (aggregate.partners.isEmpty()) LogisticsEmpty("لا توجد جهات مرتبطة.")
        aggregate.partners.forEach { link -> LogisticsLabeledValue(link.role.name, link.partnerId) }
        if (access.canManage) {
            VertoButton(
                onClick = onOpenPartners,
                enabled = !operationState.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_de47b0201b49)) }
        }
    }

    LogisticsSection("المستندات", trailing = aggregate.documents.size.toString()) {
        if (aggregate.documents.isEmpty()) LogisticsEmpty("لا توجد مستندات مرفقة.")
        aggregate.documents.forEach { document ->
            LogisticsDocumentRow(
                document = document,
                canDelete = access.canManage && shipment.state != LogisticsShipmentState.CLOSED && shipment.state != LogisticsShipmentState.CANCELLED,
                busy = operationState.isWorking,
                onOpen = { onOpenDocument(document.id) },
                onDelete = { onDeleteDocument(document.id) },
            )
        }
        if (access.canManage && shipment.state != LogisticsShipmentState.CLOSED && shipment.state != LogisticsShipmentState.CANCELLED) {
            VertoButton(
                onClick = onAddDocument,
                enabled = !operationState.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e631f1fa765e)) }
        }
    }

    detail.closedSummary?.let { closed ->
        LogisticsSection("سجل الأحداث", trailing = closed.events.size.toString()) {
            if (closed.events.isEmpty()) LogisticsEmpty("لا توجد أحداث مسجلة.")
            closed.events.forEach { event ->
                LogisticsLabeledValue(
                    event.type,
                    buildString {
                        append(formatLogisticsDate(event.occurredAt))
                        event.employeeName?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
                    },
                )
            }
        }
    }

    LogisticsSection("التكاليف", trailing = aggregate.costs.size.toString()) {
        if (aggregate.costs.isEmpty()) LogisticsEmpty("لا توجد تكاليف مسجلة.")
        CostVarianceRows(detail)
        LogisticsLabeledValue("إجمالي الفعلي بالعملة الأساسية", detail.actualCostTotal.toPlainString())
        if (access.canManage && shipment.state != LogisticsShipmentState.CLOSED && shipment.state != LogisticsShipmentState.CANCELLED) {
            VertoOutlinedButton(onClick = { showActualCost = true }, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_07c86de27d58)) }
        }
    }

    LogisticsSection("ملخص الاستلام") {
        LogisticsLabeledValue("المتوقع", detail.expectedQuantity.toString())
        LogisticsLabeledValue("المستلم", detail.receivedQuantity.toString())
        LogisticsLabeledValue("المقبول للمخزون", detail.acceptedQuantity.toString())
    }

    LogisticsOperationFeedback(operationState)
    val legacyAction = detail.nextAction
    val showLegacy = shipment.state in setOf(
        LogisticsShipmentState.DRAFT,
        LogisticsShipmentState.RECEIVING,
        LogisticsShipmentState.PARTIAL,
        LogisticsShipmentState.RECEIVED,
    )
    if (showLegacy && legacyAction != LogisticsNextAction.NONE && actionAllowed(access, legacyAction)) {
        VertoButton(
            onClick = { onAction(legacyAction) },
            enabled = !operationState.isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(legacyAction.label) }
    }
    if (access.canManage && shipment.state == LogisticsShipmentState.DRAFT && shipment.startedAt == null) {
        VertoSecondaryButton(
            text = stringResource(R.string.logistics_v232_delete_draft),
            onClick = { showPermanentDelete = true },
            enabled = !operationState.isWorking,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showPermanentDelete) {
        PermanentDeleteShipmentDialog(
            shipmentNumber = shipment.shipmentNumber,
            isDraft = shipment.state == LogisticsShipmentState.DRAFT,
            onDismiss = { showPermanentDelete = false },
            onConfirm = { confirmation, reason ->
                showPermanentDelete = false
                onPermanentDelete(confirmation, reason)
            },
        )
    }

    if (showFollowUp) {
        TextInputDialog(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d02c1412a0f8),
            label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_c1e234b3a0ff),
            confirmLabel = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save),
            onDismiss = { showFollowUp = false },
            onConfirm = { note -> showFollowUp = false; onFollowUp(note) },
        )
    }
    if (showCancel) {
        TextInputDialog(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_835dc6169e2d),
            label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_935d8234319a),
            confirmLabel = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_835dc6169e2d),
            onDismiss = { showCancel = false },
            onConfirm = { reason -> showCancel = false; onCancel(reason) },
        )
    }
    if (showEta) {
        val targetLeg = statusEtaLeg(detail)
        EtaPickerDialog(
            current = targetLeg?.plannedArrivalAt,
            onDismiss = { showEta = false },
            onConfirm = { eta ->
                showEta = false
                targetLeg?.let { onUpdateEta(it.id, eta) }
            },
        )
    }
    editingLeg?.let { leg ->
        FutureLegDialog(
            initial = leg,
            carriers = detail.availableCarriers,
            onDismiss = { editingLeg = null },
            onConfirm = { updated, reason -> editingLeg = null; onUpdateFutureLeg(updated, reason) },
        )
    }
    if (showUnplannedStation) {
        aggregate.legs.singleOrNull { it.status == LogisticsLegStatus.IN_TRANSIT }?.let { activeLeg ->
            val destinationMilestone = aggregate.milestones.firstOrNull { it.id == activeLeg.toMilestoneId }
            val originMilestone = aggregate.milestones.firstOrNull { it.id == activeLeg.fromMilestoneId }
            UnplannedStationDialog(
                activeLeg = activeLeg,
                countryContext = destinationMilestone ?: originMilestone,
                carriers = detail.availableCarriers,
                onDismiss = { showUnplannedStation = false },
                onConfirm = { draft -> showUnplannedStation = false; onInsertUnplannedStation(draft) },
            )
        } ?: run { showUnplannedStation = false }
    }
    if (showActualTimeCorrection) {
        ActualTimeCorrectionDialog(
            milestones = aggregate.milestones,
            onDismiss = { showActualTimeCorrection = false },
            onConfirm = { draft -> showActualTimeCorrection = false; onCorrectActualTime(draft) },
        )
    }
    if (showActualCost) {
        ActualCostDialog(
            detail = detail,
            onDismiss = { showActualCost = false },
            onConfirm = { draft -> showActualCost = false; onAddActualCost(draft) },
        )
    }
    customsCostMilestoneId?.let { milestoneId ->
        val brokerId = aggregate.milestones.singleOrNull { it.id == milestoneId }?.customsBrokerPartnerId
        ActualCostDialog(
            detail = detail,
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5bd564d75b0b),
            allowScope = false,
            initial = LogisticsCostDraft(
                type = LogisticsCostType.CLEARANCE,
                milestoneId = milestoneId,
                servicePartnerId = brokerId,
            ),
            onDismiss = { customsCostMilestoneId = null },
            onConfirm = { draft -> customsCostMilestoneId = null; onAddActualCost(draft) },
        )
    }
}
