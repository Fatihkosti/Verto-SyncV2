package com.verto.app.core.audit.activityevent

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.data.local.dao.AuditLogDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAuditActivityEventSource @Inject constructor(
    private val auditLogDao: AuditLogDao,
) : AuditActivityEventSource {
    override fun observeSince(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<AuditActivityRecord>> =
        auditLogDao.observeActivityEntries(organizationId, sinceEpochMillis, limit).map { entries ->
            entries.map { row ->
                val entry = row.audit
                AuditActivityRecord(
                    id = entry.id,
                    action = when (entry.action) {
                        AuditAction.INSERT -> AuditActivityAction.INSERT
                        AuditAction.UPDATE -> AuditActivityAction.UPDATE
                        AuditAction.DELETE -> AuditActivityAction.DELETE
                    },
                    table = when (entry.auditTable) {
                        AuditTable.INVOICE -> AuditActivityTable.INVOICE
                        AuditTable.INVENTORY -> AuditActivityTable.INVENTORY
                        else -> AuditActivityTable.OTHER
                    },
                    recordId = entry.recordId,
                    summary = entry.recordSummary,
                    employeeId = entry.employeeId,
                    employeeName = entry.employeeName,
                    occurredAtEpochMillis = entry.createdAt,
                    sourceType = entry.sourceType,
                    sourceId = entry.sourceId,
                    sourceVersion = entry.sourceVersion,
                    writeId = entry.writeId,
                    invoiceCategory = row.invoiceCategory.name,
                )
            }
        }
}
