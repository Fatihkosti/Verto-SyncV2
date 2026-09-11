package com.verto.app.utils

import com.verto.app.core.audit.domain.AuditMaintenancePort
import com.verto.app.core.audit.domain.AuditRecord
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.data.local.dao.AuditLogDao
import com.verto.app.data.local.entity.AuditLogEntity

/** Room-backed implementation of the append-only audit write contract. */
class AuditLogger(private val dao: AuditLogDao) : WriteAuditPort, AuditMaintenancePort {
    override suspend fun write(record: AuditRecord) {
        val rowId = dao.insert(
            AuditLogEntity(
                action = record.action,
                auditTable = record.table,
                recordId = record.recordId,
                recordSummary = record.summary,
                oldValue = record.oldValue,
                newValue = record.newValue,
                employeeId = record.employeeId,
                employeeName = record.employeeName,
                sourceType = record.sourceType,
                sourceId = record.sourceId,
                sourceVersion = record.sourceVersion,
                writeId = record.writeId,
                canUndo = record.canUndo
            )
        )
        check(rowId != -1L) { "audit insert was ignored unexpectedly" }
    }

    /** Disables undo for old entries without deleting audit history. */
    override suspend fun expireOldEntries() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dao.expireOldEntries(cutoff)
    }
}
