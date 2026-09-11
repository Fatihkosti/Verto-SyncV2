package com.verto.app.feature.invoice.di

import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.feature.invoice.application.InvoiceVoidCoordinator
import com.verto.app.feature.invoice.application.InvoiceWriteCoordinator
import com.verto.app.feature.invoice.application.PurchaseCycleCoordinator
import com.verto.app.feature.invoice.application.activityevent.InvoiceActivityEventProvider
import com.verto.app.feature.invoice.application.activityevent.InvoiceActivityEventSource
import com.verto.app.feature.invoice.application.VoidInvoiceUseCase
import com.verto.app.feature.invoice.application.command.SaveInvoiceUseCase
import com.verto.app.feature.invoice.application.command.InvoiceVoidCommand
import com.verto.app.feature.invoice.application.search.InvoiceHomeSearchProvider
import com.verto.app.feature.invoice.application.pendingaction.FinancialPendingActionClock
import com.verto.app.feature.invoice.application.pendingaction.FinancialPendingActionProvider
import com.verto.app.feature.invoice.application.pendingaction.FinancialPendingActionSource
import com.verto.app.feature.invoice.application.quickaction.InvoiceQuickActionProvider
import com.verto.app.feature.invoice.application.search.InvoiceHomeSearchSource
import com.verto.app.feature.invoice.data.search.RoomInvoiceHomeSearchSource
import com.verto.app.feature.invoice.data.activityevent.RoomInvoiceActivityEventSource
import com.verto.app.feature.invoice.data.pendingaction.RoomFinancialPendingActionSource
import com.verto.app.feature.invoice.data.pendingaction.SystemFinancialPendingActionClock
import com.verto.app.feature.invoice.data.WhatsAppInvoiceMessageShareAdapter
import com.verto.app.feature.invoice.data.RoomPurchaseCycleStore
import com.verto.app.feature.invoice.data.sync.InvoiceSyncParticipant
import com.verto.app.feature.invoice.domain.repository.InvoiceMessageShareGateway
import com.verto.app.feature.invoice.domain.port.PurchaseCycleStorePort
import com.verto.app.feature.invoice.domain.port.PurchaseCycleInvoicePort
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.PendingActionProvider
import com.verto.feature.dashboard.api.QuickActionProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InvoiceModule {
    @Provides @Singleton
    fun providePurchaseCycleStore(adapter: RoomPurchaseCycleStore): PurchaseCycleStorePort = adapter

    @Provides @Singleton
    fun providePurchaseCycleInvoicePort(coordinator: PurchaseCycleCoordinator): PurchaseCycleInvoicePort = coordinator

    @Provides @Singleton
    fun provideInvoiceMessageShareGateway(
        adapter: WhatsAppInvoiceMessageShareAdapter
    ): InvoiceMessageShareGateway = adapter

    @Provides
    fun provideSaveInvoiceUseCase(coordinator: InvoiceWriteCoordinator): SaveInvoiceUseCase =
        SaveInvoiceUseCase(coordinator)

    @Provides
    fun provideVoidInvoiceUseCase(coordinator: InvoiceVoidCoordinator): VoidInvoiceUseCase =
        VoidInvoiceUseCase(coordinator)

    @Provides
    fun provideInvoiceVoidCommand(useCase: VoidInvoiceUseCase): InvoiceVoidCommand = useCase

    @Provides @Singleton
    fun provideInvoiceHomeSearchSource(adapter: RoomInvoiceHomeSearchSource): InvoiceHomeSearchSource = adapter

    @Provides @IntoSet @Singleton
    fun provideInvoiceHomeSearchProvider(provider: InvoiceHomeSearchProvider): HomeSearchProvider = provider

    @Provides @IntoSet @Singleton
    fun provideInvoiceQuickActionProvider(provider: InvoiceQuickActionProvider): QuickActionProvider = provider

    @Provides @Singleton
    fun provideFinancialPendingActionSource(adapter: RoomFinancialPendingActionSource): FinancialPendingActionSource = adapter

    @Provides @Singleton
    fun provideFinancialPendingActionClock(clock: SystemFinancialPendingActionClock): FinancialPendingActionClock = clock

    @Provides @IntoSet @Singleton
    fun provideFinancialPendingActionProvider(provider: FinancialPendingActionProvider): PendingActionProvider = provider

    @Provides @Singleton
    fun provideInvoiceActivityEventSource(
        adapter: RoomInvoiceActivityEventSource,
    ): InvoiceActivityEventSource = adapter

    @Provides @IntoSet @Singleton
    fun provideInvoiceActivityEventProvider(
        provider: InvoiceActivityEventProvider,
    ): ActivityEventProvider = provider

    @Provides @IntoSet @Singleton
    fun provideInvoiceSyncParticipant(runtime: SyncRuntime): SyncParticipant =
        InvoiceSyncParticipant(runtime)
}
