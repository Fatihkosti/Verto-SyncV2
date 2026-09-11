package com.verto.app.feature.reports.bridge

import com.verto.app.feature.reports.application.ReportsOperationsGateway
import com.verto.app.feature.reports.application.ReportsReadModelQuery
import com.verto.app.feature.reports.application.port.ReportsAccessPort
import com.verto.app.feature.reports.application.port.ReportsDocumentExportPort
import com.verto.app.feature.reports.bridge.DefaultReportsOperationsGateway
import com.verto.app.feature.reports.bridge.DefaultReportsReadModelQuery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReportsAppBridgeModule {
    @Provides @Singleton fun provideReportsAccessPort(bridge: ReportsAccessBridge): ReportsAccessPort = bridge
    @Provides @Singleton fun provideReportsDocumentExportPort(bridge: ReportsDocumentExportBridge): ReportsDocumentExportPort = bridge
    @Provides @Singleton
    fun provideReportsReadModelQuery(adapter: DefaultReportsReadModelQuery): ReportsReadModelQuery = adapter
    @Provides @Singleton
    fun provideReportsOperationsGateway(adapter: DefaultReportsOperationsGateway): ReportsOperationsGateway = adapter
}
