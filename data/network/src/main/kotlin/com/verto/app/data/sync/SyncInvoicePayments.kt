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

    suspend fun SyncRuntime.pushPayments(orgId: String, userId: String) {
        val blocked = financiallyBlockedAggregateIds(orgId)
        val list = db.paymentDao().getDirtyPaymentsSync().filter { it.invoiceId !in blocked }  // F249
        if (list.isNotEmpty()) {
            supabase.postgrest["payments"].upsert(
                list.map { p ->
                PaymentDto(
                    id             = p.id,
                    organizationId = orgId,
                    createdBy      = userId,
                    invoiceId      = p.invoiceId,
                    clientId       = localClientIdToUuid(p.clientId, orgId),
                    amount         = p.amount.toRemoteDecimal(),
                    paymentCurrencyCode = p.paymentCurrencyCode,
                    supplierAmountMinor = p.supplierAmountMinor,
                    paymentExchangeRate = p.paymentExchangeRate,
                    paymentExchangeRateDirection = p.paymentExchangeRateDirection,
                    paymentExchangeRateTimestamp = p.paymentExchangeRateTimestamp,
                    paymentExchangeRateSource = p.paymentExchangeRateSource,
                    functionalCashAmountMinor = p.functionalCashAmountMinor,
                    historicalFunctionalAmountMinor = p.historicalFunctionalAmountMinor,
                    realizedFxDifferenceMinor = p.realizedFxDifferenceMinor,
                    legacyCurrencyStatus = p.legacyCurrencyStatus.name,
                    paymentMethod  = p.paymentMethod.name,
                    note           = p.note ?: "",
                    paidAt         = SupabaseDateParser.format(p.paidAt),
                    updatedAt      = SupabaseDateParser.format(System.currentTimeMillis()),
                    employeeId     = p.employeeId,    // SYNC-017
                    employeeName   = p.employeeName,  // SYNC-017
                    reversedPaymentId = p.reversedPaymentId   // Session 9
                )
                }
            ) { onConflict = "id" }
            db.paymentDao().markPaymentsClean(list.map { it.id })  // SYNC-012
        }

        val allocations = db.paymentDao().getAllPaymentAllocationsSync().filter { it.invoiceId !in blocked }
        if (allocations.isNotEmpty()) {
            supabase.postgrest["payment_allocations"].upsert(
                allocations.map { row ->
                    PaymentAllocationDto(
                        id = row.id,
                        organizationId = orgId,
                        paymentId = row.paymentId,
                        invoiceId = row.invoiceId,
                        allocatedTransactionAmountMinor = row.allocatedTransactionAmountMinor,
                        historicalFunctionalAmountMinor = row.historicalFunctionalAmountMinor,
                        realizedFxDifferenceMinor = row.realizedFxDifferenceMinor,
                        createdAt = SupabaseDateParser.format(row.createdAt),
                        writeId = row.writeId,
                    )
                }
            ) { onConflict = "id" }
        }

        val fxEvents = db.paymentDao().getAllRealizedFxEventsSync().filter { it.invoiceId !in blocked }
        if (fxEvents.isNotEmpty()) {
            supabase.postgrest["realized_fx_events"].upsert(
                fxEvents.map { row ->
                    RealizedFxEventDto(
                        id = row.id,
                        organizationId = orgId,
                        paymentId = row.paymentId,
                        invoiceId = row.invoiceId,
                        functionalCurrencyCode = row.functionalCurrencyCode,
                        historicalFunctionalAmountMinor = row.historicalFunctionalAmountMinor,
                        functionalCashAmountMinor = row.functionalCashAmountMinor,
                        differenceMinor = row.differenceMinor,
                        result = row.result,
                        occurredAt = SupabaseDateParser.format(row.occurredAt),
                        writeId = row.writeId,
                    )
                }
            ) { onConflict = "id" }
        }
    }


    suspend fun SyncRuntime.pullPayments(orgId: String) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_PAYMENTS)
        val pullStartedAt = System.currentTimeMillis()
        val blockedAggregates = financiallyBlockedAggregateIds(orgId)

        // SYNC-011: الفلتر التدريجي على updated_at (لا created_at) ليصل تعديل المبلغ
        val remote = supabase.postgrest["payments"].select {
            filter {
                eq("organization_id", orgId)
                if (lastPulledAt > 0L) gte("updated_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<PaymentDto>()

        // FK guard + F249 review gate: conflicted aggregates remain retrievable behind this cursor.
        val blockedReturned = remote.any { it.invoiceId in blockedAggregates }
        var immutableConflictDetected = false
        val existingInvoiceIds = db.invoiceDao().getAllInvoicesSync().map { it.id }.toSet()
        val existingClientIds  = db.clientDao().getAllClientsSyncForOrganization(orgId).map { it.id }.toSet()
        val validPayments = remote.filter {
            it.invoiceId in existingInvoiceIds && it.invoiceId !in blockedAggregates &&
                resolveClientId(it.clientId, orgId) in existingClientIds
        }

        // SYNC-011: احفظ الحقول المحلية (employeeId/employeeName — ليست في الـ DTO) قبل REPLACE
        val localById = db.paymentDao().getAllPaymentsSync().associateBy { it.id }

        validPayments.forEach { dto ->
            val local = localById[dto.id]
            if (SyncConflictPolicy.resolve(localDirty = local?.isDirty == true) == SyncConflictResolution.KEEP_LOCAL) {
                return@forEach
            }
            // F249: an existing payment is immutable financial history. A remote row that changes
            // money/method/reversal facts is not merged by updated_at; it is held for review.
            if (local != null && !paymentFinancialFactsMatch(local, dto)) {
                immutableConflictDetected = true
                db.invoiceDao().getLatestFinancialInboxForAggregate(orgId, dto.invoiceId)?.let { event ->
                    db.invoiceDao().updateFinancialInboxState(
                        eventId = event.eventId,
                        state = "REQUIRES_REVIEW",
                        reason = "immutable payment financial facts conflict for ${dto.id}",
                        appliedAt = null,
                    )
                }
                return@forEach
            }
            val remoteCurrencyKnown = dto.legacyCurrencyStatus == "KNOWN" &&
                dto.paymentCurrencyCode.isNotBlank() && dto.paymentExchangeRate.isNotBlank()
            val preserveLocalCurrency = !remoteCurrencyKnown && local?.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN
            val preserved = local.takeIf { preserveLocalCurrency }
            try {
                // Compatibility upsert is now allowed only after immutable financial facts matched.
                db.paymentDao().upsertPaymentFromRemote(
                    PaymentEntity(
                        id            = dto.id,
                        invoiceId     = dto.invoiceId,
                        clientId      = resolveClientId(dto.clientId, orgId),
                        amount        = dto.amount.toRemoteDouble(),
                        paymentCurrencyCode = preserved?.paymentCurrencyCode ?: dto.paymentCurrencyCode.uppercase(),
                        supplierAmountMinor = preserved?.supplierAmountMinor ?: dto.supplierAmountMinor.takeIf { it != 0L } ?: com.verto.app.money.Money.fromLegacyDouble(dto.amount.toRemoteDouble()).amountMinor,
                        paymentExchangeRate = preserved?.paymentExchangeRate ?: dto.paymentExchangeRate,
                        paymentExchangeRateDirection = preserved?.paymentExchangeRateDirection ?: dto.paymentExchangeRateDirection,
                        paymentExchangeRateTimestamp = preserved?.paymentExchangeRateTimestamp ?: dto.paymentExchangeRateTimestamp,
                        paymentExchangeRateSource = preserved?.paymentExchangeRateSource ?: dto.paymentExchangeRateSource,
                        functionalCashAmountMinor = preserved?.functionalCashAmountMinor ?: dto.functionalCashAmountMinor,
                        historicalFunctionalAmountMinor = preserved?.historicalFunctionalAmountMinor ?: dto.historicalFunctionalAmountMinor,
                        realizedFxDifferenceMinor = preserved?.realizedFxDifferenceMinor ?: dto.realizedFxDifferenceMinor,
                        legacyCurrencyStatus = if (preserveLocalCurrency) LegacyCurrencyStatus.KNOWN else runCatching { LegacyCurrencyStatus.valueOf(dto.legacyCurrencyStatus) }.getOrDefault(local?.legacyCurrencyStatus ?: LegacyCurrencyStatus.REVIEW_REQUIRED),
                        paymentMethod = runCatching { PaymentMethod.valueOf(dto.paymentMethod) }.getOrDefault(PaymentMethod.CASH),
                        note          = dto.note,
                        paidAt        = SupabaseDateParser.parse(dto.paidAt),
                        // SYNC-017: اقرأ من السيرفر، ولا تمسح بفراغ (يحمي فترة الانتقال قبل أول رفع)
                        employeeId    = dto.employeeId.ifBlank { local?.employeeId ?: "" },
                        employeeName  = dto.employeeName.ifBlank { local?.employeeName ?: "" },
                        // Session 9: ربط الدفعة العكسية بأصلها — لا تمسح بفراغ قبل انتشار العمود
                        reversedPaymentId = dto.reversedPaymentId ?: local?.reversedPaymentId,
                        isDirty       = false   // SYNC-012: مسحوب = نظيف
                    )
                )
            } catch (e: SQLiteConstraintException) {
                android.util.Log.w("SyncManager", "Payment skipped because dependency is not ready")
            }
        }

        val allocationRemote = supabase.postgrest["payment_allocations"].select {
            filter { eq("organization_id", orgId) }
        }.decodeList<PaymentAllocationDto>()
        if (allocationRemote.isNotEmpty()) {
            val paymentIds = db.paymentDao().getAllPaymentsSync().map { it.id }.toSet()
            val invoiceIds = db.invoiceDao().getAllInvoicesSync().map { it.id }.toSet()
            db.paymentDao().insertPaymentAllocationsFromRemote(
                allocationRemote.filter { it.paymentId in paymentIds && it.invoiceId in invoiceIds }.map { row ->
                    PaymentAllocationEntity(
                        id = row.id,
                        paymentId = row.paymentId,
                        invoiceId = row.invoiceId,
                        allocatedTransactionAmountMinor = row.allocatedTransactionAmountMinor,
                        historicalFunctionalAmountMinor = row.historicalFunctionalAmountMinor,
                        realizedFxDifferenceMinor = row.realizedFxDifferenceMinor,
                        createdAt = row.createdAt?.let(SupabaseDateParser::parse) ?: 0L,
                        sourceId = row.invoiceId,
                        writeId = row.writeId,
                    )
                }
            )
        }

        val fxRemote = supabase.postgrest["realized_fx_events"].select {
            filter { eq("organization_id", orgId) }
        }.decodeList<RealizedFxEventDto>()
        if (fxRemote.isNotEmpty()) {
            val paymentIds = db.paymentDao().getAllPaymentsSync().map { it.id }.toSet()
            val invoiceIds = db.invoiceDao().getAllInvoicesSync().map { it.id }.toSet()
            db.paymentDao().insertRealizedFxEventsFromRemote(
                fxRemote.filter { it.paymentId in paymentIds && it.invoiceId in invoiceIds }.map { row ->
                    RealizedFxEventEntity(
                        id = row.id,
                        paymentId = row.paymentId,
                        invoiceId = row.invoiceId,
                        functionalCurrencyCode = row.functionalCurrencyCode,
                        historicalFunctionalAmountMinor = row.historicalFunctionalAmountMinor,
                        functionalCashAmountMinor = row.functionalCashAmountMinor,
                        differenceMinor = row.differenceMinor,
                        result = row.result,
                        occurredAt = row.occurredAt?.let(SupabaseDateParser::parse) ?: 0L,
                        sourceId = row.paymentId,
                        writeId = row.writeId,
                    )
                }
            )
        }

        if (blockedAggregates.isEmpty() && !blockedReturned && !immutableConflictDetected) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_PAYMENTS, pullStartedAt)
        }
    }

/** F249: posted payment money/method/reversal fields are immutable across devices. */
private fun paymentFinancialFactsMatch(local: PaymentEntity, remote: PaymentDto): Boolean {
    val remoteAmountMinor = com.verto.app.money.Money.fromLegacyDouble(remote.amount.toRemoteDouble()).amountMinor
    if (local.amountMinor != remoteAmountMinor) return false
    if (local.paymentMethod.name != remote.paymentMethod) return false
    if (local.reversedPaymentId != remote.reversedPaymentId) return false

    // Old clients can legitimately lack the currency truth fields during the additive rollout.
    val remoteCurrencyKnown = remote.legacyCurrencyStatus == "KNOWN" &&
        remote.paymentCurrencyCode.isNotBlank() && remote.paymentExchangeRate.isNotBlank()
    if (!remoteCurrencyKnown) return true

    return local.paymentCurrencyCode.equals(remote.paymentCurrencyCode, ignoreCase = true) &&
        local.supplierAmountMinor == remote.supplierAmountMinor &&
        local.paymentExchangeRate == remote.paymentExchangeRate &&
        local.paymentExchangeRateDirection == remote.paymentExchangeRateDirection &&
        local.functionalCashAmountMinor == remote.functionalCashAmountMinor &&
        local.historicalFunctionalAmountMinor == remote.historicalFunctionalAmountMinor &&
        local.realizedFxDifferenceMinor == remote.realizedFxDifferenceMinor
}

