package com.verto.app.data.sync

import com.verto.app.data.sync.push.SyncV2SpecializedPushBridge
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncV2PushBridgeModule {
    @Multibinds
    abstract fun specializedV2PushBridges(): Set<SyncV2SpecializedPushBridge>
}
