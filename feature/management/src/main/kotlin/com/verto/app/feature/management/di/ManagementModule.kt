package com.verto.app.feature.management.di

import com.verto.app.feature.management.data.BenzineManagementIntegrationContributor
import com.verto.app.feature.management.data.DefaultManagementIntegrationCatalog
import com.verto.app.feature.management.domain.repository.ManagementIntegrationBadgeContributor
import com.verto.app.feature.management.domain.repository.ManagementIntegrationCatalog
import com.verto.app.feature.management.domain.repository.ManagementIntegrationContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManagementModule {
    @Multibinds
    abstract fun bindManagementIntegrationBadgeContributors(): Set<ManagementIntegrationBadgeContributor>

    @Binds
    @Singleton
    abstract fun bindManagementIntegrationCatalog(
        implementation: DefaultManagementIntegrationCatalog,
    ): ManagementIntegrationCatalog

    @Binds
    @IntoSet
    abstract fun bindBenzineManagementIntegrationContributor(
        implementation: BenzineManagementIntegrationContributor,
    ): ManagementIntegrationContributor
}
