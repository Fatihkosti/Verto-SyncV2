package com.verto.app.feature.settings.bridge
import com.verto.app.feature.settings.bridge.*
import com.verto.app.feature.settings.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsAppBridgeModule {
    @Provides @Singleton fun provideAppearanceSettingsGateway(adapter: AppearanceSettingsGatewayAdapter): AppearanceSettingsGateway = adapter
    @Provides @Singleton fun providePrintingSettingsGateway(adapter: PrintingSettingsGatewayAdapter): PrintingSettingsGateway = adapter
    @Provides @Singleton fun provideSettingsOperationsGateway(adapter: SettingsOperationsGatewayAdapter): SettingsOperationsGateway = adapter
}
