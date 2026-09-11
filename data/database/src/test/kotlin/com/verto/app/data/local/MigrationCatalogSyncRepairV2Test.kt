package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationCatalogSyncRepairV2Test {
    @Test
    fun `sync repair advances schema exactly once`() {
        assertEquals(101, ROOM_SCHEMA_VERSION)
        assertEquals(96, MIGRATION_96_97.startVersion)
        assertEquals(97, MIGRATION_96_97.endVersion)
    }

    @Test
    fun `every supported schema has one consecutive path to current schema`() {
        for (from in 1 until ROOM_SCHEMA_VERSION) {
            val path = migrationPath(from)
            assertTrue(path.isNotEmpty())
            assertEquals(from, path.first().startVersion)
            assertEquals(ROOM_SCHEMA_VERSION, path.last().endVersion)
            path.zipWithNext().forEach { (left, right) ->
                assertEquals(left.endVersion, right.startVersion)
            }
        }
    }
}
