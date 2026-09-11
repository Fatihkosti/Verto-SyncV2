package com.verto.app.feature.party.bridge

import com.verto.app.feature.party.application.ledger.PartyLedgerEventSource
import com.verto.app.feature.party.domain.repository.PartyFinancialQuery
import com.verto.app.feature.party.domain.repository.PartySyncFallbackPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PartyCrossFeatureBridgeModule {
    @Provides @Singleton
    fun providePartyFinancialQuery(adapter: AppPartyFinancialQuery): PartyFinancialQuery = adapter

    @Provides @Singleton
    fun providePartyLedgerEventSource(adapter: AppPartyLedgerEventSource): PartyLedgerEventSource = adapter

    @Provides @Singleton
    fun providePartySyncFallbackPort(adapter: AppPartySyncFallbackAdapter): PartySyncFallbackPort = adapter
}
