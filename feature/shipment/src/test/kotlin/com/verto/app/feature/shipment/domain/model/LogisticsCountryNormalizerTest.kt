package com.verto.app.feature.shipment.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsCountryNormalizerTest {
    @Test
    fun `country key ignores casing repeated spaces and diacritics`() {
        assertEquals(LogisticsCountryNormalizer.key(" Sudan "), LogisticsCountryNormalizer.key("sUDAN"))
        assertEquals(LogisticsCountryNormalizer.key("السُّودان"), LogisticsCountryNormalizer.key("السودان"))
        assertEquals("السودان", LogisticsCountryNormalizer.displayName("  السودان   "))
    }

    @Test
    fun `internal country key accepts canonical names and legacy iso only`() {
        assertTrue(LogisticsCountryNormalizer.isInternalKey(LogisticsCountryNormalizer.key("السودان")))
        assertTrue(LogisticsCountryNormalizer.isInternalKey("SD"))
        assertFalse(LogisticsCountryNormalizer.isInternalKey("السودان"))
    }

    @Test
    fun `different entered country names keep distinct report identities`() {
        assertNotEquals(LogisticsCountryNormalizer.key("السودان"), LogisticsCountryNormalizer.key("مصر"))
    }
}
