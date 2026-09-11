package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import java.util.UUID
import javax.inject.Inject

internal data class LogisticsOperationalHandoffTarget(
    val holderType: LogisticsCustodyHolderType,
    val holderId: String?,
    val holderName: String,
)

internal data class LogisticsOperationalHandoffPlan(
    val target: LogisticsOperationalHandoffTarget,
    val sourcesNeedingHandoff: List<LogisticsShipmentSource>,
    val sameCurrentHolder: Boolean,
)

internal data class LogisticsOperationalHandoffContext(
    val organizationId: String,
    val aggregate: LogisticsShipmentAggregate,
    val milestone: LogisticsMilestone,
    val positions: Map<String, LogisticsCustodyPosition>,
    val plan: LogisticsOperationalHandoffPlan,
)

internal class LogisticsCustodyWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val recordHandoff: RecordCustodyHandoffUseCase,
) {
    suspend fun sourceHandoffDraft(shipmentId: String, sourceId: String): LogisticsCustodyHandoffDraft {
        val aggregate = readService.aggregate(organizationId(), shipmentId) ?: error("لم يتم العثور على الشحنة")
        require(aggregate.sources.any { it.id == sourceId }) { "فاتورة المصدر غير موجودة" }
        val cargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate) ?: error("عدد الكراتين المؤكد غير متوفر")
        return LogisticsCustodyHandoffDraft(
            scope = LogisticsCustodyHandoffScope(shipmentId = shipmentId, sourceId = sourceId),
            currentCargo = cargo,
        )
    }

    suspend fun handoffSourceToFirstCarrier(
        shipmentId: String,
        sourceId: String,
        draft: LogisticsCustodyHandoffDraft,
        requestId: String,
    ) {
        require(draft.shipmentId == shipmentId && draft.sourceId == sourceId && draft.isValid) { "بيانات تأكيد التسليم غير صحيحة" }
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val firstLeg = aggregate.legs.minByOrNull { it.sequence } ?: error("المسار لا يحتوي مرحلة نقل")
        val carrier = readService.partner(organizationId, firstLeg.carrierPartnerId) ?: error("شركة النقل غير موجودة")
        val position = LogisticsCustodyResolver.currentForSource(
            aggregate,
            aggregate.sources.singleOrNull { it.id == sourceId } ?: error("فاتورة المصدر غير موجودة"),
        )
        val now = System.currentTimeMillis()
        recordHandoff(
            organizationId,
            RecordCustodyHandoffCommand(
                shipmentId = shipmentId,
                sourceId = sourceId,
                fromHolderType = position.holderType,
                fromHolderId = position.holderId,
                fromHolderNameSnapshot = position.holderName,
                toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER,
                toHolderId = carrier.id,
                toHolderNameSnapshot = carrier.name,
                transferredAt = now,
                receivedAt = now,
                requestId = requestId,
                handoverPackageCount = draft.handoverCount,
                receivedPackageCount = draft.receivedCount,
                handoverWeightKg = draft.currentWeightKg,
                receivedWeightKg = draft.receivedWeight,
                discrepancyNote = draft.discrepancyNote,
            ),
        )
    }

    suspend fun operationalHandoffDraft(shipmentId: String): LogisticsCustodyHandoffDraft {
        val aggregate = readService.aggregate(organizationId(), shipmentId) ?: error("لم يتم العثور على الشحنة")
        val cargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate) ?: error("عدد الكراتين المؤكد غير متوفر")
        return LogisticsCustodyHandoffDraft(
            scope = LogisticsCustodyHandoffScope(shipmentId = shipmentId),
            currentCargo = cargo,
        )
    }

    suspend fun recordOperationalHandoff(
        shipmentId: String,
        draft: LogisticsCustodyHandoffDraft,
        requestId: String,
    ) {
        require(draft.shipmentId == shipmentId && draft.sourceId == null && draft.isValid) { "بيانات تأكيد التسليم غير صحيحة" }
        val context = resolveOperationalHandoffContext(shipmentId)
        if (context.plan.sourcesNeedingHandoff.isEmpty()) return
        if (context.plan.sameCurrentHolder) {
            recordUnifiedOperationalHandoff(context, shipmentId, draft, requestId)
        } else {
            require(!draft.isDiscrepant) { "تسجيل فرق عددي يتطلب أن تكون الحمولة كلها تحت مسؤولية جهة واحدة" }
            recordPerSourceOperationalHandoffs(context, shipmentId, draft, requestId)
        }
    }

    private suspend fun resolveOperationalHandoffContext(shipmentId: String): LogisticsOperationalHandoffContext {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val milestone = aggregate.milestones.sortedByDescending { it.order }.firstOrNull {
            it.arrivedAt != null && it.departedAt == null && it.handlingStatus == LogisticsMilestoneHandlingStatus.UNLOADED
        } ?: error("يجب تأكيد التفريغ قبل التسليم")
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate)
        val target = resolveOperationalTarget(organizationId, aggregate, milestone)
        val needs = aggregate.sources.filter { source ->
            val position = positions.getValue(source.id)
            position.holderType != target.holderType || position.holderId != target.holderId
        }
        val current = needs.map { positions.getValue(it.id) }
        val sameCurrent = current.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }.size == 1 &&
            needs.size == aggregate.sources.size
        return LogisticsOperationalHandoffContext(
            organizationId, aggregate, milestone, positions,
            LogisticsOperationalHandoffPlan(target, needs, sameCurrent),
        )
    }

    private suspend fun resolveOperationalTarget(
        organizationId: String,
        aggregate: LogisticsShipmentAggregate,
        milestone: LogisticsMilestone,
    ): LogisticsOperationalHandoffTarget {
        if (milestone.type == LogisticsMilestoneType.DESTINATION) {
            return LogisticsOperationalHandoffTarget(LogisticsCustodyHolderType.WAREHOUSE, null, "المخزن")
        }
        val nextLeg = aggregate.legs.singleOrNull { it.fromMilestoneId == milestone.id } ?: error("لا توجد مرحلة نقل تالية")
        val partner = readService.partner(organizationId, nextLeg.carrierPartnerId)
        return LogisticsOperationalHandoffTarget(
            LogisticsCustodyHolderType.LOGISTICS_PARTNER,
            partner?.id,
            partner?.name ?: "المخزن",
        )
    }

    private suspend fun recordUnifiedOperationalHandoff(
        context: LogisticsOperationalHandoffContext,
        shipmentId: String,
        draft: LogisticsCustodyHandoffDraft,
        requestId: String,
    ) {
        val from = context.positions.getValue(context.plan.sourcesNeedingHandoff.first().id)
        val now = System.currentTimeMillis()
        recordHandoff(
            context.organizationId,
            RecordCustodyHandoffCommand(
                shipmentId = shipmentId, milestoneId = context.milestone.id,
                fromHolderType = from.holderType, fromHolderId = from.holderId, fromHolderNameSnapshot = from.holderName,
                toHolderType = context.plan.target.holderType, toHolderId = context.plan.target.holderId, toHolderNameSnapshot = context.plan.target.holderName,
                transferredAt = now, receivedAt = now, requestId = requestId,
                handoverPackageCount = draft.handoverCount, receivedPackageCount = draft.receivedCount,
                handoverWeightKg = draft.currentWeightKg, receivedWeightKg = draft.receivedWeight, discrepancyNote = draft.discrepancyNote,
                openedPackageCount = requireNotNull(draft.openedCount), damagedPackageCount = requireNotNull(draft.damagedCount),
            ),
        )
    }

    private suspend fun recordPerSourceOperationalHandoffs(
        context: LogisticsOperationalHandoffContext,
        shipmentId: String,
        draft: LogisticsCustodyHandoffDraft,
        requestId: String,
    ) {
        val now = System.currentTimeMillis()
        context.plan.sourcesNeedingHandoff.forEachIndexed { index, source ->
            val from = context.positions.getValue(source.id)
            recordHandoff(
                context.organizationId,
                RecordCustodyHandoffCommand(
                    shipmentId = shipmentId, sourceId = source.id, milestoneId = context.milestone.id,
                    fromHolderType = from.holderType, fromHolderId = from.holderId, fromHolderNameSnapshot = from.holderName,
                    toHolderType = context.plan.target.holderType, toHolderId = context.plan.target.holderId, toHolderNameSnapshot = context.plan.target.holderName,
                    transferredAt = now, receivedAt = now, requestId = stableRequestId("$requestId:$index:${source.id}"),
                    handoverPackageCount = draft.handoverCount, receivedPackageCount = draft.receivedCount,
                    handoverWeightKg = draft.currentWeightKg, receivedWeightKg = draft.receivedWeight,
                    openedPackageCount = requireNotNull(draft.openedCount), damagedPackageCount = requireNotNull(draft.damagedCount),
                ),
            )
        }
    }

    private fun stableRequestId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    private suspend fun organizationId(): String = readService.organizationId()
}
