package com.verto.app.feature.party.bridge

import com.verto.app.feature.party.application.port.PartyCashBalancePort
import com.verto.app.feature.party.application.port.PartyPaymentAllocator
import com.verto.app.feature.party.application.port.PartyInvoiceVoidPort
import com.verto.app.feature.party.application.port.SupplierIntelligenceSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PartyAppBridgeModule {
    @Binds @Singleton
    abstract fun bindPartyPaymentAllocator(adapter: AppPartyPaymentAllocator): PartyPaymentAllocator

    @Binds @Singleton
    abstract fun bindPartyCashBalance(adapter: AppPartyCashBalanceAdapter): PartyCashBalancePort

    @Binds @Singleton
    abstract fun bindPartyInvoiceVoid(adapter: AppPartyInvoiceVoidAdapter): PartyInvoiceVoidPort

    @Binds @Singleton
    abstract fun bindSupplierIntelligenceSource(adapter: AppSupplierIntelligenceSource): SupplierIntelligenceSource
}
