package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MigrationCatalogV306Test {
    @Test fun `schema 77 reaches 78 only through v306 migration`() {
        assert(ROOM_SCHEMA_VERSION >= 82)
        val path = migrationPath(77, 82)
        assertEquals(listOf(77 to 78, 78 to 79, 79 to 80, 80 to 81, 81 to 82), path.map { it.startVersion to it.endVersion })
        assertSame(MIGRATION_77_78, path.first())
    }

    @Test fun `every supported schema has exactly one continuous path to current`() {
        val starts = ALL_MIGRATIONS.map { it.startVersion }
        assertEquals(starts.size, starts.toSet().size)
        for (start in 1 until ROOM_SCHEMA_VERSION) {
            val path = migrationPath(start)
            assertEquals(start, path.first().startVersion)
            assertEquals(ROOM_SCHEMA_VERSION, path.last().endVersion)
            path.zipWithNext().forEach { (left, right) ->
                assertEquals(left.endVersion, right.startVersion)
            }
        }
    }
}
