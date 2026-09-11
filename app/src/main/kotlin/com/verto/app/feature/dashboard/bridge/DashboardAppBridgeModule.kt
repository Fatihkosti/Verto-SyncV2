package com.verto.app.feature.dashboard.bridge

import com.verto.feature.dashboard.api.EducationalAudienceDirectory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardAppBridgeModule {
    @Binds
    @Singleton
    abstract fun bindEducationalAudienceDirectory(
        implementation: EducationalAudienceDirectoryAdapter,
    ): EducationalAudienceDirectory
}
