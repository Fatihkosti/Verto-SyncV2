package com.verto.app.feature.auth.di

import com.verto.app.core.concurrency.AppCoroutineScope
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.PushTokenRepository
import com.verto.app.data.remote.RoleProvider
import com.verto.app.feature.auth.data.AuthGatewayAdapter
import com.verto.app.feature.auth.application.AuthSessionCoordinator
import com.verto.app.feature.auth.domain.repository.SplashSessionGateway
import com.verto.app.feature.auth.integration.DefaultAuthSessionCoordinator
import com.verto.app.feature.auth.integration.SplashSessionGatewayAdapter
import com.verto.app.feature.auth.domain.repository.AuthGateway
import com.verto.app.utils.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        preferencesManager: PreferencesManager,
        pushTokenRepository: PushTokenRepository,
        sessionWriter: SessionWriter
    ): AuthRepository = AuthRepository(preferencesManager, pushTokenRepository, sessionWriter)

    @Provides
    @Singleton
    fun provideAuthGateway(adapter: AuthGatewayAdapter): AuthGateway = adapter

    @Provides
    @Singleton
    fun provideRoleProvider(
        authRepository: AuthRepository,
        sessionReader: SessionReader,
        sessionWriter: SessionWriter,
        appScope: AppCoroutineScope,
    ): RoleProvider = RoleProvider(authRepository, sessionReader, sessionWriter, appScope)

    @Provides
    @Singleton
    fun providePermissionProvider(
        authRepository: AuthRepository,
        sessionReader: SessionReader,
        sessionWriter: SessionWriter,
        appScope: AppCoroutineScope,
    ): PermissionProvider = PermissionProvider(authRepository, sessionReader, sessionWriter, appScope)

    @Provides
    @Singleton
    fun provideAuthSessionCoordinator(implementation: DefaultAuthSessionCoordinator): AuthSessionCoordinator = implementation

    @Provides
    @Singleton
    fun provideSplashSessionGateway(adapter: SplashSessionGatewayAdapter): SplashSessionGateway = adapter
}
