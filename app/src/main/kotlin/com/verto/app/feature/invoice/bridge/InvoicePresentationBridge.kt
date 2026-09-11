package com.verto.app.feature.invoice.bridge

import android.content.Context
import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.verto.app.core.error.ErrorPresentationContext
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.InvoiceDao
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.InvoiceCategory as PersistenceInvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceStatus as PersistenceInvoiceStatus
import com.verto.app.data.local.entity.InvoiceType as PersistenceInvoiceType
import com.verto.app.data.local.entity.ItemType
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.data.local.entity.PaymentMethod as PersistencePaymentMethod
import com.verto.app.data.local.entity.PurchaseScope as PersistencePurchaseScope
import com.verto.app.data.model.canExportInvoiceCategory
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.data.repository.InvoiceSummary as PersistenceInvoiceSummary
import com.verto.app.data.repository.OrgSettings
import com.verto.app.data.repository.OrgSettingsRepository
import com.verto.app.data.sync.SyncManager
import com.verto.app.data.sync.SyncRequestReason
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.invoice.application.*
import com.verto.app.feature.invoice.application.port.InvoicePresentationPort
import com.verto.app.feature.invoice.data.toFinancialState
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.payment.domain.model.PaymentOperationResult
import com.verto.app.feature.payment.domain.port.PaymentReversalPort
import com.verto.app.pdf.InvoicePrintSettings as AppInvoicePrintSettings
import com.verto.app.pdf.generateInvoicePdf
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.UserErrorFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class InvoicePresentationBridge @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val invoiceDao: InvoiceDao,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
    private val partyDirectory: com.verto.app.feature.party.domain.repository.PartyDirectoryGateway,
    private val preferences: PreferencesManager,
    private val orgRepository: OrgSettingsRepository,
    private val syncManager: SyncManager,
    private val permissionProvider: PermissionProvider,
    private val paymentReversalPort: PaymentReversalPort,
    roleProvider: RoleProvider,
    @ApplicationContext private val context: Context,
) : InvoicePresentationPort {
    override val isAdmin: StateFlow<Boolean> = roleProvider.isAdmin
    override val permissions: StateFlow<com.verto.app.data.model.EmployeePermissions?> = permissionProvider.permissions

    override fun observeSummary(invoiceId: String): Flow<InvoiceSummary?> =
        combine(
            invoiceRepository.getInvoiceSummary(invoiceId),
            invoiceDao.observeDueInstallments(invoiceId),
        ) { summary, installments ->
            summary?.toPresentation(installments)
        }

    override fun observeClient(clientId: String): Flow<ClientItem?> =
        partyDirectory.getClientById(clientId).map { it?.toPresentation() }

    override fun observeInvoiceItems(invoiceId: String): Flow<List<InvoiceLineView>> =
        invoiceRepository.getInvoiceItemsFlow(invoiceId).map { list -> list.map(InvoiceItemEntity::toPresentation) }

    override fun observeCommunicationHistory(invoiceId: String): Flow<List<InvoiceCommunicationEvent>> =
        database.auditLogDao().getByRecord(invoiceId).map { rows ->
            rows.mapNotNull { row ->
                val kind = when (row.sourceType) {
                    "INVOICE_COMMUNICATION_INVOICE_OPENED" -> InvoiceCommunicationKind.INVOICE_OPENED
                    "INVOICE_COMMUNICATION_REMINDER_OPENED" -> InvoiceCommunicationKind.REMINDER_OPENED
                    "INVOICE_COMMUNICATION_THANK_YOU_OPENED" -> InvoiceCommunicationKind.THANK_YOU_OPENED
                    else -> null
                } ?: return@mapNotNull null
                InvoiceCommunicationEvent(
                    id = row.id,
                    kind = kind,
                    createdAt = row.createdAt,
                    employeeName = row.employeeName,
                )
            }
        }

    override fun observeOrganizationSettings(): Flow<InvoiceOrgSettings> =
        orgRepository.orgSettings.map(OrgSettings::toPresentation)

    override fun observePrintSettings(): Flow<InvoicePrintSettings> = combine(
        preferences.invoiceTemplate,
        preferences.invoiceFont,
        preferences.invoiceFontSize,
    ) { template, font, fontSize ->
        InvoicePrintSettings(template = template, font = font, fontSize = fontSize)
    }

    override fun observeClients(): Flow<List<ClientItem>> =
        partyDirectory.getAllClients().map { list -> list.map(com.verto.app.feature.party.domain.model.PartyClient::toPresentation) }

    override fun observePagedInvoices(
        category: String,
        tab: Int,
        from: Long,
        to: Long,
        search: String,
        sort: String,
        purchaseScope: String?,
    ): Flow<PagingData<InvoicePaymentSummary>> = Pager(
        PagingConfig(pageSize = 30, enablePlaceholders = false)
    ) {
        invoiceDao.getInvoicesPagedWithPaid(category, tab, from, to, search, sort, purchaseScope)
    }.flow.map { data ->
        data.map { row ->
            val financial = row.invoice.toFinancialState(row.totalPaid)
            InvoicePaymentSummary(
                invoice = row.invoice.toPresentation(),
                totalPaid = row.totalPaid,
                financial = financial.toPresentation(),
            )
        }
    }

    override suspend fun canManageCommission(): Boolean = permissionProvider.canNow { it.commissionManage }

    override suspend fun canExport(category: InvoiceCategory): Boolean = permissionProvider.canNow {
        it.canExportInvoiceCategory(category.toPersistence())
    }

    override suspend fun updateCommission(
        invoiceId: String,
        commission: Double,
        beneficiaryClientId: String,
        commissionSource: String,
    ) {
        require(commission >= 0.0) { "العمولة لا يمكن أن تكون سالبة" }
        val beneficiary = beneficiaryClientId.trim().also { require(it.isNotBlank()) { "مستفيد العمولة مطلوب" } }
        val source = commissionSource.trim().uppercase().also {
            require(it == "BUYER" || it == "REFERRER") { "مصدر العمولة غير صالح" }
        }
        val organizationId = sessionReader.snapshot().organization.id.trim().also {
            require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
        }
        val mutationId = UUID.randomUUID().toString()
        database.withTransaction {
            val current = requireNotNull(invoiceDao.getInvoiceByIdSync(invoiceId)) { "الفاتورة غير موجودة" }
            check(invoiceDao.updateCommission(invoiceId, commission, beneficiary, source, current.lifecycleVersion) == 1) {
                "CONFLICT: أعد تحميل الفاتورة ثم حاول مجددًا"
            }
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "INVOICE",
                aggregateId = invoiceId,
                operationType = "UPSERT",
                payload = mapOf(
                    "commission" to commission,
                    "commission_beneficiary_client_id" to beneficiary,
                    "commission_source" to source,
                    "command" to "UPDATE_COMMISSION_ATTRIBUTION",
                ),
                mutationId = mutationId,
            )
        }
    }

    override suspend fun reversePayment(paymentId: String): InvoicePaymentReversalResult =
        when (val result = paymentReversalPort.reverse(paymentId)) {
            is PaymentOperationResult.Error -> InvoicePaymentReversalResult.Error(
                UserErrorFactory.from(
                    failure = result.failure,
                    context = ErrorPresentationContext.TRANSIENT_ACTION,
                    operation = "invoice.reverse_payment",
                    outcome = result.outcome,
                )
            )
            is PaymentOperationResult.Success -> InvoicePaymentReversalResult.Success
        }

    override suspend fun fullSync(): Result<Unit> = syncManager.request(SyncRequestReason.OUTBOX_WRITE).map { Unit }

    override suspend fun createInvoicePdf(request: InvoicePdfRequest): File = generateInvoicePdf(
        context = context,
        client = request.client.toPersistence(),
        summary = request.summary.toPersistence(),
        orgSettings = request.orgSettings.toPersistence(),
        employeeName = request.employeeName,
        employeePhone = request.employeePhone,
        items = request.items.map(InvoiceLineView::toPersistence),
        printSettings = AppInvoicePrintSettings(
            template = request.printSettings.template,
            font = request.printSettings.font,
            fontSize = request.printSettings.fontSize,
        ),
        showCommission = false,
    )
}

private fun com.verto.app.feature.party.domain.model.PartyClient.toPresentation() = ClientItem(
    id = id, name = name, phone = phone, address = address, workplace = workplace,
    generalNote = generalNote, customerSegment = customerSegment?.name, carType = carType, bankAccount = bankAccount,
    specialty = specialty, secondaryPhones = secondaryPhones, createdAt = createdAt,
    createdBy = createdBy, isDirty = isDirty,
)

private fun ClientItem.toPersistence() = PartyIdentityEntity(
    id = id, name = name, phone = phone, address = address, workplace = workplace,
    generalNote = generalNote, carType = carType, bankAccount = bankAccount,
    specialty = specialty, secondaryPhones = secondaryPhones, createdAt = createdAt,
    createdBy = createdBy, isDirty = isDirty,
)

private fun InvoiceEntity.toPresentation() = InvoiceViewData(
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
    transactionAmountMinor = transactionAmountMinor,
    invoiceExchangeRateSnapshot = invoiceExchangeRateSnapshot,
    exchangeRateDirection = exchangeRateDirection,
    exchangeRateTimestamp = exchangeRateTimestamp,
    exchangeRateSource = exchangeRateSource,
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
        PersistencePurchaseScope.LOCAL -> PurchaseScope.LOCAL
        PersistencePurchaseScope.INTERNATIONAL -> PurchaseScope.INTERNATIONAL
    },
    createdBy = createdBy,
    voided = voided,
    isDirty = isDirty,
)

private fun InvoiceViewData.toPersistence() = InvoiceEntity(
    id = id,
    invoiceNumber = invoiceNumber,
    clientId = clientId,
    type = PersistenceInvoiceType.GOODS,
    category = category.toPersistence(),
    description = description,
    totalAmount = totalAmount,
    transactionCurrencyCode = transactionCurrencyCode,
    functionalCurrencyCode = functionalCurrencyCode,
    transactionAmountMinor = transactionAmountMinor,
    invoiceExchangeRateSnapshot = invoiceExchangeRateSnapshot,
    exchangeRateDirection = exchangeRateDirection,
    exchangeRateTimestamp = exchangeRateTimestamp,
    exchangeRateSource = exchangeRateSource,
    functionalAmountAtRecognitionMinor = functionalAmountAtRecognitionMinor,
    legacyCurrencyStatus = runCatching { com.verto.app.data.local.entity.LegacyCurrencyStatus.valueOf(legacyCurrencyStatus) }.getOrDefault(com.verto.app.data.local.entity.LegacyCurrencyStatus.REVIEW_REQUIRED),
    createdAt = createdAt,
    dueDate = dueDate,
    notifyDaysBefore = notifyDaysBefore,
    notifyRepeatDays = notifyRepeatDays,
    notificationsEnabled = notificationsEnabled,
    notes = notes,
    isOwedToMe = isOwedToMe,
    imageUri = imageUri,
    status = when (status) {
        InvoiceStatus.CLOSED_CASH -> PersistenceInvoiceStatus.CLOSED_CASH
        InvoiceStatus.CLOSED_CREDIT -> PersistenceInvoiceStatus.CLOSED_CREDIT
    },
    discount = discount,
    commission = commission,
    commissionBeneficiaryClientId = commissionBeneficiaryClientId,
    commissionSource = commissionSource,
    shipmentId = shipmentId,
    purchaseScope = when (purchaseScope) {
        PurchaseScope.LOCAL -> PersistencePurchaseScope.LOCAL
        PurchaseScope.INTERNATIONAL -> PersistencePurchaseScope.INTERNATIONAL
    },
    createdBy = createdBy,
    voided = voided,
    isDirty = isDirty,
)

private fun InvoiceItemEntity.toPresentation() = InvoiceLineView(
    id = id, invoiceId = invoiceId, itemName = itemName, itemCategory = itemCategory,
    quantity = quantity, buyPrice = buyPrice, sellPrice = sellPrice, totalPrice = totalPrice,
    unitSellPrice = unitSellPrice, unitSellPriceMinor = unitSellPriceMinor,
    unitCostAtSale = unitCostAtSale, unitCostAtSaleMinor = unitCostAtSaleMinor,
    lineRevenueSnapshot = lineRevenueSnapshot, lineRevenueSnapshotMinor = lineRevenueSnapshotMinor,
    lineCostSnapshot = lineCostSnapshot, lineCostSnapshotMinor = lineCostSnapshotMinor,
    grossProfitSnapshot = grossProfitSnapshot, grossProfitSnapshotMinor = grossProfitSnapshotMinor,
    costSnapshotStatus = costSnapshotStatus,
    description = description, isOwedToMe = isOwedToMe, inventoryItemId = inventoryItemId,
    adjustedPurchasePrice = adjustedPurchasePrice, isDirty = isDirty,
)

private fun InvoiceLineView.toPersistence() = InvoiceItemEntity(
    id = id, invoiceId = invoiceId, itemType = ItemType.GOODS, itemName = itemName,
    itemCategory = itemCategory, quantity = quantity, buyPrice = buyPrice, sellPrice = sellPrice,
    totalPrice = totalPrice,
    unitSellPrice = unitSellPrice, unitSellPriceMinor = unitSellPriceMinor,
    unitCostAtSale = unitCostAtSale, unitCostAtSaleMinor = unitCostAtSaleMinor,
    lineRevenueSnapshot = lineRevenueSnapshot, lineRevenueSnapshotMinor = lineRevenueSnapshotMinor,
    lineCostSnapshot = lineCostSnapshot, lineCostSnapshotMinor = lineCostSnapshotMinor,
    grossProfitSnapshot = grossProfitSnapshot, grossProfitSnapshotMinor = grossProfitSnapshotMinor,
    costSnapshotStatus = costSnapshotStatus,
    description = description, isOwedToMe = isOwedToMe,
    inventoryItemId = inventoryItemId, adjustedPurchasePrice = adjustedPurchasePrice, isDirty = isDirty,
)

private fun PaymentEntity.toPresentation() = PaymentItem(
    id = id, invoiceId = invoiceId, clientId = clientId, amount = amount,
    paymentCurrencyCode = paymentCurrencyCode,
    paymentExchangeRate = paymentExchangeRate,
    functionalCashAmountMinor = functionalCashAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    legacyCurrencyStatus = legacyCurrencyStatus.name,
    paymentMethod = when (paymentMethod) {
        PersistencePaymentMethod.CASH -> InvoicePaymentMethod.CASH
        PersistencePaymentMethod.TRANSFER -> InvoicePaymentMethod.TRANSFER
        PersistencePaymentMethod.CHECK -> InvoicePaymentMethod.CHECK
    },
    note = note, paidAt = paidAt, employeeId = employeeId, employeeName = employeeName,
    reversedPaymentId = reversedPaymentId, isDirty = isDirty,
)

private fun PaymentItem.toPersistence() = PaymentEntity(
    id = id, invoiceId = invoiceId, clientId = clientId, amount = amount,
    paymentCurrencyCode = paymentCurrencyCode,
    paymentExchangeRate = paymentExchangeRate,
    functionalCashAmountMinor = functionalCashAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    legacyCurrencyStatus = runCatching { com.verto.app.data.local.entity.LegacyCurrencyStatus.valueOf(legacyCurrencyStatus) }.getOrDefault(com.verto.app.data.local.entity.LegacyCurrencyStatus.REVIEW_REQUIRED),
    paymentMethod = when (paymentMethod) {
        InvoicePaymentMethod.CASH -> PersistencePaymentMethod.CASH
        InvoicePaymentMethod.TRANSFER -> PersistencePaymentMethod.TRANSFER
        InvoicePaymentMethod.CHECK -> PersistencePaymentMethod.CHECK
    },
    note = note, paidAt = paidAt, employeeId = employeeId, employeeName = employeeName,
    reversedPaymentId = reversedPaymentId, isDirty = isDirty,
)

private fun PersistenceInvoiceSummary.toPresentation(
    installments: List<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity> = emptyList(),
): InvoiceSummary = InvoiceSummary(
    invoice = invoice.toPresentation(),
    totalPaid = totalPaid,
    payments = payments.map(PaymentEntity::toPresentation),
    dueInstallments = installments.map { row ->
        InvoiceDueInstallmentView(
            sequence = row.sequence,
            amount = com.verto.app.money.Money.ofMinor(row.amountMinor, row.currencyCode).toLegacyDouble(),
            currencyCode = row.currencyCode,
            dueDate = row.dueDate,
        )
    },
    financial = financial.toPresentation().withInstallmentDueState(
        invoiceTotalMinor = invoice.totalAmountMinor,
        totalPaid = totalPaid,
        currencyCode = invoice.transactionCurrencyCode,
        installments = installments,
    ),
)

private fun InvoiceSummary.toPersistence(): PersistenceInvoiceSummary = PersistenceInvoiceSummary(
    invoice = invoice.toPersistence(),
    totalPaid = totalPaid,
    payments = payments.map(PaymentItem::toPersistence),
)

private fun InvoiceFinancialViewData.withInstallmentDueState(
    invoiceTotalMinor: Long,
    totalPaid: Double,
    currencyCode: String,
    installments: List<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity>,
): InvoiceFinancialViewData {
    if (installments.isEmpty() || isPaid) return this
    val scheduledTotal = installments.fold(0L) { acc, row -> Math.addExact(acc, row.amountMinor) }
    val baselinePaid = (invoiceTotalMinor - scheduledTotal).coerceAtLeast(0L)
    val paidMinor = com.verto.app.money.Money.fromLegacyDouble(
        totalPaid,
        currencyCode.ifBlank { com.verto.app.money.Money.TRANSACTION_CURRENCY },
    ).amountMinor
    val paidTowardSchedule = (paidMinor - baselinePaid).coerceAtLeast(0L)
    var cumulative = 0L
    val nextDue = installments.sortedWith(compareBy({ it.dueDate }, { it.sequence })).firstOrNull { row ->
        cumulative = Math.addExact(cumulative, row.amountMinor)
        cumulative > paidTowardSchedule
    } ?: return copy(isOverdue = false, overdueDays = 0)
    val now = System.currentTimeMillis()
    if (nextDue.dueDate <= 0L || nextDue.dueDate >= now) return copy(isOverdue = false, overdueDays = 0)
    val days = ((now - nextDue.dueDate) / 86_400_000L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return copy(isOverdue = true, overdueDays = days)
}

private fun com.verto.app.feature.invoice.domain.model.InvoiceFinancialState.toPresentation() =
    InvoiceFinancialViewData(
        remaining = remaining,
        isOverdue = isOverdue,
        overdueDays = overdueDays,
        isPaid = isPaid,
        progressPercent = progressPercent,
    )

private fun OrgSettings.toPresentation() = InvoiceOrgSettings(
    shopName = shopName, shopPhone = shopPhone, city = city, address = address,
    currency = currency, invoiceFooter = invoiceFooter, taxNumber = taxNumber,
    logoUrl = logoUrl, signatureUrl = signatureUrl,
)

private fun InvoiceOrgSettings.toPersistence() = OrgSettings(
    shopName = shopName, shopPhone = shopPhone, city = city, address = address,
    currency = currency, invoiceFooter = invoiceFooter, taxNumber = taxNumber,
    logoUrl = logoUrl, signatureUrl = signatureUrl,
)

private fun InvoiceCategory.toPersistence() = when (this) {
    InvoiceCategory.SALE -> PersistenceInvoiceCategory.SALE
    InvoiceCategory.PURCHASE -> PersistenceInvoiceCategory.PURCHASE
}
