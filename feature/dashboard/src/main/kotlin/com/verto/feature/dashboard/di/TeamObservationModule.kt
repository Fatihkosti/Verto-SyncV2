package com.verto.feature.dashboard.di

import com.verto.app.data.sync.SyncParticipant
import com.verto.app.feature.dashboard.data.observation.RoomTeamObservationRepository
import com.verto.app.feature.dashboard.data.observation.TeamObservationSyncParticipant
import com.verto.feature.dashboard.api.TeamObservationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TeamObservationModule {
    @Binds
    @Singleton
    abstract fun bindTeamObservationRepository(
        implementation: RoomTeamObservationRepository,
    ): TeamObservationRepository

    @Binds
    @IntoSet
    abstract fun bindTeamObservationSyncParticipant(
        implementation: TeamObservationSyncParticipant,
    ): SyncParticipant
}
