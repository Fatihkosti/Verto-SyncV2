package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import java.util.UUID
import javax.inject.Inject

internal class LogisticsMovementPreparationWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val prepareMovementUseCase: PrepareLogisticsMovementUseCase,
    private val upsertPartner: UpsertLogisticsPartnerUseCase,
    private val linkPartner: LinkLogisticsPartnerUseCase,
    private val settlementWriter: LogisticsSettlementWriter
) {
    suspend fun prepareMovement(
        shipmentId: String,
        draft: LogisticsMovementPreparationDraft,
        requestId: String,
    ) {
        require(draft.isValid) { "بيانات تجهيز الحركة غير مكتملة" }
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val leg = aggregate.legs.singleOrNull { it.id == draft.legId } ?: error("مرحلة الحركة غير موجودة")
        val partner = resolveMovementCarrier(organizationId, shipmentId, draft, aggregate)
        prepareMovementUseCase(
            organizationId,
            PrepareLogisticsMovementCommand(
                shipmentId = shipmentId,
                legId = leg.id,
                facts = LogisticsMovementExecutionFacts(
                    carrierPartnerId = partner.id,
                    representativeName = draft.representativeName.trim().takeIf(String::isNotBlank),
                    representativePhone = draft.normalizedPhone.takeIf(String::isNotBlank),
                    packageCount = requireNotNull(draft.packageCountValue),
                    weightKg = requireNotNull(draft.weightKgValue),
                ),
                preparedAt = System.currentTimeMillis(),
                requestId = stableRequestId("$requestId:movement-preparation"),
            ),
        )
        if (draft.hasCost) recordPreparedMovementCost(shipmentId, leg.id, partner.id, draft, requestId)
    }

    private suspend fun resolveMovementCarrier(
        organizationId: String,
        shipmentId: String,
        draft: LogisticsMovementPreparationDraft,
        aggregate: LogisticsShipmentAggregate,
    ): LogisticsPartner {
        val existing = draft.carrierPartnerId.takeIf(String::isNotBlank)?.let { readService.partner(organizationId, it) }
        val partner = LogisticsPartner(
            id = existing?.id ?: stableRequestId("v239-carrier:$organizationId:${draft.carrierName.trim().lowercase()}"),
            organizationId = organizationId,
            name = draft.carrierName.trim(),
            role = existing?.role?.takeIf { it == LogisticsPartnerRole.CARRIER || it == LogisticsPartnerRole.FREIGHT_FORWARDER }
                ?: LogisticsPartnerRole.CARRIER,
            phone = draft.normalizedPhone.takeIf(String::isNotBlank),
            representativeName = draft.representativeName.trim().takeIf(String::isNotBlank),
            representativePhone = draft.normalizedPhone.takeIf(String::isNotBlank),
            notes = existing?.notes.orEmpty(),
        )
        upsertPartner(partner)
        if (aggregate.partners.none { it.partnerId == partner.id && it.role == partner.role }) {
            linkPartner(organizationId, shipmentId, partner.id, partner.role)
        }
        return partner
    }

    private suspend fun recordPreparedMovementCost(
        shipmentId: String,
        legId: String,
        partnerId: String,
        draft: LogisticsMovementPreparationDraft,
        requestId: String,
    ) {
        val cost = settlementWriter.addCost(
            shipmentId,
            LogisticsCostDraft(
                type = LogisticsCostType.FREIGHT,
                amount = requireNotNull(draft.amountValue).toPlainString(),
                currency = draft.currency.trim().uppercase(),
                exchangeRate = if (draft.currency.trim().uppercase() == "SDG") "1" else requireNotNull(draft.exchangeRateValue).toPlainString(),
                status = LogisticsCostStatus.ACTUAL,
                servicePartnerId = partnerId,
                legId = legId,
                note = "تكلفة الحركة الفعلية",
            ),
            stableRequestId("$requestId:movement-cost"),
        )
        if (!draft.confirmPaid) return
        settlementWriter.confirmCostPayment(shipmentId, cost.id, stableRequestId("$requestId:movement-payment"))
        draft.proof?.let { settlementWriter.saveCostProof(shipmentId, cost.id, it, stableRequestId("$requestId:movement-proof")) }
    }

    private fun stableRequestId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    private suspend fun organizationId(): String = readService.organizationId()
}
