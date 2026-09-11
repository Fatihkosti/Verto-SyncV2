package com.verto.app.feature.integration.optimal.di

import com.verto.app.feature.integration.optimal.application.pendingaction.MaintenancePendingActionClock
import com.verto.app.feature.integration.optimal.application.pendingaction.MaintenancePendingActionProvider
import com.verto.app.feature.integration.optimal.application.activityevent.MaintenanceActivityEventProvider
import com.verto.app.feature.integration.optimal.application.activityevent.MaintenanceActivityEventSource
import com.verto.app.feature.integration.optimal.data.pendingaction.SystemMaintenancePendingActionClock
import com.verto.app.feature.integration.optimal.data.activityevent.RoomMaintenanceActivityEventSource
import com.verto.feature.dashboard.api.PendingActionProvider
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.app.feature.integration.optimal.data.DefaultOptimalHomeAccessSource
import com.verto.app.feature.integration.optimal.data.ContractGuardedOptimalOutboxEventExecutor
import com.verto.app.feature.integration.optimal.data.SessionOptimalSyncSessionGuard
import com.verto.app.feature.integration.optimal.data.DefaultOptimalHomeLocalCounters
import com.verto.app.feature.integration.optimal.data.DefaultOptimalOperationGuard
import com.verto.app.feature.integration.optimal.data.AppPrivateOptimalAttachmentStore
import com.verto.app.feature.integration.optimal.data.AndroidAudioRecorder
import com.verto.app.feature.integration.optimal.data.GuardedOptimalRegistrationRepository
import com.verto.app.feature.integration.optimal.data.OptimalMaintenanceInvoiceQueryAdapter
import com.verto.app.feature.integration.optimal.data.OptimalRegistrationRemoteSource
import com.verto.app.feature.integration.optimal.data.PinnedOptimalBackendContractGate
import com.verto.app.feature.integration.optimal.data.RoomOptimalCompanyRepository
import com.verto.app.feature.integration.optimal.data.RoomOptimalCompanyInvoicesRepository
import com.verto.app.feature.integration.optimal.data.RoomOptimalVehicleRepository
import com.verto.app.feature.integration.optimal.data.RoomOptimalMaintenanceRepository
import com.verto.app.feature.integration.optimal.data.RoomMaintenanceFollowUpRepository
import com.verto.app.feature.integration.optimal.data.RoomOptimalMaintenanceRecordsRepository
import com.verto.app.feature.integration.optimal.data.RoomOptimalOutboxRepository
import com.verto.app.feature.integration.optimal.data.SupabaseOptimalRegistrationRemoteSource
import com.verto.app.feature.integration.optimal.data.SystemOptimalClock
import com.verto.app.feature.integration.optimal.data.SystemOptimalMessagingIdGenerator
import com.verto.app.feature.integration.optimal.data.SystemOptimalLeaseTokenFactory
import com.verto.app.feature.integration.optimal.domain.model.OptimalBackendContractGate
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.application.DefaultOptimalSyncRecoveryPolicy
import com.verto.app.feature.integration.optimal.application.ScheduleOptimalSyncUseCase
import com.verto.app.feature.integration.optimal.application.OptimalSyncRecoveryPolicy
import com.verto.app.feature.integration.optimal.domain.port.AudioRecorder
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdGenerator
import com.verto.app.feature.integration.optimal.domain.port.OptimalAttachmentStore
import com.verto.app.feature.integration.optimal.domain.port.OptimalLeaseTokenFactory
import com.verto.app.feature.integration.optimal.domain.port.OptimalOutboxEventExecutor
import com.verto.app.feature.integration.optimal.domain.port.OptimalRemoteOutboxDispatcher
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncCoordinator
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncSessionGuard
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeAccessSource
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeLocalCounters
import com.verto.app.feature.integration.optimal.domain.repository.OptimalClock
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyInvoicesRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalVehicleRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceRepository
import com.verto.app.feature.integration.optimal.domain.repository.MaintenanceFollowUpRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceDetailsRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceRecordsRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalOutboxRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OptimalIntegrationModule {
    /** Empty until a backend contract is verified and a concrete dispatcher is contributed. */
    @Multibinds
    abstract fun bindOptimalRemoteOutboxDispatchers(): Set<OptimalRemoteOutboxDispatcher>

    @Binds
    @Singleton
    abstract fun bindOptimalHomeAccessSource(
        implementation: DefaultOptimalHomeAccessSource,
    ): OptimalHomeAccessSource

    @Binds
    @Singleton
    abstract fun bindOptimalHomeLocalCounters(
        implementation: DefaultOptimalHomeLocalCounters,
    ): OptimalHomeLocalCounters

    @Binds
    @Singleton
    abstract fun bindOptimalCompanyRepository(
        implementation: RoomOptimalCompanyRepository,
    ): OptimalCompanyRepository

    @Binds
    @Singleton
    abstract fun bindOptimalCompanyInvoicesRepository(
        implementation: RoomOptimalCompanyInvoicesRepository,
    ): OptimalCompanyInvoicesRepository

    @Binds
    @Singleton
    abstract fun bindOptimalVehicleRepository(
        implementation: RoomOptimalVehicleRepository,
    ): OptimalVehicleRepository

    @Binds
    @Singleton
    abstract fun bindOptimalMaintenanceRepository(
        implementation: RoomOptimalMaintenanceRepository,
    ): OptimalMaintenanceRepository

    @Binds
    @Singleton
    abstract fun bindMaintenanceFollowUpRepository(
        implementation: RoomMaintenanceFollowUpRepository,
    ): MaintenanceFollowUpRepository

    @Binds
    @Singleton
    abstract fun bindMaintenancePendingActionClock(
        implementation: SystemMaintenancePendingActionClock,
    ): MaintenancePendingActionClock

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMaintenancePendingActionProvider(
        implementation: MaintenancePendingActionProvider,
    ): PendingActionProvider

    @Binds
    @Singleton
    abstract fun bindMaintenanceActivityEventSource(
        implementation: RoomMaintenanceActivityEventSource,
    ): MaintenanceActivityEventSource

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMaintenanceActivityEventProvider(
        implementation: MaintenanceActivityEventProvider,
    ): ActivityEventProvider

    @Binds
    @Singleton
    abstract fun bindOptimalMaintenanceRecordsRepository(
        implementation: RoomOptimalMaintenanceRecordsRepository,
    ): OptimalMaintenanceRecordsRepository


    @Binds
    @Singleton
    abstract fun bindOptimalOutboxRepository(
        implementation: RoomOptimalOutboxRepository,
    ): OptimalOutboxRepository

    @Binds
    @Singleton
    abstract fun bindOptimalLeaseTokenFactory(
        implementation: SystemOptimalLeaseTokenFactory,
    ): OptimalLeaseTokenFactory

    @Binds
    @Singleton
    abstract fun bindOptimalSyncRecoveryPolicy(
        implementation: DefaultOptimalSyncRecoveryPolicy,
    ): OptimalSyncRecoveryPolicy

    @Binds
    @Singleton
    abstract fun bindOptimalOutboxEventExecutor(
        implementation: ContractGuardedOptimalOutboxEventExecutor,
    ): OptimalOutboxEventExecutor

    @Binds
    @Singleton
    abstract fun bindOptimalSyncSessionGuard(
        implementation: SessionOptimalSyncSessionGuard,
    ): OptimalSyncSessionGuard

    @Binds
    @Singleton
    abstract fun bindOptimalSyncCoordinator(
        implementation: ScheduleOptimalSyncUseCase,
    ): OptimalSyncCoordinator

    @Binds
    @Singleton
    abstract fun bindOptimalMaintenanceDetailsRepository(
        implementation: OptimalMaintenanceInvoiceQueryAdapter,
    ): OptimalMaintenanceDetailsRepository

    @Binds
    @Singleton
    abstract fun bindOptimalAttachmentStore(
        implementation: AppPrivateOptimalAttachmentStore,
    ): OptimalAttachmentStore

    @Binds
    abstract fun bindAudioRecorder(
        implementation: AndroidAudioRecorder,
    ): AudioRecorder

    @Binds
    @Singleton
    abstract fun bindOptimalMessagingIdGenerator(
        implementation: SystemOptimalMessagingIdGenerator,
    ): OptimalMessagingIdGenerator

    @Binds
    @Singleton
    abstract fun bindOptimalRegistrationRemoteSource(
        implementation: SupabaseOptimalRegistrationRemoteSource,
    ): OptimalRegistrationRemoteSource

    @Binds
    @Singleton
    abstract fun bindOptimalBackendContractGate(
        implementation: PinnedOptimalBackendContractGate,
    ): OptimalBackendContractGate

    @Binds
    @Singleton
    abstract fun bindOptimalOperationGuard(
        implementation: DefaultOptimalOperationGuard,
    ): OptimalOperationGuard

    @Binds
    @Singleton
    abstract fun bindOptimalClock(
        implementation: SystemOptimalClock,
    ): OptimalClock

    @Binds
    @Singleton
    abstract fun bindOptimalRegistrationRepository(
        implementation: GuardedOptimalRegistrationRepository,
    ): OptimalRegistrationRepository

}
