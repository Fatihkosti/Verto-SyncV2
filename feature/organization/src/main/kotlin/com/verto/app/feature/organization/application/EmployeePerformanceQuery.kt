package com.verto.app.feature.organization.application

/**
 * قراءة خام لأرقام أداء الموظف. التنفيذ يبقى في App Bridge لأن المصدر الحالي Room.
 */
interface EmployeePerformanceQuery {
    suspend fun load(employeeId: String, from: Long, to: Long): EmployeePerformanceSnapshot
}

data class EmployeePerformanceSnapshot(
    val invoiceCount: Int = 0,
    val cashInvoices: Int = 0,
    val creditInvoices: Int = 0,
    val totalSales: Double = 0.0,
    val totalProfit: Double = 0.0,
    val creditSalesTotal: Double = 0.0,
    val creditSalesPaid: Double = 0.0,
    val collections: Double = 0.0,
    val addedClients: Int = 0
)
