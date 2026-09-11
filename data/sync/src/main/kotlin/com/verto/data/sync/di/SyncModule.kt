package com.verto.data.sync.di

import com.verto.app.data.repository.NotificationSyncTrigger
import com.verto.app.data.sync.DefaultSyncOperations
import com.verto.app.data.sync.SyncNotificationTrigger
import com.verto.app.data.sync.SyncOperations
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncOperations(implementation: DefaultSyncOperations): SyncOperations

    @Binds
    abstract fun bindNotificationSyncTrigger(
        implementation: SyncNotificationTrigger
    ): NotificationSyncTrigger
}
