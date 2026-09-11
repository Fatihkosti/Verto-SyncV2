package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationCatalogB11Test {
    @Test
    fun `B11 conflict review migration is current and connected`() {
        assertEquals(101, ROOM_SCHEMA_VERSION)
        assertEquals(99, MIGRATION_99_100.startVersion)
        assertEquals(100, MIGRATION_99_100.endVersion)
        val path = migrationPath(99)
        assertEquals(listOf(99, 100), path.map { it.startVersion })
        assertTrue(path.last().endVersion == 101)
    }
}
