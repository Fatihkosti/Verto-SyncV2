package com.verto.app.data.sync

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val SYNC_REPAIR_CONTRACT_VERSION: Int = 2
const val SYNC_REPAIR_PAYLOAD_VERSION: Int = 2

@Serializable
enum class SnapshotKindV2 { FULL }

@Serializable
data class InvoiceDtoV2(
    val id: String, val invoiceNumber: Int, val clientId: String, val organizationId: String,
    val supplierInvoiceReference: String?, val supplierInvoiceReferenceNormalized: String?,
    val type: String, val category: String, val description: String, val totalAmountMinor: Long,
    val transactionCurrencyCode: String, val functionalCurrencyCode: String,
    val transactionAmountMinor: Long, val invoiceExchangeRateSnapshot: String,
    val exchangeRateDirection: String, val exchangeRateTimestamp: Long, val exchangeRateSource: String,
    val functionalAmountAtRecognitionMinor: Long, val legacyCurrencyStatus: String,
    val createdAt: Long, val dueDate: Long, val notifyDaysBefore: String, val notifyRepeatDays: Int,
    val notificationsEnabled: Boolean, val notes: String, val isOwedToMe: Boolean,
    val status: String, val discountMinor: Long, val commissionMinor: Long,
    val commissionBeneficiaryClientId: String?, val commissionSource: String, val shipmentId: String?,
    val purchaseOrderId: String?, val purchaseScope: String, val createdBy: String,
    val lifecycleStatus: String, val lifecycleVersion: Int, val postedAt: Long, val voidedAt: Long,
    val voidReason: String, val voidWriteId: String,
)

@Serializable
data class InvoiceItemDtoV2(
    val id: String, val invoiceId: String, val itemType: String, val itemName: String,
    val itemCategory: String, val itemSkuSnapshot: String, val unitSnapshot: String, val quantity: Int,
    val buyPriceMinor: Long, val sellPriceMinor: Long, val totalPriceMinor: Long, val description: String,
    val isOwedToMe: Boolean, val inventoryItemId: String, val adjustedPurchasePriceMinor: Long,
    val unitSellPriceMinor: Long, val unitCostAtSaleMinor: Long, val lineRevenueSnapshotMinor: Long,
    val lineCostSnapshotMinor: Long, val grossProfitSnapshotMinor: Long, val costSnapshotStatus: String,
)

@Serializable
data class InvoiceDueInstallmentDtoV2(
    val id: String, val invoiceId: String, val sequence: Int, val amountMinor: Long,
    val currencyCode: String, val dueDate: Long, val createdAt: Long, val writeId: String,
)

@Serializable
data class PaymentDtoV2(
    val id: String, val invoiceId: String, val clientId: String, val amountMinor: Long,
    val paymentCurrencyCode: String, val supplierAmountMinor: Long, val paymentExchangeRate: String,
    val paymentExchangeRateDirection: String, val paymentExchangeRateTimestamp: Long,
    val paymentExchangeRateSource: String, val functionalCashAmountMinor: Long,
    val historicalFunctionalAmountMinor: Long, val realizedFxDifferenceMinor: Long,
    val legacyCurrencyStatus: String, val paymentMethod: String, val note: String, val paidAt: Long,
    val employeeId: String, val employeeName: String, val sourceType: String, val sourceId: String,
    val sourceVersion: Int, val writeId: String, val reversedPaymentId: String?,
)

@Serializable
data class PaymentAllocationDtoV2(
    val id: String, val paymentId: String, val invoiceId: String,
    val allocatedTransactionAmountMinor: Long, val historicalFunctionalAmountMinor: Long,
    val realizedFxDifferenceMinor: Long, val createdAt: Long, val sourceType: String,
    val sourceId: String, val sourceVersion: Int, val writeId: String,
)

@Serializable
data class RealizedFxEventDtoV2(
    val id: String, val paymentId: String, val invoiceId: String, val functionalCurrencyCode: String,
    val historicalFunctionalAmountMinor: Long, val functionalCashAmountMinor: Long,
    val differenceMinor: Long, val result: String, val occurredAt: Long, val sourceType: String,
    val sourceId: String, val sourceVersion: Int, val writeId: String,
)

@Serializable
data class InvoiceReturnDocumentDtoV2(
    val id: String, val organizationId: String, val originalInvoiceId: String, val clientId: String,
    val documentType: String, val settlementMode: String, val transactionCurrencyCode: String,
    val functionalCurrencyCode: String, val transactionAmountMinor: Long, val functionalAmountMinor: Long,
    val reason: String, val occurredAt: Long, val recordedAt: Long, val createdBy: String,
    val createdByName: String, val writeId: String, val sourceVersion: Int,
)

@Serializable
data class InvoiceReturnLineDtoV2(
    val id: String, val returnId: String, val originalInvoiceItemId: String, val inventoryItemId: String,
    val itemNameSnapshot: String, val quantity: Int, val unitTransactionAmountMinor: Long,
    val transactionAmountMinor: Long, val unitFunctionalAmountMinor: Long, val functionalAmountMinor: Long,
    val unitCostAtSaleMinor: Long, val historicalCostAmountMinor: Long,
    val originalPurchaseUnitCostMinor: Long,
)

@Serializable
data class InvoiceReturnPaymentAllocationDtoV2(
    val id: String, val returnId: String, val paymentId: String,
    val allocatedFunctionalAmountMinor: Long, val createdAt: Long,
)

@Serializable
data class ExplicitTombstoneV2(
    val entityType: String, val id: String, val previousVersion: Long, val reason: String,
)

@Serializable
data class EffectReferenceV2(
    val owner: String, val factType: String, val factId: String,
    val businessIdentity: String, val contentHash: String,
)

@Serializable
data class FinancialAggregateSnapshotV2(
    val schemaVersion: Int = SYNC_REPAIR_PAYLOAD_VERSION,
    val organizationId: String,
    val invoiceId: String,
    val financialStreamVersion: Long,
    val expectedFinancialStreamVersion: Long?,
    val snapshotKind: SnapshotKindV2 = SnapshotKindV2.FULL,
    val header: InvoiceDtoV2,
    val items: List<InvoiceItemDtoV2>,
    val dueInstallments: List<InvoiceDueInstallmentDtoV2>,
    val payments: List<PaymentDtoV2>,
    val paymentAllocations: List<PaymentAllocationDtoV2>,
    val realizedFxEvents: List<RealizedFxEventDtoV2>,
    val returnDocuments: List<InvoiceReturnDocumentDtoV2>,
    val returnLines: List<InvoiceReturnLineDtoV2>,
    val returnPaymentAllocations: List<InvoiceReturnPaymentAllocationDtoV2>,
    val explicitTombstones: List<ExplicitTombstoneV2>,
    val effectReferences: List<EffectReferenceV2>,
    val businessContentHash: String,
)

@Serializable
data class InventoryMovementDtoV2(
    val id: String, val itemId: String, val invoiceId: String, val clientId: String,
    val movementKind: String, val signedBaseQuantity: Long, val unitPriceMinor: Long, val note: String,
    val shipmentId: String, val sourceType: String, val sourceId: String, val sourceLineId: String?,
    val sourceVersion: Int, val writeId: String, val organizationId: String, val commandId: String,
    val idempotencyKey: String, val postingGroupId: String?, val reversesMovementId: String?,
    val conversionFactorSnapshot: String, val occurredAt: Long, val recordedAt: Long?,
    val serverAcceptedAt: Long?, val serverSequence: Long?, val createdBy: String?,
    val deviceId: String, val contractVersion: Int, val createdAt: Long,
)

@Serializable
data class InventoryCostRevisionDtoV2(
    val costRevisionId: String, val organizationId: String, val itemId: String, val sourceType: String,
    val sourceId: String, val sourceLineId: String?, val revisionKind: String,
    val directPurchaseCostMinor: Long, val landedCostPerBaseUnitMinor: Long,
    val approvedInventoryCostMinor: Long, val currencyCode: String, val exchangeRateSnapshot: String,
    val allocationBasis: String, val allocationResidualMinor: Long, val isProvisional: Boolean,
    val reversesCostRevisionId: String?, val commandId: String, val idempotencyKey: String,
    val costSequence: Long?, val approvedAt: Long, val recordedAt: Long, val createdBy: String,
    val deviceId: String, val contractVersion: Int,
)

@Serializable
data class ClientCreditDtoV2(
    val id: String, val clientId: String, val amountMinor: Long, val note: String,
    val sourcePaymentId: String, val createdAt: Long, val employeeId: String, val employeeName: String,
)

@Serializable
data class ExpenseDtoV2(
    val id: String, val category: String, val item: String, val amountMinor: Long, val note: String,
    val date: Long, val lifecycleState: String, val voidedAt: Long?, val voidReason: String?,
    val reversalWriteId: String?,
)

@Serializable
data class ExpenseRevisionIntentDtoV2(
    val schemaVersion: Int = 1,
    val writeId: String,
    val expenseId: String,
    val operation: String,
    val capturedBaseVersion: Long?,
    val before: ExpenseDtoV2?,
    val after: ExpenseDtoV2,
    val beforeContentHash: String?,
    val afterContentHash: String,
    val cashDeltaMinor: Long,
    val cashMovementId: String?,
    val cashMutationId: String?,
    val actorId: String,
    val createdAt: Long,
)

@Serializable
data class ExpenseRevisionDtoV2(
    val expenseId: String,
    val serverVersion: Long,
    val previousVersion: Long?,
    val writeId: String,
    val beforeContentHash: String?,
    val afterContentHash: String,
    val cashDeltaMinor: Long,
    val cashMovementId: String?,
    val actorId: String,
    val changedAt: Long,
)

@Serializable
data class CashDenominationDtoV2(
    val id: String, val reconciliationId: String, val denominationValueMinor: Long,
    val count: Int, val subtotalMinor: Long, val isCoin: Boolean,
)

@Serializable
data class CashReconciliationDtoV2(
    val id: String, val employeeId: String, val employeeName: String, val openingBalanceMinor: Long,
    val totalSalesMinor: Long, val totalRefundsMinor: Long, val totalCashInMinor: Long,
    val totalCashOutMinor: Long, val expectedBalanceMinor: Long, val actualCountedBalanceMinor: Long,
    val varianceMinor: Long, val varianceReason: String, val status: String, val startedAt: Long,
    val endedAt: Long?, val notes: String, val denominations: List<CashDenominationDtoV2>,
)

@Serializable
data class CashMovementDtoV2(
    val id: String, val movementType: String, val amountMinor: Long,
    val balanceBeforeMinor: Long, val balanceAfterMinor: Long, val referenceId: String,
    val note: String, val sourceType: String, val sourceId: String, val sourceVersion: Int,
    val writeId: String, val createdAt: Long,
)

/** A.16 freezes this existing owner310 shape; its legacy Double fields are not reinterpreted. */
@Serializable
data class CostAllocationDtoV2(
    val id: String, val itemId: String, val sourceType: String, val sourceId: String,
    val allocatedAmount: Double, val perUnitCost: Double, val quantityAffected: Int,
    val method: String, val note: String, val createdAt: Long,
)

@Serializable data class PurchaseOrderDtoV2(
    val id: String, val organizationId: String, val orderNumber: String, val supplierId: String,
    val purchaseScope: String, val currencyCode: String, val status: String, val createdAt: Long,
    val promisedDeliveryAt: Long?, val createdBy: String, val createdByName: String,
    val closedAt: Long?, val closeReason: String?, val note: String, val writeId: String,
)
@Serializable data class PurchaseOrderLineDtoV2(
    val id: String, val purchaseOrderId: String, val lineNumber: Int, val inventoryItemId: String?,
    val itemNameSnapshot: String, val orderedQuantity: Int, val unitPriceMinor: Long,
)
@Serializable data class GoodsReceiptDtoV2(
    val id: String, val organizationId: String, val purchaseOrderId: String, val receiptNumber: String,
    val receivedAt: Long, val receivedBy: String, val receivedByName: String, val note: String,
    val writeId: String,
)
@Serializable data class GoodsReceiptLineDtoV2(
    val id: String, val goodsReceiptId: String, val purchaseOrderLineId: String,
    val inventoryItemId: String?, val receivedQuantity: Int, val acceptedQuantity: Int,
    val rejectedQuantity: Int, val unitCostMinor: Long,
)
@Serializable data class PurchaseAttachmentDtoV2(
    val mimeType: String, val displayName: String, val transferState: String,
)
@Serializable data class PurchaseMatchDtoV2(
    val id: String, val organizationId: String, val invoiceId: String, val purchaseOrderId: String,
    val status: String, val quantityVarianceUnits: Int, val priceVarianceMinor: Long,
    val quantityToleranceUnits: Int, val priceToleranceMinor: Long, val invoiceAmountMinor: Long,
    val payableAmountMinor: Long, val varianceReason: String?, val approvedBy: String?,
    val approvedByName: String?, val matchedAt: Long, val writeId: String,
)
@Serializable data class PurchaseMatchLineDtoV2(
    val id: String, val matchId: String, val invoiceItemId: String, val purchaseOrderLineId: String,
    val orderedQuantity: Int, val acceptedQuantity: Int, val invoicedQuantity: Int,
    val poUnitPriceMinor: Long, val invoiceUnitPriceMinor: Long, val quantityVarianceUnits: Int,
    val priceVarianceMinor: Long, val payableAmountMinor: Long,
)
@Serializable data class PurchaseAllocationDtoV2(
    val id: String, val organizationId: String, val matchLineId: String, val goodsReceiptLineId: String,
    val allocatedQuantity: Int, val createdAt: Long, val writeId: String,
)
@Serializable data class PurchasePaymentOverrideDtoV2(
    val id: String, val organizationId: String, val invoiceId: String, val paymentRequestId: String,
    val requestedAmountMinor: Long, val payableBeforeOverrideMinor: Long, val reason: String,
    val approvedBy: String, val approvedByName: String, val createdAt: Long,
)
@Serializable data class PurchaseRequestDtoV2(
    val purchaseOrders: List<PurchaseOrderDtoV2>,
    val purchaseOrderLines: List<PurchaseOrderLineDtoV2>,
    val goodsReceipts: List<GoodsReceiptDtoV2>,
    val goodsReceiptLines: List<GoodsReceiptLineDtoV2>,
    val attachments: List<PurchaseAttachmentDtoV2>,
    val matches: List<PurchaseMatchDtoV2>,
    val matchLines: List<PurchaseMatchLineDtoV2>,
    val allocations: List<PurchaseAllocationDtoV2>,
    val paymentOverrides: List<PurchasePaymentOverrideDtoV2>,
)

@Serializable data class SyncBatchMemberDtoV2(
    val memberOrder: Int, val memberWireJson: String, val memberWireSha256: String,
)
@Serializable data class SnapshotBlobDtoV2(val sha256: String, val snapshotJson: String)
@Serializable data class SyncBatchRequestDtoV2(
    val contractFamily: String = UNIFIED_SYNC_CONTRACT_FAMILY,
    val contractVersion: Int = SYNC_REPAIR_CONTRACT_VERSION,
    val organizationId: String, val batchId: String, val memberCount: Int,
    val members: List<SyncBatchMemberDtoV2>, val snapshotBlobs: List<SnapshotBlobDtoV2>,
)
@Serializable enum class SyncReceiptStatusV2 { APPLIED, REPLAYED, NO_OP, CONFLICT, REJECTED, RETRYABLE }
@Serializable data class SyncReceiptDtoV2(
    val status: SyncReceiptStatusV2, val mutationId: String, val aggregateId: String,
    val serverVersion: Long?, val serverRevision: Long?, val authoritativePayload: JsonObject?,
    val conflictCode: String?, val validationCode: String?, val transactionId: String?,
    val retryAfterEpochMillis: Long?, val requestHash: String,
)

@Serializable
data class SyncMutationEnvelopeV2(
    val contractFamily: String = UNIFIED_SYNC_CONTRACT_FAMILY,
    val contractVersion: Int = SYNC_REPAIR_CONTRACT_VERSION,
    val mutationId: String, val organizationId: String, val aggregateType: String,
    val aggregateId: String, val operationType: String, val baseVersion: Long?,
    val localSequence: Long, val aggregateSequence: Long, val payloadVersion: Int = SYNC_REPAIR_PAYLOAD_VERSION,
    val payload: JsonObject, val createdAtEpochMillis: Long, val commandBatchId: String?,
    val commandOrder: Int?, val dependsOnMutationId: String?,
)

object SyncContractV2Codec {
    val json: Json = Json { explicitNulls = true; encodeDefaults = true; ignoreUnknownKeys = false }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    fun decodeFinancial(value: String): FinancialAggregateSnapshotV2 =
        json.decodeFromString(FinancialAggregateSnapshotV2.serializer(), value)

    fun financialProjection(snapshot: FinancialAggregateSnapshotV2): JsonObject {
        val root = json.encodeToJsonElement(FinancialAggregateSnapshotV2.serializer(), snapshot) as JsonObject
        return stripHashes(root, rootLevel = true) as JsonObject
    }

    fun financialBusinessHash(snapshot: FinancialAggregateSnapshotV2): String =
        sha256(encodeElement(financialProjection(snapshot)))

    fun requireValid(snapshot: FinancialAggregateSnapshotV2) {
        require(snapshot.schemaVersion == SYNC_REPAIR_PAYLOAD_VERSION) { "CONTRACT_VERSION_INVALID: schemaVersion" }
        require(snapshot.snapshotKind == SnapshotKindV2.FULL) { "CONTRACT_FIELD_INVALID: snapshotKind" }
        requireText(snapshot.organizationId, "organizationId")
        requireText(snapshot.invoiceId, "invoiceId")
        require(snapshot.header.organizationId == snapshot.organizationId) { "SCOPE_MISMATCH: header.organizationId" }
        require(snapshot.header.id == snapshot.invoiceId) { "CONTRACT_REFERENCE_INVALID: header.id" }
        require(snapshot.financialStreamVersion > 0) { "CONTRACT_FIELD_INVALID: financialStreamVersion" }
        snapshot.expectedFinancialStreamVersion?.let { require(it >= 0) { "CONTRACT_FIELD_INVALID: expectedFinancialStreamVersion" } }
        require(snapshot.items.all { it.invoiceId == snapshot.invoiceId }) { "CONTRACT_REFERENCE_INVALID: items.invoiceId" }
        require(snapshot.dueInstallments.all { it.invoiceId == snapshot.invoiceId }) { "CONTRACT_REFERENCE_INVALID: dueInstallments.invoiceId" }
        require(snapshot.payments.all { it.invoiceId == snapshot.invoiceId }) { "CONTRACT_REFERENCE_INVALID: payments.invoiceId" }
        require(snapshot.paymentAllocations.all { it.invoiceId == snapshot.invoiceId }) { "CONTRACT_REFERENCE_INVALID: paymentAllocations.invoiceId" }
        require(snapshot.realizedFxEvents.all { it.invoiceId == snapshot.invoiceId }) { "CONTRACT_REFERENCE_INVALID: realizedFxEvents.invoiceId" }
        require(snapshot.returnDocuments.all { it.organizationId == snapshot.organizationId && it.originalInvoiceId == snapshot.invoiceId }) {
            "CONTRACT_REFERENCE_INVALID: returnDocuments"
        }
        requireUnique(snapshot.items.map { it.id }, "items.id")
        requireUnique(snapshot.payments.map { it.id }, "payments.id")
        requireUnique(snapshot.effectReferences.map { "${it.owner}|${it.factType}|${it.factId}" }, "effectReferences")
        snapshot.effectReferences.forEach { requireHash(it.contentHash, "effectReferences.contentHash") }
        requireHash(snapshot.businessContentHash, "businessContentHash")
        require(snapshot.businessContentHash == financialBusinessHash(snapshot)) { "CONTRACT_HASH_MISMATCH: businessContentHash" }
    }

    fun requireValid(value: InventoryMovementDtoV2) {
        listOf(value.id, value.itemId, value.organizationId, value.movementKind, value.commandId,
            value.idempotencyKey, value.deviceId).forEachIndexed { i, field -> requireText(field, "inventoryMovement[$i]") }
        require(value.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_VERSION_INVALID: inventoryMovement.contractVersion" }
        require(value.occurredAt > 0 && value.createdAt > 0) { "CONTRACT_FIELD_INVALID: inventoryMovement time" }
        value.serverSequence?.let { require(it > 0) { "CONTRACT_FIELD_INVALID: serverSequence" } }
    }

    fun requireValid(value: InventoryCostRevisionDtoV2) {
        listOf(value.costRevisionId, value.organizationId, value.itemId, value.revisionKind,
            value.currencyCode, value.exchangeRateSnapshot, value.commandId, value.idempotencyKey,
            value.deviceId).forEachIndexed { i, field -> requireText(field, "inventoryCost[$i]") }
        require(value.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_VERSION_INVALID: inventoryCost.contractVersion" }
        value.costSequence?.let { require(it > 0) { "CONTRACT_FIELD_INVALID: costSequence" } }
    }

    fun requireValid(value: ClientCreditDtoV2) {
        requireText(value.id, "clientCredit.id"); requireText(value.clientId, "clientCredit.clientId")
        requireText(value.sourcePaymentId, "clientCredit.sourcePaymentId"); require(value.createdAt > 0)
    }

    fun requireValid(value: CostAllocationDtoV2) {
        listOf(value.id, value.itemId, value.sourceType, value.sourceId, value.method)
            .forEachIndexed { index, field -> requireText(field, "costAllocation[$index]") }
        require(value.allocatedAmount.isFinite() && value.perUnitCost.isFinite()) {
            "CONTRACT_FIELD_INVALID: costAllocation finite values"
        }
        require(value.createdAt > 0) { "CONTRACT_FIELD_INVALID: costAllocation.createdAt" }
    }

    fun requireValid(value: PurchaseRequestDtoV2, organizationId: String) {
        requireText(organizationId, "purchaseRequest.organizationId")
        require(value.purchaseOrders.all { it.organizationId == organizationId }) { "SCOPE_MISMATCH: purchaseOrders" }
        require(value.goodsReceipts.all { it.organizationId == organizationId }) { "SCOPE_MISMATCH: goodsReceipts" }
        require(value.matches.all { it.organizationId == organizationId }) { "SCOPE_MISMATCH: matches" }
        require(value.allocations.all { it.organizationId == organizationId }) { "SCOPE_MISMATCH: allocations" }
        require(value.paymentOverrides.all { it.organizationId == organizationId }) { "SCOPE_MISMATCH: paymentOverrides" }
        require(value.attachments.all { it.transferState == "LOCAL_PENDING" }) {
            "CONTRACT_FIELD_INVALID: attachment.transferState"
        }
        val orderIds = value.purchaseOrders.map { it.id }.toSet()
        require(value.purchaseOrderLines.all { it.purchaseOrderId in orderIds }) {
            "CONTRACT_REFERENCE_INVALID: purchaseOrderLines.purchaseOrderId"
        }
        val receiptIds = value.goodsReceipts.map { it.id }.toSet()
        require(value.goodsReceiptLines.all { it.goodsReceiptId in receiptIds }) {
            "CONTRACT_REFERENCE_INVALID: goodsReceiptLines.goodsReceiptId"
        }
        val matchIds = value.matches.map { it.id }.toSet()
        require(value.matchLines.all { it.matchId in matchIds }) {
            "CONTRACT_REFERENCE_INVALID: matchLines.matchId"
        }
    }

    fun requireValid(value: SyncBatchRequestDtoV2) {
        require(value.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY &&
            value.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_UNSUPPORTED: batch" }
        requireText(value.organizationId, "batch.organizationId"); requireText(value.batchId, "batch.batchId")
        require(value.memberCount == value.members.size && value.memberCount > 0) {
            "CONTRACT_FIELD_INVALID: memberCount"
        }
        require(value.members.map { it.memberOrder } == value.members.indices.toList()) {
            "CONTRACT_FIELD_INVALID: memberOrder"
        }
        value.members.forEach { requireHash(it.memberWireSha256, "memberWireSha256") }
        value.snapshotBlobs.forEach { requireHash(it.sha256, "snapshotBlob.sha256") }
    }

    fun requireValid(envelope: SyncMutationEnvelopeV2) {
        require(envelope.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY) { "CONTRACT_UNSUPPORTED: contractFamily" }
        require(envelope.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_UNSUPPORTED: contractVersion" }
        require(envelope.payloadVersion == SYNC_REPAIR_PAYLOAD_VERSION) { "CONTRACT_UNSUPPORTED: payloadVersion" }
        listOf(envelope.mutationId, envelope.organizationId, envelope.aggregateType, envelope.aggregateId,
            envelope.operationType).forEachIndexed { i, field -> requireText(field, "envelope[$i]") }
        require(envelope.localSequence > 0 && envelope.aggregateSequence > 0) { "CONTRACT_FIELD_INVALID: sequence" }
        envelope.baseVersion?.let { require(it >= 0) { "CONTRACT_FIELD_INVALID: baseVersion" } }
    }

    fun checkedMultiply(left: Long, right: Long, field: String): Long = try {
        Math.multiplyExact(left, right)
    } catch (e: ArithmeticException) {
        throw IllegalArgumentException("CONTRACT_FIELD_INVALID: overflow $field", e)
    }

    private fun encodeElement(value: JsonElement): String = value.toString()

    private fun stripHashes(value: JsonElement, rootLevel: Boolean = false): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.entries.mapNotNull { (key, child) ->
            val excluded = key == "businessContentHash" || key.endsWith("Hash") ||
                (rootLevel && key in setOf("financialStreamVersion", "expectedFinancialStreamVersion"))
            if (excluded) null else key to stripHashes(child)
        }.toMap(LinkedHashMap()))
        is JsonArray -> JsonArray(value.map { stripHashes(it) })
        else -> value
    }

    private fun requireText(value: String, field: String) =
        require(value.isNotBlank()) { "CONTRACT_FIELD_MISSING: $field" }

    private fun requireHash(value: String, field: String) = require(
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    ) { "CONTRACT_FIELD_INVALID: $field" }

    private fun requireUnique(values: List<String>, field: String) =
        require(values.size == values.toSet().size) { "CONTRACT_FIELD_INVALID: duplicate $field" }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
