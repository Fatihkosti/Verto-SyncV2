package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryStockWriterMigration259Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun schema_72_to_75_preserves_stock_and_adds_writer_tables() {
        val dbName = "inventory-v259-72-75"
        helper.createDatabase(dbName, 72).apply {
            execSQL(
                """
                INSERT INTO inventory_items(
                    id,partNumber,name,barcode,isUnitItem,quantityPerUnit,isService,buyPrice,sellPrice,
                    quantity,minQuantity,location,note,createdAt,updatedAt,isDirty
                ) VALUES ('item-1','P1','Item','',0,0,0,100,120,17,1,'','',1,1,0)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            75,
            true,
            *migrationPath(72, 75).toTypedArray(),
        )
        db.query("SELECT quantity FROM inventory_items WHERE id='item-1'").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(17, cursor.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='inventory_write_guards'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='inventory_stock_outbox'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }
}
