package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationCatalogV230Test {
    @Test
    fun `schema 56 upgrades through v230 schema 57 without a gap`() {
        val path = migrationPath(56, 57)
        assertEquals(1, path.size)
        assertEquals(56, path.single().startVersion)
        assertEquals(57, path.single().endVersion)
    }
}
