package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.CreateInvoiceReturnCommand
import com.verto.app.feature.invoice.domain.model.InvoiceReturnResult
import javax.inject.Inject

class CreateInvoiceReturnUseCase @Inject constructor(
    private val coordinator: InvoiceReturnCoordinator,
) {
    suspend operator fun invoke(command: CreateInvoiceReturnCommand): Result<InvoiceReturnResult> =
        coordinator.create(command)
}
