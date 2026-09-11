package com.verto.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationCatalogV278Test {
    @Test fun `schema 76 reaches 77 through party migration`() {
        assertTrue(ROOM_SCHEMA_VERSION >= 77)
        assertEquals(listOf(76 to 77), migrationPath(76, 77).map { it.startVersion to it.endVersion })
        assertSame(MIGRATION_76_77, migrationPath(76, 77).single())
    }

    @Test fun `every supported schema has a continuous path to 77`() {
        for (start in 1 until 77) {
            val path = migrationPath(start, 77)
            assertEquals(start, path.first().startVersion)
            assertEquals(77, path.last().endVersion)
            path.zipWithNext().forEach { (left, right) -> assertEquals(left.endVersion, right.startVersion) }
        }
    }
}
