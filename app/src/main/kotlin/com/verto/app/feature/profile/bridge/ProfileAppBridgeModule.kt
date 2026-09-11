package com.verto.app.feature.profile.bridge
import com.verto.app.feature.profile.bridge.ProfileGatewayAdapter
import com.verto.app.feature.profile.domain.repository.ProfileGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProfileAppBridgeModule {
    @Provides @Singleton
    fun provideProfileGateway(adapter: ProfileGatewayAdapter): ProfileGateway = adapter
}
