package com.verto.app.core.session.di

import com.verto.app.core.session.data.PreferencesSessionStore
import com.verto.app.core.session.domain.DrawerSectionStateStore
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.utils.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun providePreferencesSessionStore(
        preferencesManager: PreferencesManager
    ): PreferencesSessionStore = PreferencesSessionStore(preferencesManager)

    @Provides
    @Singleton
    fun provideSessionReader(store: PreferencesSessionStore): SessionReader = store

    @Provides
    @Singleton
    fun provideSessionWriter(store: PreferencesSessionStore): SessionWriter = store

    @Provides
    @Singleton
    fun provideDrawerSectionStateStore(
        store: PreferencesSessionStore
    ): DrawerSectionStateStore = store
}
