package com.verto.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedOutboxWriterM04Test {
    @Test
    fun `canonical payload preserves scalar json types`() {
        val json = canonicalSyncPayload(
            mapOf(
                "amountMinor" to 123L,
                "deleted" to true,
                "name" to "123",
                "nullable" to null,
            ),
        )
        assertEquals("{\"amountMinor\":123,\"deleted\":true,\"name\":\"123\",\"nullable\":null}", json)
    }

    @Test
    fun `canonical payload is stable for nested values`() {
        val json = canonicalSyncPayload(
            mapOf(
                "z" to listOf(2, false),
                "a" to mapOf("y" to 3, "x" to true),
            ),
        )
        assertEquals("{\"a\":{\"x\":true,\"y\":3},\"z\":[2,false]}", json)
    }

    @Test
    fun `canonical payload rejects non finite numbers`() {
        val failure = runCatching { canonicalSyncPayload(mapOf("bad" to Double.NaN)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
