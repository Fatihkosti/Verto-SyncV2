package com.verto.app.data.operations.transaction

import com.verto.app.core.transaction.DatabaseTransactionRunner
import com.verto.app.data.operations.invoice.DefaultInvoiceMaintenanceCoordinator
import com.verto.app.data.operations.invoice.DefaultInvoiceReturnOutboxAdapter
import com.verto.app.data.operations.invoice.DefaultInvoiceAtomicPersistenceCoordinator
import com.verto.app.data.operations.payment.DefaultPaymentAtomicPersistenceCoordinator
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceCoordinator
import com.verto.app.feature.invoice.domain.port.InvoiceAtomicPersistenceCoordinator
import com.verto.app.feature.invoice.domain.port.InvoiceIntegrationOutboxPort
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceExtensionPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnOutboxPort
import com.verto.app.feature.invoice.domain.port.InvoiceTransactionPort
import com.verto.app.feature.payment.domain.port.PaymentTransactionPort
import com.verto.app.feature.payment.domain.port.PaymentAtomicPersistenceCoordinator
import com.verto.app.feature.payment.domain.port.PaymentIntegrationOutboxPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionOwnershipModule {
    @Binds
    @Singleton
    abstract fun bindDatabaseTransactionRunner(
        implementation: RoomDatabaseTransactionRunner,
    ): DatabaseTransactionRunner

    @Binds
    @Singleton
    abstract fun bindInvoiceTransactionPort(
        implementation: InvoiceTransactionOwnerAdapter,
    ): InvoiceTransactionPort

    @Binds
    @Singleton
    abstract fun bindPaymentTransactionPort(
        implementation: PaymentTransactionOwnerAdapter,
    ): PaymentTransactionPort

    @Binds
    @Singleton
    abstract fun bindPaymentAtomicPersistenceCoordinator(
        implementation: DefaultPaymentAtomicPersistenceCoordinator,
    ): PaymentAtomicPersistenceCoordinator

    @Binds
    @Singleton
    abstract fun bindInvoiceMaintenanceCoordinator(
        implementation: DefaultInvoiceMaintenanceCoordinator,
    ): InvoiceMaintenanceCoordinator

    @Binds
    @Singleton
    abstract fun bindInvoiceAtomicPersistenceCoordinator(
        implementation: DefaultInvoiceAtomicPersistenceCoordinator,
    ): InvoiceAtomicPersistenceCoordinator

    @Binds
    @Singleton
    abstract fun bindInvoiceReturnOutboxPort(
        implementation: DefaultInvoiceReturnOutboxAdapter,
    ): InvoiceReturnOutboxPort

    @Multibinds
    abstract fun invoiceMaintenanceExtensions(): Set<InvoiceMaintenanceExtensionPort>

    @Multibinds
    abstract fun invoiceIntegrationOutboxPorts(): Set<InvoiceIntegrationOutboxPort>

    @Multibinds
    abstract fun paymentIntegrationOutboxPorts(): Set<PaymentIntegrationOutboxPort>
}
