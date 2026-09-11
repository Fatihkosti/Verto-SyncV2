package com.verto.app.data.workers

import com.verto.app.data.sync.rfm.RfmCalculationPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RfmCalculationBridgeModule {
    @Binds
    @Singleton
    abstract fun bindRfmCalculationPort(adapter: AppRfmCalculationAdapter): RfmCalculationPort
}
