package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationCatalogM03Test {
    @Test
    fun `M03 advances Room schema exactly once`() {
        assertEquals(101, ROOM_SCHEMA_VERSION)
        assertEquals(95, MIGRATION_95_96.startVersion)
        assertEquals(96, MIGRATION_95_96.endVersion)
    }

    @Test
    fun `every previously supported Room version reaches M03 schema`() {
        for (from in 1 until ROOM_SCHEMA_VERSION) {
            val path = migrationPath(from)
            assertTrue("missing migration path from $from", path.isNotEmpty())
            assertEquals(from, path.first().startVersion)
            assertEquals(ROOM_SCHEMA_VERSION, path.last().endVersion)
            path.zipWithNext().forEach { (a, b) -> assertEquals(a.endVersion, b.startVersion) }
        }
    }
}
