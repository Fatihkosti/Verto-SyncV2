package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsDurationUnit
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind

internal const val V238_CUSTOMS_DOCUMENT_TARGET = "V238_CUSTOMS_PLAN"

internal data class LogisticsV238CustomsValidation(
    val checkpointError: String? = null,
    val afterStationError: String? = null,
    val durationError: String? = null,
    val documentError: String? = null,
) {
    val isValid: Boolean get() = checkpointError == null && afterStationError == null && durationError == null && documentError == null
}

internal enum class LogisticsV238ReviewTarget { BASICS, PURCHASE, TRIP_TYPE, ROUTE, STATION, CUSTOMS }

internal data class LogisticsV238ReviewIssue(
    val target: LogisticsV238ReviewTarget,
    val message: String,
)

internal fun LogisticsRouteWorkspaceSnapshot.v238CustomsValidation(): LogisticsV238CustomsValidation {
    val stations = milestones.filter { it.type != LogisticsMilestoneType.CUSTOMS }.sortedBy { it.order }
    val after = stations.firstOrNull { it.id == customsAfterStationId }
    val customsDocuments = pendingDocuments.filter { it.milestoneId == V238_CUSTOMS_DOCUMENT_TARGET }
    return LogisticsV238CustomsValidation(
        checkpointError = "نقطة الجمارك مطلوبة".takeIf { customsCheckpointName.trim().isBlank() },
        afterStationError = when {
            customsAfterStationId.isBlank() -> "حدد المحطة التي تقع الجمارك بعدها"
            after == null -> "المحطة المحددة لم تعد ضمن المسار"
            after.type == LogisticsMilestoneType.DESTINATION -> "لا يمكن وضع الجمارك بعد نقطة الوصول النهائية"
            else -> null
        },
        durationError = "أدخل مدة جمارك أكبر من صفر".takeIf { (customsExpectedDurationMinutes ?: 0) <= 0 },
        documentError = "أكمل تجهيز المستند أو أعد المحاولة أو احذفه".takeIf {
            customsDocuments.any { it.isStaging || it.errorMessage != null || !it.isReady }
        },
    )
}

internal fun LogisticsRouteWorkspaceSnapshot.withV238CustomsDuration(value: Int?, unit: LogisticsDurationUnit): LogisticsRouteWorkspaceSnapshot {
    val minutes = value?.takeIf { it > 0 }?.let { amount ->
        when (unit) {
            LogisticsDurationUnit.HOURS -> amount * 60
            LogisticsDurationUnit.DAYS -> amount * 24 * 60
        }
    }
    return copy(customsExpectedDurationMinutes = minutes)
}

internal fun LogisticsRouteWorkspaceSnapshot.v238CustomsDurationUnit(): LogisticsDurationUnit =
    if ((customsExpectedDurationMinutes ?: 0) > 0 && customsExpectedDurationMinutes!! % (24 * 60) == 0) {
        LogisticsDurationUnit.DAYS
    } else {
        LogisticsDurationUnit.HOURS
    }

internal fun LogisticsRouteWorkspaceSnapshot.v238CustomsDurationValue(): Int? {
    val minutes = customsExpectedDurationMinutes ?: return null
    return if (v238CustomsDurationUnit() == LogisticsDurationUnit.DAYS) minutes / (24 * 60) else minutes / 60
}

internal fun LogisticsPlanningDraft.v238ReviewIssues(
    workspace: LogisticsRouteWorkspaceSnapshot,
    invoiceOptions: List<LogisticsPurchaseInvoiceOptionUi>,
): List<LogisticsV238ReviewIssue> = buildList {
    if (sourceLocation.isBlank() || destinationLocation.isBlank() || assigneeId.isBlank()) {
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.BASICS, "أكمل تعريف الشحنة والموظف المسؤول"))
    }
    if (!isPurchaseStepReady(invoiceOptions)) {
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.PURCHASE, "راجع الموردين والفواتير؛ إحدى البيانات ناقصة أو تغيرت"))
    }
    if (!workspace.v237TripValidation().isValid) {
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.TRIP_TYPE, "اختر نوع الرحلة والنقل"))
    }
    val ordered = workspace.milestones.sortedBy { it.order }
    if (ordered.size < 2 || ordered.firstOrNull()?.type != LogisticsMilestoneType.ORIGIN || ordered.lastOrNull()?.type != LogisticsMilestoneType.DESTINATION) {
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.ROUTE, "المسار غير مكتمل"))
    }
    val firstIncomplete = workspace.v237FirstIncompleteLeg()
    if (firstIncomplete != null) {
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.STATION, "أكمل بيانات المحطة ${firstIncomplete.sequence + 1}"))
    }
    if (workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED && workspace.legs.any { it.mode == LogisticsLegTransportMode.UNSPECIFIED }) {
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.STATION, "اختر نوع النقل لكل حركة في الرحلة المختلطة"))
    }
    workspace.v238CustomsValidation().takeUnless { it.isValid }?.let { validation ->
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.CUSTOMS, validation.checkpointError ?: validation.afterStationError ?: validation.durationError ?: validation.documentError ?: "راجع بيانات الجمارك"))
    }
    if (workspace.pendingDocuments.any { it.isStaging }) {
        add(LogisticsV238ReviewIssue(LogisticsV238ReviewTarget.STATION, "انتظر اكتمال تجهيز المرفقات قبل الاعتماد"))
    }
}
