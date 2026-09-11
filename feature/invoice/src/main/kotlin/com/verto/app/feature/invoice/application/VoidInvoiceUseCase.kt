package com.verto.app.feature.invoice.application

import com.verto.app.data.model.PermissionDeniedException
import com.verto.app.feature.invoice.application.InvoiceVoidCoordinator
import com.verto.app.feature.invoice.application.command.InvoiceVoidCommand
import com.verto.app.feature.invoice.domain.model.InvoiceAuthorizationException
import com.verto.app.feature.invoice.domain.model.InvoiceVoidRequest
import javax.inject.Inject

/** Compatibility entry point; orchestration lives in [InvoiceVoidCoordinator]. */
class VoidInvoiceUseCase @Inject constructor(
    private val coordinator: InvoiceVoidCoordinator
) : InvoiceVoidCommand {
    override suspend operator fun invoke(request: InvoiceVoidRequest) {
        translateAuthorization { coordinator.void(request) }
    }

    private suspend fun translateAuthorization(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: InvoiceAuthorizationException) {
            throw PermissionDeniedException("Permission denied")
        }
    }
}
