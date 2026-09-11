package com.verto.app.feature.party.bridge

import com.verto.app.data.local.dao.ClientDao
import com.verto.app.data.remote.PartySyncFallbackRemote
import com.verto.app.data.remote.dto.toEntity
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.feature.party.domain.repository.PartySyncFallbackPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** v327 app composition bridge: Party owns clients; Invoice/Payment writes route through InvoiceRepository. */
@Singleton
class AppPartySyncFallbackAdapter @Inject constructor(
    private val clientDao: ClientDao,
    private val invoiceRepository: InvoiceRepository,
    private val remote: PartySyncFallbackRemote,
) : PartySyncFallbackPort {
    override suspend fun pullClientsInvoicesAndPayments(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = remote.pull()
            snapshot.clients.forEach { clientDao.insertClient(it.toEntity().copy(isDirty = false)) }
            invoiceRepository.mirrorInvoicesFromServer(snapshot.invoices.map { it.toEntity() })
            snapshot.payments.forEach { invoiceRepository.mirrorPaymentFromServer(it.toEntity().copy(isDirty = false)) }
        }
    }
}
