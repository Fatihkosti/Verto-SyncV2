package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.LogisticsShortageSettlementEntity
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsShortage
import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsShortageCompensation
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlement
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementAudit
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementIdentity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType
import java.math.BigDecimal
import kotlinx.serialization.json.Json

internal class LogisticsV232SettlementStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()
    private val json = Json { encodeDefaults = true }

    suspend fun save(
        settlement: LogisticsShortageSettlement,
        updatedShortage: LogisticsShortage,
        event: LogisticsEvent,
    ): LogisticsShortageSettlement {
        requireSameTenant(settlement.organizationId, updatedShortage.identity.organizationId)
        requireSameTenant(settlement.organizationId, event.organizationId)
        require(settlement.shipmentId == updatedShortage.identity.shipmentId && settlement.shipmentId == event.shipmentId) {
            "Shortage settlement belongs to another shipment"
        }
        return database.withTransaction {
            dao.findShortageSettlementByRequest(settlement.organizationId, settlement.shipmentId, settlement.requestId)?.let {
                return@withTransaction it.toDomainV2()
            }
            val current = dao.getShortages(settlement.organizationId, settlement.shipmentId)
                .singleOrNull { it.id == settlement.shortageId } ?: error("Shortage not found")
            require(current.id == updatedShortage.identity.id) { "Updated shortage identity mismatch" }
            require(current.remainingMissingQuantity >= settlement.quantity) { "Settlement quantity exceeds remaining shortage" }
            require(dao.updateShortage(updatedShortage.toEntityV2()) == 1) { "Shortage update failed" }
            dao.insertShortageSettlement(settlement.toEntityV2())
            dao.insertEvent(event.toEntityV2(json))
            settlement
        }
    }

    suspend fun list(organizationId: String, shipmentId: String): List<LogisticsShortageSettlement> =
        dao.getShortageSettlements(organizationId, shipmentId).map(LogisticsShortageSettlementEntity::toDomainV2)

    suspend fun findByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsShortageSettlement? =
        dao.findShortageSettlementByRequest(organizationId, shipmentId, requestId)?.toDomainV2()
}

internal fun LogisticsShortageSettlement.toEntityV2() = LogisticsShortageSettlementEntity().also { row ->
    row.organizationId = organizationId; row.id = id; row.shipmentId = shipmentId; row.shortageId = shortageId
    row.type = type.name; row.quantity = quantity; row.compensationAmount = compensationAmount?.toPlainString(); row.currency = currency
    row.exchangeRateSnapshot = exchangeRateSnapshot?.toPlainString(); row.baseCurrencyAmount = baseCurrencyAmount?.toPlainString()
    row.occurredAt = occurredAt; row.employeeId = employeeId; row.employeeNameSnapshot = employeeNameSnapshot
    row.note = note; row.requestId = requestId
}

internal fun LogisticsShortageSettlementEntity.toDomainV2() = LogisticsShortageSettlement(
    identity = LogisticsShortageSettlementIdentity(id, organizationId, shipmentId, shortageId),
    type = LogisticsShortageSettlementType.valueOf(type), quantity = quantity,
    compensation = compensationAmount?.let { amount -> LogisticsShortageCompensation(
        amount = BigDecimal(amount), currency = requireNotNull(currency),
        exchangeRateSnapshot = BigDecimal(requireNotNull(exchangeRateSnapshot)),
        baseCurrencyAmount = BigDecimal(requireNotNull(baseCurrencyAmount)),
    ) },
    audit = LogisticsShortageSettlementAudit(
        occurredAt = occurredAt,
        employee = employeeId?.let { LogisticsAssigneeSnapshot(it, employeeNameSnapshot.orEmpty()) },
        note = note, requestId = requestId,
    ),
)
