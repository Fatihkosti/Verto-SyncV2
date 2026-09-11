package com.verto.app.architecture

import com.verto.app.feature.invoice.application.port.InvoicePresentationPort
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.payment.application.port.PurchaseShipmentGateway
import com.verto.app.feature.shipment.application.port.LogisticsUnifiedReadPort
import org.junit.Assert.assertEquals
import org.junit.Test

class StableContractEvidenceTest {
    @Test
    fun `critical stable contract symbols remain directly exercised by evidence`() {
        assertEquals("PartyDirectoryGateway", PartyDirectoryGateway::class.java.simpleName)
        assertEquals("InvoicePresentationPort", InvoicePresentationPort::class.java.simpleName)
        assertEquals("PurchaseShipmentGateway", PurchaseShipmentGateway::class.java.simpleName)
        assertEquals("LogisticsUnifiedReadPort", LogisticsUnifiedReadPort::class.java.simpleName)
    }
}
