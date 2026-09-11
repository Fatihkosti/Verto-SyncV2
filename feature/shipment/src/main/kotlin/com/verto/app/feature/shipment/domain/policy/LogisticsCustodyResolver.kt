package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyPosition
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource

object LogisticsCustodyResolver {
    fun currentForSource(
        aggregate: LogisticsShipmentAggregate,
        source: LogisticsShipmentSource,
    ): LogisticsCustodyPosition {
        require(source.shipmentId == aggregate.shipment.id) { "Source belongs to another shipment" }
        val sourceHistory = aggregate.custodyHandoffs.filter { it.sourceId == source.id }
        val shipmentWideHistory = if (aggregate.shipment.startedAt != null) {
            aggregate.custodyHandoffs.filter { it.sourceId == null }
        } else {
            emptyList()
        }
        val latest = (sourceHistory + shipmentWideHistory)
            .maxWithOrNull(compareBy({ it.receivedAt }, { it.transferredAt }, { it.id }))
        return if (latest == null) {
            LogisticsCustodyPosition(
                holderType = LogisticsCustodyHolderType.SUPPLIER,
                holderId = source.supplierId,
                holderName = source.supplierNameSnapshot,
                receivedAt = null,
            )
        } else {
            LogisticsCustodyPosition(
                holderType = latest.toHolderType,
                holderId = latest.toHolderId,
                holderName = latest.toHolderNameSnapshot,
                receivedAt = latest.receivedAt,
            )
        }
    }

    fun currentForAllSources(aggregate: LogisticsShipmentAggregate): Map<String, LogisticsCustodyPosition> =
        aggregate.sources.associate { it.id to currentForSource(aggregate, it) }

    fun currentCargoSnapshot(aggregate: LogisticsShipmentAggregate): LogisticsCargoSnapshot? {
        val latest = aggregate.custodyHandoffs
            .filter { it.receivedPackageCount != null }
            .maxWithOrNull(compareBy({ it.receivedAt }, { it.id }))
        latest?.receivedPackageCount?.let { packageCount ->
            return LogisticsCargoSnapshot(packageCount = packageCount, weightKg = latest.receivedWeightKg)
        }
        val initial = aggregate.shipment.transportDetails ?: return null
        return initial.packageCount?.let { LogisticsCargoSnapshot(packageCount = it, weightKg = initial.weightKg) }
    }
}
