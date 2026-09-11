package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordLogisticsCostCommand
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class RecordLogisticsCostUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: RecordLogisticsCostCommand,
    ): LogisticsCost {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) {
            return store.findCostByRequest(organizationId, command.shipmentId, command.requestId)
                ?: error("Request was already processed by another logistics operation")
        }
        require(aggregate.shipment.state != LogisticsShipmentState.CLOSED) { "Closed shipment cannot record costs" }
        require(aggregate.shipment.state != LogisticsShipmentState.CANCELLED) { "Cancelled shipment cannot record costs" }
        command.servicePartnerId?.let { partnerId ->
            require(aggregate.partners.any { it.partnerId == partnerId }) {
                "Cost service partner must be linked to shipment"
            }
        }
        require(listOf(command.legId, command.milestoneId, command.sourceId).count { it != null } <= 1) {
            "Logistics cost can have only one scope"
        }
        command.legId?.let { legId ->
            require(aggregate.legs.any { it.id == legId }) { "Cost leg does not belong to shipment" }
        }
        command.milestoneId?.let { milestoneId ->
            require(aggregate.milestones.any { it.id == milestoneId }) { "Cost milestone does not belong to shipment" }
        }
        command.sourceId?.let { sourceId ->
            require(aggregate.sources.any { it.id == sourceId }) { "Cost source does not belong to shipment" }
        }
        val currency = LogisticsCurrencyPolicy.normalizeIso4217(command.currency)
        LogisticsCurrencyPolicy.requireRate(currency, command.exchangeRateSnapshot, command.exchangeRateDate)
        val computedBase = command.amount.multiply(command.exchangeRateSnapshot)
        require(computedBase.compareTo(command.baseCurrencyAmount) == 0) {
            "baseCurrencyAmount must equal amount × exchangeRateSnapshot"
        }

        val isCustomsScoped = command.milestoneId?.let { milestoneId ->
            aggregate.milestones.firstOrNull { it.id == milestoneId }?.type?.name == "CUSTOMS"
        } == true
        if (isCustomsScoped || command.type.name == "CUSTOMS_DUTY" || command.type.name == "CLEARANCE") {
            require(currency == LogisticsCurrencyPolicy.BASE_CURRENCY) { "Customs costs must be recorded in SDG" }
            require(command.note.isNotBlank()) { "Customs cost description is required" }
        }
        val description = command.note.trim().ifBlank { command.reference?.trim().orEmpty() }.ifBlank { command.type.name }

        val cost = LogisticsCost(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            type = command.type,
            amount = command.amount,
            currency = currency,
            exchangeRateSnapshot = command.exchangeRateSnapshot,
            exchangeRateDate = command.exchangeRateDate,
            baseCurrencyAmount = command.baseCurrencyAmount,
            status = command.status,
            servicePartnerId = command.servicePartnerId,
            legId = command.legId,
            milestoneId = command.milestoneId,
            sourceId = command.sourceId,
            reference = command.reference?.trim()?.takeIf(String::isNotBlank),
            note = command.note.trim(),
            description = description,
            requestId = command.requestId,
        )
        LogisticsValidation.validateCost(cost)
        val recordedAt = clock.now()
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            type = LogisticsEventType.COST_RECORDED,
            occurredAt = recordedAt,
            recordedAt = recordedAt,
            employeeId = aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf("costId" to cost.id, "costType" to cost.type.name, "status" to cost.status.name),
        )
        store.saveCost(cost, event)
        return cost
    }
}
