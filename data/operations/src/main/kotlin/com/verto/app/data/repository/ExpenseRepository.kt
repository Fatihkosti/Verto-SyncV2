package com.verto.app.data.repository

import androidx.room.withTransaction
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.audit.logPermissionDeniedFromPreferences
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.CategoryTotal
import com.verto.app.data.local.dao.ExpenseDao
import com.verto.app.data.local.entity.ExpenseEntity
import com.verto.app.data.model.PermissionDeniedException
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.sync.SyncBatchCoordinatorV2
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.sync.expense.ExpenseRevisionSnapshotB12
import com.verto.app.data.sync.expense.expenseCashDeltaMinorB12
import com.verto.app.data.sync.expense.expenseContentHashB12
import com.verto.app.data.sync.expense.expenseSnapshotMapB12
import com.verto.app.utils.CashMovementWriteResult
import com.verto.app.utils.CashRegisterManager
import com.verto.app.utils.PreferencesManager
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** B12: mutable/versioned expense producer. Expense + cash fact + immutable intents share one Room transaction. */
class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val cashRegisterManager: CashRegisterManager,
    private val userPrefs: PreferencesManager,
    private val db: AppDatabase,
    private val permissionProvider: PermissionProvider,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
    private val batchCoordinator: SyncBatchCoordinatorV2,
) {
    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    fun getExpensesByRange(from:Long,to:Long)=expenseDao.getExpensesByDateRange(from,to)
    fun getTotalMinorInRange(from:Long,to:Long)=expenseDao.getTotalExpensesMinorInRange(from,to)
    fun getCategoryTotals(from:Long,to:Long):Flow<List<CategoryTotal>> = expenseDao.getExpensesByCategory(from,to)

    suspend fun insertExpense(expense: ExpenseEntity): Long {
        validateExpense(expense)
        val identity = trustedIdentity()
        val writeId = UUID.randomUUID().toString()
        val batchId = expenseBatchId(writeId)
        val createdAt = System.currentTimeMillis()
        return runAuthorizedExpenseWrite({ requireExpensePermission(true,expense) }, { block -> db.withTransaction { block() } }) {
            check(expenseDao.getExpenseByIdSync(expense.id) == null) { "EXPENSE_ID_ALREADY_EXISTS" }
            val active = canonicalActive(expense)
            val after = active.toRevisionSnapshot()
            val cashDelta = expenseCashDeltaMinorB12(null, after)
            val result = expenseDao.insertExpense(active).also { check(it != -1L) }
            val cash = cashRegisterManager.recordExpenseDeltaB12(cashDelta, active.id, writeId, batchId)
            enqueueRevision(identity, null, after, "UPSERT", writeId, batchId, 0L, cashDelta, cash, createdAt)
            sealBatch(identity.organizationId, batchId, writeId, cash, createdAt)
            result
        }
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        validateExpense(expense)
        val identity = trustedIdentity()
        val writeId = UUID.randomUUID().toString()
        val batchId = expenseBatchId(writeId)
        val createdAt = System.currentTimeMillis()
        runAuthorizedExpenseWrite({ requireExpensePermission(true,expense) }, { block -> db.withTransaction { block() } }) {
            val old = expenseDao.getExpenseByIdSync(expense.id) ?: error("expense does not exist")
            require(old.lifecycleState == "ACTIVE") { "EXPENSE_NOT_ACTIVE" }
            val updated = canonicalActive(expense)
            check(updated.id == old.id) { "EXPENSE_IDENTITY_CHANGE_FORBIDDEN" }
            val before = old.toRevisionSnapshot()
            val after = updated.toRevisionSnapshot()
            if (expenseContentHashB12(before) == expenseContentHashB12(after)) return@runAuthorizedExpenseWrite
            val baseVersion = db.unifiedSyncDao().readUnambiguousAppliedVersion(identity.organizationId, "EXPENSE", old.id)
            val cashDelta = expenseCashDeltaMinorB12(before, after)
            expenseDao.updateExpense(updated)
            val cash = cashRegisterManager.recordExpenseDeltaB12(cashDelta, updated.id, writeId, batchId)
            enqueueRevision(identity, before, after, "UPSERT", writeId, batchId, baseVersion, cashDelta, cash, createdAt)
            sealBatch(identity.organizationId, batchId, writeId, cash, createdAt)
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        val identity = trustedIdentity()
        val writeId = UUID.randomUUID().toString()
        val batchId = expenseBatchId(writeId)
        val createdAt = System.currentTimeMillis()
        val voided = runAuthorizedExpenseWrite({ requireExpensePermission(false,expense) }, { block -> db.withTransaction { block() } }) {
            val current = expenseDao.getExpenseByIdSync(expense.id) ?: return@runAuthorizedExpenseWrite false
            if (current.lifecycleState == "VOID") return@runAuthorizedExpenseWrite false
            require(current.lifecycleState == "ACTIVE") { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
            val before = current.toRevisionSnapshot()
            val afterEntity = current.copy(
                isDirty = true,
                lifecycleState = "VOID",
                voidedAt = createdAt,
                voidReason = "USER_DELETE",
                reversalWriteId = writeId,
            )
            val after = afterEntity.toRevisionSnapshot()
            val baseVersion = db.unifiedSyncDao().readUnambiguousAppliedVersion(identity.organizationId, "EXPENSE", current.id)
            val cashDelta = expenseCashDeltaMinorB12(before, after)
            expenseDao.updateExpense(afterEntity)
            val cash = cashRegisterManager.recordExpenseDeltaB12(cashDelta, current.id, writeId, batchId)
            enqueueRevision(identity, before, after, "VOID", writeId, batchId, baseVersion, cashDelta, cash, createdAt)
            sealBatch(identity.organizationId, batchId, writeId, cash, createdAt)
            true
        }
        if (voided) runCatching { userPrefs.addPendingExpenseDeletion(expense.id) }
    }

    suspend fun getAllSync():List<ExpenseEntity> = expenseDao.getAllExpensesSync()

    private suspend fun enqueueRevision(
        identity: ExpenseActorIdentity,
        before: ExpenseRevisionSnapshotB12?,
        after: ExpenseRevisionSnapshotB12,
        operation: String,
        writeId: String,
        batchId: String,
        capturedBaseVersion: Long?,
        cashDeltaMinor: Long,
        cash: CashMovementWriteResult?,
        createdAt: Long,
    ) {
        check(cash?.signedAmountMinor == cashDeltaMinor || (cash == null && cashDeltaMinor == 0L)) {
            "BLOCKED_EXPENSE_DOMAIN_DRIFT"
        }
        val revisionIntent = linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "writeId" to writeId,
            "expenseId" to after.id,
            "operation" to operation,
            "capturedBaseVersion" to capturedBaseVersion,
            "before" to before?.let(::expenseSnapshotMapB12),
            "after" to expenseSnapshotMapB12(after),
            "beforeContentHash" to before?.let(::expenseContentHashB12),
            "afterContentHash" to expenseContentHashB12(after),
            "cashDeltaMinor" to cashDeltaMinor,
            "cashMovementId" to cash?.movementId,
            "cashMutationId" to cash?.mutationId,
            "actorId" to identity.actorId,
            "createdAt" to createdAt,
        )
        outbox.enqueue(
            organizationId = identity.organizationId,
            aggregateType = "EXPENSE",
            aggregateId = after.id,
            operationType = operation,
            payload = linkedMapOf(
                "materialization" to expenseSnapshotMapB12(after),
                "expenseRevisionIntent" to revisionIntent,
            ),
            mutationId = writeId,
            baseVersion = capturedBaseVersion,
            commandBatchId = batchId,
            commandOrder = 0,
            createdAt = createdAt,
        )
    }

    private suspend fun sealBatch(org: String, batchId: String, writeId: String, cash: CashMovementWriteResult?, createdAt: Long) {
        val members = buildList {
            add(writeId)
            cash?.mutationId?.let(::add)
        }
        batchCoordinator.seal(org, batchId, members, createdAt)
    }

    private fun canonicalActive(expense:ExpenseEntity):ExpenseEntity {
        val money=com.verto.app.money.Money.ofMinor(expense.amountMinor)
        return expense.copy(amount=money.toLegacyDouble(),amountMinor=money.amountMinor,isDirty=true,lifecycleState="ACTIVE",voidedAt=null,voidReason=null,reversalWriteId=null)
    }

    private fun validateExpense(expense:ExpenseEntity) {
        require(expense.amount.isFinite())
        requirePositiveExpenseMinor(expense.amountMinor)
        require(expense.category.isNotBlank())
        require(expense.item.isNotBlank())
    }

    private suspend fun requireExpensePermission(create:Boolean,expense:ExpenseEntity) {
        val granted=permissionProvider.canNow { if(create)it.expensesCreate else it.expensesDelete }
        if(!granted){
            val action=if(create)"expenses_create" else "expenses_delete"
            runCatching { auditLogger.logPermissionDeniedFromPreferences(action,"expenseId=${expense.id} amountMinor=${expense.amountMinor}",userPrefs) }
            throw PermissionDeniedException(if(create)"لا تملك صلاحية إضافة مصروف" else "لا تملك صلاحية حذف مصروف")
        }
    }

    private suspend fun trustedIdentity(): ExpenseActorIdentity {
        val session = sessionReader.snapshot()
        val org = session.organization.id.trim()
        val actor = session.user.id.trim()
        require(org.isNotBlank()) { "FAIL_ORG_SCOPE" }
        require(actor.isNotBlank()) { "FAIL_USER_SCOPE" }
        return ExpenseActorIdentity(org, actor)
    }
}

private data class ExpenseActorIdentity(val organizationId: String, val actorId: String)
private fun expenseBatchId(writeId: String) = "expense:$writeId"
private fun ExpenseEntity.toRevisionSnapshot() = ExpenseRevisionSnapshotB12(
    id=id, category=category, item=item, amountMinor=amountMinor, note=note, date=date,
    lifecycleState=lifecycleState, voidedAt=voidedAt, voidReason=voidReason, reversalWriteId=reversalWriteId,
)

internal suspend fun <T> runAuthorizedExpenseWrite(
    authorize:suspend()->Unit, transaction:suspend(suspend()->T)->T, block:suspend()->T,
):T { authorize(); return transaction(block) }

internal fun requirePositiveExpenseMinor(amountMinor:Long) { require(amountMinor>0L) { "Expense amount must be greater than zero" } }

// Compatibility test seams retained; B12 production uses expenseCashDeltaMinorB12 directly.
internal const val EXPENSE_UPDATE_REFUND_SOURCE = "EXPENSE_UPDATE_REFUND"
internal const val EXPENSE_VOID_REFUND_SOURCE = "EXPENSE_VOID_REFUND"

internal suspend fun <T> insertExpenseEffects(
    persist:suspend()->T, cash:suspend()->Unit, outbox:suspend()->Unit,
):T { val result=persist(); cash(); outbox(); return result }

internal suspend fun updateExpenseEffects(
    persist:suspend()->Unit, cash:suspend()->Unit, outbox:suspend()->Unit,
) { persist(); cash(); outbox() }

internal suspend fun applyExpenseAmountDelta(
    oldMinor:Long, newMinor:Long, cashOut:suspend(Long)->Unit, refund:suspend(Long,String)->Unit,
) {
    val diff=Math.subtractExact(newMinor,oldMinor)
    when {
        diff>0L -> cashOut(diff)
        diff<0L -> refund(Math.negateExact(diff),EXPENSE_UPDATE_REFUND_SOURCE)
    }
}

internal suspend fun <T> voidExpenseEffects(
    current:T?, isVoided:(T)->Boolean, refund:suspend(T)->Unit, toVoided:(T)->T, commitVoid:suspend(T)->Unit,
):Boolean {
    current ?: return false
    if(isVoided(current)) return false
    refund(current); val voided=toVoided(current); commitVoid(voided); return true
}
