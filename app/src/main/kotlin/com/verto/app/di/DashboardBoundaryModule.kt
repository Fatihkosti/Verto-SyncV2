package com.verto.app.di

import com.verto.app.feature.dashboard.application.HomeNotificationBadgeQuery
import com.verto.app.feature.dashboard.bridge.RoomHomeEventStateStore
import com.verto.app.feature.dashboard.bridge.RoomHomeNotificationBadgeQuery
import com.verto.app.feature.dashboard.bridge.RoomQuickActionOrderStore
import com.verto.feature.dashboard.api.HomeEventStateStore
import com.verto.feature.dashboard.api.QuickActionOrderStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardBoundaryModule {
    @Binds
    abstract fun bindHomeNotificationBadgeQuery(
        implementation: RoomHomeNotificationBadgeQuery,
    ): HomeNotificationBadgeQuery

    @Binds
    abstract fun bindQuickActionOrderStore(
        implementation: RoomQuickActionOrderStore,
    ): QuickActionOrderStore

    @Binds
    abstract fun bindHomeEventStateStore(
        implementation: RoomHomeEventStateStore,
    ): HomeEventStateStore
}
