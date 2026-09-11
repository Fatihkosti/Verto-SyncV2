package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.LogisticsRouteAnalyticsUseCase
import com.verto.app.feature.shipment.application.model.LogisticsUnifiedReadRecord
import com.verto.app.feature.shipment.application.model.LogisticsUnifiedSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState

internal fun unifiedCenterState(
    access: LogisticsV2Access,
    records: List<LogisticsUnifiedReadRecord>,
    nowMillis: Long,
    aggregatesById: Map<String, LogisticsShipmentAggregate>,
): LogisticsCenterUiState {
    if (!access.canView) return LogisticsCenterUiState.PermissionDenied
    val cards = records.mapNotNull { record ->
        record.v2Shipment?.toCard(aggregatesById[record.id], nowMillis)?.copy(source = LogisticsUnifiedSource.V2.name)
    }
    val groups = LogisticsCenterGroup.entries.associateWith { group -> cards.filter { it.group == group } }
    return LogisticsCenterUiState.Content(
        metrics = LogisticsCenterMetrics(
            active = cards.count { it.state != LogisticsShipmentState.CLOSED && it.state != LogisticsShipmentState.CANCELLED },
            delayed = cards.count { it.delayed },
            atCustoms = cards.count { it.atCustoms },
            waitingReceiving = cards.count {
                it.state == LogisticsShipmentState.AT_STATION || it.state == LogisticsShipmentState.ARRIVED ||
                    it.state == LogisticsShipmentState.RECEIVING || it.state == LogisticsShipmentState.PARTIAL
            },
        ),
        groups = groups,
        analytics = LogisticsRouteAnalyticsUseCase()(aggregatesById.values, nowMillis),
    )
}
