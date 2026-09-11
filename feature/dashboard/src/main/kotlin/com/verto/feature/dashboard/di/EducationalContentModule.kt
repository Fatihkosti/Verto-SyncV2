package com.verto.feature.dashboard.di

import com.verto.app.feature.dashboard.data.education.RoomEducationalContentRepository
import com.verto.app.feature.dashboard.data.education.EducationalContentSyncParticipant
import com.verto.app.data.sync.SyncParticipant
import com.verto.feature.dashboard.api.EducationalContentRepository
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EducationalContentModule {
    @Binds
    @Singleton
    abstract fun bindEducationalContentRepository(
        implementation: RoomEducationalContentRepository,
    ): EducationalContentRepository

    @Binds
    @IntoSet
    abstract fun bindEducationalContentSyncParticipant(
        implementation: EducationalContentSyncParticipant,
    ): SyncParticipant
}
