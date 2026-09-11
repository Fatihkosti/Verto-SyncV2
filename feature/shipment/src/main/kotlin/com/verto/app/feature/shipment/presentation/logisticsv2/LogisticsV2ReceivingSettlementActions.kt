package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.*

internal fun LogisticsV2ViewModel.startReceivingAndOpen(shipmentId: String, onReady: () -> Unit) {
        if (!state.accessValue.canConfirm) return permissionError()
        launchOperation("start-receiving:$shipmentId", "بدأ استلام الشحنة", {
            refreshCenter(); onReady()
        }) { requestId -> receivingWorkflow.ensureReceivingStarted(shipmentId, requestId) }
    }
internal fun LogisticsV2ViewModel.loadReceiving(shipmentId: String) {
        if (!state.accessValue.canConfirm) return setReceivingDenied()
        viewModelScope.launch {
            state.receivingValue = LogisticsReceivingUiState.Loading
            runCatching {
                val content = readWorkflow.receiving(shipmentId)
                val saved = planningWorkflow.loadReceivingDraft(shipmentId)
                    ?.takeIf { it.shipmentId == shipmentId }
                latestReceivingDraft = saved
                content.copy(savedDraft = saved)
            }.onSuccess { state.receivingValue = it }
                .onFailure { state.receivingValue = LogisticsReceivingUiState.Error(ErrorHumanizer.humanize(it, "تحميل الاستلام")) }
        }
    }

internal fun LogisticsV2ViewModel.saveFinalReceivingDraft(
        shipmentId: String,
        receivedCompletely: Boolean?,
        missingQuantities: Map<String, String>,
        lines: List<LogisticsReceivingLineDraft>,
    ) {
        val baseline = latestReceivingDraft?.takeIf { it.shipmentId == shipmentId }?.lines
            ?.takeIf { it.isNotEmpty() }
            ?: lines.filter { it.remainingBeforeBatch > 0 }.map { line ->
                LogisticsFinalReceivingLineSnapshot(
                    shipmentLineId = line.shipmentLineId,
                    sourceInvoiceId = line.sourceInvoiceId,
                    sourceInvoiceNumber = line.sourceInvoiceNumber,
                    itemName = line.itemName,
                    expectedQuantity = line.expectedQuantity,
                    alreadyReceivedQuantity = line.alreadyReceivedQuantity,
                )
            }
        val snapshot = LogisticsFinalReceivingDraftSnapshot(
            shipmentId = shipmentId,
            requestId = latestReceivingDraft?.takeIf { it.shipmentId == shipmentId }?.requestId ?: UUID.randomUUID().toString(),
            receivedCompletely = receivedCompletely,
            missingQuantities = missingQuantities.mapValues { (_, value) -> value.filter(Char::isDigit) },
            lines = baseline,
            updatedAt = nextPersistenceTimestamp(),
        )
        latestReceivingDraft = snapshot
        receivingDraftSaveJob?.cancel()
        receivingDraftSaveJob = viewModelScope.launch {
            delay(250L)
            runCatching { planningWorkflow.saveReceivingDraft(snapshot) }
                .onFailure { operationError(ErrorHumanizer.humanize(it, "حفظ مسودة الاستلام")) }
        }
    }

internal suspend fun LogisticsV2ViewModel.flushFinalReceivingDraft(shipmentId: String) {
        receivingDraftSaveJob?.cancelAndJoin()
        receivingDraftSaveJob = null
        latestReceivingDraft?.takeIf { it.shipmentId == shipmentId }?.let { planningWorkflow.saveReceivingDraft(it) }
    }

internal fun LogisticsV2ViewModel.submitReceiving(shipmentId: String, draft: LogisticsReceivingDraft, onSubmitted: () -> Unit) {
        if (!state.accessValue.canConfirm) return permissionError()
        if (!draft.isValid) return operationError("بيانات الاستلام النهائي غير مكتملة")
        val finalizationRequestId = latestReceivingDraft
            ?.takeIf { it.shipmentId == shipmentId }
            ?.requestId
            ?: UUID.randomUUID().toString()
        launchOperation("receiving-final:$shipmentId", "تم استلام الشحنة وإغلاقها", {
            latestReceivingDraft = null
            clearDurableDraft(shipmentId)
            refreshCenter(); selectShipment(shipmentId); onSubmitted()
        }) { _ ->
            flushFinalReceivingDraft(shipmentId)
            receivingWorkflow.finalizeReceiving(shipmentId, draft, finalizationRequestId)
        }
    }
internal fun LogisticsV2ViewModel.loadCosts(shipmentId: String) {
        if (!state.accessValue.canView) return setCostsDenied()
        viewModelScope.launch {
            state.costsValue = LogisticsCostsUiState.Loading
            runCatching { readWorkflow.costs(shipmentId) }
                .onSuccess { state.costsValue = it }
                .onFailure { state.costsValue = LogisticsCostsUiState.Error(ErrorHumanizer.humanize(it, "تحميل التكاليف")) }
        }
    }
internal fun LogisticsV2ViewModel.addCost(shipmentId: String, draft: LogisticsCostDraft) {
        if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات التكلفة غير صحيحة")
        launchOperation("cost:$shipmentId", "تمت إضافة التكلفة", {
            loadCosts(shipmentId); selectShipment(shipmentId)
        }) { requestId -> receivingWorkflow.addCost(shipmentId, draft, requestId) }
    }
internal fun LogisticsV2ViewModel.recoverMissingGoods(shipmentId: String, draft: LogisticsRecoveryDraft) { if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات استرجاع البضاعة غير مكتملة"); launchOperation("recovery:$shipmentId", "تم استرجاع البضاعة وإضافتها للمخزون", { refreshCenter(); loadCosts(shipmentId); selectShipment(shipmentId) }) { requestId -> receivingWorkflow.recoverMissingGoods(shipmentId, draft, requestId) } }
internal fun LogisticsV2ViewModel.settleShortage(shipmentId: String, draft: LogisticsShortageSettlementDraft) { if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات تسوية النقص غير مكتملة"); launchOperation("shortage-settle:$shipmentId:${draft.shortageId}:${draft.type}", "تمت تسوية النقص", { refreshCenter(); selectShipment(shipmentId) }) { requestId -> receivingWorkflow.settleShortage(shipmentId, draft, requestId) } }
internal fun LogisticsV2ViewModel.addLateCost(shipmentId: String, draft: LogisticsCostDraft) { if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات التكلفة المتأخرة غير صحيحة"); launchOperation("late-cost:$shipmentId:${draft.type}:${draft.amount}:${draft.reference}", "أضيفت التكلفة المتأخرة دون إعادة فتح الشحنة", { loadCosts(shipmentId); selectShipment(shipmentId) }) { requestId -> receivingWorkflow.addLateCost(shipmentId, draft, requestId) } }
internal fun LogisticsV2ViewModel.permanentlyDeleteShipment(shipmentId: String, shipmentNumber: String?, reason: String, onDeleted: () -> Unit = {}) { if (!state.accessValue.canManage) return permissionError(); launchOperation("permanent-delete:$shipmentId", "تم حذف الشحنة نهائيًا", { clearDurableDraft(shipmentId); refreshCenter(); clearSelection(); onDeleted() }) { requestId -> receivingWorkflow.permanentlyDeleteShipment(shipmentId, shipmentNumber, reason, requestId) } }
internal fun LogisticsV2ViewModel.confirmCostPayment(shipmentId: String, costId: String) { if (!state.accessValue.canManage) return permissionError(); launchOperation("cost-pay:$shipmentId:$costId", "تم تأكيد دفع التكلفة", { loadCosts(shipmentId); selectShipment(shipmentId) }) { receivingWorkflow.confirmCostPayment(shipmentId, costId, it) } }
internal fun LogisticsV2ViewModel.adjustPaidCost(shipmentId: String, costId: String, draft: LogisticsPaidCostEditDraft) { if (!state.accessValue.canManage) return permissionError(); val amount = draft.amount.toBigDecimalOrNull(); val rate = draft.exchangeRate.toBigDecimalOrNull(); if (amount == null || amount.signum() <= 0 || rate == null || rate.signum() <= 0 || draft.currency.isBlank()) return operationError("بيانات تعديل التكلفة غير صحيحة"); launchOperation("cost-adjust:$shipmentId:$costId", "تم تعديل التكلفة وتسوية فرق الصندوق", { loadCosts(shipmentId); selectShipment(shipmentId) }) { receivingWorkflow.adjustPaidCost(shipmentId, costId, draft, it) } }
internal fun LogisticsV2ViewModel.saveCostProof(shipmentId: String, costId: String, sourceUri: String, displayName: String, mimeType: String) { if (!state.accessValue.canManage) return permissionError(); if (!mimeType.startsWith("image/")) return operationError("إثبات الدفع يجب أن يكون صورة"); launchOperation("cost-proof:$shipmentId:$costId:$sourceUri", "تم حفظ إثبات الدفع", { loadCosts(shipmentId); selectShipment(shipmentId) }) { receivingWorkflow.saveCostProof(shipmentId, costId, LogisticsCostProofDraft(sourceUri, displayName, mimeType), it) } }
internal fun LogisticsV2ViewModel.settleCosts(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("settle-cost:$shipmentId", "تمت تسوية تكلفة الشحنة", {
            loadCosts(shipmentId); selectShipment(shipmentId)
        }) { requestId -> receivingWorkflow.settle(shipmentId, requestId) }
    }
internal fun LogisticsV2ViewModel.closeShipment(shipmentId: String, onClosed: () -> Unit = {}) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("close:$shipmentId", "تم إغلاق الشحنة", {
            clearDurableDraft(shipmentId); refreshCenter(); selectShipment(shipmentId); onClosed()
        }) { requestId -> receivingWorkflow.close(shipmentId, requestId) }
    }
internal fun LogisticsV2ViewModel.loadPartners(shipmentId: String) {
        if (!state.accessValue.canView) return setPartnersDenied()
        viewModelScope.launch {
            state.partnersValue = LogisticsPartnersUiState.Loading
            runCatching {
                val organizationId = readWorkflow.operationalOrganizationId()
                readWorkflow.operationalAggregate(shipmentId)
                LogisticsPartnersUiState.Content(
                    shipmentId,
                    LogisticsPartnerDirectoryUi(readWorkflow.partners(organizationId), state.accessValue.canManage),
                )
            }.onSuccess { state.partnersValue = it }
                .onFailure { state.partnersValue = LogisticsPartnersUiState.Error(ErrorHumanizer.humanize(it, "تحميل الجهات")) }
        }
    }
internal fun LogisticsV2ViewModel.saveAndLinkPartner(shipmentId: String, draft: LogisticsPartnerDraft) {
        if (!state.accessValue.canManage || !draft.isValid) return permissionError()
        launchOperation("partner:$shipmentId:${draft.name}", "تم حفظ وربط الجهة", {
            loadPartners(shipmentId); selectShipment(shipmentId)
        }) { executionWorkflow.saveAndLinkPartner(shipmentId, draft) }
    }
internal fun LogisticsV2ViewModel.deleteDocument(shipmentId: String, documentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("document-delete:$shipmentId:$documentId", "تم حذف المستند", {
            selectShipment(shipmentId)
        }) { executionWorkflow.deleteDocument(shipmentId, documentId) }
    }
