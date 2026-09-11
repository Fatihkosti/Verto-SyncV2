package com.verto.app.feature.notifications.bridge

import com.verto.app.feature.notifications.domain.repository.NotificationCenterGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationsAppBridgeModule {
    @Provides @Singleton
    fun provideNotificationCenterGateway(adapter: NotificationCenterGatewayAdapter): NotificationCenterGateway = adapter
}
