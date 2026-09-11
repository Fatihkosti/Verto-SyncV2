package com.verto.app.data.sync

import android.database.sqlite.SQLiteConstraintException
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.AppDatabase.Companion.CASH_CLIENT_UUID
import com.verto.app.data.local.AppDatabase.Companion.CASH_SUPPLIER_UUID
import com.verto.app.data.local.entity.*
import java.util.UUID
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.SupabaseDateParser
import com.verto.app.utils.SearchTextNormalizer
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

    suspend fun SyncRuntime.pushInvoices(orgId: String, userId: String) {
        // SYNC-012: ارفع الفواتير المتسخة فقط
        val blocked = financiallyBlockedAggregateIds(orgId)
        val list = db.invoiceDao().getDirtyInvoicesSync().filter { it.id !in blocked }
        if (list.isEmpty()) return

        supabase.postgrest["invoices"].upsert(
            list.map { inv ->
                InvoiceDto(
                    id             = inv.id,
                    organizationId = orgId,
                    createdBy      = inv.createdBy.ifBlank { userId },
                    clientId       = localClientIdToUuid(inv.clientId, orgId),
                    supplierInvoiceReference = inv.supplierInvoiceReference,
                    supplierInvoiceReferenceNormalized = inv.supplierInvoiceReferenceNormalized,
                    invoiceNumber  = inv.invoiceNumber,
                    type           = inv.type.name,
                    category       = inv.category.name,
                    description    = inv.description,
                    totalAmount    = inv.totalAmount.toRemoteDecimal(),
                    transactionCurrencyCode = inv.transactionCurrencyCode,
                    functionalCurrencyCode = inv.functionalCurrencyCode,
                    transactionAmountMinor = inv.transactionAmountMinor,
                    invoiceExchangeRateSnapshot = inv.invoiceExchangeRateSnapshot,
                    exchangeRateDirection = inv.exchangeRateDirection,
                    exchangeRateTimestamp = inv.exchangeRateTimestamp,
                    exchangeRateSource = inv.exchangeRateSource,
                    functionalAmountAtRecognitionMinor = inv.functionalAmountAtRecognitionMinor,
                    legacyCurrencyStatus = inv.legacyCurrencyStatus.name,
                    dueDate        = if (inv.dueDate > 0) SupabaseDateParser.format(inv.dueDate) else null,
                    notes          = inv.notes,
                    isOwedToMe     = inv.isOwedToMe,
                    imageUrl       = inv.imageUri ?: "",
                    discount       = inv.discount.toBigDecimal(),
                    discountMinor  = inv.discountMinor,
                    commission     = inv.commission.toBigDecimal(),
                    commissionBeneficiaryClientId = inv.commissionBeneficiaryClientId?.let { localClientIdToUuid(it, orgId) },
                    commissionSource = inv.commissionSource,
                    createdAt      = SupabaseDateParser.format(inv.createdAt),
                    updatedAt      = SupabaseDateParser.format(System.currentTimeMillis()),
                    status         = inv.status.name,
                    shipmentId     = inv.shipmentId,  // SYNC-017
                    purchaseOrderId = inv.purchaseOrderId,
                    purchaseScope  = inv.purchaseScope.name,
                    lifecycleStatus = inv.lifecycleStatus.name,
                    lifecycleVersion = inv.lifecycleVersion,
                    postedAt = inv.postedAt,
                    voidedAt = inv.voidedAt,
                    voidReason = inv.voidReason,
                    voidWriteId = inv.voidWriteId,
                    voided = inv.voided
                )
            }
        ) {
            onConflict    = "id"
            defaultToNull = false
        }

        // SYNC-012: صفِّر العلم للفواتير المرفوعة بعد النجاح
        db.invoiceDao().markInvoicesClean(list.map { it.id })
    }


    /**
     * Incremental invoice pull. `voided` is a financial/business state and is intentionally distinct
     * from physical deletion. Missing rows are UNKNOWN_NOT_RETURNED and are never deleted locally.
     */
    suspend fun SyncRuntime.pullInvoices(orgId: String, alreadyDeletedIds: Set<String> = emptySet()) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_INVOICES)
        val pullStartedAt = System.currentTimeMillis()
        val blockedAggregates = financiallyBlockedAggregateIds(orgId)
        var lifecycleConflictDetected = false

        val remote = supabase.postgrest["invoices"].select {
            filter {
                eq("organization_id", orgId)
                if (lastPulledAt > 0L) gte("updated_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<InvoiceDto>()
        if (remote.isEmpty()) {
            if (blockedAggregates.isEmpty()) {
                userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_INVOICES, pullStartedAt)
            }
            return
        }

        val existingMap = db.invoiceDao().getAllInvoicesSync().associateBy { it.id }

        // Bug-3 Fix (phase 2): never re-insert invoices that were deleted locally.
        // pushInvoiceDeletions() may have failed (network/RLS) so the invoice could
        // still be in Supabase. Without this guard, pullInvoices() would see it as
        // "new" (not in existingMap because it was deleted from Room) and re-insert it.
        // alreadyDeletedIds: snapshot المُلتقَط في fullSync() قبل push — يحمي حتى لو نُظِّفت القائمة
        val pendingDeletions = userPrefs.getPendingInvoiceDeletions() + alreadyDeletedIds

        // FK guard: جلب معرفات العملاء الموجودة محلياً لتفادي FK violation عند إدراج فواتير جديدة
        val existingClientIds = db.clientDao().getAllClientsSyncForOrganization(orgId).map { it.id }.toSet()

        val blockedReturned = remote.any { it.id in blockedAggregates }
        val (existingDtos, newDtos) = remote
            .filter { it.id !in pendingDeletions && it.id !in blockedAggregates }
            .partition { it.id in existingMap }

        // تحديث الفواتير الموجودة — نأخذ status من السيرفر لدعم multi-device
        existingDtos.forEach { dto ->
            val local        = checkNotNull(existingMap[dto.id])

            // F249: financial lifecycle/version conflicts never use Last-Write-Wins.
            val remoteLifecycle = if (dto.voided) {
                "VOID"
            } else {
                dto.lifecycleStatus.uppercase().takeIf { it == "DRAFT" || it == "POSTED" || it == "VOID" }
                    ?: "POSTED"
            }
            val remoteVersion = dto.lifecycleVersion.coerceAtLeast(1)
            when (FinancialAggregateConflictPolicy.resolve(
                localLifecycle = local.lifecycleStatus.name,
                localVersion = local.lifecycleVersion,
                localDirty = local.isDirty,
                remoteLifecycle = remoteLifecycle,
                remoteVersion = remoteVersion,
            )) {
                FinancialAggregateConflictPolicy.Decision.KEEP_LOCAL -> return@forEach
                FinancialAggregateConflictPolicy.Decision.REQUIRES_REVIEW -> {
                    lifecycleConflictDetected = true
                    db.invoiceDao().getLatestFinancialInboxForAggregate(orgId, dto.id)?.let { event ->
                        db.invoiceDao().updateFinancialInboxState(
                            eventId = event.eventId,
                            state = "REQUIRES_REVIEW",
                            reason = "invoice lifecycle/version conflict local=${local.lifecycleStatus.name}:${local.lifecycleVersion} remote=$remoteLifecycle:$remoteVersion",
                            appliedAt = null,
                        )
                    }
                    return@forEach
                }
                FinancialAggregateConflictPolicy.Decision.APPLY_REMOTE -> Unit
            }

            val remoteStatus = runCatching { InvoiceStatus.valueOf(dto.status) }
                .getOrElse { local.status }   // fallback للمحلي عند قيمة غير معروفة

            // Bug-2 Fix: منع تراجع CLOSED_CREDIT إلى CLOSED_CASH
            // إذا كانت الفاتورة آجلاً محلياً والسيرفر يقول كاش (بيانات قديمة/خاطئة)، احتفظ بالآجل.
            // الانتقال الصحيح (كاش → آجل على جهاز آخر) لا يزال يعمل.
            val effectiveStatus = if (
                local.status == InvoiceStatus.CLOSED_CREDIT &&
                remoteStatus == InvoiceStatus.CLOSED_CASH
            ) local.status else remoteStatus

            db.invoiceDao().updateInvoiceCoreFields(
                id                   = dto.id,
                status               = effectiveStatus.name,
                totalAmount          = dto.totalAmount.toRemoteDouble(),
                description          = dto.description,
                notes                = dto.notes,
                dueDate              = if (dto.dueDate != null) SupabaseDateParser.parse(dto.dueDate) else 0L,
                imageUri             = dto.imageUrl,
                category             = if (dto.category.isNotBlank()) dto.category else local.category.name,
                isOwedToMe           = dto.isOwedToMe,
                invoiceNumber        = dto.invoiceNumber,
                invoiceNumberSearch  = SearchTextNormalizer.identifier(dto.invoiceNumber.toString()),
                notifyDaysBefore     = dto.notifyDaysBefore,
                notifyRepeatDays     = dto.notifyRepeatDays,
                notificationsEnabled = dto.notificationsEnabled,
                createdAt            = if (dto.createdAt != null) SupabaseDateParser.parse(dto.createdAt) else local.createdAt,
                discount             = dto.discount.toDouble(),
                commission           = dto.commission.toDouble(),
                commissionBeneficiaryClientId = dto.commissionBeneficiaryClientId?.let { resolveClientId(it, orgId) },
                commissionSource     = dto.commissionSource,
                createdBy            = dto.createdBy ?: "",
                lifecycleStatus       = remoteLifecycle,
                lifecycleVersion      = dto.lifecycleVersion.coerceAtLeast(1),
                postedAt              = dto.postedAt,
                voidedAt              = dto.voidedAt,
                voidReason            = dto.voidReason,
                voidWriteId           = dto.voidWriteId,
                voided                = dto.voided
            )
            // SYNC-017: حدّث ربط الشحنة فقط عند توفّر قيمة على السيرفر (لا تمسح بفراغ)
            if (dto.shipmentId != null) {
                db.invoiceDao().updateInvoiceShipmentId(dto.id, dto.shipmentId)
            }
            val remotePurchaseScope = runCatching { PurchaseScope.valueOf(dto.purchaseScope) }.getOrElse { local.purchaseScope }
            db.invoiceDao().updateInvoicePurchaseScope(dto.id, remotePurchaseScope.name)
            db.invoiceDao().updateInvoicePurchaseCycleLink(
                id = dto.id,
                supplierInvoiceReference = dto.supplierInvoiceReference,
                supplierInvoiceReferenceNormalized = dto.supplierInvoiceReferenceNormalized,
                purchaseOrderId = dto.purchaseOrderId,
            )
            if (dto.transactionCurrencyCode.isNotBlank() && dto.functionalCurrencyCode.isNotBlank() && dto.invoiceExchangeRateSnapshot.isNotBlank()) {
                db.invoiceDao().updateInvoiceCurrencySnapshot(
                    id = dto.id,
                    transactionCurrencyCode = dto.transactionCurrencyCode.uppercase(),
                    functionalCurrencyCode = dto.functionalCurrencyCode.uppercase(),
                    transactionAmountMinor = dto.transactionAmountMinor,
                    invoiceExchangeRateSnapshot = dto.invoiceExchangeRateSnapshot,
                    exchangeRateDirection = dto.exchangeRateDirection,
                    exchangeRateTimestamp = dto.exchangeRateTimestamp,
                    exchangeRateSource = dto.exchangeRateSource,
                    functionalAmountAtRecognitionMinor = dto.functionalAmountAtRecognitionMinor,
                    legacyCurrencyStatus = runCatching { LegacyCurrencyStatus.valueOf(dto.legacyCurrencyStatus) }.getOrDefault(LegacyCurrencyStatus.REVIEW_REQUIRED).name,
                )
            }
        }

        // إدراج الفواتير الجديدة (IGNORE — لا CASCADE على invoice_items)
        // FK guard: تجاهل الفواتير التي عميلها غير موجود محلياً بعد
        val validNewDtos = newDtos.filter { resolveClientId(it.clientId, orgId) in existingClientIds }
        if (validNewDtos.isNotEmpty()) {
            // FIX-5.4: معالجة تعارض invoiceNumber — قد يُنشئ جهازان فاتورتين بنفس الرقم
            val existingNumbers = existingMap.values.mapTo(mutableSetOf()) { it.invoiceNumber }
            var maxNumber = existingNumbers.maxOrNull() ?: 0

            db.invoiceDao().insertInvoicesFromRemote(
                validNewDtos.map { dto ->
                    val category = runCatching {
                        InvoiceCategory.valueOf(dto.category)
                    }.getOrElse {
                        if (dto.isOwedToMe) InvoiceCategory.SALE else InvoiceCategory.PURCHASE
                    }
                    val resolvedStatus = runCatching {
                        InvoiceStatus.valueOf(dto.status)
                    }.getOrElse { InvoiceStatus.CLOSED_CASH }

                    var invoiceNum = dto.invoiceNumber
                    if (invoiceNum in existingNumbers) {
                        maxNumber++
                        invoiceNum = maxNumber
                        android.util.Log.w("SyncManager", "Invoice number conflict resolved")
                    }
                    existingNumbers.add(invoiceNum)

                    InvoiceEntity(
                        id            = dto.id,
                        invoiceNumber = invoiceNum,
                        clientId      = resolveClientId(dto.clientId, orgId),
                        organizationId = orgId,  // FIX-02
                        supplierInvoiceReference = dto.supplierInvoiceReference,
                        supplierInvoiceReferenceNormalized = dto.supplierInvoiceReferenceNormalized,
                        type          = runCatching { InvoiceType.valueOf(dto.type) }.getOrDefault(InvoiceType.GOODS),
                        category      = category,
                        description   = dto.description,
                        totalAmount   = dto.totalAmount.toRemoteDouble(),
                        transactionCurrencyCode = dto.transactionCurrencyCode.uppercase(),
                        functionalCurrencyCode = dto.functionalCurrencyCode.uppercase(),
                        transactionAmountMinor = dto.transactionAmountMinor.takeIf { it != 0L } ?: dto.totalAmount.toRemoteDouble().let { com.verto.app.money.Money.fromLegacyDouble(it).amountMinor },
                        invoiceExchangeRateSnapshot = dto.invoiceExchangeRateSnapshot,
                        exchangeRateDirection = dto.exchangeRateDirection,
                        exchangeRateTimestamp = dto.exchangeRateTimestamp,
                        exchangeRateSource = dto.exchangeRateSource,
                        functionalAmountAtRecognitionMinor = dto.functionalAmountAtRecognitionMinor,
                        legacyCurrencyStatus = if (dto.transactionCurrencyCode.isBlank() && dto.purchaseScope == PurchaseScope.INTERNATIONAL.name) LegacyCurrencyStatus.UNKNOWN else runCatching { LegacyCurrencyStatus.valueOf(dto.legacyCurrencyStatus) }.getOrDefault(LegacyCurrencyStatus.REVIEW_REQUIRED),
                        dueDate       = if (dto.dueDate != null) SupabaseDateParser.parse(dto.dueDate) else 0L,
                        notes         = dto.notes,
                        isOwedToMe    = dto.isOwedToMe,
                        imageUri      = dto.imageUrl,
                        createdAt     = SupabaseDateParser.parse(dto.createdAt),
                        status        = resolvedStatus,
                        discount      = dto.discount.toDouble(),
                        discountMinor = dto.discountMinor.takeIf { it != 0L } ?: com.verto.app.money.Money.fromLegacyDouble(dto.discount.toDouble()).amountMinor,
                        commission    = dto.commission.toDouble(),
                        commissionBeneficiaryClientId = dto.commissionBeneficiaryClientId?.let { resolveClientId(it, orgId) },
                        commissionSource = dto.commissionSource,
                        shipmentId    = dto.shipmentId,  // SYNC-017
                        purchaseOrderId = dto.purchaseOrderId,
                        purchaseScope = runCatching { PurchaseScope.valueOf(dto.purchaseScope) }.getOrDefault(PurchaseScope.LOCAL),
                        createdBy     = dto.createdBy ?: "",
                        lifecycleStatus = runCatching { InvoiceLifecycleStatus.valueOf(if (dto.voided) "VOID" else dto.lifecycleStatus) }.getOrDefault(InvoiceLifecycleStatus.POSTED),
                        lifecycleVersion = dto.lifecycleVersion.coerceAtLeast(1),
                        postedAt = dto.postedAt,
                        voidedAt = dto.voidedAt,
                        voidReason = dto.voidReason,
                        voidWriteId = dto.voidWriteId,
                        voided = dto.voided,
                        isDirty = false
                    )
                }
            )
        }

        // Keep the legacy cursor behind any unresolved financial aggregate so review can later
        // choose the server version without that row having disappeared behind updated_at.
        if (blockedAggregates.isEmpty() && !blockedReturned && !lifecycleConflictDetected) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_INVOICES, pullStartedAt)
        }
    }

