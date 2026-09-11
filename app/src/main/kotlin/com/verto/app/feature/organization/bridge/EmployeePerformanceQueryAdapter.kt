package com.verto.app.feature.organization.bridge

import com.verto.app.data.local.dao.EmployeePerformanceDao
import com.verto.app.feature.organization.application.EmployeePerformanceQuery
import com.verto.app.feature.organization.application.EmployeePerformanceSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmployeePerformanceQueryAdapter @Inject constructor(
    private val dao: EmployeePerformanceDao
) : EmployeePerformanceQuery {
    override suspend fun load(employeeId: String, from: Long, to: Long): EmployeePerformanceSnapshot =
        EmployeePerformanceSnapshot(
            invoiceCount = dao.invoiceCount(employeeId, from, to),
            cashInvoices = dao.cashInvoiceCount(employeeId, from, to),
            creditInvoices = dao.creditInvoiceCount(employeeId, from, to),
            totalSales = dao.totalSales(employeeId, from, to),
            totalProfit = dao.totalProfit(employeeId, from, to),
            creditSalesTotal = dao.creditSalesTotal(employeeId, from, to),
            creditSalesPaid = dao.creditSalesPaid(employeeId, from, to),
            collections = dao.collections(employeeId, from, to),
            addedClients = dao.addedClients(employeeId, from, to)
        )
}
