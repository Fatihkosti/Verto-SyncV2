package com.verto.data.database.di

import android.content.Context
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides fun provideClientDao(db: AppDatabase): ClientDao = db.clientDao()
    @Provides fun providePartyRoleDao(db: AppDatabase): PartyRoleDao = db.partyRoleDao()
    @Provides fun provideInvoiceDao(db: AppDatabase): InvoiceDao = db.invoiceDao()
    @Provides fun provideInvoiceReturnDao(db: AppDatabase): InvoiceReturnDao = db.invoiceReturnDao()
    @Provides fun providePurchaseCycleDao(db: AppDatabase): PurchaseCycleDao = db.purchaseCycleDao()
    @Provides fun provideReportsAnalyticsDao(db: AppDatabase): ReportsAnalyticsDao = db.reportsAnalyticsDao()
    @Provides fun provideInvoiceDraftDao(db: AppDatabase): InvoiceDraftDao = db.invoiceDraftDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideClientCreditDao(db: AppDatabase): ClientCreditDao = db.clientCreditDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideClientReminderDao(db: AppDatabase): ClientReminderDao = db.clientReminderDao()
    @Provides fun providePriceListDao(db: AppDatabase): PriceListDao = db.priceListDao()
    @Provides fun provideInventoryDao(db: AppDatabase): InventoryDao = db.inventoryDao()
    @Provides fun provideInventoryUnitDao(db: AppDatabase): InventoryUnitDao = db.inventoryUnitDao()
    @Provides fun provideItemCategoryDao(db: AppDatabase): ItemCategoryDao = db.itemCategoryDao()
    @Provides fun provideCashRegisterDao(db: AppDatabase): CashRegisterDao = db.cashRegisterDao()
    @Provides fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideCommissionPaymentDao(db: AppDatabase): CommissionPaymentDao = db.commissionPaymentDao()
    @Provides fun provideJoinCodeDao(db: AppDatabase): JoinCodeDao = db.joinCodeDao()
    @Provides fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideOptimalCompanyLinkDao(db: AppDatabase): OptimalCompanyLinkDao = db.optimalCompanyLinkDao()
    @Provides fun provideOptimalCompanyReadDao(db: AppDatabase): OptimalCompanyReadDao = db.optimalCompanyReadDao()
    @Provides fun provideOptimalCompanyInvoiceReadDao(db: AppDatabase): OptimalCompanyInvoiceReadDao = db.optimalCompanyInvoiceReadDao()
    @Provides fun provideOptimalRegistrationCodeDao(db: AppDatabase): OptimalRegistrationCodeDao = db.optimalRegistrationCodeDao()
    @Provides fun provideOptimalOutboxDao(db: AppDatabase): OptimalOutboxDao = db.optimalOutboxDao()
    @Provides fun provideMessageConversationDao(db: AppDatabase): MessageConversationDao = db.messageConversationDao()
    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideMessageMediaDao(db: AppDatabase): MessageMediaDao = db.messageMediaDao()
    @Provides fun provideOptimalConversationBindingDao(db: AppDatabase): OptimalConversationBindingDao = db.optimalConversationBindingDao()
    @Provides fun provideOptimalVehicleDao(db: AppDatabase): OptimalVehicleDao = db.optimalVehicleDao()
    @Provides fun provideOptimalMaintenanceDao(db: AppDatabase): OptimalMaintenanceDao = db.optimalMaintenanceDao()
    @Provides fun provideOptimalMaintenanceReadDao(db: AppDatabase): OptimalMaintenanceReadDao = db.optimalMaintenanceReadDao()
    @Provides fun provideOptimalMaintenanceDetailsReadDao(db: AppDatabase): OptimalMaintenanceDetailsReadDao = db.optimalMaintenanceDetailsReadDao()
    @Provides fun provideOptimalMaintenanceFollowUpDao(db: AppDatabase): OptimalMaintenanceFollowUpDao = db.optimalMaintenanceFollowUpDao()
    @Provides fun provideHomeQuickActionOrderDao(db: AppDatabase): HomeQuickActionOrderDao = db.homeQuickActionOrderDao()
    @Provides fun provideHomeEventStateDao(db: AppDatabase): HomeEventStateDao = db.homeEventStateDao()
    @Provides fun provideTeamObservationDao(db: AppDatabase): TeamObservationDao = db.teamObservationDao()
    @Provides fun provideEducationalContentDao(db: AppDatabase): EducationalContentDao = db.educationalContentDao()
    @Provides fun provideLogisticsDao(db: AppDatabase): LogisticsDao = db.logisticsDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideRfmCacheDao(db: AppDatabase): RfmCacheDao = db.rfmCacheDao()
    @Provides fun provideForecastCacheDao(db: AppDatabase): ForecastCacheDao = db.forecastCacheDao()
    @Provides fun provideCostAllocationDao(db: AppDatabase): CostAllocationDao = db.costAllocationDao()
    @Provides fun provideCashReconciliationDao(db: AppDatabase): CashReconciliationDao = db.cashReconciliationDao()
    @Provides fun provideEmployeePerformanceDao(db: AppDatabase): EmployeePerformanceDao = db.employeePerformanceDao()
}
