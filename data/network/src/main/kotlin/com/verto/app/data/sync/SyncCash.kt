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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

    suspend fun SyncRuntime.pushBudgets(orgId: String) {
        val list = db.budgetDao().getAllBudgetsSync()
        if (list.isEmpty()) return

        supabase.postgrest["budgets"].upsert(
            list.map { b ->
                BudgetDto(
                    id             = b.id,
                    organizationId = orgId,
                    periodType     = b.periodType.name,
                    periodStart    = SupabaseDateParser.format(b.periodStart),
                    periodEnd      = SupabaseDateParser.format(b.periodEnd),
                    budgetType     = b.budgetType.name,
                    category       = b.category,
                    targetAmount   = b.targetAmount.toRemoteDecimal(),
                    note           = b.note,
                    isActive       = b.isActive,
                    createdAt      = SupabaseDateParser.format(b.createdAt),
                    updatedAt      = SupabaseDateParser.format(System.currentTimeMillis())
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pushBudgetDeletions(orgId: String) {
        val pendingIds = userPrefs.getPendingBudgetDeletions()
        if (pendingIds.isEmpty()) return

        val failed = mutableListOf<String>()
        pendingIds.forEach { id ->
            runCatching {
                supabase.postgrest["budgets"].delete {
                    filter {
                        eq("id", id)
                        eq("organization_id", orgId)
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("SyncManager", "Remote deletion failed")
                failed += "$id (${e.message?.take(80)})"
            }
            if (isGoneFromSupabase("budgets", id, orgId)) {
                userPrefs.removePendingBudgetDeletion(id)
            }
        }
        if (failed.isNotEmpty()) error("لم يُحذف من Supabase: ${failed.joinToString(", ")}")
    }

    suspend fun SyncRuntime.pullBudgets(orgId: String, alreadyDeletedIds: Set<String> = emptySet()) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_BUDGETS)
        val pullStartedAt = System.currentTimeMillis()

        val remote = supabase.postgrest["budgets"].select {
            filter {
                eq("organization_id", orgId)
                if (lastPulledAt > 0L) gte("updated_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<BudgetDto>()
        if (remote.isEmpty()) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_BUDGETS, pullStartedAt)
            return
        }

        val pendingDeletions = userPrefs.getPendingBudgetDeletions() + alreadyDeletedIds
        remote.filter { it.id !in pendingDeletions }.forEach { dto ->
            db.budgetDao().insert(
                BudgetEntity(
                    id          = dto.id,
                    periodType  = runCatching { BudgetPeriodType.valueOf(dto.periodType) }.getOrDefault(BudgetPeriodType.MONTHLY),
                    periodStart = dto.periodStart?.let { SupabaseDateParser.parse(it) } ?: 0L,
                    periodEnd   = dto.periodEnd?.let { SupabaseDateParser.parse(it) } ?: 0L,
                    budgetType  = runCatching { BudgetType.valueOf(dto.budgetType) }.getOrDefault(BudgetType.SALES_TARGET),
                    category    = dto.category,
                    targetAmount = dto.targetAmount.toRemoteDouble(),
                    note        = dto.note,
                    isActive    = dto.isActive,
                    createdAt   = dto.createdAt?.let { SupabaseDateParser.parse(it) } ?: System.currentTimeMillis(),
                    updatedAt   = dto.updatedAt?.let { SupabaseDateParser.parse(it) } ?: System.currentTimeMillis()
                )
            )
        }

        userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_BUDGETS, pullStartedAt)
    }

    suspend fun SyncRuntime.pushCostAllocations(orgId: String) {
        val local        = db.costAllocationDao().getAllSync()
        val localItemIds = db.inventoryDao().getAllItemsSync().mapTo(mutableSetOf()) { it.id }
        val localIds     = local.mapTo(mutableSetOf()) { it.id }

        runCatching {
            val remote = supabase.postgrest["cost_allocations"]
                .select { filter { eq("organization_id", orgId) } }
                .decodeList<CostAllocationDto>()
            val stale = remote.filter { it.itemId in localItemIds && it.id !in localIds }.map { it.id }
            if (stale.isNotEmpty()) {
                supabase.postgrest["cost_allocations"].delete { filter { isIn("id", stale) } }
            }
        }.onFailure { e ->
            android.util.Log.e("SyncManager", "Orphan cost allocation cleanup failed")
        }

        if (local.isEmpty()) return
        supabase.postgrest["cost_allocations"].upsert(
            local.map { a ->
                CostAllocationDto(
                    id               = a.id,
                    organizationId   = orgId,
                    itemId           = a.itemId,
                    sourceType       = a.sourceType.name,
                    sourceId         = a.sourceId,
                    allocatedAmount  = a.allocatedAmount.toRemoteDecimal(),
                    perUnitCost      = a.perUnitCost.toRemoteDecimal(),
                    quantityAffected = a.quantityAffected,
                    method           = a.method.name,
                    note             = a.note
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pullCostAllocations(orgId: String) {
        val remote = supabase.postgrest["cost_allocations"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<CostAllocationDto>()

        val existingItemIds = db.inventoryDao().getAllItemsSync().mapTo(mutableSetOf()) { it.id }
        val valid = remote.filter { it.itemId in existingItemIds }
        if (valid.isEmpty()) return

        db.costAllocationDao().upsertAllFromRemote(
            valid.map { dto ->
                CostAllocationEntity(
                    id               = dto.id,
                    itemId           = dto.itemId,
                    sourceType       = runCatching { CostAllocationSource.valueOf(dto.sourceType) }.getOrDefault(CostAllocationSource.SHIPMENT_COST),
                    sourceId         = dto.sourceId,
                    allocatedAmount  = dto.allocatedAmount.toRemoteDouble(),
                    perUnitCost      = dto.perUnitCost.toRemoteDouble(),
                    quantityAffected = dto.quantityAffected,
                    method           = runCatching { CostAllocationMethod.valueOf(dto.method) }.getOrDefault(CostAllocationMethod.BY_QUANTITY),
                    note             = dto.note,
                    createdAt        = System.currentTimeMillis()
                )
            }
        )

        val pulledItemIds = valid.mapTo(mutableSetOf()) { it.itemId }
        val remoteIds     = valid.mapTo(mutableSetOf()) { it.id }
        val staleLocal = db.costAllocationDao().getAllSync()
            .filter { it.itemId in pulledItemIds && it.id !in remoteIds }
            .map { it.id }
        if (staleLocal.isNotEmpty()) {
            db.costAllocationDao().deleteByIds(staleLocal)
        }
    }

    suspend fun SyncRuntime.pushCashReconciliations(orgId: String) {
        val list = db.cashReconciliationDao().getAllSessionsSync()
        if (list.isEmpty()) return

        supabase.postgrest["cash_reconciliation_sessions"].upsert(
            list.map { s ->
                CashReconciliationDto(
                    id                   = s.id,
                    organizationId       = orgId,
                    employeeId           = s.employeeId,
                    employeeName         = s.employeeName,
                    openingBalance       = s.openingBalance.toRemoteDecimal(),
                    totalSales           = s.totalSales.toRemoteDecimal(),
                    totalRefunds         = s.totalRefunds.toRemoteDecimal(),
                    totalCashIn          = s.totalCashIn.toRemoteDecimal(),
                    totalCashOut         = s.totalCashOut.toRemoteDecimal(),
                    expectedBalance      = s.expectedBalance.toRemoteDecimal(),
                    actualCountedBalance = s.actualCountedBalance.toRemoteDecimal(),
                    variance             = s.variance.toRemoteDecimal(),
                    varianceReason       = s.varianceReason,
                    status               = s.status.name,
                    startedAt            = SupabaseDateParser.format(s.startedAt),
                    endedAt              = s.endedAt?.let { SupabaseDateParser.format(it) },
                    notes                = s.notes
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pushReconciliationDeletions(orgId: String) {
        val pendingIds = userPrefs.getPendingReconciliationDeletions()
        if (pendingIds.isEmpty()) return

        val failed = mutableListOf<String>()
        pendingIds.forEach { id ->
            runCatching {
                // CASCADE سيرفرياً يحذف cash_denominations التابعة
                supabase.postgrest["cash_reconciliation_sessions"].delete {
                    filter {
                        eq("id", id)
                        eq("organization_id", orgId)
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("SyncManager", "Remote deletion failed")
                failed += "$id (${e.message?.take(80)})"
            }
            if (isGoneFromSupabase("cash_reconciliation_sessions", id, orgId)) {
                userPrefs.removePendingReconciliationDeletion(id)
            }
        }
        if (failed.isNotEmpty()) error("لم يُحذف من Supabase: ${failed.joinToString(", ")}")
    }

    suspend fun SyncRuntime.pullCashReconciliations(orgId: String, alreadyDeletedIds: Set<String> = emptySet()) {
        val remote = supabase.postgrest["cash_reconciliation_sessions"].select {
            filter { eq("organization_id", orgId) }
        }.decodeList<CashReconciliationDto>()

        val pendingDeletions = userPrefs.getPendingReconciliationDeletions() + alreadyDeletedIds
        val valid = remote.filter { it.id !in pendingDeletions }
        if (valid.isEmpty()) return

        valid.forEach { dto ->
            db.cashReconciliationDao().upsertSessionFromRemote(
                CashReconciliationEntity(
                    id                   = dto.id,
                    employeeId           = dto.employeeId,
                    employeeName         = dto.employeeName,
                    openingBalance       = dto.openingBalance.toRemoteDouble(),
                    totalSales           = dto.totalSales.toRemoteDouble(),
                    totalRefunds         = dto.totalRefunds.toRemoteDouble(),
                    totalCashIn          = dto.totalCashIn.toRemoteDouble(),
                    totalCashOut         = dto.totalCashOut.toRemoteDouble(),
                    expectedBalance      = dto.expectedBalance.toRemoteDouble(),
                    actualCountedBalance = dto.actualCountedBalance.toRemoteDouble(),
                    variance             = dto.variance.toRemoteDouble(),
                    varianceReason       = dto.varianceReason,
                    status               = runCatching { ReconciliationStatus.valueOf(dto.status) }.getOrDefault(ReconciliationStatus.OPEN),
                    startedAt            = dto.startedAt?.let { SupabaseDateParser.parse(it) } ?: System.currentTimeMillis(),
                    endedAt              = dto.endedAt?.let { SupabaseDateParser.parse(it) },
                    notes                = dto.notes
                )
            )
        }
        // v141: absence from a pull is not a delete signal. Authoritative deletion requires a tombstone.
    }

    suspend fun SyncRuntime.pushCashDenominations(orgId: String) {
        val local        = db.cashReconciliationDao().getAllDenominationsSync()
        val localReconIds = db.cashReconciliationDao().getAllSessionsSync().mapTo(mutableSetOf()) { it.id }
        val localIds     = local.mapTo(mutableSetOf()) { it.id }

        runCatching {
            val remote = supabase.postgrest["cash_denominations"]
                .select { filter { eq("organization_id", orgId) } }
                .decodeList<CashDenominationDto>()
            val stale = remote.filter { it.reconciliationId in localReconIds && it.id !in localIds }.map { it.id }
            if (stale.isNotEmpty()) {
                supabase.postgrest["cash_denominations"].delete { filter { isIn("id", stale) } }
            }
        }.onFailure { e ->
            android.util.Log.e("SyncManager", "Orphan cash category cleanup failed")
        }

        if (local.isEmpty()) return
        supabase.postgrest["cash_denominations"].upsert(
            local.map { d ->
                CashDenominationDto(
                    id                = d.id,
                    organizationId    = orgId,
                    reconciliationId  = d.reconciliationId,
                    denominationValue = d.denominationValue.toRemoteDecimal(),
                    count             = d.count,
                    subtotal          = d.subtotal.toRemoteDecimal(),
                    isCoin            = d.isCoin
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pullCashDenominations(orgId: String) {
        val remote = supabase.postgrest["cash_denominations"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<CashDenominationDto>()

        val existingReconIds = db.cashReconciliationDao().getAllSessionsSync().mapTo(mutableSetOf()) { it.id }
        val valid = remote.filter { it.reconciliationId in existingReconIds }
        if (valid.isEmpty()) return

        db.cashReconciliationDao().upsertDenominationsFromRemote(
            valid.map { dto ->
                CashDenominationEntity(
                    id                = dto.id,
                    reconciliationId  = dto.reconciliationId,
                    denominationValue = dto.denominationValue.toRemoteDouble(),
                    count             = dto.count,
                    subtotal          = dto.subtotal.toRemoteDouble(),
                    isCoin            = dto.isCoin
                )
            }
        )

        val pulledReconIds = valid.mapTo(mutableSetOf()) { it.reconciliationId }
        val remoteIds      = valid.mapTo(mutableSetOf()) { it.id }
        val staleLocal = db.cashReconciliationDao().getAllDenominationsSync()
            .filter { it.reconciliationId in pulledReconIds && it.id !in remoteIds }
            .map { it.id }
        if (staleLocal.isNotEmpty()) {
            db.cashReconciliationDao().deleteDenominationsByIds(staleLocal)
        }
    }

    suspend fun SyncRuntime.pushCashRegister(orgId: String) {
        if (financiallyBlockedAggregateIds(orgId).isNotEmpty()) return
        val register = db.cashRegisterDao().getRegisterSync() ?: return
        supabase.postgrest["cash_register"].upsert(
            listOf(
                CashRegisterDto(
                    id             = "${orgId}_main",
                    organizationId = orgId,
                    balance        = register.balance.toRemoteDecimal(),
                    updatedAt      = SupabaseDateParser.format(register.updatedAt)
                )
            )
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pushCashMovements(orgId: String) {
        val blockedInvoices = financiallyBlockedAggregateIds(orgId)
        val list = db.cashRegisterDao().getAllMovementsSync().filter { movement ->
            movement.referenceId !in blockedInvoices && movement.sourceId !in blockedInvoices
        }
        if (list.isEmpty()) return

        supabase.postgrest["cash_register_movements"].upsert(
            list.map { mv ->
                CashMovementDto(
                    id             = mv.id,
                    organizationId = orgId,
                    movementType   = mv.movementType.name,
                    amount         = mv.amount.toRemoteDecimal(),
                    balanceBefore  = mv.balanceBefore.toRemoteDecimal(),
                    balanceAfter   = mv.balanceAfter.toRemoteDecimal(),
                    referenceId    = mv.referenceId,
                    note           = mv.note,
                    createdAt      = SupabaseDateParser.format(mv.createdAt)
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pullCashRegister(orgId: String) {
        // F249: do not let a legacy register timestamp silently overwrite an unresolved financial conflict.
        if (financiallyBlockedAggregateIds(orgId).isNotEmpty()) return
        val remote = supabase.postgrest["cash_register"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<CashRegisterDto>()
        val dto = remote.firstOrNull() ?: return

        val remoteUpdatedAt = SupabaseDateParser.parse(dto.updatedAt)
        val local = db.cashRegisterDao().getRegisterSync()

        // إذا لم يوجد صف محلي، أو السيرفر أحدث — اكتب القيمة
        if (local == null || remoteUpdatedAt > local.updatedAt) {
            db.cashRegisterDao().upsertRegister(
                CashRegisterEntity(
                    id        = "main",
                    balance   = dto.balance.toRemoteDouble(),
                    updatedAt = remoteUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun SyncRuntime.pullCashMovements(orgId: String) {
        val blockedInvoices = financiallyBlockedAggregateIds(orgId)
        val blockedPaymentIds = if (blockedInvoices.isEmpty()) emptySet() else db.paymentDao()
            .getAllPaymentsSync()
            .filter { it.invoiceId in blockedInvoices }
            .mapTo(mutableSetOf()) { it.id }
        val blockedReferences = blockedInvoices + blockedPaymentIds
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CASH_MVTS)
        val pullStartedAt = System.currentTimeMillis()

        val remote = supabase.postgrest["cash_register_movements"].select {
            filter {
                eq("organization_id", orgId)
                if (lastPulledAt > 0L) gte("created_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<CashMovementDto>()
        if (remote.isEmpty()) {
            if (blockedInvoices.isEmpty()) {
                userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CASH_MVTS, pullStartedAt)
            }
            return
        }

        val blockedReturned = remote.any { it.referenceId in blockedReferences }
        remote.filter { it.referenceId !in blockedReferences }.forEach { dto ->
            val entity = CashRegisterMovementEntity(
                id            = dto.id,
                movementType  = runCatching { CashMovementType.valueOf(dto.movementType) }
                    .getOrDefault(CashMovementType.MANUAL_ADD),
                amount        = dto.amount.toRemoteDouble(),
                balanceBefore = dto.balanceBefore.toRemoteDouble(),
                balanceAfter  = dto.balanceAfter.toRemoteDouble(),
                referenceId   = dto.referenceId,
                note          = dto.note,
                createdAt     = SupabaseDateParser.parse(dto.createdAt)
            )
            db.cashRegisterDao().insertMovement(entity)
        }

        if (blockedInvoices.isEmpty() && !blockedReturned) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CASH_MVTS, pullStartedAt)
        }
    }
