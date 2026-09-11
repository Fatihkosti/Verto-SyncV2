package com.verto.app.feature.management.bridge

import com.verto.app.feature.management.domain.repository.BenzineControlPlaneGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BenzineControlPlaneBridgeModule {
    @Binds
    @Singleton
    abstract fun bindBenzineControlPlaneGateway(
        implementation: SupabaseBenzineControlPlaneGateway,
    ): BenzineControlPlaneGateway
}
