package com.verto.app.feature.invoice.bridge

import com.verto.app.feature.invoice.application.port.InvoicePresentationPort
import com.verto.app.feature.invoice.domain.port.InvoiceAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceCompanyTimelinePort
import com.verto.app.feature.invoice.domain.port.InvoiceNumberPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnCreditPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnStorePort
import com.verto.app.feature.invoice.domain.port.InvoiceSettingsPort
import com.verto.app.feature.invoice.domain.port.InvoiceStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceStorePort
import com.verto.app.feature.invoice.domain.port.InvoiceSyncSchedulerPort
import com.verto.app.feature.invoice.domain.port.PurchaseSupplierRecommendationPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InvoiceAppBridgeModule {
    @Provides @Singleton fun provideInvoicePresentationPort(bridge: InvoicePresentationBridge): InvoicePresentationPort = bridge
    @Provides @Singleton fun providePurchaseSupplierRecommendationPort(
        adapter: PartyPurchaseSupplierRecommendationAdapter,
    ): PurchaseSupplierRecommendationPort = adapter
    @Provides @Singleton fun provideInvoiceStorePort(adapter: RepositoryInvoiceStoreAdapter): InvoiceStorePort = adapter
    @Provides @Singleton fun provideInvoiceCompanyTimelinePort(
        adapter: RoomInvoiceCompanyTimelineAdapter,
    ): InvoiceCompanyTimelinePort = adapter
    @Provides @Singleton fun provideInvoiceStockPort(adapter: InventoryInvoiceStockAdapter): InvoiceStockPort = adapter
    @Provides @Singleton fun provideInvoiceCashPort(adapter: CashRegisterInvoiceCashAdapter): InvoiceCashPort = adapter
    @Provides @Singleton fun provideInvoiceReturnStorePort(adapter: RoomInvoiceReturnStoreAdapter): InvoiceReturnStorePort = adapter
    @Provides @Singleton fun provideInvoiceReturnStockPort(adapter: InventoryInvoiceReturnStockAdapter): InvoiceReturnStockPort = adapter
    @Provides @Singleton fun provideInvoiceReturnCreditPort(adapter: RoomInvoiceReturnCreditAdapter): InvoiceReturnCreditPort = adapter
    @Provides @Singleton fun provideInvoiceReturnCashPort(adapter: CashRegisterInvoiceReturnCashAdapter): InvoiceReturnCashPort = adapter
    @Provides @Singleton fun provideInvoiceReturnAuthorizationPort(adapter: PermissionInvoiceReturnAuthorizationAdapter): InvoiceReturnAuthorizationPort = adapter
    @Provides @Singleton fun provideInvoiceAuthorizationPort(adapter: PermissionInvoiceAuthorizationAdapter): InvoiceAuthorizationPort = adapter
    @Provides @Singleton fun provideInvoiceSettingsPort(adapter: PreferencesInvoiceSettingsAdapter): InvoiceSettingsPort = adapter
    @Provides @Singleton fun provideInvoiceNumberPort(adapter: AllocatorInvoiceNumberAdapter): InvoiceNumberPort = adapter
    @Provides @Singleton fun provideInvoiceSyncSchedulerPort(
        adapter: WorkManagerInvoiceSyncSchedulerAdapter,
    ): InvoiceSyncSchedulerPort = adapter
}
