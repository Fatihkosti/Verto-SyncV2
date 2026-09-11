package com.verto.app.di

import android.content.Context
import com.verto.app.core.concurrency.AppCoroutineScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppCoroutineModule {

    @Provides
    @Singleton
    fun provideAppCoroutineScope(@ApplicationContext context: Context): AppCoroutineScope =
        context as? AppCoroutineScope
            ?: error("Application must own AppCoroutineScope")
}
