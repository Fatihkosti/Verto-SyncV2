package com.verto.app.feature.payment.di

import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.feature.payment.application.AddPaymentUseCase
import com.verto.app.feature.payment.application.BulkPaymentCoordinator
import com.verto.app.feature.payment.application.RecordPaymentCoordinator
import com.verto.app.feature.payment.application.ReversePaymentCoordinator
import com.verto.app.feature.payment.application.ReversePaymentUseCase
import com.verto.app.feature.payment.application.command.BulkPaymentAllocator
import com.verto.app.feature.payment.application.activityevent.PaymentActivityEventProvider
import com.verto.app.feature.payment.application.activityevent.PaymentActivityEventSource
import com.verto.app.feature.payment.application.search.PaymentHomeSearchProvider
import com.verto.app.feature.payment.application.search.PaymentHomeSearchSource
import com.verto.app.feature.payment.data.search.RoomPaymentHomeSearchSource
import com.verto.app.feature.payment.data.activityevent.RoomPaymentActivityEventSource
import com.verto.app.feature.payment.data.sync.CashSyncParticipant
import com.verto.app.feature.payment.domain.port.PaymentReversalPort
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.ActivityEventProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {
    @Provides
    fun provideAddPaymentUseCase(coordinator: RecordPaymentCoordinator): AddPaymentUseCase =
        AddPaymentUseCase(coordinator)

    @Provides
    fun provideReversePaymentUseCase(coordinator: ReversePaymentCoordinator): ReversePaymentUseCase =
        ReversePaymentUseCase(coordinator)

    @Provides
    fun providePaymentReversalPort(useCase: ReversePaymentUseCase): PaymentReversalPort = useCase

    @Provides
    fun provideBulkPaymentAllocator(coordinator: BulkPaymentCoordinator): BulkPaymentAllocator =
        BulkPaymentAllocator(coordinator)

    @Provides @Singleton
    fun providePaymentHomeSearchSource(adapter: RoomPaymentHomeSearchSource): PaymentHomeSearchSource = adapter

    @Provides @IntoSet @Singleton
    fun providePaymentHomeSearchProvider(provider: PaymentHomeSearchProvider): HomeSearchProvider = provider


    @Provides @Singleton
    fun providePaymentActivityEventSource(
        adapter: RoomPaymentActivityEventSource,
    ): PaymentActivityEventSource = adapter

    @Provides @IntoSet @Singleton
    fun providePaymentActivityEventProvider(
        provider: PaymentActivityEventProvider,
    ): ActivityEventProvider = provider

    @Provides @IntoSet @Singleton
    fun provideCashSyncParticipant(runtime: SyncRuntime): SyncParticipant =
        CashSyncParticipant(runtime)
}
