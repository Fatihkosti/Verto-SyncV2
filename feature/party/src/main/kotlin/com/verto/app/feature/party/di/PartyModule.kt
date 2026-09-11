package com.verto.app.feature.party.di

import com.verto.app.data.local.dao.ClientDao
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.repository.ClientRepository
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.utils.PreferencesManager
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.feature.party.application.CalculateClientBalanceUseCase
import com.verto.app.feature.party.application.activityevent.PartyActivityEventProvider
import com.verto.app.feature.party.application.activityevent.PartyActivityEventSource
import com.verto.app.feature.party.application.search.PartyHomeSearchProvider
import com.verto.app.feature.party.application.pendingaction.InactiveCustomerPendingActionClock
import com.verto.app.feature.party.application.pendingaction.InactiveCustomerPendingActionProvider
import com.verto.app.feature.party.application.pendingaction.InactiveCustomerPendingActionSource
import com.verto.app.feature.party.application.quickaction.PartyQuickActionProvider
import com.verto.app.feature.party.application.search.PartyHomeSearchSource
import com.verto.app.feature.party.application.query.PartyPagingGateway
import com.verto.app.feature.party.application.port.PartyPresentationCommand
import com.verto.app.feature.party.application.port.PartyPresentationQuery
import com.verto.app.feature.party.application.ledger.PartyLedgerEventSource
import com.verto.app.feature.party.application.PartyRoleCommandPort
import com.verto.app.feature.party.application.PartyRoleReadPort
import com.verto.app.feature.party.data.PartyPresentationAdapter
import com.verto.app.feature.party.data.RoomPartyRoleCommandAdapter
import com.verto.app.feature.party.data.RoomPartyRoleReadAdapter
import com.verto.app.feature.party.data.RoomPartyRemoteMirrorAdapter
import com.verto.app.feature.party.data.search.RoomPartyHomeSearchSource
import com.verto.app.feature.party.data.activityevent.RoomPartyActivityEventSource
import com.verto.app.feature.party.data.pendingaction.RoomInactiveCustomerPendingActionSource
import com.verto.app.feature.party.data.pendingaction.SystemInactiveCustomerPendingActionClock
import com.verto.app.feature.party.data.sync.ClientSyncParticipant
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.party.domain.repository.PartyRoleProjectionGateway
import com.verto.app.feature.party.domain.repository.PartyRemoteMirrorGateway
import com.verto.app.feature.party.domain.repository.PartyFinancialQuery
import com.verto.app.feature.party.domain.repository.PartySyncFallbackPort
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
object PartyModule {
    @Provides @Singleton
    fun provideClientRepository(
        clientDao: ClientDao,
        preferencesManager: PreferencesManager,
        permissionProvider: PermissionProvider,
        auditLogger: WriteAuditPort,
        syncFallback: PartySyncFallbackPort,
        database: AppDatabase,
        roleCommands: PartyRoleCommandPort,
        sessionReader: SessionReader,
        unifiedOutboxWriter: UnifiedOutboxWriter,
    ): ClientRepository = ClientRepository(
        clientDao,
        preferencesManager,
        permissionProvider,
        auditLogger,
        syncFallback,
        database,
        roleCommands,
        sessionReader,
        unifiedOutboxWriter,
    )

    @Provides @Singleton
    fun providePartyDirectoryGateway(repository: ClientRepository): PartyDirectoryGateway = repository

    @Provides @Singleton
    fun providePartyPagingGateway(repository: ClientRepository): PartyPagingGateway = repository

    @Provides @Singleton
    fun providePartyRoleProjectionGateway(adapter: com.verto.app.feature.party.data.RoomPartyRoleProjectionGateway): PartyRoleProjectionGateway = adapter

    @Provides @Singleton
    fun providePartyRemoteMirrorGateway(adapter: RoomPartyRemoteMirrorAdapter): PartyRemoteMirrorGateway = adapter



    @Provides @Singleton
    fun providePartyRoleCommandPort(adapter: RoomPartyRoleCommandAdapter): PartyRoleCommandPort = adapter

    @Provides @Singleton
    fun providePartyRoleReadPort(adapter: RoomPartyRoleReadAdapter): PartyRoleReadPort = adapter

    @Provides @Singleton
    fun providePartyPresentationQuery(adapter: PartyPresentationAdapter): PartyPresentationQuery = adapter

    @Provides @Singleton
    fun providePartyPresentationCommand(adapter: PartyPresentationAdapter): PartyPresentationCommand = adapter

    @Provides @Singleton
    fun providePartyHomeSearchSource(adapter: RoomPartyHomeSearchSource): PartyHomeSearchSource = adapter

    @Provides @IntoSet @Singleton
    fun providePartyHomeSearchProvider(provider: PartyHomeSearchProvider): HomeSearchProvider = provider

    @Provides @IntoSet @Singleton
    fun providePartyQuickActionProvider(provider: PartyQuickActionProvider): QuickActionProvider = provider


    @Provides @Singleton
    fun provideInactiveCustomerPendingActionSource(
        adapter: RoomInactiveCustomerPendingActionSource,
    ): InactiveCustomerPendingActionSource = adapter

    @Provides @Singleton
    fun provideInactiveCustomerPendingActionClock(
        clock: SystemInactiveCustomerPendingActionClock,
    ): InactiveCustomerPendingActionClock = clock

    @Provides @IntoSet @Singleton
    fun provideInactiveCustomerPendingActionProvider(
        provider: InactiveCustomerPendingActionProvider,
    ): PendingActionProvider = provider

    @Provides @Singleton
    fun providePartyActivityEventSource(
        adapter: RoomPartyActivityEventSource,
    ): PartyActivityEventSource = adapter

    @Provides @IntoSet @Singleton
    fun providePartyActivityEventProvider(
        provider: PartyActivityEventProvider,
    ): ActivityEventProvider = provider


    @Provides
    fun provideCalculateClientBalanceUseCase(): CalculateClientBalanceUseCase =
        CalculateClientBalanceUseCase()

    @Provides @IntoSet @Singleton
    fun provideClientSyncParticipant(runtime: SyncRuntime): SyncParticipant =
        ClientSyncParticipant(runtime)
}
