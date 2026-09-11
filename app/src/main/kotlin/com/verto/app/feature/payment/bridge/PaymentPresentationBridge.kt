package com.verto.app.feature.payment.bridge

import com.verto.app.feature.party.domain.model.customerSegmentFromStorage

import com.verto.app.data.local.dao.InvoiceDraftDao
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.InvoiceCategory as PersistenceInvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftLineEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceImageEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftComposerColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftFinancialColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftModeColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftPersistenceColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftLineValues
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceOwnerColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceVehicleColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftRouteColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftVehicleSuggestionColumns
import com.verto.app.data.local.entity.InvoiceEditorDraftVehicleIdentityColumns
import com.verto.app.data.local.entity.InvoiceStatus as PersistenceInvoiceStatus
import com.verto.app.data.local.entity.InvoiceType as PersistenceInvoiceType
import com.verto.app.data.model.canEditInvoiceCategory
import com.verto.app.data.model.editInvoiceDeniedMessage
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.repository.InventoryRepository
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.feature.invoice.application.command.SaveInvoiceUseCase
import com.verto.app.feature.invoice.domain.model.CompanyMaintenanceData as InvoiceCompanyMaintenanceData
import com.verto.app.feature.invoice.domain.model.CompanyMaintenanceImageData as InvoiceCompanyMaintenanceImageData
import com.verto.app.feature.invoice.domain.model.InvoiceItemData as InvoiceDomainItemData
import com.verto.app.feature.invoice.domain.model.InvoiceVehicleSuggestionsQuery as InvoiceDomainVehicleQuery
import com.verto.app.feature.invoice.domain.model.PaymentMode as InvoicePaymentMode
import com.verto.app.feature.invoice.domain.model.PurchaseScope as InvoicePurchaseScope
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceCoordinator
import com.verto.app.feature.payment.application.model.*
import com.verto.app.feature.payment.application.port.PaymentDebtWorkflowPort
import com.verto.app.feature.payment.application.port.PaymentInvoiceSummaryPort
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.repository.PartyRoleProjection
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class PaymentDebtWorkflowBridge @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val inventoryRepository: InventoryRepository,
    private val partyBridge: PaymentPartyBridge,
    private val invoiceDraftDao: InvoiceDraftDao,
    private val saveInvoiceUseCase: SaveInvoiceUseCase,
    private val invoiceMaintenanceCoordinator: InvoiceMaintenanceCoordinator,
    private val permissionProvider: PermissionProvider,
    private val roleProvider: RoleProvider,
) : PaymentDebtWorkflowPort {
    override val isAdmin: StateFlow<Boolean> = roleProvider.isAdmin
    override val permissions: StateFlow<com.verto.app.data.model.EmployeePermissions?> = permissionProvider.permissions

    override fun observeInventoryItems(): Flow<List<InventoryItemView>> =
        inventoryRepository.getAllItems().map { list -> list.map(InventoryItemEntity::toPaymentView) }

    override fun observeClients(): Flow<List<ClientItem>> = partyBridge.observeClients()

    override fun observeCustomerDecision(clientId: String): Flow<PaymentCustomerDecision> =
        partyBridge.observeCustomerDecision(clientId)

    override fun observeSupplierRecommendation(inventoryItemId: String): Flow<PaymentSupplierRecommendation> =
        partyBridge.observeSupplierRecommendation(inventoryItemId)

    override suspend fun loadInvoiceForEdit(invoiceId: String): InvoiceEditViewData? {
        val summary = invoiceRepository.getInvoiceSummary(invoiceId).first() ?: return null
        val denied = if (permissionProvider.canNow { it.canEditInvoiceCategory(summary.invoice.category) }) {
            null
        } else {
            editInvoiceDeniedMessage(summary.invoice.category)
        }
        return InvoiceEditViewData(
            invoice = summary.invoice.toPaymentView(),
            items = invoiceRepository.getInvoiceItemsSync(invoiceId).map(InvoiceItemEntity::toPaymentView),
            totalPaid = summary.totalPaid,
            dueInstallments = invoiceRepository.getDueInstallmentsSync(invoiceId).map {
                PaymentDueInstallmentDraft(
                    amount = com.verto.app.money.Money.ofMinor(it.amountMinor, it.currencyCode).toLegacyDouble(),
                    dueDate = it.dueDate,
                )
            },
            deniedMessage = denied,
        )
    }

    override suspend fun insertClient(client: ClientItem) {
        partyBridge.insertClient(client)
    }

    override suspend fun findInventoryItemByName(name: String): InventoryItemView? =
        inventoryRepository.getAllItemsSync()
            .firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?.toPaymentView()

    override suspend fun saveInventoryItem(item: InventoryItemView) {
        inventoryRepository.saveItem(item.toEntity())
    }

    override fun observeVehicleSuggestions(query: InvoiceVehicleSuggestionsQuery): Flow<List<VehicleSuggestion>> =
        invoiceMaintenanceCoordinator.observeVehicleSuggestions(
            InvoiceDomainVehicleQuery(
                organizationId = query.organizationId,
                clientId = query.clientId,
                searchTerm = query.searchTerm,
                limit = query.limit,
            )
        ).map { list ->
            list.map {
                VehicleSuggestion(
                    organizationId = it.organizationId,
                    clientId = it.clientId,
                    remoteVehicleId = it.remoteVehicleId,
                    name = it.name,
                    vehicleType = it.vehicleType,
                    plateNumber = it.plateNumber,
                    updatedAt = it.updatedAt,
                )
            }
        }

    override suspend fun saveInvoice(command: PaymentSaveInvoiceCommand): InvoiceSaveResult {
        val result = saveInvoiceUseCase(
            existingInvoiceId = command.existingInvoiceId,
            clientId = command.clientId,
            items = command.items.map {
                InvoiceDomainItemData(
                    id = it.id,
                    name = it.name,
                    quantity = it.quantity,
                    sellPrice = it.sellPrice,
                    buyPrice = it.buyPrice,
                    itemCategory = it.itemCategory,
                    inventoryItemId = it.inventoryItemId,
                )
            },
            paymentMode = when (command.paymentMode) {
                PaymentMode.CASH -> InvoicePaymentMode.CASH
                PaymentMode.CREDIT -> InvoicePaymentMode.CREDIT
            },
            dueDate = command.dueDate,
            dueInstallments = command.dueInstallments.map { it.amount to it.dueDate },
            notes = command.notes,
            isSale = command.isSale,
            originalCreatedAt = command.originalCreatedAt,
            originalInvoiceNumber = command.originalInvoiceNumber,
            initialPayment = command.initialPayment,
            shipmentId = command.shipmentId,
            purchaseScope = when (command.purchaseScope) {
                PaymentPurchaseScope.LOCAL -> InvoicePurchaseScope.LOCAL
                PaymentPurchaseScope.INTERNATIONAL -> InvoicePurchaseScope.INTERNATIONAL
            },
            discount = command.discount,
            commission = command.commission,
            commissionBeneficiaryClientId = command.commissionBeneficiaryClientId,
            commissionSource = command.commissionSource,
            exchangeRate = command.exchangeRate,
            transactionCurrencyCode = command.transactionCurrencyCode,
            organizationId = command.organizationId,
            companyClient = command.companyClient,
            maintenance = command.maintenance?.toInvoiceDomain(),
            writeId = command.writeId,
        )
        return InvoiceSaveResult(
            invoiceId = result.invoiceId,
            newInventoryItemsCreated = result.newInventoryItemsCreated,
            inventoryItemsUpdated = result.inventoryItemsUpdated,
        )
    }


    override suspend fun loadInvoiceDraft(draftKey: String, organizationId: String): InvoiceEditorDraftData? {
        val header = invoiceDraftDao.getDraft(draftKey, organizationId) ?: return null
        val lines = invoiceDraftDao.getLines(draftKey)
        val images = invoiceDraftDao.getImages(draftKey)
        return header.toPaymentDraft(lines, images)
    }

    override suspend fun saveInvoiceDraft(draft: InvoiceEditorDraftData) {
        invoiceDraftDao.replaceDraft(
            header = draft.toEntity(),
            lines = draft.items.mapIndexed { index, item -> item.toDraftLineEntity(draft.draftKey, index) },
            images = draft.maintenance?.images.orEmpty().mapIndexed { index, image ->
                InvoiceEditorDraftMaintenanceImageEntity(
                    imageId = image.imageId, draftKey = draft.draftKey, localUri = image.localUri,
                    mimeType = image.mimeType, byteSize = image.byteSize, sortOrder = index,
                )
            },
        )
    }

    override suspend fun deleteInvoiceDraft(draftKey: String, organizationId: String) {
        invoiceDraftDao.deleteDraft(draftKey, organizationId)
    }
}

class PaymentInvoiceSummaryBridge @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
) : PaymentInvoiceSummaryPort {
    override fun observeInvoiceSummary(invoiceId: String): Flow<PaymentInvoiceSummaryViewData?> =
        invoiceRepository.getInvoiceSummary(invoiceId).map { summary ->
            summary?.let {
                PaymentInvoiceSummaryViewData(
                    invoice = it.invoice.toPaymentView(),
                    totalPaid = it.totalPaid,
                    remaining = it.remaining,
                )
            }
        }
}

internal fun PartyClient.toPaymentView() = ClientItem(
    id = id, name = name, phone = phone, address = address, workplace = workplace,
    generalNote = generalNote, carType = carType, bankAccount = bankAccount,
    specialty = specialty, secondaryPhones = secondaryPhones, createdAt = createdAt, createdBy = createdBy,
    isDirty = isDirty, customerSegment = customerSegment?.name, supplierScope = supplierScope?.name,
)

internal fun PartyRoleProjection.toPaymentView() = client.toPaymentView().copy(
    hasCustomerRole = hasCustomerRole,
    hasSupplierRole = hasSupplierRole,
    customerSegment = customerSegment,
    supplierScope = supplierScope,
)

internal fun ClientItem.toPartyClient() = PartyClient(
    id = id, name = name, phone = phone, address = address, workplace = workplace,
    generalNote = generalNote, carType = carType, bankAccount = bankAccount,
    specialty = specialty, secondaryPhones = secondaryPhones, createdAt = createdAt, createdBy = createdBy,
    isDirty = isDirty,
    customerSegment = customerSegmentFromStorage(customerSegment),
    supplierScope = supplierScope?.let { runCatching { com.verto.app.feature.party.domain.model.SupplierScope.valueOf(it) }.getOrNull() },
)

private fun ClientItem.toEntity() = PartyIdentityEntity(
    id = id, name = name, phone = phone, address = address, workplace = workplace,
    generalNote = generalNote, carType = carType, bankAccount = bankAccount,
    specialty = specialty, secondaryPhones = secondaryPhones, createdAt = createdAt, createdBy = createdBy,
    isDirty = isDirty,
)

private fun InventoryItemEntity.toPaymentView() = InventoryItemView(
    id = id, partNumber = partNumber, name = name, barcode = barcode, unitId = unitId,
    linkedUnitItemId = linkedUnitItemId, isUnitItem = isUnitItem, quantityPerUnit = quantityPerUnit,
    isService = isService, buyPrice = buyPrice, sellPrice = sellPrice, quantity = quantity,
    minQuantity = minQuantity, location = location, note = note, createdAt = createdAt,
    updatedAt = updatedAt, isDirty = isDirty,
)

private fun InventoryItemView.toEntity() = InventoryItemEntity(
    id = id, partNumber = partNumber, name = name, barcode = barcode, unitId = unitId,
    linkedUnitItemId = linkedUnitItemId, isUnitItem = isUnitItem, quantityPerUnit = quantityPerUnit,
    isService = isService, buyPrice = buyPrice, sellPrice = sellPrice, quantity = quantity,
    minQuantity = minQuantity, location = location, note = note, createdAt = createdAt,
    updatedAt = updatedAt, isDirty = isDirty,
)

private fun InvoiceEntity.toPaymentView() = InvoiceViewData(
    id = id,
    invoiceNumber = invoiceNumber,
    clientId = clientId,
    type = InvoiceType.GOODS,
    category = when (category) {
        PersistenceInvoiceCategory.SALE -> InvoiceCategory.SALE
        PersistenceInvoiceCategory.PURCHASE -> InvoiceCategory.PURCHASE
    },
    description = description,
    totalAmount = totalAmount,
    transactionCurrencyCode = transactionCurrencyCode,
    functionalCurrencyCode = functionalCurrencyCode,
    invoiceExchangeRateSnapshot = invoiceExchangeRateSnapshot,
    functionalAmountAtRecognitionMinor = functionalAmountAtRecognitionMinor,
    legacyCurrencyStatus = legacyCurrencyStatus.name,
    createdAt = createdAt,
    dueDate = dueDate,
    notifyDaysBefore = notifyDaysBefore,
    notifyRepeatDays = notifyRepeatDays,
    notificationsEnabled = notificationsEnabled,
    notes = notes,
    isOwedToMe = isOwedToMe,
    imageUri = imageUri,
    status = when (status) {
        PersistenceInvoiceStatus.CLOSED_CASH -> InvoiceStatus.CLOSED_CASH
        PersistenceInvoiceStatus.CLOSED_CREDIT -> InvoiceStatus.CLOSED_CREDIT
    },
    discount = discount,
    commission = commission,
    commissionBeneficiaryClientId = commissionBeneficiaryClientId,
    commissionSource = commissionSource,
    shipmentId = shipmentId,
    purchaseScope = when (purchaseScope) {
        com.verto.app.data.local.entity.PurchaseScope.LOCAL -> PaymentPurchaseScope.LOCAL
        com.verto.app.data.local.entity.PurchaseScope.INTERNATIONAL -> PaymentPurchaseScope.INTERNATIONAL
    },
    createdBy = createdBy,
    voided = voided,
    isDirty = isDirty,
)

private fun InvoiceItemEntity.toPaymentView() = InvoiceLineView(
    id = id, invoiceId = invoiceId, itemName = itemName, itemCategory = itemCategory,
    quantity = quantity, buyPrice = buyPrice, sellPrice = sellPrice, totalPrice = totalPrice,
    description = description, isOwedToMe = isOwedToMe, inventoryItemId = inventoryItemId,
    adjustedPurchasePrice = adjustedPurchasePrice, isDirty = isDirty,
)

private fun CompanyMaintenanceData.toInvoiceDomain() = InvoiceCompanyMaintenanceData(
    recordId = recordId,
    officialVehicleOrganizationId = officialVehicleOrganizationId,
    officialVehicleId = officialVehicleId,
    vehicleName = vehicleName,
    vehicleType = vehicleType,
    plateNumber = plateNumber,
    driverOrDelegate = driverOrDelegate,
    notes = notes,
    images = images.map {
        InvoiceCompanyMaintenanceImageData(it.imageId, it.localUri, it.mimeType, it.byteSize, it.sortOrder)
    },
    createdAt = createdAt,
)


private fun encodeDraftDueInstallments(rows: List<PaymentDueInstallmentDraft>): String =
    JSONArray().apply {
        rows.forEach { row ->
            put(JSONObject().put("amount", row.amount).put("dueDate", row.dueDate))
        }
    }.toString()

private fun decodeDraftDueInstallments(raw: String): List<PaymentDueInstallmentDraft> = runCatching {
    val array = JSONArray(raw.ifBlank { "[]" })
    buildList {
        for (index in 0 until array.length()) {
            val row = array.getJSONObject(index)
            val amount = row.getDouble("amount")
            val dueDate = row.getLong("dueDate")
            if (amount > 0.0 && dueDate > 0L) add(PaymentDueInstallmentDraft(amount, dueDate))
        }
    }
}.getOrDefault(emptyList())

private fun InvoiceEditorDraftData.toEntity(): InvoiceEditorDraftEntity {
    val m = maintenance
    val v = m?.selectedOfficialVehicle
    return InvoiceEditorDraftEntity(
        draftKey = draftKey,
        organizationId = organizationId,
        route = InvoiceEditorDraftRouteColumns(
            existingInvoiceId = existingInvoiceId,
            routeClientId = routeClientId,
            mode = InvoiceEditorDraftModeColumns(isInternational, isSale, paymentMode.name),
            selectedClientId = selectedClientId,
            selectedDateMillis = selectedDateMillis,
        ),
        financial = InvoiceEditorDraftFinancialColumns(
            dueDays = dueDays, notes = notes, paidAmount = paidAmount,
            discount = discount, referrerClientId = referrerClientId,
            dueInstallmentsJson = encodeDraftDueInstallments(dueInstallments),
            transactionCurrencyCode = transactionCurrencyCode, exchangeRate = exchangeRate,
            persistence = InvoiceEditorDraftPersistenceColumns(writeId, updatedAt),
        ),
        composer = InvoiceEditorDraftComposerColumns(
            draftItemName = draftItem.name, draftItemQuantity = draftItem.quantity,
            draftItemSellPrice = draftItem.sellPrice, draftItemBuyPrice = draftItem.buyPrice,
            draftInventoryItemId = draftItem.inventoryItemId,
        ),
        maintenance = InvoiceEditorDraftMaintenanceColumns(
            enabled = m?.isEnabled == true,
            expanded = m?.isExpanded == true,
            owner = InvoiceEditorDraftMaintenanceOwnerColumns(
                organizationId = m?.ownerOrganizationId.orEmpty(), clientId = m?.ownerClientId.orEmpty(),
                recordId = m?.recordId.orEmpty(), createdAt = m?.createdAt ?: 0L,
            ),
            vehicle = InvoiceEditorDraftMaintenanceVehicleColumns(
                query = m?.vehicleQuery.orEmpty(),
                selected = v?.let {
                    InvoiceEditorDraftVehicleSuggestionColumns(
                        identity = InvoiceEditorDraftVehicleIdentityColumns(
                            organizationId = it.organizationId, clientId = it.clientId, remoteId = it.remoteVehicleId,
                        ),
                        name = it.name, type = it.vehicleType, plate = it.plateNumber, updatedAt = it.updatedAt,
                    )
                },
                plateNumber = m?.plateNumber.orEmpty(),
                driverOrDelegate = m?.driverOrDelegate.orEmpty(),
            ),
            notes = m?.notes.orEmpty(),
        ),
    )
}

private fun InvoiceItemData.toDraftLineEntity(draftKey: String, index: Int) = InvoiceEditorDraftLineEntity(
    id = id,
    draftKey = draftKey,
    sortOrder = index,
    item = InvoiceEditorDraftLineValues(
        name = name, quantity = quantity, sellPrice = sellPrice, buyPrice = buyPrice,
        itemCategory = itemCategory, inventoryItemId = inventoryItemId,
    ),
)

private fun InvoiceEditorDraftEntity.toPaymentDraft(
    lines: List<InvoiceEditorDraftLineEntity>,
    images: List<InvoiceEditorDraftMaintenanceImageEntity>,
): InvoiceEditorDraftData {
    val selected = maintenance.vehicle.selected
    val vehicle = selected?.identity?.remoteId?.let { remoteId ->
        VehicleSuggestion(
            organizationId = selected.identity.organizationId.orEmpty(),
            clientId = selected.identity.clientId.orEmpty(),
            remoteVehicleId = remoteId,
            name = selected.name.orEmpty(),
            vehicleType = selected.type.orEmpty(),
            plateNumber = selected.plate.orEmpty(),
            updatedAt = selected.updatedAt ?: 0L,
        )
    }
    val maintenanceDraft = if (maintenance.enabled || maintenance.owner.recordId.isNotBlank() || images.isNotEmpty()) {
        InvoiceMaintenanceDraftData(
            flags = InvoiceMaintenanceDraftFlags(maintenance.enabled, maintenance.expanded),
            owner = InvoiceMaintenanceDraftOwner(
                organizationId = maintenance.owner.organizationId, clientId = maintenance.owner.clientId,
                recordId = maintenance.owner.recordId, createdAt = maintenance.owner.createdAt,
            ),
            vehicle = InvoiceMaintenanceDraftVehicle(
                query = maintenance.vehicle.query, selectedOfficialVehicle = vehicle,
                plateNumber = maintenance.vehicle.plateNumber, driverOrDelegate = maintenance.vehicle.driverOrDelegate,
            ),
            notes = maintenance.notes,
            images = images.map { CompanyMaintenanceImageData(it.imageId, it.localUri, it.mimeType, it.byteSize, it.sortOrder) },
        )
    } else null
    return InvoiceEditorDraftData(
        identity = InvoiceDraftIdentity(draftKey, organizationId),
        editor = InvoiceDraftEditorContext(
            existingInvoiceId = route.existingInvoiceId, routeClientId = route.routeClientId,
            mode = InvoiceDraftMode(
                isInternational = route.mode.isInternational,
                isSale = route.mode.isSale,
                paymentMode = runCatching { PaymentMode.valueOf(route.mode.paymentMode) }.getOrDefault(PaymentMode.CASH),
            ),
            selectedClientId = route.selectedClientId, selectedDateMillis = route.selectedDateMillis,
        ),
        financial = InvoiceDraftFinancialState(
            dueDays = financial.dueDays, notes = financial.notes, paidAmount = financial.paidAmount,
            discount = financial.discount, referrerClientId = financial.referrerClientId,
            dueInstallments = decodeDraftDueInstallments(financial.dueInstallmentsJson),
            transactionCurrencyCode = financial.transactionCurrencyCode, exchangeRate = financial.exchangeRate,
            persistence = InvoiceDraftPersistence(financial.persistence.writeId, financial.persistence.updatedAt),
        ),
        items = lines.sortedBy { it.sortOrder }.map { line ->
            InvoiceItemData(
                line.id, line.item.name, line.item.quantity, line.item.sellPrice, line.item.buyPrice,
                line.item.itemCategory, line.item.inventoryItemId,
            )
        },
        draftItem = InvoiceItemData(
            name = composer.draftItemName, quantity = composer.draftItemQuantity, sellPrice = composer.draftItemSellPrice,
            buyPrice = composer.draftItemBuyPrice, inventoryItemId = composer.draftInventoryItemId,
        ),
        maintenance = maintenanceDraft,
    )
}
