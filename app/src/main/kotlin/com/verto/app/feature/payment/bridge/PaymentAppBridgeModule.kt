package com.verto.app.feature.payment.bridge

import com.verto.app.feature.payment.application.port.PurchaseShipmentGateway
import com.verto.app.feature.payment.application.port.PaymentDebtWorkflowPort
import com.verto.app.feature.payment.application.port.PaymentInvoiceSummaryPort
import com.verto.app.feature.payment.domain.port.AdvanceCreditPort
import com.verto.app.feature.payment.domain.port.PaymentAuthorizationPort
import com.verto.app.feature.payment.domain.port.PaymentCashPort
import com.verto.app.feature.payment.domain.port.PaymentCompanyTimelinePort
import com.verto.app.feature.payment.domain.port.PaymentRemotePort
import com.verto.app.feature.payment.domain.port.PaymentStorePort
import com.verto.app.feature.payment.domain.port.PurchaseReceiptPaymentGuardPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PaymentAppBridgeModule {
    @Provides @Singleton fun providePaymentDebtWorkflowPort(bridge: PaymentDebtWorkflowBridge): PaymentDebtWorkflowPort = bridge
    @Provides @Singleton fun providePaymentInvoiceSummaryPort(bridge: PaymentInvoiceSummaryBridge): PaymentInvoiceSummaryPort = bridge
    @Provides @Singleton fun providePaymentStorePort(adapter: RepositoryPaymentStoreAdapter): PaymentStorePort = adapter
    @Provides @Singleton fun providePaymentCompanyTimelinePort(
        adapter: RoomPaymentCompanyTimelineAdapter,
    ): PaymentCompanyTimelinePort = adapter
    @Provides @Singleton fun providePaymentCashPort(adapter: CashRegisterPaymentCashAdapter): PaymentCashPort = adapter
    @Provides @Singleton fun providePaymentAuthorizationPort(adapter: PermissionPaymentAuthorizationAdapter): PaymentAuthorizationPort = adapter
    @Provides @Singleton fun providePaymentRemotePort(adapter: DefaultPaymentRemoteAdapter): PaymentRemotePort = adapter
    @Provides @Singleton fun providePurchaseReceiptPaymentGuardPort(adapter: RoomPurchaseReceiptPaymentGuardAdapter): PurchaseReceiptPaymentGuardPort = adapter
    @Provides @Singleton fun provideAdvanceCreditPort(adapter: RoomAdvanceCreditAdapter): AdvanceCreditPort = adapter
    @Provides @Singleton fun providePurchaseShipmentGateway(bridge: PaymentShipmentBridge): PurchaseShipmentGateway = bridge
}
