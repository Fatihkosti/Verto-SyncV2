package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceListTemplateMigrationV376Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migration_90_91_replaces_legacy_price_list_tables_with_template_tables() {
        val name = "price-list-template-v376"
        helper.createDatabase(name, 90).close()

        val db = helper.runMigrationsAndValidate(name, 91, true, MIGRATION_90_91)
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            val names = mutableSetOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(0)
            assertTrue(names.contains("price_list_templates"))
            assertTrue(names.contains("price_list_template_items"))
            assertFalse(names.contains("price_list_header"))
            assertFalse(names.contains("price_list_items"))
        }
        db.query("PRAGMA foreign_key_list(`price_list_template_items`)").use { cursor ->
            var fkCount = 0
            while (cursor.moveToNext()) fkCount++
            assertEquals(2, fkCount)
        }
        db.close()
    }
}
