package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.local.entity.LogisticsCustodyHandoffEntity
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.LogisticsMilestoneEntity
import com.verto.app.data.local.entity.LogisticsShipmentLegEntity
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.PurchaseScope
import com.verto.app.data.local.entity.FUNCTIONAL_PER_TRANSACTION
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.application.LogisticsInventoryIdentityResolution
import com.verto.app.feature.shipment.application.LogisticsInventoryIdentityResolver
import com.verto.app.feature.shipment.domain.model.LogisticsInventoryCatalogItem
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason
import com.verto.app.feature.shipment.domain.model.LogisticsPlannedAttachment
import com.verto.app.feature.shipment.domain.model.LogisticsPlannedCost
import com.verto.app.feature.shipment.domain.model.LogisticsPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsPurchaseInvoiceLineSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsPurchaseInvoiceSnapshot
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsPurchaseInvoiceQueryPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentNumberPort
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.data.remote.LogisticsShipmentNumberAllocator
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class LogisticsShipmentNumberAdapter @Inject constructor(
    private val remote: LogisticsShipmentNumberAllocator,
    private val database: AppDatabase,
) : LogisticsShipmentNumberPort {
    override suspend fun allocate(organizationId: String): Int? {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        val remoteCandidate = remote.allocate(organizationId)?.takeIf { it > 0 }
        return database.logisticsShipmentNumberDao().allocateNext(organizationId, remoteCandidate)
    }
}

class RepositoryLogisticsPurchaseInvoiceQueryAdapter @Inject constructor(
    private val invoices: InvoiceRepository,
    private val parties: PartyDirectoryGateway,
    private val database: AppDatabase,
    private val outbox: UnifiedOutboxWriter,
) : LogisticsPurchaseInvoiceQueryPort {
    override suspend fun getPurchaseInvoice(
        organizationId: String,
        invoiceId: String,
        excludeShipmentId: String?,
    ): LogisticsPurchaseInvoiceSnapshot? {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        val invoice = invoices.getInvoiceByIdSync(invoiceId) ?: return null
        if (invoice.category != InvoiceCategory.PURCHASE || invoice.voided || invoice.purchaseScope != PurchaseScope.INTERNATIONAL) return null
        val supplier = parties.getClientByIdSync(invoice.clientId) ?: return null
        if (database.partyRoleDao().getSupplierProfileSync(organizationId, supplier.id)?.scope != "INTERNATIONAL") return null
        val conflictingShipmentCount = database.logisticsDao().countConflictingActiveShipmentsForInvoice(
            organizationId = organizationId,
            invoiceId = invoice.id,
            excludeShipmentId = excludeShipmentId,
        )
        if (conflictingShipmentCount > 0) return null
        val transactionCurrency = LogisticsCurrencyPolicy.normalizeIso4217(invoice.transactionCurrencyCode)
        val functionalCurrency = LogisticsCurrencyPolicy.normalizeIso4217(invoice.functionalCurrencyCode)
        require(functionalCurrency == LogisticsCurrencyPolicy.BASE_CURRENCY) {
            "International logistics requires functional currency ${LogisticsCurrencyPolicy.BASE_CURRENCY}"
        }
        require(invoice.exchangeRateDirection == FUNCTIONAL_PER_TRANSACTION) {
            "Unsupported invoice exchange-rate direction: ${invoice.exchangeRateDirection}"
        }
        val recognitionRate = if (transactionCurrency == functionalCurrency) {
            BigDecimal.ONE
        } else {
            invoice.invoiceExchangeRateSnapshot.toBigDecimalOrNull()
                ?.takeIf { it.signum() > 0 }
                ?: error("International purchase invoice exchange rate is missing")
        }
        LogisticsCurrencyPolicy.requireRate(
            transactionCurrency,
            recognitionRate,
            invoice.exchangeRateTimestamp.takeIf { it > 0L } ?: invoice.createdAt,
        )

        val lines = invoices.getInvoiceItemsSync(invoice.id)
        return LogisticsPurchaseInvoiceSnapshot(
            invoiceId = invoice.id,
            supplierId = supplier.id,
            supplierName = supplier.name,
            invoiceNumber = invoice.invoiceNumber.toString(),
            currency = transactionCurrency,
            exchangeRate = recognitionRate,
            invoiceDate = invoice.createdAt,
            totalAmount = BigDecimal.valueOf(invoice.totalAmount),
            lines = lines.map { line ->
                val allocated = database.logisticsDao().getAllocatedQuantityForInvoiceLine(
                    organizationId = organizationId,
                    invoiceId = invoice.id,
                    invoiceItemId = line.id,
                    excludeShipmentId = excludeShipmentId,
                )
                val resolvedInventoryItemId = resolveInventoryItemId(organizationId, invoice.id, line)
                LogisticsPurchaseInvoiceLineSnapshot(
                    invoiceItemId = line.id,
                    inventoryItemId = resolvedInventoryItemId,
                    itemName = line.itemName,
                    quantity = line.quantity,
                    // Logistics basePurchaseUnitPrice is always in functional/base currency (SDG).
                    // Converting here prevents mixing foreign invoice prices with SDG landed costs.
                    unitPrice = LogisticsCurrencyPolicy.toBaseCurrencyAmount(
                        amount = BigDecimal.valueOf(line.buyPrice),
                        currency = transactionCurrency,
                        exchangeRate = recognitionRate,
                        exchangeRateDate = invoice.exchangeRateTimestamp.takeIf { it > 0L } ?: invoice.createdAt,
                    ),
                    remainingShippableQuantity = (line.quantity - allocated).coerceAtLeast(0),
                )
            },
        )
    }

    /**
     * v222 fail-closed identity repair. Existing inventoryItemId wins only when it points to a real
     * catalog row. Name fallback is accepted only when normalization produces exactly one candidate.
     * Ambiguous/missing identities stay blank until the user explicitly selects or creates an item.
     */
    private suspend fun resolveInventoryItemId(organizationId: String, invoiceId: String, line: InvoiceItemEntity): String = database.withTransaction {
        val inventoryDao = database.inventoryDao()
        val invoiceDao = database.invoiceDao()
        val catalog = inventoryDao.getAllItemsSync().map { item ->
            LogisticsInventoryCatalogItem(item.id, item.name, item.partNumber)
        }
        when (val resolution = LogisticsInventoryIdentityResolver.resolve(
            explicitInventoryItemId = line.inventoryItemId,
            itemName = line.itemName,
            catalog = catalog,
        )) {
            is LogisticsInventoryIdentityResolution.Resolved -> {
                if (line.inventoryItemId != resolution.inventoryItemId) {
                    invoiceDao.updateInvoiceItemInventoryLink(line.id, resolution.inventoryItemId)
                    captureInvoiceLink(organizationId, invoiceId, line.id, resolution.inventoryItemId)
                }
                resolution.inventoryItemId
            }
            is LogisticsInventoryIdentityResolution.Ambiguous,
            is LogisticsInventoryIdentityResolution.Missing -> {
                if (line.inventoryItemId.isNotBlank()) {
                    invoiceDao.updateInvoiceItemInventoryLink(line.id, "")
                    captureInvoiceLink(organizationId, invoiceId, line.id, "")
                }
                ""
            }
        }
    }

    private suspend fun captureInvoiceLink(organizationId: String, invoiceId: String, invoiceItemId: String, inventoryItemId: String) {
        require(organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        val mutationId = UUID.nameUUIDFromBytes(
            "v307|INVOICE_LINK|$organizationId|$invoiceId|$invoiceItemId|$inventoryItemId".toByteArray(Charsets.UTF_8)
        ).toString()
        outbox.enqueue(
            organizationId = organizationId,
            aggregateType = "INVOICE",
            aggregateId = invoiceId,
            operationType = "UPSERT",
            mutationId = mutationId,
            payload = mapOf(
                "command" to "LINK_INVENTORY_ITEM",
                "invoiceItemId" to invoiceItemId,
                "inventoryItemId" to inventoryItemId,
            ),
        )
    }
}

class RoomLogisticsInventoryIdentityAdapter @Inject constructor(
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) : LogisticsInventoryIdentityPort {
    override suspend fun listCatalog(): List<LogisticsInventoryCatalogItem> =
        database.inventoryDao().getAllItemsSync().map { item ->
            LogisticsInventoryCatalogItem(item.id, item.name, item.partNumber)
        }

    override suspend fun bindInvoiceLine(invoiceItemId: String, inventoryItemId: String): String {
        val organizationId = trustedOrganizationId()
        return database.withTransaction {
            require(invoiceItemId.isNotBlank()) { "invoiceItemId is required" }
            require(inventoryItemId.isNotBlank()) { "inventoryItemId is required" }
            val inventory = database.inventoryDao().getItemByIdSync(inventoryItemId)
                ?: error("Inventory item not found")
            val invoiceLine = database.invoiceDao().getAllInvoiceItemsSync().singleOrNull { it.id == invoiceItemId }
                ?: error("Purchase invoice line not found")
            if (invoiceLine.inventoryItemId != inventory.id) {
                database.invoiceDao().updateInvoiceItemInventoryLink(invoiceLine.id, inventory.id)
                enqueueInvoiceLink(organizationId, invoiceLine.invoiceId, invoiceLine.id, inventory.id)
            }
            inventory.id
        }
    }

    override suspend fun createZeroStockAndBind(invoiceItemId: String): String {
        val organizationId = trustedOrganizationId()
        return database.withTransaction {
            require(invoiceItemId.isNotBlank()) { "invoiceItemId is required" }
            val invoiceLine = database.invoiceDao().getAllInvoiceItemsSync().singleOrNull { it.id == invoiceItemId }
                ?: error("Purchase invoice line not found")
            val itemName = invoiceLine.itemName.trim().takeIf { it.isNotBlank() }
                ?: error("Purchase invoice line name is required")
            val itemId = UUID.nameUUIDFromBytes(
                "v307|LOGISTICS_ZERO_STOCK|$organizationId|$invoiceItemId".toByteArray(Charsets.UTF_8)
            ).toString()
            val item = InventoryItemEntity(
                id = itemId,
                name = itemName,
                buyPrice = invoiceLine.buyPrice,
                sellPrice = invoiceLine.sellPrice,
                quantity = 0,
                isDirty = true,
            )
            database.inventoryDao().insertItem(item)
            database.invoiceDao().updateInvoiceItemInventoryLink(invoiceLine.id, item.id)
            val batchId = "logistics-inventory-link:$organizationId:$invoiceItemId"
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "INVENTORY_ITEM",
                aggregateId = item.id,
                operationType = "UPSERT",
                mutationId = UUID.nameUUIDFromBytes("$batchId|item".toByteArray(Charsets.UTF_8)).toString(),
                commandBatchId = batchId,
                commandOrder = 0,
                payload = mapOf("name" to item.name, "buyPrice" to item.buyPrice, "sellPrice" to item.sellPrice, "quantity" to 0),
            )
            enqueueInvoiceLink(organizationId, invoiceLine.invoiceId, invoiceLine.id, item.id, batchId, 1)
            item.id
        }
    }

    private suspend fun enqueueInvoiceLink(
        organizationId: String,
        invoiceId: String,
        invoiceItemId: String,
        inventoryItemId: String,
        batchId: String? = null,
        order: Int? = null,
    ) {
        outbox.enqueue(
            organizationId = organizationId,
            aggregateType = "INVOICE",
            aggregateId = invoiceId,
            operationType = "UPSERT",
            mutationId = UUID.nameUUIDFromBytes(
                "v307|INVOICE_LINK|$organizationId|$invoiceId|$invoiceItemId|$inventoryItemId".toByteArray(Charsets.UTF_8)
            ).toString(),
            commandBatchId = batchId,
            commandOrder = order,
            payload = mapOf("command" to "LINK_INVENTORY_ITEM", "invoiceItemId" to invoiceItemId, "inventoryItemId" to inventoryItemId),
        )
    }

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }
}

class UuidLogisticsIdentityAdapter @Inject constructor() : LogisticsIdentityPort {
    override fun newId(): String = UUID.randomUUID().toString()
}

class SystemLogisticsClockAdapter @Inject constructor() : LogisticsClockPort {
    override fun now(): Long = System.currentTimeMillis()
}

internal suspend fun AppDatabase.readLatestConfirmedCargoSnapshot(
    organizationId: String,
    shipmentId: String,
): LogisticsCargoSnapshot? {
    require(organizationId.isNotBlank()) { "organizationId is required" }
    require(shipmentId.isNotBlank()) { "shipmentId is required" }
    val dao = logisticsDao()
    val handoff = dao.getLatestConfirmedCargoHandoff(organizationId, shipmentId)
    handoff?.receivedPackageCount?.let { packageCount ->
        return LogisticsCargoSnapshot(
            packageCount = packageCount,
            weightKg = handoff.receivedWeightKg?.let(::BigDecimal),
        )
    }
    val initial = dao.getTransportDetails(organizationId, shipmentId) ?: return null
    return initial.packageCount?.let { packageCount ->
        LogisticsCargoSnapshot(
            packageCount = packageCount,
            weightKg = initial.weightKg?.let(::BigDecimal),
        )
    }
}

internal fun LogisticsMilestoneEntity.toDomainV2(): LogisticsMilestone = LogisticsMilestone(
    id = id,
    shipmentId = shipmentId,
    type = LogisticsMilestoneType.valueOf(type),
    order = milestoneOrder,
    location = location,
    plannedArrivalAt = plannedArrivalAt,
    plannedDepartureAt = plannedDepartureAt,
    handlingStatus = LogisticsMilestoneHandlingStatus.valueOf(handlingStatus),
    arrivedAt = arrivedAt,
    unloadedAt = unloadedAt,
    loadedAt = loadedAt,
    departedAt = departedAt,
    note = note,
    countryCode = countryCode,
    countryNameSnapshot = countryNameSnapshot,
    city = city,
    placeName = placeName,
    planKind = LogisticsPlanKind.valueOf(planKind),
    expectedStayDays = expectedStayDays,
    customsBrokerPartnerId = customsBrokerPartnerId,
    customsBrokerNameSnapshot = customsBrokerNameSnapshot,
    customsBrokerPhoneSnapshot = customsBrokerPhoneSnapshot,
    customsStartedAt = customsStartedAt,
    customsCompletedAt = customsCompletedAt,
)

internal fun LogisticsShipmentLegEntity.toDomainV2() = LogisticsShipmentLeg(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    sequence = sequence,
    fromMilestoneId = fromMilestoneId,
    toMilestoneId = toMilestoneId,
    mode = LogisticsLegTransportMode.valueOf(mode),
    carrierPartnerId = carrierPartnerId.orEmpty(),
    status = LogisticsLegStatus.valueOf(status),
    plannedDepartureAt = plannedDepartureAt,
    plannedArrivalAt = plannedArrivalAt,
    actualDepartureAt = actualDepartureAt,
    actualArrivalAt = actualArrivalAt,
    roadVehicleNumber = roadVehicleNumber,
    roadDriverName = roadDriverName,
    roadDriverPhone = roadDriverPhone,
    seaContainerNumber = seaContainerNumber,
    seaBillOfLading = seaBillOfLading,
    seaVesselReference = seaVesselReference,
    airWaybillNumber = airWaybillNumber,
    airFlightReference = airFlightReference,
    note = note,
    planKind = LogisticsPlanKind.valueOf(planKind),
    expectedTransitDays = expectedTransitDays,
    representativeNameSnapshot = representativeNameSnapshot,
    representativePhoneSnapshot = representativePhoneSnapshot,
    packageCount = packageCount,
    weightKg = weightKg?.let(::BigDecimal),
    supersededAt = supersededAt,
    supersededByLegId = supersededByLegId,
    expectedTransitMinutes = expectedTransitMinutes ?: expectedTransitDays?.times(24 * 60),
    plannedCarrierPartnerId = plannedCarrierPartnerId,
    plannedCarrierNameSnapshot = plannedCarrierNameSnapshot,
    plannedRepresentativeNameSnapshot = plannedRepresentativeNameSnapshot,
    plannedRepresentativePhoneSnapshot = plannedRepresentativePhoneSnapshot,
    plannedPackageCount = plannedPackageCount,
    plannedWeightKg = plannedWeightKg?.let(::BigDecimal),
    plannedCost = plannedCostAmount?.let { amount ->
        val currency = plannedCostCurrency ?: return@let null
        val rate = plannedExchangeRate ?: return@let null
        val base = plannedBaseCostAmount ?: return@let null
        LogisticsPlannedCost(BigDecimal(amount), currency, BigDecimal(rate), BigDecimal(base))
    },
    plannedProof = plannedProofPrivateUri?.let { uri ->
        val displayName = plannedProofDisplayName ?: return@let null
        val mimeType = plannedProofMimeType ?: return@let null
        LogisticsPlannedAttachment(uri, displayName, mimeType)
    },
)

internal fun LogisticsShipmentLeg.toEntityV2() = LogisticsShipmentLegEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    sequence = sequence,
    fromMilestoneId = fromMilestoneId,
    toMilestoneId = toMilestoneId,
    mode = mode.name,
    carrierPartnerId = carrierPartnerId.trim().takeIf { it.isNotBlank() },
    status = status.name,
    plannedDepartureAt = plannedDepartureAt,
    plannedArrivalAt = plannedArrivalAt,
    actualDepartureAt = actualDepartureAt,
    actualArrivalAt = actualArrivalAt,
    roadVehicleNumber = roadVehicleNumber,
    roadDriverName = roadDriverName,
    roadDriverPhone = roadDriverPhone,
    seaContainerNumber = seaContainerNumber,
    seaBillOfLading = seaBillOfLading,
    seaVesselReference = seaVesselReference,
    airWaybillNumber = airWaybillNumber,
    airFlightReference = airFlightReference,
    note = note,
    planKind = planKind.name,
    expectedTransitDays = expectedTransitDays,
    representativeNameSnapshot = representativeNameSnapshot,
    representativePhoneSnapshot = representativePhoneSnapshot,
    packageCount = packageCount,
    weightKg = weightKg?.toPlainString(),
    supersededAt = supersededAt,
    supersededByLegId = supersededByLegId,
    expectedTransitMinutes = expectedTransitMinutes,
    plannedCarrierPartnerId = plannedCarrierPartnerId,
    plannedCarrierNameSnapshot = plannedCarrierNameSnapshot,
    plannedRepresentativeNameSnapshot = plannedRepresentativeNameSnapshot,
    plannedRepresentativePhoneSnapshot = plannedRepresentativePhoneSnapshot,
    plannedPackageCount = plannedPackageCount,
    plannedWeightKg = plannedWeightKg?.toPlainString(),
    plannedCostAmount = plannedCost?.amount?.toPlainString(),
    plannedCostCurrency = plannedCost?.currency,
    plannedExchangeRate = plannedCost?.exchangeRate?.toPlainString(),
    plannedBaseCostAmount = plannedCost?.baseCurrencyAmount?.toPlainString(),
    plannedProofPrivateUri = plannedProof?.privateUri,
    plannedProofDisplayName = plannedProof?.displayName,
    plannedProofMimeType = plannedProof?.mimeType,
)

internal fun LogisticsCustodyHandoffEntity.toDomainV2() = LogisticsCustodyHandoff(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    sourceId = sourceId,
    milestoneId = milestoneId,
    fromHolderType = LogisticsCustodyHolderType.valueOf(fromHolderType),
    fromHolderId = fromHolderId,
    fromHolderNameSnapshot = fromHolderNameSnapshot,
    toHolderType = LogisticsCustodyHolderType.valueOf(toHolderType),
    toHolderId = toHolderId,
    toHolderNameSnapshot = toHolderNameSnapshot,
    transferredAt = transferredAt,
    receivedAt = receivedAt,
    requestId = requestId,
    note = note,
    handoverPackageCount = handoverPackageCount,
    receivedPackageCount = receivedPackageCount,
    handoverWeightKg = handoverWeightKg?.let(::BigDecimal),
    receivedWeightKg = receivedWeightKg?.let(::BigDecimal),
    packageChangeReason = packageChangeReason?.let(LogisticsPackageChangeReason::valueOf),
    packageChangeNote = packageChangeNote,
    openedPackageCount = openedPackageCount,
    damagedPackageCount = damagedPackageCount,
)

internal fun LogisticsCustodyHandoff.toEntityV2() = LogisticsCustodyHandoffEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    sourceId = sourceId,
    milestoneId = milestoneId,
    fromHolderType = fromHolderType.name,
    fromHolderId = fromHolderId,
    fromHolderNameSnapshot = fromHolderNameSnapshot,
    toHolderType = toHolderType.name,
    toHolderId = toHolderId,
    toHolderNameSnapshot = toHolderNameSnapshot,
    transferredAt = transferredAt,
    receivedAt = receivedAt,
    requestId = requestId,
    note = note,
    handoverPackageCount = handoverPackageCount,
    receivedPackageCount = receivedPackageCount,
    handoverWeightKg = handoverWeightKg?.toPlainString(),
    receivedWeightKg = receivedWeightKg?.toPlainString(),
    packageChangeReason = packageChangeReason?.name,
    packageChangeNote = packageChangeNote,
    openedPackageCount = openedPackageCount,
    damagedPackageCount = damagedPackageCount,
)

internal fun LogisticsMilestone.toEntityV2(organizationId: String): LogisticsMilestoneEntity = LogisticsMilestoneEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    type = type.name,
    milestoneOrder = order,
    location = location,
    plannedArrivalAt = plannedArrivalAt,
    plannedDepartureAt = plannedDepartureAt,
    handlingStatus = handlingStatus.name,
    arrivedAt = arrivedAt,
    unloadedAt = unloadedAt,
    loadedAt = loadedAt,
    departedAt = departedAt,
    note = note,
    countryCode = countryCode,
    countryNameSnapshot = countryNameSnapshot,
    city = city,
    placeName = placeName,
    planKind = planKind.name,
    expectedStayDays = expectedStayDays,
    customsBrokerPartnerId = customsBrokerPartnerId,
    customsBrokerNameSnapshot = customsBrokerNameSnapshot,
    customsBrokerPhoneSnapshot = customsBrokerPhoneSnapshot,
    customsStartedAt = customsStartedAt,
    customsCompletedAt = customsCompletedAt,
)
