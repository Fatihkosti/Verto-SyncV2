package com.verto.app.di

import com.verto.app.feature.messages.domain.port.CompanyMessageTimelinePort
import com.verto.app.feature.messages.application.MessagesGateway
import com.verto.app.feature.messages.data.MessagesRealtimeSource
import com.verto.app.feature.messages.bridge.GatewayCompanyMessageTimelineAdapter
import com.verto.app.feature.messages.bridge.SupabaseMessagesGateway
import com.verto.app.feature.messages.data.SupabaseMessagesRealtimeSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MessagesBoundaryModule {
    @Binds
    @Singleton
    abstract fun bindMessagesGateway(
        adapter: SupabaseMessagesGateway
    ): MessagesGateway

    @Binds
    @Singleton
    abstract fun bindCompanyMessageTimelinePort(
        adapter: GatewayCompanyMessageTimelineAdapter,
    ): CompanyMessageTimelinePort

    @Binds
    @Singleton
    abstract fun bindMessagesRealtimeSource(
        source: SupabaseMessagesRealtimeSource
    ): MessagesRealtimeSource
}
