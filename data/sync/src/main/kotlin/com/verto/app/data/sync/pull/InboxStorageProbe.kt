package com.verto.app.data.sync.pull

import android.content.Context
import android.os.StatFs
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

fun interface InboxStorageProbe { fun availableBytes(): Long }

@Singleton
class AndroidInboxStorageProbe @Inject constructor(@ApplicationContext private val context: Context) : InboxStorageProbe {
    override fun availableBytes(): Long = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }
        .getOrDefault(0L) // unknown disk capacity is not permission to advance a receive cursor
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DurableInboxStorageModule {
    @Binds abstract fun storageProbe(implementation: AndroidInboxStorageProbe): InboxStorageProbe
}
