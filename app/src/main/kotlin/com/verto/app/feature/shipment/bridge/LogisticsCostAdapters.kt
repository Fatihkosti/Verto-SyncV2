package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.LogisticsCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsCostEntity
import com.verto.app.data.local.entity.LogisticsLateCostAdjustmentEntity
import com.verto.app.data.local.entity.LogisticsLateCostAllocationEntity
import com.verto.app.feature.inventory.domain.port.ApplyShipmentLandedCostPort
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAdjustment
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAdjustmentIdentity
import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAllocation
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryCostPort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.serialization.json.Json

internal class LogisticsCostStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()
    private val json = Json { encodeDefaults = true }

    suspend fun saveCost(cost: LogisticsCost, event: LogisticsEvent) {
        LogisticsValidation.validateCost(cost)
        requireSameTenant(cost.organizationId, event.organizationId)
        require(cost.shipmentId == event.shipmentId) { "Cost/event belongs to another shipment" }
        database.withTransaction {
            require(dao.getShipment(cost.organizationId, cost.shipmentId) != null) { "Shipment not found" }
            cost.servicePartnerId?.let { partnerId ->
                require(dao.getPartner(cost.organizationId, partnerId) != null) { "Cost service partner not found" }
            }
            cost.legId?.let { legId ->
                require(dao.getLegs(cost.organizationId, cost.shipmentId).any { it.id == legId }) {
                    "Cost leg does not belong to shipment"
                }
            }
            cost.milestoneId?.let { milestoneId ->
                require(dao.getMilestones(cost.organizationId, cost.shipmentId).any { it.id == milestoneId }) {
                    "Cost milestone does not belong to shipment"
                }
            }
            cost.sourceId?.let { sourceId ->
                require(dao.getSources(cost.organizationId, cost.shipmentId).any { it.id == sourceId }) {
                    "Cost source does not belong to shipment"
                }
            }
            cost.recoveryId?.let { recoveryId ->
                require(dao.getRecoveries(cost.organizationId, cost.shipmentId).any { it.id == recoveryId }) {
                    "Cost recovery does not belong to shipment"
                }
            }
            dao.insertCost(cost.toEntityV2())
            dao.insertEvent(event.toEntityV2(json))
        }
    }

    suspend fun getCosts(organizationId: String, shipmentId: String): List<LogisticsCost> =
        dao.getCosts(organizationId, shipmentId).map(LogisticsCostEntity::toDomainV2)

    suspend fun findCostByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsCost? = dao.findCostByRequest(organizationId, shipmentId, requestId)?.toDomainV2()

    suspend fun saveCostAllocations(
        organizationId: String,
        shipmentId: String,
        allocations: List<LogisticsCostAllocation>,
        event: LogisticsEvent,
    ) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        requireSameTenant(organizationId, event.organizationId)
        require(event.shipmentId == shipmentId) { "Cost settlement event belongs to another shipment" }
        require(event.type == LogisticsEventType.COST_SETTLED) { "Cost allocation persistence requires COST_SETTLED event" }
        require(allocations.all { it.shipmentId == shipmentId }) { "Cost allocation belongs to another shipment" }

        database.withTransaction {
            require(dao.getShipment(organizationId, shipmentId) != null) { "Shipment not found" }
            val shipmentLineIds = dao.getLines(organizationId, shipmentId).mapTo(mutableSetOf()) { it.id }
            require(allocations.all { it.shipmentLineId in shipmentLineIds }) {
                "Cost allocation shipment line does not belong to shipment"
            }
            dao.insertCostAllocations(allocations.map { it.toEntityV2(organizationId) })
            dao.insertEvent(event.toEntityV2(json))
        }
    }

    suspend fun saveLateCostAdjustment(
        adjustment: LogisticsLateCostAdjustment,
        allocations: List<LogisticsLateCostAllocation>,
        event: LogisticsEvent,
    ): LogisticsLateCostAdjustment {
        requireSameTenant(adjustment.organizationId, event.organizationId)
        require(adjustment.shipmentId == event.shipmentId) { "Late-cost event belongs to another shipment" }
        require(allocations.isNotEmpty()) { "Late-cost allocation cannot be empty" }
        require(allocations.all { it.organizationId == adjustment.organizationId && it.shipmentId == adjustment.shipmentId && it.adjustmentId == adjustment.id }) {
            "Late-cost allocation identity mismatch"
        }
        return database.withTransaction {
            dao.findLateCostAdjustmentByRequest(adjustment.organizationId, adjustment.shipmentId, adjustment.requestId)?.let {
                return@withTransaction it.toDomainV2()
            }
            require(dao.getShipment(adjustment.organizationId, adjustment.shipmentId) != null) { "Shipment not found" }
            require(dao.getCosts(adjustment.organizationId, adjustment.shipmentId).any { it.id == adjustment.costId }) {
                "Late-cost adjustment cost not found"
            }
            val shipmentLineIds = dao.getLines(adjustment.organizationId, adjustment.shipmentId).mapTo(mutableSetOf()) { it.id }
            require(allocations.all { it.shipmentLineId in shipmentLineIds }) { "Late-cost allocation line not found" }
            dao.insertLateCostAdjustment(adjustment.toEntityV2())
            dao.insertLateCostAllocations(allocations.map { it.toEntityV2() })
            dao.insertEvent(event.toEntityV2(json))
            adjustment
        }
    }

    suspend fun getLateCostAdjustments(organizationId: String, shipmentId: String): List<LogisticsLateCostAdjustment> =
        dao.getLateCostAdjustments(organizationId, shipmentId).map(LogisticsLateCostAdjustmentEntity::toDomainV2)

    suspend fun getLateCostAllocations(organizationId: String, shipmentId: String): List<LogisticsLateCostAllocation> =
        dao.getLateCostAllocations(organizationId, shipmentId).map(LogisticsLateCostAllocationEntity::toDomainV2)

    suspend fun findLateCostAdjustmentByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsLateCostAdjustment? = dao.findLateCostAdjustmentByRequest(organizationId, shipmentId, requestId)?.toDomainV2()
}

class InventoryLogisticsV2CostAdapter @Inject constructor(
    private val inventory: ApplyShipmentLandedCostPort,
) : LogisticsInventoryCostPort {
    override suspend fun applyReceivingPostingUnitPrice(
        postingId: String,
        shipmentId: String,
        unitPrice: BigDecimal,
    ): Result<Unit> = inventory.apply(
        postingId = postingId,
        shipmentId = shipmentId,
        unitPrice = unitPrice,
    )
}

internal fun LogisticsCost.toEntityV2() = LogisticsCostEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    type = type.name,
    amount = amount.toPlainString(),
    currency = currency,
    exchangeRateSnapshot = exchangeRateSnapshot.toPlainString(),
    exchangeRateDate = exchangeRateDate,
    baseCurrencyAmount = baseCurrencyAmount.toPlainString(),
    status = status.name,
    legId = legId,
    milestoneId = milestoneId,
    sourceId = sourceId,
    servicePartnerId = servicePartnerId,
    reference = reference,
    note = note,
    description = description,
    paymentState = paymentState.name,
    cashReference = cashReference,
    cashPostedBaseAmount = cashPostedBaseAmount?.toPlainString(),
    cashPostedAt = cashPostedAt,
    reversalOfCostId = reversalOfCostId,
    recoveryId = recoveryId,
    requestId = requestId,
)

internal fun LogisticsCostEntity.toDomainV2() = LogisticsCost(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    type = LogisticsCostType.valueOf(type),
    amount = BigDecimal(amount),
    currency = currency,
    exchangeRateSnapshot = BigDecimal(exchangeRateSnapshot),
    exchangeRateDate = exchangeRateDate,
    baseCurrencyAmount = BigDecimal(baseCurrencyAmount),
    status = LogisticsCostStatus.valueOf(status),
    legId = legId,
    milestoneId = milestoneId,
    sourceId = sourceId,
    servicePartnerId = servicePartnerId,
    reference = reference,
    note = note,
    description = description,
    paymentState = LogisticsCostPaymentState.valueOf(paymentState),
    cashReference = cashReference,
    cashPostedBaseAmount = cashPostedBaseAmount?.let(::BigDecimal),
    cashPostedAt = cashPostedAt,
    reversalOfCostId = reversalOfCostId,
    recoveryId = recoveryId,
    requestId = requestId,
)

internal fun LogisticsCostAllocation.toEntityV2(organizationId: String): LogisticsCostAllocationEntity {
    val wholeAmount = amount.setScale(0, RoundingMode.UNNECESSARY)
    require(wholeAmount.signum() >= 0) { "Landed-cost allocation cannot be negative" }
    return LogisticsCostAllocationEntity(
        organizationId = organizationId,
        id = id,
        shipmentId = shipmentId,
        shipmentLineId = shipmentLineId,
        amount = wholeAmount.toPlainString(),
    )
}

internal fun LogisticsCostAllocationEntity.toDomainV2() = LogisticsCostAllocation(
    id = id,
    shipmentId = shipmentId,
    shipmentLineId = shipmentLineId,
    amount = BigDecimal(amount),
)


internal fun LogisticsLateCostAdjustment.toEntityV2() = LogisticsLateCostAdjustmentEntity().also { row ->
    row.organizationId = organizationId; row.id = id; row.shipmentId = shipmentId; row.costId = costId
    row.recordedAt = recordedAt; row.employeeId = employeeId; row.employeeNameSnapshot = employeeNameSnapshot; row.requestId = requestId
}

internal fun LogisticsLateCostAdjustmentEntity.toDomainV2() = LogisticsLateCostAdjustment(
    identity = LogisticsLateCostAdjustmentIdentity(id, organizationId, shipmentId, costId),
    recordedAt = recordedAt, employee = employeeId?.let { LogisticsAssigneeSnapshot(it, employeeNameSnapshot.orEmpty()) }, requestId = requestId,
)

internal fun LogisticsLateCostAllocation.toEntityV2() = LogisticsLateCostAllocationEntity(
    organizationId = organizationId, id = id, adjustmentId = adjustmentId, shipmentId = shipmentId,
    shipmentLineId = shipmentLineId, amount = amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString(),
)

internal fun LogisticsLateCostAllocationEntity.toDomainV2() = LogisticsLateCostAllocation(
    id = id, organizationId = organizationId, adjustmentId = adjustmentId, shipmentId = shipmentId,
    shipmentLineId = shipmentLineId, amount = BigDecimal(amount),
)
