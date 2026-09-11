package com.verto.app.di

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.*
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.repository.*
import com.verto.app.core.audit.domain.AuditMaintenancePort
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.utils.CashRegisterManager
import com.verto.app.utils.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {


    @Provides
    @Singleton
    fun provideInvoiceRepository(
        invoiceDao: InvoiceDao,
        paymentDao: PaymentDao,
        preferencesManager: PreferencesManager,
        db: AppDatabase,
        sessionReader: SessionReader,
        unifiedOutboxWriter: UnifiedOutboxWriter,
    ): InvoiceRepository = InvoiceRepository(
        invoiceDao,
        paymentDao,
        preferencesManager,
        db,
        sessionReader,
        unifiedOutboxWriter,
    )

    @Provides
    @Singleton
    fun provideExpenseRepository(
        db: AppDatabase,
        expenseDao: ExpenseDao,
        cashRegisterManager: CashRegisterManager,
        preferencesManager: PreferencesManager,
        permissionProvider: PermissionProvider,
        auditLogger: WriteAuditPort,
        sessionReader: SessionReader,
        unifiedOutboxWriter: UnifiedOutboxWriter,
        syncBatchCoordinatorV2: com.verto.app.data.sync.SyncBatchCoordinatorV2,
    ): ExpenseRepository = ExpenseRepository(
        expenseDao,
        cashRegisterManager,
        preferencesManager,
        db,
        permissionProvider,
        auditLogger,
        sessionReader,
        unifiedOutboxWriter,
        syncBatchCoordinatorV2,
    )


    @Provides
    @Singleton
    fun provideNoteRepository(
        noteDao: NoteDao,
        db: AppDatabase,
        sessionReader: SessionReader,
        unifiedOutboxWriter: UnifiedOutboxWriter,
    ): NoteRepository = NoteRepository(noteDao, db, sessionReader, unifiedOutboxWriter)

    @Provides
    @Singleton
    fun provideWithdrawalRepository(
        authRepository: AuthRepository
    ): WithdrawalRepository = WithdrawalRepository(authRepository)

    @Provides
    @Singleton
    fun provideAuditLogRepository(
        dao: AuditLogDao,
        auditMaintenance: AuditMaintenancePort
    ): AuditLogRepository = AuditLogRepository(dao, auditMaintenance)
}
