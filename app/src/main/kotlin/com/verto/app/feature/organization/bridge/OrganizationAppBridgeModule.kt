package com.verto.app.feature.organization.bridge
import com.verto.app.data.repository.OrgSettingsRepository
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.feature.organization.bridge.OrganizationSettingsGatewayAdapter
import com.verto.app.feature.organization.bridge.OrganizationTeamGatewayAdapter
import com.verto.app.feature.organization.application.EmployeePerformanceQuery
import com.verto.app.feature.organization.domain.repository.OrganizationSettingsGateway
import com.verto.app.feature.organization.domain.repository.OrganizationTeamGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrganizationAppBridgeModule {
    @Provides @Singleton
    fun provideOrganizationSettingsGateway(adapter: OrganizationSettingsGatewayAdapter): OrganizationSettingsGateway = adapter
    @Provides @Singleton
    fun provideOrganizationTeamGateway(adapter: OrganizationTeamGatewayAdapter): OrganizationTeamGateway = adapter
    @Provides @Singleton
    fun provideEmployeePerformanceQuery(adapter: EmployeePerformanceQueryAdapter): EmployeePerformanceQuery = adapter

    @Provides @IntoSet @Singleton
    fun provideOrganizationSyncParticipant(
        runtime: SyncRuntime,
        orgSettingsRepository: OrgSettingsRepository
    ): SyncParticipant = OrganizationSyncParticipant(runtime, orgSettingsRepository)
}
