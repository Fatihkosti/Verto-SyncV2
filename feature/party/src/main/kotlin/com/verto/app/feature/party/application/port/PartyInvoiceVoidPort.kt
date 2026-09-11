package com.verto.app.feature.party.application.port

/** Boundary used by Party without depending on Invoice domain types. */
fun interface PartyInvoiceVoidPort {
    suspend fun void(invoiceId: String, reason: String, refundPayments: Boolean)
}
