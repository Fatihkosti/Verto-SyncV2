package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationCatalogV231Test {
    @Test
    fun `migration catalog reaches schema 58`() {
        val path = migrationPath(57, 58)
        assertEquals(1, path.size)
        assertEquals(57, path.single().startVersion)
        assertEquals(58, path.single().endVersion)
        assert(ROOM_SCHEMA_VERSION >= 58)
    }
}
