package com.verto.app.data.repository

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.BudgetDao
import com.verto.app.data.local.entity.BudgetEntity
import com.verto.app.data.local.entity.BudgetType
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.utils.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val userPrefs: PreferencesManager,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {
    fun getAllActiveBudgets() = dao.getAllActiveBudgets()
    fun getAllBudgets() = dao.getAllBudgets()
    fun getCurrentBudgets() = dao.getCurrentBudgets()

    suspend fun getActiveBudgetForType(type: BudgetType) = dao.getActiveBudgetForType(type)
    suspend fun getById(id: String) = dao.getById(id)

    suspend fun saveBudget(budget: BudgetEntity) {
        val orgId = trustedOrganizationId()
        val updated = budget.copy(updatedAt = System.currentTimeMillis())
        database.withTransaction {
            dao.insert(updated)
            outbox.enqueue(
                organizationId = orgId,
                aggregateType = "BUDGET",
                aggregateId = updated.id,
                operationType = "UPSERT",
                payload = budgetPayload(updated),
            )
        }
    }

    suspend fun deleteBudget(budget: BudgetEntity) = deleteInternal(budget.id)

    suspend fun deleteById(id: String) = deleteInternal(id)

    private suspend fun deleteInternal(id: String) {
        val orgId = trustedOrganizationId()
        database.withTransaction {
            dao.deleteById(id)
            outbox.enqueue(
                organizationId = orgId,
                aggregateType = "BUDGET",
                aggregateId = id,
                operationType = "DELETE",
                payload = mapOf("deleted" to true),
            )
        }
        // Legacy compatibility mirror only; durable authority is already committed above.
        runCatching { userPrefs.addPendingBudgetDeletion(id) }
    }

    private fun budgetPayload(b: BudgetEntity) = mapOf(
        "budgetType" to b.budgetType.name,
        "category" to b.category,
        "isActive" to b.isActive,
        "note" to b.note,
        "periodEnd" to b.periodEnd,
        "periodStart" to b.periodStart,
        "periodType" to b.periodType.name,
        "targetAmount" to b.targetAmount,
        "updatedAt" to b.updatedAt,
    )

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }
}
