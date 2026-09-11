package com.verto.app.data.repository

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.CashReconciliationDao
import com.verto.app.data.local.entity.CashDenominationEntity
import com.verto.app.data.local.entity.CashReconciliationEntity
import com.verto.app.data.local.entity.ReconciliationStatus
import com.verto.app.data.sync.UnifiedOutboxWriter
import javax.inject.Inject
import java.util.UUID
import javax.inject.Singleton

@Singleton
class CashReconciliationRepository @Inject constructor(
    private val dao: CashReconciliationDao,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {
    fun getAllSessions() = dao.getAllSessions()
    fun getSessionsByStatus(status: ReconciliationStatus) = dao.getSessionsByStatus(status)
    fun getCurrentOpenSession() = dao.getCurrentOpenSession()
    fun getSessionsInRange(from: Long, to: Long) = dao.getSessionsInRange(from, to)
    fun getDenominationsForSession(sessionId: String) = dao.getDenominationsForSession(sessionId)

    suspend fun getSessionById(id: String) = dao.getSessionById(id)

    suspend fun startSession(session: CashReconciliationEntity) {
        val orgId = trustedOrganizationId()
        val mutationId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.insertSession(session)
            outbox.enqueue(orgId, "CASH_RECONCILIATION", session.id, "COMMAND", payload(session, "START"), mutationId)
        }
    }

    suspend fun closeSession(session: CashReconciliationEntity, denominations: List<CashDenominationEntity>) {
        val closed = session.copy(
            status = ReconciliationStatus.CLOSED,
            endedAt = System.currentTimeMillis()
        )
        val orgId = trustedOrganizationId()
        val mutationId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.updateSession(closed)
            if (denominations.isNotEmpty()) {
                dao.replaceDenominations(session.id, denominations)
            }
            outbox.enqueue(
                orgId,
                "CASH_RECONCILIATION",
                session.id,
                "COMMAND",
                payload(closed, "CLOSE"),
                mutationId,
            )
        }
    }

    suspend fun updateSession(session: CashReconciliationEntity) {
        val orgId = trustedOrganizationId()
        val mutationId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.updateSession(session)
            outbox.enqueue(orgId, "CASH_RECONCILIATION", session.id, "UPSERT", payload(session, "UPDATE"), mutationId)
        }
    }

    suspend fun deleteSession(session: CashReconciliationEntity) {
        error("FAIL_DELETE_POLICY: CASH_RECONCILIATION is NO_CLIENT_DELETE")
    }

    private suspend fun payload(session: CashReconciliationEntity, command: String): Map<String, Any?> {
        val denominations = dao.getDenominationsForSessionSync(session.id).sortedBy { it.id }
        return mapOf("materialization" to mapOf(
            "id" to session.id, "command" to command, "employeeId" to session.employeeId,
            "employeeName" to session.employeeName, "openingBalanceMinor" to session.openingBalanceMinor,
            "totalSalesMinor" to session.totalSalesMinor, "totalRefundsMinor" to session.totalRefundsMinor,
            "totalCashInMinor" to session.totalCashInMinor, "totalCashOutMinor" to session.totalCashOutMinor,
            "expectedBalanceMinor" to session.expectedBalanceMinor,
            "actualCountedBalanceMinor" to session.actualCountedBalanceMinor,
            "varianceMinor" to session.varianceMinor, "varianceReason" to session.varianceReason,
            "status" to session.status.name, "startedAt" to session.startedAt, "endedAt" to session.endedAt,
            "notes" to session.notes,
            "denominations" to denominations.map { row -> mapOf(
                "id" to row.id, "reconciliationId" to row.reconciliationId,
                "denominationValueMinor" to row.denominationValueMinor, "count" to row.count,
                "subtotalMinor" to row.subtotalMinor, "isCoin" to row.isCoin,
            ) },
        ))
    }

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }
}
