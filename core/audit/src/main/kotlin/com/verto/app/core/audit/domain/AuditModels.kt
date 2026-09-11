package com.verto.app.core.audit.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AuditAction(val label: String) {
    INSERT("إضافة"),
    UPDATE("تعديل"),
    DELETE("حذف")
}

@Serializable
enum class AuditTable(val label: String) {
    INVOICE("فاتورة"),
    PAYMENT("سداد"),
    CLIENT("عميل"),
    EXPENSE("مصروف"),
    INVENTORY("مخزون"),
    SHIPMENT("شحنة"),
    OPTIMAL("Optimal"),
    COMMISSION("عمولة"),
    WITHDRAWAL("طلب سحب"),
    EDUCATIONAL_TOPIC("موضوع تعليمي")
}

data class AuditRecord(
    val action: AuditAction,
    val table: AuditTable,
    val recordId: String,
    val summary: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val canUndo: Boolean = true,
    val sourceType: String = "",
    val sourceId: String = "",
    val sourceVersion: Int = 1,
    val writeId: String = "",
)
