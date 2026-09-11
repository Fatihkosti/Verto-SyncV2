package com.verto.app.di

import android.content.Context
import com.verto.app.core.audit.domain.AuditMaintenancePort
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.activityevent.AuditActivityEventProvider
import com.verto.app.core.audit.activityevent.AuditActivityEventSource
import com.verto.app.core.audit.activityevent.RoomAuditActivityEventSource
import com.verto.app.core.export.data.AndroidDocumentShareAdapter
import com.verto.app.core.export.domain.DocumentSharePort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.backup.BackupManager
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.AuditLogDao
import com.verto.app.data.local.dao.CashRegisterDao
import com.verto.app.data.remote.InvoiceNumberAllocator
import com.verto.app.data.remote.PushTokenRepository
import com.verto.app.data.repository.SubscriptionManager
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.utils.AuditLogger
import com.verto.app.utils.CashRegisterManager
import com.verto.app.utils.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import com.verto.feature.dashboard.api.ActivityEventProvider

@Module
@InstallIn(SingletonComponent::class)
object InfrastructureModule {

    @Provides
    @Singleton
    fun provideCashRegisterManager(
        dao: CashRegisterDao,
        dependencies: com.verto.app.utils.CashRegisterDependencies
    ): CashRegisterManager = CashRegisterManager(dao, dependencies)

    @Provides
    @Singleton
    fun provideAuditLogger(dao: AuditLogDao): AuditLogger = AuditLogger(dao)

    @Provides
    fun provideWriteAuditPort(logger: AuditLogger): WriteAuditPort = logger

    @Provides
    fun provideAuditMaintenancePort(logger: AuditLogger): AuditMaintenancePort = logger


    @Provides
    @Singleton
    fun provideAuditActivityEventSource(
        adapter: RoomAuditActivityEventSource,
    ): AuditActivityEventSource = adapter

    @Provides
    @IntoSet
    @Singleton
    fun provideAuditActivityEventProvider(
        provider: AuditActivityEventProvider,
    ): ActivityEventProvider = provider

    @Provides
    @Singleton
    fun provideDocumentSharePort(
        adapter: AndroidDocumentShareAdapter
    ): DocumentSharePort = adapter

    @Provides
    @Singleton
    fun providePushTokenRepository(@ApplicationContext context: Context): PushTokenRepository =
        PushTokenRepository(context)

    @Provides
    @Singleton
    fun provideInvoiceNumberAllocator(
        preferencesManager: PreferencesManager
    ): InvoiceNumberAllocator = InvoiceNumberAllocator(preferencesManager)

    @Provides
    @Singleton
    fun provideSubscriptionManager(
        preferencesManager: PreferencesManager
    ): SubscriptionManager = SubscriptionManager(preferencesManager)

    @Provides
    @Singleton
    fun provideBackupManager(
        @ApplicationContext context: Context,
        db: AppDatabase,
        preferencesManager: PreferencesManager,
        sessionReader: SessionReader,
        outbox: UnifiedOutboxWriter,
    ): BackupManager = BackupManager(context, db, preferencesManager, sessionReader, outbox)
}
