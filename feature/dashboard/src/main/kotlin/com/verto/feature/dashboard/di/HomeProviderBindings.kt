package com.verto.feature.dashboard.di

import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.HomeSearchProvider
import com.verto.feature.dashboard.api.PendingActionProvider
import com.verto.feature.dashboard.api.QuickActionProvider
import dagger.Module
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeProviderBindings {
    @Multibinds
    abstract fun homeSearchProviders(): Set<HomeSearchProvider>

    @Multibinds
    abstract fun pendingActionProviders(): Set<PendingActionProvider>

    @Multibinds
    abstract fun activityEventProviders(): Set<ActivityEventProvider>

    @Multibinds
    abstract fun quickActionProviders(): Set<QuickActionProvider>
}
