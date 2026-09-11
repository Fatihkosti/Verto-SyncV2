package com.verto.app.feature.invoice.bridge

import com.verto.app.utils.normalizeSupplierInvoiceReference
import com.verto.app.data.local.entity.CashMovementType as PersistenceCashMovementType
import com.verto.app.data.local.entity.InvoiceCategory as PersistenceInvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceDueInstallmentEntity
import com.verto.app.data.local.entity.InvoiceStatus as PersistenceInvoiceStatus
import com.verto.app.data.local.entity.InvoiceLifecycleStatus as PersistenceInvoiceLifecycleStatus
import com.verto.app.data.local.entity.InvoiceType
import com.verto.app.data.local.entity.ItemType
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.data.local.entity.PaymentAllocationEntity
import com.verto.app.data.local.entity.RealizedFxEventEntity
import com.verto.app.data.local.entity.LegacyCurrencyStatus as PersistenceLegacyCurrencyStatus
import com.verto.app.data.local.entity.PaymentMethod
import com.verto.app.data.local.entity.PurchaseScope as PersistencePurchaseScope
import com.verto.app.data.remote.InvoiceNumberAllocator
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.feature.invoice.domain.model.InvoiceCashMovementType
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoicePayment
import com.verto.app.feature.invoice.domain.model.InvoicePaymentAllocation
import com.verto.app.feature.invoice.domain.model.InvoiceDueInstallment
import com.verto.app.feature.invoice.domain.model.RealizedFxEvent
import com.verto.app.feature.invoice.domain.model.LegacyCurrencyStatus
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMethod
import com.verto.app.feature.invoice.domain.model.InvoiceRecord
import com.verto.app.feature.invoice.domain.model.InvoiceStatus
import com.verto.app.feature.invoice.domain.model.InvoiceLifecycleStatus
import com.verto.app.feature.invoice.domain.model.InvoiceStockItem
import com.verto.app.feature.inventory.domain.model.InventoryPurchaseReceiptCommand
import com.verto.app.feature.invoice.domain.model.InvoiceInventoryRevaluationRecord
import com.verto.app.feature.invoice.domain.model.InvoicePurchaseStockCommand
import com.verto.app.feature.invoice.domain.model.InvoicePostingIdentity
import com.verto.app.feature.invoice.domain.model.InvoiceSaleStockMutation
import com.verto.app.feature.invoice.domain.model.InvoiceWriteClaim
import com.verto.app.feature.invoice.domain.model.InvoiceWriteIdentity
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.inventory.domain.model.InventoryItem
import com.verto.app.feature.inventory.domain.model.InventoryPostingIdentity
import com.verto.app.feature.inventory.domain.model.InventorySaleStockMutation
import com.verto.app.feature.inventory.domain.port.InventoryStockPort
import com.verto.app.feature.inventory.domain.port.InventoryLinePostingStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceNumberPort
import com.verto.app.feature.invoice.domain.port.InvoiceSettingsPort
import com.verto.app.feature.invoice.domain.port.InvoiceStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceLinePostingStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceStorePort
import com.verto.app.feature.invoice.domain.port.InvoiceSyncSchedulerPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncCoordinator
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScope
import com.verto.app.utils.CashRegisterManager
import com.verto.app.money.Money
import com.verto.app.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RepositoryInvoiceStoreAdapter @Inject constructor(
    private val repository: InvoiceRepository
) : InvoiceStorePort {
    override suspend fun getInvoiceById(invoiceId: String): InvoiceRecord? =
        repository.getInvoiceByIdSync(invoiceId)?.toDomain()

    override suspend fun getInvoiceItems(invoiceId: String): List<InvoiceLine> =
        repository.getInvoiceItemsSync(invoiceId).map { it.toDomain() }

    override suspend fun getTotalPaid(invoiceId: String): Double =
        repository.getTotalPaidForInvoiceSync(invoiceId)

    override suspend fun getPayments(invoiceId: String): List<InvoicePayment> =
        repository.getPaymentsForInvoiceSync(invoiceId).map { it.toDomain() }

    override suspend fun getPaymentAllocations(invoiceId: String): List<InvoicePaymentAllocation> =
        repository.getPaymentAllocationsForInvoiceSync(invoiceId).map { it.toDomain() }

    override suspend fun getRealizedFxEvents(invoiceId: String): List<RealizedFxEvent> =
        repository.getRealizedFxEventsForInvoiceSync(invoiceId).map { it.toDomain() }

    override suspend fun getDueInstallments(invoiceId: String): List<InvoiceDueInstallment> =
        repository.getDueInstallmentsSync(invoiceId).map { it.toDomain() }

    override suspend fun getReversalForPayment(originalPaymentId: String): InvoicePayment? =
        repository.getReversalForPaymentSync(originalPaymentId)?.toDomain()

    override suspend fun claimWrite(identity: InvoiceWriteIdentity): InvoiceWriteClaim {
        val (invoiceId, claimed) = repository.claimInvoiceWrite(
            organizationId = identity.organizationId,
            operationType = identity.operation.name,
            writeId = identity.writeId,
            targetInvoiceId = identity.invoiceId,
            sourceVersion = identity.sourceVersion,
        )
        return InvoiceWriteClaim(invoiceId = invoiceId, claimed = claimed)
    }

    override suspend fun insertInvoiceWithItems(
        invoice: InvoiceRecord,
        items: List<InvoiceLine>
    ): Pair<String, Int> = repository.insertInvoiceWithItems(
        invoice.toEntity(),
        items.map { it.toEntity() }
    )

    override suspend fun updateInvoice(invoice: InvoiceRecord) =
        repository.updateInvoice(invoice.toEntity())

    override suspend fun updatePostedDescription(invoice: InvoiceRecord, expectedVersion: Int): Boolean =
        repository.updatePostedDescriptionOptimistic(
            id = invoice.id,
            notes = invoice.notes,
            dueDate = invoice.dueDate,
            expectedVersion = expectedVersion,
        )

    override suspend fun updateInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>) =
        repository.updateInvoiceWithItems(invoice.toEntity(), items.map { it.toEntity() })

    override suspend fun addPayment(payment: InvoicePayment): String =
        repository.addPayment(payment.toEntity())

    override suspend fun addPaymentAllocation(allocation: InvoicePaymentAllocation) =
        repository.addPaymentAllocation(allocation.toEntity())

    override suspend fun addRealizedFxEvent(event: RealizedFxEvent) =
        repository.addRealizedFxEvent(event.toEntity())

    override suspend fun replaceDueInstallments(invoiceId: String, installments: List<InvoiceDueInstallment>) =
        repository.replaceDueInstallments(invoiceId, installments.map { it.toEntity() })

    override suspend fun deletePayments(invoiceId: String) =
        repository.deletePaymentsByInvoiceId(invoiceId)

    override suspend fun markVoided(
        invoiceId: String,
        expectedVersion: Int,
        voidedAt: Long,
        reason: String,
        writeId: String,
    ): Boolean = repository.markInvoiceVoidedOptimistic(
        id = invoiceId,
        expectedVersion = expectedVersion,
        voidedAt = voidedAt,
        reason = reason,
        writeId = writeId,
    )
}

private fun InvoiceDueInstallmentEntity.toDomain() = InvoiceDueInstallment(
    id = id, invoiceId = invoiceId, sequence = sequence, amountMinor = amountMinor,
    currencyCode = currencyCode, dueDate = dueDate, createdAt = createdAt, writeId = writeId,
)

private fun InvoiceDueInstallment.toEntity() = InvoiceDueInstallmentEntity(
    id = id, invoiceId = invoiceId, sequence = sequence, amountMinor = amountMinor,
    currencyCode = currencyCode, dueDate = dueDate, createdAt = createdAt, writeId = writeId,
)

class InventoryInvoiceStockAdapter @Inject constructor(
    private val inventory: InventoryStockPort
) : InvoiceStockPort, InvoiceLinePostingStockPort {
    override suspend fun getAllItems(): List<InvoiceStockItem> =
        inventory.getAllItems().map { it.toInvoiceDomain() }

    override suspend fun getItem(itemId: String): InvoiceStockItem? =
        inventory.getItem(itemId)?.toInvoiceDomain()

    override suspend fun saveItem(item: InvoiceStockItem) =
        inventory.saveItem(item.toInventoryDomain())

    override suspend fun deductStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        clientId: String,
        unitPrice: Double,
        allowNegativeStock: Boolean,
        sourceWriteId: String,
    ): Result<Unit> = inventory.deductStock(
        itemId = itemId,
        quantity = quantity,
        invoiceId = invoiceId,
        clientId = clientId,
        unitPrice = unitPrice,
        allowNegativeStock = allowNegativeStock,
        sourceWriteId = sourceWriteId,
    )

    override suspend fun deductPostingLine(
        mutation: InvoiceSaleStockMutation,
        identity: InvoicePostingIdentity,
    ): Result<Unit> {
        val lineAware = inventory as? InventoryLinePostingStockPort
        return lineAware?.deductPostingLine(
            mutation = InventorySaleStockMutation(
                itemId = mutation.itemId,
                quantity = mutation.quantity,
                invoiceId = mutation.invoiceId,
                clientId = mutation.clientId,
                unitPrice = mutation.unitPrice,
                allowNegativeStock = mutation.allowNegativeStock,
            ),
            identity = InventoryPostingIdentity(
                writeId = identity.writeId,
                sourceLineId = identity.sourceLineId,
                postingGroupId = identity.postingGroupId,
            ),
        ) ?: inventory.deductStock(
            itemId = mutation.itemId,
            quantity = mutation.quantity,
            invoiceId = mutation.invoiceId,
            clientId = mutation.clientId,
            unitPrice = mutation.unitPrice,
            allowNegativeStock = mutation.allowNegativeStock,
            sourceWriteId = identity.writeId,
        )
    }

    override suspend fun addStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        supplierId: String,
        unitPrice: Double,
        sourceWriteId: String,
    ): Result<Unit> = inventory.addStock(
        itemId = itemId,
        quantity = quantity,
        invoiceId = invoiceId,
        supplierId = supplierId,
        unitPrice = unitPrice,
        sourceWriteId = sourceWriteId,
    )

    override suspend fun receivePurchaseAtLatestPrice(
        command: InvoicePurchaseStockCommand,
    ): InvoiceInventoryRevaluationRecord? = inventory.receivePurchaseAtLatestPrice(
        InventoryPurchaseReceiptCommand(
            itemId = command.itemId,
            quantity = command.quantity,
            invoiceId = command.invoiceId,
            supplierId = command.supplierId,
            buyPriceMinor = command.buyPriceMinor,
            sellPriceMinor = command.sellPriceMinor,
            actorId = command.actorId,
            actorName = command.actorName,
            occurredAt = command.occurredAt,
            writeId = command.writeId,
            eventId = command.eventId,
            sourceType = command.sourceType,
            sourceId = command.sourceId,
            sourceLineId = command.sourceLineId,
            postingGroupId = command.postingGroupId,
        ),
    )?.let { event ->
        InvoiceInventoryRevaluationRecord(
            itemId = event.itemId,
            quantityBefore = event.quantityBefore,
            oldUnitCostMinor = event.oldUnitCostMinor,
            newUnitCostMinor = event.newUnitCostMinor,
            revaluationDifferenceMinor = event.revaluationDifferenceMinor,
        )
    }

    override suspend fun deleteMovements(invoiceId: String) =
        inventory.deleteMovementsByInvoiceId(invoiceId)

    override suspend fun reverseMovements(invoiceId: String, sourceWriteId: String) =
        inventory.reverseInvoiceMovements(invoiceId, sourceWriteId)
}

class CashRegisterInvoiceCashAdapter @Inject constructor(
    private val manager: CashRegisterManager
) : InvoiceCashPort {
    override suspend fun onSaleCash(amount: Double, invoiceId: String, sourceWriteId: String) =
        manager.onSaleCash(amount, invoiceId, sourceWriteId)

    override suspend fun onPurchaseCash(amount: Double, invoiceId: String, sourceWriteId: String) =
        manager.onPurchaseCash(amount, invoiceId, sourceWriteId)

    override suspend fun onPaymentReceived(amount: Double, invoiceId: String, sourceWriteId: String) =
        manager.recordMovement(
            PersistenceCashMovementType.PAYMENT_RECEIVED,
            amount,
            invoiceId,
            sourceType = "INVOICE",
            sourceId = invoiceId,
            writeId = sourceWriteId,
        )

    override suspend fun recordMovement(
        type: InvoiceCashMovementType,
        amount: Double,
        referenceId: String,
        note: String,
        sourceWriteId: String,
    ) = manager.recordMovement(
        type.toLegacy(), amount, referenceId, note,
        sourceType = "INVOICE", sourceId = referenceId, writeId = sourceWriteId,
    )

    override suspend fun reverseMovement(
        type: InvoiceCashMovementType,
        amount: Double,
        referenceId: String,
        note: String,
        sourceWriteId: String,
    ) = manager.reverseMovement(
        type.toLegacy(), amount, referenceId, note,
        writeId = sourceWriteId, sourceType = "INVOICE", sourceId = referenceId,
    )
}

class PermissionInvoiceAuthorizationAdapter @Inject constructor(
    private val permissionProvider: PermissionProvider
) : InvoiceAuthorizationPort {
    override suspend fun canSave(isSale: Boolean, creatingNew: Boolean): Boolean = when {
        isSale && creatingNew -> permissionProvider.canNow { it.salesCreate }
        isSale -> permissionProvider.canNow { it.salesEdit }
        creatingNew -> permissionProvider.canNow { it.purchasesCreate }
        else -> permissionProvider.canNow { it.purchasesEdit }
    }

    override suspend fun canPost(category: InvoiceCategory): Boolean = when (category) {
        InvoiceCategory.SALE -> permissionProvider.canNow { it.salesCreate }
        InvoiceCategory.PURCHASE -> permissionProvider.canNow { it.purchasesCreate }
    }

    override suspend fun canEditDescription(category: InvoiceCategory): Boolean = when (category) {
        InvoiceCategory.SALE -> permissionProvider.canNow { it.salesEdit }
        InvoiceCategory.PURCHASE -> permissionProvider.canNow { it.purchasesEdit }
    }

    override suspend fun canOverrideStock(): Boolean =
        permissionProvider.canNow { it.inventoryEdit }

    override suspend fun canApproveExchangeRate(): Boolean =
        permissionProvider.canNow { it.inventoryPrice }

    override suspend fun canManuallyAllocateLandedCost(): Boolean =
        permissionProvider.canNow { it.shipmentsManage && it.inventoryPrice }

    override suspend fun canManageCommission(): Boolean =
        permissionProvider.canNow { it.commissionManage }

    override suspend fun canOverridePurchaseVariance(): Boolean =
        permissionProvider.canNow { it.purchasesEdit && it.inventoryPrice }

    override suspend fun canOverrideUnreceivedPurchasePayment(): Boolean =
        permissionProvider.canNow { it.purchasesEdit && it.suppliersAddPayment }

    override suspend fun canVoid(category: InvoiceCategory): Boolean = when (category) {
        InvoiceCategory.SALE -> permissionProvider.canNow { it.salesDelete }
        InvoiceCategory.PURCHASE -> permissionProvider.canNow { it.purchasesDelete }
    }
}

class PreferencesInvoiceSettingsAdapter @Inject constructor(
    private val preferencesManager: PreferencesManager
) : InvoiceSettingsPort {
    override suspend fun allowNegativeStock(): Boolean =
        preferencesManager.allowNegativeStock.first()

    override suspend fun functionalCurrencyCode(): String =
        preferencesManager.orgCurrency.first().trim().uppercase().ifBlank { LEGACY_DEFAULT_FUNCTIONAL_CURRENCY }

    private companion object {
        /**
         * Compatibility for pre-F246 organizations that historically stored local invoices without
         * an explicit currency. Verto's legacy local ledger was SDG-denominated; a blank migrated
         * setting must not block invoice posting. New/edited organization settings still override it.
         */
        const val LEGACY_DEFAULT_FUNCTIONAL_CURRENCY = "SDG"
    }
}

class AllocatorInvoiceNumberAdapter @Inject constructor(
    private val allocator: InvoiceNumberAllocator
) : InvoiceNumberPort {
    override suspend fun allocate(): Int? = allocator.allocate()
}

class WorkManagerInvoiceSyncSchedulerAdapter @Inject constructor(
    private val scheduleOptimalSync: OptimalSyncCoordinator,
) : InvoiceSyncSchedulerPort {
    override suspend fun requestSync(organizationId: String, userId: String) {
        val organization = organizationId.trim()
        val user = userId.trim()
        if (organization.isNotEmpty() && user.isNotEmpty()) {
            scheduleOptimalSync.immediate(
                OptimalSyncScope(organizationId = organization, userId = user),
            )
        } else {
            scheduleOptimalSync.immediateForCurrentSession()
        }
    }
}

private fun InvoiceEntity.toDomain() = InvoiceRecord(
    id = id,
    invoiceNumber = invoiceNumber,
    clientId = clientId,
    organizationId = organizationId,
    supplierInvoiceReference = supplierInvoiceReference,
    category = category.toDomain(),
    description = description,
    totalAmount = Money.ofMinor(totalAmountMinor).toLegacyDouble(),
    totalAmountMinor = totalAmountMinor,
    transactionCurrencyCode = transactionCurrencyCode,
    functionalCurrencyCode = functionalCurrencyCode,
    transactionAmountMinor = transactionAmountMinor,
    invoiceExchangeRateSnapshot = invoiceExchangeRateSnapshot,
    exchangeRateDirection = exchangeRateDirection,
    exchangeRateTimestamp = exchangeRateTimestamp,
    exchangeRateSource = exchangeRateSource,
    functionalAmountAtRecognitionMinor = functionalAmountAtRecognitionMinor,
    legacyCurrencyStatus = when (legacyCurrencyStatus) {
        PersistenceLegacyCurrencyStatus.KNOWN -> LegacyCurrencyStatus.KNOWN
        PersistenceLegacyCurrencyStatus.UNKNOWN -> LegacyCurrencyStatus.UNKNOWN
        PersistenceLegacyCurrencyStatus.REVIEW_REQUIRED -> LegacyCurrencyStatus.REVIEW_REQUIRED
    },
    createdAt = createdAt,
    dueDate = dueDate,
    notes = notes,
    isOwedToMe = isOwedToMe,
    status = status.toDomain(),
    discount = Money.ofMinor(discountMinor).toLegacyDouble(),
    discountMinor = discountMinor,
    commission = Money.ofMinor(commissionMinor).toLegacyDouble(),
    commissionMinor = commissionMinor,
    commissionBeneficiaryClientId = commissionBeneficiaryClientId,
    commissionSource = commissionSource,
    shipmentId = shipmentId,
    purchaseOrderId = purchaseOrderId,
    purchaseScope = when (purchaseScope) {
        PersistencePurchaseScope.LOCAL -> PurchaseScope.LOCAL
        PersistencePurchaseScope.INTERNATIONAL -> PurchaseScope.INTERNATIONAL
    },
    createdBy = createdBy,
    lifecycleStatus = when (lifecycleStatus) {
        PersistenceInvoiceLifecycleStatus.DRAFT -> InvoiceLifecycleStatus.DRAFT
        PersistenceInvoiceLifecycleStatus.POSTED -> InvoiceLifecycleStatus.POSTED
        PersistenceInvoiceLifecycleStatus.VOID -> InvoiceLifecycleStatus.VOID
    },
    lifecycleVersion = lifecycleVersion,
    postedAt = postedAt,
    voidedAt = voidedAt,
    voidReason = voidReason,
    voidWriteId = voidWriteId,
    voided = voided,
)

private fun InvoiceRecord.toEntity() = InvoiceEntity(
    id = id,
    invoiceNumber = invoiceNumber,
    clientId = clientId,
    organizationId = organizationId.trim(),
    supplierInvoiceReference = supplierInvoiceReference?.trim()?.takeIf { it.isNotEmpty() },
    supplierInvoiceReferenceNormalized = normalizeSupplierInvoiceReference(supplierInvoiceReference),
    type = InvoiceType.GOODS,
    category = category.toLegacy(),
    description = description,
    totalAmount = Money.ofMinor(totalAmountMinor).toLegacyDouble(),
    totalAmountMinor = totalAmountMinor,
    transactionCurrencyCode = transactionCurrencyCode,
    functionalCurrencyCode = functionalCurrencyCode,
    transactionAmountMinor = transactionAmountMinor,
    invoiceExchangeRateSnapshot = invoiceExchangeRateSnapshot,
    exchangeRateDirection = exchangeRateDirection,
    exchangeRateTimestamp = exchangeRateTimestamp,
    exchangeRateSource = exchangeRateSource,
    functionalAmountAtRecognitionMinor = functionalAmountAtRecognitionMinor,
    legacyCurrencyStatus = when (legacyCurrencyStatus) {
        LegacyCurrencyStatus.KNOWN -> PersistenceLegacyCurrencyStatus.KNOWN
        LegacyCurrencyStatus.UNKNOWN -> PersistenceLegacyCurrencyStatus.UNKNOWN
        LegacyCurrencyStatus.REVIEW_REQUIRED -> PersistenceLegacyCurrencyStatus.REVIEW_REQUIRED
    },
    createdAt = createdAt,
    dueDate = dueDate,
    notes = notes,
    isOwedToMe = isOwedToMe,
    status = status.toLegacy(),
    discount = Money.ofMinor(discountMinor).toLegacyDouble(),
    discountMinor = discountMinor,
    commission = Money.ofMinor(commissionMinor).toLegacyDouble(),
    commissionMinor = commissionMinor,
    commissionBeneficiaryClientId = commissionBeneficiaryClientId,
    commissionSource = commissionSource,
    shipmentId = shipmentId,
    purchaseOrderId = purchaseOrderId,
    purchaseScope = when (purchaseScope) {
        PurchaseScope.LOCAL -> PersistencePurchaseScope.LOCAL
        PurchaseScope.INTERNATIONAL -> PersistencePurchaseScope.INTERNATIONAL
    },
    createdBy = createdBy,
    lifecycleStatus = when (lifecycleStatus) {
        InvoiceLifecycleStatus.DRAFT -> PersistenceInvoiceLifecycleStatus.DRAFT
        InvoiceLifecycleStatus.POSTED -> PersistenceInvoiceLifecycleStatus.POSTED
        InvoiceLifecycleStatus.VOID -> PersistenceInvoiceLifecycleStatus.VOID
    },
    lifecycleVersion = lifecycleVersion,
    postedAt = postedAt,
    voidedAt = voidedAt,
    voidReason = voidReason,
    voidWriteId = voidWriteId,
    voided = lifecycleStatus == InvoiceLifecycleStatus.VOID,
)

private fun InvoiceItemEntity.toDomain() = InvoiceLine(
    id = id,
    invoiceId = invoiceId,
    itemName = itemName,
    itemCategory = itemCategory,
    itemSkuSnapshot = itemSkuSnapshot,
    unitSnapshot = unitSnapshot,
    quantity = quantity,
    buyPrice = Money.ofMinor(buyPriceMinor).toLegacyDouble(),
    buyPriceMinor = buyPriceMinor,
    sellPrice = Money.ofMinor(sellPriceMinor).toLegacyDouble(),
    sellPriceMinor = sellPriceMinor,
    totalPrice = Money.ofMinor(totalPriceMinor).toLegacyDouble(),
    totalPriceMinor = totalPriceMinor,
    unitSellPrice = Money.ofMinor(unitSellPriceMinor).toLegacyDouble(),
    unitSellPriceMinor = unitSellPriceMinor,
    unitCostAtSale = Money.ofMinor(unitCostAtSaleMinor).toLegacyDouble(),
    unitCostAtSaleMinor = unitCostAtSaleMinor,
    lineRevenueSnapshot = Money.ofMinor(lineRevenueSnapshotMinor).toLegacyDouble(),
    lineRevenueSnapshotMinor = lineRevenueSnapshotMinor,
    lineCostSnapshot = Money.ofMinor(lineCostSnapshotMinor).toLegacyDouble(),
    lineCostSnapshotMinor = lineCostSnapshotMinor,
    grossProfitSnapshot = Money.ofMinor(grossProfitSnapshotMinor).toLegacyDouble(),
    grossProfitSnapshotMinor = grossProfitSnapshotMinor,
    costSnapshotStatus = costSnapshotStatus,
    isOwedToMe = isOwedToMe,
    inventoryItemId = inventoryItemId
)

private fun InvoiceLine.toEntity() = InvoiceItemEntity(
    id = id,
    invoiceId = invoiceId,
    itemType = ItemType.GOODS,
    itemName = itemName,
    itemCategory = itemCategory,
    itemSkuSnapshot = itemSkuSnapshot,
    unitSnapshot = unitSnapshot,
    quantity = quantity,
    buyPrice = Money.ofMinor(buyPriceMinor).toLegacyDouble(),
    buyPriceMinor = buyPriceMinor,
    sellPrice = Money.ofMinor(sellPriceMinor).toLegacyDouble(),
    sellPriceMinor = sellPriceMinor,
    totalPrice = Money.ofMinor(totalPriceMinor).toLegacyDouble(),
    totalPriceMinor = totalPriceMinor,
    unitSellPrice = Money.ofMinor(unitSellPriceMinor).toLegacyDouble(),
    unitSellPriceMinor = unitSellPriceMinor,
    unitCostAtSale = Money.ofMinor(unitCostAtSaleMinor).toLegacyDouble(),
    unitCostAtSaleMinor = unitCostAtSaleMinor,
    lineRevenueSnapshot = Money.ofMinor(lineRevenueSnapshotMinor).toLegacyDouble(),
    lineRevenueSnapshotMinor = lineRevenueSnapshotMinor,
    lineCostSnapshot = Money.ofMinor(lineCostSnapshotMinor).toLegacyDouble(),
    lineCostSnapshotMinor = lineCostSnapshotMinor,
    grossProfitSnapshot = Money.ofMinor(grossProfitSnapshotMinor).toLegacyDouble(),
    grossProfitSnapshotMinor = grossProfitSnapshotMinor,
    costSnapshotStatus = costSnapshotStatus,
    isOwedToMe = isOwedToMe,
    inventoryItemId = inventoryItemId
)

private fun PaymentEntity.toDomain() = InvoicePayment(
    id = id,
    invoiceId = invoiceId,
    clientId = clientId,
    amount = Money.ofMinor(amountMinor).toLegacyDouble(),
    amountMinor = amountMinor,
    paymentCurrencyCode = paymentCurrencyCode,
    supplierAmountMinor = supplierAmountMinor,
    paymentExchangeRate = paymentExchangeRate,
    paymentExchangeRateDirection = paymentExchangeRateDirection,
    paymentExchangeRateTimestamp = paymentExchangeRateTimestamp,
    paymentExchangeRateSource = paymentExchangeRateSource,
    functionalCashAmountMinor = functionalCashAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    legacyCurrencyStatus = when (legacyCurrencyStatus) {
        PersistenceLegacyCurrencyStatus.KNOWN -> LegacyCurrencyStatus.KNOWN
        PersistenceLegacyCurrencyStatus.UNKNOWN -> LegacyCurrencyStatus.UNKNOWN
        PersistenceLegacyCurrencyStatus.REVIEW_REQUIRED -> LegacyCurrencyStatus.REVIEW_REQUIRED
    },
    paymentMethod = InvoicePaymentMethod.CASH,
    note = note,
    paidAt = paidAt,
    employeeId = employeeId,
    employeeName = employeeName,
    sourceType = sourceType,
    sourceId = sourceId,
    sourceVersion = sourceVersion,
    writeId = writeId,
    reversedPaymentId = reversedPaymentId,
)

private fun InvoicePayment.toEntity() = PaymentEntity(
    id = id,
    invoiceId = invoiceId,
    clientId = clientId,
    amount = Money.ofMinor(amountMinor).toLegacyDouble(),
    amountMinor = amountMinor,
    paymentCurrencyCode = paymentCurrencyCode,
    supplierAmountMinor = supplierAmountMinor,
    paymentExchangeRate = paymentExchangeRate,
    paymentExchangeRateDirection = paymentExchangeRateDirection,
    paymentExchangeRateTimestamp = paymentExchangeRateTimestamp,
    paymentExchangeRateSource = paymentExchangeRateSource,
    functionalCashAmountMinor = functionalCashAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    legacyCurrencyStatus = when (legacyCurrencyStatus) {
        LegacyCurrencyStatus.KNOWN -> PersistenceLegacyCurrencyStatus.KNOWN
        LegacyCurrencyStatus.UNKNOWN -> PersistenceLegacyCurrencyStatus.UNKNOWN
        LegacyCurrencyStatus.REVIEW_REQUIRED -> PersistenceLegacyCurrencyStatus.REVIEW_REQUIRED
    },
    paymentMethod = PaymentMethod.CASH,
    note = note,
    paidAt = paidAt,
    employeeId = employeeId,
    employeeName = employeeName,
    sourceType = sourceType,
    sourceId = sourceId,
    sourceVersion = sourceVersion,
    writeId = writeId,
    reversedPaymentId = reversedPaymentId,
)

private fun PaymentAllocationEntity.toDomain() = InvoicePaymentAllocation(
    id = id,
    paymentId = paymentId,
    invoiceId = invoiceId,
    allocatedTransactionAmountMinor = allocatedTransactionAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    createdAt = createdAt,
    sourceType = sourceType,
    sourceId = sourceId,
    sourceVersion = sourceVersion,
    writeId = writeId,
)

private fun RealizedFxEventEntity.toDomain() = RealizedFxEvent(
    id = id,
    paymentId = paymentId,
    invoiceId = invoiceId,
    functionalCurrencyCode = functionalCurrencyCode,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    functionalCashAmountMinor = functionalCashAmountMinor,
    differenceMinor = differenceMinor,
    result = result,
    occurredAt = occurredAt,
    writeId = writeId,
)

private fun InvoicePaymentAllocation.toEntity() = PaymentAllocationEntity(
    id = id, paymentId = paymentId, invoiceId = invoiceId,
    allocatedTransactionAmountMinor = allocatedTransactionAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor, createdAt = createdAt,
    sourceType = sourceType, sourceId = sourceId, sourceVersion = sourceVersion, writeId = writeId,
)

private fun RealizedFxEvent.toEntity() = RealizedFxEventEntity(
    id = id, paymentId = paymentId, invoiceId = invoiceId,
    functionalCurrencyCode = functionalCurrencyCode,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    functionalCashAmountMinor = functionalCashAmountMinor, differenceMinor = differenceMinor,
    result = result, occurredAt = occurredAt, sourceId = paymentId, writeId = writeId,
)

private fun InventoryItem.toInvoiceDomain() = InvoiceStockItem(
    id = id,
    partNumber = partNumber,
    name = name,
    unitId = unitId,
    linkedUnitItemId = linkedUnitItemId,
    isUnitItem = isUnitItem,
    quantityPerUnit = quantityPerUnit,
    isService = isService,
    buyPrice = buyPrice,
    sellPrice = sellPrice,
    quantity = quantity,
    minQuantity = minQuantity,
    location = location,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDirty = isDirty
)

private fun InvoiceStockItem.toInventoryDomain() = InventoryItem(
    id = id,
    partNumber = partNumber,
    name = name,
    unitId = unitId,
    linkedUnitItemId = linkedUnitItemId,
    isUnitItem = isUnitItem,
    quantityPerUnit = quantityPerUnit,
    isService = isService,
    buyPrice = buyPrice,
    sellPrice = sellPrice,
    quantity = quantity,
    minQuantity = minQuantity,
    location = location,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDirty = isDirty
)

private fun PersistenceInvoiceCategory.toDomain() = when (this) {
    PersistenceInvoiceCategory.SALE -> InvoiceCategory.SALE
    PersistenceInvoiceCategory.PURCHASE -> InvoiceCategory.PURCHASE
}

private fun InvoiceCategory.toLegacy() = when (this) {
    InvoiceCategory.SALE -> PersistenceInvoiceCategory.SALE
    InvoiceCategory.PURCHASE -> PersistenceInvoiceCategory.PURCHASE
}

private fun PersistenceInvoiceStatus.toDomain() = when (this) {
    PersistenceInvoiceStatus.CLOSED_CASH -> InvoiceStatus.CLOSED_CASH
    PersistenceInvoiceStatus.CLOSED_CREDIT -> InvoiceStatus.CLOSED_CREDIT
}

private fun InvoiceStatus.toLegacy() = when (this) {
    InvoiceStatus.CLOSED_CASH -> PersistenceInvoiceStatus.CLOSED_CASH
    InvoiceStatus.CLOSED_CREDIT -> PersistenceInvoiceStatus.CLOSED_CREDIT
}

private fun InvoiceCashMovementType.toLegacy() = when (this) {
    InvoiceCashMovementType.SALE_CASH -> PersistenceCashMovementType.SALE_CASH
    InvoiceCashMovementType.PURCHASE_CASH -> PersistenceCashMovementType.PURCHASE_CASH
    InvoiceCashMovementType.PAYMENT_RECEIVED -> PersistenceCashMovementType.PAYMENT_RECEIVED
    InvoiceCashMovementType.PAYMENT_MADE -> PersistenceCashMovementType.PAYMENT_MADE
}
