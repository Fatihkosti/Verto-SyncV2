package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryReconciliationMigration258Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun schema_72_to_74_is_schema_only_and_quantity_guard_is_bypass_scoped() {
        val dbName = "inventory-v258-72-74"
        helper.createDatabase(dbName, 72).apply {
            execSQL(
                """
                INSERT INTO inventory_items(
                    id,partNumber,name,barcode,isUnitItem,quantityPerUnit,isService,buyPrice,sellPrice,
                    quantity,minQuantity,location,note,createdAt,updatedAt,isDirty
                ) VALUES ('item-1','P1','Item','','0',0,0,100,120,10,1,'','',1,1,0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO inventory_movements(
                    id,itemId,invoiceId,clientId,movementType,quantity,quantityBefore,quantityAfter,
                    unitPrice,note,shipmentId,createdAt
                ) VALUES ('legacy-1','item-1','','','ADJUST',2,8,10,0,'legacy','',1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 74, true, MIGRATION_72_73, MIGRATION_73_74)
        db.query("SELECT quantity FROM inventory_items WHERE id='item-1'").use { c ->
            check(c.moveToFirst())
            assertEquals(10, c.getInt(0))
        }
        db.query("SELECT contract_version FROM inventory_movements WHERE id='legacy-1'").use { c ->
            check(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT state FROM inventory_reconciliation_control WHERE control_key='inventory-v2'").use { c ->
            check(c.moveToFirst())
            assertEquals("PENDING", c.getString(0))
        }

        assertThrows(Exception::class.java) {
            db.execSQL("UPDATE inventory_items SET quantity=11 WHERE id='item-1'")
        }

        db.execSQL("INSERT INTO inventory_reconciliation_apply_context(item_id,token) VALUES('item-1','t1')")
        db.execSQL("UPDATE inventory_items SET quantity=11 WHERE id='item-1'")
        db.execSQL("DELETE FROM inventory_reconciliation_apply_context WHERE item_id='item-1'")

        db.execSQL(
            """
            INSERT INTO inventory_reconciliation_markers(
                organization_id,item_id,contract_version,authority_kind,source_device_id,
                canonical_snapshot,authoritative_legacy_balance,reconciliation_delta,
                reconciliation_movement_id,idempotency_key,server_sequence,marker_checksum,state,
                approved_at,server_accepted_at,approved_by,completed_at
            ) VALUES ('org-1','item-1',2,'CENTRAL',NULL,11,2,9,'m-server',
                'inventory-reconcile:2:org-1:item-1',1,'checksum-1','COMPLETE',2,2,'user-1',2)
            """.trimIndent()
        )
        db.execSQL("UPDATE inventory_items SET quantity=12 WHERE id='item-1'")
        db.close()
    }
    @Test
    fun exported_legacy_schemas_to_74_preserve_inventory_snapshot() {
        val exportedSchemas = listOf(
            39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 60, 61, 68, 72,
        )
        exportedSchemas.forEach { fromVersion ->
            val dbName = "inventory-v258-$fromVersion-74"
            helper.createDatabase(dbName, fromVersion).apply {
                val hasBarcode = query("PRAGMA table_info(inventory_items)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "barcode") {
                            found = true
                            break
                        }
                    }
                    found
                }
                val columns = buildString {
                    append("id,partNumber,name,")
                    if (hasBarcode) append("barcode,")
                    append("isUnitItem,quantityPerUnit,isService,buyPrice,sellPrice,quantity,minQuantity,location,note,createdAt,updatedAt,isDirty")
                }
                val values = buildString {
                    append("'item-$fromVersion','P$fromVersion','Item $fromVersion',")
                    if (hasBarcode) append("'',")
                    append("0,0,0,100,120,17,1,'','',1,1,0")
                }
                execSQL("INSERT INTO inventory_items($columns) VALUES($values)")
                close()
            }

            val db = helper.runMigrationsAndValidate(
                dbName,
                74,
                true,
                *migrationPath(fromVersion, 74).toTypedArray(),
            )
            db.query("SELECT quantity FROM inventory_items WHERE id='item-$fromVersion'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("schema $fromVersion", 17, cursor.getInt(0))
            }
            db.query("SELECT state FROM inventory_reconciliation_control WHERE control_key='inventory-v2'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("schema $fromVersion", "PENDING", cursor.getString(0))
            }
            db.close()
        }
    }

}
