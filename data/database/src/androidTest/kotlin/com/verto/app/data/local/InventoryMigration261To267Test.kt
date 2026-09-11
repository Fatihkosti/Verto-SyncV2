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
class InventoryMigration261To267Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun schema_75_to_76_preserves_ledger_and_restricts_item_delete() {
        val name = "inventory-v267-75-76"
        helper.createDatabase(name, 75).apply {
            execSQL("INSERT INTO inventory_items(id,partNumber,name,barcode,nameSearch,partNumberSearch,barcodeSearch,isUnitItem,quantityPerUnit,isService,buyPrice,buy_price_minor,sellPrice,sell_price_minor,quantity,minQuantity,location,note,createdAt,updatedAt,isDirty) VALUES('i','','Item','','','','',0,0,0,10,1000,12,1200,5,1,'','',1,1,0)")
            execSQL("INSERT INTO inventory_movements(id,itemId,invoiceId,clientId,movementType,quantity,quantityBefore,quantityAfter,unitPrice,unit_price_minor,note,shipmentId,source_type,source_id,source_version,write_id,createdAt,contract_version) VALUES('m','i','','','IN',5,0,5,10,1000,'','','OPENING_BALANCE','i',2,'open',1,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 76, true, MIGRATION_75_76)
        db.query("SELECT quantity,is_archived FROM inventory_items WHERE id='i'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(5, c.getInt(0)); assertEquals(0, c.getInt(1))
        }
        runCatching { db.execSQL("DELETE FROM inventory_items WHERE id='i'") }
            .onSuccess { error("history FK must block item deletion") }
        db.query("SELECT COUNT(*) FROM inventory_movements WHERE id='m'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.close()
    }
}
