package com.verto.app.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verto.app.data.local.dao.*
import com.verto.app.data.local.entity.*

@TypeConverters(Converters::class)
@Database(
    entities = [
        PartyIdentityEntity::class,
        PartyRoleEntity::class,
        CustomerProfileEntity::class,
        SupplierProfileEntity::class,
        PartyMigrationIssueEntity::class,
        PartyRoleAuditEntity::class,
        PartySyncOutboxEntity::class,
        PartySyncConflictEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        InvoiceDueInstallmentEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        RealizedFxEventEntity::class,
        InvoiceWriteGuardEntity::class,
        FinancialOutboxEntity::class,
        FinancialInboxEntity::class,
        InvoiceReturnDocumentEntity::class,
        InvoiceReturnLineEntity::class,
        InvoiceReturnPaymentAllocationEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderLineEntity::class,
        GoodsReceiptEntity::class,
        GoodsReceiptLineEntity::class,
        PurchaseCycleAttachmentEntity::class,
        PurchaseInvoiceMatchEntity::class,
        PurchaseInvoiceMatchLineEntity::class,
        PurchaseInvoiceReceiptAllocationEntity::class,
        PurchasePaymentOverrideEntity::class,
        PurchaseOrderShipmentSourceEntity::class,
        InvoiceEditorDraftEntity::class,
        InvoiceEditorDraftLineEntity::class,
        InvoiceEditorDraftMaintenanceImageEntity::class,
        ExpenseEntity::class,
        NoteEntity::class,
        ClientReminderEntity::class,
        PriceListTemplateEntity::class,
        PriceListTemplateItemEntity::class,
        InventoryItemEntity::class,
        InventoryMovementEntity::class,
        InventoryCostRevisionEntity::class,
        InventoryReconciliationControlEntity::class,
        InventoryReconciliationMarkerEntity::class,
        InventoryReconciliationQuarantineEntity::class,
        InventoryReconciliationApplyContextEntity::class,
        InventoryWriteGuardEntity::class,
        InventoryStockOutboxEntity::class,
        InventorySyncConflictEntity::class,
        InventorySyncCursorEntity::class,
        InventoryCostOutboxEntity::class,
        InventoryCostRevaluationEventEntity::class,
        LandedCostAdjustmentEventEntity::class,
        InventoryUnitEntity::class,
        ItemCategoryEntity::class,
        CashRegisterEntity::class,
        CashRegisterMovementEntity::class,
        AuditLogEntity::class,
        CategoryEntity::class,
        CommissionPaymentEntity::class,

        // ── الجديد ──
        BudgetEntity::class,
        RfmCacheEntity::class,
        ForecastCacheEntity::class,
        CostAllocationEntity::class,
        CashReconciliationEntity::class,
        CashDenominationEntity::class,
        JoinCodeEntity::class,
        NotificationEntity::class,
        ClientCreditEntity::class,
        OptimalCompanyLinkEntity::class,
        OptimalRegistrationCodeEntity::class,
        OptimalOutboxEntity::class,
        MessageConversationEntity::class,
        MessageEntity::class,
        MessageMediaEntity::class,
        OptimalConversationBindingEntity::class,
        OptimalVehicleEntity::class,
        OptimalMaintenanceRecordEntity::class,
        OptimalMaintenanceImageEntity::class,
        OptimalMaintenanceFollowUpEntity::class,
        HomeQuickActionOrderEntity::class,
        HomeEventStateEntity::class,
        TeamObservationEntity::class,
        EducationalTopicEntity::class,
        EducationalTopicTargetEntity::class,
        LogisticsShipmentEntity::class,
        LogisticsShipmentSourceEntity::class,
        LogisticsShipmentLineEntity::class,
        LogisticsMilestoneEntity::class,
        LogisticsShipmentLegEntity::class,
        LogisticsCustodyHandoffEntity::class,
        LogisticsAssignmentEntity::class,
        LogisticsEventEntity::class,
        LogisticsPartnerEntity::class,
        LogisticsShipmentPartnerLinkEntity::class,
        LogisticsTransportDetailsEntity::class,
        LogisticsDocumentEntity::class,
        LogisticsCostEntity::class,
        LogisticsReceivingBatchEntity::class,
        LogisticsReceivingLineEntity::class,
        LogisticsInventoryPostingEntity::class,
        LogisticsCostAllocationEntity::class,
        LogisticsShortageEntity::class,
        LogisticsRecoveryEntity::class,
        LogisticsRecoveryLineEntity::class,
        LogisticsRecoveryPostingEntity::class,
        LogisticsShipmentNumberSequenceEntity::class,
        LogisticsPaymentEntity::class,
        LogisticsRouteTemplateEntity::class,
        LogisticsRouteTemplateStopEntity::class,
        LogisticsShortageSettlementEntity::class,
        LogisticsLateCostAdjustmentEntity::class,
        LogisticsLateCostAllocationEntity::class,
        LogisticsCustomsPlanEntity::class,
        LogisticsCustomsPlanDocumentEntity::class,
        LogisticsPlanRevisionEntity::class,
        LogisticsPlanRevisionChangeEntity::class,
        SyncOutboxEntity::class,
        SyncInboxEntity::class,
        SyncCursorEntity::class,
        SyncConflictEntity::class,
        SyncConflictReviewEvidenceEntity::class,
        SyncConflictResolutionAuditEntity::class,
        SyncSequenceStateEntity::class,
        OrganizationSettingsLocalEntity::class,
        SyncAttachmentTransferEntity::class,
        SyncRecoveryStateEntity::class,
        SyncBootstrapStageEntity::class,
        SyncRecoveryProtectionManifestEntity::class,
        SyncHealthStateEntity::class,
        SyncLegacyMigrationEntryEntity::class,
        SyncLegacyMigrationStateEntity::class,
        SyncEntityVersionEntity::class,
        SyncLocalGenerationEntity::class,
        SyncMutationPacketEntity::class,
        SyncPendingReferenceEntity::class,
        SyncWriteBatchEntity::class,
        SyncWriteBatchMemberEntity::class,
        SyncInboxGroupEntity::class,
        SyncInboxApplyRequestEntity::class,
        SyncInboxDependencyEntity::class,
        SyncInboxTouchedKeyEntity::class,
        SyncMigrationEvidenceV2Entity::class,
        SyncSnapshotBlobEntity::class,
        ExpenseRevisionHistoryEntity::class,
        RetiredShipmentArchiveEntity::class,
    ],
    version = ROOM_SCHEMA_VERSION, // single source of truth: MigrationCatalog
    exportSchema = true   // الجلسة 9: تصدير schema لاختبارات الـ migration (إلى app/schemas)
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun partyRoleDao(): PartyRoleDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun invoiceReturnDao(): InvoiceReturnDao
    abstract fun purchaseCycleDao(): PurchaseCycleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun noteDao(): NoteDao
    abstract fun clientReminderDao(): ClientReminderDao
    abstract fun priceListDao(): PriceListDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun inventoryReconciliationDao(): InventoryReconciliationDao
    abstract fun inventoryUnitDao(): InventoryUnitDao
    abstract fun itemCategoryDao(): ItemCategoryDao
    abstract fun cashRegisterDao(): CashRegisterDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun categoryDao(): CategoryDao
    abstract fun commissionPaymentDao(): CommissionPaymentDao
    abstract fun joinCodeDao(): JoinCodeDao
    abstract fun notificationDao(): NotificationDao
    abstract fun optimalCompanyLinkDao(): OptimalCompanyLinkDao
    abstract fun optimalCompanyReadDao(): OptimalCompanyReadDao
    abstract fun optimalCompanyInvoiceReadDao(): OptimalCompanyInvoiceReadDao
    abstract fun optimalRegistrationCodeDao(): OptimalRegistrationCodeDao
    abstract fun optimalOutboxDao(): OptimalOutboxDao
    abstract fun messageConversationDao(): MessageConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun messageMediaDao(): MessageMediaDao
    abstract fun optimalConversationBindingDao(): OptimalConversationBindingDao
    abstract fun optimalVehicleDao(): OptimalVehicleDao
    abstract fun optimalMaintenanceDao(): OptimalMaintenanceDao
    abstract fun optimalMaintenanceReadDao(): OptimalMaintenanceReadDao
    abstract fun optimalMaintenanceDetailsReadDao(): OptimalMaintenanceDetailsReadDao
    abstract fun optimalMaintenanceFollowUpDao(): OptimalMaintenanceFollowUpDao
    abstract fun homeQuickActionOrderDao(): HomeQuickActionOrderDao
    abstract fun homeEventStateDao(): HomeEventStateDao
    abstract fun teamObservationDao(): TeamObservationDao
    abstract fun educationalContentDao(): EducationalContentDao
    abstract fun logisticsDao(): LogisticsDao
    abstract fun logisticsShipmentNumberDao(): LogisticsShipmentNumberDao
    abstract fun budgetDao(): BudgetDao
    abstract fun rfmCacheDao(): RfmCacheDao
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun costAllocationDao(): CostAllocationDao
    abstract fun cashReconciliationDao(): CashReconciliationDao
    abstract fun employeePerformanceDao(): com.verto.app.data.local.dao.EmployeePerformanceDao
    abstract fun clientCreditDao(): com.verto.app.data.local.dao.ClientCreditDao
    abstract fun invoiceDraftDao(): InvoiceDraftDao
    abstract fun reportsAnalyticsDao(): ReportsAnalyticsDao
    abstract fun unifiedSyncDao(): UnifiedSyncDao
    abstract fun unifiedSyncProducerV307Dao(): UnifiedSyncProducerV307Dao
    abstract fun syncRecoveryDao(): SyncRecoveryDao
    abstract fun syncLegacyMigrationDao(): SyncLegacyMigrationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // ✅ UUID ثابتة للعملاء النظاميين — تُستخدم في SyncManager وأماكن أخرى
        const val CASH_CLIENT_UUID   = "00000000-0000-0000-0000-000000000001"
        const val CASH_SUPPLIER_UUID = "00000000-0000-0000-0000-000000000002"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "verto_db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys=ON")
                            installOptimalMaintenanceIntegrityGuards(db)
                            installInvoiceReturnIntegrityGuards(db)
                            installPurchaseCycleIntegrityGuards(db)
                            installPartyHistoricalDeleteGuard(db)
                            installB11ConflictIntegrityGuards(db)
                            installB12ExpenseRevisionIntegrityGuards(db)
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
