package com.verto.app.core.audit.domain

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable

/** عقد كتابة سجل التدقيق دون كشف Room أو DAO للمستهلكين. */
interface WriteAuditPort {
    suspend fun write(record: AuditRecord)

    suspend fun log(
        action: AuditAction,
        table: AuditTable,
        recordId: String,
        summary: String = "",
        oldValue: String = "",
        newValue: String = "",
        employeeId: String = "",
        employeeName: String = "",
        canUndo: Boolean = true,
        sourceType: String = "",
        sourceId: String = "",
        sourceVersion: Int = 1,
        writeId: String = "",
    ) = write(
        AuditRecord(
            action = action,
            table = table,
            recordId = recordId,
            summary = summary,
            oldValue = oldValue,
            newValue = newValue,
            employeeId = employeeId,
            employeeName = employeeName,
            canUndo = canUndo,
            sourceType = sourceType,
            sourceId = sourceId,
            sourceVersion = sourceVersion,
            writeId = writeId,
        )
    )

    suspend fun logInsert(
        table: AuditTable,
        recordId: String,
        summary: String,
        newValue: String = "",
        employeeId: String = "",
        employeeName: String = "",
        sourceType: String = "",
        sourceId: String = "",
        sourceVersion: Int = 1,
        writeId: String = "",
    ) = log(
        AuditAction.INSERT,
        table,
        recordId,
        summary,
        newValue = newValue,
        employeeId = employeeId,
        employeeName = employeeName,
        sourceType = sourceType,
        sourceId = sourceId,
        sourceVersion = sourceVersion,
        writeId = writeId,
    )

    suspend fun logUpdate(
        table: AuditTable,
        recordId: String,
        summary: String,
        oldValue: String,
        newValue: String,
        employeeId: String = "",
        employeeName: String = "",
        sourceType: String = "",
        sourceId: String = "",
        sourceVersion: Int = 1,
        writeId: String = "",
    ) = log(
        AuditAction.UPDATE,
        table,
        recordId,
        summary,
        oldValue,
        newValue,
        employeeId,
        employeeName,
        sourceType = sourceType,
        sourceId = sourceId,
        sourceVersion = sourceVersion,
        writeId = writeId,
    )

    suspend fun logDelete(
        table: AuditTable,
        recordId: String,
        summary: String,
        oldValue: String = "",
        employeeId: String = "",
        employeeName: String = "",
        sourceType: String = "",
        sourceId: String = "",
        sourceVersion: Int = 1,
        writeId: String = "",
    ) = log(
        AuditAction.DELETE,
        table,
        recordId,
        summary,
        oldValue = oldValue,
        employeeId = employeeId,
        employeeName = employeeName,
        sourceType = sourceType,
        sourceId = sourceId,
        sourceVersion = sourceVersion,
        writeId = writeId,
    )
}
