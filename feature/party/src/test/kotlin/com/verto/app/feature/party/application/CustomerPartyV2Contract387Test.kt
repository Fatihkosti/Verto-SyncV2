package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.PartyRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerPartyV2Contract387Test {
    @Test fun `profile fields have one semantic meaning`() {
        val profile = CustomerProfile(
            partyId = "p",
            segment = CustomerSegment.COMPANY,
            purchaseContactName = "Buyer",
            businessActivity = "Mining",
            vehicleModels = listOf("Hilux", "Land Cruiser"),
        )
        assertEquals("Buyer", profile.purchaseContactName)
        assertEquals("Mining", profile.businessActivity)
        assertEquals(listOf("Hilux", "Land Cruiser"), profile.vehicleModels)
    }

    @Test fun `unknown legacy classification is never guessed into a customer role`() {
        val mapping = LegacyPartyRoleMapper.map("ALIEN")
        assertTrue(mapping.roles.isEmpty())
        assertEquals(setOf("ALIEN"), mapping.unknownTokens)
    }

    @Test fun `legacy competitor compatibility becomes dual role without a competitor segment`() {
        val mapping = LegacyPartyRoleMapper.map("COMPETITOR")
        assertEquals(setOf(PartyRole.CUSTOMER, PartyRole.SUPPLIER), mapping.roles)
        assertEquals(CustomerSegment.TRADER, mapping.customerSegment)
    }
}
