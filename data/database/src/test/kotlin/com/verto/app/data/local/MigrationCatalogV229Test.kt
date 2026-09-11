package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationCatalogV229Test {
    @Test
    fun every_supported_schema_has_a_contiguous_path_to_56() {
        for (from in 1 until ROOM_SCHEMA_VERSION) {
            val path = migrationPath(from)
            assertEquals(from, path.first().startVersion)
            assertEquals(ROOM_SCHEMA_VERSION, path.last().endVersion)
            path.zipWithNext().forEach { (left, right) -> assertEquals(left.endVersion, right.startVersion) }
        }
    }

    @Test
    fun v55_upgrades_through_the_current_migration_chain() {
        val path = migrationPath(55)
        assertEquals(55, path.first().startVersion)
        assertEquals(56, path.first().endVersion)
        assertEquals(ROOM_SCHEMA_VERSION, path.last().endVersion)
    }
}
