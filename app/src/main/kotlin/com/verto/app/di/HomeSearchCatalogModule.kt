package com.verto.app.di

import com.verto.app.ui.navigation.search.AppCatalogHomeSearchProvider
import com.verto.feature.dashboard.api.HomeSearchProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeSearchCatalogModule {
    @Provides
    @IntoSet
    @Singleton
    fun provideAppCatalogHomeSearchProvider(
        provider: AppCatalogHomeSearchProvider,
    ): HomeSearchProvider = provider
}
