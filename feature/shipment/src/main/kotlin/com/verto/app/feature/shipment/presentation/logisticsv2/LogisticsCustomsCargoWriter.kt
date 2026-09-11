package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import javax.inject.Inject

internal class LogisticsCustomsCargoWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val startCustoms: StartLogisticsCustomsUseCase,
    private val completeCustoms: CompleteLogisticsCustomsUseCase,
    private val recordCargoRepackUseCase: RecordCargoRepackUseCase,
) {
    suspend fun customsPickupDraft(shipmentId: String): LogisticsCustomsPickupDraft {
        val aggregate = readService.aggregate(organizationId(), shipmentId) ?: error("لم يتم العثور على الشحنة")
        val milestone = LogisticsV240ExecutionPolicy.customsHostMilestone(aggregate)
            ?.takeIf { it.arrivedAt != null && it.departedAt == null &&
                it.handlingStatus == LogisticsMilestoneHandlingStatus.UNLOADED && it.customsStartedAt == null }
            ?: error("المحطة المحددة للجمارك ليست جاهزة لاستلام المخلص")
        val cargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate) ?: error("عدد الكراتين المؤكد غير متوفر")
        val brokers = readService.partners(organizationId()).filter { it.role == LogisticsPartnerRole.CUSTOMS_BROKER }
        require(brokers.isNotEmpty()) { "أضف مخلصًا جمركيًا أولًا" }
        return LogisticsCustomsPickupDraft(
            handoff = LogisticsCustodyHandoffDraft(LogisticsCustodyHandoffScope(shipmentId), currentCargo = cargo),
            milestoneId = milestone.id,
            brokers = brokers,
            selectedBrokerId = brokers.singleOrNull()?.id.orEmpty(),
        )
    }

    suspend fun startCustoms(shipmentId: String, draft: LogisticsCustomsPickupDraft, requestId: String) {
        require(draft.shipmentId == shipmentId && draft.isValid) { "بيانات استلام المخلص غير صحيحة" }
        startCustoms(
            organizationId(),
            StartLogisticsCustomsCommand(
                shipmentId = shipmentId, milestoneId = draft.milestoneId,
                brokerPartnerId = requireNotNull(draft.selectedBroker).id,
                receivedAt = System.currentTimeMillis(), requestId = requestId,
                receipt = LogisticsCustodyReceiptCounts(
                    handoverPackageCount = requireNotNull(draft.handoff.handoverCount),
                    receivedPackageCount = requireNotNull(draft.handoff.receivedCount),
                    openedPackageCount = requireNotNull(draft.handoff.openedCount),
                    damagedPackageCount = requireNotNull(draft.handoff.damagedCount),
                    discrepancyNote = draft.handoff.discrepancyNote,
                ),
            ),
        )
    }

    suspend fun completeCustoms(shipmentId: String, requestId: String) {
        val aggregate = readService.aggregate(organizationId(), shipmentId) ?: error("لم يتم العثور على الشحنة")
        val milestone = LogisticsV240ExecutionPolicy.customsHostMilestone(aggregate)
            ?.takeIf { it.customsStartedAt != null && it.customsCompletedAt == null }
            ?: error("لا توجد معاملة جمركية قيد التنفيذ")
        completeCustoms(
            organizationId(),
            CompleteLogisticsCustomsCommand(shipmentId, milestone.id, System.currentTimeMillis(), requestId),
        )
    }

    suspend fun cargoRepackDraft(shipmentId: String): LogisticsCargoRepackDraft {
        val aggregate = readService.aggregate(organizationId(), shipmentId) ?: error("لم يتم العثور على الشحنة")
        val cargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate) ?: error("عدد الكراتين المؤكد غير متوفر")
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values
        require(positions.isNotEmpty() && positions.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }.size == 1) {
            "إعادة التعبئة تتطلب مسؤولًا حاليًا واحدًا عن كامل الحمولة"
        }
        val milestoneId = aggregate.milestones.sortedByDescending { it.order }
            .firstOrNull { it.arrivedAt != null && it.departedAt == null }?.id
        return LogisticsCargoRepackDraft(
            shipmentId = shipmentId,
            milestoneId = milestoneId,
            previousCargo = cargo,
        )
    }

    suspend fun recordCargoRepack(shipmentId: String, draft: LogisticsCargoRepackDraft, requestId: String) {
        require(draft.shipmentId == shipmentId && draft.isValid) { "بيانات إعادة التعبئة غير صحيحة" }
        recordCargoRepackUseCase(
            organizationId(),
            RecordCargoRepackCommand(
                shipmentId = shipmentId,
                milestoneId = draft.milestoneId,
                change = LogisticsCargoRepackChange(
                    previous = draft.previousCargo,
                    updated = LogisticsCargoSnapshot(requireNotNull(draft.newCount), draft.newWeight),
                    reason = draft.reason,
                    note = draft.note,
                ),
                occurredAt = System.currentTimeMillis(),
                requestId = requestId,
                proof = draft.proof,
            ),
        )
    }

    private suspend fun organizationId(): String = readService.organizationId()
}
