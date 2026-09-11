package com.verto.app.feature.invoice.application

import java.util.UUID
import javax.inject.Inject

/** Narrow deterministic seam for invoice write identity and time. */
internal open class InvoiceWriteIdentityFactory @Inject constructor() {
    open fun newId(): String = UUID.randomUUID().toString()
    open fun nowMillis(): Long = System.currentTimeMillis()
}
