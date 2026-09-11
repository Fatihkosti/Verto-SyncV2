package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationCatalogV234Test {
    @Test
    fun `migration catalog reaches schema 61 for v234 contract`() {
        val path = migrationPath(60, 61)
        assertEquals(1, path.size)
        assertEquals(60, path.single().startVersion)
        assertEquals(61, path.single().endVersion)
        assert(ROOM_SCHEMA_VERSION >= 61)
    }
}
