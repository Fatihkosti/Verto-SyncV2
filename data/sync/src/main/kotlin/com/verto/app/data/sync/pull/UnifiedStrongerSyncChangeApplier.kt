package com.verto.app.data.sync.pull

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.CashDenominationEntity
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.CashReconciliationEntity
import com.verto.app.data.local.entity.CashRegisterEntity
import com.verto.app.data.local.entity.CashRegisterMovementEntity
import com.verto.app.data.local.entity.ClientCreditEntity
import com.verto.app.data.local.entity.CommissionPaymentEntity
import com.verto.app.data.local.entity.CostAllocationEntity
import com.verto.app.data.local.entity.CostAllocationMethod
import com.verto.app.data.local.entity.CostAllocationSource
import com.verto.app.data.local.entity.ExpenseEntity
import com.verto.app.data.local.entity.ExpenseRevisionHistoryEntity
import com.verto.app.data.local.entity.GoodsReceiptEntity
import com.verto.app.data.local.entity.GoodsReceiptLineEntity
import com.verto.app.data.local.entity.InventoryCostRevisionEntity
import com.verto.app.data.local.entity.InventoryCostRevisionKind
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryMovementKind
import com.verto.app.data.local.entity.MovementType
import com.verto.app.data.local.entity.OptimalMaintenanceFollowUpEntity
import com.verto.app.data.local.entity.OptimalMaintenanceFollowUpStatus
import com.verto.app.data.local.entity.OptimalMaintenanceImageEntity
import com.verto.app.data.local.entity.OptimalMaintenanceRecordEntity
import com.verto.app.data.local.entity.OptimalOutboxStatus
import com.verto.app.data.local.entity.OptimalVehicleEntity
import com.verto.app.data.local.entity.PurchaseCycleAttachmentEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchLineEntity
import com.verto.app.data.local.entity.PurchaseInvoiceReceiptAllocationEntity
import com.verto.app.data.local.entity.PurchasePaymentOverrideEntity
import com.verto.app.data.local.entity.ReconciliationStatus
import com.verto.app.data.sync.SyncChange
import com.verto.app.data.sync.expense.ExpenseRevisionSnapshotB12
import com.verto.app.data.sync.expense.expenseCashDeltaMinorB12
import com.verto.app.data.sync.expense.expenseContentHashB12
import com.verto.app.money.Money
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Session 310 REMOTE_APPLY boundary. It writes canonical domain tables/inboxes only and never calls
 * producer APIs or any network API. B09 financial snapshots materialize real domain tables.
 * The outer Pull Engine owns the Room group transaction, APPLIED state and cursor.
 */
@Singleton
class UnifiedStrongerSyncChangeApplier @Inject constructor(
    private val database: AppDatabase,
    private val financialMaterializer: FinancialMaterializerV2,
) {
    suspend fun apply(change: SyncChange) = applyMaterialization(ChangeMaterialization(change))

    internal fun beginFinancialBatch(organizationId: String, scopeId: String) =
        financialMaterializer.beginBatch(organizationId, scopeId)

    internal suspend fun completeFinancialBatch(batch: FinancialApplyBatchV2) = financialMaterializer.completeBatch(batch)

    internal suspend fun applyMaterialization(
        change: UnifiedRemoteMaterialization,
        financialBatch: FinancialApplyBatchV2? = null,
    ) {
        when (change.aggregateType) {
            "INVOICE", "PAYMENT" -> if (financialBatch == null) financialMaterializer.materialize(change)
                else financialMaterializer.apply(change, financialBatch)
            "CLIENT_CREDIT" -> applyClientCredit(change)
            "GOODS_RECEIPT" -> applyGoodsReceipt(change)
            "PURCHASE_MATCH" -> applyPurchaseMatch(change)
            "PURCHASE_PAYMENT_OVERRIDE" -> applyPurchasePaymentOverride(change)
            "INVENTORY_MOVEMENT" -> applyInventoryMovement(change)
            "INVENTORY_COST_REVISION" -> applyInventoryCost(change)
            "COST_ALLOCATION" -> applyCostAllocation(change)
            "EXPENSE" -> applyExpense(change)
            "CASH_REGISTER" -> applyCashRegister(change)
            "CASH_MOVEMENT" -> applyCashMovement(change)
            "CASH_RECONCILIATION" -> applyCashReconciliation(change)
            "COMMISSION_PAYMENT" -> applyCommissionPayment(change)
            "OPTIMAL_VEHICLE" -> applyOptimalVehicle(change)
            "OPTIMAL_MAINTENANCE" -> applyOptimalMaintenance(change)
            "OPTIMAL_FOLLOW_UP" -> applyOptimalFollowUp(change)
            else -> throw UnifiedSyncPullFailure("CONTRACT_UNSUPPORTED", "not a Session 310 aggregate: ${change.aggregateType}")
        }
    }

    private suspend fun applyClientCredit(c: UnifiedRemoteMaterialization) {
        val p = c.payload.objOrSelf("materialization")
        check(p.reqString("id") == c.aggregateId) { "SCOPE_MISMATCH: client credit identity" }
        val amountMinor = p.reqLong("amountMinor")
        database.clientCreditDao().insertCreditFromRemote(
            ClientCreditEntity(
                id = c.aggregateId,
                clientId = p.reqString("clientId"),
                amount = Money.ofMinor(amountMinor).toLegacyDouble(),
                amountMinor = amountMinor,
                note = p.requiredStringAllowEmpty("note"),
                sourcePaymentId = p.reqString("sourcePaymentId"),
                createdAt = p.reqLong("createdAt"),
                employeeId = p.requiredStringAllowEmpty("employeeId"),
                employeeName = p.requiredStringAllowEmpty("employeeName"),
                isDirty = false,
            )
        )
    }

    private suspend fun applyGoodsReceipt(c: UnifiedRemoteMaterialization) {
        val p = c.payload.objOrSelf("materialization")
        val r = p.obj("receipt")
        val receipt = GoodsReceiptEntity(
            id = c.aggregateId, organizationId = c.organizationId,
            purchaseOrderId = r.reqString("purchaseOrderId"), receiptNumber = r.reqString("receiptNumber"),
            receivedAt = r.reqLong("receivedAt"), receivedBy = r.reqString("receivedBy"),
            receivedByName = r.string("receivedByName"), note = r.string("note"), writeId = r.reqString("writeId"),
        )
        val lines = p.array("lines").map { e ->
            val o = e.asObj()
            GoodsReceiptLineEntity(
                id = o.reqString("id"), goodsReceiptId = receipt.id,
                purchaseOrderLineId = o.reqString("purchaseOrderLineId"), inventoryItemId = o.nullableString("inventoryItemId"),
                receivedQuantity = o.reqInt("receivedQuantity"), acceptedQuantity = o.reqInt("acceptedQuantity"),
                rejectedQuantity = o.reqInt("rejectedQuantity"), unitCostMinor = o.reqLong("unitCostMinor"),
            )
        }
        val attachments = p.array("attachments").map { e ->
            val o = e.asObj()
            PurchaseCycleAttachmentEntity(
                id = o.reqString("id"), organizationId = c.organizationId, ownerType = o.reqString("ownerType"),
                ownerId = o.reqString("ownerId"), uri = o.reqString("uri"), mimeType = o.string("mimeType"),
                displayName = o.string("displayName"), createdAt = o.long("createdAt") ?: c.changedAtEpochMillis,
                writeId = o.reqString("writeId"),
            )
        }
        database.purchaseCycleDao().applyRemotePreCycle(c.organizationId, emptyList(), emptyList(), listOf(receipt), lines, attachments)
    }

    private suspend fun applyPurchaseMatch(c: UnifiedRemoteMaterialization) {
        val p = c.payload.objOrSelf("materialization")
        val m = p.obj("match")
        val match = PurchaseInvoiceMatchEntity(
            id = c.aggregateId, organizationId = c.organizationId, invoiceId = m.reqString("invoiceId"),
            purchaseOrderId = m.reqString("purchaseOrderId"), status = m.reqString("status"),
            quantityVarianceUnits = m.reqInt("quantityVarianceUnits"), priceVarianceMinor = m.reqLong("priceVarianceMinor"),
            quantityToleranceUnits = m.reqInt("quantityToleranceUnits"), priceToleranceMinor = m.reqLong("priceToleranceMinor"),
            invoiceAmountMinor = m.reqLong("invoiceAmountMinor"), payableAmountMinor = m.reqLong("payableAmountMinor"),
            varianceReason = m.nullableString("varianceReason"), approvedBy = m.nullableString("approvedBy"),
            approvedByName = m.nullableString("approvedByName"), matchedAt = m.reqLong("matchedAt"), writeId = m.reqString("writeId"),
        )
        val lines = p.array("lines").map { e ->
            val o=e.asObj(); PurchaseInvoiceMatchLineEntity(
                id=o.reqString("id"), matchId=match.id, invoiceItemId=o.reqString("invoiceItemId"),
                purchaseOrderLineId=o.reqString("purchaseOrderLineId"), orderedQuantity=o.reqInt("orderedQuantity"),
                acceptedQuantity=o.reqInt("acceptedQuantity"), invoicedQuantity=o.reqInt("invoicedQuantity"),
                poUnitPriceMinor=o.reqLong("poUnitPriceMinor"), invoiceUnitPriceMinor=o.reqLong("invoiceUnitPriceMinor"),
                quantityVarianceUnits=o.reqInt("quantityVarianceUnits"), priceVarianceMinor=o.reqLong("priceVarianceMinor"),
                payableAmountMinor=o.reqLong("payableAmountMinor"),
            )
        }
        val allocs = p.array("allocations").map { e ->
            val o=e.asObj(); PurchaseInvoiceReceiptAllocationEntity(
                id=o.reqString("id"), organizationId=c.organizationId, matchLineId=o.reqString("matchLineId"),
                goodsReceiptLineId=o.reqString("goodsReceiptLineId"), allocatedQuantity=o.reqInt("allocatedQuantity"),
                createdAt=o.long("createdAt") ?: c.changedAtEpochMillis, writeId=o.reqString("writeId"),
            )
        }
        database.purchaseCycleDao().applyRemotePostCycle(c.organizationId, listOf(match), lines, allocs, emptyList())
    }

    private suspend fun applyPurchasePaymentOverride(c: UnifiedRemoteMaterialization) {
        val o = c.payload.objOrSelf("materialization").objOrSelf("override")
        val row = PurchasePaymentOverrideEntity(
            id=c.aggregateId, organizationId=c.organizationId, invoiceId=o.reqString("invoiceId"),
            paymentRequestId=o.reqString("paymentRequestId"), requestedAmountMinor=o.reqLong("requestedAmountMinor"),
            payableBeforeOverrideMinor=o.reqLong("payableBeforeOverrideMinor"), reason=o.reqString("reason"),
            approvedBy=o.reqString("approvedBy"), approvedByName=o.string("approvedByName"),
            createdAt=o.long("createdAt") ?: c.changedAtEpochMillis,
        )
        database.purchaseCycleDao().applyRemotePostCycle(c.organizationId, emptyList(), emptyList(), emptyList(), listOf(row))
    }

    private suspend fun applyInventoryMovement(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("movement")
        val signed=p.reqLong("signedBaseQuantity")
        val unitPriceMinor = p.reqLong("unitPriceMinor")
        val serverSequence = p.reqLong("serverSequence").also { require(it > 0L) { "CONTRACT_FIELD_INVALID: serverSequence" } }
        val contractVersion = p.reqInt("contractVersion").also { require(it == 2) { "CONTRACT_VERSION_INVALID" } }
        val row=InventoryMovementEntity(
            id=c.aggregateId, itemId=p.reqString("itemId"), invoiceId=p.requiredStringAllowEmpty("invoiceId"), clientId=p.requiredStringAllowEmpty("clientId"),
            movementType=enumValue<MovementType>(p.reqString("movementType")), quantity=p.reqInt("quantity"),
            quantityBefore=p.reqInt("quantityBefore"), quantityAfter=p.reqInt("quantityAfter"),
            unitPrice=Money.ofMinor(unitPriceMinor).toLegacyDouble(), unitPriceMinor=unitPriceMinor, note=p.requiredStringAllowEmpty("note"),
            shipmentId=p.requiredStringAllowEmpty("shipmentId"), sourceType=p.requiredStringAllowEmpty("sourceType"), sourceId=p.requiredStringAllowEmpty("sourceId"),
            sourceVersion=p.reqInt("sourceVersion"), writeId=p.requiredStringAllowEmpty("writeId"), organizationId=c.organizationId,
            movementKind=enumValue<InventoryMovementKind>(p.reqString("movementKind")), signedBaseQuantity=signed, sourceLineId=p.requiredNullableString("sourceLineId"),
            commandId=p.reqString("commandId"), idempotencyKey=p.reqString("idempotencyKey"), postingGroupId=p.requiredNullableString("postingGroupId"),
            reversesMovementId=p.requiredNullableString("reversesMovementId"), conversionFactorSnapshot=p.reqString("conversionFactorSnapshot"),
            occurredAt=p.reqLong("occurredAt"), recordedAt=p.reqLong("recordedAt"), serverAcceptedAt=p.reqLong("serverAcceptedAt"),
            serverSequence=serverSequence, createdBy=p.requiredNullableString("createdBy"), deviceId=p.reqString("deviceId"),
            contractVersion=contractVersion, createdAt=p.reqLong("createdAt"),
        )
        database.inventoryDao().applyPulledInventoryMovements(c.organizationId, listOf(row), row.serverSequence!!, c.changedAtEpochMillis)
    }

    private suspend fun applyInventoryCost(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("costRevision")
        val costSequence = p.reqLong("costSequence").also { require(it > 0L) { "CONTRACT_FIELD_INVALID: costSequence" } }
        val contractVersion = p.reqInt("contractVersion").also { require(it == 2) { "CONTRACT_VERSION_INVALID" } }
        val row=InventoryCostRevisionEntity(
            costRevisionId=c.aggregateId, organizationId=c.organizationId, itemId=p.reqString("itemId"), sourceType=p.reqString("sourceType"),
            sourceId=p.reqString("sourceId"), sourceLineId=p.requiredNullableString("sourceLineId"), revisionKind=enumValue<InventoryCostRevisionKind>(p.reqString("revisionKind")),
            directPurchaseCostMinor=p.reqLong("directPurchaseCostMinor"), landedCostPerBaseUnitMinor=p.reqLong("landedCostPerBaseUnitMinor"),
            approvedInventoryCostMinor=p.reqLong("approvedInventoryCostMinor"), currencyCode=p.reqString("currencyCode"),
            exchangeRateSnapshot=p.reqString("exchangeRateSnapshot"), allocationBasis=p.requiredStringAllowEmpty("allocationBasis"),
            allocationResidualMinor=p.reqLong("allocationResidualMinor"), isProvisional=p.reqBool("isProvisional"),
            reversesCostRevisionId=p.requiredNullableString("reversesCostRevisionId"), commandId=p.reqString("commandId"),
            idempotencyKey=p.reqString("idempotencyKey"), costSequence=costSequence, approvedAt=p.reqLong("approvedAt"),
            recordedAt=p.reqLong("recordedAt"), createdBy=p.reqString("createdBy"), deviceId=p.reqString("deviceId"),
            contractVersion=contractVersion,
        )
        database.inventoryDao().applyPulledInventoryCostRevisions(c.organizationId, listOf(row), row.costSequence!!, c.changedAtEpochMillis)
    }

    private suspend fun applyCostAllocation(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("materialization")
        val row=CostAllocationEntity(
            id=c.aggregateId, itemId=p.reqString("itemId"), sourceType=enumValue(p.string("sourceType", "SHIPMENT_COST")),
            sourceId=p.string("sourceId"), allocatedAmount=p.reqDouble("allocatedAmount"), perUnitCost=p.double("perUnitCost") ?: 0.0,
            quantityAffected=p.int("quantityAffected") ?: 0, method=enumValue(p.string("method", "BY_QUANTITY")),
            note=p.string("note"), createdAt=p.long("createdAt") ?: c.changedAtEpochMillis,
        )
        val old=database.costAllocationDao().getByIdSync(c.aggregateId)
        if (old == null) check(database.costAllocationDao().insertFromRemote(row) != -1L) else require(old == row) { "IMMUTABLE_COST_ALLOCATION_CONFLICT" }
    }

    private suspend fun applyExpense(c: UnifiedRemoteMaterialization) {
        val p = c.payload.objOrSelf("materialization")
        val lifecycle = p.string("lifecycleState", "ACTIVE")
        require(lifecycle == "ACTIVE" || lifecycle == "VOID") { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
        val amountMinor = p.reqLong("amountMinor")
        require(amountMinor > 0L) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
        val row = ExpenseEntity(
            id = c.aggregateId,
            category = p.reqString("category"),
            item = p.reqString("item"),
            amount = Money.ofMinor(amountMinor).toLegacyDouble(),
            amountMinor = amountMinor,
            note = p.string("note"),
            date = p.long("date") ?: c.changedAtEpochMillis,
            isDirty = false,
            lifecycleState = lifecycle,
            voidedAt = p.long("voidedAt"),
            voidReason = p.nullableString("voidReason"),
            reversalWriteId = p.nullableString("reversalWriteId"),
        )
        val old = database.expenseDao().getExpenseByIdSync(c.aggregateId)
        val revision = c.payload["expenseRevision"] as? JsonObject
        if (!c.isBootstrap && revision == null) {
            throw UnifiedSyncPullFailure("CONTRACT_UNSUPPORTED", "EXPENSE_REVISION_METADATA_REQUIRED")
        }
        revision?.let { r ->
            val serverVersion = r.reqLong("serverVersion")
            val previousVersion = r.long("previousVersion")
            val writeId = r.reqString("writeId")
            val beforeHash = r.nullableString("beforeContentHash")
            val afterHash = r.reqString("afterContentHash")
            val actorId = r.reqString("actorId")
            val changedAt = r.long("changedAt") ?: c.changedAtEpochMillis
            val cashDeltaMinor = r.reqLong("cashDeltaMinor")
            val cashMovementId = r.nullableString("cashMovementId")
            require(c.entityVersion == null || c.entityVersion == serverVersion) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
            val afterSnapshot = row.toExpenseRevisionSnapshotB12()
            require(expenseContentHashB12(afterSnapshot) == afterHash) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
            val frozenPacket = database.unifiedSyncDao().readMutationPacket(c.organizationId, writeId)
            if (frozenPacket != null) {
                val frozenRoot = Json.parseToJsonElement(frozenPacket.intentJson) as? JsonObject
                    ?: throw UnifiedSyncPullFailure("VALIDATION", "frozen expense intent must be object")
                val frozenPayload = frozenRoot.obj("payload")
                val frozenRevision = frozenPayload.obj("expenseRevisionIntent")
                require(frozenRevision.reqLong("cashDeltaMinor") == cashDeltaMinor) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
                require(frozenRevision.nullableString("beforeContentHash") == beforeHash) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
                require(frozenRevision.reqString("afterContentHash") == afterHash) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
                require(frozenRevision.nullableString("cashMovementId") == cashMovementId) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
            } else {
                val expectedDelta = expenseCashDeltaMinorB12(old?.toExpenseRevisionSnapshotB12(), afterSnapshot)
                require(expectedDelta == cashDeltaMinor) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
                if (old != null && beforeHash != null) {
                    require(expenseContentHashB12(old.toExpenseRevisionSnapshotB12()) == beforeHash) {
                        "BLOCKED_EXPENSE_DOMAIN_DRIFT"
                    }
                }
            }
            require((cashDeltaMinor == 0L) == (cashMovementId == null)) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
            database.unifiedSyncDao().putExpenseRevisionHistory(
                ExpenseRevisionHistoryEntity(
                    organizationId = c.organizationId,
                    expenseId = c.aggregateId,
                    serverVersion = serverVersion,
                    previousVersion = previousVersion,
                    writeId = writeId,
                    beforeContentHash = beforeHash,
                    afterContentHash = afterHash,
                    actorId = actorId,
                    changedAt = changedAt,
                )
            )
        }
        if (old == null) check(database.expenseDao().insertExpenseFromRemote(row) != -1L)
        else database.expenseDao().updateExpense(row)
    }

    private suspend fun applyCashRegister(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("materialization")
        database.cashRegisterDao().upsertRegister(
            CashRegisterEntity(id="main", balance=p.reqDouble("balance"), balanceMinor=p.reqLong("balanceMinor"), updatedAt=p.long("updatedAt") ?: c.changedAtEpochMillis)
        )
    }

    private suspend fun applyCashMovement(c: UnifiedRemoteMaterialization) =
        applyCashMovement336(database, c)

    private suspend fun applyCashReconciliation(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("materialization")
        val session=CashReconciliationEntity(
            id=c.aggregateId, employeeId=p.string("employeeId"), employeeName=p.string("employeeName"), openingBalance=p.double("openingBalance") ?: 0.0,
            totalSales=p.double("totalSales") ?: 0.0, totalRefunds=p.double("totalRefunds") ?: 0.0, totalCashIn=p.double("totalCashIn") ?: 0.0,
            totalCashOut=p.double("totalCashOut") ?: 0.0, expectedBalance=p.double("expectedBalance") ?: 0.0,
            actualCountedBalance=p.double("actualCountedBalance") ?: 0.0, variance=p.double("variance") ?: 0.0,
            varianceReason=p.string("varianceReason"), status=enumValue(p.string("status", "OPEN")),
            startedAt=p.long("startedAt") ?: c.changedAtEpochMillis, endedAt=p.long("endedAt"), notes=p.string("notes"),
        )
        database.cashReconciliationDao().upsertSessionFromRemote(session)
        val denoms=p.array("denominations").map { e -> val o=e.asObj(); CashDenominationEntity(
            id=o.reqString("id"), reconciliationId=c.aggregateId, denominationValue=o.reqDouble("denominationValue"),
            count=o.int("count") ?: 0, subtotal=o.double("subtotal") ?: 0.0, isCoin=o.bool("isCoin", false)
        ) }
        if (denoms.isNotEmpty()) database.cashReconciliationDao().upsertDenominationsFromRemote(denoms)
    }

    private suspend fun applyCommissionPayment(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("materialization")
        database.commissionPaymentDao().insertFromRemote(CommissionPaymentEntity(
            id=c.aggregateId, clientId=p.reqString("clientId"), clientName=p.string("clientName"), invoiceIds=p.string("invoiceIds"),
            totalAmount=p.reqDouble("totalAmount"), bankName=p.string("bankName"), transactionRef=p.string("transactionRef"),
            paidAt=p.long("paidAt") ?: c.changedAtEpochMillis,
        ))
    }

    private suspend fun applyOptimalVehicle(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("materialization")
        database.optimalVehicleDao().upsertFromRemote(OptimalVehicleEntity(
            organizationId=c.organizationId, clientId=p.reqString("clientId"), remoteVehicleId=c.aggregateId,
            name=p.reqString("name"), vehicleType=p.string("vehicleType"), plateNumber=p.string("plateNumber"),
            updatedAt=p.long("updatedAt") ?: c.changedAtEpochMillis,
        ))
    }

    private suspend fun applyOptimalMaintenance(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("materialization")
        val record=OptimalMaintenanceRecordEntity(
            organizationId=c.organizationId, recordId=c.aggregateId, invoiceId=p.reqString("invoiceId"),
            vehicleClientId=p.nullableString("vehicleClientId"), vehicleId=p.nullableString("vehicleId"),
            vehicleNameSnapshot=p.string("vehicleNameSnapshot"), vehicleTypeSnapshot=p.string("vehicleTypeSnapshot"),
            plateNumberSnapshot=p.string("plateNumberSnapshot"), driverOrDelegate=p.string("driverOrDelegate"), notes=p.string("notes"),
            syncStatus=OptimalOutboxStatus.SYNCED, createdAt=p.long("createdAt") ?: c.changedAtEpochMillis,
            updatedAt=p.long("updatedAt") ?: c.changedAtEpochMillis,
        )
        val images=p.array("images").mapIndexed { index,e -> val o=e.asObj(); OptimalMaintenanceImageEntity(
            organizationId=c.organizationId, imageId=o.reqString("imageId"), recordId=c.aggregateId,
            localUri=o.string("localUri"), storagePath=o.reqString("storagePath"), mimeType=o.string("mimeType"),
            byteSize=o.long("byteSize") ?: 0L, sortOrder=o.int("sortOrder") ?: index, syncStatus=OptimalOutboxStatus.SYNCED,
            createdAt=o.long("createdAt") ?: c.changedAtEpochMillis,
        ) }
        database.optimalMaintenanceDao().upsert(record, images)
    }

    private suspend fun applyOptimalFollowUp(c: UnifiedRemoteMaterialization) {
        val p=c.payload.objOrSelf("materialization")
        val status=enumValue<OptimalMaintenanceFollowUpStatus>(p.string("status", "IN_PROGRESS"))
        val existing=database.optimalMaintenanceFollowUpDao().get(c.organizationId, c.aggregateId)
        if (existing == null) {
            require(status == OptimalMaintenanceFollowUpStatus.IN_PROGRESS) { "FOLLOW_UP_PARENT_STATE_MISSING" }
            database.optimalMaintenanceFollowUpDao().start(OptimalMaintenanceFollowUpEntity(
                organizationId=c.organizationId, recordId=c.aggregateId, status=status,
                startedAt=p.long("startedAt") ?: c.changedAtEpochMillis, expectedAt=p.long("expectedAt"),
                updatedAt=p.long("updatedAt") ?: c.changedAtEpochMillis,
            ))
        } else {
            val updatedAt=p.long("updatedAt") ?: c.changedAtEpochMillis
            if (existing.expectedAt != p.long("expectedAt") && existing.status == OptimalMaintenanceFollowUpStatus.IN_PROGRESS) {
                database.optimalMaintenanceFollowUpDao().updateExpectedAt(c.organizationId, c.aggregateId, p.long("expectedAt"), updatedAt)
            }
            if (existing.status != status) database.optimalMaintenanceFollowUpDao().updateStatus(c.organizationId, c.aggregateId, status, updatedAt)
        }
    }

    private inline fun <reified T:Enum<T>> enumValue(v:String):T = runCatching { enumValueOf<T>(v) }
        .getOrElse { throw UnifiedSyncPullFailure("VALIDATION", "invalid enum ${T::class.simpleName}=$v") }
}

private fun ExpenseEntity.toExpenseRevisionSnapshotB12() = ExpenseRevisionSnapshotB12(
    id = id, category = category, item = item, amountMinor = amountMinor, note = note, date = date,
    lifecycleState = lifecycleState, voidedAt = voidedAt, voidReason = voidReason, reversalWriteId = reversalWriteId,
)

private fun JsonElement.asObj():JsonObject = this as? JsonObject ?: throw UnifiedSyncPullFailure("VALIDATION", "expected object")
private fun JsonObject.obj(name:String):JsonObject = this[name] as? JsonObject ?: throw UnifiedSyncPullFailure("VALIDATION", "missing object $name")
private fun JsonObject.objOrSelf(name:String):JsonObject = this[name] as? JsonObject ?: this
private fun JsonObject.array(name:String):List<JsonElement> = (this[name] as? JsonArray)?.toList() ?: emptyList()
private fun JsonObject.primitive310(name:String):JsonPrimitive? = this[name] as? JsonPrimitive
private fun JsonObject.string(name:String, default:String=""):String = primitive310(name)?.content ?: default
private fun JsonObject.nullableString(name:String):String? = primitive310(name)?.content?.takeUnless { it=="null" || it.isBlank() }
private fun JsonObject.reqString(name:String):String = primitive310(name)?.content?.takeIf { it.isNotBlank() } ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
private fun JsonObject.requiredStringAllowEmpty(name:String):String = primitive310(name)?.content?.takeUnless { it == "null" }
    ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
private fun JsonObject.requiredNullableString(name:String):String? {
    if (!containsKey(name)) throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
    return nullableString(name)
}
private fun JsonObject.long(name:String):Long? = primitive310(name)?.longOrNull
private fun JsonObject.reqLong(name:String):Long = long(name) ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
private fun JsonObject.int(name:String):Int? = primitive310(name)?.intOrNull
private fun JsonObject.reqInt(name:String):Int = int(name) ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
private fun JsonObject.double(name:String):Double? = primitive310(name)?.doubleOrNull
private fun JsonObject.reqDouble(name:String):Double = double(name) ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
private fun JsonObject.bool(name:String, default:Boolean):Boolean = primitive310(name)?.booleanOrNull ?: default
private fun JsonObject.reqBool(name:String):Boolean = primitive310(name)?.booleanOrNull
    ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
