package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.*
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

internal data class LogisticsPlanningWorkflowCostMoney(
    val type: LogisticsCostType,
    val amount: BigDecimal,
    val currency: String,
    val exchangeRate: BigDecimal,
    val status: LogisticsCostStatus,
)

internal data class LogisticsPlanningWorkflowCostScope(
    val servicePartnerId: String? = null,
    val sourceInvoiceId: String? = null,
    val sourceId: String? = null,
    val milestoneId: String? = null,
    val legId: String? = null,
)

internal data class LogisticsPlanningWorkflowCost(
    val money: LogisticsPlanningWorkflowCostMoney,
    val scope: LogisticsPlanningWorkflowCostScope,
    val reference: String? = null,
    val note: String = "",
)

internal data class LogisticsPlanningWorkflowDocumentSource(
    val stagedPrivateUri: String,
    val displayName: String,
    val mimeType: String,
    val type: LogisticsDocumentType,
)

internal data class LogisticsPlanningWorkflowDocumentScope(
    val sourceInvoiceId: String? = null,
    val milestoneId: String? = null,
    val legId: String? = null,
)

internal data class LogisticsPlanningWorkflowDocument(
    val source: LogisticsPlanningWorkflowDocumentSource,
    val scope: LogisticsPlanningWorkflowDocumentScope,
)

internal data class LogisticsPlanningWorkflowCore(
    val assignee: LogisticsAssigneeSnapshot,
    val sources: List<LogisticsShipmentSource>,
    val lines: List<LogisticsShipmentLine>,
)

internal data class LogisticsPlanningWorkflowSchedule(
    val expectedDepartureAt: Long?,
    val expectedArrivalAt: Long?,
    val transportDetails: LogisticsTransportDetails?,
)

internal data class LogisticsPlanningWorkflowRoute(
    val milestones: List<LogisticsMilestone>,
    val legs: List<LogisticsShipmentLeg>,
)

internal data class LogisticsPlanningWorkflowArtifacts(
    val costs: List<LogisticsPlanningWorkflowCost>,
    val documents: List<LogisticsPlanningWorkflowDocument>,
)

internal data class LogisticsPlanningWorkflowSubmission(
    val markReady: Boolean,
    val requestId: String,
)

internal data class LogisticsPlanningWorkflowCommand(
    val shipmentId: String,
    val core: LogisticsPlanningWorkflowCore,
    val schedule: LogisticsPlanningWorkflowSchedule,
    val route: LogisticsPlanningWorkflowRoute,
    val artifacts: LogisticsPlanningWorkflowArtifacts,
    val submission: LogisticsPlanningWorkflowSubmission,
)

/** Owns the multi-write planning sequence; Presentation maps UI drafts into this command. */
internal class LogisticsPlanningOrchestrator internal constructor(
    private val operations: LogisticsPlanningOperations,
) {
    @Inject
    constructor(operations: DefaultLogisticsPlanningOperations) : this(operations as LogisticsPlanningOperations)
    suspend operator fun invoke(command: LogisticsPlanningWorkflowCommand): LogisticsShipment {
        val organizationId = operations.organizationId()
        persistPlanBase(organizationId, command)
        val aggregate = operations.aggregate(organizationId, command.shipmentId)
            ?: error("لم يتم العثور على الشحنة")
        val sourceIdsByInvoice = aggregate.sources.associate { it.invoiceId to it.id }
        persistCosts(organizationId, command, sourceIdsByInvoice)
        persistDocuments(organizationId, command, sourceIdsByInvoice)
        val result = if (command.submission.markReady) {
            operations.markReady(
                organizationId,
                command.shipmentId,
                stableRequestId("${command.submission.requestId}:ready"),
            )
        } else {
            operations.aggregate(organizationId, command.shipmentId)?.shipment
                ?: error("لم يتم العثور على الشحنة")
        }
        command.artifacts.documents.forEach { document ->
            runCatching {
                operations.removeDraft(organizationId, command.shipmentId, document.source.stagedPrivateUri)
            }
        }
        return result
    }

    private suspend fun persistPlanBase(organizationId: String, command: LogisticsPlanningWorkflowCommand) {
        operations.prepare(
            organizationId,
            PrepareLogisticsShipmentCommand(
                shipmentId = command.shipmentId,
                assignee = command.core.assignee,
                sources = command.core.sources,
                lines = command.core.lines,
                expectedDepartureAt = command.schedule.expectedDepartureAt,
                expectedArrivalAt = command.schedule.expectedArrivalAt,
                transportDetails = command.schedule.transportDetails,
                requestId = stableRequestId("${command.submission.requestId}:prepare"),
            ),
        )
        operations.saveRoute(
            organizationId,
            SaveShipmentRouteCommand(
                shipmentId = command.shipmentId,
                milestones = command.route.milestones,
                legs = command.route.legs,
                occurredAt = System.currentTimeMillis(),
                requestId = stableRequestId("${command.submission.requestId}:route"),
            ),
        )
    }

    private suspend fun persistCosts(
        organizationId: String,
        command: LogisticsPlanningWorkflowCommand,
        sourceIdsByInvoice: Map<String, String>,
    ) {
        command.artifacts.costs.forEachIndexed { index, cost ->
            val sourceId = cost.scope.sourceId ?: cost.scope.sourceInvoiceId?.let { invoiceId ->
                sourceIdsByInvoice[invoiceId] ?: error("Cost source invoice is no longer selected: $invoiceId")
            }
            operations.recordCost(
                organizationId,
                RecordLogisticsCostCommand(
                    shipmentId = command.shipmentId,
                    type = cost.money.type,
                    amount = cost.money.amount,
                    currency = cost.money.currency,
                    exchangeRateSnapshot = cost.money.exchangeRate,
                    exchangeRateDate = if (cost.money.currency.trim().uppercase() == "SDG") null else System.currentTimeMillis(),
                    baseCurrencyAmount = cost.money.amount.multiply(cost.money.exchangeRate),
                    status = cost.money.status,
                    servicePartnerId = cost.scope.servicePartnerId,
                    legId = cost.scope.legId,
                    milestoneId = cost.scope.milestoneId,
                    sourceId = sourceId,
                    reference = cost.reference,
                    note = cost.note,
                    requestId = stableRequestId("${command.submission.requestId}:cost:$index"),
                ),
            )
        }
    }

    private suspend fun persistDocuments(
        organizationId: String,
        command: LogisticsPlanningWorkflowCommand,
        sourceIdsByInvoice: Map<String, String>,
    ) {
        command.artifacts.documents.forEachIndexed { index, document ->
            operations.saveDocument(
                SaveLogisticsDocumentUseCase.Command(
                    organizationId = organizationId,
                    shipmentId = command.shipmentId,
                    sourceId = document.scope.sourceInvoiceId?.let { invoiceId ->
                        sourceIdsByInvoice[invoiceId] ?: error("Document source invoice is no longer selected: $invoiceId")
                    },
                    milestoneId = document.scope.milestoneId,
                    legId = document.scope.legId,
                    type = document.source.type,
                    sourceUri = "",
                    stagedPrivateUri = document.source.stagedPrivateUri,
                    displayName = document.source.displayName,
                    mimeType = document.source.mimeType,
                    createdAt = System.currentTimeMillis(),
                    requestId = stableRequestId("${command.submission.requestId}:document:$index"),
                ),
            )
        }
    }

    private fun stableRequestId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
}
