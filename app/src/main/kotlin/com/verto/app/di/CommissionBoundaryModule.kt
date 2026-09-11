package com.verto.app.di

import com.verto.app.feature.commission.application.CommissionControllerFactory
import com.verto.app.feature.commission.bridge.DefaultCommissionControllerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CommissionBoundaryModule {
    @Binds
    abstract fun bindCommissionControllerFactory(
        implementation: DefaultCommissionControllerFactory
    ): CommissionControllerFactory
}
