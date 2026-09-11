package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.PartyRole
import com.verto.app.feature.party.domain.model.SupplierScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPartyRoleMapperTest {
    @Test fun `competitor becomes trader customer plus supplier role`() {
        val result = LegacyPartyRoleMapper.map(" competitor ")
        assertEquals(setOf(PartyRole.CUSTOMER, PartyRole.SUPPLIER), result.roles)
        assertEquals(CustomerSegment.TRADER, result.customerSegment)
        assertEquals(SupplierScope.UNKNOWN, result.supplierScope)
    }

    @Test fun `compound supplier distributor separates role and segment`() {
        val result = LegacyPartyRoleMapper.map("SUPPLIER, DISTRIBUTOR")
        assertEquals(setOf(PartyRole.CUSTOMER, PartyRole.SUPPLIER), result.roles)
        assertEquals(CustomerSegment.DISTRIBUTOR, result.customerSegment)
        assertEquals(SupplierScope.LOCAL, result.supplierScope)
    }

    @Test fun `unknown token is quarantinable and never guessed`() {
        val result = LegacyPartyRoleMapper.map("ALIEN")
        assertTrue(result.roles.isEmpty())
        assertEquals(setOf("ALIEN"), result.unknownTokens)
    }
}
