package com.verto.app.di
import com.verto.app.feature.commission.application.CommissionReportGateway
import com.verto.app.feature.commission.bridge.CommissionReportGatewayAdapter
import com.verto.app.feature.dashboard.application.DashboardAdminGateway
import com.verto.app.feature.dashboard.bridge.DashboardAdminGatewayAdapter
import com.verto.app.feature.expenses.application.ExpensesGateway
import com.verto.app.feature.expenses.bridge.ExpensesOperationsAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
@Module @InstallIn(SingletonComponent::class)
abstract class FinanceBoundaryModule {
 @Binds abstract fun bindExpensesGateway(implementation: ExpensesOperationsAdapter): ExpensesGateway
 @Binds abstract fun bindDashboardAdminGateway(implementation: DashboardAdminGatewayAdapter): DashboardAdminGateway
 @Binds abstract fun bindCommissionReportGateway(implementation: CommissionReportGatewayAdapter): CommissionReportGateway
}
