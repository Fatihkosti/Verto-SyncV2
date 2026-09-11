package com.verto.app.feature.shipment.domain.port

import com.verto.app.feature.shipment.domain.model.LogisticsInventoryCatalogItem
import com.verto.app.feature.shipment.domain.model.LogisticsAssignment
import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlanDocument
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevision
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsInventoryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPayment
import com.verto.app.feature.shipment.domain.model.LogisticsPurchaseInvoiceSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingBatch
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import com.verto.app.feature.shipment.domain.model.LogisticsRecovery
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryLine
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentPartnerLink
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShortage
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlement
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAdjustment
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAllocation
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow

interface LogisticsShipmentStorePort {
    fun observeShipments(organizationId: String): Flow<List<LogisticsShipment>>
    suspend fun getShipment(organizationId: String, shipmentId: String): LogisticsShipmentAggregate?
    suspend fun shipmentNumberExists(organizationId: String, shipmentNumber: String): Boolean
    suspend fun isRequestProcessed(organizationId: String, requestId: String): Boolean

    suspend fun getPartner(organizationId: String, partnerId: String): LogisticsPartner? =
        throw UnsupportedOperationException("Operational partner lookup is not implemented")

    suspend fun listPartners(organizationId: String): List<LogisticsPartner> =
        throw UnsupportedOperationException("Operational partner directory is not implemented")

    suspend fun listRouteTemplates(organizationId: String): List<LogisticsRouteTemplate> =
        throw UnsupportedOperationException("Route template directory is not implemented")

    suspend fun getRouteTemplate(organizationId: String, templateId: String): LogisticsRouteTemplate? =
        throw UnsupportedOperationException("Route template lookup is not implemented")

    suspend fun upsertRouteTemplate(template: LogisticsRouteTemplate) {
        throw UnsupportedOperationException("Route template persistence is not implemented")
    }

    suspend fun hasInventoryPosting(organizationId: String, shipmentId: String): Boolean =
        throw UnsupportedOperationException("Operational inventory posting lookup is not implemented")


    /** v234 planning customs event. It is separate from route stations and from execution customs facts. */
    suspend fun getCustomsPlan(organizationId: String, shipmentId: String): LogisticsCustomsPlan? =
        throw UnsupportedOperationException("Customs planning lookup is not implemented")

    suspend fun saveCustomsPlan(
        plan: LogisticsCustomsPlan,
        documents: List<LogisticsCustomsPlanDocument>,
    ) {
        throw UnsupportedOperationException("Customs planning persistence is not implemented")
    }

    suspend fun getPlanRevisions(organizationId: String, shipmentId: String): List<LogisticsPlanRevision> =
        throw UnsupportedOperationException("Plan revision lookup is not implemented")

    /**
     * Atomically appends a consecutive plan revision and updates shipment.currentPlanRevision.
     * Implementations must reject duplicate request ids and revision gaps.
     */
    suspend fun appendPlanRevision(
        updatedShipment: LogisticsShipment,
        revision: LogisticsPlanRevision,
    ) {
        throw UnsupportedOperationException("Plan revision persistence is not implemented")
    }

    suspend fun createShipment(
        shipment: LogisticsShipment,
        event: LogisticsEvent,
    )

    /** Local-only draft header write. No network call is permitted by this contract. */
    suspend fun saveShipmentHeader(
        shipment: LogisticsShipment,
        assignment: LogisticsAssignment?,
    ) {
        throw UnsupportedOperationException("Draft header persistence is not implemented")
    }

    /** Local-only purchase-source/line write used by planning auto-save. */
    suspend fun savePurchasePlan(
        shipment: LogisticsShipment,
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
    ) {
        throw UnsupportedOperationException("Draft purchase persistence is not implemented")
    }

    suspend fun savePlanning(
        shipment: LogisticsShipment,
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
        assignment: LogisticsAssignment,
        event: LogisticsEvent,
    )

    suspend fun saveShipmentState(
        shipment: LogisticsShipment,
        event: LogisticsEvent,
    )

    suspend fun changeAssignment(
        shipment: LogisticsShipment,
        endedAssignmentId: String?,
        assignment: LogisticsAssignment,
        event: LogisticsEvent,
    )

    suspend fun saveMilestone(
        shipment: LogisticsShipment,
        milestone: LogisticsMilestone,
        event: LogisticsEvent,
    )

    /** Replaces only a not-yet-executed DRAFT route and is authoritative locally. */
    suspend fun replacePlannedRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
    ) {
        throw UnsupportedOperationException("Planned route replacement is not implemented")
    }

    suspend fun saveRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
        event: LogisticsEvent,
    )

    /** Executed milestone correction: corrected fact and audit event commit atomically. */
    suspend fun correctMilestone(
        shipment: LogisticsShipment,
        milestone: LogisticsMilestone,
        event: LogisticsEvent,
    ) {
        throw UnsupportedOperationException("Milestone correction persistence is not implemented")
    }

    /** Executed leg correction: corrected fact and audit event commit atomically. */
    suspend fun correctLeg(
        shipment: LogisticsShipment,
        leg: LogisticsShipmentLeg,
        event: LogisticsEvent,
    ) {
        throw UnsupportedOperationException("Leg correction persistence is not implemented")
    }

    suspend fun saveCustodyHandoff(
        handoff: LogisticsCustodyHandoff,
        event: LogisticsEvent,
    )

    /** Commits a customs state/milestone transition and optional custody handoff as one fact. */
    suspend fun saveCustomsTransition(
        shipment: LogisticsShipment,
        milestone: LogisticsMilestone,
        handoff: LogisticsCustodyHandoff?,
        event: LogisticsEvent,
    ) {
        throw UnsupportedOperationException("Customs transition persistence is not implemented")
    }

    suspend fun getLatestConfirmedCargoSnapshot(
        organizationId: String,
        shipmentId: String,
    ): LogisticsCargoSnapshot? {
        throw UnsupportedOperationException("Cargo snapshot lookup is not implemented")
    }

    suspend fun saveOperationalUpdate(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
        event: LogisticsEvent,
    )

    /** v238: future planned-leg edit + audit event + consecutive plan revision must commit atomically. */
    suspend fun saveFuturePlanLegRevision(
        updatedShipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        leg: LogisticsShipmentLeg,
        event: LogisticsEvent,
        revision: LogisticsPlanRevision,
    ) {
        throw UnsupportedOperationException("Atomic future plan revision persistence is not implemented")
    }

    suspend fun appendEvent(event: LogisticsEvent)

    suspend fun upsertPartner(partner: LogisticsPartner)
    suspend fun linkPartner(organizationId: String, link: LogisticsShipmentPartnerLink)
    suspend fun saveDocument(document: LogisticsDocument, event: LogisticsEvent)
    suspend fun deleteDocument(organizationId: String, shipmentId: String, documentId: String): Boolean
    suspend fun saveCost(cost: LogisticsCost, event: LogisticsEvent)
    suspend fun getCosts(organizationId: String, shipmentId: String): List<LogisticsCost>
    suspend fun findCostByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsCost? {
        throw UnsupportedOperationException("Cost request lookup is not implemented")
    }

    suspend fun savePayment(payment: LogisticsPayment, event: LogisticsEvent) {
        throw UnsupportedOperationException("Payment persistence is not implemented")
    }
    suspend fun getPayments(organizationId: String, shipmentId: String): List<LogisticsPayment> =
        throw UnsupportedOperationException("Payment lookup is not implemented")
    suspend fun findPaymentByRequest(organizationId: String, requestId: String): LogisticsPayment? =
        throw UnsupportedOperationException("Payment request lookup is not implemented")

    suspend fun saveReceivingBatch(
        batch: LogisticsReceivingBatch,
        updatedShipment: LogisticsShipment,
        event: LogisticsEvent,
    )

    suspend fun upsertShortage(shortage: LogisticsShortage): LogisticsShortage =
        throw UnsupportedOperationException("Shortage persistence is not implemented")
    suspend fun getShortages(organizationId: String, shipmentId: String): List<LogisticsShortage> =
        throw UnsupportedOperationException("Shortage lookup is not implemented")
    suspend fun findShortageByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsShortage? = throw UnsupportedOperationException("Shortage request lookup is not implemented")

    suspend fun saveShortageSettlement(
        settlement: LogisticsShortageSettlement,
        updatedShortage: LogisticsShortage,
        event: LogisticsEvent,
    ): LogisticsShortageSettlement = throw UnsupportedOperationException("Shortage settlement persistence is not implemented")

    suspend fun getShortageSettlements(
        organizationId: String,
        shipmentId: String,
    ): List<LogisticsShortageSettlement> = throw UnsupportedOperationException("Shortage settlement lookup is not implemented")

    suspend fun findShortageSettlementByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsShortageSettlement? = throw UnsupportedOperationException("Shortage settlement request lookup is not implemented")

    suspend fun createRecovery(recovery: LogisticsRecovery): LogisticsRecovery =
        throw UnsupportedOperationException("Recovery persistence is not implemented")
    suspend fun getRecoveries(organizationId: String, shipmentId: String): List<LogisticsRecovery> =
        throw UnsupportedOperationException("Recovery lookup is not implemented")
    suspend fun findRecoveryByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsRecovery? = throw UnsupportedOperationException("Recovery request lookup is not implemented")

    suspend fun createRecoveryLines(
        organizationId: String,
        shipmentId: String,
        lines: List<LogisticsRecoveryLine>,
    ): List<LogisticsRecoveryLine> = throw UnsupportedOperationException("Recovery line persistence is not implemented")

    suspend fun getRecoveryLines(
        organizationId: String,
        recoveryId: String,
    ): List<LogisticsRecoveryLine> = throw UnsupportedOperationException("Recovery line lookup is not implemented")

    suspend fun saveRecoveryPosting(posting: LogisticsRecoveryPosting): LogisticsRecoveryPosting =
        throw UnsupportedOperationException("Recovery posting persistence is not implemented")
    suspend fun getRecoveryPosting(
        organizationId: String,
        recoveryLineId: String,
    ): LogisticsRecoveryPosting? = throw UnsupportedOperationException("Recovery posting lookup is not implemented")

    suspend fun saveCostAllocations(
        organizationId: String,
        shipmentId: String,
        allocations: List<LogisticsCostAllocation>,
        event: LogisticsEvent,
    )

    suspend fun saveLateCostAdjustment(
        adjustment: LogisticsLateCostAdjustment,
        allocations: List<LogisticsLateCostAllocation>,
        event: LogisticsEvent,
    ): LogisticsLateCostAdjustment = throw UnsupportedOperationException("Late-cost adjustment persistence is not implemented")

    suspend fun getLateCostAdjustments(
        organizationId: String,
        shipmentId: String,
    ): List<LogisticsLateCostAdjustment> = throw UnsupportedOperationException("Late-cost adjustment lookup is not implemented")

    suspend fun getLateCostAllocations(
        organizationId: String,
        shipmentId: String,
    ): List<LogisticsLateCostAllocation> = throw UnsupportedOperationException("Late-cost allocation lookup is not implemented")

    suspend fun findLateCostAdjustmentByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsLateCostAdjustment? = throw UnsupportedOperationException("Late-cost adjustment request lookup is not implemented")
}

interface LogisticsPurchaseInvoiceQueryPort {
    suspend fun getPurchaseInvoice(
        organizationId: String,
        invoiceId: String,
        excludeShipmentId: String? = null,
    ): LogisticsPurchaseInvoiceSnapshot?
}

interface LogisticsInventoryIdentityPort {
    suspend fun listCatalog(): List<LogisticsInventoryCatalogItem>
    suspend fun bindInvoiceLine(invoiceItemId: String, inventoryItemId: String): String
    suspend fun createZeroStockAndBind(invoiceItemId: String): String
}

interface LogisticsShipmentNumberPort {
    suspend fun allocate(organizationId: String): Int?
}

interface AssigneeDirectoryPort {
    suspend fun getEmployee(
        organizationId: String,
        employeeId: String,
    ): LogisticsAssigneeRecord?
}

data class LogisticsAssigneeRecord(
    val employeeId: String,
    val employeeName: String,
    val active: Boolean,
)

interface LogisticsDocumentStoragePort {
    suspend fun importPrivate(
        organizationId: String,
        shipmentId: String,
        documentId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String,
    ): LogisticsStoredDocument

    /** Copies a shipment-owned draft file into its final private document identity without deleting the draft copy. */
    suspend fun promoteDraft(
        organizationId: String,
        shipmentId: String,
        documentId: String,
        draftPrivateUri: String,
        displayName: String,
        mimeType: String,
    ): LogisticsStoredDocument = throw UnsupportedOperationException("Draft document promotion is not implemented")

    /** Removes only a draft file owned by the same organization + shipment. */
    suspend fun deleteDraft(
        organizationId: String,
        shipmentId: String,
        draftPrivateUri: String,
    ) {
        throw UnsupportedOperationException("Draft document deletion is not implemented")
    }

    fun ownsPrivate(organizationId: String, shipmentId: String, privateUri: String): Boolean = false
    suspend fun deletePrivate(privateUri: String)
    fun exists(privateUri: String): Boolean
    fun open(privateUri: String, mimeType: String): Boolean = false
}

data class LogisticsStoredDocument(
    val privateUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class LogisticsCashPostingContext(
    val organizationId: String,
    val shipmentId: String,
    val costId: String,
    val requestId: String,
    val description: String,
    val reference: String,
)

data class LogisticsCashActor(
    val actorId: String?,
    val actorName: String?,
    val occurredAt: Long,
)

data class LogisticsCashPostingRequest(
    val context: LogisticsCashPostingContext,
    val exactWholeBaseAmount: BigDecimal,
    val actor: LogisticsCashActor,
)

data class LogisticsCashAdjustmentRequest(
    val context: LogisticsCashPostingContext,
    val previousWholeBaseAmount: BigDecimal,
    val exactWholeBaseAmount: BigDecimal,
    val updatedCost: LogisticsCost,
    val actor: LogisticsCashActor,
)

data class LogisticsCashPostingRecord(
    val reference: String,
    val signedBaseAmount: BigDecimal,
    val occurredAt: Long,
)

/** Consumer-owned cash boundary. Shipment domain never imports cash implementation types. */
interface LogisticsCashPostingPort {
    suspend fun postExpense(request: LogisticsCashPostingRequest): LogisticsCost
    suspend fun postAdjustment(request: LogisticsCashAdjustmentRequest): LogisticsCost
    suspend fun findByReference(organizationId: String, reference: String): LogisticsCashPostingRecord?
    suspend fun reverseShipmentPayments(
        organizationId: String,
        shipmentId: String,
        requestId: String,
        actor: LogisticsCashActor,
    ) {
        throw UnsupportedOperationException("Shipment cash reversal is not implemented")
    }
}

interface LogisticsInventoryPostingPort {
    suspend fun postAcceptedStock(posting: LogisticsInventoryPosting)
}

/**
 * Atomic persistence boundary for Logistics V2 receiving.
 * Implementations must commit inventory stock, receiving batch/lines, shipment state,
 * posting audit rows, and the request/event marker in one all-or-nothing transaction.
 */
interface LogisticsReceivingTransactionPort {
    suspend fun commitReceiving(
        postings: List<LogisticsInventoryPosting>,
        batch: LogisticsReceivingBatch,
        updatedShipment: LogisticsShipment,
        event: LogisticsEvent,
    )
}

interface LogisticsInventoryCostPort {
    suspend fun applyReceivingPostingUnitPrice(
        postingId: String,
        shipmentId: String,
        unitPrice: BigDecimal,
    ): Result<Unit>
}

interface LogisticsPermanentDeletePort {
    suspend fun deleteDraft(organizationId: String, shipmentId: String)
    suspend fun deleteExecuted(
        organizationId: String,
        shipmentId: String,
        requestId: String,
        actor: LogisticsCashActor,
    )
}

interface LogisticsIdentityPort {
    fun newId(): String
}

interface LogisticsClockPort {
    fun now(): Long
}
