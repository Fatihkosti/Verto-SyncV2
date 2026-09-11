package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlanDocument
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevision
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevisionKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsV234Contract
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsPurchaseInvoiceQueryPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

data class LogisticsCustomsPlanDocumentInput(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val privateUri: String,
    val sha256: String,
)

data class SaveLogisticsCustomsPlanCommand(
    val shipmentId: String,
    val checkpointName: String,
    val afterStationId: String,
    val expectedDurationMinutes: Int,
    val documents: List<LogisticsCustomsPlanDocumentInput> = emptyList(),
)

/** v238 planning-only customs write. It never creates a route milestone or execution fact. */
class SaveLogisticsCustomsPlanUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: SaveLogisticsCustomsPlanCommand,
    ): LogisticsCustomsPlan {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.checkpointName.trim().isNotBlank()) { "نقطة الجمارك مطلوبة" }
        require(command.afterStationId.isNotBlank()) { "حدد المحطة التي تقع الجمارك بعدها" }
        require(command.expectedDurationMinutes > 0) { "مدة الجمارك يجب أن تكون أكبر من صفر" }
        require(command.documents.all {
            it.id.isNotBlank() && it.displayName.isNotBlank() && it.mimeType.isNotBlank() &&
                it.privateUri.isNotBlank() && it.sha256.isNotBlank() && it.sizeBytes > 0L
        }) { "يوجد مستند جمارك غير مكتمل" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        require(aggregate.shipment.state == LogisticsShipmentState.DRAFT && aggregate.shipment.currentPlanRevision == 0) {
            "Approved customs planning cannot be changed without a tracked future-plan revision"
        }
        val stations = aggregate.milestones.filter(LogisticsV234Contract::isStation).sortedBy { it.order }
        require(stations.any { it.id == command.afterStationId }) { "محطة الجمارك لم تعد ضمن المسار" }
        require(stations.none { it.id == command.afterStationId && it.type.name == "DESTINATION" }) {
            "لا يمكن وضع الجمارك بعد نقطة الوصول النهائية"
        }

        val now = clock.now()
        val previous = aggregate.customsPlan
        val plan = LogisticsCustomsPlan(
            id = previous?.id ?: identities.newId(),
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            checkpointName = command.checkpointName.trim(),
            afterStationId = command.afterStationId,
            expectedDurationMinutes = command.expectedDurationMinutes,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
        )
        LogisticsV234Contract.requireCustomsPlan(plan, stations)

        val existingDocuments = aggregate.customsPlanDocuments.associateBy { it.id }
        val documents = command.documents.map { input ->
            existingDocuments[input.id]?.also { existing ->
                require(existing.displayName == input.displayName && existing.mimeType == input.mimeType &&
                    existing.sizeBytes == input.sizeBytes && existing.privateUri == input.privateUri && existing.sha256 == input.sha256
                ) { "Existing customs planning documents are immutable; replace the document instead" }
            } ?: LogisticsCustomsPlanDocument(
                id = input.id,
                organizationId = organizationId,
                shipmentId = command.shipmentId,
                customsPlanId = plan.id,
                displayName = input.displayName,
                mimeType = input.mimeType,
                sizeBytes = input.sizeBytes,
                privateUri = input.privateUri,
                sha256 = input.sha256,
                createdAt = now,
            )
        }
        store.saveCustomsPlan(plan, documents)
        return plan
    }
}

data class ApproveLogisticsPlanCommand(
    val shipmentId: String,
    val requestId: String,
    val employeeId: String? = null,
    val employeeName: String? = null,
)

/** v238 authoritative approval: validation + revision 1 + READY state in one Room transaction. */
class ApproveLogisticsPlanUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val purchaseInvoices: LogisticsPurchaseInvoiceQueryPort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: ApproveLogisticsPlanCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }

        var aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val priorByRequest = aggregate.planRevisions.firstOrNull { it.requestId == command.requestId }
            ?: store.getPlanRevisions(organizationId, command.shipmentId).firstOrNull { it.requestId == command.requestId }
        if (priorByRequest != null) {
            require(priorByRequest.kind == LogisticsPlanRevisionKind.INITIAL_APPROVAL) {
                "Approval request id is already used by another plan revision"
            }
            return store.getShipment(organizationId, command.shipmentId)?.shipment ?: aggregate.shipment
        }

        require(aggregate.shipment.state == LogisticsShipmentState.DRAFT) { "تم اعتماد التخطيط مسبقًا" }
        require(aggregate.shipment.currentPlanRevision == 0 && aggregate.shipment.planApprovedAt == null) {
            "تم اعتماد التخطيط مسبقًا"
        }
        LogisticsValidation.validateReady(aggregate)
        val customs = aggregate.customsPlan ?: error("أكمل بيانات الجمارك قبل اعتماد التخطيط")
        LogisticsV234Contract.requireCustomsPlan(customs, aggregate.milestones)
        require(aggregate.customsPlanDocuments.none { it.privateUri.isBlank() || it.sizeBytes <= 0L || it.sha256.isBlank() }) {
            "أكمل تجهيز مستندات الجمارك أو احذف المستند المتعذر"
        }
        validatePurchaseInvoicesStillEligible(organizationId, aggregate, purchaseInvoices)

        // Re-read after the external invoice check to avoid approving stale local state.
        aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        require(aggregate.shipment.state == LogisticsShipmentState.DRAFT && aggregate.shipment.currentPlanRevision == 0) {
            "تم اعتماد التخطيط مسبقًا"
        }
        LogisticsValidation.validateReady(aggregate)
        val latestCustoms = aggregate.customsPlan ?: error("أكمل بيانات الجمارك قبل اعتماد التخطيط")
        LogisticsV234Contract.requireCustomsPlan(latestCustoms, aggregate.milestones)

        val approvedAt = clock.now()
        val revision = LogisticsPlanRevision(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            revisionNumber = 1,
            kind = LogisticsPlanRevisionKind.INITIAL_APPROVAL,
            reason = "",
            changedByEmployeeId = command.employeeId?.trim()?.takeIf(String::isNotBlank),
            changedByEmployeeNameSnapshot = command.employeeName?.trim()?.takeIf(String::isNotBlank),
            recordedAt = approvedAt,
            requestId = command.requestId,
            changes = emptyList(),
        )
        val updated = aggregate.shipment.copy(
            state = LogisticsShipmentState.READY,
            currentPlanRevision = 1,
            planApprovedAt = approvedAt,
        )
        LogisticsV234Contract.requireRevision(revision, aggregate.shipment.currentPlanRevision)
        LogisticsV234Contract.requireRevisionShipmentTransition(aggregate.shipment, updated, revision)
        store.appendPlanRevision(updated, revision)
        return updated
    }
}

private suspend fun validatePurchaseInvoicesStillEligible(
    organizationId: String,
    aggregate: com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate,
    purchaseInvoices: LogisticsPurchaseInvoiceQueryPort,
) {
    val linesByInvoice = aggregate.lines.groupBy { it.sourceInvoiceId }
    aggregate.sources.forEach { source ->
        val invoice = purchaseInvoices.getPurchaseInvoice(
            organizationId = organizationId,
            invoiceId = source.invoiceId,
            excludeShipmentId = aggregate.shipment.id,
        ) ?: error("إحدى الفواتير لم تعد متاحة لأنها ارتبطت بشحنة أخرى")
        val invoiceLines = invoice.lines.associateBy { it.invoiceItemId }
        val selectedLines = linesByInvoice[source.invoiceId].orEmpty()
        require(selectedLines.map { it.sourceInvoiceItemId }.toSet() == invoice.lines.map { it.invoiceItemId }.toSet()) {
            "الفاتورة تغيرت منذ إضافتها إلى الشحنة؛ حدّث الاختيار"
        }
        selectedLines.forEach { line ->
            val snapshot = invoiceLines[line.sourceInvoiceItemId]
                ?: error("أحد أصناف الفاتورة لم يعد موجودًا")
            require(snapshot.inventoryItemId.isNotBlank() && line.inventoryItemId == snapshot.inventoryItemId) {
                "هوية صنف الفاتورة تغيرت؛ حدّث الاختيار"
            }
            require(line.expectedQuantity == snapshot.remainingShippableQuantity) {
                "الكمية المتاحة في الفاتورة تغيرت؛ حدّث الاختيار"
            }
        }
    }
}
