package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.PrepareLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.RecordLogisticsCostCommand
import com.verto.app.feature.shipment.domain.model.SaveShipmentRouteCommand
import javax.inject.Inject

/** Testable application seam for the multi-write planning orchestration. */
internal interface LogisticsPlanningOperations {
    suspend fun organizationId(): String
    suspend fun aggregate(organizationId: String, shipmentId: String): LogisticsShipmentAggregate?
    suspend fun prepare(organizationId: String, command: PrepareLogisticsShipmentCommand)
    suspend fun saveRoute(organizationId: String, command: SaveShipmentRouteCommand)
    suspend fun recordCost(organizationId: String, command: RecordLogisticsCostCommand)
    suspend fun saveDocument(command: SaveLogisticsDocumentUseCase.Command)
    suspend fun markReady(organizationId: String, shipmentId: String, requestId: String): LogisticsShipment
    suspend fun removeDraft(organizationId: String, shipmentId: String, privateUri: String)
}

internal class DefaultLogisticsPlanningOperations @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val prepareShipment: PrepareLogisticsShipmentUseCase,
    private val saveRoutePlan: SaveDynamicRoutePlanUseCase,
    private val recordLogisticsCost: RecordLogisticsCostUseCase,
    private val saveLogisticsDocument: SaveLogisticsDocumentUseCase,
    private val markShipmentReady: MarkLogisticsShipmentReadyUseCase,
) : LogisticsPlanningOperations {
    override suspend fun organizationId(): String = readService.organizationId()

    override suspend fun aggregate(organizationId: String, shipmentId: String): LogisticsShipmentAggregate? =
        readService.aggregate(organizationId, shipmentId)

    override suspend fun prepare(organizationId: String, command: PrepareLogisticsShipmentCommand) {
        prepareShipment(organizationId, command)
    }

    override suspend fun saveRoute(organizationId: String, command: SaveShipmentRouteCommand) {
        saveRoutePlan(organizationId, command)
    }

    override suspend fun recordCost(organizationId: String, command: RecordLogisticsCostCommand) {
        recordLogisticsCost(organizationId, command)
    }

    override suspend fun saveDocument(command: SaveLogisticsDocumentUseCase.Command) {
        saveLogisticsDocument(command)
    }

    override suspend fun markReady(organizationId: String, shipmentId: String, requestId: String): LogisticsShipment =
        markShipmentReady(organizationId, shipmentId, requestId)

    override suspend fun removeDraft(organizationId: String, shipmentId: String, privateUri: String) {
        saveLogisticsDocument.removeDraft(organizationId, shipmentId, privateUri)
    }
}
