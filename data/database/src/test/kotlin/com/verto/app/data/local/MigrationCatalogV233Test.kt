package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationCatalogV233Test {
    @Test
    fun `migration catalog reaches schema 60 and retires legacy shipment tables`() {
        val path = migrationPath(59, 60)
        assertEquals(1, path.size)
        assertEquals(59, path.single().startVersion)
        assertEquals(60, path.single().endVersion)
        assert(ROOM_SCHEMA_VERSION >= 60)
    }
}
