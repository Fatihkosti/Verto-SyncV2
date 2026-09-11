package com.verto.app.feature.invoice.application

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.invoice.domain.model.InvoiceIntegrationWriteKind
import com.verto.app.feature.invoice.domain.model.InvoiceDueInstallment
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceLifecycleStatus
import com.verto.app.feature.invoice.domain.model.InvoiceSaveResult
import com.verto.app.feature.invoice.domain.model.InvoiceWriteIdentity
import com.verto.app.feature.invoice.domain.model.InvoiceWriteOperation
import com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceCandidateLine
import com.verto.app.feature.invoice.domain.model.PurchasePaymentOverrideRecord
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.feature.invoice.domain.port.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.first
import java.util.UUID

import javax.inject.Inject

class InvoiceWriteCoordinator @Inject internal constructor(
    private val transactionPort: InvoiceTransactionPort,
    private val store: InvoiceStorePort,
    private val sessionReader: SessionReader,
    private val settings: InvoiceSettingsPort,
    private val authorization: InvoiceAuthorizationPort,
    private val preparation: InvoiceWritePreparation,
    private val editPolicy: InvoiceEditPolicy,
    private val inventoryWriter: InvoiceInventoryWriter,
    private val paymentWriter: InvoicePaymentWriter,
    private val auditLogger: WriteAuditPort,
    private val postCommitEffects: InvoicePostCommitEffects,
    private val atomicPersistenceCoordinator: InvoiceAtomicPersistenceCoordinator = NoOpInvoiceAtomicPersistenceCoordinator,
    private val purchaseCycle: PurchaseCycleInvoicePort? = null,
) {
    constructor(
        transactionPort: InvoiceTransactionPort, store: InvoiceStorePort, stock: InvoiceStockPort,
        cash: InvoiceCashPort, auditLogger: WriteAuditPort,
        sessionReader: SessionReader, settings: InvoiceSettingsPort, authorization: InvoiceAuthorizationPort,
        numberPort: InvoiceNumberPort, syncScheduler: InvoiceSyncSchedulerPort,
        atomicPersistenceCoordinator: InvoiceAtomicPersistenceCoordinator = NoOpInvoiceAtomicPersistenceCoordinator,
    ) : this(
        transactionPort, store, sessionReader, settings, authorization,
        InvoiceWritePreparation(numberPort, InvoiceSaveValidator(), InvoiceDraftFactory(), InvoiceWriteIdentityFactory()),
        InvoiceEditPolicy(), InvoiceInventoryWriter(stock), InvoicePaymentWriter(store, cash), auditLogger,
        InvoicePostCommitEffects(syncScheduler), atomicPersistenceCoordinator, null,
    )

    suspend fun save(command: SaveInvoiceCommand): InvoiceSaveResult {
        val normalizedCommand = normalizeWriteIdentity(command)
        val creatingNew = normalizedCommand.existingInvoiceId == null
        authorize(normalizedCommand, creatingNew)
        val validated = preparation.validate(normalizedCommand)
        if (preparation.requiresReferencedInventory(normalizedCommand)) {
            preparation.validateReferencedInventory(normalizedCommand, inventoryWriter.loadItems())
        }

        val currentUser = sessionReader.currentUser.first()
        val actor = InvoiceActor(currentUser.id, currentUser.name)
        val draft = preparation.createDraft(normalizedCommand, validated, actor.id)
        val allowNegativeStock = settings.allowNegativeStock()
        val releasedEditItems = normalizedCommand.existingInvoiceId
            ?.takeIf { normalizedCommand.isSale }
            ?.let { invoiceId ->
                store.getInvoiceById(invoiceId)
                    ?.takeIf { it.lifecycleStatus == InvoiceLifecycleStatus.POSTED }
                    ?.let { store.getInvoiceItems(invoiceId) }
            }
            .orEmpty()
        if (allowNegativeStock &&
            inventoryWriter.requiresNegativeStockOverride(normalizedCommand, releasedEditItems) &&
            !authorization.canOverrideStock()
        ) {
            throw SecurityException("لا تملك صلاحية تجاوز المخزون المتاح")
        }
        if (normalizedCommand.transactionCurrencyCode != normalizedCommand.functionalCurrencyCode &&
            normalizedCommand.exchangeRateSource != "FUNCTIONAL_CURRENCY" &&
            !authorization.canApproveExchangeRate()
        ) {
            throw SecurityException("لا تملك صلاحية اعتماد سعر الصرف")
        }
        return if (!normalizedCommand.existingInvoiceId.isNullOrEmpty()) {
            saveEdit(normalizedCommand, validated, draft, actor, allowNegativeStock)
        } else {
            saveNew(normalizedCommand, validated, draft, actor, allowNegativeStock)
        }
    }

    private suspend fun normalizeWriteIdentity(command: SaveInvoiceCommand): SaveInvoiceCommand {
        val organizationId = command.organizationId.trim().ifBlank {
            sessionReader.organizationId.first().trim()
        }
        require(organizationId.isNotEmpty()) { "تعذر تحديد الشركة لهذه الفاتورة" }
        val writeId = command.writeId.trim().ifBlank { preparation.newId() }
        val requestedAt = command.requestedAt.takeIf { it > 0L } ?: preparation.nowMillis()
        val existingInvoice = command.existingInvoiceId?.let { store.getInvoiceById(it) }
        require(existingInvoice?.legacyCurrencyStatus != com.verto.app.feature.invoice.domain.model.LegacyCurrencyStatus.UNKNOWN) {
            "عملة هذه الفاتورة الدولية القديمة غير معروفة؛ راجع بيانات العملة قبل تعديلها"
        }
        val existingCurrencyTruth = existingInvoice
            ?.takeIf { it.legacyCurrencyStatus == com.verto.app.feature.invoice.domain.model.LegacyCurrencyStatus.KNOWN }
        val functionalCurrency = existingCurrencyTruth?.functionalCurrencyCode
            ?.takeIf { it.isNotBlank() }
            ?: command.functionalCurrencyCode.trim().ifBlank { settings.functionalCurrencyCode() }.trim().uppercase()
        require(functionalCurrency.isNotEmpty()) { "حدد العملة الوظيفية للمنشأة قبل حفظ فاتورة مالية" }
        val transactionCurrency = existingCurrencyTruth?.transactionCurrencyCode
            ?.takeIf { it.isNotBlank() }
            ?: if (command.purchaseScope == com.verto.app.feature.invoice.domain.model.PurchaseScope.LOCAL) {
                functionalCurrency
            } else {
                command.transactionCurrencyCode.trim().uppercase().also {
                    require(it.isNotEmpty()) { "عملة الفاتورة الدولية مطلوبة" }
                }
            }
        val normalizedRate = when {
            existingCurrencyTruth?.invoiceExchangeRateSnapshot?.isNotBlank() == true -> com.verto.app.money.ExchangeRate.parse(
                existingCurrencyTruth.invoiceExchangeRateSnapshot, transactionCurrency, functionalCurrency,
            )
            transactionCurrency == functionalCurrency -> com.verto.app.money.ExchangeRate.one(transactionCurrency, functionalCurrency)
            else -> com.verto.app.money.ExchangeRate.parse(
                command.exchangeRate.asDecimal().toPlainString(),
                baseCurrency = transactionCurrency,
                quoteCurrency = functionalCurrency,
            )
        }
        val rateSource = existingCurrencyTruth?.exchangeRateSource?.takeIf { it.isNotBlank() }
            ?: if (transactionCurrency == functionalCurrency) "FUNCTIONAL_CURRENCY"
            else command.exchangeRateSource.trim().ifBlank { "USER_INPUT" }
        val contractualDueDate = command.dueInstallments.minOfOrNull { it.dueDate } ?: command.dueDate
        return command.copy(
            organizationId = organizationId,
            writeId = writeId,
            requestedAt = requestedAt,
            dueDate = contractualDueDate,
            purchaseScope = existingCurrencyTruth?.purchaseScope ?: command.purchaseScope,
            transactionCurrencyCode = transactionCurrency,
            functionalCurrencyCode = functionalCurrency,
            exchangeRate = normalizedRate,
            exchangeRateTimestamp = existingCurrencyTruth?.exchangeRateTimestamp?.takeIf { it > 0L }
                ?: command.exchangeRateTimestamp.takeIf { it > 0L } ?: requestedAt,
            exchangeRateSource = rateSource,
            supplierInvoiceReference = command.supplierInvoiceReference?.trim()?.takeIf { it.isNotEmpty() },
            purchaseOrderId = existingInvoice?.purchaseOrderId
                ?: command.purchaseOrderId?.trim()?.takeIf { it.isNotEmpty() },
            purchaseVarianceReason = command.purchaseVarianceReason?.trim()?.takeIf { it.isNotEmpty() },
            unreceivedPaymentOverrideReason = command.unreceivedPaymentOverrideReason?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun authorize(command: SaveInvoiceCommand, creatingNew: Boolean) {
        val category = if (command.isSale) InvoiceCategory.SALE else InvoiceCategory.PURCHASE
        val allowed = if (creatingNew) {
            authorization.canPost(category)
        } else {
            val existing = command.existingInvoiceId?.let { store.getInvoiceById(it) }
                ?: throw IllegalStateException("الفاتورة المطلوب تعديلها غير موجودة")
            when (existing.lifecycleStatus) {
                InvoiceLifecycleStatus.DRAFT -> authorization.canPost(existing.category)
                InvoiceLifecycleStatus.POSTED -> authorization.canEditDescription(existing.category)
                InvoiceLifecycleStatus.VOID -> false
            }
        }
        if (allowed) return
        val action = if (creatingNew) "ترحيل" else "تعديل"
        val kind = if (command.isSale) "فاتورة بيع" else "فاتورة شراء"
        throw SecurityException("لا تملك صلاحية $action $kind")
    }

    private suspend fun saveEdit(
        command: SaveInvoiceCommand,
        validated: ValidatedInvoiceSave,
        draft: PreparedInvoiceDraft,
        actor: InvoiceActor,
        allowNegativeStock: Boolean,
    ): InvoiceSaveResult {
        val invoiceId = checkNotNull(command.existingInvoiceId)
        val snapshot = loadEditSnapshot(invoiceId)
        val nonFinancial = editPolicy.nonFinancialEditOrNull(snapshot, draft)
        if (nonFinancial != null) return saveNonFinancialEdit(command, actor, invoiceId, snapshot, nonFinancial)
        val inventoryContext = inventoryWriter.loadContext()
        val inventoryRequest = InvoiceInventoryWriteRequest(
            command, invoiceId, allowNegativeStock, inventoryContext, validated, actor.id, actor.name,
        )
        return saveFullEdit(command, draft, actor, snapshot, inventoryRequest)
    }

    private suspend fun loadEditSnapshot(invoiceId: String): InvoiceEditSnapshot {
        val oldInvoice = store.getInvoiceById(invoiceId)
        require(oldInvoice != null) { "الفاتورة المطلوب تعديلها غير موجودة" }
        require(oldInvoice.lifecycleStatus != InvoiceLifecycleStatus.VOID && !oldInvoice.voided) {
            "لا يمكن تعديل فاتورة ملغاة"
        }
        val oldItems = store.getInvoiceItems(invoiceId)
        return InvoiceEditSnapshot(oldInvoice, oldItems, store.getTotalPaid(invoiceId))
    }

    private suspend fun saveNonFinancialEdit(
        command: SaveInvoiceCommand,
        actor: InvoiceActor,
        invoiceId: String,
        snapshot: InvoiceEditSnapshot,
        edit: InvoiceNonFinancialEdit,
    ): InvoiceSaveResult {
        var duplicate = false
        transactionPort.inTransaction {
            duplicate = !claimWrite(command, invoiceId, InvoiceWriteOperation.UPDATE)
            if (!duplicate) {
                val oldInvoice = checkNotNull(snapshot.oldInvoice)
                val updated = edit.updatedInvoice.copy(lifecycleVersion = oldInvoice.lifecycleVersion + 1)
                check(store.updatePostedDescription(updated, oldInvoice.lifecycleVersion)) {
                    "CONFLICT: تم تعديل الفاتورة من عملية أخرى؛ أعد تحميلها ثم حاول مجددًا"
                }
                persistDueScheduleForEdit(command, updated, snapshot.totalPaid)
                persistIntegrationArtifacts(command, invoiceId, InvoiceIntegrationWriteKind.UPDATED)
                auditEdit(
                    command = command,
                    actor = actor,
                    invoiceId = invoiceId,
                    summary = "فاتورة #${oldInvoice.invoiceNumber} — تعديل وصفي آمن بعد الترحيل",
                    oldInvoice = oldInvoice,
                    newInvoice = updated,
                )
            }
        }
        if (!duplicate) {
            postCommitEffects.afterEdit(
                InvoiceEditEffects(command.organizationId, actor.id, invoiceId),
            )
        }
        return InvoiceSaveResult(invoiceId)
    }

    private suspend fun saveFullEdit(
        command: SaveInvoiceCommand,
        draft: PreparedInvoiceDraft,
        actor: InvoiceActor,
        snapshot: InvoiceEditSnapshot,
        request: InvoiceInventoryWriteRequest,
    ): InvoiceSaveResult {
        val invoiceId = checkNotNull(command.existingInvoiceId)
        val oldInvoice = requireNotNull(snapshot.oldInvoice)
        if (oldInvoice.lifecycleStatus == InvoiceLifecycleStatus.POSTED) {
            val requestedCategory = if (command.isSale) InvoiceCategory.SALE else InvoiceCategory.PURCHASE
            require(oldInvoice.category == requestedCategory) {
                "لا يمكن تغيير نوع الفاتورة أثناء التصحيح بعد الترحيل"
            }
            require(oldInvoice.clientId == command.clientId) { "لا يمكن تغيير عميل فاتورة مرحلة" }
            require(oldInvoice.status == draft.invoice.status) { "لا يمكن تغيير نوع تسوية فاتورة مرحلة" }
            val commissionAttributionChanged =
                oldInvoice.commissionMinor != draft.invoice.commissionMinor ||
                    oldInvoice.commissionBeneficiaryClientId != draft.invoice.commissionBeneficiaryClientId ||
                    oldInvoice.commissionSource != draft.invoice.commissionSource
            if (commissionAttributionChanged && !authorization.canManageCommission()) {
                throw SecurityException("لا تملك صلاحية إدارة العمولات")
            }
            if (oldInvoice.status == com.verto.app.feature.invoice.domain.model.InvoiceStatus.CLOSED_CREDIT) {
                val totalPaid = Money.fromLegacyDouble(snapshot.totalPaid)
                require(draft.invoice.totalAmountMinor >= totalPaid.amountMinor) {
                    "صافي الفاتورة بعد التعديل لا يمكن أن يقل عن المبلغ المسدد"
                }
            }
        } else {
            require(oldInvoice.lifecycleStatus == InvoiceLifecycleStatus.DRAFT) { "لا يمكن تعديل فاتورة ملغاة" }
        }
        var duplicate = false
        var inventoryResult = InvoiceInventoryWriteResult()
        transactionPort.inTransaction {
            duplicate = !claimWrite(command, invoiceId, InvoiceWriteOperation.UPDATE)
            if (!duplicate) {
                // DRAFT rows never own stock. A POSTED local sale/purchase owns stock, so v370 reverses
                // the old posting inside the same transaction before writing the corrected posting.
                if (oldInvoice.lifecycleStatus == InvoiceLifecycleStatus.POSTED) {
                    inventoryWriter.reverseForEdit(request, snapshot.oldItems, oldInvoice.isOwedToMe)
                }
                val createdBy = oldInvoice.createdBy.takeIf { it.isNotBlank() } ?: actor.id
                val updatedInvoice = draft.invoice.copy(
                    createdAt = oldInvoice.createdAt,
                    createdBy = createdBy,
                    lifecycleStatus = if (oldInvoice.lifecycleStatus == InvoiceLifecycleStatus.POSTED)
                        InvoiceLifecycleStatus.POSTED else draft.invoice.lifecycleStatus,
                    lifecycleVersion = oldInvoice.lifecycleVersion + 1,
                    postedAt = oldInvoice.postedAt.takeIf { it > 0L } ?: draft.invoice.postedAt,
                )
                val persistedLines = inventoryWriter.snapshotSaleCosts(draft.lines, command.isSale)
                store.updateInvoiceWithItems(updatedInvoice, persistedLines)
                inventoryResult = inventoryWriter.writeForEdit(request, persistedLines)
                paymentWriter.writeForEdit(InvoiceEditPaymentRequest(command, updatedInvoice, actor, invoiceId, oldInvoice))
                persistDueScheduleForEdit(command, updatedInvoice, snapshot.totalPaid)
                persistIntegrationArtifacts(command, invoiceId, InvoiceIntegrationWriteKind.UPDATED)
                auditInventoryEffects(command, actor, invoiceId, inventoryResult.revaluations, request.allowNegativeStock)
                auditEdit(
                    command = command,
                    actor = actor,
                    invoiceId = invoiceId,
                    summary = if (oldInvoice.lifecycleStatus == InvoiceLifecycleStatus.POSTED)
                        "فاتورة #${updatedInvoice.invoiceNumber} — تصحيح بعد الترحيل (بنود/خصم/عمولة)"
                    else "فاتورة #${updatedInvoice.invoiceNumber} — ${draft.description}",
                    oldInvoice = oldInvoice,
                    newInvoice = updatedInvoice,
                )
            }
        }
        if (!duplicate) {
            postCommitEffects.afterEdit(
                InvoiceEditEffects(command.organizationId, actor.id, invoiceId),
            )
        }
        return InvoiceSaveResult(invoiceId)
    }

    private suspend fun saveNew(
        command: SaveInvoiceCommand,
        validated: ValidatedInvoiceSave,
        draft: PreparedInvoiceDraft,
        actor: InvoiceActor,
        allowNegativeStock: Boolean,
    ): InvoiceSaveResult {
        val outcome = persistNewInvoice(
            command, validated, draft, actor, allowNegativeStock, inventoryWriter.loadContext()
        )
        if (!outcome.duplicate) {
            postCommitEffects.afterCreate(
                InvoiceCreateEffects(
                    organizationId = command.organizationId,
                    actorId = actor.id,
                ),
            )
        }
        return InvoiceSaveResult(
            outcome.savedId, outcome.inventoryResult.newItemsCreated, outcome.inventoryResult.itemsUpdated,
        )
    }

    private suspend fun persistNewInvoice(
        command: SaveInvoiceCommand, validated: ValidatedInvoiceSave, draft: PreparedInvoiceDraft,
        actor: InvoiceActor, allowNegativeStock: Boolean, inventoryContext: InvoiceInventoryContext
    ): NewInvoicePersistenceOutcome = transactionPort.inTransaction {
        val claim = store.claimWrite(
            InvoiceWriteIdentity(
                organizationId = command.organizationId,
                operation = InvoiceWriteOperation.CREATE,
                writeId = command.writeId,
                invoiceId = draft.invoice.id,
            ),
        )
        if (!claim.claimed) {
            val existing = requireNotNull(store.getInvoiceById(claim.invoiceId)) {
                "معرّف writeId مرتبط بفاتورة غير موجودة"
            }
            NewInvoicePersistenceOutcome(existing.id, existing.invoiceNumber, InvoiceInventoryWriteResult(), true)
        } else {
            persistClaimedNew(command, validated, draft, actor, allowNegativeStock, inventoryContext)
        }
    }

    private suspend fun persistClaimedNew(
        command: SaveInvoiceCommand, validated: ValidatedInvoiceSave, draft: PreparedInvoiceDraft,
        actor: InvoiceActor, allowNegativeStock: Boolean, inventoryContext: InvoiceInventoryContext
    ): NewInvoicePersistenceOutcome {
        val persistedLines = inventoryWriter.snapshotSaleCosts(draft.lines, command.isSale)
        val inserted = store.insertInvoiceWithItems(draft.invoice, persistedLines)
        check(inserted.first == draft.invoice.id) { "معرّف الفاتورة المدرج لا يطابق حارس الكتابة" }
        val savedId = inserted.first
        val finalInvoiceNumber = inserted.second
        val match = matchPurchaseCycleIfNeeded(
            command, draft.invoice.totalAmountMinor, persistedLines, actor, savedId,
        )
        authorizeInitialPurchasePaymentIfNeeded(
            command, draft.invoice.totalAmountMinor, match?.match?.payableAmountMinor, actor, savedId,
        )
        paymentWriter.writeForCreate(InvoiceCreatePaymentRequest(command, draft.invoice, actor, savedId))
        persistDueScheduleForCreate(command, draft.invoice, savedId)
        val inventoryResult = inventoryWriter.writeForCreate(
            InvoiceInventoryWriteRequest(
                command, savedId, allowNegativeStock, inventoryContext, validated, actor.id, actor.name,
            ),
            persistedLines,
        )
        persistIntegrationArtifacts(command, savedId, InvoiceIntegrationWriteKind.CREATED)
        auditInventoryEffects(command, actor, savedId, inventoryResult.revaluations, allowNegativeStock)
        auditCreate(command, actor, savedId, draft.invoice.copy(invoiceNumber = finalInvoiceNumber))
        return NewInvoicePersistenceOutcome(savedId, finalInvoiceNumber, inventoryResult, false)
    }

    private suspend fun matchPurchaseCycleIfNeeded(
        command: SaveInvoiceCommand,
        invoiceAmountMinor: Long,
        lines: List<com.verto.app.feature.invoice.domain.model.InvoiceLine>,
        actor: InvoiceActor,
        invoiceId: String,
    ): com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchAssessment? {
        val orderId = command.purchaseOrderId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(!command.isSale) { "أمر الشراء لا يمكن ربطه بفاتورة بيع" }
        val port = requireNotNull(purchaseCycle) { "PurchaseCycleInvoicePort غير مهيأ" }
        val varianceApproval = command.purchaseVarianceReason?.takeIf { it.isNotBlank() }
            ?.let { authorization.canOverridePurchaseVariance() } == true
        val assessment = port.assessSupplierInvoice(
            organizationId = command.organizationId,
            purchaseOrderId = orderId,
            supplierId = command.clientId,
            purchaseScope = command.purchaseScope,
            invoiceId = invoiceId,
            invoiceAmountMinor = invoiceAmountMinor,
            lines = lines.map { line ->
                PurchaseInvoiceCandidateLine(
                    invoiceItemId = line.id,
                    inventoryItemId = line.inventoryItemId,
                    itemName = line.itemName,
                    quantity = line.quantity,
                    unitPriceMinor = line.buyPriceMinor,
                )
            },
            quantityToleranceUnits = command.purchaseQuantityToleranceUnits,
            priceToleranceMinor = command.purchasePriceToleranceMinor,
            varianceReason = command.purchaseVarianceReason,
            approvedBy = actor.id.takeIf { varianceApproval },
            approvedByName = actor.name.takeIf { varianceApproval },
            writeId = command.writeId,
            matchedAt = command.requestedAt,
        )
        if (assessment.exceedsTolerance && !varianceApproval) {
            throw SecurityException("فرق المطابقة يتجاوز السماح ولا تملك صلاحية اعتماده")
        }
        port.persistSupplierInvoiceMatch(assessment)
        return assessment
    }

    private suspend fun authorizeInitialPurchasePaymentIfNeeded(
        command: SaveInvoiceCommand,
        invoiceAmountMinor: Long,
        payableMinor: Long?,
        actor: InvoiceActor,
        invoiceId: String,
    ) {
        if (payableMinor == null) return
        val effectiveRequested = when (command.paymentMode) {
            com.verto.app.feature.invoice.domain.model.InvoicePaymentMode.CASH -> invoiceAmountMinor
            com.verto.app.feature.invoice.domain.model.InvoicePaymentMode.CREDIT -> command.initialPayment.amountMinor
        }
        if (effectiveRequested <= payableMinor) return
        val reason = command.unreceivedPaymentOverrideReason?.trim().orEmpty()
        require(reason.isNotEmpty()) { "لا يمكن دفع قيمة كمية غير مستلمة دون Override موثق" }
        if (!authorization.canOverrideUnreceivedPurchasePayment()) {
            throw SecurityException("لا تملك صلاحية دفع كمية غير مستلمة")
        }
        requireNotNull(purchaseCycle).recordPaymentOverride(
            PurchasePaymentOverrideRecord(
                id = UUID.nameUUIDFromBytes("purchase-payment-override|${command.organizationId}|${command.writeId}".toByteArray()).toString(),
                organizationId = command.organizationId,
                invoiceId = invoiceId,
                paymentRequestId = UUID.nameUUIDFromBytes(
                    "invoice-create-payment|$invoiceId|${command.writeId}".toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                ).toString(),
                requestedAmountMinor = effectiveRequested,
                payableBeforeOverrideMinor = payableMinor,
                reason = reason,
                approvedBy = actor.id,
                approvedByName = actor.name,
                createdAt = command.requestedAt,
            )
        )
    }

    private suspend fun persistDueScheduleForCreate(
        command: SaveInvoiceCommand,
        invoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord,
        invoiceId: String,
    ) {
        if (command.paymentMode == com.verto.app.feature.invoice.domain.model.InvoicePaymentMode.CASH) {
            store.replaceDueInstallments(invoiceId, emptyList())
            return
        }
        val outstanding = Math.subtractExact(invoice.totalAmountMinor, command.initialPayment.amountMinor)
        require(outstanding >= 0L) { "الدفعة المقدمة أكبر من إجمالي الفاتورة" }
        val requested = if (command.dueInstallments.isNotEmpty()) command.dueInstallments else {
            require(command.dueDate > 0L) { "حدد تاريخ استحقاق أو جدول دفعات للفاتورة الآجلة" }
            listOf(com.verto.app.feature.invoice.domain.model.InvoiceDueInstallmentDraft(Money.ofMinor(outstanding, invoice.transactionCurrencyCode), command.dueDate))
        }
        store.replaceDueInstallments(invoiceId, materializeDueSchedule(command, invoice, requested, outstanding))
    }

    private suspend fun persistDueScheduleForEdit(
        command: SaveInvoiceCommand,
        invoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord,
        totalPaid: Double,
    ) {
        if (command.paymentMode == com.verto.app.feature.invoice.domain.model.InvoicePaymentMode.CASH) {
            store.replaceDueInstallments(invoice.id, emptyList())
            return
        }
        // Empty means preserve the existing contractual schedule during an unrelated edit.
        if (command.dueInstallments.isEmpty()) return
        val paidMinor = Money.fromLegacyDouble(totalPaid).amountMinor
        val outstanding = Math.subtractExact(invoice.totalAmountMinor, paidMinor)
        require(outstanding >= 0L) { "إجمالي الفاتورة المعدل أقل من المسدد" }
        store.replaceDueInstallments(
            invoice.id,
            materializeDueSchedule(command, invoice, command.dueInstallments, outstanding),
        )
    }

    private fun materializeDueSchedule(
        command: SaveInvoiceCommand,
        invoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord,
        requested: List<com.verto.app.feature.invoice.domain.model.InvoiceDueInstallmentDraft>,
        outstandingMinor: Long,
    ): List<InvoiceDueInstallment> {
        require(requested.isNotEmpty()) { "جدول الاستحقاق فارغ" }
        requested.forEach { row ->
            require(row.amount.isPositive()) { "مبلغ كل دفعة استحقاق يجب أن يكون أكبر من صفر" }
            require(row.dueDate > 0L) { "تاريخ كل دفعة استحقاق مطلوب" }
        }
        val totalScheduled = requested.fold(0L) { acc, row -> Math.addExact(acc, row.amount.amountMinor) }
        require(totalScheduled == outstandingMinor) {
            "مجموع دفعات الاستحقاق يجب أن يساوي المبلغ المتبقي"
        }
        val ordered = requested.sortedBy { it.dueDate }
        return ordered.mapIndexed { index, row ->
            InvoiceDueInstallment(
                id = UUID.nameUUIDFromBytes(
                    "invoice-due|${invoice.id}|${command.writeId}|${index + 1}".toByteArray(Charsets.UTF_8)
                ).toString(),
                invoiceId = invoice.id,
                sequence = index + 1,
                amountMinor = row.amount.amountMinor,
                currencyCode = invoice.transactionCurrencyCode,
                dueDate = row.dueDate,
                createdAt = command.requestedAt,
                writeId = command.writeId,
            )
        }
    }

    private suspend fun claimWrite(
        command: SaveInvoiceCommand,
        invoiceId: String,
        operation: InvoiceWriteOperation,
    ): Boolean {
        val claim = store.claimWrite(
            InvoiceWriteIdentity(
                organizationId = command.organizationId,
                operation = operation,
                writeId = command.writeId,
                invoiceId = invoiceId,
            ),
        )
        require(claim.invoiceId == invoiceId) {
            "writeId مستخدم مسبقاً لعملية $operation على فاتورة أخرى"
        }
        return claim.claimed
    }

    private suspend fun auditInventoryEffects(
        command: SaveInvoiceCommand,
        actor: InvoiceActor,
        invoiceId: String,
        revaluations: List<InvoiceInventoryRevaluation>,
        allowNegativeStock: Boolean,
    ) {
        for (event in revaluations) {
            auditLogger.logUpdate(
                table = AuditTable.INVENTORY,
                recordId = event.itemId,
                summary = "إعادة تقييم ${event.itemName} حسب آخر سعر شراء — فاتورة $invoiceId — رصيد سابق ${event.quantityBefore} — فرق ${event.revaluationDifferenceMinor}",
                oldValue = event.oldBuyPriceMinor.toString(),
                newValue = event.newBuyPriceMinor.toString(),
                employeeId = actor.id,
                employeeName = actor.name,
                sourceType = "INVOICE",
                sourceId = invoiceId,
                sourceVersion = 1,
                writeId = command.writeId,
            )
        }
        if (allowNegativeStock && command.isSale) {
            for (item in inventoryWriter.negativeReferencedItems(command)) {
                auditLogger.log(
                    action = AuditAction.UPDATE,
                    table = AuditTable.INVENTORY,
                    recordId = item.id,
                    summary = "بيع بمخزون سالب وفق السياسة المفعلة — ${item.name}: ${item.quantity}",
                    employeeId = actor.id,
                    employeeName = actor.name,
                    canUndo = false,
                    sourceType = "INVOICE",
                    sourceId = invoiceId,
                    sourceVersion = 1,
                    writeId = command.writeId,
                )
            }
        }
    }

    private suspend fun auditCreate(
        command: SaveInvoiceCommand,
        actor: InvoiceActor,
        invoiceId: String,
        invoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord,
    ) {
        auditLogger.logInsert(
            table = AuditTable.INVOICE,
            recordId = invoiceId,
            summary = "فاتورة #${invoice.invoiceNumber} — ${invoice.description}",
            newValue = invoice.toAuditJson(),
            employeeId = actor.id,
            employeeName = actor.name,
            sourceType = "INVOICE",
            sourceId = invoiceId,
            sourceVersion = 1,
            writeId = command.writeId,
        )
    }

    private suspend fun auditEdit(
        command: SaveInvoiceCommand,
        actor: InvoiceActor,
        invoiceId: String,
        summary: String,
        oldInvoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord?,
        newInvoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord
    ) {
        auditLogger.logUpdate(
            table = AuditTable.INVOICE,
            recordId = invoiceId,
            summary = summary,
            oldValue = oldInvoice?.toAuditJson() ?: "",
            newValue = newInvoice.toAuditJson(),
            employeeId = actor.id,
            employeeName = actor.name,
            sourceType = "INVOICE",
            sourceId = invoiceId,
            sourceVersion = 1,
            writeId = command.writeId,
        )
    }
    private suspend fun persistIntegrationArtifacts(
        command: SaveInvoiceCommand,
        invoiceId: String,
        writeKind: InvoiceIntegrationWriteKind,
    ) {
        atomicPersistenceCoordinator.persist(
            PersistInvoiceIntegrationCommand(
                writeId = command.writeId,
                organizationId = command.organizationId,
                invoiceId = invoiceId,
                clientId = command.clientId,
                writeKind = writeKind,
                isSale = command.isSale,
                companyClient = command.companyClient,
                maintenance = command.maintenance,
                occurredAt = command.requestedAt,
            ),
        ).getOrThrow()
    }
    private class NewInvoicePersistenceOutcome(
        val savedId: String, val finalInvoiceNumber: Int,
        val inventoryResult: InvoiceInventoryWriteResult, val duplicate: Boolean,
    ) {}
}
