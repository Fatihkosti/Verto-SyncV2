package com.verto.app.feature.invoice.application.command

import com.verto.app.feature.invoice.domain.model.InvoiceVoidRequest

fun interface InvoiceVoidCommand {
    suspend operator fun invoke(request: InvoiceVoidRequest)
}
