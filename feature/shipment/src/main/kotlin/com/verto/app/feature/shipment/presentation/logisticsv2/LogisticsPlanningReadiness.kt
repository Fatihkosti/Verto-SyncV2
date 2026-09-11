package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType

internal enum class PlanningRepairTarget {
    BASICS_DEPARTURE,
    BASICS_ARRIVAL,
    BASICS_ASSIGNEE,
    PURCHASE_SOURCES,
    PURCHASE_CARGO,
    ROUTE_DETAILS,
    ROUTE_STOP,
    ROUTE_LEG,
    ROUTE_DOCUMENTS,
}

internal data class PlanningBlocker(
    val id: String,
    val message: String,
    val step: LogisticsPlanningStep,
    val target: PlanningRepairTarget,
    val index: Int? = null,
)

/** v230 readiness: supplier invoices + route topology/timing only. Operational carrier/cargo/cost/doc fields are excluded. */
internal fun LogisticsPlanningDraft.resolvePlanningBlockers(routeDetailsInputValid: Boolean = true): List<PlanningBlocker> = buildList {
    fun blocker(id: String, message: String, step: LogisticsPlanningStep, target: PlanningRepairTarget, index: Int? = null) {
        add(PlanningBlocker(id, message, step, target, index))
    }

    if (!sourceLocation.toLogisticsPlanningPlace().isValid) {
        blocker("basics.departure", "حدد دولة ومدينة المغادرة", LogisticsPlanningStep.BASICS, PlanningRepairTarget.BASICS_DEPARTURE)
    }
    if (!destinationLocation.toLogisticsPlanningPlace().isValid) {
        blocker("basics.arrival", "حدد دولة ومدينة الوصول", LogisticsPlanningStep.BASICS, PlanningRepairTarget.BASICS_ARRIVAL)
    }
    if (assigneeId.isBlank() || assigneeName.isBlank()) {
        blocker("basics.assignee", "حدد الموظف المتابع", LogisticsPlanningStep.BASICS, PlanningRepairTarget.BASICS_ASSIGNEE)
    }
    if (sources.isEmpty() || lines.isEmpty()) {
        blocker("purchase.sources", "أضف موردًا وفاتورة واحدة على الأقل", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.PURCHASE_SOURCES)
    }
    if (sources.any { source -> lines.none { it.sourceInvoiceId == source.invoiceId } }) {
        blocker("purchase.invoice", "كل فاتورة مختارة يجب ربطها كاملة", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.PURCHASE_SOURCES)
    }
    if (lines.any { it.inventoryItemId.isBlank() || it.expectedQuantity <= 0 }) {
        blocker("purchase.snapshot", "إحدى الفواتير غير جاهزة للشحن", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.PURCHASE_SOURCES)
    }

    val orderedMilestones = milestones.sortedBy { it.order }
    val orderedLegs = legs.sortedBy { it.sequence }
    if (orderedMilestones.size < 2 || orderedLegs.size != orderedMilestones.size - 1) {
        blocker("route.structure", "أكمل محطات الرحلة", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_STOP)
        return@buildList
    }
    if (orderedMilestones.first().type != LogisticsMilestoneType.ORIGIN || orderedMilestones.first().location != sourceLocation) {
        blocker("route.origin", "المصدر لا يطابق تعريف الشحنة", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_STOP, 0)
    }
    if (orderedMilestones.last().type != LogisticsMilestoneType.DESTINATION || orderedMilestones.last().location != destinationLocation) {
        blocker("route.destination", "الوجهة لا تطابق تعريف الشحنة", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_STOP, orderedMilestones.lastIndex)
    }
    if (orderedMilestones.count { it.type == LogisticsMilestoneType.CUSTOMS } > 1) {
        blocker("route.customs.count", "حدد محطة جمارك واحدة فقط", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_STOP)
    }
    orderedMilestones.singleOrNull { it.type == LogisticsMilestoneType.CUSTOMS }?.let { customs ->
        if (customs.expectedStayDays?.let { it > 0 } != true) {
            blocker("route.customs.duration", "حدد مدة الجمارك المتوقعة", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_STOP)
        }
    }
    orderedLegs.forEachIndexed { index, leg ->
        if (leg.sequence != index || orderedMilestones.getOrNull(index)?.id != leg.fromMilestoneId ||
            orderedMilestones.getOrNull(index + 1)?.id != leg.toMilestoneId
        ) {
            blocker("route.leg.$index.link", "ربط المرحلة ${index + 1} غير صحيح", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_LEG, index)
        }
        if (leg.expectedTransitDays?.let { it > 0 } != true) {
            blocker("route.leg.$index.duration", "حدد مدة المرحلة ${index + 1}", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_LEG, index)
        }
        if (leg.carrierPartnerId.isNotBlank() || leg.packageCount != null || leg.weightKg != null) {
            blocker("route.leg.$index.execution", "بيانات التنفيذ لا تُحفظ أثناء التخطيط", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_LEG, index)
        }
    }
    val modes = orderedLegs.map { it.mode }.toSet()
    if (LogisticsLegTransportMode.UNSPECIFIED in modes && modes.size > 1) {
        blocker("route.mode.partial", "اختر موحدًا أو مختلطًا فقط", LogisticsPlanningStep.PURCHASE, PlanningRepairTarget.ROUTE_LEG)
    }
}.distinctBy { it.id }

internal fun LogisticsPlanningDraft.isRouteReadyForReview(): Boolean = resolvePlanningBlockers().none {
    it.target in setOf(PlanningRepairTarget.ROUTE_STOP, PlanningRepairTarget.ROUTE_LEG, PlanningRepairTarget.ROUTE_DETAILS)
}
