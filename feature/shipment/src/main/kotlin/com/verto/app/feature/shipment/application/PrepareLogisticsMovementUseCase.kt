package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.PrepareLogisticsMovementCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

/**
 * Confirms execution facts for the next future leg without starting physical movement.
 * Planned values remain untouched for plan-vs-actual reporting.
 */
class PrepareLogisticsMovementUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: PrepareLogisticsMovementCommand,
    ): LogisticsShipmentLeg {
        validateCommand(organizationId, command)
        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val requestedLeg = aggregate.legs.singleOrNull { it.id == command.legId }
            ?: error("مرحلة الحركة غير موجودة")
        if (store.isRequestProcessed(organizationId, command.requestId)) return requestedLeg
        val nextLeg = nextLegForPreparation(aggregate, command.legId)

        val carrier = store.getPartner(organizationId, command.facts.carrierPartnerId)
            ?: error("شركة النقل الفعلية غير محفوظة")
        require(carrier.role == LogisticsPartnerRole.CARRIER || carrier.role == LogisticsPartnerRole.FREIGHT_FORWARDER) {
            "الجهة المختارة ليست شركة نقل"
        }

        val actual = nextLeg.copy(
            carrierPartnerId = carrier.id,
            representativeNameSnapshot = command.facts.representativeName?.trim()?.takeIf(String::isNotBlank),
            representativePhoneSnapshot = command.facts.representativePhone?.let(::normalizePhone)?.takeIf(String::isNotBlank),
            packageCount = command.facts.packageCount,
            weightKg = command.facts.weightKg,
        )
        LogisticsValidation.validateLeg(actual)

        val updatedShipment = if (aggregate.shipment.state == LogisticsShipmentState.READY) {
            LogisticsLifecyclePolicy.requireTransition(LogisticsShipmentState.READY, LogisticsShipmentState.WAITING_DEPARTURE)
            aggregate.shipment.copy(state = LogisticsShipmentState.WAITING_DEPARTURE)
        } else aggregate.shipment

        val event = preparedEvent(organizationId, aggregate, actual, command)
        store.saveOperationalUpdate(updatedShipment, emptyList(), listOf(actual), event)
        return actual
    }

    private fun validateCommand(organizationId: String, command: PrepareLogisticsMovementCommand) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.legId.isNotBlank()) { "legId is required" }
        require(command.facts.carrierPartnerId.isNotBlank()) { "شركة النقل الفعلية مطلوبة" }
        require(command.facts.packageCount > 0) { "عدد الكراتين الفعلي يجب أن يكون أكبر من صفر" }
        require(command.facts.weightKg.signum() > 0) { "الوزن الفعلي يجب أن يكون أكبر من صفر" }
        require(command.preparedAt >= 0L) { "preparedAt must be non-negative" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        command.facts.representativePhone?.let { requireValidPhone(it) }
    }

    private fun nextLegForPreparation(aggregate: LogisticsShipmentAggregate, legId: String): LogisticsShipmentLeg {
        require(aggregate.shipment.state == LogisticsShipmentState.READY || aggregate.shipment.state == LogisticsShipmentState.AT_STATION) {
            "يمكن تجهيز حركة فقط لخطة معتمدة أو محطة مكتملة"
        }
        val nextLeg = aggregate.legs
            .filter { it.status == LogisticsLegStatus.PLANNED && it.actualDepartureAt == null && it.supersededAt == null }
            .minByOrNull { it.sequence } ?: error("لا توجد حركة مستقبلية للتجهيز")
        require(nextLeg.id == legId) { "يمكن تجهيز الحركة التالية فقط" }
        require(nextLeg.actualArrivalAt == null) { "الحركة وصلت فعليًا ولا يمكن تجهيزها مجددًا" }
        require(nextLeg.mode.name != "UNSPECIFIED") { "نوع النقل غير محدد لهذه الحركة" }
        if (aggregate.shipment.state == LogisticsShipmentState.AT_STATION) {
            val station = aggregate.milestones.singleOrNull { it.id == nextLeg.fromMilestoneId }
                ?: error("محطة الانطلاق الحالية غير موجودة")
            require(station.handlingStatus == LogisticsMilestoneHandlingStatus.UNLOADED) {
                "أكد استلام وتفريغ المحطة الحالية قبل تجهيز الحركة التالية"
            }
        }
        return nextLeg
    }

    private fun preparedEvent(
        organizationId: String,
        aggregate: LogisticsShipmentAggregate,
        actual: LogisticsShipmentLeg,
        command: PrepareLogisticsMovementCommand,
    ) = LogisticsEvent(
        id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
        type = LogisticsEventType.MOVEMENT_PREPARED, occurredAt = command.preparedAt, recordedAt = clock.now(),
        employeeId = aggregate.shipment.assignee?.employeeId, employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
        requestId = command.requestId,
        payload = mapOf(
            "legId" to actual.id, "carrierPartnerId" to actual.carrierPartnerId,
            "packageCount" to actual.packageCount.toString(), "weightKg" to actual.weightKg?.toPlainString().orEmpty(),
            "plannedCarrierPartnerId" to actual.plannedCarrierPartnerId.orEmpty(),
            "plannedPackageCount" to actual.plannedPackageCount?.toString().orEmpty(),
            "plannedWeightKg" to actual.plannedWeightKg?.toPlainString().orEmpty(), "movementStarted" to "false",
        ),
    )

    private fun requireValidPhone(raw: String) {
        val normalized = normalizePhone(raw)
        val digits = normalized.count(Char::isDigit)
        require(digits in 7..15) { "أدخل رقم هاتف صحيح" }
    }

    private fun normalizePhone(raw: String): String {
        return raw.filter(Char::isDigit)
    }
}
