package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationCatalogV232Test {
    @Test
    fun `migration catalog reaches schema 59`() {
        val path = migrationPath(58, 59)
        assertEquals(1, path.size)
        assertEquals(58, path.single().startVersion)
        assertEquals(59, path.single().endVersion)
        assert(ROOM_SCHEMA_VERSION >= 59)
    }
}
