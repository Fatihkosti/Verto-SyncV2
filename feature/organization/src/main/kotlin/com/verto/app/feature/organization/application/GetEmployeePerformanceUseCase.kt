package com.verto.app.feature.organization.application

import com.verto.app.utils.MoneyMath
import javax.inject.Inject

/**
 * أرقام أداء الموظف الخام (بدون تنسيق ولا رمز عملة).
 * التنسيق يتم في طبقة الـ UI فقط.
 */
data class EmployeeSalesPerformance(
    val invoiceCount: Int = 0,
    val cashInvoices: Int = 0,
    val creditInvoices: Int = 0,
    val totalSales: Double = 0.0,
    val totalProfit: Double = 0.0,
    val debts: Double = 0.0,
    val collections: Double = 0.0,
    val addedClients: Int = 0
)

/**
 * يحسب أداء موظف المبيعات من Room (offline-first) لنطاق زمني [from, to].
 *
 * النِسبة معتمدة على قرارات المالك في EmployeePerformanceBacklog.md:
 *  • الفاتورة/العميل تُنسب لمن أنشأها (createdBy).
 *  • التحصيل يُنسب لفواتير البيع التي أنشأها الموظف (قرار #3 المُحدَّث).
 *  • الديون = إجمالي فواتير الآجل للموظف − المدفوع عليها.
 */
class GetEmployeePerformanceUseCase @Inject constructor(
    private val query: EmployeePerformanceQuery
) {
    suspend operator fun invoke(
        employeeId: String,
        from: Long,
        to: Long
    ): EmployeeSalesPerformance {
        if (employeeId.isBlank()) return EmployeeSalesPerformance()

        val snapshot = query.load(employeeId, from, to)
        val debts = MoneyMath.subtract(snapshot.creditSalesTotal, snapshot.creditSalesPaid)
            .coerceAtLeast(0.0)

        return EmployeeSalesPerformance(
            invoiceCount = snapshot.invoiceCount,
            cashInvoices = snapshot.cashInvoices,
            creditInvoices = snapshot.creditInvoices,
            totalSales = snapshot.totalSales,
            totalProfit = snapshot.totalProfit,
            debts = debts,
            collections = snapshot.collections,
            addedClients = snapshot.addedClients
        )
    }
}
