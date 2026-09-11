package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.integration.optimal.domain.port.OptimalCompanyTimelinePort
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationQueryPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingPort
import com.verto.app.feature.invoice.domain.port.InvoiceIntegrationOutboxPort
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceExtensionPort
import com.verto.app.feature.management.domain.repository.ManagementIntegrationBadgeContributor
import com.verto.app.feature.management.domain.repository.ManagementIntegrationContributor
import com.verto.app.feature.payment.domain.port.PaymentIntegrationOutboxPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OptimalFeatureBridgeModule {
    @Binds
    @Singleton
    abstract fun bindOptimalMessagingPort(implementation: OptimalMessagingBridge): OptimalMessagingPort

    @Binds
    @Singleton
    abstract fun bindOptimalConversationQueryPort(implementation: OptimalConversationQueryBridge): OptimalConversationQueryPort

    @Binds
    @Singleton
    abstract fun bindOptimalCompanyTimelinePort(implementation: OptimalCompanyTimelineBridge): OptimalCompanyTimelinePort

    @Binds
    @IntoSet
    abstract fun bindOptimalManagementIntegrationContributor(
        implementation: OptimalManagementIntegrationContributor,
    ): ManagementIntegrationContributor

    @Binds
    @IntoSet
    abstract fun bindOptimalManagementBadgeContributor(
        implementation: OptimalManagementBadgeContributor,
    ): ManagementIntegrationBadgeContributor

    @Binds
    @IntoSet
    abstract fun bindInvoiceMaintenanceExtension(
        implementation: OptimalInvoiceMaintenanceExtensionAdapter,
    ): InvoiceMaintenanceExtensionPort

    @Binds
    @IntoSet
    abstract fun bindInvoiceIntegrationOutboxPort(
        implementation: OptimalInvoiceIntegrationOutboxAdapter,
    ): InvoiceIntegrationOutboxPort

    @Binds
    @IntoSet
    abstract fun bindPaymentIntegrationOutboxPort(
        implementation: OptimalInvoiceIntegrationOutboxAdapter,
    ): PaymentIntegrationOutboxPort
}
