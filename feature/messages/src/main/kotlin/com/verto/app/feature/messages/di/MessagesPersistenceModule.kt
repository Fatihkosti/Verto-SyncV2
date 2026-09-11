package com.verto.app.feature.messages.di

import com.verto.app.feature.messages.data.RoomMessageOwnerAdapter
import com.verto.app.feature.messages.domain.port.MessageOwnerPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MessagesPersistenceModule {
    @Binds
    @Singleton
    abstract fun bindMessageOwnerPort(
        implementation: RoomMessageOwnerAdapter,
    ): MessageOwnerPort
}
